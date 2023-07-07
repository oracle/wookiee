package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.actor.WookieeActor._
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.oracle.infy.wookiee.service.messages._
import com.oracle.infy.wookiee.utils.ThreadUtil

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object WookieeActor {
  type Receive = PartialFunction[Any, Unit]

  protected[oracle] val futureExecutor: ExecutionContext = ThreadUtil.createEC("WookieeActorFutureExecutor")

  protected[oracle] case class AskInterceptor(promise: Promise[Any]) extends WookieeActor {

    protected override def receive: Receive = {
      case ex: Throwable =>
        promise.tryFailure(ex)
        ()
      case message: Any =>
        promise.trySuccess(message)
        ()
    }
  }
}

trait WookieeActor extends WookieeOperations with WookieeMonitor with WookieeScheduler with WookieeDefaultMailbox {
  implicit val thisActor: WookieeActor = this
  implicit val ec: ExecutionContext = futureExecutor
  private val lastSender: AtomicReference[WookieeActor] = new AtomicReference[WookieeActor](this)

  private lazy val receiver: AtomicReference[Receive] =
    new AtomicReference[Receive](receive)

  /* Overrideable Classic Actor Methods */

  protected def receive: Receive = health

  protected def become(behavior: Receive): Unit = lockedOperation {
    receiver.set(behavior)
  }

  protected def sender(): WookieeActor = lastSender.get()

  protected def preStart(): Unit = {}

  protected def postStop(): Unit = {}

  /* Utility Methods */

  protected def self: WookieeActor = this

  protected def pipe(future: Future[Any]): Unit = {
    val toSend = lastSender.get()
    future.onComplete({
      case Success(message) =>
        toSend ! message
      case Failure(ex) if toSend.isInstanceOf[AskInterceptor] =>
        toSend ! ex
      case Failure(ex) =>
        log.error("WA500: Error in processing of message", ex)
    })
  }

  protected def health: Receive = {
    case CheckHealth =>
      pipe(checkHealth)
  }

  /* User Facing Methods */

  def path: String = name

  // Set sender to null if not sending from an actor or if we don't expect responses
  def !(message: Any)(implicit sender: WookieeActor = null): Unit = {
    enqueueMessage(message)(sender)
    Future {
      tryAndLogError[Unit](
        lockedOperation {
          dequeueMessage() match {
            case (msg, null) =>
              lastSender.set(noSender)
              receiver.get()(msg)
            case (msg, interceptor: AskInterceptor) =>
              try {
                lastSender.set(interceptor)
                receiver.get()(msg)
              } catch {
                case ex: Throwable =>
                  log.warn(s"WA502: [$name] Uncaught error in processing of request [$msg]", ex)
                  interceptor.promise.failure(ex)
              }
            case (msg, theSender) =>
              lastSender.set(theSender)
              receiver.get()(msg)
          }
          ()
        },
        Some(s"WA501: [$name] Uncaught error in processing of message [$message]")
      ).getOrElse({})
    }
    ()
  }

  def ?(message: Any): Future[Any] = {
    val promise: Promise[Any] = Promise[Any]()
    this.!(message)(AskInterceptor(promise))
    promise.future
  }

  /* Internal Methods */

  // Called on actors below a Component or Service that are registered in that entities `getDependents` method
  override def start(): Unit = preStart()

  // Called on actors below a Component or Service that are registered in that entities `getDependents` method
  override def prepareForShutdown(): Unit = postStop()

  protected lazy val noSender: WookieeActor = new WookieeActor {

    // Discard the reply
    override def receive: Receive = {
      case _ =>
    }
  }
}
