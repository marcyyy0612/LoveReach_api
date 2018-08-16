package controllers

import javax.inject.Inject
import play.api.cache._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import repositories.ShopsJDBC
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

case class NearShops(shopId: Int, shopName: String, shopUrl: String, shopDis: Double)

class ShopsController @Inject()(cache: AsyncCacheApi,
                                checkToken: CSRFCheck,
                                val dbConfigProvider: DatabaseConfigProvider,
                                shopsJDBC: ShopsJDBC,
                                cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    // 登録している店のリスト取得
    def showShopsList: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ =>
                    db.run(shopsJDBC.nearShopsDBIO(uuid))
                        .recover {
                            case e =>
                                BadRequest(Json.obj("result" -> "failure"))
                        }
            }
        }
}

object ShopsJsonFormatter {
    implicit val shopsRowWritesFormat: Writes[NearShops] = (shop: NearShops) => {
        Json.obj(
            "SHOP_ID" -> shop.shopId,
            "SHOP_NAME" -> shop.shopName,
            "SHOP_URL" -> shop.shopUrl,
            "SHOP_DIS" -> shop.shopDis
        )
    }
}
