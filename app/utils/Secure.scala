package utils

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class Secure {}

object Secure {
  val bcrypt = new BCryptPasswordEncoder()

  def createHash(password: String): String = bcrypt.encode(password)

  def authenticate(password: String, registedPassword: String): Boolean =
    bcrypt.matches(password, registedPassword)
}
