#Wookiee - Working with HttpClient
###Overview
Included in Wookiee is a HttpClient that uses Spray http client under the covers. The client uses Akka messaging to send 
* GET 
* POST 
* UPDATE
* DELETE
* PUT
* PATCH
* OPTIONS
* HEAD
requests to the underlying http client. 

###Usage
First requirement is to get the httpClient actor, you can do this by executing the following code:

```val httpClient = actorSelection("/user/system/component/wookiee-spray/spray-client")```

From that point you simply send messages to the client receiving ```Future[HttpResp]``` in response

####Messages
Below is a list of messages that can be sent the httpClient actor for requests to be executed against a server

| Name | Structure |
|:-----|:----------|
| HttpGet | ```HttpGet(config:HttpConfig, path:String, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpPost | ```HttpPost[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpPut | ```HttpPut[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpDelete | ```HttpDelete(config:HttpConfig, path:String, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpOptions | ```HttpOptions[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpPatch | ```HttpPatch[T](config:HttpConfig, path:String, body:T, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpHead | ```HttpHead(config:HttpConfig, path:String, headers:List[HttpHeader]=HttpConstants.defaultHeaders)``` |
| HttpPing | ```HttpPing(config:HttpConfig, path:String="ping")``` |

```HttpConfig(server:String, port:Int:80, useSSL:Boolean=false, timeout:Int=5)```

* The HttpConfig object defines the connection that you will use to execute the request
* The body of type T will be automatically a JSON object, so content-type on the header will always be set to 'application/json' and the object that you pass in will be marshalled to a JSON object automatically
* By default you do not have to send in the headers, and it will default to the following:

```
Content-Type:application/json
Connection:keep-alive
Cache-Control:max-age(0)
Accept:application/json,text/html,application/xml,application/xhtml+xml
Accept-Encoding:gzip,deflate
```

####Response
The response or object that is sent back to the sender will always be<br/>
```HttpResp(resp:HttpEntity, headers:List[HttpHeader], statusCode:StatusCode)```<br/>
This object contains
* HttpEntity - this is basically the body of the response, you can use resp.asString to get the stringified version of it, you can then also extract an object through JSON if you have your marshallers set up correctly using ```parse(resp.asString).extract[ObjectClassName]```
* List[HttpHeader] - the list of http headers that were sent back on the response
* StatusCode - the Http status code that was sent along with the response

The only exception to this would be on HttpPing, when you send this message it will response with ```ConnectionStatus(connect:Boolean, info:String)``` This message is just to ping the server to see if it is up and responding to requests. By default the path for the ping will be set to ```/ping``` however it can be overridden in the message. 

###Example
This code has not been compiled or tested, but the idea should work

```
case class ResponseData(data:Detections)
case class Detections(languages:List[Language])
case class Language(language:String, isReliable:Boolean, confidence:Double)

val httpClient = actorSelection("/user/system/component/wookiee-spray/spray-client")
val httpConfig = new HttpConfig("www.googleapis.com")
val futureResponse = httpClient ? HttpGet(httpConfig, "/language/translate/v2/languages?key=<INSERT-YOUR-KEY>&target=zh-TW")(httpConfig.getTimeout)
// for blocking response uncomment this section and comment out the stuff below
// val resp = Await.result(f, httpConfig.getTimeout.duraction).asInstanceOf[HttpResp]
// val myObject = JsonParser.parse(resp.resp.asString).extract[ResponseData]
futureResponse onComplete {
  case Success(result) =>
    val myObject = JsonParser.parse(resp.resp.asString).extract[ResponseData]
  case Failure(e) => // throw exception or something to handle the error
}
```
