package com.webtrends.harness.component.spray.websocket

import akka.actor.Props

/**
 * Created by wallinm on 4/17/15.
 */
trait WebSocketWorkerHelper {

  def initWebSocketWorkerHelper = {
    addWebSocketWorkers
  }

  /**
   * This function should be implemented by any service that wants to add
   * any websocket workers
   */
  def addWebSocketWorkers = {}

  /**
   * Wrapper that allows services to add a websocket worker with props to the websocket manager
   *
   * @param path name of the command you want to add
   * @param props the props for that command actor class
   * @return
   */
  def addWebSocketWorkerWithProps(path:String, props:Props) = {
    WebSocketManager.addWorker(path, props)
  }

  /**
   * Wrapper that allows services to add a websocket worker to the websocket manager
   *
   * @param path name of the command you want to add
   * @param actorClass the class for the actor
   */
  def addWebSocketWorker[T](path:String, actorClass:Class[T]) = {
    WebSocketManager.addWorker[T](path, actorClass)
  }
}
