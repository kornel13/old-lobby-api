import actors.{DBActor, UserActor, UsersSupervisor}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class UserActorSpec
    extends TestKit(ActorSystem("UserActorSpec"))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {

  import model._
  import model.user._
  import model.table._
  import UserActor._

  "Anonymous actor" should {

    "respond to a ping" in {
      val (anonymousUserActor, wsActorProbe, _) = UserActorSpec.anonymousUserFixture()
      anonymousUserActor ! ping(2)
      wsActorProbe.expectMsg(pong(2))
    }

    "should receive login incorrect msg when credentials are not correct" in {
      val (anonymousUserActor, wsActorProbe, dbActorTestProbe) = UserActorSpec.anonymousUserFixture()
      val (user, passwd) = ("user", "password")
      anonymousUserActor ! login(user, passwd)
      dbActorTestProbe.expectMsg(DBActor.AuthenticateUser("user", utils.PasswordHasher.hashPassword(user, passwd)))
      dbActorTestProbe.reply(DBActor.UserInvalid)

      wsActorProbe.expectMsg(login_failed)
    }

    "should be able to log with correct credentials" in {
      val (anonymousUserActor, wsActorProbe, dbActorTestProbe) = UserActorSpec.anonymousUserFixture()
      val (user, passwd) = ("user", "password")
      anonymousUserActor ! login(user, passwd)
      dbActorTestProbe.expectMsg(DBActor.AuthenticateUser("user", utils.PasswordHasher.hashPassword(user, passwd)))
      val role = CommonUser
      dbActorTestProbe.reply(DBActor.UserAuthenticated(User(user, role, subscription = false)))

      wsActorProbe.expectMsg(login_successful(role.toString))

    }

    "should respond with unauthorized for any other messages" in {
      val (anonymousUserActor, wsActorProbe, _) = UserActorSpec.anonymousUserFixture()
      anonymousUserActor ! subscribe_tables
      wsActorProbe.expectMsg(not_authorized)
    }
  }

  "Logged as a user actor" should {
    "respond to a ping" in {
      val (loggedUserActor, wsActorProbe, _, _, _) = UserActorSpec.loggedUserFixture(subscribed = false)
      loggedUserActor ! ping(2)
      wsActorProbe.expectMsg(pong(2))
    }

    "get list when subscribe" in {
      val (loggedUserActor, wsActorProbe, dbActorTestProbe, _, user) =
        UserActorSpec.loggedUserFixture(subscribed = false)
      loggedUserActor ! subscribe_tables
      dbActorTestProbe.expectMsg(DBActor.Subscribe(user.userName))
      val listedTable = Seq.empty[TableModel]
      dbActorTestProbe.reply(DBActor.ListedTables(listedTable))
      wsActorProbe.expectMsg(table_list(listedTable))
    }

    "send update to a socket when table updated if subscribed" in {
      val (loggedUserActor, wsActorProbe, _, _, _) = UserActorSpec.loggedUserFixture(subscribed = true)
      loggedUserActor ! SendUpdateToSocket(table_removed(1L))
      wsActorProbe.expectMsg(table_removed(1L))
    }

    "NOT send update to a socket when table updated if unsubscribed" in {
      val (loggedUserActor, wsActorProbe, _, _, _) = UserActorSpec.loggedUserFixture(subscribed = false)
      loggedUserActor ! SendUpdateToSocket(table_removed(1L))
      wsActorProbe.expectNoMessage()
    }

    "should respond with unauthorized for any table modification messages" in {
      val (loggedUserActor, wsActorProbe, _, _, _) = UserActorSpec.loggedUserFixture(subscribed = false)
      loggedUserActor ! remove_table(2L)
      wsActorProbe.expectMsg(not_authorized)
    }
  }

  "Logged as an admin actor" should {
    "respond to a ping" in {
      val (adminUserActor, wsActorProbe, _, _, _) = UserActorSpec.adminUserFixture(subscribed = false)
      adminUserActor ! ping(2)
      wsActorProbe.expectMsg(pong(2))
    }

    "perform successful table modification with update to websocket" in {
      val (adminUserActor, wsActorProbe, dbActorTestProbe, supervisorActorProbe, _) =
        UserActorSpec.adminUserFixture(subscribed = true)
      val removeTableId = 2L
      adminUserActor ! remove_table(removeTableId)
      dbActorTestProbe.expectMsg(DBActor.RemoveTable(removeTableId))
      dbActorTestProbe.reply(DBActor.OperationSucceeded(DBActor.RemoveTable(removeTableId)))

      supervisorActorProbe.expectMsg(UsersSupervisor.UpdateNotification(table_removed(removeTableId)))
      supervisorActorProbe.reply(SendUpdateToSocket(table_removed(removeTableId)))
      wsActorProbe.expectMsg(table_removed(removeTableId))
    }

    "notify about failed table modification" in {
      val (adminUserActor, wsActorProbe, dbActorTestProbe, _, _) =
        UserActorSpec.adminUserFixture(subscribed = true)
      val removeTableId = 2L
      adminUserActor ! remove_table(removeTableId)
      dbActorTestProbe.expectMsg(DBActor.RemoveTable(removeTableId))
      dbActorTestProbe.reply(DBActor.OperationFailed(DBActor.RemoveTable(removeTableId), new Exception("failed")))
      wsActorProbe.expectMsg(removal_failed(removeTableId))
    }
  }

  object UserActorSpec {
    def anonymousUserFixture(): (ActorRef, TestProbe, TestProbe) = {
      val wsActorProbe = TestProbe("wsActor")
      val dbActorTestProbe = TestProbe("databaseActor")
      val anonymousUserActor =
        system.actorOf(Props(new UserActor("id", wsActorProbe.ref, dbActorTestProbe.ref)))
      (anonymousUserActor, wsActorProbe, dbActorTestProbe)
    }

    def loggedUserFixture(subscribed: Boolean): (ActorRef, TestProbe, TestProbe, TestProbe, User) = {
      val wsActorProbe = TestProbe("wsActor")
      val dbActorTestProbe = TestProbe("databaseActor")
      val supervisorActorProbe = TestProbe("supervisor")
      val loggedUserActor = TestActorRef[UserActor](
        Props(new UserActor("id", wsActorProbe.ref, dbActorTestProbe.ref)),
        supervisorActorProbe.ref,
        "loggedUser"
      )
      val role = CommonUser
      val user = User("userName", role, subscription = subscribed)
      loggedUserActor ! DBActor.UserAuthenticated(user)
      wsActorProbe.expectMsg(login_successful(role.toString))
      (loggedUserActor, wsActorProbe, dbActorTestProbe, supervisorActorProbe, user)
    }

    def adminUserFixture(subscribed: Boolean): (ActorRef, TestProbe, TestProbe, TestProbe, User) = {
      val wsActorProbe = TestProbe("wsActor")
      val dbActorTestProbe = TestProbe("databaseActor")
      val supervisorActorProbe = TestProbe("supervisor")
      val adminUserActor = TestActorRef[UserActor](
        Props(new UserActor("id", wsActorProbe.ref, dbActorTestProbe.ref)),
        supervisorActorProbe.ref,
        "loggedUser"
      )
      val role = Admin
      val user = User("admin", role, subscription = subscribed)
      adminUserActor ! DBActor.UserAuthenticated(user)
      wsActorProbe.expectMsg(login_successful(role.toString))
      (adminUserActor, wsActorProbe, dbActorTestProbe, supervisorActorProbe, user)

    }
  }

}
