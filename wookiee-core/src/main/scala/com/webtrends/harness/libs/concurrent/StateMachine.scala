/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.webtrends.harness.libs.concurrent

/**
 * A state machine with a non-blocking mutex protecting its state.
 */
private[harness] class StateMachine[S](initialState: S) {

  /**
   * The current state. Modifications to the state should be performed
   * inside the body of a call to `exclusive`. To read the state, it is
   * usually OK to read this field directly, even though its not volatile
   * or atomic, so long as you're happy about happens-before relationships.
   */
  var state: S = initialState

  val mutex = new NonBlockingMutex()

  /**
   * Exclusive access to the state. The state is read and passed to
   * f. Inside f it is safe to modify the state, if desired.
   */
  def exclusive(f: S => Unit) = mutex.exclusive { f(state) }

}
