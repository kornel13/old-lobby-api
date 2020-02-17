import actors.UsersSupervisor
import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.stream.scaladsl.Flow
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import model.Message
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext

class UserSupervisorActorSpec
    extends TestKit(ActorSystem("SupervisorActorSpec"))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val actorSystem = system

  import UsersSupervisor._

  "A supervisor actor" should {
    val dbActorTestProbe = TestProbe("databaseActor")
    val supervisorActor = system.actorOf(Props(new UsersSupervisor(dbActorTestProbe.ref)))

    "create a flow and a child userActor when new connection is set up" in {
      supervisorActor ! NewWebSocketConnection("requestId")
      expectMsgType[Flow[Message, Message, NotUsed]]
    }
  }

}
