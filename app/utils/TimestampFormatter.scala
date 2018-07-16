package utils

import java.sql.Timestamp
import java.text.SimpleDateFormat

import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }

object TimestampFormatter {
  implicit object timestampFormat extends Format[Timestamp] {
    val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    def reads(json: JsValue): JsSuccess[Timestamp] = {
      val str = json.as[String]
      JsSuccess(new Timestamp(format.parse(str).getTime))
    }

    def writes(ts: Timestamp) = JsString(format.format(ts))
  }
}
