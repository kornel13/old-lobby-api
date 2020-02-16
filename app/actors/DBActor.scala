package actors

import akka.actor.{Actor, ActorLogging}
import akka.pattern._
import javax.inject.Inject
import model.table.TableModel
import model.user.{UserRepository, UserToAdd, UserToRemove}
import play.api.libs.concurrent.InjectedActorSupport
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext


class DBActor @Inject()(userRepository: UserRepository)(implicit ec: ExecutionContext)
  extends Actor with InjectedActorSupport with ActorLogging {
  import DBActor._
  import model.user.UserMapper._
  import model._

  log.info("DBActor Created")

  override def receive: Receive = {
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
  import model._

  case object ListUsers
  final case class RemoveUser(userToRemove: UserToRemove)
  final case class AddUser(addUser: UserToAdd)

  final case class AddTable(tableModel: TableModel)
  final case class RemoveTable(tableModel: TableModel)
  final case class UpdateTable(tableModel: TableModel)

  case object OperationSucceeded
  case object OperationFailed


}
