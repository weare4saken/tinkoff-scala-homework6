package com.mypackage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object DelayedFutureOpsImpl extends DelayedFutureOps {

  def sequence[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[List[A]] = () => {
    val futures = list.map(df => df())
    Future.sequence(futures)
  }

  def seqPar[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[(List[Throwable], List[A])] = () => {
    val futures = list.map(df => df().transform {
      case Success(value) => Success(Right(value))
      case Failure(ex) => Success(Left(ex))
    })

    Future.sequence(futures).map { results =>
      val (errors, successes) = results.partitionMap(identity)
      (errors, successes)
    }
  }

  def traverse[A, B](list: List[A])(f: A => DelayedFuture[B])(using ExecutionContext): DelayedFuture[List[B]] = () => {
    val futures = list.map(a => f(a)())
    Future.sequence(futures)
  }

  def batchTraverse[A](in: List[A], batchSize: Int, futures: DelayedFuture[A])(using ExecutionContext): DelayedFuture[List[A]] = () => {
    val batches = in.grouped(batchSize).toList

    def processBatch(acc: Future[List[A]], batch: List[A]): Future[List[A]] = {
      val batchFutures = batch.map(_ => futures())
      val batchResult = Future.sequence(batchFutures)
      acc.flatMap(lst => batchResult.map(lst ++ _))
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
