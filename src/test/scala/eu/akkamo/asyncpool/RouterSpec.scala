package eu.akkamo.asyncpool

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.{Success, Try}

/**
  * @author jubu
  */
class RouterSpec extends FlatSpec with Matchers {


  /**
    * congigured actor system
    */
  private implicit val actorSystem = ActorSystem("test", ConfigFactory.load().getConfig("test.RouterSpec"))

  private val name = "TestConfig"

  private val sessionFactory = { name: String =>
    val cfg = actorSystem.settings.config.getConfig(s"akka.actor.deployment./${name}.session")
    val ret = cfg.getInt("value")
    ret
  }

  private lazy val router = Router.buildRouter[Int, Int](sessionFactory, name)

  "?" should "return result" in {
    val job = router ? { arg => arg + 1 }
    val result: Int = Await.result(job, Duration(1, SECONDS))
    assert(result == 11)
  }

  "?" should "throws exception" in {
    val job = router ? { arg => throw new RuntimeException("Error occured") }
    an[Exception] should be thrownBy Await.result(job, Duration(1, SECONDS))
  }

  "?" should "accept implicit timeout" in {
    implicit val timeout: Timeout = 10 microsecond
    val job = router ? { arg =>
      Try(Thread.sleep(100))
      arg + 1
    }
    an[AskTimeoutException] should be thrownBy Await.result(job, Duration(10, SECONDS))
  }

  "!" should "be executed" in {
    var result = 0
    router ! { arg =>
      result = arg + 1
      result
    }
    Try(Thread.sleep(100))
    assert(result != 0)
  }

  "??" should "created Actor should return result" in {
    val promise = Promise[Int]()
    val future = promise.future
    actorSystem.actorOf(Props(
      new Actor {
        import Router._
        override def receive: Receive = {
          case Success(JobResponse(v:Int, _)) =>  promise.success(v);()
        }

        @scala.throws[Exception](classOf[Exception])
        override def preStart(): Unit = {
          super.preStart()
          // send message from actor
          router ?? {arg => arg + 1}
        }
      }
    ))

    val response = Await.result(future, Duration(1, SECONDS))
    assert(response == 11)
  }

  "??" should "created Actor should return context + result" in {
    val promise = Promise[Int]()
    val future = promise.future
    actorSystem.actorOf(Props(
      new Actor {
        import Router._
        override def receive: Receive = {
          case Success(JobResponse(v:Int, Some(c:Int))) if(c == 1) =>  promise.success(v);()
        }

        @scala.throws[Exception](classOf[Exception])
        override def preStart(): Unit = {
          super.preStart()
          // send message from actor
          router ?? ({arg => arg + 1}, Some(1))
        }
      }
    ))

    val response = Await.result(future, Duration(1, SECONDS))
    assert(response == 11)
  }
}
