package org.w3.vs.model

import org.joda.time.{DateTimeZone, DateTime}
import scalaz.Scalaz._
import play.api.libs.json._
import org.w3.vs.util.implicits._
import org.w3.vs.Database
import org.w3.vs.store.Formats._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.api.FailoverStrategy
import concurrent.Future

import org.joda.time.{DateTime, DateTimeZone}
import akka.pattern.{ask, AskTimeoutException}
import play.api.libs.iteratee._
import play.Logger
import org.w3.vs.util._
import org.w3.vs.store.MongoStore.journalCommit
import org.w3.vs.web._
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor._
import scala.util.{Success, Failure, Try}
import scala.concurrent.duration.Duration
import scala.concurrent.{ops => _, _}
import scala.concurrent.ExecutionContext.Implicits.global
import exception._
import org.w3.vs.view.model.JobView
import scalaz.Scalaz._
import play.modules.reactivemongo.json.ImplicitBSONHandlers._
import akka.actor._
import reactivemongo.core.commands.Count
import reactivemongo.bson.BSONDocument

import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._
import reactivemongo.api.FailoverStrategy
import reactivemongo.api.collections.default.BSONCollection

case class Coupon(
  id: CouponId = CouponId(),
  code: String,
  campaign: String,
  description: Option[String],
  credits: Int,
  expirationDate: DateTime = DateTime.now(DateTimeZone.UTC).plusYears(1),        // 90 days
  useDate: Option[DateTime] = None,
  usedBy: Option[UserId] = None) {

  def isUsed: Boolean = usedBy.isDefined

  def isExpired = {
    expirationDate <= DateTime.now(DateTimeZone.UTC)
  }

  def save()(implicit conf: Database): Future[Coupon] = {
    Coupon.save(this).map(_ => this)
  }

  def update()(implicit conf: Database): Future[Coupon] = {
    Coupon.update(this).map(_ => this)
  }

  def compactString = s"${code} - ${campaign} - Credits: ${credits} - ${expirationDate} - ${useDate} - ${usedBy}"

}

object Coupon {

  def delete(code: String)(implicit conf: ValidatorSuite): Future[Unit] = {
    val query = Json.obj("code" -> toJson(code))
    collection.remove[JsValue](query) map {
      lastError => ()
    }
  }

  def apply(
    code: String,
    campaign: String,
    credits: Int): Coupon = {
    new Coupon(code = code, campaign = campaign, description = None, credits = credits)
  }

  def apply(
    code: String,
    campaign: String,
    credits: Int,
    description: String,
    validity: String): Coupon = {
    new Coupon(code = code, campaign = campaign, description = Some(description), credits = credits) // TODO
  }

  def redeem(couponCode: String, userId: UserId)(implicit vs: ValidatorSuite with Database): Future[(User, Coupon)] = {
    for {
      coupon <- get(couponCode).map{ coupon =>
        if (coupon.useDate.isDefined) { throw new AlreadyUsedCouponException() }
        coupon
      }
      user <- model.User.updateCredits(userId, coupon.credits)
      redeemed <-
        coupon.copy(useDate = Some(DateTime.now(DateTimeZone.UTC)), usedBy = Some(userId)).update()
    } yield (user, redeemed)
  }

  def collection(implicit conf: Database): BSONCollection =
    conf.db("coupons", failoverStrategy = FailoverStrategy(retries = 0))

  def getAll()(implicit conf: Database): Future[List[Coupon]] = {
    val cursor = collection.find(Json.obj()).cursor[JsValue]
    cursor.toList() map {
      list => list flatMap { coupon =>
        try {
          Some(coupon.as[Coupon])
        } catch { case t: Throwable =>
          None
        }
      }
    }
  }

  def get(code: String)(implicit conf: Database): Future[Coupon] = {
    val query = Json.obj("code" -> toJson(code))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption() map {
      case None => throw new NoSuchElementException("Invalid coupon code: " + code)
      case Some(json) => json.as[Coupon]
    }
  }

  def save(coupon: Coupon)(implicit conf: Database): Future[Unit] = {
    val couponJson = toJson(coupon)
    import reactivemongo.core.commands.LastError
    collection.insert(couponJson, writeConcern = journalCommit) map { lastError => () } recover {
      case LastError(_, _, Some(11000), _, _, _, _) => throw new DuplicateCouponException()
    }
  }

  def update(coupon: Coupon)(implicit conf: Database): Future[Unit] = {
    val selector = Json.obj("_id" -> toJson(coupon.id))
    val update = toJson(coupon)
    collection.update(selector, update, writeConcern = journalCommit) map {
      lastError => if (!lastError.ok) throw lastError
    }
  }

}
