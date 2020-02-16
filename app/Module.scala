import actors.{DBActor, UsersSupervisor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[UsersSupervisor]("usersSupervisor")
    bindActor[DBActor]("databaseActor")
  }
}
