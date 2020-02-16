package utils

import com.roundeights.hasher.Implicits._

object PasswordHasher {
  def hashPassword(username: String, password: String): String = {
    println(s"FOR U: $username and p: $password hash is ${password.salt(username).sha256.hex}")
    password.salt(username).sha256.hex
  }

}
