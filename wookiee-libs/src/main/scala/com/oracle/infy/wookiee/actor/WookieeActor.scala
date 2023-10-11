package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.actor.WookieeActor._
import com.oracle.infy.wookiee.actor.mailbox.WookieeDefaultMailbox
import com.oracle.infy.wookiee.actor.router.{RoundRobinRouter, WookieeActorRouter}
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.oracle.infy.wookiee.service.messages._

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.{Failure, Success}

object WookieeActor {
  type Receive = PartialFunction[Any, Unit]
  sealed trait ManagementMessage
  case class PreStart() extends ManagementMessage
  case class PoisonPill() extends ManagementMessage

  // Use this to create a WookieeActorRouter which will route messages to multiple actors
  // Useful for when parallelism is needed but you don't want to create a new actor for each message
  def withRouter(
      actorMaker: => WookieeActor,
      routees: Int = 5
  ): WookieeActorRouter =
    new RoundRobinRouter(routees, actorMaker)

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
trait WookieeActor extends WookieeOperations with WookieeMonitor with WookieeDefaultMailbox {
  // Used to send this actor along as the sender() in classic actor methods
  implicit val thisActor: WookieeActor = this
  private val lastSender: AtomicReference[WookieeActor] = new AtomicReference[WookieeActor](this)

  protected[wookiee] lazy val receiver: AtomicReference[Receive] =
    new AtomicReference[Receive](receive)

  // Call preStart on initialization
  self ! PreStart()

  /* Overrideable Classic Actor Methods */

  // Classic receive method that will take in messages of any type, one at a time (unless
  // one enters a Future during execution which will cause the next message to begin processing).
  // For health checks to work, be sure to override with super.receive orElse { ... }
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
  // and hasn't been overridden (call super.prepareForShutdown if you override)
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
      case Failure(ex) =>
        toSend match {
          case _: AskInterceptor =>
            toSend ! ex
          case _ =>
            log.error("WA501: Error in processing of message", ex)
        }
    })
  }

  private lazy val stashQueue = new ConcurrentLinkedQueue[(Any, WookieeActor)]()

  // Stashes messages into a separate queue to be processed later
  // Note that the queue is unbounded so eventually you'll have to unstash or face a memory leak
  protected def stash(message: Any)(implicit sender: WookieeActor = null): Unit = {
    stashQueue.offer((message, sender))
    ()
  }

  // Empty the stash queue into the main messages queue
  protected def unstashAll(): Unit = lockedOperation {
    var stashed = Option(stashQueue.poll())
    while (stashed.isDefined) {
      stashed.foreach(msg => this.!(msg._1)(msg._2))
      stashed = Option(stashQueue.poll())
    }
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
            case (_: PreStart, _) =>
              lastSender.set(noSender)
              preStart()
            case (PoisonPill, _) =>
              lastSender.set(noSender)
              postStop()
            case (_: PoisonPill, _) =>
              lastSender.set(noSender)
              postStop()
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
  def ?(message: Any)(implicit timeout: FiniteDuration = 60.seconds, sender: WookieeActor = noSender): Future[Any] = {
    val promise: Promise[Any] = Promise[Any]()
    this.!(message)(AskInterceptor(promise, Option(sender)))
    scheduleOnce(timeout)({
      promise.tryFailure(
        new TimeoutException(s"WA503: [$name] Timeout in processing of request [$message] from [${sender.name}] to ")
      )
      ()
    })
    promise.future
  }

  /* Internal Methods */

  // Called on actors below a Component or Service that are registered in that entities `getDependents` method
  override def prepareForShutdown(): Unit = postStop()

  // Used to ignoring replies when the sender was not specified
  protected lazy val noSender: WookieeActor = new WookieeActor {
    override val name: String = "NoSender"

    // Discard the reply
    override def receive: Receive = {
      case _ =>
    }
  }
}
