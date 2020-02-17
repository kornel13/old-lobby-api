package actors

import actors.UserActor.{SendUpdateToSocket, SocketClosedSuccessfully, SocketClosedWithFailure}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import model._
import model.user.{Admin, CommonUser, User}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class UserActor(id: String, wsActorRef: ActorRef, dbActorRef: ActorRef) extends Actor with ActorLogging {

  import model._
  import DBActor._

  implicit val timeout: Timeout = Timeout(1000 milliseconds)
  implicit val ec: ExecutionContext = context.system.dispatcher

  override def receive: Receive = anonymousUserReceive

  override def postStop(): Unit = {
    log.info(s"Stopping actor $self")
  }

  private def anonymousUserReceive: Receive =
    pingPongReceive.orElse(loggingReceive).orElse(unauthorizedReceive).orElse(socketFlowReceive)

  private def adminReceive(user: User): Receive =
    pingPongReceive
      .orElse(alreadyLoggedReceive)
      .orElse(subscriptionReceive(user))
      .orElse(updateReceive(user))
      .orElse(tablesModificationReceive)
      .orElse(socketFlowReceive)

  private def loggedUserReceive(user: User): Receive =
    pingPongReceive
      .orElse(alreadyLoggedReceive)
      .orElse(subscriptionReceive(user))
      .orElse(updateReceive(user))
      .orElse(unauthorizedReceive)
      .orElse(socketFlowReceive)

  private def subscriptionReceive(user: User): Receive = {
    case _: subscribe_tables.type =>
      if (user.subscription)
        wsActorRef ! already_subscribed
      else {
        val listedTablesF = (dbActorRef ? Subscribe(user.userName)).mapTo[ListedTables].map(tl => table_list(tl.tables))
        pipe(listedTablesF) to wsActorRef
        setBehavior(user.copy(subscription = true))
      }

    case _: unsubscribe_tables.type =>
      if (!user.subscription) {
        wsActorRef ! already_unsubscribed
      } else {
        dbActorRef ! Unsubscribe(user.userName)
        setBehavior(user.copy(subscription = false))
      }
  }

  private def updateReceive(user: User): Receive = {
    case SendUpdateToSocket(update) => if (user.subscription) wsActorRef ! update
  }

  private def pingPongReceive: Receive = {
    case ping(seqNr) => wsActorRef ! pong(seqNr)
  }

  private def alreadyLoggedReceive: Receive = {
    case login(_, _) => wsActorRef ! already_logged
  }

  private def loggingReceive: Receive = {
    case login(username, password) =>
      dbActorRef ! AuthenticateUser(username, utils.PasswordHasher.hashPassword(username, password))

    case UserAuthenticated(user) =>
      wsActorRef ! login_successful(user.role.toString)
      log.info(s"LOGGED as $user")
      setBehavior(user)

    case UserInvalid =>
      wsActorRef ! login_failed
  }

  private def unauthorizedReceive: Receive = {
    case _: Message => wsActorRef ! not_authorized
  }

  private def tablesModificationReceive: Receive = {
    case add_table(after_id, table) => dbActorRef ! AddTable(table, after_id)

    case update_table(table) => dbActorRef ! UpdateTable(table)

    case remove_table(id) => dbActorRef ! RemoveTable(id)

    case OperationSucceeded(modification) =>
      val updateMessage = modification match {
        case AddTable(tableModel, afterId) => table_added(afterId, tableModel)
        case RemoveTable(id)               => table_removed(id)
        case UpdateTable(tableModel)       => table_updated(tableModel)
      }
      context.parent ! UsersSupervisor.UpdateNotification(updateMessage)

    case OperationFailed(modification, throwable) =>
      log.error(s"Couldn't remove a table: ${throwable.getMessage}")
      modification match {
        case AddTable(tableModel, _) => wsActorRef ! addition_failed(tableModel.id)
        case RemoveTable(id)         => wsActorRef ! removal_failed(id)
        case UpdateTable(tableModel) => wsActorRef ! update_failed(tableModel.id)
      }
  }

  private def socketFlowReceive: Receive = {
    case SocketClosedSuccessfully => context.stop(self)
    case SocketClosedWithFailure(throwable: Throwable) =>
      log.error(s"Soxket closed with a failure ${throwable.getMessage}")
      context.stop(self)
  }

  private def setBehavior(user: User): Unit = user.role match {
    case CommonUser => context.become(loggedUserReceive(user))
    case Admin      => context.become(adminReceive(user))
  }
}

object UserActor {
  def props(id: String, wsActorRef: ActorRef, supervisorActorRef: ActorRef): Props =
    Props(new UserActor(id, wsActorRef, supervisorActorRef))

  case class SendUpdateToSocket(update: Message)
  case object SocketClosedSuccessfully
  case class SocketClosedWithFailure(throwable: Throwable)

}
