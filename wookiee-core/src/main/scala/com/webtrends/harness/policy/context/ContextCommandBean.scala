package com.webtrends.harness.policy.context

import com.webtrends.harness.command.CommandBean

import scala.collection.mutable

/**
 * Created by crossleyp on 8/24/15.
 */
@SerialVersionUID(100L)
class ContextCommandBean extends CommandBean {

  private val context:mutable.Map[String, AnyRef] = mutable.Map[String, AnyRef]()

  def getContext:Map[String, AnyRef] = context.toMap

  def setContext(key:String, value:AnyRef): Unit = {
    context += (key -> value)
  }

}
