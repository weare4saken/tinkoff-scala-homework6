package com.mypackage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object DelayedFutureOpsImpl extends DelayedFutureOps {

  def sequence[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[List[A]] = () => {
    def helper(remaining: List[DelayedFuture[A]], acc: List[A]): Future[List[A]] = {
      remaining match {
        case Nil => Future.successful(acc)
        case head :: tail =>
          head().flatMap { result =>
            helper(tail, acc :+ result)
          }
      }
    }
    helper(list, Nil)
  }

  def seqPar[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[(List[Throwable], List[A])] = () => {
    def helper(remaining: List[DelayedFuture[A]], errors: List[Throwable], successes: List[A]): Future[(List[Throwable], List[A])] = {
      remaining match {
        case Nil => Future.successful((errors, successes))
        case head :: tail =>
          head().transform(result => Success(Try(result))).flatMap {
            case Success(Success(value)) => helper(tail, errors, successes :+ value)
            case Success(Failure(ex)) => helper(tail, errors :+ ex, successes)
            case Failure(ex) => helper(tail, errors :+ ex, successes) // to handle unexpected failure in transform
          }
      }
    }
    helper(list, Nil, Nil)
  }

  def traverse[A, B](list: List[A])(f: A => DelayedFuture[B])(using ExecutionContext): DelayedFuture[List[B]] = () => {
    def helper(remaining: List[A], acc: List[B]): Future[List[B]] = {
      remaining match {
        case Nil => Future.successful(acc)
        case head :: tail =>
          f(head)().flatMap { result =>
            helper(tail, acc :+ result)
          }
      }
    }
    helper(list, Nil)
  }

  def batchTraverse[A](in: List[A], batchSize: Int, futures: DelayedFuture[A])(using ExecutionContext): DelayedFuture[List[A]] = () => {
    val batches = in.grouped(batchSize).toList

    def processBatch(acc: Future[List[A]], batch: List[A]): Future[List[A]] = {
      def helper(remaining: List[A], accBatch: List[A]): Future[List[A]] = {
        remaining match {
          case Nil => Future.successful(accBatch)
          case head :: tail =>
            futures().flatMap { result =>
              helper(tail, accBatch :+ result)
            }
        }
      }
      acc.flatMap(lst => helper(batch, Nil).map(lst ++ _))
    }

    batches.foldLeft(Future.successful(List.empty[A]))(processBatch)
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
        if (result == t) Future.failed(new RuntimeException("All transformations failed"))
        else Future.successful(result)
      }
    }
    applyTransformations(t, chain)
  }
}