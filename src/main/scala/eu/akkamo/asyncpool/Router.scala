package eu.akkamo.asyncpool

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.routing.FromConfig
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Router
  *
  * @param actor router Actor
  */
class Router[U, V](val actor: ActorRef) {

  import java.util.concurrent.TimeUnit.SECONDS

  import Router._

  import scala.concurrent.ExecutionContext.Implicits

  /**
    * Send `job` to the worker and retrieve response
    *
    * @param job     job for processing
    * @param ec      implicit execution context, default `global`
    * @param timeout implicit timeout, default 60 seconds
    * @return result as [[scala.concurrent.Future]]
    */
  @throws[AskTimeoutException]
  def ?(job: Job[U, V])(implicit ec: ExecutionContext = Implicits.global,
                        timeout: Timeout = Timeout(60, SECONDS)): Future[V] = {
    ask(actor, Router.Task(job, true)).map(_.asInstanceOf[Response[V, _]]).flatMap {
      case Failure(th) => Future.failed(th)
      case Success(JobResponse(value, _)) => Future.successful(value)
    }
  }

  /**
    * Send `job` to the worker
    *
    * @param job job for processing
    */
  def !(job: Job[U, V]): Unit = {
    actor ! Router.Task(job, false)
  }

  /**
    * Send `job` to the worker,then send job result as response:[[eu.akkamo.asyncpool.Router#Response]]
    * back to the  the `sender`
    *
    * @param job job for processing
    * @param sender
    * @tparam CTX non mandatory context, returned in response
    */
  def ??[CTX](job: Job[U, V], ctx:Option[CTX] = None)(implicit sender: ActorRef): Unit = {
    actor ! Router.Task(job, true, ctx)
  }
}


object Router {

  /**
    * Job Response
    * @param value job result
    * @param ctx optional context passed when ask (see. [[eu.akkamo.asyncpool.Router.??]])  is called
    * @tparam V the job result Type
    * @tparam CTX optional context Type
    */
  case class JobResponse[V, CTX](value: V, ctx:Option[CTX] = None)

  type Response[V, CTX] = Try[JobResponse[V, CTX]]

  type Job[U, V] = (U) => V

  type SF[U] = Either[String => U, (String, ActorSystem) => U]

  /**
    *
    * @param ctxFactory function accepting string
    * @param name router name
    * @param as implicit Akka actor system
    * @tparam U the `ctx` type
    * @tparam V the Job result type
    * @return instance of router
    */
  def buildRouter[U, V](ctxFactory: String => U, name: String)(implicit as: ActorSystem) = {
    new Router[U, V](as.actorOf(FromConfig.props(Props(classOf[Worker[V]], Left(ctxFactory))), name))
  }

  /**
    *
    * @param ctxFactory function accepting (string, Akka actor system)
    * @param name router name
    * @param as implicit Akka actor system
    * @tparam U the `ctx` type
    * @tparam V the Job result type
    * @return instance of router
    */
  def buildRouter[U, V](ctxFactory: (String, ActorSystem) => U, name: String)(implicit as: ActorSystem) = {
    new Router[U, V](as.actorOf(FromConfig.props(Props(classOf[Worker[V]], Left(ctxFactory))), name))
  }

  @SerialVersionUID(1L)
  private[Router] case class Task[T, U, CTX](val f: T => U, ask: Boolean,  ctx:Option[CTX] = None) extends Serializable {
    def apply(t: T): U = {
      f(t)
    }
  }

  /**
    * @param ctxFactory
    * @tparam U worker `ctx` type
    * @author jubu
    */
  private class Worker[U](ctxFactory: SF[U]) extends Actor with ActorLogging {

    private val ctx = ctxFactory match {
      case Left(f) => f(context.parent.path.name)
      case Right(f) => f(context.parent.path.name, context.system)
    }

    def receive = {
      case t: Task[U, _,_] => try {
        log.debug(s"Task received: $t")
        val res = t(ctx)
        if(t.ask) {
          sender ! Success(JobResponse(res, t.ctx))
        }
      } catch {
        case e: Throwable => {
          sender ! Failure(e)
          log.error(e, "received task failed")
          throw e
        }
      }
    }

    @scala.throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
      super.preStart()
      log.info(s"started,  path: ${self.path}")
    }

    @scala.throws[Exception](classOf[Exception])
    override def postStop(): Unit = {
      super.postStop()
      log.info(s"stopped path: ${self.path}")
    }

    @scala.throws[Exception](classOf[Exception])
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      super.preRestart(reason, message)
      log.error(reason, s"restarted with message:$message, path: ${self.path}")
    }
  }

}