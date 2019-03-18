package models

import java.sql.Date

import play.api.libs.json.{Json, Reads}

object SignupJsonFormatter {

  case class SignupForm(userId: Option[Int],
                        userName: String,
                        sex: Int,
                        birthday: Date,
                        profile: Option[String],
                        createdAt: Date,
                        mailAddress: String,
                        password: String,
                        profileImage: String)

  implicit val signupFormReads: Reads[SignupForm] = Json.reads[SignupForm]
}

object SigninJsonFormatter {

  case class SigninForm(mailAddress: String, password: String)

  implicit val signinFromReads: Reads[SigninForm] = Json.reads[SigninForm]
}

object LocationJsonFormatter {

  case class LocationForm(latitude: scala.math.BigDecimal, longitude: scala.math.BigDecimal)

  implicit val LocationFormReads: Reads[LocationForm] = Json.reads[LocationForm]
}
