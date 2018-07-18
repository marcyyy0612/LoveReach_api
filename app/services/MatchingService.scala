package services

import javax.inject.Inject
import models.Tables._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class MatchingService @Inject()(val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[MySQLProfile] {

    def isBothMatching(userId: Int, partnerId: Int): Future[Boolean] =
        db.run(
            MatchRelations
                .filter(_.matchState === 1)
                .filter(_.partnerId === userId)
                .filter(_.userId === partnerId)
                .result
        )
            .map(_.nonEmpty)
}
