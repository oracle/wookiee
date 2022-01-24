package com.oracle.infy.wookiee.grpc

import cats.effect.{Blocker, ContextShift, IO}
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils.curatorFramework
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.retry.RetryForever

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object WookieeGrpcUtils {

  def createCurator(
      zkQuorumString: String,
      retryInterval: FiniteDuration,
      zookeeperBlockingExecutionContext: ExecutionContext
  )(implicit cs: ContextShift[IO], blocker: Blocker): IO[CuratorFramework] =
    cs.blockOn(blocker) {
      IO {
        curatorFramework(
          zkQuorumString,
          zookeeperBlockingExecutionContext,
          new RetryForever(retryInterval.toMillis.toInt)
        )
      }
    }
}
