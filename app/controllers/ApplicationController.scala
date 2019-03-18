package controllers

import java.sql.Timestamp
import java.util.UUID._

import akka.Done
import javax.inject.Inject
import models.LocationJsonFormatter.LocationForm
import models.SigninJsonFormatter.SigninForm
import models.SignupJsonFormatter.SignupForm
import models.Tables._
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json._
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.filters.csrf._
import repositories.ApplicationJDBC
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject()(cache: AsyncCacheApi,
                                      addToken: CSRFAddToken,
                                      applicationJDBC: ApplicationJDBC,
                                      val dbConfigProvider: DatabaseConfigProvider,
                                      cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  def signup: Action[JsValue] = addToken {
    Action.async(parse.json) { implicit rs =>
      val uuid: String = randomUUID().toString
      rs.body.validate[SignupForm].map { form =>
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
          Ok(Json.obj("result" -> "success")).withSession("UUID" -> uuid)
        }
      }.recoverTotal { e =>
        // NGの場合はバリデーションエラーを返す
        Future.successful(BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e))))
      }
    }
  }

  def signin: Action[JsValue] = addToken {
    Action.async(parse.json) { implicit rs =>
      val uuid: String = randomUUID().toString

      def formJsResult: JsResult[SigninForm] = rs.body.validate[SigninForm]

      def usersDBIO(form: SigninForm): DBIO[Option[UsersRow]] =
        Users.filter(_.mailAddress === form.mailAddress.bind).result.headOption // 別のファイルに切り出せそうですね

      def loginDBIO(id: Int) = {
        val datetime = new Timestamp(System.currentTimeMillis())
        LoginStatuses.map(user => (user.userId, user.loginDatetime)) += (id, datetime)
      }

      def futureResult =
        for {
          form <- Future.fromTry(JsResult.toTry(formJsResult))
          users <- db.run(usersDBIO(form))
          userIdOpt = users.flatMap(_.userId)
          registedPasswordOpt = users.map(_.password)
          isValidPassword = registedPasswordOpt.exists(p => utils.Secure.authenticate(form.password, p))
          cacheResult <- userIdOpt match {
            case Some(userId) =>
              cache.set(uuid, userId)
              db.run(loginDBIO(userId)) // ログインデータベースに挿入
            case _ => Future.successful(Done)
          } if isValidPassword
        } yield Ok(Json.obj("result" -> "success")).withSession("UUID" -> uuid)

      futureResult.recover {
        case e => Unauthorized(Json.obj("result" -> "failure"))
      }
    }
  }

  def insertLocation[JsValue] =
    Action.async(parse.json) { implicit rs =>
      rs.body.validate[LocationForm].map(form => {
        val uuid = rs.session.get("UUID")
        uuid match {
          case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
          case _ =>
            db.run(applicationJDBC.insertLocationDBIO(uuid, form)).recover {
              case e => BadRequest(Json.obj("result" -> "failure"))
            }
        }
      }).recoverTotal { e =>
        // NGの場合はバリデーションエラーを返す
        Future.successful(BadRequest(Json.obj("validate result" -> "failure", "error" -> JsError.toJson(e))))
      }
    }

  def signout: Action[AnyContent] =
    Action.async { implicit rs =>
      val uuid = rs.session.get("UUID")
      uuid match {
        case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
        case _ =>
          db.run(applicationJDBC.siginoutDBIO(uuid)).recover {
            case e => BadRequest(Json.obj("result" -> "failure"))
          }
      }
    }

  def isAlreadySingin: Action[AnyContent] =
    Action.async { implicit rs =>
      val uuid = rs.session.get("UUID")
      uuid match {
        case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")).withNewSession)
        case _ =>
          cache.get[Int](uuid.getOrElse("None")).flatMap {
            case Some(id) => Future.successful(Ok(Json.obj("result" -> "success")))
            case _ => Future.successful(Unauthorized(Json.obj("result" -> "failure")).withNewSession)
          }
      }
    }
}
