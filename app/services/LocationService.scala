package services

import javax.inject.Inject
import models.Tables._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import scala.math._

class LocationService @Inject()(val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[MySQLProfile] {

    case class UsersDistance(userId: Int, distance: Double)

    def calcDistance(lat1: BigDecimal, lng1: BigDecimal, lat2: BigDecimal, lng2: BigDecimal): Double = {
        val r = 6378.137

        val myLat = lat1.toDouble * Pi / 180
        val myLng = lng1.toDouble * Pi / 180
        val pairLat = lat2.toDouble * Pi / 180
        val pairLng = lng2.toDouble * Pi / 180

        //        println(r * acos(sin(myLat) * sin(pairLat) + cos(myLat) * cos(pairLat) * cos(pairLng - myLng)))
        r * acos(sin(myLat) * sin(pairLat) + cos(myLat) * cos(pairLat) * cos(pairLng - myLng))
    }

    def myLocationDBIO(id: Int) =
        UsersLocation.result.headOption

    def pairLocationDBIO(id: Int) =
        UsersLocation.filterNot(_.userId === id).result

    def resultDBIO(id: Int) =
        for {
            myLocation <- myLocationDBIO(id)
            pairLocation <- pairLocationDBIO(id)
        } yield {
            pairLocation.flatMap(pair => {
                myLocation.map(my => {
                    UsersDistance(pair.userId, calcDistance(my.latitude, my.longitude, pair.latitude, pair.longitude))
                })
            })
        }
}
