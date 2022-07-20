package com.oracle.infy.wookiee.health

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class HealthCheckProviderSpec extends AnyWordSpecLike with Matchers {

  val rollupTests = List(
    (
      "Report NORMAL when all NORMAL", // Test Name
      List( // Health Components to roll up
        HealthComponent("test1", ComponentState.NORMAL, "", None, List()),
        HealthComponent("test2", ComponentState.NORMAL, "", None, List())
      ),
      (ComponentState.NORMAL, "Thunderbirds are GO") // Expected ApplicationHealth details
    ),
    (
      "Report DEGRADED when DEGRADED",
      List(
        HealthComponent("test1", ComponentState.NORMAL, "", None, List()),
        HealthComponent("test2", ComponentState.DEGRADED, "degradedDetails", None, List()),
        HealthComponent("test3", ComponentState.NORMAL, "", None, List())
      ),
      (ComponentState.DEGRADED, "test2[DEGRADED] - degradedDetails")
    ),
    (
      "Report CRITICAL when CRITICAL",
      List(
        HealthComponent("test1", ComponentState.NORMAL, "", None, List()),
        HealthComponent("test2", ComponentState.DEGRADED, "degradedDetails", None, List()),
        HealthComponent("test3", ComponentState.CRITICAL, "criticalDetails", None, List())
      ),
      (ComponentState.CRITICAL, "test2[DEGRADED] - degradedDetails; test3[CRITICAL] - criticalDetails")
    ),
    (
      "Report DEGRADED when DEGRADED child component",
      List(
        HealthComponent(
          "test1",
          ComponentState.NORMAL,
          "",
          None,
          List( // This should be DEGRADED since it has a DEGRADED child, but testing to ensure full hierarchy is evaluated
            HealthComponent("test1A", ComponentState.NORMAL, "", None, List()),
            HealthComponent("test1B", ComponentState.DEGRADED, "degradedDetails", None, List())
          )
        )
      ),
      (ComponentState.DEGRADED, "test1B[DEGRADED] - degradedDetails")
    ),
    (
      "Report CRITICAL when CRITICAL child component",
      List(
        HealthComponent(
          "test1",
          ComponentState.NORMAL,
          "",
          None,
          List( // This should be DEGRADED since it has a DEGRADED child, but testing to ensure full hierarchy is evaluated
            HealthComponent("test1A", ComponentState.CRITICAL, "criticalDetails", None, List()),
            HealthComponent("test1B", ComponentState.DEGRADED, "degradedDetails", None, List())
          )
        )
      ),
      (ComponentState.CRITICAL, "test1A[CRITICAL] - criticalDetails; test1B[DEGRADED] - degradedDetails")
    )
  )

  rollupTests.foreach {
    case (testName, components, (expectedState, expectedDetails)) =>
      testName in {
        val actual = HealthCheckProvider.rollupHealth(components)
        actual.state shouldEqual expectedState
        actual.details shouldEqual expectedDetails
      }
  }

}
