/*
 *  Copyright (c) 2016 Webtrends (http://www.webtrends.com)
 *  See the LICENCE.txt file distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.webtrends.harness.libs.iteratee

import Parsing._

import org.specs2.mutable._
import concurrent.duration.Duration
import concurrent.Await

object ParsingSpec extends Specification
    with IterateeSpecification with ExecutionSpecification {

  "Parsing" should {

    "split case 1" in {
      mustExecute(10) { foldEC =>
        val data = Enumerator(List("xx", "kxckikixckikio", "cockik", "isdodskikisd", "ksdloii").map(_.getBytes): _*)
        val parsed = data |>>> Parsing.search("kiki".getBytes).transform(Iteratee.fold(List.empty[MatchInfo[Array[Byte]]]) { (s, c: MatchInfo[Array[Byte]]) => s :+ c }(foldEC))

        val result = Await.result(parsed, Duration.Inf).map {
          case Matched(kiki) => "Matched(" + new String(kiki) + ")"
          case Unmatched(data) => "Unmatched(" + new String(data) + ")"
        }.mkString(", ")

        result must equalTo(
          "Unmatched(xxkxc), Matched(kiki), Unmatched(xc), Matched(kiki), Unmatched(ococ), Matched(kiki), Unmatched(sdods), Matched(kiki), Unmatched(sdks), Unmatched(dloii)")
      }
    }

    "split case 1" in {
      mustExecute(11) { foldEC =>
        val data = Enumerator(List("xx", "kxckikixcki", "k", "kicockik", "isdkikodskikisd", "ksdlokiikik", "i").map(_.getBytes): _*)
        val parsed = data |>>> Parsing.search("kiki".getBytes).transform(Iteratee.fold(List.empty[MatchInfo[Array[Byte]]]) { (s, c: MatchInfo[Array[Byte]]) => s :+ c }(foldEC))

        val result = Await.result(parsed, Duration.Inf).map {
          case Matched(kiki) => "Matched(" + new String(kiki) + ")"
          case Unmatched(data) => "Unmatched(" + new String(data) + ")"
        }.mkString(", ")

        result must equalTo(
          "Unmatched(xxkxc), Matched(kiki), Unmatched(xckikkico), Unmatched(c), Matched(kiki), Unmatched(sdkikods), Matched(kiki), Unmatched(sdksdlok), Unmatched(ii), Matched(kiki), Unmatched()")
      }
    }

  }
}
