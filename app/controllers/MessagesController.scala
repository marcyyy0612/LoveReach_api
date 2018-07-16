package controllers

import java.sql.{ Date, Timestamp }

import controllers.MessageJsonFormatter._
import javax.inject.Inject
import models.Tables
import models.Tables._
import play.api.cache._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import services.MatchingService
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._
import utils.TimestampFormatter._

import scala.concurrent.{ ExecutionContext, Future }

class MessagesController @Inject()(cache: AsyncCacheApi,
                                   checkToken: CSRFCheck,
                                   matchingService: MatchingService,
                                   val dbConfigProvider: DatabaseConfigProvider,
                                   cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  // matchしているユーザへメッセージ送信
  def insertMessages: Action[JsValue] =
    Action.async(parse.json) { implicit rs =>
      val signinUserId = cache.get[Int]("userId")

      rs.session.get("UUID") match {
        case None => Future.successful(Ok(Json.obj("result" -> "failure")))
        case _ => {
          signinUserId.flatMap(id => {
            rs.body
              .validate[MessageForm]
              .map { form =>
                val message = MessagesRow(id.get, form.partnerId, form.message, form.sendDatetime)
                db.run(Messages += message).map { _ =>
                  Ok(Json.obj("result" -> "success"))
                }
              }
              .recoverTotal { e =>
                // NGの場合はバリデーションエラーを返す
                Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
              }
          })
        }
      }
    }

  // matchしているユーザとのメッセージを取得
  def selectMessages(partnerId: Int): Action[AnyContent] =
    Action.async { implicit rs =>
      rs.session.get("UUID") match {
        case None => Future.successful(Ok(Json.obj("result" -> "failure")))
        case _ => {
          def messagesDBIO(userId: Int) =
            Messages
              .filter(messages => {
                messages.userId === userId.bind || messages.userId === partnerId.bind
              })
              .filter(messages => {
                messages.partnerId === userId.bind || messages.partnerId === partnerId.bind
              })
              .result

          def resultDBIO =
            for {
              userIdOpt <- DBIO.from(cache.get[Int]("userId"))
              userId <- userIdOpt match {
                case Some(userId) => DBIO.successful(userId)
                case _            => DBIO.failed(new Exception("cache not found"))
              }
              messages <- messagesDBIO(userId)
            } yield Ok(Json.obj("MESSAGES" -> messages))

          db.run(resultDBIO).recover {
            case e => BadRequest(Json.obj("result" -> "failure"))
          }
        }
      }
    }
}
object MessageJsonFormatter {

  implicit val messageRowWritesFormat: Writes[Tables.MessagesRow] = (message: MessagesRow) => {
    Json.obj(
      "USER_ID" -> message.userId,
      "PARTNER_ID" -> message.partnerId,
      "MESSAGE" -> message.message,
      "SEND_DATETIME" -> message.sendDatetime
    )
  }

  case class MessageForm(userId: Int, partnerId: Int, message: String, sendDatetime: Timestamp)

  implicit val MessageFormReads: Reads[MessageForm] = Json.reads[MessageForm]
}
