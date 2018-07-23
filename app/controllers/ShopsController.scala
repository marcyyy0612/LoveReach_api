package controllers

import controllers.ShopsJsonFormatter._
import javax.inject.Inject
import models.Tables
import models.Tables._
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.filters.csrf._
import slick.jdbc.MySQLProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ShopsController @Inject()(checkToken: CSRFCheck,
                                   val dbConfigProvider: DatabaseConfigProvider,
                                   cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
        with HasDatabaseConfigProvider[MySQLProfile] {

    // 登録している店のリスト取得
    def showShopsList: Action[AnyContent] =
        Action.async { implicit rs =>
            val uuid = rs.session.get("UUID")
            uuid match {
                case None => Future.successful(Unauthorized(Json.obj("result" -> "failure")))
                case _ => {
                    val shopsDBIO =
                        Shops.sortBy(_.shopId).result

                    db.run(shopsDBIO).map(shops => {
                        Ok(Json.obj("SHOPS" -> shops))
                    }).recover {
                        case e =>
                            BadRequest(Json.obj("result" -> "failure"))
                    }
                }
            }
        }

}

object ShopsJsonFormatter {

    implicit val shopsRowWritesFormat: Writes[Tables.ShopsRow] = (shop: ShopsRow) => {
        Json.obj(
            "SHOP_ID" -> shop.shopId,
            "SHOP_NAME" -> shop.shopName,
            "SHOP_URL" -> shop.shopUrl
        )
    }
}
