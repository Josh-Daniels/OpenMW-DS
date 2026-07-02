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
    const val P_INVENTORY_START = "COMPANION_INVENTORY_START"
    const val P_INVENTORY_ITEM = "COMPANION_INVENTORY_ITEM:"
    const val P_INVENTORY_END = "COMPANION_INVENTORY_END"
    private const val P_EQUIPMENT = "COMPANION_EQUIPMENT:"
    private const val P_ACTIVE_EFFECTS = "COMPANION_ACTIVE_EFFECTS:"
    const val P_CHARACTER = "COMPANION_CHARACTER:"
    private const val P_TARGET = "COMPANION_TARGET:"
    const val P_INFO = "COMPANION_INFO:"
    const val P_JOURNAL_START = "COMPANION_JOURNAL_START:"
    const val P_JOURNAL_ENTRY = "COMPANION_JOURNAL_ENTRY:"
    const val P_JOURNAL_END = "COMPANION_JOURNAL_END:"
    // Streamed dialogue topic list (one topic per line so a long list never trips
    // the engine's 4096-byte stdout flush). Topic payloads are plain strings, not JSON.
    // CLOSED signals the conversation ended → clear the list.
    const val P_DIALOGUE_START = "COMPANION_DIALOGUE_START"
    const val P_DIALOGUE_TOPIC = "COMPANION_DIALOGUE_TOPIC:"
    const val P_DIALOGUE_END = "COMPANION_DIALOGUE_END"
    const val P_DIALOGUE_CLOSED = "COMPANION_DIALOGUE_CLOSED"
    // Service entries (Barter/Spells/Travel/...), streamed separately from topics.
    // The colon on _SERVICE disambiguates it from _SERVICES_START/_END under contains().
    const val P_DIALOGUE_SERVICES_START = "COMPANION_DIALOGUE_SERVICES_START"
    const val P_DIALOGUE_SERVICE = "COMPANION_DIALOGUE_SERVICE:"
    const val P_DIALOGUE_SERVICES_END = "COMPANION_DIALOGUE_SERVICES_END"
    // NPC name (also the history-clear signal) + streamed response text. _SAY_LINE and
    // _SAY_LINKS carry the colon; _SAY_START/_TOPIC/_END are matched as-is. Order in the
    // repo's when() disambiguates _SAY_START from _SAY_START-prefixed lines via contains.
    const val P_DIALOGUE_NPC = "COMPANION_DIALOGUE_NPC:"
    const val P_DIALOGUE_SAY_START = "COMPANION_DIALOGUE_SAY_START"
    const val P_DIALOGUE_SAY_TOPIC = "COMPANION_DIALOGUE_SAY_TOPIC:"
    const val P_DIALOGUE_SAY_LINE = "COMPANION_DIALOGUE_SAY_LINE:"
    const val P_DIALOGUE_SAY_LINKS = "COMPANION_DIALOGUE_SAY_LINKS:"
    const val P_DIALOGUE_SAY_END = "COMPANION_DIALOGUE_SAY_END"
    // In-dialogue system message box (e.g. "10 Gold pieces were removed..."). Single
    // short line, published to the history immediately (no START/END streaming).
    const val P_DIALOGUE_MSG = "COMPANION_DIALOGUE_MSG:"
    // Question/answer choices (shown instead of topics while active). CHOICE payload is
    // "<display>|<id>". Empty START/END = no active choice.
    const val P_DIALOGUE_CHOICE_START = "COMPANION_DIALOGUE_CHOICE_START"
    const val P_DIALOGUE_CHOICE = "COMPANION_DIALOGUE_CHOICE:"
    const val P_DIALOGUE_CHOICE_END = "COMPANION_DIALOGUE_CHOICE_END"
    // Streamed character-description batch (one record per line — the full set of
    // descriptions is far larger than the engine's 4096-byte stdout flush limit).
    const val P_CHARDETAIL_START = "COMPANION_CHARDETAIL_START"
    const val P_CHARDETAIL_ATTR = "COMPANION_CHARDETAIL_ATTR:"
    const val P_CHARDETAIL_SKILL = "COMPANION_CHARDETAIL_SKILL:"
    const val P_CHARDETAIL_DYN = "COMPANION_CHARDETAIL_DYN:"
    const val P_CHARDETAIL_RACE = "COMPANION_CHARDETAIL_RACE:"
    const val P_CHARDETAIL_CLASS = "COMPANION_CHARDETAIL_CLASS:"
    const val P_CHARDETAIL_LEVEL = "COMPANION_CHARDETAIL_LEVEL:"
    const val P_CHARDETAIL_END = "COMPANION_CHARDETAIL_END"

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
                line.contains(P_ACTIVE_EFFECTS) ->
                    current.copy(activeEffects = parseActiveEffects(after(line, P_ACTIVE_EFFECTS)))
                line.contains(P_CHARACTER) ->
                    current.copy(character = parseCharacter(after(line, P_CHARACTER)))
                line.contains(P_TARGET) ->
                    current.copy(target = parseTarget(after(line, P_TARGET)))
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
                type = o.optString("type", "spell"),
                icon = o.optString("icon", "")
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
        return (0 until arr.length()).mapNotNull {
            parseInventoryItem(arr.getJSONObject(it).toString())
        }
    }

    /** Parses a COMPANION_INFO payload into the detail-popup model. Null if malformed. */
    fun parseItemInfo(json: String): ItemInfo? = try {
        val o = JSONObject(json)
        val rows = o.optJSONArray("rows")?.let { arr ->
            (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                r.optString("k", "") to r.optString("v", "")
            }
        } ?: emptyList()
        val effects = o.optJSONArray("effects")?.let { arr ->
            (0 until arr.length()).map {
                val e = arr.getJSONObject(it)
                InfoEffect(e.optString("t", ""), e.optBoolean("h", false))
            }
        } ?: emptyList()
        ItemInfo(o.optString("name", ""), rows, effects)
    } catch (e: Exception) {
        null
    }

    /** Parses a single COMPANION_INVENTORY_ITEM payload. Null if malformed. */
    fun parseInventoryItem(json: String): InventoryItem? = try {
        val o = JSONObject(json)
        InventoryItem(
            id = o.optString("id", ""),
            stackId = o.optString("sid", ""),
            name = o.optString("name", ""),
            count = o.optInt("count", 1),
            category = o.optString("cat", "misc"),
            icon = o.optString("icon", ""),
            statVal = o.optString("statVal", ""),
            statKey = o.optString("statKey", ""),
            cond = if (o.has("cond")) o.optDouble("cond", 1.0).toFloat() else null
        )
    } catch (e: Exception) {
        null
    }

    private fun parseEquipment(json: String): Map<String, String> {
        val o = JSONObject(json)
        val map = LinkedHashMap<String, String>()
        o.keys().forEach { k -> map[k] = o.getString(k) }
        return map
    }

    private fun parseActiveEffects(json: String): List<ActiveEffect> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ActiveEffect(
                name = o.optString("name", ""),
                harmful = o.optBoolean("harmful", false),
                icon = o.optString("icon", "")
            )
        }
    }

    private fun parseCharacter(json: String): CharacterInfo {
        val o = JSONObject(json)
        val attrs = o.getJSONArray("attributes")
        val attributes = (0 until attrs.length()).map { i ->
            val a = attrs.getJSONObject(i)
            AttributeStat(
                id = a.getString("id"),
                name = a.optString("name", ""),
                current = a.getDouble("current").toFloat(),
                base = a.getDouble("base").toFloat()
                // icon arrives via COMPANION_CHARDETAIL_ATTR (merged in the repo).
            )
        }
        val skillsArr = o.getJSONArray("skills")
        val skills = (0 until skillsArr.length()).map { i ->
            val s = skillsArr.getJSONObject(i)
            SkillStat(
                id = s.getString("id"),
                name = s.optString("name", ""),
                value = s.getDouble("value").toFloat(),
                category = s.optString("cat", "misc"),
                // icon arrives via COMPANION_CHARDETAIL_SKILL (merged in the repo).
                progress = s.optDouble("progress", 0.0).toFloat()
            )
        }
        return CharacterInfo(
            name = o.optString("name", ""),
            race = o.optString("race", ""),
            className = o.optString("class", ""),
            birthSign = o.optString("birthSign", ""),
            level = o.optInt("level", 0),
            attributes = attributes,
            skills = skills
        )
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

    /** Empty payload {} or a missing name/health → null (no current target). */
    private fun parseTarget(json: String): TargetInfo? {
        val o = JSONObject(json)
        val name = o.optString("name", "")
        val h = o.optJSONObject("health") ?: return null
        if (name.isEmpty()) return null
        return TargetInfo(
            name = name,
            health = Dynamic(
                h.getDouble("current").toFloat(),
                h.getDouble("max").toFloat()
            )
        )
    }

    private fun parseSelectedSpell(payload: String): String? {
        if (payload == "null") return null
        // payload is a JSON string literal like "fireball"; wrap to reuse JSON unescaping
        return JSONArray("[$payload]").getString(0)
    }

    // ---- Streamed character-description batch parsing ----

    private fun strArray(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it, "") }
    }

    /** id, desc, governed-skill display names, and icon path. Null if malformed. */
    fun parseDetailAttr(json: String): DetailAttr? = try {
        val o = JSONObject(json)
        DetailAttr(o.optString("id", ""), o.optString("desc", ""),
            strArray(o, "skills"), o.optString("icon", ""))
    } catch (e: Exception) { null }

    /** id, desc, governing-attribute name, specialization, icon. Null if malformed. */
    fun parseDetailSkill(json: String): DetailSkill? = try {
        val o = JSONObject(json)
        DetailSkill(o.optString("id", ""), o.optString("desc", ""),
            o.optString("attr", ""), o.optString("spec", ""), o.optString("icon", ""))
    } catch (e: Exception) { null }

    /** id ("health"/"magicka"/"fatigue") and its description. Null if malformed. */
    fun parseDetailDyn(json: String): Pair<String, String>? = try {
        val o = JSONObject(json)
        o.optString("id", "") to o.optString("desc", "")
    } catch (e: Exception) { null }

    fun parseDetailRace(json: String): DetailRace? = try {
        val o = JSONObject(json)
        DetailRace(o.optString("desc", ""), strArray(o, "skills"), strArray(o, "abilities"))
    } catch (e: Exception) { null }

    fun parseDetailClass(json: String): DetailClass? = try {
        val o = JSONObject(json)
        DetailClass(o.optString("desc", ""), o.optString("spec", ""),
            strArray(o, "attrs"), strArray(o, "major"), strArray(o, "minor"))
    } catch (e: Exception) { null }

    fun parseDetailLevel(json: String): Pair<Int, Int>? = try {
        val o = JSONObject(json)
        o.optInt("progress", 0) to o.optInt("total", 0)
    } catch (e: Exception) { null }

    data class DetailAttr(val id: String, val desc: String, val skills: List<String>, val icon: String)
    data class DetailSkill(val id: String, val desc: String, val attr: String, val spec: String, val icon: String)
    data class DetailRace(val desc: String, val skills: List<String>, val abilities: List<String>)
    data class DetailClass(
        val desc: String, val spec: String,
        val attrs: List<String>, val major: List<String>, val minor: List<String>
    )
}
