package controllers

import actors._
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.Timeout
import javax.inject._
import model._
import model.user.{User, UserToAddNotHashedPassword, UserToRemove}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MainController @Inject()(
  @Named("usersSupervisor") userSupervisor: ActorRef,
  @Named("databaseActor") dbActor: ActorRef,
  evolutionRepository: EvolutionRepository,
  cc: ControllerComponents
)(implicit ec: ExecutionContext, mat: Materializer)
    extends AbstractController(cc)
    with SameOriginCheck {

  val logger = play.api.Logger(getClass)

  def index = Action { implicit request: Request[AnyContent] =>
    Ok("Dziala")
  }
  implicit val timeout: Timeout = Timeout(1000 milliseconds)

  ////////////DEBUG //////////////////////////
  def listTables = Action.async { _ =>
    (dbActor ? DBActor.ListTables).mapTo[Seq[model.table.TableModel]].map(tables => Ok(Json.toJson(tables)))
  }

  def addTable = Action.async(parse.json[model.Message]) { implicit req: Request[model.Message] =>
    (dbActor ? DBActor.AddTable(req.body.asInstanceOf[model.add_table].table))
      .mapTo[DBActor.TableOperationResult]
      .map(_ => Ok(s"Added ${req.body}"))
  }

  def removeTable = Action.async(parse.json[model.Message]) { implicit req: Request[Message] =>
    (dbActor ? DBActor.RemoveTable(req.body.asInstanceOf[model.remove_table].id))
      .mapTo[DBActor.TableOperationResult]
      .map(_ => Ok(s"Removed ${req.body}"))
  }

  def updateTable = Action.async(parse.json[model.Message]) { implicit req: Request[Message] =>
    (dbActor ? DBActor.UpdateTable(req.body.asInstanceOf[model.update_table].table))
      .mapTo[DBActor.TableOperationResult]
      .map(_ => Ok(s"Added ${req.body}"))
  }

  ///////////////////////////////////////////

  def listUsers = Action.async { _ =>
    (dbActor ? DBActor.ListUsers).mapTo[Seq[User]].map(users => Ok(Json.toJson(users)))
  }

  def addUser = Action.async(parse.json[UserToAddNotHashedPassword]) {
    implicit req: Request[UserToAddNotHashedPassword] =>
      import model.user.UserMapper.toHashedPassword
      (dbActor ? DBActor.AddUser(toHashedPassword(req.body))).mapTo[Int].map(_ => Ok(s"Added ${req.body}"))
  }

  def removeUser = Action.async(parse.json[UserToRemove]) { implicit req: Request[UserToRemove] =>
    (dbActor ? DBActor.RemoveUser(req.body)).mapTo[Int].map(_ => Ok(s"Removed ${req.body.userName}"))
  }

  def evolution = Action { _ =>
    Ok(evolutionRepository.getEvolutionSchema)
  }

  implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[Message, Message]

  def ws: WebSocket = WebSocket.acceptOrResult[Message, Message] {
    case rh /*if sameOriginCheck(rh)*/ =>
      wsFutureFlow(rh)
        .map { flow =>
          Right(flow)
        }
        .recover {
          case e: Exception =>
            logger.error("Cannot create websocket", e)
            val jsError = Json.obj("error" -> "Cannot create websocket")
            val result = InternalServerError(jsError)
            Left(result)
        }

    //    case rejected =>
    //      logger.error(s"Request ${rejected} failed same origin check")
    //      Future.successful {
    //        Left(Forbidden("forbidden"))
    //      }
  }

  private def wsFutureFlow(request: RequestHeader): Future[Flow[Message, Message, NotUsed]] = {
    implicit val timeout = Timeout(1.second) // the first run in dev can take a while :-(
    val askFuture = userSupervisor ? UsersSupervisor.Create(request.id.toString)
    askFuture.mapTo[Flow[Message, Message, NotUsed]]
  }

}

trait SameOriginCheck {

  def logger: Logger

  def sameOriginCheck(rh: RequestHeader): Boolean = {
    rh.headers.get("Origin") match {
      case Some(originValue) if originMatches(originValue) =>
        logger.debug(s"originCheck: originValue = $originValue")
        true

      case Some(badOrigin) =>
        logger.error(
          s"originCheck: rejecting request because Origin header value ${badOrigin} is not in the same origin"
        )
        false

      case None =>
        logger.error("originCheck: rejecting request because no Origin header found")
        false
    }
  }

  def originMatches(origin: String): Boolean = {
    origin.contains("localhost:9000") || origin.contains("localhost:19001")
  }

}
