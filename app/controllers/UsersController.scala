package controllers

import java.sql.{Date, Timestamp}

import controllers.UsersJsonFormatter._
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

class UsersController @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck,
                                matchingService: MatchingService,
                                val dbConfigProvider: DatabaseConfigProvider,
                                cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    val MALE = Option(1)
    val FEMALE = Option(2)
    val DIVER = Option(3)

    def selectSigninUser: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ => {
                    def userDBIO(userId: Int) = Users.filter(_.userId === userId.bind).result

                    def resultDBIO =
                        for {
                            userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                            userId <- userIdOpt match {
                                case Some(userId) => DBIO.successful(userId)
                                case _ => DBIO.failed(new Exception("cache not found"))
                            }
                            user <- userDBIO(userId)
                        } yield Ok(Json.obj("Me" -> user))

                    db.run(resultDBIO).recover {
                        case e => BadRequest(Json.obj("result" -> "failure"))
                    }
                }
            }
        }

    def list: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ => {
                    def myInfoDBIO(id: Int) =
                        Users.filter(_.userId === id).map(_.sex).result.headOption

                    def usersDBIO(id: Int) =
                        Users.filter(_.userId =!= id).result

                    def selectedUsersDBIO(id: Int) =
                        MatchRelations.filter(_.userId === id).result

                    val resultDBIO = for {
                        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                        userId <- userIdOpt match {
                            case Some(userId) => DBIO.successful(userId)
                            case _ => DBIO.failed(new Exception("cache not found"))
                        }
                        users <- usersDBIO(userId)
                        selectedUsers <- selectedUsersDBIO(userId)
                        nonSelectedUsers = users.filterNot(user => {
                            selectedUsers.exists(selectedUser => user.userId.contains(selectedUser.partnerId))
                        })
                        searchGender <- myInfoDBIO(userId)
                        resultUsers = searchGender match {
                            case MALE => nonSelectedUsers.filter(_.sex == 2)
                            case FEMALE => nonSelectedUsers.filter(_.sex == 1)
                            case DIVER => nonSelectedUsers
                            case _ => Nil
                        }
                    } yield Ok(Json.obj("USERS" -> resultUsers))

                    db.run(resultDBIO).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
                }
            }
        }

    // プロフィール更新
    def update: Action[JsValue] =
        Action.async(parse.json) { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ => {
                    val signinUserId = cache.get[Int](uuid.getOrElse("None"))
                    signinUserId.flatMap(userId => {
                        rs.body
                            .validate[UserForm]
                            .map { form =>
                                // OKの場合はユーザ情報を更新
                                val user = UsersRow(
                                    userId,
                                    form.userName,
                                    form.sex,
                                    form.birthday,
                                    form.profile,
                                    form.createdAt,
                                    form.mailAddress,
                                    form.password,
                                    form.profileImage
                                )
                                db.run(Users.filter(t => t.userId === user.userId.bind).update(user)).map { _ =>
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

    // 退会処理
    //    def remove(userId: Int): Action[AnyContent] = Action.async { implicit rs =>
    //        // ユーザを削除
    //        db.run(Users.filter(t => t.userId === userId.bind).delete).map { _ =>
    //            Ok(Json.obj("result" -> "success"))
    //        }
    //    }

    // 相互likeしている(matchしている)ユーザのみ表示
    def listMatchingUsers: Action[AnyContent] =
        Action.async { implicit rs =>
            val LIKE: Int = 1

            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "no uuid")))
                case _ => {
                    def listFollowerDBIO(id: Int) =
                        Users
                            .filter(
                                _.userId in MatchRelations
                                    .filter(_.userId === id.bind)
                                    .filter(_.matchState === LIKE)
                                    .map(_.partnerId)
                            )
                            .result

                    def listFolloweeDBIO(id: Int) =
                        MatchRelations
                            .filter(_.partnerId === id.bind)
                            .filter(_.matchState === LIKE)
                            .result

                    val resultDBIO = for {
                        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                        userId <- userIdOpt match {
                            case Some(userId) => DBIO.successful(userId)
                            case _ => DBIO.failed(new Exception("cache not found"))
                        }
                        users <- listFollowerDBIO(userId)
                        partners <- listFolloweeDBIO(userId)
                        matcherUsers = users.filter(user => partners.exists(p => user.userId.contains(p.userId)))
                    } yield Ok(Json.obj("MatchUser" -> matcherUsers))

                    db.run(resultDBIO).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
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
//            "BIRTHDAY" -> user.birthday.toLocalDate,
            "PROFILE" -> user.profile,
//            "CREATED_AT" -> user.createdAt.toLocalDate,
//            "MAIL_ADDRESS" -> user.mailAddress,
//            "PASSWORD" -> user.password,
            "PROFILE_IMAGE" -> user.profileImage
        )
    }

    // ユーザ情報を受け取るためのケースクラス
    case class UserForm(userId: Option[Int],
                        userName: String,
                        sex: Int,
                        birthday: Date,
                        profile: Option[String],
                        createdAt: Date,
                        mailAddress: String,
                        password: String,
                        profileImage: String)

    implicit val userFormReads: Reads[UserForm] = Json.reads[UserForm]
}
