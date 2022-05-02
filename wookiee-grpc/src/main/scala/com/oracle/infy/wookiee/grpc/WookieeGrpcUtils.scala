package com.oracle.infy.wookiee.grpc

import cats.effect.IO
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
  )(implicit): IO[CuratorFramework] =
    cs.blockOn(blocker) {
//      val _ = Some("foo").asInstanceOf[Option[String]]
      IO {
        curatorFramework(
          zkQuorumString,
          zookeeperBlockingExecutionContext,
          new RetryForever(retryInterval.toMillis.toInt)
        )
      }
    }
}
