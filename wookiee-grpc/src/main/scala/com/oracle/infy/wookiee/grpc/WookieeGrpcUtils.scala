package com.oracle.infy.wookiee.grpc

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils.curatorFramework
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.retry.RetryUntilElapsed

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object WookieeGrpcUtils {
  val DEFAULT_MAX_MESSAGE_SIZE = 4194304

  def createCurator(
      zkQuorumString: String,
      retryInterval: FiniteDuration,
      zookeeperBlockingExecutionContext: ExecutionContext
  ): IO[CuratorFramework] =
    IO.blocking {
      curatorFramework(
        zkQuorumString,
        zookeeperBlockingExecutionContext,
        new RetryUntilElapsed(retryInterval.toMillis.toInt, retryInterval.toMillis.toInt / 3)
      )
    }
}
