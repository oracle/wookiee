package com.oracle.infy.wookiee.grpc.model

object LoadBalancers {

  sealed trait LoadBalancingPolicy

  sealed trait RoundRobinPolicy extends LoadBalancingPolicy

  final case object RoundRobinPolicy extends RoundRobinPolicy

  sealed trait RoundRobinWeightedPolicy extends LoadBalancingPolicy

  final case object RoundRobinWeightedPolicy extends RoundRobinWeightedPolicy
}
