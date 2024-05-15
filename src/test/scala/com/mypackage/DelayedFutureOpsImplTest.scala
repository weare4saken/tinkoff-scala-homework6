package com.mypackage

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class DelayedFutureOpsImplTest extends AnyFunSuite with Matchers {

  import DelayedFutureOpsImpl._

  test("sequence should return a DelayedFuture with the correct results") {
    val df1: DelayedFuture[Int] = () => Future.successful(1)
    val df2: DelayedFuture[Int] = () => Future.successful(2)
    val df3: DelayedFuture[Int] = () => Future.successful(3)

    val result = sequence(List(df1, df2, df3))()
    result.map(res => res shouldEqual List(1, 2, 3))
  }

  test("seqPar should return a DelayedFuture with both successes and failures") {
    val df1: DelayedFuture[Int] = () => Future.successful(1)
    val df2: DelayedFuture[Int] = () => Future.failed(new RuntimeException("Failed1"))
    val df3: DelayedFuture[Int] = () => Future.successful(3)
    val df4: DelayedFuture[Int] = () => Future.failed(new RuntimeException("Failed2"))
    val df5: DelayedFuture[Int] = () => Future.successful(5)

    val result = seqPar(List(df1, df2, df3, df4, df5))()
    result.map {
      case (errors, successes) =>
        errors should have size 2
        errors.map(_.getMessage) shouldEqual List("Failed1", "Failed2")
        successes shouldEqual List(1, 3, 5)
    }
  }

  test("traverse should apply the function to each element and return the results") {
    val list = List(1, 2, 3)
    val f: Int => DelayedFuture[String] = (i: Int) => () => Future.successful(i.toString)

    val result = traverse(list)(f)()
    result.map(res => res shouldEqual List("1", "2", "3"))
  }

  test("batchTraverse should process elements in batches") {
    val list = List(1, 2, 3, 4, 5)
    val future: DelayedFuture[Int] = () => Future.successful(1)

    val result = batchTraverse(list, 2, future)()
    result.map(res => res shouldEqual List.fill(5)(1))
  }

  test("transformationChain should apply transformations in sequence and use all successful ones") {
    val chain: Seq[Transformation[Int]] = Seq(
      (i: Int) => () => Future.successful(i + 1),
      (i: Int) => () => Future.failed(new RuntimeException("Fail2")),
      (i: Int) => () => Future.successful(i + 2)
    )

    val transform = transformationChain(chain)
    val result = transform(5)()

    result.map(res => res shouldEqual 8)
  }

  test("transformationChain should apply all transformations in sequence if necessary") {
    val chain: Seq[Transformation[Int]] = Seq(
      (i: Int) => () => Future.successful(i + 1),
      (i: Int) => () => Future.failed(new RuntimeException("Error")),
      (i: Int) => () => Future.successful(i * 2)
    )

    val transform = transformationChain(chain)
    val result = transform(5)()

    result.map(res => res shouldEqual 12)
  }

  test("transformationChain should fail if all transformations fail") {
    val chain: Seq[Transformation[Int]] = Seq(
      (i: Int) => () => Future.failed(new RuntimeException("Fail1")),
      (i: Int) => () => Future.failed(new RuntimeException("Fail2"))
    )

    val transform = transformationChain(chain)
    val result = transform(5)()

    result.transformWith {
      case Success(_) => Future.failed(new Exception("Expected failure, but got success"))
      case Failure(_) => Future.successful(succeed)
    }
  }
}
