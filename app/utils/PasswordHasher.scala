package utils

import com.roundeights.hasher.Implicits._

object PasswordHasher {
  def hashPassword(username: String, password: String): String = password.salt(username).sha256.hex
}
