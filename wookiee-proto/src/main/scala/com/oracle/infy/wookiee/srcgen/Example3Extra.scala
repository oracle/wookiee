package com.oracle.infy.wookiee.srcgen

sealed trait ActivityServiceException extends Exception with ServiceException

case class InvalidGroupingIntervalException(code: Int, detail: String, paramsJson: Option[String])
    extends ActivityServiceException
    with ValidationException
