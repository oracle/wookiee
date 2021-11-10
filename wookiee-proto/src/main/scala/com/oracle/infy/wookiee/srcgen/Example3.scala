package com.oracle.infy.wookiee.srcgen

trait ServiceException {
  def code: Int
  def detail: String

  // Parameters as Serialized JSON, for resolving placeholders in localized message
  def paramsJson: Option[String]
}

trait PolicyException extends ServiceException

trait ClientException extends PolicyException

trait ValidationException extends ClientException


