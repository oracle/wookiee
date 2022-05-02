/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.zookeeper

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.NodeRegistration._
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperActor._
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperEvent.Internal._
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperEvent._
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperService._
import com.oracle.infy.wookiee.component.zookeeper.config.ZookeeperSettings
import com.oracle.infy.wookiee.component.zookeeper.discoverable.DiscoverableService._
import com.oracle.infy.wookiee.health.{ActorHealth, ComponentState, HealthComponent}
import com.oracle.infy.wookiee.logging.ActorLoggingAdapter
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{BackgroundCallback, CuratorEvent}
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong
import org.apache.curator.framework.recipes.cache.{ChildData, CuratorCache, CuratorCacheListener}
import org.apache.curator.framework.recipes.leader.{LeaderLatch, LeaderLatchListener}
import org.apache.curator.framework.state.{ConnectionState, ConnectionStateListener}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.{NoNodeException, NodeExistsException}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

object ZookeeperActor {
  @SerialVersionUID(1L) private[zookeeper] case class GetSetWeightInterval()
  @SerialVersionUID(1L) private[zookeeper] case class GetLeaderRegistrars(path: String, namespace: Option[String])

  def props(settings: ZookeeperSettings, clusterEnabled: Boolean = false)(implicit system: ActorSystem): Props =
    Props(classOf[ZookeeperActor], settings, clusterEnabled)
}

class ZookeeperActor(settings: ZookeeperSettings, clusterEnabled: Boolean = false)
    extends Actor
    with ActorLoggingAdapter
    with ActorHealth
    with ConnectionStateListener
    with CuratorCacheListener
    with Stash {

  implicit val actorSystem: ActorSystem = context.system
  import context.dispatcher
  val registrationPath = s"${getBasePath(settings)}/nodes/$getAddress"
  val utf8: Charset = Charset.forName("UTF-8")

  private case class RegisterNode()
  private case class WeightKey(basePath: String, id: String)
  private case class WeightState(current: Int, stored: Option[Int])
  private case object SetWeight

  protected val setWeightInterval: Long =
    context.system.settings.config.getDuration("discoverability.set-weight-interval", SECONDS)

  private var currentState = ConnectionState.LOST
  private var stateRegistrars: Set[ActorRef] = Set.empty
  private var childRegistrars: Map[(String, Option[String]), CacheEntry] = Map.empty
  private var leadershipRegistrars: Map[(String, Option[String]), LeaderEntry] = Map.empty
  private var weightRegistrars: Map[WeightKey, WeightState] = Map.empty

  protected val curator: Curator = Curator(settings)
  protected val callback = new DefaultCallback
  @SerialVersionUID(1L) private case class CacheEntry(cache: CuratorCache, registrars: Set[ActorRef])

  @SerialVersionUID(1L) private case class LeaderEntry(leader: LeaderLatch, registrars: Set[ActorRef])

  @SerialVersionUID(1L) protected case class StateChanged(state: ConnectionState)

  override def preStart(): Unit = {
    log.info(s"Starting Curator client ${self.path}")

    Try({
      // Register as a handler
      ZookeeperService.registerMediator(self)(context.system)
      startCurator()

      context
        .system
        .scheduler
        .scheduleWithFixedDelay(setWeightInterval.seconds, setWeightInterval.seconds, self, SetWeight)

    }).recover({
      case e: Exception => log.error("An error occurred trying to start curator.", e)
    })
    ()
  }

  override def postStop(): Unit = {
    // Un-register our self
    unregisterNode()
    // Un-register as a handler
    ZookeeperService.unregisterMediator(context.system)
    // We are stopped so shutdown curator
    stopCurator()
    log.info("Curator client stopped")
  }

  override def receive: Receive = initializing

  def initializing: Receive = baseProcessing orElse {
    case _ => stash() // Stash everything else for now
  }

  def baseProcessing: Receive = health orElse {
    case StateChanged(state) =>
      // Handle the connection state
      handleStateChange(state)
  }

  /**
    * This is the main processing handler
    */
  def processing: Receive = baseProcessing orElse {
    // Set the weight we've been collecting
    case SetWeight => setAllWeights()
    // Set the data for the given path
    case SetPathData(path, data, create, ephemeral, optNamespace, async) =>
      setData(path, data, create, ephemeral, optNamespace, async); ()
    // Get data for the given path
    case GetPathData(path, optNamespace) =>
      getData(path, optNamespace)
    // Get data for the given path
    case GetOrSetPathData(path, data, ephemeral, optNamespace) =>
      getOrSetData(path, data, ephemeral, optNamespace)
    // Get children for the given path
    case GetPathChildren(path, includeData, optNamespace) =>
      getChildren(path, includeData, optNamespace)
    // Check if a node exists
    case GetNodeExists(path, optNamespace) =>
      nodeExists(path, optNamespace)
    // Create a node
    case CreateNode(path, createMode, data, optNamespace) =>
      createNode(path, createMode, data, optNamespace)
    // Delete a node
    case DeleteNode(path, optNamespace) =>
      deleteNode(path, optNamespace)
    // query for service names
    case QueryForNames(basePath) =>
      queryForNames(basePath)
    // Update weight
    case UpdateWeight(weight, basePath, id, forceSet) =>
      updateWeight(weight, basePath, id, forceSet)
    // query for service instances
    case QueryForInstances(basePath, id) =>
      queryForInstances(basePath, id)
    // make service discoverable
    case MakeDiscoverable(basePath, id, uriSpec) =>
      makeDiscoverable(basePath, id, uriSpec)
    // get a single instance from the provider
    case GetInstance(basePath, name) =>
      getInstance(basePath, name)
    // get all the instances from the provider
    case GetAllInstances(basePath, name) =>
      allInstances(basePath, name)
    // get all the instances from the provider
    case _: GetRegistrationPath =>
      sender() ! registrationPath
    // counter creation
    case CreateCounter(basePath) =>
      sender() ! new DistributedAtomicLong(curator.client, basePath, new ExponentialBackoffRetry(1000, 10))
    // Registration messages
    case r: RegisterZookeeperEvent =>
      registerForEvents(r); ()
    case ur: UnregisterZookeeperEvent =>
      unregisterForEvents(ur)
    case _: GetSetWeightInterval =>
      sender() ! setWeightInterval
    case GetLeaderRegistrars(path, optNamespace) =>
      sender() ! leadershipRegistrars
        .get((path, optNamespace))
        .map {
          _.registrars
        }
        .getOrElse(Nil)
    case RegisterNode =>
      // Re-register our self
      registerNode(clusterEnabled)
  }

  private def getClientContext(namespace: Option[String]): CuratorFramework = namespace match {
    case Some(space) => curator.client.usingNamespace(space)
    case None        => curator.client
  }

  private def getChildren(path: String, includeData: Boolean, namespace: Option[String]): Unit = {
    try {
      val nodes = for {
        child <- getClientContext(namespace).getChildren.forPath(path).asScala
        data = if (includeData) Some(getClientContext(namespace).getData.forPath(s"$path/$child")) else None
      } yield (child, data)
      sender() ! nodes.toSeq
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred trying to fetch children from the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def getData(path: String, namespace: Option[String]): Unit = {
    try {
      sender() ! getClientContext(namespace).getData.forPath(path)
    } catch {
      case nn: NoNodeException =>
        log.debug("No node found for path {}", path)
        sender() ! Status.Failure(nn)
      case e: Exception =>
        log.error(e, "An error occurred trying to fetch data from the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def setData(
      path: String,
      data: Array[Byte],
      create: Boolean,
      ephemeral: Boolean,
      namespace: Option[String],
      async: Boolean
  ) = {
    def nodeCreate: String = {
      try {
        val mode = if (ephemeral) CreateMode.EPHEMERAL else CreateMode.PERSISTENT
        getClientContext(namespace).create.creatingParentsIfNeeded.withMode(mode).forPath(path)
      } catch {
        case _: NodeExistsException => path
        case e: Exception =>
          log.error(e, "An error occurred trying to create a node for the path {}", path)
          path
      }
    }

    val dataPath = if (create) nodeCreate else path

    try {
      if (async) {
        getClientContext(namespace).setData().inBackground(callback).forPath(dataPath, data)
      } else {
        getClientContext(namespace).setData().forPath(dataPath, data)
        sender() ! data.length
      }
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred trying to set data for the path {}", path)
        if (!async) sender() ! Status.Failure(e)
    }
  }

  private def getOrSetData(path: String, data: Array[Byte], ephemeral: Boolean, namespace: Option[String]): Unit = {
    try {
      sender() ! getClientContext(namespace).getData.forPath(path)
    } catch {
      case _: NoNodeException =>
        try {
          val mode = if (ephemeral) CreateMode.EPHEMERAL else CreateMode.PERSISTENT
          getClientContext(namespace).create.creatingParentsIfNeeded.withMode(mode).forPath(path, data)
          sender() ! data
        } catch {
          case e: Exception =>
            log.error(e, "An error occurred trying to get or set data for the path {}", path)
            sender() ! Status.Failure(e)
        }

      case e: Exception =>
        log.error(e, "An error occurred trying to get or set data for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def nodeExists(path: String, namespace: Option[String]): Unit = {
    try {
      sender() ! (getClientContext(namespace).checkExists.forPath(path) != null)
    } catch {
      case _: NoNodeException => sender().tell(false, self)
      case e: Exception =>
        log.error(e, "An error occurred trying to create a node for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def createNode(path: String, mode: CreateMode, data: Option[Array[Byte]], namespace: Option[String]): Unit = {
    try {
      sender() ! getClientContext(namespace)
        .create
        .creatingParentsIfNeeded
        .withMode(mode)
        .forPath(path, data.getOrElse(Array.empty[Byte]))
    } catch {
      case _: NodeExistsException => sender() ! path
      case e: Exception =>
        log.error(e, "An error occurred trying to create a node for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def deleteNode(path: String, namespace: Option[String]): Unit = {
    try {
      getClientContext(namespace).delete.deletingChildrenIfNeeded().forPath(path)
      sender() ! path
    } catch {
      case _: NoNodeException => sender() ! path // Swallow
      case e: Exception =>
        log.error(e, "An error occurred trying to delete a node for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def queryForNames(basePath: String): Unit = {
    try {
      // List[String]
      sender() ! curator.discovery(basePath).queryForNames().asScala.toList
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred trying to query for names")
        sender() ! Status.Failure(e)
    }
  }

  private def queryForInstances(basePath: String, id: String): Unit = {
    try {
      // List[ServiceInstance[WookieeServiceDetails]]
      val query = curator.discovery(basePath).queryForInstances(id).asScala.toList
      sender() ! query
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred trying to query for instances")
        sender() ! Status.Failure(e)
    }
  }

  private def unregisterNode(): Unit = {
    deleteNode(registrationPath, None)
    log.info(s"Unregistered self at path: $registrationPath")
  }

  private def registerNode(clusterEnabled: Boolean): Unit = {
    val path = registrationPath

    if (doRegisterSelf()) {
      // Delete the node first
      Try {
        getClientContext(None).delete.deletingChildrenIfNeeded().forPath(path)
        log.info(s"Cleaning out self in zookeeper on path: [$path]")
      } recover {
        case _ =>
          log.info(s"Node [$path] could not be deleted on registration, probably was cleaned up on shutdown.")
      }

      val json = compact(render(("address" -> getAddress) ~ ("cluster-enabled" -> clusterEnabled)))

      Try {
        getClientContext(None)
          .create
          .creatingParentsIfNeeded
          .withMode(CreateMode.EPHEMERAL)
          .forPath(path, json.getBytes(utf8))
        log.info(s"Registering self in zookeeper on path: [$path]")
      } recover {
        case t =>
          log.error(t, "Failed to create node registration for {}", path)
      }
      ()
    } else log.info(s"register-self set to 'false', not registering on path [$registrationPath]")
  }

  protected def setAllWeights(): Unit = {
    weightRegistrars.filter { case (_, ws) => ws.stored.isEmpty || ws.current != ws.stored.get }.foreach {
      case (key, weight) =>
        setWeight(key, weight)
    }
  }

  protected def setWeight(key: WeightKey, weight: WeightState): Unit = {
    try {
      curator.discovery(key.basePath, key.id) match {
        case None =>
        case Some(d) =>
          val instance = d.queryForInstances(key.id).asScala.headOption
          instance.foreach(_.getPayload.setWeight(weight.current))
          weightRegistrars += key -> WeightState(weight.current, Some(weight.current))
          instance.foreach(d.updateService)
      }
    } catch {
      case e: Exception =>
        log.error(e, s"An error occurred while trying to update weight for ${key.basePath}/${key.id}")
    }
  }

  private def updateWeight(weight: Int, basePath: String, id: String, forceSet: Boolean): Unit = {
    val key = WeightKey(basePath, id)
    val storedWeight = weightRegistrars.get(key).flatMap(w => w.stored)
    val weightState = WeightState(weight, storedWeight)
    weightRegistrars += key -> weightState
    if (forceSet) {
      setWeight(key, weightState)
    }
    sender() ! true
  }

  private def makeDiscoverable(basePath: String, id: String, uriSpec: UriSpec): Unit = {
    try {
      if (curator.discovery(basePath).queryForInstances(id).isEmpty) {
        val builder = ServiceInstance
          .builder[WookieeServiceDetails]()
          .name(id)
          .payload(new WookieeServiceDetails(0))
          .uriSpec(uriSpec)

        val instance = builder.build()

        curator.registerService(basePath, instance)
        log.info(s"Service is now discoverable ${instance.toString}")
        sender() ! true
      } else {
        log.info(s"Not making {$id} discoverable as it already is")
        sender() ! false
      }
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred while trying to make discoverable")
        sender() ! Status.Failure(e)
    }
  }

  private def getInstance(basePath: String, name: String): Unit = {
    try {
      sender() ! curator.createServiceProvider(basePath, name).getInstance()
    } catch {
      case e: Exception =>
        log.error(s"An error occurred while trying to get an instance from the discoverable provider: ${e.getMessage}")
        sender() ! Status.Failure(e)
    }
  }

  private def allInstances(basePath: String, name: String): Unit = {
    try {
      sender() ! curator.createServiceProvider(basePath, name).getAllInstances.asScala.toList
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred while trying to get all instances from the discoverable provider")
        sender() ! Status.Failure(e)
    }
  }

  protected def handleStateChange(state: ConnectionState): Unit = {
    log.info("Zookeeper connection state changed to {}", state.name)
    currentState = state

    if (currentState == ConnectionState.RECONNECTED || currentState == ConnectionState.CONNECTED) {
      unstashAll()
      context.become(processing)
    } else {
      context.become(initializing)
    }

    // Now update all of the registered listeners
    stateRegistrars foreach { act =>
      act ! ZookeeperStateEvent(currentState)
    }

    state match {
      case ConnectionState.RECONNECTED =>
        // Re-register our self
        self ! RegisterNode
      case ConnectionState.CONNECTED =>
        // Re-register our self
        self ! RegisterNode
      case _ => // Do nothing
    }
  }

  private def registerForEvents(reg: RegisterZookeeperEvent) = {
    reg.to match {
      case ZookeeperStateEventRegistration(registrar) =>
        stateRegistrars += registrar
        // Send the current state of ZK to the registrar
        registrar ! ZookeeperStateEvent(currentState)

      case ZookeeperChildEventRegistration(registrar, path, optNamespace) =>
        // Update the child registrations
        childRegistrars
          .get((path, optNamespace))
          .map { entry =>
            if (!entry.registrars.contains(registrar)) {
              childRegistrars.updated((path, optNamespace), entry.copy(registrars = entry.registrars ++ Set(registrar)))
            }
          }
          .getOrElse {
            Try({
              // Adding a new cache
              val cache = CuratorCache.build(getClientContext(optNamespace), path)
              cache.listenable().addListener(this)
              cache.start()
              childRegistrars += ((path, optNamespace) -> CacheEntry(cache, Set(registrar)))
            }).recover({
              case e: Exception =>
                log.error(s"An error occurred trying to create a path listener for the path $path", e)
            })
          }

      case ZookeeperLeaderEventRegistration(registrar, path, optNamespace) =>
        // Update the leadership registrations
        leadershipRegistrars
          .get((path, optNamespace))
          .map { entry =>
            if (!entry.registrars.contains(registrar)) {
              leadershipRegistrars.updated(
                (path, optNamespace),
                entry.copy(registrars = entry.registrars ++ Set(registrar))
              )
            }
          }
          .getOrElse {
            Try({
              // Adding a new leader selector
              val sel = new LeaderLatch(getClientContext(optNamespace), path)
              sel.addListener(new LeaderListener(path, self, optNamespace))
              sel.start()
              leadershipRegistrars += ((path, optNamespace) -> LeaderEntry(sel, Set(registrar)))
            }).recover({
              case e: Exception =>
                log.error(s"An error occurred trying to create a leader selector for the path $path", e)
            })
          }
    }

  }

  private def unregisterForEvents(reg: UnregisterZookeeperEvent): Unit = {
    reg.to match {
      case ZookeeperStateEventRegistration(registrar) => stateRegistrars -= registrar
      case ZookeeperChildEventRegistration(registrar, path, optNamespace) =>
        childRegistrars.get((path, optNamespace)).foreach { entry =>
          if (entry.registrars.contains(registrar)) {
            val newEntry = (path, optNamespace) -> entry.copy(registrars = entry.registrars.filterNot(_ == registrar))
            if (newEntry._2.registrars.isEmpty) {
              // No more registrars so we can shutdown the cache
              entry.cache.listenable().removeListener(this)
              entry.cache.close()
              val key = (path, optNamespace)
              childRegistrars -= key
            } else {
              childRegistrars += newEntry
            }
          }
        }

      case ZookeeperLeaderEventRegistration(registrar, path, optNamespace) =>
        leadershipRegistrars.get((path, optNamespace)).foreach { entry =>
          if (entry.registrars.contains(registrar)) {
            val newEntry = (path, optNamespace) -> entry.copy(registrars = entry.registrars.filterNot(_ == registrar))
            if (newEntry._2.registrars.isEmpty) {
              // No more registrars so we can shutdown the leader selector
              entry.leader.close()
              val key = (path, optNamespace)
              leadershipRegistrars -= key
            } else {
              leadershipRegistrars += newEntry
            }
          }
        }
    }
  }

  /**
    * This is the health of the current object, by default will be NORMAL
    * In general this should be overridden to define the health of the current object
    * For objects that simply manage other objects you shouldn't need to do anything
    * else, as the health of the children components would be handled by their own
    * CheckHealth function
    *
    * @return
    */
  override protected def getHealth: Future[HealthComponent] = {
    val healthy = currentState match {
      case ConnectionState.LOST | ConnectionState.READ_ONLY | ConnectionState.SUSPENDED =>
        false
      case ConnectionState.RECONNECTED =>
        true
      case ConnectionState.CONNECTED =>
        true
    }

    val zookeeperState = if (healthy) ComponentState.NORMAL else ComponentState.CRITICAL
    val msg = if (zookeeperState == ComponentState.NORMAL) {
      s"We are currently connected to the zookeeper quorum on ${settings.quorum}"
    } else {
      s"We are currently not able to connect to the zookeeper quorum on ${settings.quorum}"
    }

    val components = curator.getServiceProviderDetails() map { k =>
      HealthComponent(k._1.toString, ComponentState.NORMAL, k._2.toString)
    }

    Future {
      HealthComponent("zookeeper", zookeeperState, details = msg, extra = None, components.toList)
    }
  }

  private def startCurator(): Unit = {
    curator.start(Some(this))
  }

  /**
    * This method can be overriden to provide additional functionality when stopping curator
    */
  def stoppingCurator(): Unit = {}

  private def stopCurator(): Unit = {
    // Close all
    childRegistrars.values foreach { entry =>
      entry.cache.listenable().removeListener(this)
      entry.cache.close()
    }
    childRegistrars = Map.empty

    leadershipRegistrars.values foreach { entry =>
      entry.leader.close()
    }
    leadershipRegistrars = Map.empty

    stoppingCurator()
    curator.stop()
  }

  /**
    * Curator handler for when the connected state changes to Zookeeper
    */
  def stateChanged(cur: CuratorFramework, connectedState: ConnectionState): Unit = {
    self ! StateChanged(connectedState)
  }

  /**
    * Curator handler for when a child node changes for a path that we are watching
    */
  def event(`type`: CuratorCacheListener.Type, oldData: ChildData, data: ChildData): Unit = {
    // Ignore any initialization events
    if (data != null || oldData != null) {
      val path = if (data != null) data.getPath else oldData.getPath
      log.debug(s"Zookeeper child event for path $path")

      // Cycle through the paths to determine if any start with this change
      for (entry <- childRegistrars) {
        if (path.startsWith(entry._1._1)) {
          // Notify the registered listeners
          for (act <- entry._2.registrars) act ! ZookeeperChildEvent(`type`, oldData, data)
        }
      }
    }
  }

  private def doRegisterSelf(): Boolean = {
    val sysConfig = context.system.settings.config
    val conf =
      if (sysConfig.hasPath(ZookeeperManager.ComponentName))
        sysConfig.getConfig(ZookeeperManager.ComponentName).withFallback(sysConfig)
      else sysConfig
    Try(conf.getBoolean("register-self")).getOrElse(true)
  }

  protected class DefaultCallback extends BackgroundCallback {

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getResultCode != 0) {
        log.error(s"Setting data on path ${event.getPath} failed")
      }
    }
  }
}

private class LeaderListener(path: String, ref: ActorRef, namespace: Option[String])(implicit ex: ExecutionContext)
    extends LeaderLatchListener {

  def isLeader(): Unit = publishEvent(true)

  def notLeader(): Unit = publishEvent(false)

  def publishEvent(leader: Boolean): Unit = {
    // Notify the registered listeners
    implicit val timeout: Timeout = Timeout(2000, TimeUnit.MILLISECONDS)
    (ref ? GetLeaderRegistrars(path, namespace)).mapTo[Set[ActorRef]].map { set =>
      set foreach {
        _ ! ZookeeperLeadershipEvent(leader)
      }
    }
    ()
  }
}
