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
package com.webtrends.harness.component.cache

import java.nio.charset.StandardCharsets

import akka.pattern.Patterns
import akka.actor.{ActorSelection, ActorRef}
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import net.liftweb.json._
import net.liftweb.json.ext.JodaTimeSerializers
import scala.concurrent._
import net.liftweb.json.Extraction._
import scala.util.Success
import scala.util.Failure
import akka.util.Timeout
import scala.concurrent.duration.Duration
import scala.pickling._
import binary._

/**
 * Trait to help with caching objects in the wookiee-cache
 * A cacheable object will be converted to JSON and then stored as Array[Byte] in the cache manager
 *
 * TODO: Need to implement timeout strategies. Currently it simply times out the object based on when it
 * was inserted. This can cause an object to constantly be updating the insert time and possibly never timeout.
 * Also need to implement a strategy where everything will time out every hour or something like that.
 */
trait Cacheable[T] extends Serializable {
  this : Serializable =>

  // This is a wrapper object that will basically wrap the object with an insertion time and any other meta data that it may require
  @SerialVersionUID(10L)
  protected case class CacheWrapper(data:Array[Byte], insertionTime:Long=0L) extends Serializable

  @transient implicit def liftJsonFormats = DefaultFormats.lossless + NoTypeHints ++ JodaTimeSerializers.all
  @transient implicit val timeout = Timeout(Duration(1, "seconds"))

  /**
   * Gets the timeout for the data in the cache, by default will be set to 0L which means it will never time out
   * The value is in milliseconds
   *
   * @return milliseconds for timeout of data in cache
   */
  def dataTimeout : Long = -1L

  /**
   * The key used to cache the object
   *
   * @return
   */
  def key : String = ???

  /**
   * The namespace used for the object
   *
   * @return
   */
  def namespace : String = ???

  /**
   * Extract function allows cacheable object to have control how it extracts the byte data from the array
   * by default it will try and parse it as a JSON object and then extract it to the class type
   *
   * @param obj the array of bytes
   * @return
   */
  protected def extract(obj:Array[Byte])(implicit m: Manifest[T]) : Option[T] = {
    Some(JsonParser.parse(new String(obj, StandardCharsets.UTF_8)).extract[T])
  }

  /**
   * getBytes function allows cacheable object to have control over how it writes the data to memcache
   * By default it will use Lift JSON to decompose then render the object from json to a string and
   * then simply call getBytes on the string
   *
   * @return
   */
  protected def getBytes : Array[Byte] = {
    compactRender(decompose(this)).getBytes(StandardCharsets.UTF_8)
  }

  /**
   * Checks to see if the object that was stored in the cache is past the timeout, if it
   * is then then false else true, if timeout is 0L then assumes no timeout and will always return true
   *
   * @param insertionTime the time the object was inserted into the cache
   * @return true if current time subtract the timeout for the object is less than or equal too the time the object
   *         was inserted into the cache
   */
  protected def checkTimeout(insertionTime:Long) : Boolean = {
    if (dataTimeout < 0 || compat.Platform.currentTime - dataTimeout <= insertionTime) {
      true
    } else {
      false
    }
  }

  def readFromCacheSelect(cacheRef:ActorSelection, cacheKey:Option[CacheKey]=None)
      (implicit timeout:Timeout, executor:ExecutionContext, m:Manifest[T]) : Future[Option[T]] = {
    val p = Promise[Option[T]]
    cacheRef.resolveOne onComplete {
      case Success(s) =>
        readFromCache(s, cacheKey)(timeout, executor, m) onComplete {
          case Success(result) => p success result
          case Failure(f) =>
            p success None
            throw f
        }
      case Failure(f) =>
        p success None
        throw f
    }
    p.future
  }

  /**
   * Looks in the supplied cache for the current object
   *
   * @param cacheRef This is a reference to the cache actor
   * @param timeout Timeout for the cache read
   * @return
   */
  def readFromCache(cacheRef:ActorRef, cacheKey:Option[CacheKey]=None)
      (implicit timeout:Timeout, executor:ExecutionContext, m:Manifest[T]) : Future[Option[T]] = {
    val ck = getCacheKey(cacheKey)
    val p = Promise[Option[T]]
    val future = Patterns.ask(cacheRef, Get(namespace, ck), timeout).mapTo[Option[ChannelBuffer]]
    future onComplete {
      case Success(s) =>
        unwrapData(s) match {
          case Some(s) => p success Some(s)
          case None =>
            cacheRef ! Delete(namespace, ck)
            p success None
        }
      case Failure(f) => throw f
    }
    p.future
  }

  def writeInCacheSelect(cacheRef:ActorSelection, cacheKey:Option[CacheKey]=None)
      (implicit timeout:Timeout, executor:ExecutionContext) : Future[Boolean] = {
    val p = Promise[Boolean]
    cacheRef.resolveOne onComplete {
      case Success(s) =>
        writeInCache(s, cacheKey)(timeout, executor) onComplete {
          case Success(s) => p success true
          case Failure(f) => p success false
        }
      case Failure(f) => throw f
    }
    p.future
  }

  /**
   * Writes the current object to the supplied cache
   *
   * @param cacheRef This is a reference to the cache actor
   * @param timeout Timeout for the cache read
   * @return
   */
  def writeInCache(cacheRef:ActorRef, cacheKey:Option[CacheKey]=None)
      (implicit timeout:Timeout, executor:ExecutionContext) : Future[Boolean] = {
    val p = Promise[Boolean]
    val future = Patterns.ask(cacheRef, Add(namespace, getCacheKey(cacheKey), wrapData), timeout).mapTo[Boolean]
    future onComplete {
      case Success(s) => p success true
      case Failure(f) =>
        p failure f
        throw f
    }
    p.future
  }

  /**
   * Deletes the current item from the cache
   *
   * @param cacheRef The is a reference to the cache actor
   * @param cacheKey Optional key, usually this is managed by the object itself
   * @param timeout timeout for the cache delete response
   * @param executor the executor
   * @return true if delete was successful
   */
  def deleteFromCache(cacheRef:ActorRef, cacheKey:Option[CacheKey]=None)
      (implicit timeout:Timeout, executor:ExecutionContext) : Future[Boolean] = {
    val p = Promise[Boolean]
    val future = Patterns.ask(cacheRef, Delete(namespace, getCacheKey(cacheKey)), timeout).mapTo[Boolean]
    future onComplete {
      case Success(s) => p success true
      case Failure(f) =>
        p failure f
        throw f
    }
    p.future
  }

  private def wrapData : ChannelBuffer = {
    val wrapper = CacheWrapper(this.getBytes, compat.Platform.currentTime)
    ChannelBuffers.wrappedBuffer(wrapper.pickle.value)
  }

  private def unwrapData(data:Option[ChannelBuffer])(implicit m:Manifest[T]) : Option[T] = {
    data match {
      case Some(buffer) =>
        val wrapper = buffer.array.unpickle[CacheWrapper]
        extract(wrapper.data) match {
          case Some(value) =>
            if (checkTimeout(wrapper.insertionTime)) {
              Some(value)
            } else {
              None
            }
          case None => None
        }
      case None => None
    }
  }

  protected def getCacheKey(cacheKey:Option[CacheKey]) : String = {
    cacheKey match {
      case Some(s) => s.toString
      case None => key
    }
  }
}
