package com.oracle.infy.wookiee.grpc.settings

// Configuration for SSL which can be reflected on both the client and server
// Will be filled in from the configuration of 'wookiee-grpc-component.grpc.ssl'
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
