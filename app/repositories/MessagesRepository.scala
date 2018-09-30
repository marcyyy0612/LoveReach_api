package repositories

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
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._
import utils.TimestampFormatter._

import scala.concurrent.ExecutionContext

class MessagesRepository @Inject()(cache: AsyncCacheApi,
                             checkToken: CSRFCheck,
                             val dbConfigProvider: DatabaseConfigProvider,
                             cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  def insertMessagesDBIO(form: MessageForm, uuid: Option[String]) = {
    def messagesDBIO(message: MessagesRow) =
      Messages += message

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        message = MessagesRow(userId, form.partnerId, form.message, form.sendDatetime)
        messageResult <- messagesDBIO(message)
      } yield Ok(Json.obj("result" -> "success"))

    resultDBIO
  }

  def selectMessagesDBIO(partnerId: Int, uuid: Option[String]) = {
    def messagesDBIO(userId: Int, partnerId: Int) =
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
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        messages <- messagesDBIO(userId, partnerId)
      } yield Ok(Json.obj("MESSAGES" -> messages))

    resultDBIO
  }

}
