package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import model._

class UserActor (id: String, wsActorRef: ActorRef, supervisorActorRef: ActorRef)
  extends Actor with ActorLogging {
  import model._

  override def receive: Receive = loggedUserReceive

  private def loggedUserReceive: Receive = {
    case ping(seqNr) => wsActorRef ! pong(seqNr)
    case login(username, password) => wsActorRef ! login_failed
    case _: subscribe_table.type => wsActorRef ! pong(1)
    case add_table(after_id, table) => wsActorRef ! not_authorized
    case update_table(table) => wsActorRef ! not_authorized
    case remove_table(id) => wsActorRef ! not_authorized

    case msg => log.info(s"COKOLWIEK PRZYSZLO $msg, type: ${msg.getClass}")
  }

  private def anonymousUser: Receive = {
    case ping(seqNr) => wsActorRef ! pong(seqNr)
    case login(username, password) => wsActorRef ! login_failed
    case _: Message => wsActorRef ! not_authorized
  }

//  private def adminUserReceive: Receive  = {
//    case _ => "not implemented"
//  }

  override def postStop(): Unit = {
    log.info(s"Stopping actor $self")
  }
}

object UserActor {
  def props(id: String, wsActorRef: ActorRef, supervisorActorRef: ActorRef) = Props(new UserActor(id, wsActorRef, supervisorActorRef))


}
