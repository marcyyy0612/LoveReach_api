package services
import javax.inject.Inject
import models.SessionUsers
import play.api.cache.AsyncCacheApi
import play.api.mvc.RequestHeader
import play.filters.csrf.CSRFCheck
import repositories.CacheRepository

import scala.concurrent.ExecutionContext

class SessionService @Inject()(cache: AsyncCacheApi,
                               checkToken: CSRFCheck,
                               sessionUsers: SessionUsers,
                               cacheRepo: CacheRepository)(implicit ec: ExecutionContext) {

  def getUserCache(req: RequestHeader) = {
    val userIdOpt = for {
      sessionId <- req.session.get("UUID")
      userId <- cacheRepo.getUserId(sessionId)
    } yield userId

    userIdOpt match {
      case Some(userId) => true
      case None => false
    }
  }
}
