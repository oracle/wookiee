package com.oracle.infy.wookiee.grpc.model

object LoadBalancers {

  sealed trait LoadBalancingPolicy

  // Use the default round robin lb that built into gRPC
  sealed trait RoundRobinPolicy extends LoadBalancingPolicy
  final case object RoundRobinPolicy extends RoundRobinPolicy

  // Load balancer based on server load. If all server loads are equal, it will just be round robin
  sealed trait RoundRobinWeightedPolicy extends LoadBalancingPolicy
  final case object RoundRobinWeightedPolicy extends RoundRobinWeightedPolicy

  // Load balancer based on consistent hashing and round robin. If the caller has "hash-key" call option set,
  // it will do consistent hashing based load balancing. Otherwise it will default to round robin
  sealed trait RoundRobinHashedPolicy extends LoadBalancingPolicy
  final case object RoundRobinHashedPolicy extends RoundRobinHashedPolicy
}
