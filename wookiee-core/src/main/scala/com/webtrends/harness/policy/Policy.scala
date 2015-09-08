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

package com.webtrends.harness.policy

import akka.util.Timeout
import com.webtrends.harness.command.CommandResponse
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
 * A policy should be a api for underlying business logic located in a command. It should not ever deal with the
 * underlying messaging protocol but rather focus passing data from an API exclusively to the business logic. It should also not
 * deal with any storage or caching or anything like that.
 *
 * @author Peter Crossley 8/19/2015
 */
trait Policy extends AnyRef with PolicyHelper {
  implicit val timeout = Timeout(2 seconds)

  /**
   * Name of the policy that will be used for the actor name
   *
   * @return
   */
  def policyName : String = getClass.getSimpleName


  def decomposeCommandResponse[T<:AnyRef:Manifest](bean:Future[CommandResponse[T]]) : Future[T] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val f = Promise[T]()
    bean.mapTo[CommandResponse[T]] onComplete {
      case Success(resp) =>
        f.success(resp.data.get)
      case Failure(f) => PolicyException("Error in decomposeCommandResponse", f)
    }
    f.future
  }
}