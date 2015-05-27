# Wookie - Creating a Component

## Overview

This document contains information on how to create a simple maven project for a Wookie component. It will use Maven archetypes to layout a basic project. It also sets out a design pattern that should be adhered to when building a new component, and giving a basic understanding of the underlying support structure that the component leverages.

## Component Design Pattern

A component is comprised of 3 primary objects:

1. The component traits
2. The component actors
3. The component manager

#### 1. The component traits
The component trait is generally a very simple bit of code that starts the component actor that it is associated with. The reason for having this trait is firstly to allow the component manager to start any actors that it requires easily as children of the manager actor, and secondly to allow developers to use the component as a library outside of Wookie. By extending an actor with the trait you can simply use the start function to start the component and the stop function to stop the component. Here is an example of the code for the trait. Which would almost be identical for any trait.
```scala
trait MyComponent {
	this: Actor =>
	
	def startMyComponent : ActorRef = {
		context.actorOf(MyComponentActor.props, MyComponent.MyComponentName)
		// any other code that is needed for starting up the actor
	}
	
	def stopMyComponent = {
		// execute any code that the actor needs for clean shutdown
	}
}
object MyComponent {
	val MyComponentName = "MyComponent"
}
```
A component trait matches to a single component actor, and although there is no "interface" as part of the design pattern we expect at the very least that the function "startMyComponent" is present and that it returns an actorRef. This allows a user to use this as part of a library and start up the actor exactly like the manager would expect now the developer gets more flexibility in it's usage. Generally the trait is expected to be written exactly like this, with the term "MyComponent" replaced everywhere with the name of the actor that you are developing.

#### 2. The component actors
The component actor is where the logic of the actual component goes. This is where the messages will be received and executed against. This is the actor that the trait above is starting. Below is the basic code outline for the actor.
```scala
object MyComponentActor {
	def props:Props = Props(classOf[MyComponentActor])
}
class MyComponentActor with Actor with ActorHealth with ActorLoggingAdapter {
	
	def receive = health orElse {
		// receive message functionality here
		case MyComponentMessage => MyComponentMessageFunction
	}
	
	def MyComponentMessageFunction = {
		// do some work
		sender ! "work response here"
	}
	
	// you generally would override this function with any specific health information 
	// for the actor
	override protected def getHealth : Future[HealthComponent] = super.getHealth
}
```
The actor is very flexible and the primary thing around the development of the actor would be the props function in the object (akka best practices), and overriding the getHealth function. Otherwise a user would be free to build out the actor as required for the use case that is being developed against.

#### 3. The component manager
The component manager is the piece of the puzzle that holds all the actors of the component, starts and stops all the child actors, and routes messages between the actors seamlessly. Below is a simple example of a component manager.
```scala
class MyComponentManager(name:String) extends Component(name) with MyComponent {
	override protected def defaultChildName = Some(MyComponent.MyComponentName)
	
	override def receive = super.receive
	
	override def start = {
		// in this function you start any of your component child actors
		startMyComponent
	}
	
	override def stop = {
		// in this function you stop any of your component child actors
		stopMyComponent
	}
}
```

## Messaging within Component child actors
Messaging amongst the child actors within a component is managed through the ComponentManager. There is no extra work that is required, adding the trait ComponentHelper will add the following functions:

* ```def request(name:String, msg:Any, childName:Option[String]=None)(implicit timeout:Timeout) : Future[ComponentResponse]``` - Making this request will route the request through the ComponentManager and onto the requested component by using the name variable. If there is only one child actor then there is no need to provide any potential child of the component. The msg variable is simple the message that is handled by the eventual actor.
* ```def message(name:String, msg:Any, childName:Option[String]=None)``` - This will simply route a message to the Component through the ComponentManager just like the request. In this instance though there is no future and fire it and go on.
* ```def getComponent(name:String)(implicit timeout:Timeout) : Future[ActorRef]``` - The getComponent function allows you to get the actor reference for the component, for any specific functionality that you require.

## Deploying Dev Wookie Component
wookie-lib comes with some bash scripts that enable a developer to deploy components easily to Wookie. The bash scripts can be found in the folder <WOOKIE_ROOT_PATH>/wookie-core/bin.
When in that directory you can execute the command simply like ```./setDevComponent.sh```. Check the permissions on the file if you are having a problem executing the bash script, however you should be the owner and should have no issues with it. Once the script has been executed it will create a directory in the wookie-lib called "components". This directory will contain all the components that currently exist under <WOOKIE_ROOT_PATH>/components. Initially they will all be disabled, so although you deployed the code (which is just symbolic links) they will not start up in Wookie. The assumption is that not all components will be required for whatever service the developer is building. To enable a component in Wookie just go to the conf file for that component and set the enabled flag in the config file to true. On next startup the component that you enabled will startup along with Wookie.

A component can be deployed to the harness using two methods, one being a pseudo uber JAR and the other being a directory structure. The bash scripts will deploy using the uber JAR however the directory structure is still supported. The preferred method is to use the uber JAR and there is no real advantage over using the directory structure.

#### Deploying Component as JAR
The following plugin for Maven is included in all current components, and if you are using the component archetype it will include the plugin automatically into your pom.xml file:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
     <artifactId>maven-shade-plugin</artifactId>
     <version>2.3</version>
     <configuration>
         <artifactSet>
             <includes>
                 <include>*:*</include>
             </includes>
         </artifactSet>
         <filters>
             <filter>
                 <artifact>*:*</artifact>
                 <excludes>
                     <exclude>META-INF/*.SF</exclude>
                     <exclude>META-INF/*.DSA</exclude>
                     <exclude>META-INF/*.RSA</exclude>
                 </excludes>
             </filter>
         </filters>
         <transformers>
             <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                 <resource>reference.conf</resource>
             </transformer>
         </transformers>
     </configuration>
     <executions>
         <execution>
             <id>Full</id>
             <phase>package</phase>
             <goals>
                 <goal>shade</goal>
             </goals>
             <configuration>
                 <finalName>full-${project.artifactId}-${project.version}</finalName>
             </configuration>
         </execution>
         <execution>
             <id>Component</id>
             <phase>package</phase>
             <goals>
                 <goal>shade</goal>
             </goals>
             <configuration>
                 <finalName>component-${project.artifactId}-${project.version}</finalName>
                 <artifactSet>
                     <excludes>
                         <!-- These are all libraries that are already loaded as part of the harness
                                 So we can keep them completely out of the component version of the Uber Jar -->
                         <exclude>com.webtrends:wookie-core:*</exclude>
                         <exclude>org.scala-lang:scala-library:*</exclude>
                         <exclude>com.typesafe.akka:akka-actor_*:*</exclude>
                         <exclude>com.typesafe.akka:akka-cluster_*:*</exclude>
                         <exclude>com.typesafe.akka:akka-remote_*:*</exclude>
                         <exclude>com.typesafe:config:*</exclude>
                         <exclude>org.slf4j:slf4j-api:*</exclude>
                         <exclude>ch.qos.logback:logback-classic:*</exclude>
                         <exclude>joda-time:joda-time:*</exclude>
                         <exclude>org.scala-lang.modules:scala-parser-combinators_*:*</exclude>
                         <exclude>com.esotericsoftware.kryo:kryo:*</exclude>
                         <exclude>de.javakaffee:kryo-serializers:*</exclude>
                         <exclude>com.twitter:chill_*:*</exclude>
                         <exclude>com.twitter:chill-akka_*:*</exclude>
                         <exclude>org.objenesis:objenesis:*</exclude>
                         <exclude>commons-net:commons-net</exclude>
                     </excludes>
                 </artifactSet>
             </configuration>
         </execution>
     </executions>
</plugin>
```

This plugin will generate two files, "full-[COMPONENT_NAME]-[COMPONENT_VERSION].jar" and "component-[COMPONENT_NAME]-[COMPONENT_VERSION].jar". The full version is a complete uber jar, with all dependencies. The component version will exclude all dependencies that are known to be contained in Wookie. Both versions will concatenate all reference.conf files which each component has and which may be contained in other dependent libraries. Both jars can be used in Wookie, the component jar however is simply lighter. You would just need to drop this Jar into the "components" directory of Wookie to get it working. The ComponentManager in Wookie expects the filename structure that is shown above. If you really need/want to change the component name you can add the mapping application.conf of your harness, like so:

```
wookie-system {
  ...
  component-mappings {
    componentJarName = "componentName"
  }
  ...
}
```

#### Deploying Component as Directory Structure

This is not the preferred method but is still supported by Wookie. In this scenario you would drop original jar [COMPONENT_NAME]-[COMPONENT_VERSION].jar and all library dependences in the target/lib directory into a subdirectory under the [COMPONENT_NAME] directory under the harness components directory. The reference.conf file for the component would be copied under the [COMPONENT_NAME] directory.

#####NOTES

* The script deploys the components to a subdirectory in the wookie-lib called "components" which is the default directory for components found in the application.conf config file. If you change this in the config file it will mean that when you start up Wookie it will look in a different directory for the components and this script will basically not work.

## IntelliJ (Idea)

In order to create a component using IntelliJ (12.x or later), you should use the following steps: (Steps are based on 14.x however are very similar in 12.x and 13.x)

1. Select "File"->"New Module" or "New Project".
2. Select the "Maven" module/project type.
3. On the same screen click the "Create from archetype" checkbox and select our archetype `com.webtrends.archetypes:wookie-component-archetype`.
4. If the archetype does not exist in the list, click "Add archetype".  Enter `com.webtrends.archetypes` for the group id, `wookie-component-archetype` for the artifact id, and some version number (e.g. "1.0-SNAPSHOT") for the version.
5. If you needed to add, click OK.

6. Click Next and then insert `com.webtrends.service` for the GroupId and the name of the new component you are creating for ArtifactId
7. Click Next.
8. Click the `+` sign in the properties box and add the following property `component-name` with the value that will equal the name of your new component. (This will be the name of the primary class file)
9. Click Next.
10. Insert the Module name and locations where you want to place the module/project
11. Click Finish.
12. Once everything is loaded, click on the refresh button for your maven component to import sources.

13. Add files to git as required.
