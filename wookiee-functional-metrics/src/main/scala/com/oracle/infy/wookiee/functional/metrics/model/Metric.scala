package com.oracle.infy.wookiee.metrics.model

import java.util.concurrent.TimeUnit

trait Metric[F[_]]

trait Timer[F[_]] extends Metric[F] {
  def time[A](f: F[A]): F[A]
  def update(time: Long, unit: TimeUnit): F[Unit]
}

trait Counter[F[_]] extends Metric[F] {
  def inc(): F[Unit]
  def inc(amount: Long): F[Unit]
}

trait Meter[F[_]] extends Metric[F] {
  def mark(): F[Unit]
  def mark(amount: Long): F[Unit]
}

trait Histogram[F[_]] extends Metric[F] {
  def update(amount: Long): F[Unit]
}

trait Gauge[F[_], T] {
  def setValue(value: T): F[Unit]
}
