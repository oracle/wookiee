# Wookiee - Component: ZooKeeper

For Configuration information see [ZooKeeper Config](docs/config.md)

## Overview

Each instance of Wookiee can automatically manages itself with ZooKeeper so that others nodes can make use
of clustering. Wookiee does this through the use of Curator which then allows the platform to provide interaction
with ZooKeeper through the instance of Curator that is managed.

## Basic Usage

The system is designed so that a developer simply needs to apply a trait (interface) on an actor in order to have
full access to this functionality. Wookiee is responsible for managing the communication with ZooKeeper
and thus a service does not need to manage its own communication. Currently, not all functionality is supported, but
it does include the ability to get node data, set node data, create nodes, getting node children, receiving ZooKeeper
state events, receiving node child events, and managing node leadership.

## Interacting with ZooKeeper Nodes

In order to take full benefit of the functionality one simply needs to apply the trait `ZookeeperAdapter` and then start
interacting with the system.

```scala

import com.webtrends.harness.component.zookeeper.ZookeeperAdapter

// Define the actor and have it apply the ZookeeperAdapter trait
class MyZookeeperActor extends Actor with ActorLoggingAdapter with ZookeeperAdapter {
     import context.dispatcher
     implicit val timeout = Timeout(2000)
     val host = InetAddress.getLocalHost.getCanonicalHostName

     def preStart: Unit = {
          // Lets create a node and give it some data
          createNode(s"/plugin-example/ephnodes/$host", true /* Is this node ephemeral? */, None /* I could give it the data here instead of making the next call */).onComplete {
               case Success(path) =>
                   // The node was created so lets set some data
                    setData(path, "mydata".getBytes)
               case Failure(fail) => // Handle Error
          }

          // We could have also combined the previous example into this call
          // setData(s"/plugin-example/ephnodes/$host",  "mydata".getBytes, true /* Create the node if it does not exist */, true /* Is the node ephemeral ?* /)
          // Lets get some data
          getData(s"/plugin-example/ephnodes/$host").onComplete {
               case Success(data /* Array[Bytes] */) => // Do something with the data
               case Failure(fail) => // Handle the error
          }

          // Lets get some information about child nodes
          getChildren("s"/plugin-example/ephnodes", true /* Do we want the data as well? */).onComplete {
               case Success(childrenWithData /* Seq[(String, Option[Array[Byte]])] */) => // Do something with the children and data
               case Failure(fail) => Handle the error
          }
     }
}
```

## ZooKeeper Events

The ability to receive events for ZooKeeper changes are also available through the trait `ZookeeperEventAdapter`.
If this trait is applied to an actor then the ability to register/unregister for events becomes available.

```scala

import com.webtrends.harness.component.zookeeper.ZookeeperEventAdapter

// Define the actor and have it apply the ZookeeperEventAdapter
class MyZookeeperEventActor with Actor with ActorLoggingAdapter with ZookeeperEventAdapter {
     def preStart: Unit = {
         // Register for ZooKeeper State events
          register(self, ZookeeperStateEventRegistration(self))
          // Register for ZooKeeper Child events on the given path
          register(self, ZookeeperChildEventRegistration(self, "/someparentpath"))
          // Register for ZooKeeper leadership events
          register(self, ZookeeperLeaderEventRegistration(self, "/someleadershippath")
     }
     def postStop: Unit = {
          // Unregister for state events
          unregister(self, ZookeeperStateEventRegistration(self))
          // Unregister for child events on the given path
          unregister(self, ZookeeperChildEventRegistration(self, "/someparentpath"))
          // Unregister for leadership events
          unregister(self, ZookeeperLeaderEventRegistration(self, "someleadershippath"))
     }
     def receive = {
          case ZookeeperStateEvent(state) =>
               // A connection state event occured (see com.netflix.curator.framework.state.ConnectionState)
          case ZookeeperChildEvent(event) =>
               // A child event occurred on the registered path (see com.netflix.curator.framework.recipes.cache.PathChildrenCacheEvent)
          case ZookeeperLeadershipEvent(leader /* Am I the leader */) =>
               // A leadership event occured on the registered path
     }
}
```

