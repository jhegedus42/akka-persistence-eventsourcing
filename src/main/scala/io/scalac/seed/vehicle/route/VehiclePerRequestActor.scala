package io.scalac.seed.vehicle.route

import akka.actor._
import akka.actor.SupervisorStrategy.Stop
import akka.actor.OneForOneStrategy
import io.scalac.seed.common._
import io.scalac.seed.vehicle.domain.VehicleAggregate._
import org.json4s.DefaultFormats
import scala.concurrent.duration._
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.routing.{HttpService, RequestContext}
import spray.httpx.Json4sSupport
import io.scalac.seed.vehicle.service.VehicleAggregateManager

trait PerRequest extends Actor with ActorLogging with Json4sSupport {

  import context._

  val json4sFormats = DefaultFormats

  def r: RequestContext
  def target: ActorRef
  def message: VehicleAggregateManager.Command

  setReceiveTimeout(2.seconds)
  target ! message

  def complete[T <: AnyRef](status: StatusCode, obj: T) = {
    r.complete(status, obj)
    stop(self)
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e => {
        complete(InternalServerError, Error(e.getMessage))
        Stop
      }
    }
}

object PerRequest {
  
  case class RegisterVehicleRequestActor(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) extends PerRequest {
    override val receive: Receive = {
      case res: Vehicle => complete(Created, res)
      case ReceiveTimeout    => complete(GatewayTimeout, Error("Request timeout"))
      case res               => 
        log.error("received unexpected message " + res)
        complete(InternalServerError, "Something unexpected happened. We're working on it.")
    }
  }

  case class DeleteVehicleRequestActor(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) extends PerRequest {
    override val receive: Receive = {
      case res @ EmptyVehicle   => complete(NoContent, "")
      case ReceiveTimeout     => complete(GatewayTimeout, Error("Request timeout"))
      case res                => 
        log.error("received unexpected message " + res)
        complete(InternalServerError, "Something unexpected happened. We're working on it.")
    }
  }

  case class UpdateVehicleRequestActor(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) extends PerRequest {
    override val receive: Receive = {
      case res: Vehicle  => complete(OK, res)
      case res @ EmptyVehicle   => complete(NotFound, "")
      case ReceiveTimeout     => complete(GatewayTimeout, Error("Request timeout"))
      case res                => 
        log.error("received unexpected message " + res)
        complete(InternalServerError, "Something unexpected happened. We're working on it.")
    }  
  }

  case class GetVehicleRequestActor(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) extends PerRequest {
    override val receive: Receive = {
      case res: Vehicle  => complete(OK, res)
      case res @ EmptyVehicle   => complete(NotFound, "")
      case ReceiveTimeout     => complete(GatewayTimeout, Error("Request timeout"))
      case res                => 
        log.error("received unexpected message " + res)
        complete(InternalServerError, "Something unexpected happened. We're working on it.")
    }  
  }
}

trait PerRequestCreator {
  this: HttpService =>

  import PerRequest._
  
  def perRequestRegister(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) =
    actorRefFactory.actorOf(Props(RegisterVehicleRequestActor(r, target, message)))
    
  def perRequestUpdate(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) =
    actorRefFactory.actorOf(Props(UpdateVehicleRequestActor(r, target, message)))
    
  def perRequestDelete(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) =
    actorRefFactory.actorOf(Props(DeleteVehicleRequestActor(r, target, message)))

  def perRequestGet(r: RequestContext, target: ActorRef, message: VehicleAggregateManager.Command) =
    actorRefFactory.actorOf(Props(GetVehicleRequestActor(r, target, message)))

}