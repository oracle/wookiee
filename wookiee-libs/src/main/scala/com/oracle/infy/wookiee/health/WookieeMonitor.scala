package com.oracle.infy.wookiee.health

import com.oracle.infy.wookiee.component.ComponentInfo
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ClassUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * This trait is used to enable health checks on a class. All Component and Service classes
  * extend it by default and can override `getHealth` to define their own health check. Outside
  * of Component/Service extensions you can mix this trait into any class to enable health checks
  * as long as that class is in the chain of classes that link down from `getDependentHealths`.
  */
trait WookieeMonitor extends LoggingAdapter {
  // Override this with the name of the health component to show up in the check
  val name: String = ClassUtil.getSimpleNameSafe(this.getClass)

  /**
    * Only hit automatically within Component and Service classes
    * Starts the service/component, called right away
    * Will be called on any dependent components as well
    */
  def start(): Unit =
    log.info(s"WM100: [$name] is starting")

  /**
    * Only hit automatically within Component and Service classes
    * Any logic to run once all (non-hot deployed) components are up
    * Will be called on any dependent components as well
    */
  def systemReady(): Unit =
    log.debug(s"System Ready called on [$name]")

  /**
    * Can override to act when another Wookiee Component comes up. This method will be hit when each
    * other Component in the Service has Started. It will also be back-filled with any Components that
    * reached the Started state before this Service, so no worries about load order. Great for when
    * one needs to use another Component without the need for custom waiting logic, and convenient
    * since it provides the actorRef of that Started Component
    *
    * Will be called on any dependent components as well
    *
    * @param info Info about the Component that is ready for interaction, name and actor ref.
    *             Note: The 'state' will always be Started
    */
  def onComponentReady(info: ComponentInfo): Unit =
    log.debug(s"Component Ready called on [$name] for [${info.name}]")

  /**
    * Should return a list of child components that should be scanned and:
    * 1. Checked for health and aggregated along with this class's health
    * 2. Will propagate the prepareForShutdown message to all children
    * 3. Will propagate the systemReady message to all children
    * 4. Will propagate the start message to all children
    */
  def getDependents: Iterable[WookieeMonitor] = List()

  /**
    * This is the health of the current object, by default will be NORMAL
    * In general this should be overridden to define the health of the current object
    * For objects that simply manage other objects you shouldn't need to do anything
    * else, as the health of the children components would be handled by their own
    * getHealth function
    */
  def getHealth: Future[HealthComponent] =
    Future.successful(HealthComponent(name, ComponentState.NORMAL, "Healthy"))

  /**
    * Any logic to run once we get the shutdown message but before we begin killing executors.
    * Will be called for all dependent components as well. Be sure to call super.prepareForShutdown
    * after (or before if dependencies should shutdown first) any custom logic.
    */
  def prepareForShutdown(): Unit =
    log.debug(s"Prepare for shutdown called on [$name]")

  // Internal but called from outside
  def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()

    getHealth.onComplete {
      case Success(s) =>
        p completeWith checkHealthOfChildren(s, this)
      case Failure(f) =>
        log.warn(s"WM402: Failed to get health from component: [$name]", f)
        p success HealthComponent(name, ComponentState.CRITICAL, f.getMessage)
    }

    p.future
  }

  // Go through each `getDependentHealths`
  protected def checkHealthOfChildren(
      parentStatus: HealthComponent,
      current: WookieeMonitor
  ): Future[HealthComponent] = {
    val healthFutures = current.getDependents map { ref =>
      ref
        .getHealth
        .recover({
          case ex: Exception =>
            log.warn(s"WM400: Parent [$name] failed to get health of child component: [${ref.name}]", ex)
            HealthComponent(
              ref.name,
              ComponentState.CRITICAL,
              s"Failure to get health of child component. ${ex.getMessage}"
            )
        })
        .map(res => (ref, res)) // Hold onto the class reference as well as the health result
    }

    // Will recurse through this WookieeMonitor object's health then each child down the tree
    Future.sequence(healthFutures) flatMap { childrenAndHealths =>
      childrenAndHealths foreach { it =>
        parentStatus.addComponent(it._2)
      }
      Future.sequence(childrenAndHealths map { it =>
        checkHealthOfChildren(it._2, it._1)
      }) map { _ =>
        parentStatus
      }
    } recover {
      case f: Throwable =>
        log.warn(s"WM401: Parent [$name] failed to retrieve health of children objects", f)
        HealthComponent(
          name,
          ComponentState.CRITICAL,
          s"Failure to get health of child components. ${f.getMessage}"
        )

    }
  }

  // Internal but called from outside, will call prepareForShutdown() on this and all dependents
  protected[oracle] def propagateOnComponentReady(info: ComponentInfo): Unit =
    propogateCall({ mon =>
      mon.onComponentReady(info)
    }, "Error in onComponentReady")

  // Internal but called from outside, will call prepareForShutdown() on this and all dependents
  protected[oracle] def propagatePrepareForShutdown(): Unit =
    propogateCall({ mon =>
      mon.prepareForShutdown()
    }, "Error in prepareForShutdown")

  // Internal but called from outside, will call start() on this and all dependents
  protected[oracle] def propagateStart(): Unit =
    propogateCall({ mon =>
      mon.start()
    }, "Error in start")

  // Internal but called from outside, will call systemReady() on this and all dependents
  protected[oracle] def propagateSystemReady(): Unit =
    propogateCall({ mon =>
      mon.systemReady()
    }, "Error in system ready")

  // Useful util to iterate through all dependents and call a function
  protected[oracle] def propogateCall(call: WookieeMonitor => Unit, errorMsg: String): Unit =
    try {
      call(this)
      getDependents.foreach(dep => dep.propogateCall(call, errorMsg))
    } catch {
      case ex: Throwable =>
        log.error(s"WM404: Unexpected exception in [$name]: $errorMsg", ex)
    }
}
