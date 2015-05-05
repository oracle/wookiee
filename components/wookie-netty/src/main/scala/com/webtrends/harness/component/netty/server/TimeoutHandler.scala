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

package com.webtrends.harness.component.netty.server

import io.netty.channel.{ChannelHandlerContext, ChannelDuplexHandler}
import io.netty.handler.timeout.{IdleStateEvent, IdleState}

/**
 * Created by wallinm on 1/7/15.
 */
class TimeoutHandler extends ChannelDuplexHandler {
  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef) {
    evt match {
      case e: IdleStateEvent =>
        e.state match {
          case IdleState.READER_IDLE => ctx.close
          case _ =>
        }
    }
  }
}
