package model.user

import io.scalaland.chimney.dsl._
import julienrf.json.derived.flat
import play.api.libs.json.{Format, Json, __}

sealed trait UserRole
object UserRole {
  implicit val format: Format[UserRole] = flat.oformat[UserRole]((__ \ "$type").format[String])
}
case object CommonUser extends UserRole
case object Admin extends UserRole

case class User(username: String, role: UserRole)
object User {
  implicit val format: Format[User] = Json.format
}

case class UserToAddNotHashedPassword(userName: String, passwordNotHashed: String, role: UserRole = CommonUser)
object UserToAddNotHashedPassword {
  implicit val format: Format[UserToAddNotHashedPassword] = Json.format
}

case class UserToAdd(userName: String, passwordHash: String, role: UserRole = CommonUser)
object UserToAdd {
  implicit val format: Format[UserToAdd] = Json.format
}

case class UserToRemove(userName: String)
object UserToRemove {
  implicit val format: Format[UserToRemove] = Json.format
}


object UserMapper {
  def toDb(model: UserToAdd): UserModelDb =
    model.into[UserModelDb].withFieldComputed(_.role, _.role match {
      case CommonUser => "user"
      case Admin => "admin"
    }).transform

  def toUser(model: UserModelDb): User = model.into[User]
    .withFieldRenamed(_.userName, _.username)
    .withFieldComputed(_.role, _.role match {
    case "user" => CommonUser
    case "admin" => Admin
  }).transform

  def toHashedPassword(model: UserToAddNotHashedPassword): UserToAdd = model.into[UserToAdd]
    .withFieldComputed(_.passwordHash, m => utils.PasswordHasher.hashPassword(m.userName, m.passwordNotHashed))
    .transform
}