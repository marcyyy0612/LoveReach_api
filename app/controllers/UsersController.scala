package controllers

import controllers.UsersJsonFormatter._
import javax.inject.Inject
import models.Tables
import models.Tables._
import play.api.cache._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import repositories.UsersJDBC
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

class UsersController @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck,
                                usersJDBC: UsersJDBC,
                                val dbConfigProvider: DatabaseConfigProvider,
                                cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    val Male = Some(1)
    val Female = Some(2)
    val Diver = Some(3)

    def selectSigninUser: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ =>
                    db.run(usersJDBC.signinUserDBIO(uuid)).recover {
                        case e => BadRequest(Json.obj("result" -> "failure"))
                    }
            }
        }

    def list: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ =>
                    db.run(usersJDBC.usersListDBIO(uuid)).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
            }
        }

    def update: Action[JsValue] =
        Action.async(parse.json) { implicit rs =>
            rs.body.validate[UserForm].map { form =>
                val uuid = rs.session.get("UUID")
                uuid match {
                    case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                    case _ =>
                        db.run(usersJDBC.updateDBIO(uuid, form)).recover {
                            case e =>
                                BadRequest(Json.obj("result" -> "failure"))
                        }
                }
            }.recoverTotal { e =>
                // NGの場合はバリデーションエラーを返す
                Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
            }
        }

    def updateImg: Action[JsValue] =
        Action.async(parse.json) { implicit rs =>
            rs.body.validate[UserImgForm].map { form =>
                val uuid = rs.session.get("UUID")
                uuid match {
                    case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                    case _ =>
                        db.run(usersJDBC.updateImgDBIO(uuid, form)).recover {
                            case e =>
                                BadRequest(Json.obj("result" -> "failure"))
                        }
                }
            }.recoverTotal { e =>
                // NGの場合はバリデーションエラーを返す
                Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
            }
        }

    // 退会処理
    def deleteAccount: Action[AnyContent] =
        Action.async { implicit rs =>

            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "Unauthorized")))
                case _ =>
                    db.run(usersJDBC.deleteDBIO(uuid)).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
            }
        }

    // 相互likeしている(matchしている)ユーザのみ表示
    def listMatchingUsers: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "no uuid")))
                case _ =>
                    db.run(usersJDBC.matchingUserDBIO(uuid)).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
            }
        }

}

object UsersJsonFormatter {

    implicit val usersRowWritesFormat: Writes[Tables.UsersRow] = (user: UsersRow) => {
        Json.obj(
            "USER_ID" -> user.userId,
            "USER_NAME" -> user.userName,
            "SEX" -> user.sex,
            "PROFILE" -> user.profile,
            "PROFILE_IMAGE" -> user.profileImage
        )
    }

    // ユーザ情報を受け取るためのケースクラス
    case class UserForm(userName: String, sex: Int, profile: Option[String])

    implicit val userFormReads: Reads[UserForm] = Json.reads[UserForm]

    case class UserImgForm(profileImage: String)

    implicit val userImgFormReads: Reads[UserImgForm] = Json.reads[UserImgForm]

    case class DeleteAccountForm(password: String)

    implicit val deleteAccountFromReads: Reads[DeleteAccountForm] = Json.reads[DeleteAccountForm]
}


