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

  // Used by the ask (?) method to intercept the reply and use it to complete our Future
  protected[oracle] case class AskInterceptor(promise: Promise[Any], theSender: Option[WookieeActor])
      extends WookieeActor {

    protected override def receive: Receive = {
      case ex: Throwable =>
        promise.tryFailure(ex)
        ()
      case message: Any =>
        // Try to complete the future, if it's already done then send message along via normal route
        if (!promise.trySuccess(message) && theSender != null)
          theSender.foreach(_ ! message)
        ()
    }
  }
}

/**
  * This is the base trait for all Wookiee actors. It provides all the same functionality as a classic actor.
  * This actor is not run inside of any actor system. It contains a receive method through which any message (type) can
  * be handled. The actor is capable of changing its receive method via the become(..) method. It also contains
  * a scheduler that can be used to schedule messages to be sent to any actor.
  *
  * It is possible to mix in another extension of WookieeDefaultMailbox to change the mailbox implementation.
  */
trait WookieeActor extends WookieeOperations with WookieeMonitor with WookieeScheduler with WookieeDefaultMailbox {
  // Used to send this actor along as the sender() in classic actor methods
  implicit val thisActor: WookieeActor = this
  implicit val ec: ExecutionContext = futureExecutor
  private val lastSender: AtomicReference[WookieeActor] = new AtomicReference[WookieeActor](this)

  private lazy val receiver: AtomicReference[Receive] =
    new AtomicReference[Receive](receive)

  /* Overrideable Classic Actor Methods */

  // Classic receive method that will take in messages of any type, one at a time (unless
  // one enters a Future during execution which will cause the next message to begin processing).
  protected def receive: Receive = health

  // Used to swap out the function that constitutes the receive method, useful for changing states
  protected def become(behavior: Receive): Unit = lockedOperation {
    receiver.set(behavior)
  }

  // The sender of the current message, this value could change if you enter a Future so be sure
  // to save it locally before doing so
  protected def sender(): WookieeActor = lastSender.get()

  // Classic actor start method that will be called when WookieeMonitor.start is called
  protected def preStart(): Unit = {}

  // Classic actor stop method that will be called when WookieeMonitor.prepareForShutdown is called
  protected def postStop(): Unit = {}

  /* Utility Methods */

  // Used to send this actor along as the sender() in classic actor methods, simply a self reference
  protected def self: WookieeActor = this

  // Useful method to evaluate a future and pipe the result back to the sender
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

  // Always add this to your receive method to enable health checks
  protected def health: Receive = {
    case CheckHealth =>
      pipe(checkHealth)
  }

  /* User Facing Methods */

  // The name of the actor, used for logging and health checks
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

  // Classic actor method to request a response from another actor
  // Will succeed when another actor has sent back any message, though further
  // messages may come back which will be handled via the normal receive path.
  def ?(message: Any)(implicit sender: WookieeActor = null): Future[Any] = {
    val promise: Promise[Any] = Promise[Any]()
    this.!(message)(AskInterceptor(promise, Option(sender)))
    promise.future
  }

  /* Internal Methods */

  // Called on actors below a Component or Service that are registered in that entities `getDependents` method
  override def start(): Unit = preStart()

  // Called on actors below a Component or Service that are registered in that entities `getDependents` method
  override def prepareForShutdown(): Unit = postStop()

  // Used to ignoring replies when the sender was not specified
  protected lazy val noSender: WookieeActor = new WookieeActor {

    // Discard the reply
    override def receive: Receive = {
      case _ =>
    }
  }
}