package repositories

import java.sql.Timestamp

import controllers.MatchingJsonFormatter._
import javax.inject.Inject
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

class MatchingsRepository @Inject()(cache: AsyncCacheApi,
                              checkToken: CSRFCheck,
                              val dbConfigProvider: DatabaseConfigProvider,
                              cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  def matchingDBIO(form: MatchingForm, uuid: Option[String]) = {
    def matchDBIO(matchRelation: MatchRelationsRow) =
      MatchRelations += matchRelation

    def isBothMatchDBIO(userId: Int, partnerId: Int) =
      MatchRelations
        .filter(_.matchState === 1)
        .filter(_.partnerId === userId)
        .filter(_.userId === partnerId)
        .result
        .map(_.nonEmpty)

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        matchRelation = MatchRelationsRow(userId, form.partnerId, form.matchState, form.selectedDatetime)
        matchResult <- matchDBIO(matchRelation)
        isMatching <- isBothMatchDBIO(userId, form.partnerId)
      } yield Ok(Json.obj("result" -> "success", "isMatching" -> isMatching))

    resultDBIO
  }

}
