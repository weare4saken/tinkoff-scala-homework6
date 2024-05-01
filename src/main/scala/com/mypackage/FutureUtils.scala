package com.mypackage

import scala.concurrent.{ExecutionContext, Future}

/**
 * Специальный тип для отложенного вычисления Future.
 * Такой тип позволяет создать Future, который будет выполнен только тогда, когда будет вызвана функция apply.
 */
type DelayedFuture[A] = () => Future[A]
type Transformation[T] = T => DelayedFuture[T]

trait DelayedFutureOps {
  /**
   * Написать функцию sequence которая будет принимать список `DelayedFuture` и возвращать `DelayedFuture` в котором будут результаты выполнения всех `Future` из списка.
   * Результат должен быть в том же порядке что и входной список.
   * Если какой-то `Future` завершился с ошибкой, то в качестве результата возвращаем Future с этой ошибкой.
   */
  def sequence[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[List[A]]

  /**
   * Написать функцию seqPar которая будет принимать список `DelayedFuture` и возвращать `DelayedFuture` в котором будут результаты выполнения всех `DelayedFuture` из списка.
   * Результат должен быть в том же порядке что и входной список.
   * Если какой-то `Future` завершился с ошибкой, то она должна быть включена в список ошибок.
   */
  def seqPar[A](list: List[DelayedFuture[A]])(using ExecutionContext): DelayedFuture[(List[Throwable], List[A])]

  /**
   * Написать функцию traverse которая будет принимать список и функцию, которая из элемента списка будет строить `DelayedFuture`.
   * Функция должна вернуть `DelayedFuture[List[B]]` в котором будут результаты применения функции к каждому элементу списка.
   */
  def traverse[A, B](list: List[A])(f: A => DelayedFuture[B])(using ExecutionContext): DelayedFuture[List[B]]


  /**
   * Написать функцию batchTraverse которая будет принимать список `DelayedFuture` и выдавать `DelayedFuture[List[A]]`
   * в которой `Future` над элементами будут запускаться не сразу а батчами размера size.
   * Таким образом, вам нужно выполнить все `Future` из списка, но одновременно необходимо запускать не более `batchSize` штук.
   */
  def batchTraverse[A](in: List[A], batchSize: Int, futures: DelayedFuture[A])(using ExecutionContext): DelayedFuture[List[A]]

  /**
   * Дан набор возможных трансформаций: type Transformation[T] = T => DelayedFuture§[T]
   * Написать функцию преобразования последовательности трансформаций в возможную трансформацию.
   * Новая трансформация это результат работы всей цепочки трансформаций, которые не вернули Future.failed.
   * Если все вернули Future.failed, то общий результат Future.failed.
   */
  def transformationChain[T](chain: Seq[Transformation[T]])(using ExecutionContext): Transformation[T]


}
