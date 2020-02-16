package actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import javax.inject.Inject
import model.table.{TableModel, TableRepository}
import model.user.{User, UserRepository, UserToAdd, UserToRemove}
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.{ExecutionContext, Future}


class DBActor @Inject()(userRepository: UserRepository, tableRepository: TableRepository)(implicit ec: ExecutionContext)
  extends Actor with InjectedActorSupport with ActorLogging {

  import DBActor._
  import model.user.UserMapper
  import model.table.TableModelMapper

  log.info("DBActor Created")

  override def receive: Receive = userReceive orElse tableReceive

  private def userReceive: Receive = {
    case AuthenticateUser(userName, passwordHash) => pipeFuture(sender(), {
      log.debug(s"Loggin $userName ph: $passwordHash")
      userRepository.getUser(userName).map(
        _.map(userModelDb => {
          log.debug(s"Received ph: ${userModelDb.passwordHash} vs ${passwordHash} => are equal ${userModelDb.passwordHash == passwordHash}")

          if (userModelDb.passwordHash == passwordHash)
            UserAuthenticated(UserMapper.toUser(userModelDb))
          else
            UserInvalid
        })
          .getOrElse({
            log.debug(s"couldnt find ${userName}"); UserInvalid
          })
      )
    })

    case ListUsers => pipeFuture(sender(), userRepository.listUsers.map(_.map(UserMapper.toUser)))

    case AddUser(addUser: UserToAdd) => pipeFuture(sender(), userRepository.addUser(UserMapper.toDb(addUser)))

    case RemoveUser(userToRemove) => pipeFuture(sender(), userRepository.removeUser(userToRemove.userName))
  }

  private val tableReceive: Receive = {
    case ListTables =>
      log.info("Got list tables")
      pipeFuture(sender(), tableRepository.list.map(_.map(TableModelMapper.toMsg)))

    case AddTable(tableModel) => pipeFuture(sender(),
      {
        log.info(s"RECEIVED DBActor add ${tableModel}")
        handleDbResponse(tableRepository.add(TableModelMapper.toDb(tableModel)))
      }
    )

    case RemoveTable(id) => pipeFuture(sender(),
      handleDbResponse(tableRepository.remove(id))
    )

    case UpdateTable(tableModel) => pipeFuture(sender(),
      handleDbResponse(tableRepository.update(TableModelMapper.toDb(tableModel)))
    )
  }

  private def pipeFuture[R](senderRef: ActorRef, future: => Future[R]) = {
    pipe(future) to senderRef
  }

  private def handleDbResponse(future: => Future[Int]): Future[TableOperationResult] = {
    future.map {
      case 1 => OperationSucceeded
      case rows => OperationFailed(new Exception(s"Not expected rows abount updated [$rows]"))
    }.recover {
      case th: Throwable => OperationFailed(th)
    }
  }

}

object DBActor {

  case object ListUsers

  final case class RemoveUser(userToRemove: UserToRemove)

  final case class AddUser(addUser: UserToAdd)

  final case class AuthenticateUser(userName: String, passwordHash: String)

  sealed trait AuthenticationResponse

  case object UserInvalid extends AuthenticationResponse

  final case class UserAuthenticated(user: User) extends AuthenticationResponse

  case object ListTables

  final case class AddTable(tableModel: TableModel)

  final case class RemoveTable(id: Long)

  final case class UpdateTable(tableModel: TableModel)

  sealed trait TableOperationResult

  case object OperationSucceeded extends TableOperationResult

  final case class OperationFailed(throwable: Throwable) extends TableOperationResult


}
