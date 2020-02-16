package actors

import actors.UserActor.SendUpdateToSocket
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import model._
import model.user.{Admin, CommonUser}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class UserActor(id: String, wsActorRef: ActorRef, dBActor: ActorRef)
  extends Actor with ActorLogging {

  import model._
  import DBActor._

  implicit val timeout: Timeout = Timeout(1000 milliseconds)
  implicit val ec: ExecutionContext = context.system.dispatcher

  override def receive: Receive = anonymousUserReceive

  private def anonymousUserReceive: Receive =
    pingPongReceive orElse
      loggingReceive orElse
      unauthorizedReceive

  private def adminReceive: Receive =
    pingPongReceive orElse
      alreadyLoggedReceive orElse
      subscriptionReceive orElse
      updateReceive orElse
      tablesModificationReceive

  private def loggedUserReceive: Receive = pingPongReceive orElse
    alreadyLoggedReceive orElse
    subscriptionReceive orElse
    updateReceive orElse
    unauthorizedReceive

  private def subscriptionReceive: Receive = {
    case _: subscribe_table.type => wsActorRef ! not_authorized
    case _: unsubscribe_table.type => wsActorRef ! not_authorized
  }

  private def updateReceive: Receive = {
    case SendUpdateToSocket(update) => wsActorRef ! update
  }

  private def pingPongReceive: Receive = {
    case ping(seqNr) => wsActorRef ! pong(seqNr)
  }

  private def alreadyLoggedReceive: Receive = {
    case login(_, _) => wsActorRef ! already_logged
  }

  private def loggingReceive: Receive = {
    case login(username, password) =>
      dBActor ! AuthenticateUser(username, utils.PasswordHasher.hashPassword(username, password))

    case UserAuthenticated(user) =>
      wsActorRef ! login_successful(user.role.toString)
      log.info(s"LOGGED as ${user.role}")
      user.role match {
        case CommonUser => context.become(loggedUserReceive)
        case Admin => context.become(adminReceive)
      }

    case UserInvalid =>
      wsActorRef ! login_failed
  }

  private def unauthorizedReceive: Receive = {
    case _: Message => wsActorRef ! not_authorized
  }

  private def tablesModificationReceive: Receive = {
    case add_table(after_id, table) =>
      log.info(s"RECEIVED add_table $after_id $table")
      val resultF = (dBActor ? AddTable(table)).mapTo[TableOperationResult]
      resultF.map {
        case OperationSucceeded => context.parent ! UsersSupervisor.UpdateNotification(table_added(after_id, table))
        case OperationFailed(throwable) => log.error(s"Couldn't add a table: ${throwable.getMessage}")
      }

    case update_table(table) =>
      val resultF = (dBActor ? UpdateTable(table)).mapTo[TableOperationResult]
      resultF.map {
        case OperationSucceeded => context.parent ! UsersSupervisor.UpdateNotification(update_table(table))
        case OperationFailed(throwable) => log.error(s"Couldn't add a table: ${throwable.getMessage}")
      }

    case remove_table(id) =>
      val resultF = (dBActor ? RemoveTable(id)).mapTo[TableOperationResult]
      resultF.map {
        case OperationSucceeded => context.parent ! UsersSupervisor.UpdateNotification(remove_table(id))
        case OperationFailed(throwable) => log.error(s"Couldn't add a table: ${throwable.getMessage}")
      }
  }

  override def postStop(): Unit = {
    log.info(s"Stopping actor $self")
  }
}

object UserActor {
  def props(id: String, wsActorRef: ActorRef, supervisorActorRef: ActorRef): Props = Props(new UserActor(id, wsActorRef, supervisorActorRef))

  case class SendUpdateToSocket(update: Message)

}
