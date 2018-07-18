package controllers

import java.sql.Date
import java.util.UUID._

import akka.Done
import controllers.SignInJsonFormatter.SignInForm
import controllers.SignUpJsonFormatter.SignUpForm
import javax.inject.Inject
import models.Tables._
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json._
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.filters.csrf._
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject()(cache: AsyncCacheApi,
                                      addToken: CSRFAddToken,
                                      val dbConfigProvider: DatabaseConfigProvider,
                                      cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    def signup: Action[JsValue] = addToken {
        Action.async(parse.json) { implicit rs =>
            val uuid: String = randomUUID().toString
            rs.body
                .validate[SignUpForm]
                .map { form =>
                    // OKの場合はユーザを登録
                    val user = UsersRow(
                        form.userId,
                        form.userName,
                        form.sex,
                        form.birthday,
                        form.profile,
                        form.createdAt,
                        form.mailAddress,
                        utils.Secure.createHash(form.password),
                        form.profileImage
                    )
                    db.run(Users += user).map { _ =>
                        val cacheResult = form.userId match {
                            case Some(userId) => cache.set(uuid, userId)
                            case _ => Future.successful(Done)
                        }
                        Ok(Json.obj("result" -> "success")).withSession("UUID" -> uuid)
                    }
                }
                .recoverTotal { e =>
                    // NGの場合はバリデーションエラーを返す
                    Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
                }
        }
    }

    def signin: Action[JsValue] = addToken {
        Action.async(parse.json) { implicit rs =>
            val uuid: String = randomUUID().toString

            def formJsResult: JsResult[SignInForm] = rs.body.validate[SignInForm]

            def usersDBIO(form: SignInForm): DBIO[Option[UsersRow]] =
                Users.filter(_.mailAddress === form.mailAddress).result.headOption // 別のファイルに切り出せそうですね

            def futureResult =
                for {
                    form <- Future.fromTry(JsResult.toTry(formJsResult))
                    users <- db.run(usersDBIO(form))
                    userIdOpt = users.flatMap(_.userId)
                    registedPasswordOpt = users.map(_.password)
                    isValidPassword = registedPasswordOpt.exists(p => utils.Secure.authenticate(form.password, p))
                    cacheResult <- userIdOpt match {
                        case Some(userId) => cache.set(uuid, userId)
                        case _ => Future.successful(Done)
                    } if isValidPassword
                } yield {
                    Ok(Json.obj("result" -> "success")).withSession("UUID" -> uuid)
                }

            futureResult.recover {
                case e =>
                    Unauthorized(Json.obj("result" -> "failure"))
            }
        }
    }

    def signout: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Ok(Json.obj("result" -> "failure")))
                case _ => {
                    cache.remove(uuid.getOrElse("None"))
                    Future.successful(Ok(Json.obj("result" -> "success")).withNewSession)
                }
            }
        }
}

object SignUpJsonFormatter {

    case class SignUpForm(userId: Option[Int],
                          userName: String,
                          sex: Int,
                          birthday: Date,
                          profile: Option[String],
                          createdAt: Date,
                          mailAddress: String,
                          password: String,
                          profileImage: String)

    implicit val signupFormReads: Reads[SignUpForm] = Json.reads[SignUpForm]
}

object SignInJsonFormatter {

    case class SignInForm(mailAddress: String, password: String)

    implicit val signinFromReads: Reads[SignInForm] = Json.reads[SignInForm]
}
