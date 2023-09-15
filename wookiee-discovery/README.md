wookiee-discovery
=================

Wookiee Discovery is a library that enables registration and execution of Discoverable Commands.
The purpose of these commands is to allow communication between Wookiee Services running in a cluster.
The communication layer itself is abstracted away from the user and powered under the hood by gRPC.

## Usage
### Configuration
To use Wookiee Discovery, you must first add it as a dependency to your project.
```xml
<dependency>
    <groupId>com.oracle.infy</groupId>
    <artifactId>wookiee-discovery_2.13</artifactId>
    <version>${wookiee.version}</version>
</dependency>
```

Then you must add the following configuration to your `application.conf` file on the hosting server.
```hocon
wookiee-zookeeper {
  quorum = "zoo01.server.address:2181,zoo02.server.address:2181" # comma separated list of zookeeper hosts
}

wookiee-grpc-component {
  grpc {
    port = 3182 # port on which we host gRPC server
    zk-discovery-path = "/wookiee/internal" # the client will use this path to discover this gRPC service
    server-host-name = "host01.server.address" # fqdn of this server
  }
}
```

### Registration
To register a command, you must first create a class that implements the [DiscoverableCommand](src/main/scala/com/oracle/infy/wookiee/discovery/command/DiscoverableCommand.scala) interface.
This interface requires you to implement the `name` and `execute` methods to set a name we can use to call this command from elsewhere, and to define the behavior of the command, respectively.
Once you have your command, you can register it with the [DiscoverableCommandHelper](src/main/scala/com/oracle/infy/wookiee/discovery/command/DiscoverableCommandHelper.scala) object by calling `registerDiscoverableCommand` using the newly implemented `DiscoverableCommand`.
See an example at [InternalDiscoverableCommand](../examples/advanced-communication/src/main/scala/com/oracle/infy/wookiee/communication/command/InternalDiscoverableCommand.scala).
```scala
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommand

// These types can be anything you want
case class InputHolder(input: String)
case class OutputHolder(output: String)

class MyCommand extends DiscoverableCommand[InputHolder, OutputHolder] {
  // Must be unique for each new implementation of DiscoverableCommand
  override def name: String = "my-command"

  // This is where you define the behavior of your command
  override def execute(args: InputHolder): Future[OutputHolder] = Future {
    log.info(s"Executing command: $commandName, [${args.input}]")
    OutputHolder(args.input + ", Output: Hello World!")
  }
}
```

Then you can register your command with the `DiscoverableCommandHelper` object.
```scala
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandHelper

DiscoverableCommandHelper.registerDiscoverableCommand(new MyCommand)
```

This can be done very easily during startup of your Service if you have your main Wookiee Service class extend the [WookieeDiscoverableService](src/main/scala/com/oracle/infy/wookiee/discovery/command/WookieeDiscoverableService.scala) trait.
```scala
import com.oracle.infy.wookiee.discovery.command.WookieeDiscoverableService

class MyService(config: Config) extends WookieeDiscoverableService(config) {
  override val name: String = "My Wookiee Service"
  
  override def addDiscoverableCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    DiscoverableCommandHelper.registerDiscoverableCommand(new MyCommand)
  }
}
```

#### Authentication
If you want to add authentication to your command, you can do so during registration by specifying the `authToken` parameter while calling `registerDiscoverableCommand`.
```scala
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandHelper

DiscoverableCommandHelper.registerDiscoverableCommand(new MyCommand, "my-auth-token")
```

### Execution
Once you have registered your command, you can execute it from anywhere in the cluster (including from the hosting Service) by calling `executeDiscoverableCommand` on the [DiscoverableCommandExecution](src/main/scala/com/oracle/infy/wookiee/discovery/command/DiscoverableCommandExecution.scala) object.
```scala
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandExecution

DiscoverableCommandHelper.executeDiscoverableCommand(
  "/wookiee/internal", // the path the host specified at wookiee-grpc-component.grpc.zk-discovery-path
  "zoo01.server.address:2181,zoo02.server.address:2181", // the zookeeper host to use for discovery 
  "my-auth-token", // auth token if needed, can be anything or empty if not needed
  None, // if using SSL, see below section 'SSL' for more details
  "my-command", // corresponds to the name of the command you registered
  InputHolder("Hello") // the actual input to the command, will need to be the same class type it expects
)
```

#### SSL
If you want to use SSL, you must first create a [SSLClientSettings](../wookiee-grpc/src/main/scala/com/oracle/infy/wookiee/grpc/settings/SSLClientSettings.scala) object.
Pass this along wrapped in a Some to the `executeDiscoverableCommand` method.