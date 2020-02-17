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
import play.api.Configuration
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
  configuration: Configuration,
  cc: ControllerComponents
)(implicit ec: ExecutionContext, mat: Materializer)
    extends AbstractController(cc) {

  val logger = play.api.Logger(getClass)

  def index = Action { implicit request: Request[AnyContent] =>
    Ok("Lobby API Web socket implementation")
  }
  implicit val timeout: Timeout = Timeout(1000 milliseconds)

  def listTables = Action.async { _ =>
    (dbActor ? DBActor.ListTables).mapTo[Seq[model.table.TableModel]].map(tables => Ok(Json.toJson(tables)))
  }

  def addTable = Action.async(parse.json[model.Message]) { implicit req: Request[model.Message] =>
    {
      val addModel = req.body.asInstanceOf[model.add_table]
      (dbActor ? DBActor.AddTable(addModel.table, addModel.after_id))
        .mapTo[DBActor.TableOperationResult]
        .map {
          case DBActor.OperationSucceeded(modification) => Ok(s"Added: $modification")
          case DBActor.OperationFailed(modification, throwable) =>
            BadRequest(s"For $modification => ${throwable.getMessage}")
        }
    }
  }

  def removeTable = Action.async(parse.json[model.Message]) { implicit req: Request[Message] =>
    (dbActor ? DBActor.RemoveTable(req.body.asInstanceOf[model.remove_table].id))
      .mapTo[DBActor.TableOperationResult]
      .map {
        case DBActor.OperationSucceeded(modification) => Ok(s"Removed: $modification")
        case DBActor.OperationFailed(modification, throwable) =>
          BadRequest(s"For $modification => ${throwable.getMessage}")
      }
  }

  def updateTable = Action.async(parse.json[model.Message]) { implicit req: Request[Message] =>
    (dbActor ? DBActor.UpdateTable(req.body.asInstanceOf[model.update_table].table))
      .mapTo[DBActor.TableOperationResult]
      .map {
        case DBActor.OperationSucceeded(modification) => Ok(s"Updated: $modification")
        case DBActor.OperationFailed(modification, throwable) =>
          BadRequest(s"For $modification => ${throwable.getMessage}")
      }
  }

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

  def ws: WebSocket =
    WebSocket.acceptOrResult[Message, Message](
      requestHeader =>
        wsFutureFlow(requestHeader)
          .map { flow =>
            Right(flow)
          }
          .recover {
            case e: Exception =>
              logger.error("Cannot create websocket", e)
              val jsError = Json.obj("error" -> "Cannot create websocket")
              Left(InternalServerError(jsError))
        }
    )

  private def wsFutureFlow(request: RequestHeader): Future[Flow[Message, Message, NotUsed]] = {
    implicit val timeout = Timeout(1.second)
    val askFuture = userSupervisor ? UsersSupervisor.NewWebSocketConnection(request.id.toString)
    askFuture.mapTo[Flow[Message, Message, NotUsed]]
  }

}
