package com.ashareai.app.scoring

import java.security.MessageDigest
import kotlin.math.abs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/** Versioned deterministic factor contract shared by both workspaces. */
data class FactorGenome(
    val formulaVersion: String = FACTOR_FORMULA_VERSION,
    val schemaVersion: String = FACTOR_GENOME_SCHEMA_VERSION,
    val fundamentalWeight: Double = .35,
    val technicalWeight: Double = .35,
    val sentimentWeight: Double = .20,
    val qualityWeight: Double = .10,
    val momentumWeight: Double = 0.0,
    val volatilityWeight: Double = 0.0,
    val liquidityWeight: Double = 0.0,
    val buyThreshold: Double = 70.0,
    val sellThreshold: Double = 50.0,
    val riskLowThreshold: Double = 70.0,
    val riskHighThreshold: Double = 50.0,
    val maxPosition: Int = 70,
    val riskBudget: Double = .20,
    val mutationSeed: Int = 0,
) {
    init {
        require(formulaVersion.isNotBlank()) { "formula version is required" }
        require(schemaVersion.isNotBlank()) { "schema version is required" }
        require(
            listOf(fundamentalWeight, technicalWeight, sentimentWeight, qualityWeight)
                .all { it.isFinite() && it in 0.05..0.60 },
        ) { "production component weights must be within [0.05, 0.60]" }
        require(
            listOf(momentumWeight, volatilityWeight, liquidityWeight)
                .all { it.isFinite() && it in 0.0..0.30 },
        ) { "shadow component weights must be within [0, 0.30]" }
        require(
            listOf(buyThreshold, sellThreshold, riskLowThreshold, riskHighThreshold)
                .all { it.isFinite() && it in 0.0..100.0 },
        ) { "thresholds must be within [0, 100]" }
        require(riskBudget.isFinite() && riskBudget > 0.0 && riskBudget <= 1.0) {
            "risk budget must be within (0, 1]"
        }
        require(mutationSeed >= 0) { "mutation seed must be non-negative" }
        require(abs(totalWeight() - 1.0) <= 1e-6) { "factor weights must sum to 1" }
        require(buyThreshold - sellThreshold >= 5.0) { "buy/sell threshold gap must be at least 5" }
        require(riskLowThreshold - riskHighThreshold >= 5.0) { "risk threshold gap must be at least 5" }
        require(maxPosition in setOf(0, 10, 20, 30, 50, 70, 100)) { "unsupported max position" }
    }

    fun totalWeight() = fundamentalWeight + technicalWeight + sentimentWeight + qualityWeight +
        momentumWeight + volatilityWeight + liquidityWeight

    /** Stable identity of the parameters used by a deterministic decision. */
    val parameterSha256: String
        get() {
            val values = buildJsonObject {
                put("buy_threshold", buyThreshold)
                put("formula_version", formulaVersion)
                put("fundamental_weight", fundamentalWeight)
                if (formulaVersion != FACTOR_FORMULA_VERSION) {
                    put("liquidity_weight", liquidityWeight)
                }
                put("max_position", maxPosition)
                if (formulaVersion != FACTOR_FORMULA_VERSION) {
                    put("momentum_weight", momentumWeight)
                }
                put("mutation_seed", mutationSeed)
                put("quality_weight", qualityWeight)
                put("risk_budget", riskBudget)
                put("risk_high_threshold", riskHighThreshold)
                put("risk_low_threshold", riskLowThreshold)
                put("schema_version", schemaVersion)
                put("sell_threshold", sellThreshold)
                put("sentiment_weight", sentimentWeight)
                put("technical_weight", technicalWeight)
                if (formulaVersion != FACTOR_FORMULA_VERSION) {
                    put("volatility_weight", volatilityWeight)
                }
            }
            val canonical = Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), values)
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
}

data class FactorEvidence(
    val fundamental: Double,
    val technical: Double,
    val sentiment: Double,
    val quality: Double,
    val dividendBonus: Double = 0.0,
    val eventRiskMultiplier: Double = 1.0,
    val marketScoreAdjustment: Double = 0.0,
    val marketRiskMultiplier: Double = 1.0,
    val momentum: Double = 50.0,
    val volatility: Double = 50.0,
    val liquidity: Double = 50.0,
)

data class FactorDecision(
    val score: Double,
    val baseScore: Double,
    val action: Action,
    val risk: Risk,
    val position: Int,
    val formulaVersion: String,
)

enum class Action { BUY, HOLD, SELL }
enum class Risk { LOW, MEDIUM, HIGH }

object FactorScoring {
    /** Score before event and market risk multipliers, matching base_total_score semantics. */
    fun baseScore(e: FactorEvidence, genome: FactorGenome = FactorGenome()): Double {
        validate(e)
        val fundamental = minOf(100.0, e.fundamental + e.dividendBonus)
        return (fundamental * genome.fundamentalWeight + e.technical * genome.technicalWeight +
            e.sentiment * genome.sentimentWeight + e.quality * genome.qualityWeight +
            e.momentum * genome.momentumWeight + e.volatility * genome.volatilityWeight +
            e.liquidity * genome.liquidityWeight + e.marketScoreAdjustment).coerceIn(0.0, 100.0).round6()
    }

    fun score(e: FactorEvidence, genome: FactorGenome = FactorGenome()): Double {
        validate(e)
        return (baseScore(e, genome) *
            e.eventRiskMultiplier * e.marketRiskMultiplier).round6()
    }

    fun decide(e: FactorEvidence, genome: FactorGenome = FactorGenome()): FactorDecision {
        val score = score(e, genome)
        val action = when {
            score >= genome.buyThreshold -> Action.BUY
            score < genome.sellThreshold -> Action.SELL
            else -> Action.HOLD
        }
        val risk = when {
            score >= genome.riskLowThreshold -> Risk.LOW
            score < genome.riskHighThreshold -> Risk.HIGH
            else -> Risk.MEDIUM
        }
        val position = when {
            score >= genome.buyThreshold + 10 -> 70
            score >= genome.buyThreshold -> 50
            score >= genome.sellThreshold -> 20
            score >= genome.sellThreshold - 10 -> 10
            else -> 0
        }.coerceAtMost(genome.maxPosition)
        return FactorDecision(score, baseScore(e, genome), action, risk, position, genome.formulaVersion)
    }

    private fun validate(e: FactorEvidence) {
        require(listOf(e.fundamental, e.technical, e.sentiment, e.quality, e.momentum, e.volatility, e.liquidity)
            .all { it in 0.0..100.0 }) { "component scores must be within [0, 100]" }
        require(e.dividendBonus in 0.0..10.0)
        require(e.eventRiskMultiplier in 0.0..1.0)
        require(e.marketScoreAdjustment in -10.0..10.0)
        require(e.marketRiskMultiplier in .8..1.0)
    }

    private fun Double.round6() = kotlin.math.round(this * 1_000_000.0) / 1_000_000.0
}

const val FACTOR_FORMULA_VERSION = "factor-v1.0.0"
const val SHADOW_FACTOR_FORMULA_VERSION = "factor-v2.0.0"
const val FACTOR_GENOME_SCHEMA_VERSION = "factor-genome-v1"
