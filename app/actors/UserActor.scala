package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import model._
import model.user.{Admin, CommonUser}

import scala.concurrent.duration._

class UserActor (id: String, wsActorRef: ActorRef, dBActor: ActorRef)
  extends Actor with ActorLogging {
  import model._
  import DBActor._

  implicit val timeout: Timeout = Timeout(500 milliseconds)

  override def receive: Receive = anonymousUser

  private def loggedUserReceive: Receive = {
    case ping(seqNr) => wsActorRef ! pong(seqNr)
    case _: subscribe_table.type => wsActorRef ! pong(1)
    case _: unsubscribe_table.type => wsActorRef ! pong(1)
    case add_table(after_id, table) => wsActorRef ! not_authorized
    case update_table(table) => wsActorRef ! not_authorized
    case remove_table(id) => wsActorRef ! not_authorized

    case msg => log.info(s"COKOLWIEK PRZYSZLO $msg, type: ${msg.getClass}")
  }


  private def anonymousUser: Receive = {
    case ping(seqNr) => wsActorRef ! pong(seqNr)

    case login(username, password) =>
      dBActor ! AuthenticateUser(username, utils.PasswordHasher.hashPassword(username, password))

    case UserAuthenticated(user) =>
      wsActorRef ! login_successful(user.role.toString)
      user.role match {
        case CommonUser => context.become(loggedUserReceive)
        case Admin => context.become(loggedUserReceive)
      }

    case UserInvalid =>
      wsActorRef ! login_failed

    case _: Message => wsActorRef ! not_authorized
  }

  override def postStop(): Unit = {
    log.info(s"Stopping actor $self")
  }
}

object UserActor {
  def props(id: String, wsActorRef: ActorRef, supervisorActorRef: ActorRef) = Props(new UserActor(id, wsActorRef, supervisorActorRef))



}
