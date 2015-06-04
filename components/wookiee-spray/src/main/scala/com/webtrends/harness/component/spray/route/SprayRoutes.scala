/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.spray.route

import akka.actor.{Props, Actor, ActorLogging}
import akka.io.Tcp
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.{CommandResponse, Command, CommandBean}
import com.webtrends.harness.component.spray.{HttpReloadRoutes, SprayManager}
import com.webtrends.harness.component.spray.directive.CommandDirectives
import net.liftweb.json._
import net.liftweb.json.ext.JodaTimeSerializers
import spray.http._
import spray.httpx.LiftJsonSupport
import spray.httpx.marshalling.ToResponseMarshaller
import spray.httpx.unmarshalling._
import spray.routing._
import spray.routing.directives.MethodDirectives

import scala.util.{Try, Failure, Success}

/**
 * Used for command functions that are required for all Spray traits that you can add to commands
 * to add GET, POST, DELETE, UPDATE routes to the command
 *
 * @author Michael Cuthbert on 12/5/14.
 */
private[route] trait SprayRoutes extends CommandDirectives
    with CommandRouteHandler
    with LiftJsonSupport {
  this : Command =>

  import context.dispatcher
  implicit def liftJsonFormats = Serialization.formats(NoTypeHints) ++ JodaTimeSerializers.all
  protected def getRejectionHandler : Directive0 = rejectionHandler
  protected def getExceptionHandler : Directive0 = exceptionHandler
  protected val sprayManager = context.actorSelection(HarnessConstants.ComponentFullName + "/" + SprayManager.ComponentName)

  // components can have categories that they fall into, if a component has a category only a single component
  // of that category can be available. Then a user can message that category, so it would then be possible
  // to not know at all what the underlying component you are using, as long has they handle the same messages.

  // default the marshaller to the lift json marshaller
  implicit def OutputMarshaller[T <: AnyRef] = liftJsonMarshaller[T]

  //override this value if you require a different response code
  def responseStatusCode = StatusCodes.OK

  /**
   * Function that allows you to override the headers for a response. The Map allows you to specifically
   * set headers for specific HTTPMethods, if None set for HttpMethod, then the header will apply to all
   * methods. This is done so that if you have multiple traits like SprayPut, SprayPost, SprayOptions, SprayGet, etc.
   * you can apply different headers depending on what you are doing.
   *
   * @return
   */
  def getResponseHeaders : Map[String, List[HttpHeader]] = {
    Map[String, List[HttpHeader]]()
  }

  /**
   * Function can be used to override any directives that you wish to use at the beginning of
   * the route
   *
   * @return
   */
  def preRoute : Directive0 = {
    pass
  }

  protected def innerExecute[T<:AnyRef:Manifest](bean:Option[CommandBean]=None) = {
    parameterMap {
      params =>
        val updatedBean = bean match {
          case Some(b) => b.appendMap(params); b
          case None => CommandBean(params)
        }
        onComplete[CommandResponse[T]](execute(Some(updatedBean)).mapTo[CommandResponse[T]]) {
          case Success(s) =>
            s.data match {
              case Some(data) =>
                val media = MediaTypes.forExtension(s.responseType) match {
                  case Some(m) => m
                  case None =>
                    val mt = Try({
                      val rt = s.responseType.split("/")
                      MediaTypes.getForKey((rt(0), rt(1))).get
                    }) recover {
                      case _ => MediaTypes.`application/json`
                    }
                    mt getOrElse MediaTypes.`application/json`
                }
                respondWithMediaType(media) {
                  complete { responseStatusCode -> data }
                }
              case None =>
                complete(StatusCodes.NoContent)
            }
          case Failure(f) => throw f
        }
    }
  }

  protected def buildRoute(httpMethod:Directive0) : Route = {
    getRejectionHandler {
      getExceptionHandler {
        preRoute {
          httpMethod {
            mapHeaders(getResponseHeaders) {
              commandPaths(paths) {
                bean => innerExecute(Some(bean))
              }
            }
          }
        }
      }
    }
  }

  case class Ok(remaining:Int)

  def sendStreamingResponse(ctx:RequestContext) : Unit = {
    context.actorOf {
      Props {
        new Actor with ActorLogging {
          def receive = {
            case Ok(0) =>

            case Ok(remaining) =>

            case ev:Tcp.ConnectionClosed =>
          }
        }
      }
    }
  }

  protected def addRoute(name:String, route:Route) = {
    RouteManager.addRoute(name, route)
    sprayManager ! HttpReloadRoutes
  }
}

/**
 * Trait for building your own custom spray route
 *
 * Use innerExecute to help with hooking up the command correctly
 */
trait SprayCustom extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_custom", customRoute)

  def customRoute : Route
}

/**
 * Trait for adding get routes to Command
 */
trait SprayGet extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_get", buildRoute(MethodDirectives.get))
}

/**
 * Based trait for any routes that grab the entity from the body of the request.
 * Currently Put and Post, due to the implicit manifest that is required for marshalling
 * any traits that use this base trait cannot be mixed in together.
 */
sealed protected trait EntityRoutes extends SprayRoutes {
  this : Command =>
  // default the unmarshaller to lift json unmarshaller
  implicit def InputUnmarshaller[T : Manifest] = liftJsonUnmarshaller[T]

  protected def entityRoute[T<:AnyRef:Manifest](httpMethod:Directive0) : Route = {
    getRejectionHandler {
      getExceptionHandler {
        preRoute {
          commandPaths(paths) {
            bean =>
              httpMethod {
                entity(as[T]) {
                  po =>
                    bean.appendMap(Map(CommandBean.KeyEntity -> po))
                    mapHeaders(getResponseHeaders) {
                      innerExecute(Some(bean))
                    }
                }
              }
          }
        }
      }
    }
  }
}

/**
 * Trait for adding post routes to Command entity extraction will default to JObject
 */
trait SprayPost extends EntityRoutes {
  this : Command =>
  protected def postRoute[T<:AnyRef:Manifest] = entityRoute[T](MethodDirectives.post)
  def setRoute : Route = postRoute[JObject]
  addRoute(commandName + "_post", setRoute)
}

/**
 * Trait for adding delete routes to Command
 */
trait SprayDelete extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_delete", buildRoute(MethodDirectives.delete))
}

/**
 * Trait for adding options routes to Command
 */
trait SprayOptions extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_options", optionsRoute)

  implicit def optionsMarshaller = liftJsonMarshaller[JValue]

  /**
   * Function will return all the allowed headers for the command. Basically if you mixin a trait like
   * SprayGet, it will add the Get method to the allow header. This method should be overridden if you
   * are using the SprayCustom trait as then you would be defining your own routes and methods.
   *
   * @return
   */
  private def getMethods = {
    def getMethod[I<:SprayRoutes](klass:Class[I], method:HttpMethod) : Option[HttpMethod] = {
      klass.isAssignableFrom(this.getClass) match {
        case true => Some(method)
        case false => None
      }
    }

    Seq[Option[HttpMethod]] (
      getMethod(classOf[SprayGet], HttpMethods.GET),
      getMethod(classOf[SprayHead], HttpMethods.HEAD),
      getMethod(classOf[SprayPatch], HttpMethods.PATCH),
      getMethod(classOf[SprayPut], HttpMethods.PUT),
      getMethod(classOf[SprayPost], HttpMethods.POST),
      getMethod(classOf[SprayOptions], HttpMethods.OPTIONS),
      getMethod(classOf[SprayDelete], HttpMethods.DELETE)
    ).flatten
  }

  /**
   * Override this function to give the options specific information about the command
   *
   * @return
   */
  def optionsResponse : JValue =  parse("""{}""")

  def optionsRoute = {
    respondJson {
      getRejectionHandler {
        getExceptionHandler {
          preRoute {
            commandPaths(paths) {
              bean =>
                options {
                  respondWithHeaders(HttpHeaders.Allow(getMethods: _*), HttpHeaders.`Access-Control-Allow-Methods`(getMethods)) {
                    mapHeaders(getResponseHeaders) {
                      ctx =>
                        ctx.complete(StatusCodes.OK -> optionsResponse)
                        ToResponseMarshaller.fromMarshaller[JValue](StatusCodes.OK)(optionsMarshaller)
                    }
                  }
                }
            }
          }
        }
      }
    }
  }
}

/**
 * Trait for adding head routes to Command
 */
trait SprayHead extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_head", buildRoute(MethodDirectives.head))
}

/**
 * Trait for adding patch routes to Command
 */
trait SprayPatch extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_patch", buildRoute(MethodDirectives.patch))
}

/**
 * Trait for adding put routes to Command
 */
trait SprayPut extends EntityRoutes {
  this : Command =>
  protected def putRoute[T<:AnyRef:Manifest] = entityRoute[T](MethodDirectives.put)
  def setRoute : Route = putRoute[JObject]
  addRoute(commandName + "_put", setRoute)
}


