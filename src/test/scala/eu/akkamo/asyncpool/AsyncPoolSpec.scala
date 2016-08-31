package eu.akkamo.asyncpool

import akka.actor.{ActorSystem, Props}
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * @author jubu
  */
class AsyncPoolSpec extends FlatSpec with Matchers {


  /**
    * congigured actor system
    */
  private implicit val actorSystem = ActorSystem("test", ConfigFactory.load().getConfig("test.asyncPoolSpec"))

  private val name = "TestConfig"

  private val sessionFactory = () => {
    val cfg = actorSystem.settings.config.getConfig(s"akka.actor.deployment./${name}.session")
    val ret = cfg.getInt("value")
    ret
  }

  /*
    private val supervisor: OneForOneStrategy = OneForOneStrategy(3, Duration(2, SECONDS)) {
      case _: Throwable => Restart
    }


    private val router: ActorRef =
      actorSystem.actorOf(
        Props(classOf[Worker[Int]], sessionFactory).withRouter(
          RoundRobinPool(nrOfInstances = 100, supervisorStrategy = supervisor)), name)
  */

  private lazy val router = actorSystem.actorOf(FromConfig.props(Props(classOf[Worker[Int]], sessionFactory)), name)

  private lazy val pool: AsyncPool[Int] = AsyncPool[Int](router)

  "AsyncPoolSpec" should "return result" in {
    val job: Future[Int] = pool { arg => arg + 1 }
    val result: Int = Await.result(job, Duration(1, SECONDS))
    assert(result == 11)
  }

  "AsyncPoolSpec" should "throws exception" in {
    val job: Future[Int] = pool {
      arg => throw new RuntimeException("Error occured")
    }
    an[Exception] should be thrownBy Await.result(job, Duration(1, SECONDS))
  }
}
