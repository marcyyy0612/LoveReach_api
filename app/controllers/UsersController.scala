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
import services.{LocationService, MatchingService}
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class UsersController @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck,
                                matchingService: MatchingService,
                                locationService: LocationService,
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

    def list: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ =>
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
                        distance <- locationService.resultDBIO(userId)
                        nearUsers = distance.filter(_.distance < 5) //ログインユーザの中で自分との距離が5km 未満のユーザのみ
                        users <- usersDBIO(userId) //自分以外のユーザ
                        selectedUsers <- selectedUsersDBIO(userId) //like, nopeの選択をしたユーザ
                        nonSelectedUsers = users.filterNot(user => { //未選択のユーザ
                            selectedUsers.exists(selectedUser => user.userId.contains(selectedUser.partnerId))
                        })
                        searchGender <- myInfoDBIO(userId) //自分が選択した興味のある性別
                        resultUsers = searchGender match { //選択した興味のある性別によって選別
                            case Male => nonSelectedUsers.filter(_.sex == 2)
                            case Female => nonSelectedUsers.filter(_.sex == 1)
                            case Diver => nonSelectedUsers
                            case _ => Nil
                        }
                        responseUsers = nearUsers.flatMap(user => {
                            resultUsers.filter(_.userId.get == user.userId)
                        })
                    } yield {
                        Ok(Json.obj("USERS" -> responseUsers))
                    }

                    db.run(resultDBIO).recover {
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
                        def userDBIO(id: Int) = Users.filter(_.userId === id)
                            .map(user => (user.userName, user.sex, user.profile))
                            .update(form.userName, form.sex, form.profile)

                        def resultDBIO = for {
                            userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                            userId <- userIdOpt match {
                                case Some(userId) => DBIO.successful(userId)
                                case _ => DBIO.failed(new Exception("cache not found"))
                            }
                            user <- userDBIO(userId)
                        } yield Ok(Json.obj("result" -> "success"))

                        db.run(resultDBIO).recover {
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
                        def userDBIO(id: Int) = Users.filter(_.userId === id)
                            .map(user => user.profileImage)
                            .update(form.profileImage)

                        def resultDBIO = for {
                            userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                            userId <- userIdOpt match {
                                case Some(userId) => DBIO.successful(userId)
                                case _ => DBIO.failed(new Exception("cache not found"))
                            }
                            user <- userDBIO(userId)
                        } yield Ok(Json.obj("result" -> "success"))

                        db.run(resultDBIO).recover {
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
                case _ => {
                    def usersDBIO(id: Int): DBIO[Option[UsersRow]] =
                        Users.filter(_.userId === id.bind).result.headOption

                    def deleteUserDBIO(id: Int) =
                        Users.filter(_.userId === id.bind).delete

                    def deleteMessagesDBIO(id: Int) =
                        Messages.filter(_.userId === id.bind).delete

                    def deleteLocationDBIO(id: Int) =
                        UsersLocation.filter(_.userId === id.bind).delete

                    def deleteMatchingDBIO(id: Int) =
                        MatchRelations.filter(_.userId === id.bind).delete

                    def deleteLoginDBIO(id: Int) =
                        LoginStatuses.filter(_.userId === id.bind).delete

                    def resultDBIO = for {
                        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                        userId <- userIdOpt match {
                            case Some(userId) =>
                                cache.remove(uuid.getOrElse("None"))
                                DBIO.successful(userId)
                            case _ => DBIO.failed(new Exception("cache not found"))
                        }
                        deleteLogin <- deleteLoginDBIO(userId)
                        deleteMatching <- deleteMatchingDBIO(userId)
                        deleteLocation <- deleteLocationDBIO(userId)
                        deleteMessages <- deleteMessagesDBIO(userId)
                        deleteUser <- deleteUserDBIO(userId)
                    } yield {
                        Ok(Json.obj("result" -> "success")).withNewSession
                    }

                    db.run(resultDBIO).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
                }
            }
        }

    // 相互likeしている(matchしている)ユーザのみ表示
    def listMatchingUsers: Action[AnyContent] =
        Action.async {
            implicit rs =>
                val LIKE: Int = 1

                val uuid = rs.session.get("UUID")
                uuid match {
                    case None => Future.successful(Unauthorized(Json.obj("result" -> "no uuid")))
                    case _ =>
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


