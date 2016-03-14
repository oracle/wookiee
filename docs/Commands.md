# Commands
A command is where you business logic resides and is a place that is seperated from the rest of the system. This encourages developers to place business code away from other parts of the system and allows the developer to focus on the business requirements instead of the pieces needed for support of applications like HTTP transport or Akka messaging. Obviously a developer should understand these things, but you could technically get away with not knowing a single thing about it and still get things to work as they would expect.

###Design
The design is fairly simple and relies on Akka quite heavily by hiding some of the complexity from the developer of the command. Below is a list of the components and how they relate to each other.

1. CommandManager - This component manages all the commands in the system. A developer would add commands to the CommandManager which would then be available for execution via Akka Messaging. The companion object contains the list of commands available to the developer.
2. Command - An individual command is an actor that is basically a child of the CommandManager. Or more precisely it is a routee where as the CommandManager is the router. Each command has deployment properties that defined how many routees are created for the command and what type of routing it will use. Along with multiple other options. For more information see [Akka Routing](http://doc.akka.io/docs/akka/snapshot/scala/routing.html). Here is an example of the configuration that a developer can add into their application.conf file.
3. CommandBean - A map of properties that is passed to the Command on execution. 

	```
	akka.actor.deployment {
	  /system/command/Ping {
	    router = round-robin
	    nr-of-instances = 3
	  }
	}
	```
4. Service - The base service object contains helper functions that enable developers to easily use the Commands. Alternatively you can include the trait CommandHelper that includes the helper functions. Below is a list of those commands:
	* ```addCommand(name:String, actorClass:Class[T], remote:Boolean=false)``` - This function allows you to easily add commands to the CommandManager, generally should not be used outside of the addCommands function. See below.
	* ```executeCommand(name:String, bean:Option[CommandBean]=None, server:Option[String]=None, port:Int=0) : Future[BaseCommandResponse]``` - Executes commands. Include the server and port if you want the command executed on a remote server.
	* ```addCommands``` - This function is called once the service resolves the actorRef for the CommandManager, so you would add your commands by overriding this function.
```scala 
override def addCommands = {
	addCommand("CommandName", classOf[CommandClass])
} 
```
Above is an example of how you would register your commands
```scala
executeCommand(PingCommand.CommandName) onComplete {
	case Success(s) => ctx.complete(s.data.asInstanceOf[String])
    case Failure(f) => ctx.complete(f.getMessage)
}
```	
Above is an example of how you would execute your commands and get a response of type BaseCommandResponse.

###Example
For an example on how all this will work in the code you can see the example "example-http" for specific detail. However a basic Command would look as follows:
```
class ExampleCommand extends Command {
    override def commandName: String = "Example Command"

    def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
        Future { CommandResponse(Some("Results after Execution").asInstanceOf[T])) }
    }
}
```
Basic example of executing a remote command:
```
executeCommand[String]("CommandName", None, "10.0.0.1", 2552) onComplete {
	case Success(s) => println(s)
	case Failure(f) => f.printStackTrace()
}
```

Note* If you create multiple commands in your service with the same name, you can run into a possibility that will cause unexpected behavior and possibly errors that are less than helpful. :)
