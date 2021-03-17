package com.oracle.infy.wookiee.grpc.settings

final case class SSLServerSettings(
    sslCertificateChainPath: String,
    sslPrivateKeyPath: String,
    sslPassphrase: Option[String],
    sslCertificateTrustPath: Option[String]
)
