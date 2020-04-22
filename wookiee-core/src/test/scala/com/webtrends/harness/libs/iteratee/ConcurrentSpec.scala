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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.TimeUnit._
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.WordSpecLike

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class ConcurrentSpec extends ExecutionSpecification with IterateeSpecification with WordSpecLike {
  "Concurrent.broadcast (0-arg)" should {
    "broadcast the same to already registered iteratees" in {
      mustExecute(38) { foldEC =>
        val (broadcaster, pushHere) = Concurrent.broadcast[String]
        val results = Future.sequence(Range(1, 20).map(_ => Iteratee.fold[String, String]("") { (s, e) => s + e }(foldEC)).map(broadcaster.apply).map(_.flatMap(_.run)))
        pushHere.push("beep")
        pushHere.push("beep")
        pushHere.eofAndEnd()
        Await.result(results, Duration.Inf) mustBe Range(1, 20).map(_ => "beepbeep")
      }
    }

    "allow invoking end twice" in {
      val (broadcaster, pushHere) = Concurrent.broadcast[String]
      val result = broadcaster |>>> Iteratee.getChunks[String]
      pushHere.push("beep")
      pushHere.end()
      pushHere.end()
      Await.result(result, Duration.Inf) mustBe Seq("beep")
    }
    
    "return the iteratee if already ended before the iteratee is attached" in {
      val (broadcaster, pushHere) = Concurrent.broadcast[String]
      pushHere.end()
      val result = broadcaster |>>> Iteratee.getChunks[String]
      Await.result(result, Duration.Inf) mustBe Nil
    }
    
    "allow ending with an exception" in {
      val (broadcaster, pushHere) = Concurrent.broadcast[String]
      val result = broadcaster |>>> Iteratee.getChunks[String]
      pushHere.end(new RuntimeException("foo"))
      an [RuntimeException] should be thrownBy Await.result(result, Duration.Inf)
    }
    
    "update the end result after end is already called" in {
      val (broadcaster, pushHere) = Concurrent.broadcast[String]
      val result1 = broadcaster |>>> Iteratee.getChunks[String]
      pushHere.end(new RuntimeException("foo"))
      an [RuntimeException] should be thrownBy Await.result(result1, Duration.Inf)
      pushHere.end()
      val result2 = broadcaster |>>> Iteratee.getChunks[String]
      Await.result(result2, Duration.Inf) mustBe Nil
    }
  }

  "Concurrent.buffer" should {

    def now = System.currentTimeMillis()

    "not slow down the enumerator if the iteratee is slow" in {
      val enumeratorFinished = new CountDownLatch(1)
      mustExecute(10) { bufferEC =>
        // This enumerator emits elements as fast as it can and
        // signals that it has finished using a latch
        val fastEnumerator = Enumerator((1 to 10): _*).onDoneEnumerating {
          enumeratorFinished.countDown()
        }
        val slowIteratee = Iteratee.foldM(List[Int]()) { (s, e: Int) =>
          Future {
            enumeratorFinished.await(waitTime.toMillis, TimeUnit.MILLISECONDS)
            s :+ e
          }
        }
        // Concurrent.buffer should buffer elements so that the the
        // fastEnumerator can complete even though the slowIteratee
        // won't consume anything until it has finished.
        val result =
          fastEnumerator &>
            Concurrent.buffer(20, (_: Input[Int]) => 1)(bufferEC) |>>>
            slowIteratee

        await(result) mustBe (1 to 10).toList
      }
    }

    "throw an exception when buffer is full" in {
      testExecution { foldEC =>
        val foldCount = new AtomicInteger()
        val p = Promise[List[Long]]()
        val stuckIteratee = Iteratee.foldM(List[Long]()) { (s, e: Long) => foldCount.incrementAndGet(); p.future }(foldEC)
        val fastEnumerator = Enumerator[Long](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result =
          fastEnumerator &>
            Concurrent.buffer(7) |>>>
            stuckIteratee

        an [Exception] must be thrownBy Await.result(result, Duration.Inf)
        foldEC.executionCount mustBe foldCount.get()
      }
    }

    "drop intermediate unused input, swallow even the unused eof forcing u to pass it twice" in {
      testExecution { (flatMapEC, mapEC) =>
        val p = Promise[List[Long]]()
        val slowIteratee = Iteratee.flatten(timeout(Cont[Long, List[Long]] {
          case Input.El(e) => Done(List(e), Input.Empty)
          case in => throw new MatchError(in) // Shouldn't occur, but here to suppress compiler warning
        }, Duration(100, MILLISECONDS)))
        val fastEnumerator = Enumerator[Long](1, 2, 3, 4, 5, 6, 7, 8, 9, 10) >>> Enumerator.eof
        val preparedMapEC = mapEC.prepare()
        val result =
          fastEnumerator |>>>
            (Concurrent.buffer(20) &>>
              slowIteratee).flatMap { l => Iteratee.getChunks.map(l ++ (_: List[Long]))(preparedMapEC) }(flatMapEC)

        Await.result(result, Duration.Inf) must not be List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        flatMapEC.executionCount must not be 0
        mapEC.executionCount mustBe flatMapEC.executionCount
      }
    }

  }

  "Concurrent.lazyAndErrIfNotReady" should {

    "return an error if the iteratee is taking too long" in {
      // Create an iteratee that never finishes. This means that our
      // Concurrent.lazyAndErrIfNotReady timeout will always fire.
      // Once we've got our timeout we release the iteratee so that
      // it can finish normally.
      val gotResult = new CountDownLatch(1)
      val slowIteratee = Iteratee.foldM(List[Int]()) { (s, e: Int) =>
        Future {
          gotResult.await(waitTime.toMillis, TimeUnit.MILLISECONDS)
          s :+ e
        }
      }

      val fastEnumerator = Enumerator((1 to 10): _*) >>> Enumerator.eof
      val result = Try(await(fastEnumerator &> Concurrent.lazyAndErrIfNotReady(50) |>>> slowIteratee))
      // We've got our result (hopefully a timeout), so let the iteratee
      // complete.
      gotResult.countDown()
      an [Exception] must be thrownBy result.get
    }

  }

  "Concurrent.unicast" should {
    "allow to push messages and end" in {
      mustExecute(2, 2) { (unicastEC, foldEC) =>
        val a = "FOO"
        val b = "bar"
        val startCount = new AtomicInteger()
        val completeCount = new AtomicInteger()
        val errorCount = new AtomicInteger()
        val enumerator: Enumerator[String] = Concurrent.unicast[String](
          c => {
            startCount.incrementAndGet()
            c.push(a)
            c.push(b)
            c.eofAndEnd()
          },
          () => completeCount.incrementAndGet(),
          (_: String, _: Input[String]) => errorCount.incrementAndGet())(unicastEC)
        val promise = (enumerator |>> Iteratee.fold[String, String]("")(_ ++ _)(foldEC)).flatMap(_.run)

        Await.result(promise, Duration.Inf) mustBe a + b
        startCount.get() mustBe 1
        completeCount.get() mustBe 0
        errorCount.get() mustBe 0
      }
    }

    "call the onComplete callback when the iteratee is done" in {
      mustExecute(2) { unicastEC =>
        val completed = Promise[String]

        val enumerator: Enumerator[String] = Concurrent.unicast[String](onStart = { c =>
          c.push("foo")
          c.push("bar")
        }, onComplete = {
          completed.success("called")
        })(unicastEC)

        val future = enumerator |>>> Cont {
          case Input.El(data) => Done(data)
          case _ => Done("didn't get data")
        }

        Await.result(future, Duration.Inf) mustBe "foo"
        Await.result(completed.future, Duration.Inf) mustBe "called"
      }
    }

    "call the onError callback when the iteratee encounters an error" in {
      mustExecute(2) { unicastEC =>
        val error = Promise[String]

        val enumerator: Enumerator[String] = Concurrent.unicast[String](onStart = { c =>
          c.push("foo")
          c.push("bar")
        }, onError = { (err, input) =>
          error.success(err)
        })(unicastEC)

        enumerator |>> Cont {
          case Input.El(data) => Error(data, Input.Empty)
          case in => Error("didn't get data", in)
        }

        Await.result(error.future, Duration.Inf) mustBe "foo"
      }
    }

    "allow invoking end twice" in {
      mustExecute(1) { unicastEC =>
        val endInvokedTwice = new CountDownLatch(1)
        val enumerator: Enumerator[String] = Concurrent.unicast[String](onStart = { c =>
          c.end()
          c.end()
          endInvokedTwice.countDown()
        })(unicastEC)

        Await.result(enumerator |>>> Iteratee.getChunks[String], Duration.Inf) mustBe Nil
        endInvokedTwice.await(10, TimeUnit.SECONDS) mustBe true
      }
    }

    "allow ending with an exception" in {
      mustExecute(1) { unicastEC =>
        val enumerator: Enumerator[String] = Concurrent.unicast[String](onStart = { c =>
          c.end(new RuntimeException("foo"))
        })(unicastEC)

        val result = enumerator |>>> Iteratee.getChunks[String]
        an [RuntimeException] must be thrownBy Await.result(result, Duration.Inf)
      }
    }

    "only use the invocation of the first end" in {
      mustExecute(1) { unicastEC =>
        val endInvokedTwice = new CountDownLatch(1)
        val enumerator: Enumerator[String] = Concurrent.unicast[String](onStart = { c =>
          c.end()
          c.end(new RuntimeException("foo"))
          endInvokedTwice.countDown()
        })(unicastEC)

        val result = enumerator |>>> Iteratee.getChunks[String]
        endInvokedTwice.await(10, TimeUnit.SECONDS) mustBe true
        Await.result(result, Duration.Inf) mustBe Nil
      }
    }

  }

  "Concurrent.broadcast (2-arg)" should {
    "call callback in the correct ExecutionContext" in {
      mustExecute(1) { callbackEC =>
        val (e0, c) = Concurrent.broadcast[Int]
        val interestCount = new AtomicInteger()
        val interestDone = new CountDownLatch(1)
        val (e2, _) = Concurrent.broadcast(e0, { f =>
          interestCount.incrementAndGet()
          interestDone.countDown()
        })(callbackEC)
        val i = e2 |>>> Iteratee.getChunks[Int]
        c.push(1)
        c.push(2)
        c.push(3)
        c.eofAndEnd()
        Await.result(i, Duration.Inf) mustBe List(1, 2, 3)
        interestDone.await(30, SECONDS) mustBe true
        interestCount.get() mustBe 1
      }
    }
  }

  "Concurrent.patchPanel" should {

    "perform patching in the correct ExecutionContext" in {
      mustExecute(1) { ppEC =>
        val e = Concurrent.patchPanel[Int] { pp =>
          pp.patchIn(Enumerator.eof)
        }(ppEC)
        Await.result(e |>>> Iteratee.getChunks[Int], Duration.Inf) mustBe Nil
      }
    }
  }

  "Concurrent.joined" should {
    "join the iteratee and enumerator if the enumerator is applied first" in {
      val (iteratee, enumerator) = Concurrent.joined[String]
      val result = enumerator |>>> Iteratee.getChunks[String]
      val unitResult = Enumerator("foo", "bar") |>>> iteratee
      await(result) mustBe Seq("foo", "bar")
      await(unitResult) mustBe ()
    }
    "join the iteratee and enumerator if the iteratee is applied first" in {
      val (iteratee, enumerator) = Concurrent.joined[String]
      val unitResult = Enumerator("foo", "bar") |>>> iteratee
      val result = enumerator |>>> Iteratee.getChunks[String]
      await(result) mustBe Seq("foo", "bar")
      await(unitResult) mustBe ()
    }
    "join the iteratee and enumerator if the enumerator is applied during the iteratees run" in {
      val (iteratee, enumerator) = Concurrent.joined[String]
      val (broadcast, channel) = Concurrent.broadcast[String]
      val unitResult = broadcast |>>> iteratee
      channel.push("foo")
      Thread.sleep(10)
      val result = enumerator |>>> Iteratee.getChunks[String]
      channel.push("bar")
      channel.end()
      await(result) mustBe Seq("foo", "bar")
      await(unitResult) mustBe ()
    }
    "break early from infinite enumerators" in {
      val (iteratee, enumerator) = Concurrent.joined[String]
      val infinite = Enumerator.repeat("foo")
      val unitResult = infinite |>>> iteratee
      val head = enumerator |>>> Iteratee.head
      await(head) mustBe Some("foo")
      await(unitResult) mustBe ()
    }
  }

  "Concurrent.runPartial" should {
    "redeem the iteratee with the result and the partial enumerator" in {
      val (a, remaining) = await(Concurrent.runPartial(Enumerator("foo", "bar"), Iteratee.head[String]))
      a mustBe Some("foo")
      await(remaining |>>> Iteratee.getChunks[String]) mustBe Seq("bar")
    }
    "work when there is no input left in the enumerator" in {
      val (a, remaining) = await(Concurrent.runPartial(Enumerator("foo", "bar"), Iteratee.getChunks[String]))
      a mustBe Seq("foo", "bar")
      await(remaining |>>> Iteratee.getChunks[String]) mustBe Nil
    }
  }
}
