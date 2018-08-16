package repositories

import javax.inject.Inject
import models.Tables._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.LocationService
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class LocationJDBC @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                             locationService: LocationService)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[MySQLProfile] {

    case class UsersDistance(userId: Int, distance: Double)

    def locationDBIO(id: Int) = {
        def myLocationDBIO =
            UsersLocation.result.headOption

        def pairLocationDBIO =
            UsersLocation.filterNot(_.userId === id).result

        def resultDBIO =
            for {
                myLocation <- myLocationDBIO
                pairLocation <- pairLocationDBIO
            } yield {
                pairLocation.flatMap(pair => {
                    myLocation.map(my => {
                        UsersDistance(pair.userId, locationService.calcDistance(my.latitude, my.longitude, pair.latitude, pair.longitude))
                    })
                })
            }

        resultDBIO
    }

}
