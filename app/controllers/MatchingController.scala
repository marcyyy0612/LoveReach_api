package controllers

import java.sql.Timestamp

import controllers.MatchingJsonFormatter._
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

import scala.concurrent.{ExecutionContext, Future}

class MatchingController @Inject()(cache: AsyncCacheApi,
                                   checkToken: CSRFCheck,
                                   val dbConfigProvider: DatabaseConfigProvider,
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
                                        case _ => DBIO.failed(new Exception("cache not found"))
                                    }
                                    matchRelation = MatchRelationsRow(userId, form.partnerId, form.matchState, form.selectedDatetime)
                                    matchResult <- matchDBIO(matchRelation)
                                    isMatching <- isBothMatchDBIO(userId, form.partnerId)
                                } yield Ok(Json.obj("result" -> "success", "isMatching" -> isMatching))

                            db.run(resultDBIO).recover {
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
