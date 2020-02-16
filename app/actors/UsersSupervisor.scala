package actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, Status}
import akka.event.LoggingReceive
import akka.stream.scaladsl._
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.util.Timeout
import javax.inject.{Inject, Named}
import model.Message
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object UsersSupervisor {

  case class Create(id: String)

  final case class UpdateNotification(update: Message)
}

class UsersSupervisor @Inject()(@Named("databaseActor") dbActor: ActorRef, configuration: Configuration)(
  implicit ec: ExecutionContext,
  mat: Materializer
) extends Actor
    with InjectedActorSupport
    with ActorLogging {

  implicit val timeout: Timeout = Timeout(2.seconds)

  import UsersSupervisor._

  override def receive: Receive = receiveWithOpenedWsActors(Seq.empty[ActorRef])

  private def receiveWithOpenedWsActors(actors: Seq[ActorRef]): Receive = LoggingReceive {
    case Create(id) =>
      val name = s"userActor-$id"
      log.info(s"Setting up an user actor $name")
      val (flow, _) = customActorRefFlow(name, out => UserActor.props(id, out, dbActor))
      //val flow = ActorFlow.actorRef[Message, Message](out => UserActor.props(id, out, self))
      println(context.children.map(_.path.toString).mkString("\n"))
      sender() ! flow

    case UpdateNotification(update) => context.children.foreach(_ ! UserActor.SendUpdateToSocket(update))
  }

  private def customActorRefFlow[In, Out](
    actorName: String,
    props: ActorRef => Props,
    bufferSize: Int = 16,
    overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew
  )(implicit factory: ActorRefFactory, mat: Materializer): (Flow[In, Out, _], ActorRef) = {
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
