package org.w3.vs.model

import org.w3.util._
import scalaz.std.string._
import scalaz.Scalaz.ToEqualOps
import org.w3.banana.TryW
import org.w3.vs.exception._
import org.w3.vs._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.vs.diesel._
import org.w3.vs.diesel.ops._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{Concurrent, Enumerator}
import org.w3.vs.actor.message.RunUpdate
import akka.actor.{Actor, Props, ActorRef}
import java.nio.channels.ClosedChannelException
import org.w3.util.akkaext.{Deafen, Listen, PathAware}

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson

import org.w3.vs.store.Formats._

case class User(id: UserId, vo: UserVO)(implicit conf: VSConfiguration) {
  
  import conf._

  val userUri = id.toUri

  // getJob with id only if owned by user. should probably be a db request directly.
  def getJob(jobId: JobId): Future[Job] = {
    Job.getFor(id) map {
      jobs => jobs.filter(_.id === jobId).headOption.getOrElse { throw UnknownJob(jobId) }
    }
  }

  def getJobs(): Future[Iterable[Job]] = {
    Job.getFor(id)
  }
  
  def save(): Future[Unit] = User.save(this)
  
  def delete(): Future[Unit] = User.delete(this)

  lazy val enumerator: Enumerator[RunUpdate] = {
    val (_enumerator, channel) = Concurrent.broadcast[RunUpdate]
    val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunUpdate =>
          try {
            channel.push(msg)
          } catch {
            case e: ClosedChannelException => {
              logger.error("ClosedChannel exception: ", e)
              channel.eofAndEnd()
            }
            case e => {
              logger.error("Enumerator exception: ", e)
              channel.eofAndEnd()
            }
          }
        case msg => logger.error("subscriber got " + msg)
      }
    }))
    listen(subscriber)
    _enumerator
  }

  def listen(implicit listener: ActorRef): Unit =
    PathAware(usersRef, path).tell(Listen(listener), listener)

  def deafen(implicit listener: ActorRef): Unit =
    PathAware(usersRef, path).tell(Deafen(listener), listener)

  val usersRef = system.actorFor(system / "users")

  private val path = system / "users" / id.toString
  
}

object User {

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("users")

  val emailsGraph = URI("https://validator.w3.org/suite/emails")
  
  val logger = play.Logger.of(classOf[User])

  def apply(
    userId: UserId,
    name: String,
    email: String,
    password: String)(
    implicit conf: VSConfiguration): User =
      User(userId, UserVO(name, email, password))

  def get(userUri: Rdf#URI)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    for {
      userId <- userUri.as[UserId].asFuture
      userVO <- {
        val query = Json.obj(("_id" -> Json.obj("$oid" -> userId.oid.stringify)))
        val cursor = collection.find[JsValue, JsValue](query)
        cursor.toList map { _.headOption match {
          case Some(json) => json.as[UserVO]
          case None => sys.error("user not found")
        }}
      }
    } yield new User(userId, userVO)
  }
  
  def get(id: UserId)(implicit conf: VSConfiguration): Future[User] =
    get(UserUri(id))

  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    getByEmail(email) map { 
      case user if (user.vo.password /== password) => throw Unauthenticated
      case user => user
    }
  }

  def register(email: String, name: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    val user = User(userId = UserId(), email = email, name = name, password = password)
    user.save().map(_ => user)
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("email" -> JsString(email))
    val cursor: FlattenedCursor[JsValue] = collection.find[JsValue, JsValue](query)
    cursor.toList map { _.headOption match {
      case Some(json) => {
        val id = (json \ "_id" \ "$oid").as[UserId]
        val userVo = json.as[UserVO]
        User(id, userVo)
      }
      case None => throw UnknownUser
    }}
  }

  def save(vo: UserVO)(implicit conf: VSConfiguration): Future[Rdf#URI] = {
    import conf._
    val userId = UserId()
    val oid = userId.oid
    val user = toJson(vo).asInstanceOf[JsObject] + ("_id" -> Json.obj("$oid" -> oid.stringify))
    collection.insert(user) map { lastError =>
      userId.toUri
    }
  }
  
  def save(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val userId = user.id
    val oid = userId.oid
    val userJ = toJson(user.vo).asInstanceOf[JsObject] + ("_id" -> Json.obj("$oid" -> oid.stringify))
    collection.insert(userJ) map { lastError => () }
  }

  def delete(user: User)(implicit conf: VSConfiguration): Future[Unit] =
    sys.error("")
    
}
