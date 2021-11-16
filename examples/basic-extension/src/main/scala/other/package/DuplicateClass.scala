package other.`package`

import com.webtrends.harness.logging.LoggingAdapter

object DuplicateClass extends LoggingAdapter {
  private val inst = "O"

  def logInfo(compName: String): String = {
    log.info(s"Class hash code: '${this.hashCode()}'")
    log.info(s"Calling Instance: '$compName'")
    log.info(s"This Instance: '$inst'")
    inst
  }
}