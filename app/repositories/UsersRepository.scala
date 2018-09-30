package repositories

import controllers.UsersJsonFormatter._
import javax.inject.Inject
import models.SessionUsers
import models.Tables._
import play.api.cache._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class UsersRepository @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck,
                                locationJDBC: LocationRepository,
                                val dbConfigProvider: DatabaseConfigProvider,
                                cc: ControllerComponents)
                               (implicit
                                ec: ExecutionContext,
                                ctx: SessionUsers)
  extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  val Male = Some(1)
  val Female = Some(2)
  val Diver = Some(3)
  val LIKE = 1

  def signinUserDBIO(uuid: Option[String]) = {
    def userDBIO(userId: Int) =
      Users.filter(_.userId === userId.bind).result

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        user <- userDBIO(userId)
      } yield Ok(Json.obj("Me" -> user))

    resultDBIO
  }

  def usersListDBIO(uuid: Option[String]) = {
    def myInfoDBIO(id: Int) =
      Users.filter(_.userId === id).map(_.sex).result.headOption

    def usersDBIO(id: Int) =
      Users.filter(_.userId =!= id).result

    def selectedUsersDBIO(id: Int) =
      MatchRelations.filter(_.userId === id).result

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        distance <- locationJDBC.locationDBIO(userId)
        nearUsers = distance.filter(_.distance < 5) //ログインユーザの中で自分との距離が5km 未満のユーザのみ
        users <- usersDBIO(userId) //自分以外のユーザ
        selectedUsers <- selectedUsersDBIO(userId) //like, nopeの選択をしたユーザ
        nonSelectedUsers = users.filterNot(user => { //未選択のユーザ
          selectedUsers.exists(selectedUser => user.userId.contains(selectedUser.partnerId))
        })
        searchGender <- myInfoDBIO(userId) //自分が選択した興味のある性別
        resultUsers = searchGender match { //選択した興味のある性別によって選別
          case Male   => nonSelectedUsers.filter(_.sex == 2)
          case Female => nonSelectedUsers.filter(_.sex == 1)
          case Diver  => nonSelectedUsers
          case _      => Nil
        }

        responseUsers = nearUsers.flatMap(user => {
          resultUsers.filter(_.userId.getOrElse("None") == user.userId)
        })
      } yield Ok(Json.obj("USERS" -> responseUsers))

    resultDBIO
  }

  def updateDBIO(uuid: Option[String], form: UserForm) = {
    def userDBIO(id: Int) =
      Users
        .filter(_.userId === id)
        .map(user => (user.userName, user.sex, user.profile))
        .update(form.userName, form.sex, form.profile)

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        user <- userDBIO(userId)
      } yield Ok(Json.obj("result" -> "success"))

    resultDBIO
  }

  def updateImgDBIO(uuid: Option[String], form: UserImgForm) = {
    def userDBIO(id: Int) =
      Users
        .filter(_.userId === id)
        .map(user => user.profileImage)
        .update(form.profileImage)

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        user <- userDBIO(userId)
      } yield Ok(Json.obj("result" -> "success"))

    resultDBIO
  }

  def deleteDBIO(uuid: Option[String]) = {
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

    def resultDBIO =
      for {
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

    resultDBIO
  }

  def matchingUserDBIO(uuid: Option[String]) = {
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

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
        users <- listFollowerDBIO(userId)
        partners <- listFolloweeDBIO(userId)
        matcherUsers = users.filter(user => partners.exists(p => user.userId.contains(p.userId)))
      } yield Ok(Json.obj("MatchUser" -> matcherUsers))

    resultDBIO
  }
}
