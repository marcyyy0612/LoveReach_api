package repositories

import javax.inject.Inject
import play.api.cache._
import play.api.db.slick._
import play.filters.csrf._
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class CacheRepository @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile]{

  def getUserId(sessionId: String) = {
    val userId =
      for {
        userIdOpt <- DBIO.from(cache.get[Int](sessionId.getOrElse("None")))
        userId <- userIdOpt match {
          case Some(userId) => DBIO.successful(userId)
          case _            => DBIO.failed(new Exception("cache not found"))
        }
      } yield userId
    userId
  }
}
