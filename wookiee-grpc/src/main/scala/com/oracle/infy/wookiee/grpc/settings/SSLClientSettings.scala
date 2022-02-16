package com.oracle.infy.wookiee.grpc.settings

final case class SSLClientSettings(
    serviceAuthority: String,
    sslCertificateTrustPath: Option[String],
    mTLSOptions: Option[mTLSOptions]
)

final case class mTLSOptions(
    sslCertificateChainPath: String,
    sslPrivateKeyPath: String,
    sslPassphrase: Option[String]
)
