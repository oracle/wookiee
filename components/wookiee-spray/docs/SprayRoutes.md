# Wookiee - Spray Routes

The wookiee-http component includes a set of traits that you can mixin to your commands to enable automatic routing. For example you can mixin the trait SprayGet and simply by adding that you will automatically get a spray route through GET that will execute your command. By default the route will use the path "_wt_internal/[COMMAND_NAME]" which in general would be modified to what you want by overriding the function ```override def path: String = "/newpath/mycommand"```. In the example you would basically have a get request route using the path "/newpath/mycommand". This document will describe the default cases for all the http methods and how you can extend the traits for specific uses.

### Http Method Traits
Below is the list of [mixin traits](../src/main/scala/com/webtrends/harness/component/spray/route/SprayRoutes.scala) which automatically add http method routes to a command simply by mixing the trait into the command.

* SprayGet
* SprayCustom
* SprayPost
* SprayDelete
* SprayOptions
* SprayHead
* SprayPatch
* SprayPut

A basic example would be to mixin your trait like below

```
class FooCommand extends Command with SprayGet {
    implicit val executionContext = context.dispatcher
    override def path: String = "/bar"
    override def commandName: String = "Foo"
    override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
        // here is where our business logic
    }
}
```

Above is literally the simplest example for this. However this will create a command that will execute the execute function and be accessed via a Get request using the path /bar. That is all that is required. Obviously things can get more complicated and that is where you would want to extend the functionality of the Command and the routing ability of the Command. Lets go through it line by line.

1. This is the definition for your class which includes the SprayGet trait which will add the GET route
2. We are required to define the execution context as the execute function requires it
3. We are overriding the default path of /_wt_internal/Foo to be /bar instead
4. We are setting the command name which is used for various things including registering the command
5. Definition of the execute function which we would just need to fill in the details.

One other thing to note is the SprayCustom trait. This trait will allow the developer to create a complete custom route and bypass all the automatic routing functionality. This would be for specific cases where very specific Spray functionality is required. To do this you would simply override the function ```def customRoute : Route```. Example:

```
override def customRoute: Route = {
    exceptionHandler {
      get {
        path("foo" / "bar") {
          innerExecute()
        }
      }
    }
  }
```

Note the function innerExecute(), this function helps by automatically executing the execute function on the command by using the onComplete directive. However this is not required, although it would be highly recommended that you pass the actual business logic off to the execute function otherwise using the Command would be fairly pointless.

### Creating the path
Creating a path within a Command is greatly simplified, and although the Path directive is very impressive it is difficult to create a path directly from a String. A String is required in the Command as the base Command trait can have no notion of the underlying transfer protocols that are used to execute it. The primary reason for Commands is to create business units that can have various transportation protocols easily added to it and switched out. However the simplicity of the CommandPath should handle the majority of use cases. In the case that it does not, the developer would be required to use the SprayCustom trait and write the route manually. The [directive](../src/main/scala/com/webtrends/harness/component/spray/directive/CommandDirectives.scala) can be used as follows in a route:

```
get {
  commandPath("/foo") {
    complete("bar")
  }
}
```

The directive handles two primary cases, firstly it splits a path using the '/' character as any URL would. Then it also allows users to get parameters from the URL, so a URL like '/account/1/user/2/obj/3' can be created with the string '/account/$accId/user/$userId/obj/$objId'. This will then match the URL and assume that any of the path elements starting with $ are variables. The variables will be placed in the [CommandBean](../../wookiee-core/src/main/scala/com/webtrends/harness/command/CommandBean.scala) and can be retrieved in the Commands execute function using the key's like accId, userId and ObjId. The variables will automatically be converted to Integers if it is not a String.

### Marshalling/Unmarshalling
One important part of any Restful service or any HTTP driven API is marshalling and unmarshalling. How do I pull objects into my execute function and how to I push them back out. By default all marshalling and unmarshalling will be handled for the developer, and there would be no further requirement. However it handles the data in a very specific way and so may not be desirable for the developer. Below we outline the ways that marshalling and unmarshalling is handled

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
We can however make things a little easier for ourselves in the case of marshalling. By default it will not automatically convert to case classes. The reason for this is that in the case of marshalling, we know ahead of time what we are passing down. However with a route we don't have the class manifest ahead of time and so to be explicit about it we need to override a simple function in the SprayPut or SprayPost traits.

```
override def setRoute = putRoute[Person]
```

This is simply a function we override in the Command, and by passing in the Person type into the putRoute function we set the bounds for the manifest on the lift json marshaller and so force the json marshaller to attempt to marshall to that object type.

#### Custom
Custom marshalling is required in instances when JSON is not sufficient for our needs. If you look at the [example-rest](../../examples/example-rest) the [Create comamnd](../../examples/example-rest/src/main/scala/com/webtrends/service/rest/Create.scala) uses custom marshallers to take in a completely different structure and returns basically a sentence. The marshaller and unmarshaller are customized by overriding two functions ```override implicit def InputUnmarshaller[T:Manifest]``` and ```override implicit def OutputMarshaller[T<:AnyRef]```. I will use the examples created in the Create command in the example-rest, but basically a developer has free reign to build whatever type of marshaller and unmarshaller that they require if the default marshallers are not sufficient for their purposes.

```
  // Below is a custom unmarshaller if you want to use it differently
  // The command will use a custom unmarshaller and mimetype
  override implicit def InputUnmarshaller[T:Manifest] =
    Unmarshaller.delegate[String, T](ContentTypes.`plain/text`) {
      per =>
        val Array(name, age) = per.split(":")
        Person(name, Integer.parseInt(age)).asInstanceOf[T]
    }
  // Below is a custom marshaller if you want to use it differently
  // The command will use a custom marshaller and mimetype
  override implicit def OutputMarshaller[T<:AnyRef] =
    Marshaller.delegate[T, String](ContentTypes.`plain/text`) {
      per =>
        val perIn = per.asInstanceOf[Person]
        s"${perIn.name}:${perIn.age}"
    }
```

### Mimetypes
As you may have noticed in the previous section regarding custom marshallers, there was code showing ContentTypes, these would basically be the mimetypes that are used in getting data from the request or returning data to the user. By default the mimetypes are primarily focused around `application/json`. However a user has the ability to create a custom mimetype and plug it into the custom marshallers and unmarshallers as well as the CommandResponses.
To register a type you could create a companion object in your service by doing the following:

```
object MyService {
  val MyMimeType = "application/vnd.webtrends.myobject"
  val `application/vnd.webtrends.myobject` = MediaTypes.register(MediaType.custom(MyMimeType))
}
```

We can then use the mime type in place of ```ContentTypes.`plain/text```` that was shown in the custom marshaller and unmarshaller or in the CommandResponse as such

```
val per = Person("Mike", 34)
CommandResponse[T](Some(per.asInstanceOf[T]), MyService.MyMimeType)
```

This would then send the mime type back in the response to the user.

### Status Codes
Currently all successful routes will result in a 200. Spray has a default rejection handler that will reject a request based on things like request not being handled. For more information on the default rejection handling from Spray you can look [here](http://spray.io/documentation/1.2.2/spray-routing/key-concepts/rejections/). A developer can extend the rejection handler by overriding the function ```protected def getRejectionHandler : Directive0 = exceptionHandler```
The exceptionHandler is defined as following:
```
def exceptionHandler = handleExceptions(ExceptionHandler({
    case ce:CommandException => complete(BadRequest, s"Command Exception - ${ce.getMessage}\n\t${ce.toString}")
    case arg:IllegalArgumentException => complete(BadRequest, s"Illegal Arguments - ${arg.getMessage}\n\t${arg.toString}")
    case me:MappingException => complete(BadRequest, s"Mapping Exception - ${me.getMessage}\n\t${me.toString}")
    case ex:Exception => ctx => failWith(ex)
  }))
```
