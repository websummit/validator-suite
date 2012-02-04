package org.w3.vs.assertor

import org.w3.vs.model._
import com.codecommit.antixml._
import scala.io.Source

/** An Assertor that reads [[http://code.w3.org/unicorn/wiki/Documentation/Observer/Response ObservationResponse]]s from [[scala.io.Source]]s
 */
trait FromUnicornFormatAssertor extends FromSourceAssertor {

  def assert(source: Source): Events = {
    val response:Elem = XML.fromSource(source)
    val obversationRef = response.attrs get "ref" getOrElse sys.error("malformed xml")
    val obversationLang = response.attrs get QName(Some("xml"), "lang") getOrElse sys.error("malformed xml")
    val events =
      for {
        message <- response \ "message"
        typ <- message.attrs get "type"
      } yield {
        val id = message.attrs get "id" getOrElse {
          val title = (message \ "title" \ text headOption) getOrElse "UNKNOWN"
          title.hashCode.toString
        }
        val eventRef = message.attrs get "ref" getOrElse obversationRef
        val eventLang = message.attrs get "lang" getOrElse obversationLang
        val contexts =
          for {
            context <- message \ "context"
            content <- context \ text
          } yield {
            val contextRef = context.attrs get "ref" getOrElse eventRef
            val line = context.attrs get "line" map { s => s.toInt }
            val column = context.attrs get "column" map { s => s.toInt }
            Context(content, contextRef, line, column)
          }
        Event(typ, id, eventLang, contexts)
      }
    Events(events.toList)
  }  

}