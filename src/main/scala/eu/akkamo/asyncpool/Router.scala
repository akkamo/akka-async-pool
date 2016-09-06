package eu.akkamo.asyncpool

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.routing.{FromConfig, RouterConfig}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Router
  *
  * @param actor router Actor
  */
class Router[U](val actor: ActorRef) {

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
  def ?[V](job: Job[U, V])(implicit ec: ExecutionContext = Implicits.global,
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
  def ![V](job: Job[U, V]): Unit = {
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
  def ??[V, CTX](job: Job[U, V], ctx: Option[CTX] = None)(implicit sender: ActorRef): Unit = {
    actor ! Router.Task(job, true, ctx)
  }
}


object Router {

  /**
    * Job Response
    *
    * @param value job result
    * @param ctx   optional context passed when ask (see. [[eu.akkamo.asyncpool.Router.??]])  is called
    * @tparam V   the job result Type
    * @tparam CTX optional context Type
    */
  case class JobResponse[V, CTX](value: V, ctx: Option[CTX] = None)

  type Response[V, CTX] = Try[JobResponse[V, CTX]]

  type Job[U, V] = (U) => V

  type SessionFactoryWrapper[U] = Either[String => U, (String, ActorSystem) => U]

  /**
    *
    * @param sessionFactory function accepts string (Router name) returns `session`
    * @param name           router name
    * @param as             implicit Akka actor system
    * @tparam U the `session` type
    * @return instance of router
    */
  def buildRouter[U](sessionFactory: String => U, name: String)(implicit as: ActorSystem) = {
    new Router[U](as.actorOf(FromConfig.props(Props(classOf[Worker[U]], Left(sessionFactory))), name))
  }

  /**
    *
    * @param sessionFactory function accepts (string (router name), Akka actor system) returns 'session'
    * @param name           router name
    * @param as             implicit Akka actor system
    * @tparam U `session` type
    * @return instance of router
    */
  def buildRouter[U](sessionFactory: (String, ActorSystem) => U, name: String)(implicit as: ActorSystem) = {
    new Router[U](as.actorOf(FromConfig.props(Props(classOf[Worker[U]], Right(sessionFactory))), name))
  }

  /**
    *
    * @param sessionFactory function accepts string (Router name) returns `session`
    * @param routerConfig   router configuration
    * @tparam U `session` type
    * @return instance of router Props
    */
  def buildRouterProps[U](sessionFactory: String => U, routerConfig: RouterConfig): Props = {
    Props(classOf[Worker[U]], Left(sessionFactory)).withRouter(routerConfig)
  }

  /**
    *
    * @param sessionFactory function accepts (string (router name), Akka actor system) returns 'session'
    * @param routerConfig   router configuration
    * @tparam U `session` type
    * @return instance of router Props
    */
  def buildRouterProps[U](sessionFactory: (String, ActorSystem) => U, routerConfig: RouterConfig): Props = {
    Props(classOf[Worker[U]], Right(sessionFactory)).withRouter(routerConfig)
  }

  @SerialVersionUID(1L)
  private[Router] case class Task[U, V, CTX](val job: Job[U, V], ask: Boolean, ctx: Option[CTX] = None) extends Serializable

  /**
    * @param ctxFactory
    * @tparam U worker `session` type
    */
  private class Worker[U](ctxFactory: SessionFactoryWrapper[U]) extends Actor with ActorLogging {

    private val session = ctxFactory match {
      case Left(f) => f(context.parent.path.name)
      case Right(f) => f(context.parent.path.name, context.system)
    }

    def receive = {
      case t: Task[U, _, _] => try {
        log.debug(s"Task received: $t")
        val res = t.job(session)
        if (t.ask) {
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