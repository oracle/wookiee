package com.oracle.infy.wookiee.component.grpc.server

import cats.effect.unsafe.IORuntime
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.{ConfigUtil, ThreadUtil}
import com.typesafe.config.Config
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.retry.RetryUntilElapsed

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.util.Try

trait ExtensionHostServices extends LoggingAdapter {

  lazy val bossThreads: Int =
    ConfigUtil.getConfigAtEitherLevel(s"${GrpcManager.ComponentName}.grpc.boss-threads", hostConfig.getInt) // scalafix:ok
  lazy val workerThreads: Int =
    ConfigUtil.getConfigAtEitherLevel(s"${GrpcManager.ComponentName}.grpc.worker-threads", hostConfig.getInt) // scalafix:ok
  private lazy val mainEC: ExecutionContext = ThreadUtil.createEC(getClass.getSimpleName + "-main") // scalafix:ok
  private lazy val blockingEC
      : ExecutionContext = ThreadUtil.createEC(getClass.getSimpleName + "-blocking") // scalafix:ok
  private lazy val scheduledExecutor: ScheduledThreadPoolExecutor =
    ThreadUtil.scheduledThreadPoolExecutor(getClass.getSimpleName + "-scheduled", 5) // scalafix:ok

  private lazy val curatorConnectString: String =
    Try(ConfigUtil.getConfigAtEitherLevel("zookeeper-config.connect-string", hostConfig.getString)).getOrElse(
      ConfigUtil.getConfigAtEitherLevel("wookiee-zookeeper.quorum", hostConfig.getString)
    ) // scalafix:ok
  private lazy val curator: AtomicReference[CuratorFramework] = new AtomicReference() // scalafix:ok

  implicit lazy val runtime: IORuntime = ThreadUtil.ioRuntime(mainEC, blockingEC, scheduledExecutor) // scalafix:ok

  def hostConfig: Config

  def getCurator: CuratorFramework = Option(curator.get()) match {
    case Some(c) => c
    case None    => renewCurator
  }

  // Refresh the curator object, as it doesn't seem to be able to reconnect after a connection loss
  def renewCurator: CuratorFramework = {
    log.info("EHS100: Renewing curator framework")
    Try(Option(curator.get()).foreach(_.close()))
    val newCurator = createCurator(curatorConnectString)
    curator.set(newCurator)
    newCurator
  }

  protected def createCurator(
      connectionString: String
  ): CuratorFramework = {
    val retryPeriod = Try(hostConfig.getInt(s"${GrpcManager.ComponentName}.grpc.zk-retry-period-sec"))
      .getOrElse(15000)
    log.info(s"EHS101: Creating and starting curator framework with retry period of $retryPeriod seconds")

    val newCurator = GRPCUtils
      .curatorFramework(
        connectionString,
        blockingEC,
        new RetryUntilElapsed(retryPeriod * 1000, retryPeriod * 1000 / 3),
        retryPeriod * 1000
      )

    newCurator.start()
    log.info(s"EHS102: Started curator framework to '$connectionString'")

    newCurator
  }
}
