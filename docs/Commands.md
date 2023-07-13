# Commands
A command is where you business logic resides and is a place that is seperated from the rest of the system. This encourages developers to place business code away from other parts of the system and allows the developer to focus on the business requirements instead of the pieces needed for support of applications like HTTP transport or Akka messaging. Obviously a developer should understand these things, but you could technically get away with not knowing a single thing about it and still get things to work as they would expect.

## Design
The design is fairly simple and hides much of the complexity from the developer of the command. Below is a list of the classes and how they relate to each other.

1. [WookieeCommandExecutive](../wookiee-libs/src/main/scala/com/oracle/infy/wookiee/command/WookieeCommandExecutive.scala) - This class manages all the commands in the system. A developer would add commands to the WookieeCommandManager which would then be available for execution via Akka Messaging. The companion object contains the list of commands available to the developer.
2. [WookieeCommand](../wookiee-libs/src/main/scala/com/oracle/infy/wookiee/command/WookieeCommand.scala) - An individual command is an instance of a class that is basically a child of the WookieeCommandExecutive. Or more precisely it is a routee whereas the WookieeCommandExecutive is the router.
   1. A WookieeCommand allows for any type of Input and Output as specified by the developer. The Input and Output are defined by the developer and are not restricted by the system.
   ```scala
   case class MyInput(input: String)
   case class MyOutput(output: String)
	  
   class MyCommand extends WookieeCommand[MyInput, MyOutput] {
     // This is the name of the command that will be used to execute it 
     override def commandName: String = "MyCommand"
   
	 override def execute(input: MyInput): Future[MyOutput] = {
	   // Do some work here
	 }
   }
   ```
3. [WookieeCommandHelper](../wookiee-libs/src/main/scala/com/oracle/infy/wookiee/command/WookieeCommandHelper.scala) - This trait/object allows for easy access to `WookieeCommandExecutive` in order actually add WookieeCommands to the system. This is done by calling the `registerCommand` function. This function takes an instance of the WookieeCommand.
```scala
import com.oracle.infy.wookiee.service.ServiceV2
import com.typesafe.config.Config

class MyService(config: Config) extends ServiceV2 {
  override def start(): Unit = {
	WookieeCommandHelper.registerCommand(new MyCommand)
  }
}
```
4. [WookieeCommandHelper](../wookiee-libs/src/main/scala/com/oracle/infy/wookiee/command/WookieeCommandHelper.scala) - This trait/object also allows easy execution of previously registered WookieeCommands. Simply call the `executeCommand` function and pass in the name of the command and the input. The function returns a `Future` of the output type.
```scala 
WookieeCommandHelper.executeCommand[MyOutput]("MyCommand", MyInput("Some Input"))
```
5. For registering and executing Commands that can be remotely executed on a different Wookiee Service. See the [Wookiee Discovery Component](../wookiee-discovery/README.md) for more information.

Note* If you create multiple commands in your service with the same name, you can run into a possibility that will cause unexpected behavior and possibly errors that are less than helpful. :)
