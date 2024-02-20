# Wookiee Platform

[![Latest Release](https://img.shields.io/github/release/oracle/wookiee.svg)](https://github.com/oracle/wookiee/releases) [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

Fastest way to get going with Wookiee check out the [Quickstart Guide](docs/quickstart.md).

Wookiee is Licensed under the Apache 2.0 License, for more information see [LICENSE](LICENSE)

## Usage

Wookiee is meant to save you from the endless tedium of creating yet another micro service. 
It provides a common Main class ([HarnessService](wookiee-core/src/main/scala/com/oracle/infy/wookiee/app/HarnessService.scala)) 
and tacks on a ton of out of the box conveniences.

<b>So think of Wookiee when you...</b>
<i>
* ...are trying to track down what library you put the eleventh health check 
implementation of your career in for you to copy paste
* ...just aren't sure whether you want to use Colossus or Akka Http and you'd like to be
able to swap between the two in a few minutes or just run both at once!
* ...need to get metrics recording in your service and reporting out to Graphite 
and you want to be able to do it with zero lines of setup code
* ...have found that interacting with the old school Scala(Java) main() method reminds
you too much of being in college and you begin to doubt you've improved at all
* ...don't have the patience to throw together an artisanal configuration reader for the
hundredth time because your cycles are more important, dang it!
* ...want the new intern to be able to create their own new Services using a really simple
template with tons of examples since your company runs all their Services on one framework
* ...would rather focus on the functionality of your Actors than worrying about
linking up health checks, starting everything up, and sending out PoisonPills on shutdown
* ...have no appetite for creating a new logger variable for every single class you want to hear from
* ...need to integrate a new technology but find it unsavory to write thirty lines of
"hotNewTech.start(config); ...; hotNewTech.whatever(); ...; hotNewTech.close()" in every codebase
* ...just want to be able to get straight to the fun stuff!

</i>

### Adding to Pom

Add [latest version](https://github.com/oracle/wookiee/releases/latest) of wookiee, either Scala 2.12 or 2.13 varietals:
~~~~
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-core_${scala.artifact.version}</artifactId>
    <version>${wookiee.version}</version>
</dependency>
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-test_${scala.artifact.version}</artifactId>
    <version>${wookiee.version}</version>
</dependency>
~~~~
```sbt
libraryDependencies += "com.oracle.infy" %% "wookiee-core" % "${wookiee.version}"
libraryDependencies += "com.oracle.infy" %% "wookiee-test" % "${wookiee.version}"
```

### What's Included

The Wookiee platform repository contains the core, supporting components and a test library. It is built primarily on Scala and [Akka](http://akka.io). It contains example projects as well as Maven archetypes for creating various service and creating a component. Wookiee is split into 2 primary components, the Wookiee library and the system components. The Wookiee library is comprised of the of the following components:

* [Command Executive](wookiee-libs/src/main/scala/com/oracle/infy/wookiee/command/WookieeCommandExecutive.scala) - Adds and Executes commands. 
* [Component Manager](wookiee-core/src/main/scala/com/oracle/infy/wookiee/component/ComponentManager.scala) - Loads up component Jars and managers
* [Service Manager](wookiee-core/src/main/scala/com/oracle/infy/wookiee/service/ServiceManager.scala) - Loads up user services, this is where the primary business logic for the application would reside
* [Health Provider](wookiee-libs/src/main/scala/com/oracle/infy/wookiee/health/WookieeMonitor.scala) - provides framework for health in components and services
* [Logging](wookiee-libs/src/main/scala/com/oracle/infy/wookiee/logging/LoggingAdapter.scala) - provides basic logging capability for components and services
* [Utilities](wookiee-libs/src/main/scala/com/oracle/infy/wookiee/utils) - Utility libraries for common functions in the code.

### Command Executive
The command manager is the central actor for routing all execution of commands in our system. A command is simply the primary execution point for specific set of work. 
For more information see the [Commands](docs/Commands.md) documentation.

### Loading System Components
The Wookiee library loads various components when it starts up. Each component is derived through 1 of 4 methods:

1. It checks the sub folders found in the root folder defined in the application config under the key "components.component-path". The name of the folder will be the name of the actor that is initialized by the component manager. Each component folder will contain a lib folder with all the jars that the component uses as well as the component jar. A conf file must be located in the component folder for configuration of the component. For specifics around individual components, the configuration file and the expected pattern for the component see Components section in this doc.

2. It checks for jars in the aforementioned folder under the key "components.component-path". The jars there are expected to be shaded jars that contain all the needed libraries and config for the component in the jar. Any configuration can be overridden in your primary conf file using the config from the reference conf in the jar.

3. It loads a component from a class based on the configuration that is loaded into the system. This list of components are found in the main configuration under the key "components.lib-components". The value of the key is a list of strings that simply point to the config for the component.

4. It loads a component automatically based on a key in the config file for that component "dynamic-component". If the key is set to true in the config it will load up the component.*

* Note: If a list of components are set under the key "component.lib-components" there would be no components loaded automatically, essentially the last type would be switched off.

### Loading Services
Services that are built for Wookiee are also loaded into memory by the core. The exception to this would be if Wookiee is used just as a library or embedded in an application. In this case the service in this context is not really a service but rather an application. In general however, if Wookiee is to be used as a library it would most likely be more beneficial to use the individual system components in search for the specific functionality that your App requires. Services are loaded in a similar fashion to the components, where the services are located in sub folders found in the root folder defined in the application config under the key "services.service-path". The primary difference being that classes are loaded into a separate class loader instead of the root Wookiee class loader. (** note ** not the system class loader)

In most cases services will be loaded as mentioned above, however one can also load the service dynamically which will be described below:

### Local Messaging
There is a cluster component which allows for messaging across a cluster. However, by default Wookiee will include local messaging. The messaging works identically to how clustered messaging works, and is based on a simple PubSub methodology.

### Health
Standardized health checks is provided by the library. The WookieeMonitor trait will apply default health functionality to any class that leverages the trait. By default, a developer would only have to insert the following code into their class
```scala
import com.typesafe.config.Config
import com.oracle.infy.wookiee.service.ServiceV2
import scala.concurrent.Future
import com.oracle.infy.wookiee.health.{WookieeMonitor, HealthComponent, ComponentState}

// ServiceV2 is a trait that extends WookieeMonitor
class MyService(config: Config) extends ServiceV2(config) {
  val underClass = new MyUnderClass()
	
  override def getHealth: Future[HealthComponent] = {
    Future.successful(HealthComponent(name, ComponentState.NORMAL, "MyService is healthy"))
  }
  
  override def getDependents: Iterable[WookieeMonitor] = List(underClass)
}

// WookieeMonitor is a trait that provides health functionality
// If the parent that creates this class extends WookieeMonitor and implements the getDependents function
// then the getHealth function will be called on the dependents
class MyUnderClass() extends WookieeMonitor {
  override def getHealth: Future[HealthComponent] = {
    Future.successful(HealthComponent(name, ComponentState.NORMAL, "MyUnderClass is healthy"))
  }
}
```
The above code will give the actor basic health functionality, this will do two things:

* receive health check requests in MyService and return a healthcheck status
* iterate through the WookieeMonitor dependents and call the getHealth function on them

Generally a developer would want to override the getHealth function to give customized health check status for the class. Example:
```scala
	override def getHealth : Future[HealthComponent] = {
		Future {
			Math.random.toInt match {
				case s if s > 50 =>
					HealthComponent(self.path.name, ComponentState.NORMAL, "Random health check is NORMAL")
				case s if s > 10 && s <= 50 =>
					HealthComponent(self.path.name, ComponentState.DEGRADED, "Random health check is DEGRADED")
				case s if s <= 10 =>
					HealthComponent(self.path.name, ComponentState.CRITICAL, "Random health check is CRITICAL")
			}
		}
	}
```
This code will create a random health check result based on the value of the random int. As shown the ComponentState can be either NORMAL, DEGRADED or CRITICAL. 
Lastly if needed a developer can override the checkHealth function that will handle the message, which will by default use getHealth to get the health of the current actor and then traverse the children to get their health, however if there is a requirement to modify this behavior you can simply override it.

### Configuration Watching
The file specified in config.file will actually be watched for any changes to it and a message
will be sent to all components and services (which will then forward it on to their getDependents() lists). 
To hook into this most easily, extend the ConfigHelperV2 class like so:

```scala
import com.oracle.infy.wookiee.config.ConfigHelperV2

class ConfigWatchingClass extends ConfigHelperV2 {
  override def renewConfiguration(): Unit = {
      super.renewConfiguration()
      renewableConfig // Do something with your updated config object
  }
}

class ConfigWatchingService extends ServiceV2 {
  val configWatchingClass = new ConfigWatchingClass()
    
  override def getDependents: Iterable[WookieeMonitor] = List(configWatchingClass)
}
```

### Logging
Standardized logging is provided by the library. This can be applied to any class using the trait LoggingAdapter. This will give you the "log" variable which will allow you to write info, debug, warn, error and trace messages to the log.
```scala
class AnyClass extends LoggingAdapter {
  log.info("This is a log message")
}
```

Wookiee can be used as both a library and a service. To use it as a service a developer would be required to simply execute the HarnessService app, for use as a library the developer would be required to add a dependency in the project's pom and then initialize Wookiee manually. Alternatively the developer could add a dependency for a single component to the POM and use it separately. For more information on leveraging a single component see the doc specific to that component.

[**Releases**](https://github.com/oracle/wookiee/releases)

# How To
There are several aspects to utilizing the functionality contained with Wookiee and its supporting libraries. This
section outlines the available functionality and how to best utilize the Wookiee Platform.

### Increasing Artifact Version
To bump the version of Wookiee Core/Test simply increase it in the VERSION file.
The version that is published will be the literal contents of that file.

### Creating a service
As services are what provide functionality to the Wookiee container, this section provides information on how to
create a basic service.

[Example](examples/basic-service/src/main/scala/com/oracle/infy/qa/BasicService.scala)

### Creating a component
Components provide pluggable core functionality in to Wookiee. This allows developers to pick and choose the kind of functionality that they want.

[Example](examples/basic-extension/src/main/scala/com/oracle/infy/wookiee/qa/BasicExtension.scala)

### Components
A component is dynamically loaded in Wookiee. This allows for a developer to then only load the components that they wish to use as part of the Wookiee Platform. A component is defined by a class object with the Component trait found in the wookiee-core project. Wookiee will start up any component that is found in location that is defined by the component-path key in the harness configuration file.
```
wookiee-system {
  # This is the path to the location of the components (defaults to "components")
  # Should just contain the jar for the component
  component-path = "components"
  ...
}
```


* [Webservice Component](wookiee-web)
  * Most modern Component for HTTP/WS services
* [Cache Component](wookiee-cache)
  * Provides a Cache implementation for Wookiee
* [Memcache Component](wookiee-cache-memcache)
  * Extends the Cache Component to provide a Memcache implementation
* [gRPC Component](wookiee-grpc-component)
  * Allows for creation and querying of gRPC services
* [Metrics Component](wookiee-metrics)
  * Metrics that can be attached anywhere and sent to a metrics service
* [Zookeeper Component](wookiee-zookeeper)
  * Provides Zookeeper connection management and service discovery

### Configuring a component
Each component loaded in Wookiee should provide a default configuration that will fit most situations.  The Wookiee Platform
uses Typesafe Config to load configurations at runtime in layers.  A component's default configuration should be given the lowest
priority, the reference conf, following the layered priority schema set by Typesafe Config.  This can be problematic at times, as third party libs
and components with equally prioritized, overlapping configurations are combined in the application.  To ensure component 
configurations take precedence, place components jars at the beginning of the classpath.  One approach is to separate components
from third party libs in the distribution.

Maven dist.xml
```xml
<assembly>
    <id>bin</id>
    <includeBaseDirectory>false</includeBaseDirectory>
    <formats>
        <format>tar.gz</format>
    </formats>
    <files>
        <file>
            <source>${project.build.directory}/${project.build.finalName}.jar</source>
        </file>
    </files>
    <dependencySets>
        <dependencySet>
            <useProjectArtifact>false</useProjectArtifact>
            <outputDirectory>/lib/thirdparty</outputDirectory>
            <scope>runtime</scope>
            <excludes>
                <exclude>*:wookiee*:jar</exclude>
            </excludes>
        </dependencySet>
        <dependencySet>
            <useProjectArtifact>false</useProjectArtifact>
            <outputDirectory>/lib/components</outputDirectory>
            <scope>runtime</scope>
            <includes>
                <include>*:wookiee*:jar</include>
            </includes>
        </dependencySet>
    </dependencySets>
</assembly>
```

Run command:
```
java -cp *:lib/components/*:lib/thirdparty/* com.oracle.infy.wookiee.app.HarnessService
```

## Discoverability
Wookiee provides a service discovery mechanism that allows services to register themselves with the Wookiee Platform.
This gives us the ability to message services that are running in the Wookiee Platform without having to know the
exact host that those services are on. 
* [Wookiee Discovery](wookiee-discovery)
* [Example of Usage](examples/advanced-communication)

## Wookiee Actors
Wookiee formerly ran on akka Actors (and still will until version 2.5). There is an interface called the WookieeActor
that emulates the functionality of an akka Actor. This can be used from anywhere in the Wookiee Platform and has all
the same methods and behavior as an akka Actor. This is useful for when you want to use the Wookiee Platform but don't
want to use akka Actors.

```scala
import com.oracle.infy.wookiee.actors.WookieeActor

class MyActor extends WookieeActor {
  override def receive: Receive = {
    case "hello" => sender() ! "world"
  }
}
```
These WookieeActors have all the akka functionality including the ability to `become(receiver)` and use schedulers.
To spin up an actor is easy and doesn't require an actor system or actor context.

```scala
import com.oracle.infy.wookiee.actors.WookieeActor

val myActor = WookieeActor.actorOf(new MyActor)
```

One can also create a router of actors using the `WookieeActor.withRouter` method.

```scala
import com.oracle.infy.wookiee.actors.WookieeActor
import com.oracle.infy.wookiee.actors.router.RoundRobinRouter

// Will create 10 instances of MyActor and round robin between them
WookieeActor.withRouter(new MyActor, new RoundRobinRouter(10))
```


# wookiee-grpc
* [wookiee-grpc](#wookiee-grpc)
## Installation
wookiee-grpc is available for Scala 2.12 and 2.13. There are no plans to support scala 2.11 or lower.
```scala
libraryDependencies += "com.oracle.infy" %% "wookiee-grpc" % "2.2.8"
```

## Setup ScalaPB
We use [ScalaPB](https://github.com/scalapb/ScalaPB) to generate source code from a `.proto` file. You can use
other plugins/code generators if you wish. wookiee-grpc will work as long as you have `io.grpc.ServerServiceDefinition`
for the server and something that accept `io.grpc.ManagedChannel` for the client.

Declare your gRPC service using proto3 syntax and save it in `src/main/protobuf/myService.proto`
```proto
syntax = "proto3";

package com.oracle.infy.wookiee;

message HelloRequest {
  string name = 1;
}

message HelloResponse {
  string resp = 1;
}

service MyService {
  rpc greet(HelloRequest) returns (HelloResponse) {}
}

```

Add ScalaPB plugin to `plugin.sbt` file
```sbt
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.8"

```

Configure the project in `build.sbt` so that ScalaPB can generate code
```sbt
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    )
  )

```

In the sbt shell, type `protocGenerate` to generate scala code based on the `.proto` file. ScalaPB will generate
code and put it under `target/scala-2.13/src_managed/main`.

## Using wookiee-grpc
After the code has been generated by ScalaPB, you can use wookiee-grpc for service discoverability and load balancing.

wookiee-grpc is written using functional concepts. One key concept is side-effect management/referential transparency.
We use cats-effect (https://typelevel.org/cats-effect/) internally.
If you want to use cats-effect, you can use the methods that return `IO[_]`. Otherwise, use the methods prefixed with `unsafe`.
When using `unsafe` methods, you are expected to handle any exceptions

### Imports
Add the following imports:
```sbt
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings._
import com.oracle.infy.wookiee.grpc._
import com.oracle.infy.wookiee.grpc.model.LoadBalancers._
import io.grpc._

```

### Creating a Server
```sbt
    val serverSettingsF: ServerSettings = ServerSettings(
      discoveryPath = zookeeperDiscoveryPath,
      serverServiceDefinition = ssd,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = Host(0, "localhost", 9091, HostMetadata(0, quarantined = false)),
      authSettings = None,
      sslServerSettings = None,
      bossExecutionContext = mainEC,
      workerExecutionContext = mainEC,
      applicationExecutionContext = mainEC,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism,
      curatorFramework = curator
    )

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.start(serverSettingsF).unsafeToFuture()

```


### Creating a Client Channel
```sbt
    val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel
      .of(
        ChannelSettings(
          serviceDiscoveryPath = zookeeperDiscoveryPath,
          eventLoopGroupExecutionContext = blockingEC,
          channelExecutionContext = mainEC,
          offloadExecutionContext = blockingEC,
          eventLoopGroupExecutionContextThreads = bossThreads,
//           Load Balancing Policy
//             One of:
//               RoundRobinPolicy
//               RoundRobinWeightedPolicy
//               RoundRobinHashedPolicy
          lbPolicy = RoundRobinPolicy,
          curatorFramework = curator,
          sslClientSettings = None,
          clientAuthSettings = None
        )
      )
      .unsafeRunSync()

    val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

```

### Executing a gRPC Call
```sbt
    val gRPCResponseF: Future[HelloResponse] = for {
      server <- serverF
      resp <- stub
        .withInterceptors(new ClientInterceptor {
          override def interceptCall[ReqT, RespT](
              method: MethodDescriptor[ReqT, RespT],
              callOptions: CallOptions,
              next: Channel
          ): ClientCall[ReqT, RespT] = {
            next.newCall(
              method,
              // Set the WookieeGrpcChannel.hashKeyCallOption when using RoundRobinHashedPolicy
              callOptions.withOption(WookieeGrpcChannel.hashKeyCallOption, "Some hash")
            )
          }
        })
        .greet(HelloRequest("world!"))
      _ <- wookieeGrpcChannel.shutdown().unsafeToFuture()
      _ <- server.shutdown().unsafeToFuture()
    } yield resp

    println(Await.result(gRPCResponseF, Duration.Inf))
    curator.close()
    zkFake.close()
    ()

```


### Setting up load balancing methods in channel settings

There are three load balancing policies that ship with wookiee-grpc.
The load balancing policies are set up within the gRPC Channel Settings.

* **Round Robin**

  A simple round robin policy that alternates between hosts as calls are executed. It's fairly simplistic.


* **Round Robin Weighted**

  This load balancer takes server load into consideration and distributes calls to the server with the lowest
  current usage. If all loads are equivalent, it defaults to simple Round Robin behavior.


* **Round Robin Hashed**

  Provides "stickiness" for the gRPC host. If you want a particular host to serve the request for all the calls with a
  particular key, you can use this policy. For example, if you want a single server to service all requests that use
  the key "foo", you can set the `WookieeGrpcChannel.hashKeyCallOption` on every call. This will ensure that all gRPC calls using the same
  hash will be executed on the same server.

```sbt
    val gRPCResponseF: Future[HelloResponse] = for {
      server <- serverF
      resp <- stub
        .withInterceptors(new ClientInterceptor {
          override def interceptCall[ReqT, RespT](
              method: MethodDescriptor[ReqT, RespT],
              callOptions: CallOptions,
              next: Channel
          ): ClientCall[ReqT, RespT] = {
            next.newCall(
              method,
              // Set the WookieeGrpcChannel.hashKeyCallOption when using RoundRobinHashedPolicy
              callOptions.withOption(WookieeGrpcChannel.hashKeyCallOption, "Some hash")
            )
          }
        })
        .greet(HelloRequest("world!"))
      _ <- wookieeGrpcChannel.shutdown().unsafeToFuture()
      _ <- server.shutdown().unsafeToFuture()
    } yield resp

    println(Await.result(gRPCResponseF, Duration.Inf))
    curator.close()
    zkFake.close()
    ()

```

## Putting it all together

Here is an example of a complete gRPC solution

```scala
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}
import cats.effect.{Blocker, ContextShift, IO, Timer}
//wookiee-grpc imports
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings._
import com.oracle.infy.wookiee.grpc._
import com.oracle.infy.wookiee.grpc.model.LoadBalancers._
import io.grpc._
//wookiee-grpc imports
import org.typelevel.log4cats.Logger
// This is from ScalaPB generated code
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.grpc.ServerServiceDefinition
import org.apache.curator.test.TestingServer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Example {

  def main(args: Array[String]): Unit = {
    val bossThreads = 10
    val mainECParallelism = 10

    // wookiee-grpc is written using functional concepts. One key concept is side-effect management/referential transparency
    // We use cats-effect (https://typelevel.org/cats-effect/) internally.
    // If you want to use cats-effect, you can use the methods that return IO[_]. Otherwise, use the methods prefixed with `unsafe`.
    // When using `unsafe` methods, you are expected to handle any exceptions

    val uncaughtExceptionHandler = new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        System.err.println("Got an uncaught exception on thread " ++ t.getName ++ " " ++ e.toString)
      }
    }

    val tf = new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        t.setName("blocking-" ++ t.getId.toString)
        t.setUncaughtExceptionHandler(uncaughtExceptionHandler)
        t.setDaemon(true)
        t
      }
    }

    // The blocking execution context must create daemon threads if you want your app to shutdown
    val blockingEC = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(tf))
    // This is the execution context used to execute your application specific code
    implicit val mainEC: ExecutionContext = ExecutionContext.fromExecutor(
      new ForkJoinPool(
        mainECParallelism,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        uncaughtExceptionHandler,
        true
      )
    )

    // Use a separate execution context for the timer
    val timerEC = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

    implicit val cs: ContextShift[IO] = IO.contextShift(mainEC)
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingEC)
    implicit val timer: Timer[IO] = IO.timer(timerEC)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()

    val zookeeperDiscoveryPath = "/discovery"

    // This is just to demo, use an actual Zookeeper quorum.
    val zkFake = new TestingServer()
    val connStr = zkFake.getConnectString

    val curator = WookieeGrpcUtils.createCurator(connStr, 5.seconds, blockingEC).unsafeRunSync()
    curator.start()

    val ssd: ServerServiceDefinition = MyService.bindService(
      (request: HelloRequest) => {
        println("received request")
        Future.successful(HelloResponse("Hello " ++ request.name))
      },
      mainEC
    )

    //Creating a Server
    val serverSettingsF: ServerSettings = ServerSettings(
      discoveryPath = zookeeperDiscoveryPath,
      serverServiceDefinition = ssd,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = Host(0, "localhost", 9091, HostMetadata(0, quarantined = false)),
      authSettings = None,
      sslServerSettings = None,
      bossExecutionContext = mainEC,
      workerExecutionContext = mainEC,
      applicationExecutionContext = mainEC,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism,
      curatorFramework = curator
    )

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.start(serverSettingsF).unsafeToFuture()
    //Creating a Server

    //channelSettings
    val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel
      .of(
        ChannelSettings(
          serviceDiscoveryPath = zookeeperDiscoveryPath,
          eventLoopGroupExecutionContext = blockingEC,
          channelExecutionContext = mainEC,
          offloadExecutionContext = blockingEC,
          eventLoopGroupExecutionContextThreads = bossThreads,
//           Load Balancing Policy
//             One of:
//               RoundRobinPolicy
//               RoundRobinWeightedPolicy
//               RoundRobinHashedPolicy
          lbPolicy = RoundRobinPolicy,
          curatorFramework = curator,
          sslClientSettings = None,
          clientAuthSettings = None
        )
      )
      .unsafeRunSync()

    val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)
    //channelSettings

    //grpcCall
    val gRPCResponseF: Future[HelloResponse] = for {
      server <- serverF
      resp <- stub
        .withInterceptors(new ClientInterceptor {
          override def interceptCall[ReqT, RespT](
              method: MethodDescriptor[ReqT, RespT],
              callOptions: CallOptions,
              next: Channel
          ): ClientCall[ReqT, RespT] = {
            next.newCall(
              method,
              // Set the WookieeGrpcChannel.hashKeyCallOption when using RoundRobinHashedPolicy
              callOptions.withOption(WookieeGrpcChannel.hashKeyCallOption, "Some hash")
            )
          }
        })
        .greet(HelloRequest("world!"))
      _ <- wookieeGrpcChannel.shutdown().unsafeToFuture()
      _ <- server.shutdown().unsafeToFuture()
    } yield resp

    println(Await.result(gRPCResponseF, Duration.Inf))
    curator.close()
    zkFake.close()
    ()
    //grpcCall
  }
}

Example.main(Array.empty[String])
// received request
// HelloResponse(Hello world!,UnknownFieldSet(Map()))
```
## Contributing
This project is not accepting external contributions at this time. For bugs or enhancement requests, please file a GitHub issue unless it’s security related. When filing a bug remember that the better written the bug is, the more likely it is to be fixed. If you think you’ve found a security vulnerability, do not raise a GitHub issue and follow the instructions in our [security policy](./SECURITY.md).

## Security
Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process

## License
Copyright (c) 2004, 2023 Oracle and/or its affiliates.
Released under the Apache License Version 2.0
