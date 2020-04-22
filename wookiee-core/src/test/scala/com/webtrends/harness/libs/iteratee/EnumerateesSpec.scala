package com.webtrends.harness.libs.iteratee

import com.webtrends.harness.libs.iteratee.Execution.Implicits.{defaultExecutionContext => dec}
import org.scalatest.WordSpecLike

import scala.concurrent._
import scala.concurrent.duration.Duration

class EnumerateesSpec extends WordSpecLike
    with IterateeSpecification with ExecutionSpecification {

  "Enumeratee.mapInput" should {

    "transform each input" in {
      mustExecute(2) { mapEC =>
        mustTransformTo(1, 2)(2, 4)(Enumeratee.mapInput[Int](_.map(_ * 2))(mapEC))
      }
    }

  }

  "Enumeratee.mapConcatInput" should {

    "transform each input element into a sequence of inputs" in {
      mustExecute(2) { mapEC =>
        mustTransformTo(1, 2)(1, 1, 2, 2)(Enumeratee.mapConcatInput[Int](x => List(Input.El(x), Input.Empty, Input.El(x)))(mapEC))
      }
    }

  }

  "Enumeratee.mapConcat" should {

    "transform each input element into a sequence of input elements" in {
      mustExecute(2) { mapEC =>
        mustTransformTo(1, 2)(1, 1, 2, 2)(Enumeratee.mapConcat[Int](x => List(x, x))(mapEC))
      }
    }

  }

  "Enumeratee.mapFlatten" should {

    "transform each input element into the output of an enumerator" in {
      mustExecute(2) { mapFlattenEC =>
        mustTransformTo(1, 2)(1, 1, 2, 2)(Enumeratee.mapFlatten[Int](x => Enumerator(x, x))(mapFlattenEC))
      }
    }

  }

  "Enumeratee.mapInputFlatten" should {

    "transform each input" in {
      mustExecute(2) { mapEC =>
        val eee: Enumeratee.CheckDone[Int, Int] = Enumeratee.mapInputFlatten[Int][Int] {
          case Input.El(x) => Enumerator(x * 2)
          case Input.Empty => Enumerator.empty
          case Input.EOF => Enumerator.empty
        }(mapEC)
        mustTransformTo(1, 2)(2, 4)(eee compose Enumeratee.take(2))
      }
    }

  }

  "Enumeratee.mapInputM" should {

    "transform each input" in {
      mustExecute(2) { mapEC =>
        mustTransformTo(1, 2)(2, 4)(Enumeratee.mapInputM[Int]((i: Input[Int]) => Future.successful(i.map(_ * 2)))(mapEC))
      }
    }

  }

  "Enumeratee.mapM" should {

    "transform each input element" in {
      mustExecute(2) { mapEC =>
        mustTransformTo(1, 2)(2, 4)(Enumeratee.mapM[Int]((x: Int) => Future.successful(x * 2))(mapEC))
      }
    }

  }

  "Enumeratee.take" should {

    "not execute callback on take 0" in {
      mustExecute(0, 0) { (generateEC, foldEC) =>
        var triggered = false
        val enumerator = Enumerator.generateM {
          triggered = true
          Future(Some(1))(dec)
        }(generateEC)
        Await.result(enumerator &> Enumeratee.take(0) |>>> Iteratee.fold(0)((_: Int) + (_: Int))(foldEC), Duration.Inf) mustBe 0
        triggered mustBe false
      }
    }

  }

  "Enumeratee.takeWhile" should {

    "pass input through while the predicate is met" in {
      mustExecute(3) { breakEC =>
        mustTransformTo(1, 2, 3, 2, 1)(1, 2)(Enumeratee.takeWhile[Int](_ <= 2)(breakEC))
      }
    }

  }

  "Enumeratee.breakE" should {

    "pass input through until the predicate is met" in {
      mustExecute(3) { breakEC =>
        mustTransformTo(1, 2, 3, 2, 1)(1, 2)(Enumeratee.breakE[Int](_ > 2)(breakEC))
      }
    }

  }

  "Enumeratee.onIterateeDone" should {

    "call the callback when the iteratee is done" in {
      mustExecute(1) { doneEC =>
        val count = new java.util.concurrent.atomic.AtomicInteger()
        mustTransformTo(1, 2, 3)(1, 2, 3)(Enumeratee.onIterateeDone(() => count.incrementAndGet())(doneEC))
        count.get() mustBe 1
      }
    }

  }

  "Enumeratee.onEOF" should {

    "call the callback on EOF" in {
      mustExecute(1) { eofEC =>
        val count = new java.util.concurrent.atomic.AtomicInteger()
        mustTransformTo(1, 2, 3)(1, 2, 3)(Enumeratee.onEOF(() => count.incrementAndGet())(eofEC))
        count.get() mustBe 1
      }
    }

  }

  "Enumeratee.map" should {

    "infer its types correctly from the preceeding enumerator" in {
      mustExecute(0) { mapEC =>
        val addOne = Enumerator(1, 2, 3, 4) &> Enumeratee.map[Int](i => i + 1)(mapEC)
        addOne
        true //this test is about compilation and if it compiles it means we got it right
      }
    }

  }

  "Enumeratee.flatten" should {

    "passAlong a future enumerator" in {
      mustExecute(9) { sumEC =>
        val passAlongFuture = Enumeratee.flatten {
          Future {
            Enumeratee.passAlong[Int]
          }(ExecutionContext.global)
        }
        val sum = Iteratee.fold[Int, Int](0)(_ + _)(sumEC)
        val enumerator = Enumerator(1, 2, 3, 4, 5, 6, 7, 8, 9)
        Await.result(enumerator |>>> passAlongFuture &>> sum, Duration.Inf) mustBe 45
      }
    }

  }

  "Enumeratee.filter" should {

    "only enumerate input that satisfies the predicate" in {
      mustExecute(6) { filterEC =>
        mustTransformTo("One", "Two", "Three", "Four", "Five", "Six")("One", "Two", "Six")(Enumeratee.filter[String](_.length < 4)(filterEC))
      }
    }

  }

  "Enumeratee.filterNot" should {

    "only enumerate input that doesn't satisfy the predicate" in {
      mustExecute(6) { filterEC =>
        mustTransformTo("One", "Two", "Three", "Four", "Five", "Six")("Three", "Four", "Five")(Enumeratee.filterNot[String](_.length < 4)(filterEC))
      }
    }

  }

  "Enumeratee.collect" should {

    "ignores input that doesn't satisfy the predicate and transform the input when matches" in {
      mustExecute(6) { collectEC =>
        mustTransformTo("One", "Two", "Three", "Four", "Five", "Six")("ONE", "TWO", "SIX")(Enumeratee.collect[String] { case e @ ("One" | "Two" | "Six") => e.toUpperCase(java.util.Locale.ENGLISH) }(collectEC))
      }
    }

  }

  "Enumeratee.recover" should {

    "perform computations and log errors" in {
      mustExecute(3, 3) { (recoverEC, mapEC) =>
        val eventuallyInput = Promise[Input[Int]]()
        val result = Enumerator(0, 2, 4) &> Enumeratee.recover[Int] { (_, input) =>
          eventuallyInput.success(input)
        }(recoverEC) &> Enumeratee.map[Int] { i =>
          8 / i
        }(mapEC) |>>> Iteratee.getChunks // => List(4, 2)

        Await.result(result, Duration.Inf) mustBe List(4, 2)
        Await.result(eventuallyInput.future, Duration.Inf) mustBe Input.El(0)
      }
    }
  }

}
