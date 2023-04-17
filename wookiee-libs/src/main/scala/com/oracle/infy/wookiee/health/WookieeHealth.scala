package com.oracle.infy.wookiee.health

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
trait WookieeHealth extends LoggingAdapter {
  // Override this with the name of the health component to show up in the check
  val name: String = ClassUtil.getSimpleNameSafe(this.getClass)

  /**
    * Should return a list of child components that should be checked for health
    * and aggregated along with this class's health
    */
  def getDependentHealths: Iterable[WookieeHealth] = List()

  /**
    * This is the health of the current object, by default will be NORMAL
    * In general this should be overridden to define the health of the current object
    * For objects that simply manage other objects you shouldn't need to do anything
    * else, as the health of the children components would be handled by their own
    * CheckHealth function
    */
  def getHealth: Future[HealthComponent] =
    Future.successful(HealthComponent(name, ComponentState.NORMAL, "Healthy"))

  // Internal
  protected def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()

    getHealth.onComplete {
      case Success(s) =>
        p completeWith checkHealthOfChildren(s, this)
      case Failure(f) =>
        log.warn(s"WH402: Failed to get health from component: [$name]", f)
        p success HealthComponent(name, ComponentState.CRITICAL, f.getMessage)
    }

    p.future
  }

  // Go through each `getDependentHealths`
  protected def checkHealthOfChildren(
      parentStatus: HealthComponent,
      current: WookieeHealth
  ): Future[HealthComponent] = {
    val healthFutures = current.getDependentHealths map { ref =>
      ref
        .getHealth
        .recover({
          case ex: Exception =>
            log.warn(s"WH400: Parent [$name] failed to get health of child component: [${ref.name}]", ex)
            HealthComponent(
              ref.name,
              ComponentState.CRITICAL,
              s"Failure to get health of child component. ${ex.getMessage}"
            )
        })
        .map(res => (ref, res))
    }

    // Will recurse through this WookieeHealth object's health then each child down the tree
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
        log.warn(s"WH401: Parent [$name] failed to retrieve health of children objects", f)
        HealthComponent(
          name,
          ComponentState.CRITICAL,
          s"Failure to get health of child components. ${f.getMessage}"
        )

    }
  }
}
