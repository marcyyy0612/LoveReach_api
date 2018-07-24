package controllers

import java.sql.{ Date, Timestamp }

import controllers.MatchingJsonFormatter._
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

class MatchingController @Inject()(cache: AsyncCacheApi,
                                   checkToken: CSRFCheck,
                                   matchingService: MatchingService,
                                   val dbConfigProvider: DatabaseConfigProvider,
                                   cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  val LIKE: Int = 1

  // マッチング処理
  def insertMatchRelation: Action[JsValue] =
    Action.async(parse.json) { implicit rs =>
      val uuid = rs.session.get("UUID")
      uuid match {
        case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
        case _ => {
          val signinUserId = cache.get[Int](uuid.getOrElse("None"))
          signinUserId.flatMap(userId => {
            rs.body
              .validate[MatchingForm]
              .map { form =>
                // OKの場合はMatchRelationテーブルに登録
                val matchRelation =
                  MatchRelationsRow(userId.get, form.partnerId, form.matchState, form.selectedDatetime)
                matchingService
                  .isBothMatching(userId.get, form.partnerId)
                  .flatMap(isMatching => {
                    db.run(MatchRelations += matchRelation).map { _ =>
                      Ok(Json.obj("result" -> "success", "isMatching" -> isMatching))
                    }
                  })
              }
              .recoverTotal { e =>
                // NGの場合はバリデーションエラーを返す
                Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
              }
          })
        }
      }
    }
}

object MatchingJsonFormatter {

  case class MatchingForm(userId: Int, partnerId: Int, matchState: Int, selectedDatetime: Timestamp)

  implicit val matchingFormReads: Reads[MatchingForm] = Json.reads[MatchingForm]
}
