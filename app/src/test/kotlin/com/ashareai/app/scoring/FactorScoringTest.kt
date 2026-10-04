package com.ashareai.app.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FactorScoringTest {
    @Test
    fun defaultFormulaMatchesSourceSample() {
        val decision = FactorScoring.decide(
            FactorEvidence(
                fundamental = 80.0,
                technical = 70.0,
                sentiment = 60.0,
                quality = 90.0,
                dividendBonus = 5.0,
                eventRiskMultiplier = 0.8,
                marketScoreAdjustment = 2.0,
                marketRiskMultiplier = 0.9,
            ),
        )

        assertEquals(77.25, decision.baseScore, 0.000001)
        assertEquals(55.62, decision.score, 0.000001)
        assertEquals(FACTOR_FORMULA_VERSION, decision.formulaVersion)
        assertEquals(Action.HOLD, decision.action)
        assertEquals(Risk.MEDIUM, decision.risk)
        assertEquals(20, decision.position)
    }

    @Test
    fun decisionBandsUseDeterministicBoundaryRules() {
        val buy = FactorScoring.decide(evidence(70.0))
        val sell = FactorScoring.decide(evidence(49.999999))
        val hold = FactorScoring.decide(evidence(50.0))

        assertEquals(Action.BUY, buy.action)
        assertEquals(Action.SELL, sell.action)
        assertEquals(Action.HOLD, hold.action)
        assertEquals(50, buy.position)
        assertEquals(10, sell.position)
        assertEquals(20, hold.position)
    }

    @Test
    fun defaultGenomeHashMatchesSourceCanonicalHash() {
        assertEquals(
            "50915e77bc3cb15a31149a46896d110f789d975bede0849ce56f2aacb0630abe",
            FactorGenome().parameterSha256,
        )
    }

    @Test
    fun genomeRejectsOutOfContractParameters() {
        listOf<() -> FactorGenome>(
            { FactorGenome(fundamentalWeight = 0.04, technicalWeight = 0.66) },
            {
                FactorGenome(
                    fundamentalWeight = 0.30,
                    technicalWeight = 0.25,
                    sentimentWeight = 0.09,
                    qualityWeight = 0.05,
                    momentumWeight = 0.31,
                )
            },
            { FactorGenome(buyThreshold = 101.0) },
            { FactorGenome(riskBudget = 0.0) },
            { FactorGenome(mutationSeed = -1) },
        ).forEach { create ->
            try {
                create()
                fail("invalid genome should be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

    private fun evidence(score: Double) = FactorEvidence(
        fundamental = score,
        technical = score,
        sentiment = score,
        quality = score,
    )
}
