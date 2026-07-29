package com.example.clashtracker

import kotlin.math.min

/**
 * Best-effort ESTIMATE of the opponent's elixir. This can never be exact —
 * the game doesn't expose the opponent's elixir anywhere on screen. Accuracy
 * depends entirely on how reliably CardDetector catches each play.
 *
 * Regen rates:
 *  - normal: 1 elixir per 2.8s
 *  - double elixir (after 2:00 game time): 1 per 1.4s
 *  - overtime / sudden death: 1 per 0.93s
 */
class ElixirTracker {

    companion object {
        const val NORMAL_REGEN_MS = 2800L
        const val DOUBLE_REGEN_MS = 1400L
        const val TRIPLE_REGEN_MS = 930L
        const val MAX_ELIXIR = 10.0
        const val DOUBLE_ELIXIR_AT_MS = 120_000L
    }

    private var elixir = 5.0
    private var lastUpdateMs = System.currentTimeMillis()
    private var matchStartMs = System.currentTimeMillis()

    // Lower this as missed/low-confidence detections accumulate.
    var confidence = 1.0
        private set

    fun startMatch() {
        elixir = 5.0
        matchStartMs = System.currentTimeMillis()
        lastUpdateMs = matchStartMs
        confidence = 1.0
    }

    /** Call frequently (e.g. every UI tick) to accrue regen. */
    fun tick(overtimeActive: Boolean = false) {
        val now = System.currentTimeMillis()
        val elapsedSinceMatchStart = now - matchStartMs
        val regenMs = when {
            overtimeActive -> TRIPLE_REGEN_MS
            elapsedSinceMatchStart >= DOUBLE_ELIXIR_AT_MS -> DOUBLE_REGEN_MS
            else -> NORMAL_REGEN_MS
        }
        val deltaMs = now - lastUpdateMs
        elixir = min(MAX_ELIXIR, elixir + deltaMs.toDouble() / regenMs)
        lastUpdateMs = now
    }

    /** Call when CardDetector reports a new opponent card play. */
    fun onCardPlayed(cardName: String, detectionConfidence: Double) {
        val cost = CardDatabase.costOf(cardName)
        if (cost != null) {
            elixir = (elixir - cost).coerceAtLeast(0.0)
        }
        // Simple confidence decay/recovery based on detection quality.
        confidence = ((confidence * 0.8) + (detectionConfidence * 0.2)).coerceIn(0.0, 1.0)
    }

    fun currentEstimate(): Double = elixir
}
