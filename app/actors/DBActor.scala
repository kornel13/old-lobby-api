package actors

import akka.actor.{Actor, ActorLogging}
import akka.pattern._
import javax.inject.Inject
import model.table.TableModel
import model.user.{UserRepository, UserToAdd, UserToRemove, User}
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext


class DBActor @Inject()(userRepository: UserRepository)(implicit ec: ExecutionContext)
  extends Actor with InjectedActorSupport with ActorLogging {
  import DBActor._
  import model.user.UserMapper._

  log.info("DBActor Created")

  override def receive: Receive = {
    case AuthenticateUser(userName, passwordHash) =>
      val dbFuture = userRepository.getUser(userName).map(
        _.map(userModelDb =>
          if(userModelDb.passwordHash == passwordHash)
            UserAuthenticated(toUser(userModelDb))
          else
            UserInvalid)
          .getOrElse(UserInvalid)
      )
      pipe(dbFuture) to sender()

    case ListUsers =>
      val dbFuture = userRepository.listUsers.map(_.map(toUser))
      pipe(dbFuture) to sender()

    case AddUser(addUser: UserToAdd) =>

      val dbFuture = userRepository.addUser(toDb(addUser))
      pipe(dbFuture) to sender()

    case RemoveUser(userToRemove) =>
      val dbFuture = userRepository.removeUser(userToRemove.userName)
      pipe(dbFuture) to sender()
  }

}

object DBActor {

  case object ListUsers
  final case class RemoveUser(userToRemove: UserToRemove)
  final case class AddUser(addUser: UserToAdd)
  final case class AuthenticateUser(userName: String, passwordHash: String)

  trait AuthenticationResponse
  case object UserInvalid extends AuthenticationResponse
  final case class UserAuthenticated(user: User) extends AuthenticationResponse

  final case class AddTable(tableModel: TableModel)
  final case class RemoveTable(tableModel: TableModel)
  final case class UpdateTable(tableModel: TableModel)

  case object OperationSucceeded
  case object OperationFailed


}
