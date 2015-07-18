/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.zookeeper

import java.net.InetAddress

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.webtrends.harness.component.zookeeper.ZookeeperActor.GetLeaderRegistrars
import com.webtrends.harness.component.zookeeper.ZookeeperEvent.Internal.{UnregisterZookeeperEvent, RegisterZookeeperEvent}
import com.webtrends.harness.component.zookeeper.ZookeeperEvent._
import com.webtrends.harness.component.zookeeper.ZookeeperService._
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.component.zookeeper.discoverable.DiscoverableService._
import com.webtrends.harness.component.zookeeper.discoverable.DiscoverableService
import com.webtrends.harness.health.{ComponentState, HealthComponent, ActorHealth}
import com.webtrends.harness.logging.ActorLoggingAdapter
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{CuratorEvent, BackgroundCallback}
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.{PathChildrenCacheEvent, PathChildrenCache, PathChildrenCacheListener}
import org.apache.curator.framework.recipes.leader.{LeaderLatchListener, LeaderLatch}
import org.apache.curator.framework.state.{ConnectionState, ConnectionStateListener}
import org.apache.curator.x.discovery.{UriSpec, ServiceInstance}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.{NoNodeException, NodeExistsException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.collection.JavaConversions._

object ZookeeperActor {
  @SerialVersionUID(1L) private[zookeeper] case class GetLeaderRegistrars(path: String, namespace: Option[String])

  def props(settings:ZookeeperSettings, clusterEnabled:Boolean=false)(implicit system: ActorSystem): Props =
    Props(classOf[ZookeeperActor], settings, clusterEnabled)
}

class ZookeeperActor(settings:ZookeeperSettings, clusterEnabled:Boolean=false) extends Actor
    with ActorLoggingAdapter
    with ActorHealth
    with ConnectionStateListener
    with PathChildrenCacheListener
    with Stash
    with NodeRegistration {

  import context.dispatcher

  private case class RegisterNode()

  protected val curator = Curator(settings)
  private var currentState = ConnectionState.LOST
  protected val callback = new DefaultCallback
  private var stateRegistrars: Set[ActorRef] = Set.empty
  private var childRegistrars: Map[(String, Option[String]), CacheEntry] = Map.empty
  private var leadershipRegistrars: Map[(String, Option[String]), LeaderEntry] = Map.empty

  @SerialVersionUID(1L) private case class CacheEntry(cache: PathChildrenCache, registrars: Set[ActorRef])

  @SerialVersionUID(1L) private case class LeaderEntry(leader: LeaderLatch, registrars: Set[ActorRef])

  @SerialVersionUID(1L) protected case class StateChanged(state: ConnectionState)

  override def preStart(): Unit = {
    log.info("Starting Curator client")

    Try({
      // Register as a handler
      ZookeeperService.registerMediator(self)
      DiscoverableService.registerMediator(self)
      startCurator
    }).recover({
      case e: Exception => log.error("An error occurred trying to start curator.", e)
    })
  }

  override def postStop(): Unit = {
    // Un-register ourself
    unregisterNode(curator.client, settings)
    // Un-register as a handler
    ZookeeperService.unregisterMediator(self)
    DiscoverableService.unregisterMediator(self)
    // We are stopped so shutdown curator
    stopCurator
    log.info("Curator client stopped")
  }

  override def receive = initializing

  def initializing: Receive = baseProcessing orElse {
    case msg => stash() // Stash everything else for now
  }

  def baseProcessing: Receive = health orElse {
    case StateChanged(state) =>
      // Handle the connection state
      handleStateChange(state)
  }

  /**
   * This is the main processing handler
   * @return
   */
  def processing: Receive = baseProcessing orElse {
    // Set the data for the given path
    case SetPathData(path, data, create, ephemeral, optNamespace, async) => setData(path, data, create, ephemeral, optNamespace, async)
    // Get data for the given path
    case GetPathData(path, optNamespace) => getData(path, optNamespace)
    // Get data for the given path
    case GetOrSetPathData(path, data, ephemeral, optNamespace) => getOrSetData(path, data, ephemeral, optNamespace)
    // Get children for the given path
    case GetPathChildren(path, includeData, optNamespace) => getChildren(path, includeData, optNamespace)
    // Check if a node exists
    case GetNodeExists(path, optNamespace) => nodeExists(path, optNamespace)
    // Create a node
    case CreateNode(path, ephemeral, data, optNamespace) => createNode(path, ephemeral, data, optNamespace)
    // Delete a node
    case DeleteNode(path, optNamespace) => deleteNode(path, optNamespace)
    // query for service names
    case QueryForNames(basePath) => queryForNames(basePath)
    // query for service instances
    case QueryForInstances(basePath, name, id) => queryForInstances(basePath, name, id)
    // make service discoverable
    case MakeDiscoverable(basePath, id, name, address, port, uriSpec) => makeDiscoverable(basePath, id, name, address, port, uriSpec)
    // get a single instance from the provider
    case GetInstance(basePath, name) => getInstance(basePath, name)
    // get all the instances from the provider
    case GetAllInstances(basePath, name) => getAllInstances(basePath, name)
    // Registration messages
    case r: RegisterZookeeperEvent => registerForEvents(r)
    case ur: UnregisterZookeeperEvent => unregisterForEvents(ur)
    case GetLeaderRegistrars(path, optNamespace) => sender() ! leadershipRegistrars.get((path, optNamespace)).map {
      _.registrars
    }.getOrElse(Nil)
    case RegisterNode =>
      // Re-register our self
      registerNode(curator.client, settings, clusterEnabled)
  }

  private def getClientContext(namespace: Option[String]): CuratorFramework = namespace match {
    case Some(space) => curator.client.usingNamespace(space)
    case None => curator.client
  }

  private def getChildren(path: String, includeData: Boolean, namespace: Option[String]) = {
    try {
      val nodes = for {
        child <- getClientContext(namespace).getChildren.forPath(path)
        data = if (includeData) Some(getClientContext(namespace).getData.forPath(s"${path}/${child}")) else None
      } yield (child, data)
      sender() ! nodes
    }
    catch {
      case e: Exception =>
        log.error(e, "An error occurred trying to fetch children from the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def getData(path: String, namespace: Option[String]) = {
    try {
      sender() ! getClientContext(namespace).getData.forPath(path)
    }
    catch {
      case nn: NoNodeException =>
        log.error("No node found for path {}", path)
        sender() ! Status.Failure(nn)
      case e: Exception =>
        log.error(e, "An error occurred trying to fetch data from the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def setData(path: String, data: Array[Byte], create: Boolean, ephemeral: Boolean, namespace: Option[String], async: Boolean) = {
    def nodeCreate: String = {
      try {
        val mode = if (ephemeral) CreateMode.EPHEMERAL else CreateMode.PERSISTENT
        getClientContext(namespace).create.creatingParentsIfNeeded.withMode(mode).forPath(path)
      } catch {
        case ne: NodeExistsException => path
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

  private def getOrSetData(path: String, data: Array[Byte], ephemeral: Boolean, namespace: Option[String]) = {
    try {
      sender() ! getClientContext(namespace).getData.forPath(path)
    } catch {
      case e: NoNodeException =>
        try {
          val mode = if (ephemeral) CreateMode.EPHEMERAL else CreateMode.PERSISTENT
          getClientContext(namespace).create.creatingParentsIfNeeded.withMode(mode).forPath(path, data)
          sender() ! data
        }
        catch {
          case e: Exception =>
            log.error(e, "An error occurred trying to get or set data for the path {}", path)
            sender() ! Status.Failure(e)
        }

      case e: Exception =>
        log.error(e, "An error occurred trying to get or set data for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def nodeExists(path: String, namespace: Option[String]) = {
    try {
      sender() ! (getClientContext(namespace).checkExists.forPath(path) != null)
    } catch {
      case ne: NoNodeException => sender().tell(false, self)
      case e: Exception =>
        log.error(e, "An error occurred trying to create a node for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def createNode(path: String, ephemeral: Boolean, data: Option[Array[Byte]], namespace: Option[String]) = {
    try {
      val mode = if (ephemeral) CreateMode.EPHEMERAL else CreateMode.PERSISTENT
      sender() ! getClientContext(namespace).create.creatingParentsIfNeeded.withMode(mode).forPath(path, data.getOrElse(Array.empty[Byte]))
    } catch {
      case ne: NodeExistsException => sender() ! path
      case e: Exception =>
        log.error(e, "An error occurred trying to create a node for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def deleteNode(path: String, namespace: Option[String]) = {
    try {
      getClientContext(namespace).delete.forPath(path)
      sender() ! path
    } catch {
      case ne: NoNodeException => sender() ! path // Swallow
      case e: Exception =>
        log.error(e, "An error occurred trying to create a node for the path {}", path)
        sender() ! Status.Failure(e)
    }
  }

  private def queryForNames(basePath:String) = {
    try {
      sender() ! curator.discovery(basePath).queryForNames()
    } catch {
      case e: Exception =>
        log.error(e, "An error occurred trying to query for names")
        sender() ! Status.Failure(e)
    }
  }

  private def queryForInstances(basePath:String, name:String, id:Option[String]=None) = {
    try {
      val query = id match {
        case Some(i) => curator.discovery(basePath).queryForInstance(name, i)
        case None => curator.discovery(basePath).queryForInstances(name)
      }
      sender() ! query
    } catch {
      case e:Exception =>
        log.error(e, "An error occurred trying to query for instances")
        sender() ! Status.Failure(e)
    }
  }

  private def makeDiscoverable(basePath:String, id:String, name:String, address:Option[String], port:Int, uriSpec:UriSpec) = {
    try {
      if (curator.discovery(basePath).queryForInstance(name, id) == null) {
        val builder = ServiceInstance.builder[Void]()
          .id(id)
          .name(name)
          .port(port)
          .uriSpec(uriSpec)
        address match {
          case Some(a) => builder.address(a)
          case None => //ignore
        }
        val instance = builder.build()

        curator.registerService(basePath, instance)
        log.info(s"Service is now discoverable ${instance.toString}")
        sender() ! true
      } else {
        log.info(s"Not making {$id, $name} discoverable as it already is")
        sender() ! false
      }
    } catch {
      case e:Exception =>
        log.error(e, "An error occurred while trying to make discoverable")
        sender() ! Status.Failure(e)
    }
  }

  private def getInstance(basePath:String, name:String) = {
    try {
      sender() ! curator.createServiceProvider(basePath, name).getInstance()
    } catch {
      case e:Exception =>
        log.error(e, "An error occurred while trying to get an instance from the discoverable provider")
        sender() ! Status.Failure(e)
    }
  }

  private def getAllInstances(basePath:String, name:String) = {
    try {
      sender() ! curator.createServiceProvider(basePath, name).getAllInstances
    } catch {
      case e:Exception =>
        log.error(e, "An error occurred while trying to get all instances from the discoverable provider")
        sender() ! Status.Failure(e)
    }
  }

  protected def handleStateChange(state: ConnectionState) = {
    log.info("Zookeeper connection state changed to {}", state.name)
    currentState = state

    if (currentState == ConnectionState.RECONNECTED || currentState == ConnectionState.CONNECTED) {
      unstashAll()
      context.become(processing)
    }
    else {
      context.become(initializing)
    }

    // Now update all of the registered listeners
    stateRegistrars foreach {
      act => act ! ZookeeperStateEvent(currentState)
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
        childRegistrars.get((path, optNamespace)).map {
          entry =>
            if (!entry.registrars.contains(registrar)) {
              childRegistrars.updated((path, optNamespace), entry.copy(registrars = entry.registrars ++ Set(registrar)))
            }
        }.getOrElse {
          Try({
            // Adding a new cache
            val cache = new PathChildrenCache(getClientContext(optNamespace), path, true)
            cache.getListenable.addListener(this)
            cache.start(StartMode.POST_INITIALIZED_EVENT)
            childRegistrars += ((path, optNamespace) -> CacheEntry(cache, Set(registrar)))
          }).recover({
            case e: Exception => log.error(s"An error occurred trying to create a path listener for the path $path", e)
          })
        }

      case ZookeeperLeaderEventRegistration(registrar, path, optNamespace) =>
        // Update the leadership registrations
        leadershipRegistrars.get((path, optNamespace)).map {
          entry =>
            if (!entry.registrars.contains(registrar)) {
              leadershipRegistrars.updated((path, optNamespace), entry.copy(registrars = entry.registrars ++ Set(registrar)))
            }
        }.getOrElse {
          Try({
            // Adding a new leader selector
            val sel = new LeaderLatch(getClientContext(optNamespace), path)
            sel.addListener(new LeaderListener(path, self, optNamespace))
            sel.start
            leadershipRegistrars += ((path, optNamespace) -> LeaderEntry(sel, Set(registrar)))
          }).recover({
            case e: Exception => log.error(s"An error occurred trying to create a leader selector for the path $path", e)
          })
        }
    }

  }

  private def unregisterForEvents(reg: UnregisterZookeeperEvent) = {
    reg.to match {
      case ZookeeperStateEventRegistration(registrar) => stateRegistrars -= registrar
      case ZookeeperChildEventRegistration(registrar, path, optNamespace) =>
        childRegistrars.get((path, optNamespace)).map {
          entry =>
            if (entry.registrars.contains(registrar)) {
              val newEntry = ((path, optNamespace) -> entry.copy(registrars = entry.registrars.filterNot(_ == registrar)))
              if (newEntry._2.registrars.isEmpty) {
                // No more registrars so we can shutdown the cache
                entry.cache.getListenable.removeListener(this)
                entry.cache.close
                val key = (path, optNamespace)
                childRegistrars -= key
              }
              else {
                childRegistrars += newEntry
              }
            }
        }

      case ZookeeperLeaderEventRegistration(registrar, path, optNamespace) =>
        leadershipRegistrars.get((path, optNamespace)).map {
          entry =>
            if (entry.registrars.contains(registrar)) {
              val newEntry = ((path, optNamespace) -> entry.copy(registrars = entry.registrars.filterNot(_ == registrar)))
              if (newEntry._2.registrars.isEmpty) {
                // No more registrars so we can shutdown the leader selector
                entry.leader.close
                val key = (path, optNamespace)
                leadershipRegistrars -= key
              }
              else {
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
    }
    else {
      s"We are currently not able to connect to the zookeeper quorum on ${settings.quorum}"
    }

    val components = curator.getServiceProviderDetails() map {
      k =>
        new HealthComponent(k._1.toString, ComponentState.NORMAL, k._2.toString)
    }

    Future {
      new HealthComponent("zookeeper", zookeeperState, details = msg, extra = None, components.toList)
    }
  }

  private def startCurator = {
    curator.start(Some(this))
  }

  /**
   * This method can be overriden to provide additional functionality when stopping curator
   */
  def stoppingCurator: Unit = {}

  private def stopCurator = {
    // Close all
    childRegistrars.values foreach {
      entry =>
        entry.cache.getListenable.removeListener(this)
        entry.cache.close
    }
    childRegistrars = Map.empty

    leadershipRegistrars.values foreach {
      entry =>
        entry.leader.close
    }
    leadershipRegistrars = Map.empty

    stoppingCurator
    curator.stop
  }

  /**
   * Curator handler for when the connected state changes to Zookeeper
   * @param cur
   * @param connectedState
   */
  def stateChanged(cur: CuratorFramework, connectedState: ConnectionState): Unit = {
    self ! StateChanged(connectedState)
  }

  /**
   * Curator handler for when a child node changes for a path that we are watching
   * @param curator
   * @param event
   */
  def childEvent(curator: CuratorFramework, event: PathChildrenCacheEvent): Unit = {
    // Ignore any initialization events
    if (event != null && (event.getType == PathChildrenCacheEvent.Type.CHILD_ADDED ||
      event.getType == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
      event.getType == PathChildrenCacheEvent.Type.CHILD_UPDATED)) {

      val path = event.getData.getPath
      log.debug(s"Zookeeper child event for path ${path}")

      // Cycle through the paths to determine if any start with this change
      for (entry <- childRegistrars) {
        if (path.startsWith(entry._1._1)) {
          // Notify the registered listeners
          for (act <- entry._2.registrars) act ! ZookeeperChildEvent(event)
        }
      }
    }
  }

  protected class DefaultCallback extends BackgroundCallback {
    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getResultCode != 0) {
        log.error(s"Setting data on path ${event.getPath} failed")
      }
    }
  }
}

private class LeaderListener(path: String, ref: ActorRef, namespace: Option[String])(implicit ex: ExecutionContext) extends LeaderLatchListener {

  def isLeader = publishEvent(true)

  def notLeader = publishEvent(false)

  def publishEvent(leader: Boolean) = {
    // Notify the registered listeners
    implicit val timeout = Timeout(2000)
    (ref ? GetLeaderRegistrars(path, namespace)).mapTo[Set[ActorRef]].onSuccess {
      case set => set foreach {
        _ ! ZookeeperLeadershipEvent(leader)
      }
    }
  }
}