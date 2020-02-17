package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.event.LoggingReceive
import akka.stream.scaladsl._
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import javax.inject.{Inject, Named}
import model.Message
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext

object UsersSupervisor {

  case class NewWebSocketConnection(id: String)

  final case class UpdateNotification(update: Message)
}

class UsersSupervisor @Inject()(@Named("databaseActor") dbActor: ActorRef)(
  implicit ec: ExecutionContext,
  mat: Materializer
) extends Actor
    with InjectedActorSupport
    with ActorLogging {
  import UsersSupervisor._

  override def receive: Receive = LoggingReceive {
    case NewWebSocketConnection(id) =>
      val name = s"userActor-$id"
      log.info(s"Setting up an user actor $name")
      val (flow, _) = customActorRefFlow(name, out => UserActor.props(id, out, dbActor))
      log.debug(s"Supervisor children: ${context.children.map(_.path.toString).mkString("[", ", ", "]")}")
      sender() ! flow

    case UpdateNotification(update) => context.children.foreach(_ ! UserActor.SendUpdateToSocket(update))
  }

  private def customActorRefFlow[In, Out](
    actorName: String,
    props: ActorRef => Props,
    bufferSize: Int = 16,
    overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew
  )(implicit mat: Materializer): (Flow[In, Out, _], ActorRef) = {
    val completionMatcher: PartialFunction[Any, CompletionStrategy] = {
      case akka.actor.Status.Success(s: CompletionStrategy) => s
      case akka.actor.Status.Success(_)                     => CompletionStrategy.draining
      case akka.actor.Status.Success                        => CompletionStrategy.draining
    }
    val failureMatcher: PartialFunction[Any, Throwable] = {
      case akka.actor.Status.Failure(cause) => cause
    }

    val (outActor, publisher) = Source
      .actorRef[Out](completionMatcher, failureMatcher, bufferSize, overflowStrategy)
      .toMat(Sink.asPublisher(false))(Keep.both)
      .run()

    val childActorRef = context.actorOf(props(outActor), actorName)

    (
      Flow.fromSinkAndSource(
        Sink.actorRef(
          ref = childActorRef,
          onCompleteMessage = Status.Success(()),
          onFailureMessage = throwable => Status.Failure(throwable)
        ),
        Source.fromPublisher(publisher)
      ),
      childActorRef
    )
  }
}
