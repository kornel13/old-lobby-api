package actors

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.{ask, pipe}
import akka.stream.scaladsl._
import akka.event.LoggingReceive
import akka.stream.Materializer
import akka.util.Timeout
import javax.inject.Inject
import model.Message
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.streams.ActorFlow

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object UsersSupervisor {
  case class Create(id: String)
  case object Broadcast
  case object RegisterActor
}

class UsersSupervisor @Inject() (configuration: Configuration)(implicit ec: ExecutionContext, mat: Materializer)
  extends Actor with InjectedActorSupport with ActorLogging {

  implicit val timeout: Timeout = Timeout(2.seconds)
  import UsersSupervisor._

  override def receive: Receive = receiveWithOpenedWsActors(Seq.empty[ActorRef])

  private def receiveWithOpenedWsActors(actors: Seq[ActorRef]): Receive = LoggingReceive {
    case Create(id) =>
      val name = s"userActor-$id"
      log.info(s"Setting up an user actor $name")
      val flow = ActorFlow.actorRef[Message, Message](out =>  UserActor.props(id, out, self))
      sender() ! flow

    case Broadcast =>
  }

}
