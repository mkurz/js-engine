package com.typesafe.jse

import org.specs2.mutable.Specification
import com.typesafe.jse.Engine.JsExecutionResult
import java.io.File
import scala.collection.immutable
import akka.pattern.ask
import org.specs2.time.NoTimeConversions
import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.Await
import java.util.concurrent.TimeUnit

class TriremeSpec extends Specification {

  sequential

  def withActorSystem[T](block: ActorSystem => T): T = {
    val system = ActorSystem()
    try block(system) finally {
      system.shutdown()
      system.awaitTermination()
    }
  }

  def withEngine[T](block: ActorRef => T): T = withActorSystem { system =>
    val engine = system.actorOf(Trireme.props(), "trireme-spec")
    block(engine)
  }

  "The Trireme engine" should {
    "execute some javascript by passing in a string arg and comparing its return value" in {
      withEngine {
        engine =>
          val f = new File(classOf[TriremeSpec].getResource("test-node.js").toURI)
          implicit val timeout = Timeout(5000L, TimeUnit.MILLISECONDS)

          val futureResult = (engine ? Engine.ExecuteJs(f, immutable.Seq("999"), timeout.duration)).mapTo[JsExecutionResult]
          val result = Await.result(futureResult, timeout.duration)
          new String(result.output.toArray, "UTF-8").trim must_== "999"
          new String(result.error.toArray, "UTF-8").trim must_== ""
      }
    }

    "execute some javascript by passing in a string arg and comparing its return value expecting an error" in {
      withEngine {
        engine =>
          val f = new File(classOf[TriremeSpec].getResource("test-rhino.js").toURI)
          implicit val timeout = Timeout(5000L, TimeUnit.MILLISECONDS)

          val futureResult = (engine ? Engine.ExecuteJs(f, immutable.Seq("999"), timeout.duration)).mapTo[JsExecutionResult]
          val result = Await.result(futureResult, timeout.duration)
          new String(result.output.toArray, "UTF-8").trim must_== ""
          new String(result.error.toArray, "UTF-8").trim must startWith("""ReferenceError: "readFile" is not defined""")
      }
    }

    def runSimpleTest(system: ActorSystem) = {
      implicit val timeout = Timeout(5000L, TimeUnit.MILLISECONDS)
      val f = new File(classOf[TriremeSpec].getResource("test-node.js").toURI)
      val engine = system.actorOf(Trireme.props(), "not-leak-threads-test")
      val futureResult = (engine ? Engine.ExecuteJs(f, immutable.Seq("999"), timeout.duration)).mapTo[JsExecutionResult]
      val result = Await.result(futureResult, timeout.duration)
      new String(result.output.toArray, "UTF-8").trim must_== "999"
      new String(result.error.toArray, "UTF-8").trim must_== ""
    }

    "not leak threads" in withActorSystem { system =>
      // this test assumes that there are no other trireme tests running concurrently, if there are, the trireme thread
      // count will be non 0
      runSimpleTest(system)

      Thread.sleep(1)

      import scala.collection.JavaConverters._
      val triremeThreads = Thread.getAllStackTraces.keySet.asScala
        .filter(_.getName.contains("Trireme"))

      ("trireme threads: " + triremeThreads) <==> (triremeThreads.size === 0)
      ok
    }


    "not leak file descriptors" in withActorSystem { system =>
      import java.lang.management._
      val os = ManagementFactory.getOperatingSystemMXBean
      // To get the open file descriptor count, you need to use non portable APIs, so use reflection
      try {
        val method = os.getClass.getMethod("getOpenFileDescriptorCount")
        // method is public native, needs to be set accessible to be invoked using reflection
        method.setAccessible(true)
        def getCount = method.invoke(os).asInstanceOf[Long]

        val openFds = getCount
        runSimpleTest(system)
        getCount must_== openFds
      } catch {
        case nse: NoSuchMethodException =>
          println("Skipping file descriptor leak test because OS mbean doesn't have getOpenFileDescriptorCount")
          ok
      }
    }
  }

}
