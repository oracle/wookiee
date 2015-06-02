# Wookiee - Socko Routes

The Socko component has a set of traits that you can mixin to your command to enable automatic routing. At it's most basically level you would simply add the trait to the command and a route would automatically be added and your command would be executed when the route was hit by the client. In reality it is most likely that there would be a couple of functions that would be overridden to address the specific needs of your command. By default the route will use the path "_wt_internal/[COMMAND_NAME]" which in general would be modified to what you want by overriding the function ```override def path: String = "/newpath/mycommand"```. In the example you would basically have a get request route using the path "/newpath/mycommand". This document will describe the default cases for all the http methods and how you can extend the traits for specific uses.

### Http Method Traits
Below is the list of [mixin traits](../src/main/scala/com/webtrends/harness/component/socko/route/SockoRoutes.scala) which automatically add http method routes to a command simply by mixing the trait into the command.

* SockoGet
* SockoCustom
* SockoPost
* SockoDelete
* SockoOptions
* SockoHead
* SockoPut

A basic example would be to mixin your trait like below

```
class FooCommand extends Command with SockoGet {
    implicit val executionContext = context.dispatcher
    override def path: String = "/bar"
    override def commandName: String = "Foo"
    override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
        // here is where our business logic
    }
}
```

Above is literally the simplest example for this. However this will create a command that will execute the execute function and be accessed via a Get request using the path /bar. That is all that is required. Obviously things can get more complicated and that is where you would want to extend the functionality of the Command and the routing ability of the Command. Lets go through it line by line.

1. This is the definition for your class which includes the SockoGet trait which will add the GET route
2. We are required to define the execution context as the execute function requires it
3. We are overriding the default path of /_wt_internal/Foo to be /bar instead
4. We are setting the command name which is used for various things including registering the command
5. Definition of the execute function which we would just need to fill in the details.

One other thing to note is the SockoCustom trait. This trait will allow the developer to create a complete custom route and bypass all the automatic routing functionality. This would be for specific cases where very specific Socko functionality is required. To do this you would simply override the function ```def customMatcher(event: HttpRequestEvent) : Option[mutable.HashMap[String, AnyRef]]```. Example:

```
override def customMatcher(event: HttpRequestEvent) : Option[mutable.HashMap[String, AnyRef]] = {
    Command.matchPath("example/path", event.endPoint.path) match {
      case Some(b) =>
        b.addValue(CommandBean.KeyPath, p._1)
        val bean = new SockoCommandBean(event)
        bean.appendMap(b.toMap)
        return Some(bean)
      case None => // ignore and continue in the stream
    }
}
```

One a match is found, one should determine their own behavior with customHandler, IMPORTANT: be sure to write to the event in this handler, it is up to the user to ensure the event is dealt with at this point

```
override def customHandler(bean: mutable.HashMap[String, AnyRef]) {
    val commandBean = bean.asInstanceOf[SockoCommandBean]
    commandBean.response.write(HttpResponseStatus.OK, "Handled!")
}
```

Note the function innerExecute(event), this function helps by automatically executing the execute function on the command. However this is not required, although it would be highly recommended that you pass the actual business logic off to the execute function otherwise using the Command would be fairly pointless.

### Creating the path
Creating a path within a Command is greatly simplified, and although the Path function is useful it is difficult to create a parameterized path. A String is required in the Command as the base Command trait can have no notion of the underlying transfer protocols that are used to execute it. The primary reason for Commands is to create business units that can have various transportation protocols easily added to it and switched out. However the simplicity of the RoutePath should handle the majority of use cases. In the case that it does not, the developer would be required to use the SockoCustom trait and write the route manually. The [RoutePath](../src/main/scala/com/webtrends/harness/component/socko/route/RoutePath.scala) can be used as follows in a route:

```
val bean = new CommandBean()
object PATH extends RoutePath(path, bean)
event match {
    case GET(PATH("/foo")) =>
        event.response.write("bar")
        Future { true }
    case _ => Future { false }
}
```

The RoutePath class handles two primary cases, firstly it splits a path using the '/' character as any URL would. Then it also allows users to get parameters from the URL, so a URL like '/account/1/user/2/obj/3' can be created with the string '/account/$accId/user/$userId/obj/$objId'. This will then match the URL and assume that any of the path elements starting with $ are variables. The variables will be placed in the [CommandBean](../../wookiee-lib/src/main/scala/com/webtrends/harness/command/CommandBean.scala) and can be retrieved in the Commands execute function using the key's like accId, userId and ObjId. The variables will automatically be converted to Integers if it is not a String.

### Marshalling/Unmarshalling
One important part of any Restful service or any Http driven API is marshalling and unmarshalling. How do I pull objects into my execute function and how to I push them back out. By default all marshalling and unmarshalling will be handled for the developer, and there would be no further requirement. However it handles the data in a very specific way and so may not be desirable for the developer. Below we outline the ways that marshalling and unmarshalling is handled

#### Default
By default all marshalling and unmarshalling is managed by the LiftJson library, and in this case any request bodies will be marshalled to JObject objects and unmarshalling back to JSON on the way out. If you don't want to deal with marshalling and unmarshalling and are ok with JSON, then you can simply leave as is. And you can access the JObject from the command bean by using the standard key that is used for any entities that are marshalled to JSON from the request body. You can access using the following

```
// variable bean is the CommandBean
val entity = b(CommandBean.KeyEntity).asInstanceOf[JObject]
```

Remember by default the entity is marshalled into a JObject so if we left it at that we would simply set it as such.

Unmarshalling is even easier, although by default it will just unmarshall to JSON you can pass in your objects directly to the CommandResponse and it will attempt to marshall the object appriorately. So if you have a case class defined as such ```case class Person(name:String, age:Int)``` it will marshall that object automatically to ```{"name":"value", "age":value}```. Basically you are not required to convert your object to JSON first and then send it along in the CommandResponse, you would just need to pass down the object. So in the end your code would look something like this:

```
val per = Person("Mike", 34)
CommandResponse[T](Some(per.asInstanceOf[T]))
```

#### Case Classes
We can however make things a little easier for ourselves in the case of marshalling. By default it will not automatically convert to case classes. The reason for this is that in the case of marshalling, we know ahead of time what we are passing down. However with a route we don't have the class manifest ahead of time and so to be explicit about it we need to override the unmarshall function in the SockoPut or SockoPost traits.

```
override def unmarshall[T <: AnyRef : Manifest](obj: Array[Byte]): Option[T] = {
    super.unmarshall[Person](obj).asInstanceOf[Some[T]]
}
```

By passing in the Person type into the super.unmarshall function we set the bounds for the manifest on the lift json marshaller and so force the json marshaller to attempt to marshall to that object type.

#### Custom
Custom marshalling is required in instances when JSON is not sufficient for our needs. If you look at the [example-rest](../../examples/example-rest) the [SCreate comamnd](../../examples/example-rest/src/main/scala/com/webtrends/service/sockorest/SCreate.scala) uses custom marshallers to take in a completely different structure and returns basically a sentence. The marshaller and unmarshaller are customized by overriding two functions ```override def unmarshall[T<:AnyRef:Manifest](obj:Array[Byte]) : Option[T]``` and ```override def marshallObject(obj:Any) : Array[Byte]```. I will use the examples created in the SCreate command in the example-rest, but basically a developer has free reign to build whatever type of marshaller and unmarshaller that they require if the default marshallers are not sufficient for their purposes.

```
  // Below is a custom unmarshaller if you want to use it differently
  // The Create command will use a custom unmarshaller and mimetype
  // and the Update command will use the default unmarshaller using JSON
  override def unmarshall[T <: AnyRef : Manifest](obj: Array[Byte]): Option[T] = {
    val Array(name, age) = (new String(obj)).split(":")
    Some(Person(name, Integer.parseInt(age)).asInstanceOf[T])
  }

  // Below is a custom marshaller if you want to use it differently
  // The Create command will use a custom marshaller and mimetype
  // and the Update command will use the default marshaller
  override def marshallObject(obj: Any): Array[Byte] = {
    val person = obj.asInstanceOf[Person]
    s"Socko Person: ${person.name}, ${person.age}".getBytes
  }
```

### Status Codes
Currently all successful routes will result in a 200. The Socko component has a default rejection handler that will reject a request based on things like request not being handled. A developer can extend the rejection handler by overriding the function ```protected def getRejectionHandler(event:HttpRequestEvent, e:Throwable)```
The exceptionHandler is defined as following:
```
protected def getRejectionHandler(event:HttpRequestEvent, e:Throwable) = {
    e match {
      case ce:CommandException =>
        log.debug(ce.getMessage, ce)
        event.response.write(HttpResponseStatus.BAD_REQUEST, s"Command Exception - ${ce.getMessage}\n\t${ce.toString}")
      case arg:IllegalArgumentException =>
        log.debug(arg.getMessage, arg)
        event.response.write(HttpResponseStatus.BAD_REQUEST, s"Illegal Arguments - ${arg.getMessage}\n\t${arg.toString}")
      case me:MappingException =>
        log.debug(me.getMessage, me)
        event.response.write(HttpResponseStatus.BAD_REQUEST, s"Mapping Exception - ${me.getMessage}\n\t${me.toString}")
      case ex:Exception =>
        log.debug(ex.getMessage, ex)
        event.response.write(HttpResponseStatus.INTERNAL_SERVER_ERROR, s"Server Error - ${ex.getMessage}\n\t${ex.toString}")
    }
}
```