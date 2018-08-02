package controllers

import controllers.ShopsJsonFormatter._
import javax.inject.Inject
import models.Tables
import models.Tables._
import play.api.cache._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import services.LocationService
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ShopsController @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck,
                                val dbConfigProvider: DatabaseConfigProvider,
                                locationService: LocationService,
                                cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    case class NearShops(shopId: Int, shopName: String, shopUrl: String, shopDis: Double)

    // 登録している店のリスト取得
    def showShopsList: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ => {
                    def myLocationDBIO(userId: Int) =
                        UsersLocation.filter(_.userId === userId.bind).result.headOption

                    def shopsDBIO =
                        Shops.sortBy(_.shopId).result

                    def resultDBIO =
                        for {
                            userIdOpt <- DBIO.from(cache.get[Int](uuid.getOrElse("None")))
                            userId <- userIdOpt match {
                                case Some(userId) =>
                                    DBIO.successful(userId)
                                case _ =>
                                    DBIO.failed(new Exception("cache not found"))
                            }
                            user <- myLocationDBIO(userId)
                            shops <- shopsDBIO
                        } yield {
                            val shopsList =
                                shops.flatMap(shop => {
                                user.map(user => {
                                    NearShops(
                                        shop.shopId.get,
                                        shop.shopName,
                                        shop.shopUrl,
                                        locationService.calcDistance(
                                            user.latitude,
                                            user.longitude,
                                            shop.shopLat,
                                            shop.shopLng
                                        )
                                    )
                                })
                            })
                            val nearShopsList =
                                shopsList.filter(_.shopDis < 5) // 現在地から5km未満の店を返す
                            Ok(Json.toJson("SHOPS" -> nearShopsList))
                        }

                    db.run(resultDBIO)
                        .recover {
                            case e =>
                                BadRequest(Json.obj("result" -> "failure"))
                        }
                }
            }
        }

}

object ShopsJsonFormatter {

    implicit val shopsRowWritesFormat: Writes[Tables.ShopsRow] = (shop: ShopsRow) => {
        Json.obj("SHOP_ID" -> shop.shopId, "SHOP_NAME" -> shop.shopName, "SHOP_URL" -> shop.shopUrl)
    }
}
