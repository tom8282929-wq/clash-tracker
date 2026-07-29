package com.example.clashtracker

import android.content.Context
import org.json.JSONObject

/**
 * Loads card elixir costs from assets/elixir_costs.json.
 * "Mirror" is variable-cost (last card played + 1, capped at 6) and is
 * exposed separately so callers handle it explicitly rather than trusting
 * a fixed lookup value.
 */
object CardDatabase {

    private var costs: Map<String, Int> = emptyMap()
    var loaded = false
        private set

    fun load(context: Context) {
        if (loaded) return
        val json = context.assets.open("elixir_costs.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(json)
        val cardsArray = root.getJSONArray("cards")
        val map = mutableMapOf<String, Int>()

        for (i in 0 until cardsArray.length()) {
            val card = cardsArray.getJSONObject(i)
            val name = card.getString("name")
            if (card.isNull("elixir")) continue // e.g. Mirror — variable cost
            map[name] = card.getInt("elixir")
        }
        costs = map
        loaded = true
    }

    /** Returns null if the card name isn't recognized or has variable cost. */
    fun costOf(cardName: String): Int? = costs[cardName]

    fun allCardNames(): Set<String> = costs.keys
}
