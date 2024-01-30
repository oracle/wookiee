package com.oracle.infy.wookiee.component.web.ws.tyrus

import io.helidon.common.http.DataChunk
import org.glassfish.tyrus.spi.Connection

import java.nio.ByteBuffer
import java.util.concurrent.Flow
import javax.websocket.CloseReason
import javax.websocket.CloseReason.CloseCodes.{NORMAL_CLOSURE, UNEXPECTED_CONDITION}
import com.oracle.infy.wookiee.logging.LoggingAdapter

// Based on io.helidon.webserver.tyrus.TyrusReaderSubscriber but in Scala and purpose-built for Wookiee
class WookieeTyrusSubscriber(connection: Connection) extends Flow.Subscriber[DataChunk] with LoggingAdapter {
  require(connection != null, "Connection cannot be null")

  protected val MaxRetries = 5
  private val ConnectionClosed = new CloseReason(NORMAL_CLOSURE, "Connection closed")

  protected[oracle] var subscription: Flow.Subscription = _

  override def onSubscribe(subscription: Flow.Subscription): Unit = {
    this.subscription = subscription
    subscription.request(1L)
  }

  override def onNext(item: DataChunk): Unit =
    if (subscription != null) {
      submitDataChunk(item)
    } else {
      item.release()
    }

  private def submitDataChunk(item: DataChunk): Unit = {
    try {
      item.data().toList.foreach(submitBuffer)
    } finally {
      item.flush()
      item.release()
    }

    if (subscription != null) {
      subscription.request(1L)
    }
  }

  private def submitBuffer(data: ByteBuffer): Unit = {
    var retries = MaxRetries
    while (data.remaining() > 0 && retries > 0) {
      connection.getReadHandler.handle(data)
      retries -= 1
    }

    if (retries == 0) {
      log.info("DEBUG : WTS : Exhausted retires closing connection ")
      subscription.cancel()
      subscription = null
      connection.close(
        new CloseReason(UNEXPECTED_CONDITION, s"Tyrus did not consume all data after $MaxRetries retries")
      )
    }
  }

  override def onError(throwable: Throwable): Unit = {
    log.info(s"DEBUG : WTS : Encountered error ${throwable.getMessage} ")
    connection.close(new CloseReason(UNEXPECTED_CONDITION, throwable.getMessage))
  }

  override def onComplete(): Unit =
    connection.close(ConnectionClosed)
}
