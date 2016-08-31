package eu.akkamo.asyncpool

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}


/**
  *
  * @param router
  * @param timeout
  * @tparam T
  * @author jubu
  */
case class AsyncPool[T](router: ActorRef)
                       (implicit ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global,
                        timeout: Timeout = Timeout(10, TimeUnit.SECONDS)) {
  type Job[U] = (T) => U

  def apply[U](f: Job[U]): Future[U] = ask(router, Task(f)).map {
    _.asInstanceOf[U]
  }

}

object AsyncPool {
  type SessionFactory = Either[Config, String]
  //def apply(system:Ac)()
}


/**
  *
  * @param factory
  * @tparam T
  * @author jubu
  */
case class Worker[T](factory: () => T) extends Actor {

  // create it only one time
  private val v = factory()

  def receive = {
    case t: Task[T, _] => try {
      sender ! t(v)
    } catch {
      case e: Throwable => sender ! e
    }
  }
}

@SerialVersionUID(1L)
private case class Task[T, U](val f: T => U) extends Serializable {
  def apply(t: T): U = {
    f(t)
  }
}