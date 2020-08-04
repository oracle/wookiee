# wookiee-grpc 

## Install
wookiee-grpc is available for Scala 2.12 and 2.13. There are no plans to support scala 2.11 or lower.
```scala
libraryDependencies += "com.oracle.infy.wookiee" %% "wookiee-grpc" % "@VERSION@"
```

## Code gen
First generate code using `.proto`. See [ScalaPB](https://github.com/scalapb/ScalaPB) for a guide. 

Declare your gRPC service using proto3 syntax and save it in `@PROTO_FILE@`
```proto
@PROTO_DEF@
```

Add ScalaPB plugin to `plugin.sbt` file
```sbt
@PLUGIN_DEF@

```

Configure the project in `build.sbt` so that ScalaPB will generate code
```sbt
@PROJECT_DEF@
```

## Client
First generate code using `.proto` file and [ScalaPB](https://github.com/scalapb/ScalaPB). 

```scala mdoc
import java.util.concurrent.{Executors, ForkJoinPool}

import cats.effect.{ContextShift, IO}
import com.oracle.infy.wookiee.grpc.WookieeGrpcChannel

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object GrpcClient {

  def main(args: Array[String]): Unit = {

    val dispatcherThreads = 1
    val dispatcherEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(dispatcherThreads))
    val blockingEC = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
    val mainEC = ExecutionContext.fromExecutorService(new ForkJoinPool(4))

    implicit val contextShift: ContextShift[IO] = IO.contextShift(mainEC)

    WookieeGrpcChannel.of(
      zookeeperQuorum = "localhost:2181",
      serviceDiscoveryPath = "/discovery",
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = Int.MaxValue,
      grpcChannelThreadLimit = dispatcherThreads,
      dispatcherExecutionContext = dispatcherEC,
      mainExecutionContext = mainEC,
      blockingExecutionContext = blockingEC
    )
    ()
  }
}
```

## Server
