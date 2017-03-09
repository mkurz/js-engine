package com.typesafe.jse

import akka.actor._
import com.typesafe.jse.Engine.ExecuteJs
import akka.contrib.process.BlockingProcess
import akka.contrib.process.BlockingProcess.Started
import scala.collection.immutable
import akka.contrib.process.StreamEvents.Ack
import java.io.File

/**
 * Provides an Actor on behalf of a JavaScript Engine. Engines are represented as operating system processes and are
 * communicated with by launching with arguments and returning a status code.
 * @param stdArgs a sequence of standard command line arguments used to launch the engine from the command line.
 * @param stdEnvironment a sequence of standard module paths.
 */
class LocalEngine(stdArgs: immutable.Seq[String], stdEnvironment: Map[String, String]) extends Engine(stdArgs, stdEnvironment) {

  def receive = {
    case ExecuteJs(f, args, timeout, timeoutExitValue, environment) =>
      val requester = sender()

      context.actorOf(BlockingProcess.props(
        (stdArgs :+ f.getCanonicalPath) ++ args,
        stdEnvironment ++ environment, self),
        "process"
      )
      context.become {
        case Started(i, o, e) =>
          context.become(engineIOHandler(i, o, e, requester, Ack, timeout, timeoutExitValue))
          i ! PoisonPill // We don't need an input stream so close it out straight away.
      }
  }
}


/**
 * Local engine utilities.
 */
object LocalEngine {

  def path(path: Option[File], command: String): String = path.fold(command)(_.getCanonicalPath)

}

/**
 * Used to manage a local instance of Node.js with CommonJs support. common-node is assumed to be on the path.
 */
object CommonNode {

  import LocalEngine._

  def props(command: Option[File] = None, stdArgs: immutable.Seq[String] = Nil, stdEnvironment: Map[String, String] = Map.empty): Props = {
    val args = Seq(path(command, "common-node")) ++ stdArgs
    Props(classOf[LocalEngine], args, stdEnvironment)
  }
}

/**
 * Used to manage a local instance of Node.js. Node is assumed to be on the path.
 */
object Node {

  import LocalEngine._

  def props(command: Option[File] = None, stdArgs: immutable.Seq[String] = Nil, stdEnvironment: Map[String, String] = Map.empty): Props = {
    val args = Seq(path(command, "node")) ++ stdArgs
    Props(classOf[LocalEngine], args, stdEnvironment)
  }
}

/**
 * Used to manage a local instance of PhantomJS. PhantomJS is assumed to be on the path.
 */
object PhantomJs {

  import LocalEngine._

  def props(command: Option[File] = None, stdArgs: immutable.Seq[String] = Nil, stdEnvironment: Map[String, String] = Map.empty): Props = {
    val args = Seq(path(command, "phantomjs")) ++ stdArgs
    Props(classOf[LocalEngine], args, stdEnvironment)
  }
}
