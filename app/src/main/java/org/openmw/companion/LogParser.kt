package org.openmw.companion

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure, host-independent parsing. Each COMPANION_* line carries a JSON payload
 * after its prefix. We find the prefix (the raw log line has a timestamp and
 * script tag in front of it), then decode the payload and fold it into the
 * current GameState. Anything malformed is swallowed and returns null so a
 * single bad line can never crash the reader.
 */
object LogParser {

    private const val TAG = "CompanionParser"
    private const val P_STATS = "COMPANION_STATS:"
    private const val P_SPELLS = "COMPANION_SPELLS:"
    private const val P_SELECTED_SPELL = "COMPANION_SELECTED_SPELL:"
    private const val P_INVENTORY = "COMPANION_INVENTORY:"
    private const val P_EQUIPMENT = "COMPANION_EQUIPMENT:"
    const val P_JOURNAL_START = "COMPANION_JOURNAL_START:"
    const val P_JOURNAL_ENTRY = "COMPANION_JOURNAL_ENTRY:"
    const val P_JOURNAL_END = "COMPANION_JOURNAL_END:"

    /** Returns an updated state, or null if this line isn't ours / was malformed. */
    fun parseLine(line: String, current: GameState): GameState? {
        return try {
            when {
                line.contains(P_STATS) ->
                    parseStats(after(line, P_STATS), current)
                line.contains(P_SELECTED_SPELL) ->
                    current.copy(selectedSpell = parseSelectedSpell(after(line, P_SELECTED_SPELL)))
                line.contains(P_SPELLS) ->
                    current.copy(spells = parseSpells(after(line, P_SPELLS)))
                line.contains(P_INVENTORY) ->
                    current.copy(inventory = parseInventory(after(line, P_INVENTORY)))
                line.contains(P_EQUIPMENT) ->
                    current.copy(equipment = parseEquipment(after(line, P_EQUIPMENT)))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSpells(json: String): List<SpellEntry> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SpellEntry(
                id = o.getString("id"),
                name = o.optString("name", ""),
                type = o.optString("type", "spell")
            )
        }
    }

    private fun after(line: String, prefix: String): String {
        val i = line.indexOf(prefix)
        return line.substring(i + prefix.length).trim()
    }

    private fun parseStats(json: String, current: GameState): GameState {
        val o = JSONObject(json)
        fun dyn(name: String): Dynamic {
            val d = o.getJSONObject(name)
            return Dynamic(d.getDouble("current").toFloat(), d.getDouble("max").toFloat())
        }
        val p = o.getJSONObject("pos")
        return current.copy(
            health = dyn("health"),
            magicka = dyn("magicka"),
            fatigue = dyn("fatigue"),
            cell = o.getString("cell"),
            pos = Vec3(
                p.getDouble("x").toFloat(),
                p.getDouble("y").toFloat(),
                p.getDouble("z").toFloat()
            ),
            cellIsExterior = o.optBoolean("cellExt", false),
            cellGridX = o.optInt("cellGX", 0),
            cellGridY = o.optInt("cellGY", 0),
            rotZ = o.optDouble("rotZ", 0.0).toFloat(),
            lastUpdateMs = System.currentTimeMillis()
        )
    }

    private fun parseInventory(json: String): List<InventoryItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            InventoryItem(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                count = o.optInt("count", 1),
                category = o.optString("cat", "misc")
            )
        }
    }

    private fun parseEquipment(json: String): Map<String, String> {
        val o = JSONObject(json)
        val map = LinkedHashMap<String, String>()
        o.keys().forEach { k -> map[k] = o.getString(k) }
        return map
    }

    fun parseJournalEntry(json: String): JournalEntry? = try {
        val o = JSONObject(json)
        JournalEntry(
            questId = o.optString("q", ""),
            questName = o.optString("n", ""),
            text = o.optString("t", ""),
            day = o.optInt("d", 0),
            month = o.optInt("m", 0),
            dayOfMonth = o.optInt("dom", 0)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Journal entry parse failed: $json", e)
        null
    }

    private fun parseSelectedSpell(payload: String): String? {
        if (payload == "null") return null
        // payload is a JSON string literal like "fireball"; wrap to reuse JSON unescaping
        return JSONArray("[$payload]").getString(0)
    }
}
