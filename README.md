# Wookie Platform

[![Build Status](https://travis-ci.org/Webtrends/wookie.svg?branch=master)](https://travis-ci.org/Webtrends/wookie?branch=master)

Fastest way to get going with Wookie check out the [Quickstart Guide](docs/quickstart.md).

Wookie is Licensed under the Apache 2.0 License, for more information see [LICENSE](LICENSE)

The Wookie platform repository contains the core, supporting components and a test library. It is built primarily on Scala and [Akka](http://akka.io). It contains example projects as well as Maven archetypes for creating various service and creating a component. Wookie is split into 2 primary components, the Wookie library and the system components. The Wookie library is comprised of the of the following components:

* Command Manager - Adds and Executes commands. 
* Component Manager - Loads up component Jars and managers
* Service Manager - Loads up user services, this is where the primary business logic for the application would reside
* Health Provider - provides framework for health in components and services
* Logging - provides basic logging capability for components and services
* Util - Utility libraries for common functions in the code.

### Command Manager
The command manager is the central actor for routing all execution of commands in our system. A command is simply the primary execution point for specific set of work. 
For more information see the [Commands](docs/Commands.md) documentation.

### Loading System Components
The Wookie library loads various components when it starts up. Each component is derived through 1 of 3 methods:

1. It checks the sub folders found in the root folder defined in the application config under the key "components.component-path". The name of the folder will be the name of the actor that is initialized by the component manager. Each component folder will contain a lib folder with all the jars that the component uses as well as the component jar. A conf file must be located in the component folder for configuration of the component. For specifics around individual components, the configuration file and the expected pattern for the component see Components section in this doc.

2. It checks for jars in the aforementioned folder under the key "components.component-path". The jars there are expected to be shaded jars that contain all the needed libraries and config for the component in the jar. Any configuration can be overridden in your primary conf file using the config from the reference conf in the jar.

3. It loads a component from a class based on the configuration that is loaded into the system. This list of components are found in the main configuration under the key "components.lib-components". The value of the key is a list of strings that simply point to the config for the component.

4. It loads a component automatically based on a key in the config file for that component "dynamic-component". If the key is set to true in the config it will load up the component.*

*Note If a list of components are set under the key "component.lib-components" there would be no components loaded automatically, essentially the last type would be switched off.

### Loading Services
Services that are built for Wookie are also loaded into memory by the core. The exception to this would be if Wookie is used just as a library or embedded in an application. In this case the service in this context is not really a service but rather an application. In general however, if Wookie is to be used as a library it would most likely be more beneficial to use the individual system components in search for the specific functionality that your App requires. Services are loaded in a similar fashion to the components, where the services are located in sub folders found in the root folder defined in the application config under the key "services.service-path". The primary difference being that classes are loaded into a separate class loader instead of the root Wookie class loader. (** note ** not the system class loader)

In most cases services will be loaded as mentioned above, however one can also load the service dynamically which will be described below:

### Local Messaging
There is a cluster component which allows for messaging across a cluster. However by default Wookie will include local messaging. The messaging works identically to how clustered messaging works, and is based on a simple PubSub methodology.

### Health
Standardized health checks is provided by the library. The ActorHealth trait will apply default health functionality to any actor that leverages the trait. By default, a developer would only have to insert the following code into their actor class
```java
class ActorClass extends ActorHealth {
	def receive = health orElse {
		case Message => "Do something here"
	}
}
```
The above code will give the actor basic health functionality, this will do three things:

* receive messages using the HealthCheck message and return a healthy healthcheck status
* implement the default getHealth function which defines the health for the actor
* implement the checkHealth function we will get the health of the current actor using the getHealth function above and then execute the request on the child actors and get the health status for them.

Generally a developer would want to override the getHealth function to give customized health check status for the actor. Example:
```java
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

### Logging
Standardized logging is provided by the library. This can be applied to any actor using the trait ActorLoggingAdapter. This will give you the "log" variable which will allow you to write info, debug, warn, error and trace messages to the log. If you need to add logging to a non-actor based class, possibly like an object you can use the following code.
```java
	val externalLogger = LoggerFactory.getLogger(this.getClass)
```

Wookie can be used as both a library and a service. To use it as a service a developer would be required to simply execute the HarnessService app, for use as a library the developer would be required to add a dependency in the project's pom and then initialize Wookie manually. Alternatively the developer could add a dependency for a single component to the POM and use it separetely. For more information on leveraging a single component see the doc specific to that component.

[**Release Notes**](docs/ReleaseNotes.md)

# How To
There are several aspects to utilizing the functionality contained with Wookie and it's supporting libraries. This
section outlines the available functionality and how to best utilize the Wookie Platform.

### Development Environment
The purpose of this section is to aggregate notes and processes in setting up development environment for creating services for Wookie.

[Instructions](docs/DevSetup.md)

### Creating a service
As services are what provides functionality to the Wookie container, this section provides information on how to
create a basic service.

[Instructions](archetypes/wookie-service-archetypes/README.md)

### Creating a component
Components provide pluggable core functionality in to Wookie. This allows developers to pick and choose the kind of functionality that they want.

[Instructions](archetypes/wookie-component-archetypes/README.md)

### Components
A component is dynamically loaded in Wookie. This allows for a developer to then only load the components that they wish to use as part of the Wookie Platform. A component is defined by a class object with the Component trait found in the wookie-core project. Wookie will start up any component that is found in location that is defined by the component-path key in the harness configuration file.
```
wookie-system {
  # This is the path to the location of the components (defaults to "components")
  # Should just contain the jar for the component
  component-path = "components"
  ...
}
```

1. [Http Component](components/wookie-spray/README.md)
2. [Metrics Component](components/wookie-metrics/README.md)
3. [Zookeeper Component](components/wookie-zookeeper/README.md)
4. [Cluster Component](components/wookie-cluster/README.md)
5. [Cache Component](components/wookie-cache/README.md)
6. [Memcache Component](components/wookie-cache-memcache/README.md)
8. [Kafka Component](components/wookie-kafka/README.md)
9. [Netty Component](components/wookie-netty/README.md)
9. [Socko Component](components/wookie-socko/README.md)
