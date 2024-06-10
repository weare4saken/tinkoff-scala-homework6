package com.mypackage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.collection.mutable.ListBuffer

object DelayedFutureOpsImpl extends DelayedFutureOps {

  def sequence[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[List[A]] = () => {
    val buffer = ListBuffer.empty[A]
    val futureResult = list.foldRight(Future.successful(buffer)) { (delayed, accFuture) =>
      for {
        acc <- accFuture
        result <- delayed()
      } yield {
        acc.prepend(result)
        acc
      }
    }
    futureResult.map(_.toList)
  }

  def seqPar[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[(List[Throwable], List[A])] = () => {
    val errors = ListBuffer.empty[Throwable]
    val successes = ListBuffer.empty[A]

    def helper(remaining: List[DelayedFuture[A]]): Future[(List[Throwable], List[A])] = {
      remaining match {
        case Nil => Future.successful((errors.toList, successes.toList))
        case head :: tail =>
          head().transform(result => Success(Try(result))).flatMap {
            case Success(Success(value)) =>
              successes.append(value)
              helper(tail)
            case Success(Failure(ex)) =>
              errors.append(ex)
              helper(tail)
            case Failure(ex) =>
              errors.append(ex)
              helper(tail)
          }
      }
    }
    helper(list)
  }

  def traverse[A, B](list: List[A])(f: A => DelayedFuture[B])(using ExecutionContext): DelayedFuture[List[B]] = () => {
    val buffer = ListBuffer.empty[B]
    val futureResult = list.foldRight(Future.successful(buffer)) { (a, accFuture) =>
      for {
        acc <- accFuture
        result <- f(a)()
      } yield {
        acc.prepend(result)
        acc
      }
    }
    futureResult.map(_.toList)
  }

  def batchTraverse[A](in: List[A], batchSize: Int, futures: DelayedFuture[A])(using ExecutionContext): DelayedFuture[List[A]] = () => {
    val batches = in.grouped(batchSize).toList

    def processBatch(acc: Future[ListBuffer[A]], batch: List[A]): Future[ListBuffer[A]] = {
      acc.flatMap { lst =>
        traverse(batch)(_ => futures).apply().map { results =>
          lst.appendAll(results)
          lst
        }
      }
    }

    val initial = Future.successful(ListBuffer.empty[A])
    batches.foldLeft(initial)(processBatch).map(_.toList)
  }

  def transformationChain[T](chain: Seq[Transformation[T]])(using ExecutionContext): Transformation[T] = (t: T) => () => {
    def applyTransformations(t: T, transformations: Seq[Transformation[T]]): Future[T] = {
      transformations.foldLeft(Future.successful(t)) { (accFuture, transformation) =>
        accFuture.flatMap { acc =>
          transformation(acc)().recover {
            case _ => acc
          }
        }
      }.flatMap { result =>
        if (result == t) Future.failed(new RuntimeException("Все трансформации не удались"))
        else Future.successful(result)
      }
    }

    applyTransformations(t, chain)
  }
}
