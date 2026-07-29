package com.example.clashtracker

import java.util.ArrayDeque

/**
 * Tracks the opponent's card cycle. We don't know their 8-card deck up
 * front — cycle predictions get more reliable as more unique cards are
 * observed over the course of the match.
 */
class CycleTracker {

    // Most recent unique plays, most recent first. Capped at 4 = "out of rotation".
    private val recentUnique = ArrayDeque<String>()

    // All unique cards seen this match, in first-seen order.
    private val seenOrder = mutableListOf<String>()

    fun onCardPlayed(cardName: String) {
        if (!seenOrder.contains(cardName)) {
            seenOrder.add(cardName)
        }
        recentUnique.remove(cardName) // move to front if already present
        recentUnique.addFirst(cardName)
        while (recentUnique.size > 4) {
            recentUnique.removeLast()
        }
    }

    fun lastFour(): List<String> = recentUnique.toList()

    /**
     * Best-guess "likely back in cycle" cards: cards we've seen before that
     * are NOT in the current last-4 window. This is only meaningful once
     * enough unique cards have been observed (ideally 5+, since an 8-card
     * deck cycles after 4 other cards are played).
     */
    fun likelyNext(): List<String> {
        if (seenOrder.size <= 4) return emptyList() // not enough data yet
        return seenOrder.filter { it !in recentUnique }
    }
}
