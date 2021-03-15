package com.oracle.infy.wookiee.grpc.settings

final case class SSLClientSettings(
    sslCertificateChainPath: String,
    sslPrivateKeyPath: String,
    sslPassphrase: Option[String],
    sslCertificateTrustPath: String
)
