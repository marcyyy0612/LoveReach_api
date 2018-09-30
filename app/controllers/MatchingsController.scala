package controllers

import java.sql.Timestamp

import controllers.MatchingJsonFormatter._
import javax.inject.Inject
import play.api.cache._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import repositories.MatchingsRepository
import slick.jdbc.MySQLProfile
import utils.TimestampFormatter._

import scala.concurrent.{ ExecutionContext, Future }

class MatchingsController @Inject()(cache: AsyncCacheApi,
                                    checkToken: CSRFCheck,
                                    val dbConfigProvider: DatabaseConfigProvider,
                                    matchingsJDBC: MatchingsRepository,
                                    cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  val LIKE: Int = 1

  // マッチング処理
  def insertMatchRelation: Action[JsValue] =
    Action.async(parse.json) { implicit rs =>
      rs.body
        .validate[MatchingForm]
        .map { form =>
          val uuid = rs.session.get("UUID")
          uuid match {
            case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
            case _ =>
              db.run(matchingsJDBC.matchingDBIO(form, uuid)).recover {
                case e => BadRequest(Json.obj("result" -> "failure"))
              }

          }
        }
        .recoverTotal { e =>
          // NGの場合はバリデーションエラーを返す
          Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
        }
    }
}

object MatchingJsonFormatter {

  case class MatchingForm(userId: Int, partnerId: Int, matchState: Int, selectedDatetime: Timestamp)

  implicit val matchingFormReads: Reads[MatchingForm] = Json.reads[MatchingForm]
}
