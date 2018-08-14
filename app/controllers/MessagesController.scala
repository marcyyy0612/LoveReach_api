package controllers

import java.sql.Timestamp

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

import scala.concurrent.{ExecutionContext, Future}

class MessagesController @Inject()(cache: AsyncCacheApi,
                                   checkToken: CSRFCheck,
                                   matchingService: MatchingService,
                                   val dbConfigProvider: DatabaseConfigProvider,
                                   cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    // matchしているユーザへメッセージ送信
    def insertMessages(): Action[JsValue] =
        Action.async(parse.json) { implicit rs =>
            rs.body.validate[MessageForm].map { form =>
                val uuid = rs.session.get("UUID")
                uuid match {
                    case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                    case _ =>
                        def messagesDBIO(message: MessagesRow) =
                            Messages += message

                        def resultDBIO = for {
                            userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                            userId <- userIdOpt match {
                                case Some(userId) => DBIO.successful(userId)
                                case _ => DBIO.failed(new Exception("cache not found"))
                            }
                            message = MessagesRow(userId, form.partnerId, form.message, form.sendDatetime)
                            messageResult <- messagesDBIO(message)
                        } yield Ok(Json.obj("result" -> "success"))

                        db.run(resultDBIO).recover {
                            case e => BadRequest(Json.obj("result" -> "failure"))
                        }
                }
            }.recoverTotal { e =>
                // NGの場合はバリデーションエラーを返す
                Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
            }
        }

    // matchしているユーザとのメッセージを取得
    def selectMessages(partnerId: Int): Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ =>
                    def messagesDBIO(userId: Int) =
                        Messages
                            .filter(messages => {
                                messages.userId === userId.bind || messages.userId === partnerId.bind
                            })
                            .filter(messages => {
                                messages.partnerId === userId.bind || messages.partnerId === partnerId.bind
                            })
                            .sortBy(_.sendDatetime)
                            .result

                    def resultDBIO =
                        for {
                            userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                            userId <- userIdOpt match {
                                case Some(userId) => DBIO.successful(userId)
                                case _ => DBIO.failed(new Exception("cache not found"))
                            }
                            messages <- messagesDBIO(userId)
                        } yield Ok(Json.obj("MESSAGES" -> messages))

                    db.run(resultDBIO).recover {
                        case e => BadRequest(Json.obj("result" -> "failure"))
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
