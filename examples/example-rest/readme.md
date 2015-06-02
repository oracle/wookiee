# Rest Example

This is an example project that uses a lot of the concepts in the harness to build out a very basic REST client. This rest client will use Create, Read, Update and Delete actions against a case class called Person ```case class Person(name:String, age:Int) extends Cacheable```. It will leverage the Command design pattern built into the harness and use the components wookiee-http and wookiee-cache-memcache. There are various things in the example that are done in a very non-production type of way. For example the handling of exceptions. However the core of the example is simply to show how easy it is to create a restful project with the command, component and service design patterns. And to validate some of the features of the wookiee-http using Spray.

In this document I will describe how you would go about creating this service. To build this service from scratch should take very quick if you use all the defaults. In this example I have extended it somewhat to use things like custom marshalling and unmarshalling to show how to do it. But if you ignore that it would be quicker.

### Creating Service
Creating the service is fairly straight forward and one can use the service archetype to build a base very very quickly. You can read the document on building a service using the archetype [Here](../../archetypes/wookiee-service-archetypes). Let's call our service "Person" so when building the service from the archetype don't forget to add the property "service-name" = "Person". Once you have built a service archetype we need to add some of the depencies to our POM file. For this example we will be using the wookiee-cache-memcache component for storage and the wookiee-http component for our transport protocol. You can add those to your POM using the dependencies below:
```
   <dependency>
       <groupId>com.webtrends</groupId>
       <artifactId>wookiee-http</artifactId>
       <version>${platform.version}</version>
   </dependency>
   <dependency>
       <groupId>com.webtrends</groupId>
       <artifactId>wookiee-cache-memcache</artifactId>
       <version>${platform.version}</version>
   </dependency>
```
*As a quick note, this example uses memcache for it's storage however there is no reason why you couldn't use something else completely. It is just convenient in building out this example.*

We need to modify the default state of the object PersonService to do the following things:

1. Add an implicit timeout for the initialization of our cache. This could be configurable if you wanted but not necessary at the moment. If you wanted to make it configurable you would modify the resources/wookiee-person.conf file with the following:
   ```
	 wookiee-person {
	 	cache-timeout = 2s
	 }
   ```
   However adding the line ```implicit val timeout = Timeout(2 seconds)``` is acceptable for the example.
2. Override the actor's preStart function to initialize the cache. Because this is not really part of the tutorial and more related to memcache and the memcache component I will simply say reference the code in the [PersonService](src/main/scala/com/webtrends/service/PersonService.scala) and for more information see the [Memcache Component](../../components/wookiee-cache-memcache)
3. Create our case class Person object which in our example will be Cacheable so that we can easily shove it into Memcache and get it out.

   ```
   case class Person(name:String="None", age:Int=0) extends Cacheable[Person] {
     override def key: String = name
     override def namespace: String = PersonService.Namespace
   }
   ```

### Creating Commands
The example code shows 5 commands, however the ReadJSON command is really just an extra command to show different functionality. We are going to deviate somewhat from the example code, and stick to the very basics of building out the most basic code and use as much of the defaults as we can. Initially the service archetype would have created a single command, we can rename this to "Create" instead of "PersonCommand" and then build the boiler plate code that will be the same for each command and then copy and paste for our four commands:

* Create - This will create the object and store in memcache and return true or false
* Read - This will read the object from memcache and return the Person object in the response
* Update - We are not going to build this one as in this example it is only different because it is showing a different way to do things with Put. However in a real Restful service you would obviously build this to specifically be an update.
* Delete - This will delete the object from memcache and return a 204 to the user.

First the boiler plate code - All the commands will have this common code - which a lot is already built by the archetype. I have removed the comments to make it more compactful

```
package com.webtrends.service

import com.webtrends.harness.command.{Command, CommandResponse, CommandBean}
import scala.concurrent.Future

class Create extends Command with ComponentHelper {
	implicit val executionContext = context.dispatcher
	override def path: String = "/person"
  	override def commandName: String = Create.CommandName
  	override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
		Future {
      	  		CommandResponse("TODO: All execute work here".asInstanceOf[T])
    		}
  	}
}

object Create {
	def CommandName = "Create"
}
```

For each command we create, in the simple example three commands, we would replace the text "Create" with "Read" and "Delete" appropriately. Each command will have a different execute function and I will go through each one, although the execution of the command does not specifically relate to what I am trying to show it will help understand a little more what we are trying to do.
#####Create
```
	val p = Promise[CommandResponse[T]]
    bean match {
      case Some(b) =>
        getComponent("wookiee-cache-memcache") onComplete {
          case Success(actor) =>
            val person = b(CommandBean.KeyEntity).asInstanceOf[Person]
            person.writeInCache(actor) onComplete {
              case Success(s) => p success CommandResponse[T](Some(true.asInstanceOf[T]))
              case Failure(f) => p success CommandResponse[T](Some(false.asInstanceOf[T]))
            }
          case Failure(f) => p failure f
        }
      case None => p failure new CommandException("Create", "Bean not set")
    }
    p.future
``` 

* We first check to see if we have a command bean, which always should be set in the scenario we are establishing and so we should never hit the None case.
* we use the getComponent helper function from the trait "ComponentHelper" that we mixed in to get the memcache component manager, so that we can store the object in memcache.
* We pull the Person object from the bean using the KeyEntity key which will require a little more work to get the marshalling to work like we expect it, which I will show in the next section.
* We execute the "writeInCache" function that is part of every Cacheable object which will simply store the object directly into memcache
* Send the CommandResponse back with the true if it was able to create it and false otherwise

#####Read
```
	val p = Promise[CommandResponse[T]]
    bean match {
      case Some(b) =>
        getComponent("wookiee-cache-memcache") onComplete {
          case Success(actor) =>
            val personName = b("name").asInstanceOf[String]
            Person(personName).readFromCache(actor) onComplete {
              case Success(person) =>
                person match {
                  case Some(per) => p success CommandResponse[T](Some(per.asInstanceOf[T]))
                  case None => p failure CommandException("PersonRead", s"Person not found with name [$personName]")
                }
              case Failure(f) => p failure f
            }
          case Failure(f) => p failure f
        }
      case None => p failure new CommandException("Create", "Cache not initialized")
    }
    p.future
```

* First two sections are identical to Create command
* Pull the $name variable from the path so that we know what Person we are looking up. This is defined by the path, which I will show later
* If found, send the CommandResponse back with the person object, if not send a CommandException which will end up being a 400 Bad Request. This may not be desirable in a case like this, however it is an example showing how you would send a 400 back.

#####Delete
```
	val p = Promise[CommandResponse[T]]
    bean match {
      case Some(b) =>
        getComponent("wookiee-cache-memcache") onComplete {
          case Success(actor) =>
            // If we were doing a real API we might want to check the cache to see if it
            // exists first and if it does then throw some sort of exception, but this is just an example
            val personName = b("name").asInstanceOf[String]
            Person(personName).deleteFromCache(actor) onComplete {
              case Success(s) => p success CommandResponse[T](None, "txt")
              case Failure(f) => p failure f
            }
          case Failure(f) => p failure f
        }
      case None => p failure new CommandException("Create", "Cache not initialized")
    }
    p.future
```

* First three sections are identical to Read Command
* Simply delete the person based on the name from Memcache and return a 204. By setting None in the CommandResponse it will automatically return a 204 when we extend it with Spray.

### Extending with Spray Routes
At this point we have simply created the business logic for the commands. And currently we could access them only through Akka and the CommandManager, which in some cases would be sufficient, however this example is specifically about creating a Restful service using our wookiee-http component. So below I will detail the couple things that you would be required to do for each command. This is really the easiest part, as all the heavy lifting is done behind the scenes.

##### Create

1. Mixin the SprayPost trait
2. Override following function ```override def setRoute = putRoute[Person]```

##### Read

1. Mixin the SprayGet trait
2. Modify the path for Read, so that it looks like "/person/$name"

#### Delete

1. Mixin the SprayDelete trait
2. Modify the path for Delete, so that it looks like "/person/$name"

And literally that is all you do to add Spray Http capabilities to your commands, at the most basic level.

### End Points
Once you have completed that you will end up with the following end points with examples to show how they would work.

| End Point    | Type   | Request | Response |
| ------------ |:------:|:-------:|---------:|
| /person      | POST   | {"name":"Mike","age":34} | {"name":"Mike","age":34} |
| /person/Mike | GET    |         | {"name":"Mike","age":34} |
| /person/Mike | DELETE |         | 204 NoContent |
