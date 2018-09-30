package repositories

import controllers.LocationJsonFormatter.LocationForm
import javax.inject.Inject
import models.Tables._
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{ DatabaseConfigProvider, HasDatabaseConfigProvider }
import play.api.libs.json._
import play.api.mvc.{ AbstractController, ControllerComponents }
import play.filters.csrf._
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class ApplicationRepository @Inject()(cache: AsyncCacheApi,
                                addToken: CSRFAddToken,
                                val dbConfigProvider: DatabaseConfigProvider,
                                cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[MySQLProfile] {

  def insertLocationDBIO(uuid: Option[String], form: LocationForm) = {
    def usersLocationDBIO(id: Int) =
      UsersLocation.filter(_.userId === id).map(_.userId).result.headOption

    def insertLocationDBIO(id: Int) = {
      val location = UsersLocationRow(id, form.latitude, form.longitude)
      UsersLocation += location
    }

    def updateLocationDBIO(id: Int) =
      UsersLocation
        .filter(_.userId === id)
        .map(location => (location.userId, location.latitude, location.longitude))
        .update(id, form.latitude, form.longitude)

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) =>
            DBIO.successful(userId)
          case _ => DBIO.failed(new Exception("cache not found"))
        }
        result <- usersLocationDBIO(userId).flatMap {
          case Some(maybeInt) => updateLocationDBIO(userId)
          case _              => insertLocationDBIO(userId)
        }
      } yield {
        Ok(Json.obj("result" -> "success"))
      }

    resultDBIO
  }

  def siginoutDBIO(uuid: Option[String]) = {
    def deleteLoginDBIO(id: Int) = LoginStatuses.filter(_.userId === id).delete

    def deleteLocationDBIO(id: Int) = UsersLocation.filter(_.userId === id).delete

    def resultDBIO =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) =>
            cache.remove(uuid.getOrElse("None"))
            DBIO.successful(userId)
          case _ => DBIO.failed(new Exception("cache not found"))
        }
        resultDeleteLogin <- deleteLoginDBIO(userId)
        resultDeleteLocation <- deleteLocationDBIO(userId)
      } yield Ok(Json.obj("result" -> "success")).withNewSession

    resultDBIO
  }
}
