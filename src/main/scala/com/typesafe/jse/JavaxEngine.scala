package com.typesafe.jse

import akka.actor._
import akka.contrib.process.StreamEvents.Ack
import akka.contrib.process._
import java.io._
import javax.script._
import scala.collection.immutable
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.jse.Engine.ExecuteJs

/**
 * Declares an in-JVM JavaScript engine. The actor is expected to be associated with a blocking dispatcher as the
 * javax.script API is synchronous.
 */
class JavaxEngine(
    stdArgs: immutable.Seq[String],
    ioDispatcherId: String,
    engineName: String
  ) extends Engine(stdArgs, Map.empty) {

  val StdioTimeout = Engine.infiniteSchedulerTimeout(context.system.settings.config)

  def receive = {
    case ExecuteJs(source, args, timeout, timeoutExitValue, environment) =>
      val requester = sender()

      val stdinSink = context.actorOf(BufferingSink.props(ioDispatcherId = ioDispatcherId), "stdin")
      val stdinIs = new SourceStream(stdinSink, StdioTimeout)
      val stdoutSource = context.actorOf(ForwardingSource.props(self, ioDispatcherId = ioDispatcherId), "stdout")
      val stdoutOs = new SinkStream(stdoutSource, StdioTimeout)
      val stderrSource = context.actorOf(ForwardingSource.props(self, ioDispatcherId = ioDispatcherId), "stderr")
      val stderrOs = new SinkStream(stderrSource, StdioTimeout)

      try {
        context.become(engineIOHandler(
          stdinSink, stdoutSource, stderrSource,
          requester,
          Ack,
          timeout, timeoutExitValue
        ))

        context.actorOf(JavaxEngineShell.props(
          source.getCanonicalFile,
          stdArgs ++ args,
          stdinIs, stdoutOs, stderrOs,
          engineName
        ), "javax-engine-shell") ! JavaxEngineShell.Execute

      } finally {
        // We don't need stdin
        blocking(Try(stdinIs.close()))
      }
  }
}

object JavaxEngine {

  /**
   * Creates the Props of a JavaxEngine
   *
   * @param stdArgs
   * @param ioDispatcherId
   * @param engineName The name of the engine to load. Defaults to "js".
   * @return
   */
  def props(
      stdArgs: immutable.Seq[String] = Nil,
      ioDispatcherId: String = "blocking-process-io-dispatcher",
      engineName: String = "js") =
    Props(new JavaxEngine(stdArgs, ioDispatcherId, engineName)).withDispatcher(ioDispatcherId)

}

private[jse] class JavaxEngineShell(
    script: File,
    args: immutable.Seq[String],
    stdinIs: InputStream,
    stdoutOs: OutputStream,
    stderrOs: OutputStream,
    engineName: String
  ) extends Actor with ActorLogging {

  import JavaxEngineShell._

  val engine = new ScriptEngineManager(null).getEngineByName(engineName)

  if (engine == null) throw new Exception(s"Javascript engine '$engineName' not found")

  def receive = {

    case Execute =>

      val scriptReader = new FileReader(script)

      val reader = new InputStreamReader(stdinIs)
      val writer = new PrintWriter(stdoutOs, true)
      val errorWriter = new PrintWriter(stderrOs, true)

      val context = {
        val c: ScriptContext = new SimpleScriptContext()
        c.setReader(reader)
        c.setWriter(writer)
        c.setErrorWriter(errorWriter)
        // If you create a new ScriptContext object and use it to evaluate scripts, then
        // ENGINE_SCOPE of that context has to be associated with a nashorn Global object somehow.
        // See https://wiki.openjdk.java.net/display/Nashorn/Nashorn+jsr223+engine+notes
        c.setBindings(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.ENGINE_SCOPE)
        c.setAttribute("arguments", args.toArray, ScriptContext.ENGINE_SCOPE)
        c.setAttribute(ScriptEngine.FILENAME, script.getName, ScriptContext.ENGINE_SCOPE)
        c
      }

      try {
        blocking(engine.eval(scriptReader, context))
        sender() ! 0
      } catch {
        case e: ScriptException =>
          e.printStackTrace(new PrintStream(stderrOs))
          sender() ! 1
      } finally {
        // Will close the underlying stdoutOs and stderrOs
        Try(writer.close())
        Try(errorWriter.close())
      }

  }

}

private[jse] object JavaxEngineShell {

  def props(
      source: File,
      args: immutable.Seq[String],
      stdinIs: InputStream,
      stdoutOs: OutputStream,
      stderrOs: OutputStream,
      engineName: String) =
    Props(new JavaxEngineShell(source, args, stdinIs, stdoutOs, stderrOs, engineName))

  case object Execute

}
