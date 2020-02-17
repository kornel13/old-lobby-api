package actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import javax.inject.Inject
import model.table.{TableModel, TableRepository}
import model.user.{User, UserRepository, UserToAdd, UserToRemove}
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.{ExecutionContext, Future}

class DBActor @Inject()(userRepository: UserRepository, tableRepository: TableRepository)(implicit ec: ExecutionContext)
    extends Actor
    with InjectedActorSupport
    with ActorLogging {

  import DBActor._
  import model.user.UserMapper
  import model.table.TableModelMapper

  log.info("DBActor Created")

  override def receive: Receive = userReceive.orElse(tableReceive)

  private def userReceive: Receive = {
    case AuthenticateUser(userName, passwordHash) =>
      pipeFuture(
        sender(),
        userRepository
          .getUser(userName)
          .map(
            _.map(userModelDb => {
              if (userModelDb.passwordHash == passwordHash)
                UserAuthenticated(UserMapper.toUser(userModelDb))
              else
                UserInvalid
            }).getOrElse {
              log.debug(s"Couldn't find $userName")
              UserInvalid
            }
          )
      )
    case Subscribe(userName) =>
      pipeFuture(sender(), {
        for {
          _ <- userRepository.subscribe(userName, subscription = true)
          list <- tableRepository.list
        } yield ListedTables(list.map(TableModelMapper.toMsg))
      })

    case Unsubscribe(userName) => userRepository.subscribe(userName, subscription = false)

    case ListUsers => pipeFuture(sender(), userRepository.listUsers.map(_.map(UserMapper.toUser)))

    case AddUser(addUser: UserToAdd) => pipeFuture(sender(), userRepository.addUser(UserMapper.toDb(addUser)))

    case RemoveUser(userToRemove) => pipeFuture(sender(), userRepository.removeUser(userToRemove.userName))
  }

  private val tableReceive: Receive = {
    case ListTables =>
      log.info("Got list tables")
      pipeFuture(sender(), tableRepository.list.map(_.map(TableModelMapper.toMsg)))

    case mod @ AddTable(tableModel, afterId) =>
      pipeFuture(sender(), handleDbResponse(mod, tableRepository.add(TableModelMapper.toDb(tableModel), afterId)))

    case mod @ RemoveTable(id) => pipeFuture(sender(), handleDbResponse(mod, tableRepository.remove(id)))

    case mod @ UpdateTable(tableModel) =>
      pipeFuture(sender(), handleDbResponse(mod, tableRepository.update(TableModelMapper.toDb(tableModel))))
  }

  private def pipeFuture[R](senderRef: ActorRef, future: => Future[R]) = {
    pipe(future) to senderRef
  }

  private def handleDbResponse(
    modification: TableModification,
    future: => Future[Int]
  ): Future[TableOperationResult] = {
    future
      .map {
        case 1    => OperationSucceeded(modification)
        case rows => OperationFailed(modification, new Exception(s"Not expected rows abount updated [$rows]"))
      }
      .recover {
        case th: Throwable => OperationFailed(modification, th)
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

  case class Subscribe(userName: String)
  case class Unsubscribe(userName: String)

  case object ListTables
  final case class ListedTables(tables: Seq[TableModel])

  sealed trait TableModification
  final case class AddTable(tableModel: TableModel, afterId: Long) extends TableModification
  final case class RemoveTable(id: Long) extends TableModification
  final case class UpdateTable(tableModel: TableModel) extends TableModification

  sealed trait TableOperationResult
  final case class OperationSucceeded(modification: TableModification) extends TableOperationResult
  final case class OperationFailed(modification: TableModification, throwable: Throwable) extends TableOperationResult

}
