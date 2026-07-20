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
    const val P_SPELLS_START = "COMPANION_SPELLS_START"
    const val P_SPELLS_ITEM = "COMPANION_SPELLS_ITEM:"
    const val P_SPELLS_END = "COMPANION_SPELLS_END"
    private const val P_SELECTED_SPELL = "COMPANION_SELECTED_SPELL:"
    private const val P_INVENTORY = "COMPANION_INVENTORY:"
    const val P_INVENTORY_START = "COMPANION_INVENTORY_START"
    const val P_INVENTORY_ITEM = "COMPANION_INVENTORY_ITEM:"
    const val P_INVENTORY_END = "COMPANION_INVENTORY_END"
    // Looting/pickpocketing container session. OPEN carries the header (name +
    // isCorpse) on session start; ITEM/END stream the contents (re-emitted on
    // change, one item per line for 4096-byte flush safety); CLOSED ends it.
    const val P_CONTAINER_OPEN = "COMPANION_CONTAINER_OPEN:"
    const val P_CONTAINER_ITEM = "COMPANION_CONTAINER_ITEM:"
    const val P_CONTAINER_END = "COMPANION_CONTAINER_END"
    const val P_CONTAINER_CLOSED = "COMPANION_CONTAINER_CLOSED"
    // One-shot vanilla put-restriction GMST strings; + the Lua backstop's "put blocked" signal.
    const val P_GMST = "COMPANION_GMST:"
    const val P_PUT_BLOCKED = "COMPANION_PUT_BLOCKED:"

    // Barter session (native GM_Barter TradeWindow, streamed one small line each).
    // OPEN{vendor,vendorGold,playerGold} → ITEM{side,id,sid,name,count,value,cat,icon,worn}*
    // → END; then a running OFFER{merchantOffer,balance,extraGold,vendorGold,playerGold}
    // after every change; OFFER_ACCEPTED / OFFER_REJECTED{reason} on submit; CLOSED on
    // close. Order of the contains() checks matters — see GameStateRepository.onRawLine.
    const val P_BARTER_OPEN = "COMPANION_BARTER_OPEN:"
    const val P_BARTER_ITEM = "COMPANION_BARTER_ITEM:"
    const val P_BARTER_END = "COMPANION_BARTER_END"
    const val P_BARTER_OFFER = "COMPANION_BARTER_OFFER:"
    const val P_BARTER_OFFER_ACCEPTED = "COMPANION_BARTER_OFFER_ACCEPTED"
    const val P_BARTER_OFFER_REJECTED = "COMPANION_BARTER_OFFER_REJECTED:"
    const val P_BARTER_CLOSED = "COMPANION_BARTER_CLOSED"
    // Merchant repair (GM_MerchantRepair). OPEN:<npcName> → PLAYER_GOLD:<n> → ITEM:
    // <name>|<sid>|<condition>|<maxCondition>|<cost>* → END, then CLOSED. None of these
    // tokens is a contains()-substring of another. PLAYER_GOLD is emitted within a repair
    // export (right after OPEN and again on each re-export after a repair).
    const val P_REPAIR_OPEN = "COMPANION_REPAIR_OPEN:"
    const val P_REPAIR_ITEM = "COMPANION_REPAIR_ITEM:"
    const val P_REPAIR_END = "COMPANION_REPAIR_END"
    const val P_REPAIR_CLOSED = "COMPANION_REPAIR_CLOSED"
    const val P_PLAYER_GOLD = "COMPANION_PLAYER_GOLD:"
    // Travel (native TravelWindow → bottom-screen overlay). OPEN{npcName} → PLAYER_GOLD →
    // DEST{index|name|cost|interior}* → END; CLOSED on close. None is a substring of another.
    const val P_TRAVEL_OPEN = "COMPANION_TRAVEL_OPEN:"
    const val P_TRAVEL_DEST = "COMPANION_TRAVEL_DEST:"
    const val P_TRAVEL_END = "COMPANION_TRAVEL_END"
    const val P_TRAVEL_CLOSED = "COMPANION_TRAVEL_CLOSED"
    // Rest/wait (GM_Rest). OPEN:<mode>|<dateString>|<warning> then CLOSED. Distinct tokens.
    const val P_SLEEP_OPEN = "COMPANION_SLEEP_OPEN:"
    const val P_SLEEP_CLOSED = "COMPANION_SLEEP_CLOSED"
    // Spell buying (GM_SpellBuying → bottom-screen overlay). OPEN:<npcName> → PLAYER_GOLD →
    // SPELL:<index|name|school|cost|known>* → END, then CLOSED. Same buffered pattern as repair.
    // OPEN also flips the boolean flow below (Vanilla-mode conversation step-aside). OPEN carries a
    // colon so its token can't match the SPELL/END lines; none is a substring of another.
    const val P_SPELLBUYING_OPEN = "COMPANION_SPELLBUYING_OPEN:"
    const val P_SPELLBUYING_SPELL = "COMPANION_SPELLBUYING_SPELL:"
    const val P_SPELLBUYING_END = "COMPANION_SPELLBUYING_END"
    const val P_SPELLBUYING_CLOSED = "COMPANION_SPELLBUYING_CLOSED"
    // Training (GM_Training → bottom-screen overlay). OPEN:<npcName> → PLAYER_GOLD →
    // SKILL:<index|name|currentLevel|cost|capped>* → END, then CLOSED. Same buffered pattern.
    const val P_TRAINING_OPEN = "COMPANION_TRAINING_OPEN:"
    const val P_TRAINING_SKILL = "COMPANION_TRAINING_SKILL:"
    const val P_TRAINING_END = "COMPANION_TRAINING_END"
    const val P_TRAINING_CLOSED = "COMPANION_TRAINING_CLOSED"
    // Bare open/closed signals (no payload) for the two remaining dialogue-service windows that push
    // over GM_Dialogue (no DS overlay yet). Drive a boolean session in GameStateRepository so the
    // conversation overlay steps aside while the native window is up in Vanilla mode.
    const val P_SPELLMAKING_OPEN = "COMPANION_SPELLMAKING_OPEN"
    const val P_SPELLMAKING_CLOSED = "COMPANION_SPELLMAKING_CLOSED"
    const val P_ENCHANTING_OPEN = "COMPANION_ENCHANTING_OPEN"
    const val P_ENCHANTING_CLOSED = "COMPANION_ENCHANTING_CLOSED"
    // Native persuasion modal (PersuasionDialog, Vanilla-persuasion mode). Same open/close-flag shape
    // as the other dialogue-service windows; drives the conversation step-aside + controller-nav gate.
    const val P_PERSUASION_OPEN = "COMPANION_PERSUASION_OPEN"
    const val P_PERSUASION_CLOSED = "COMPANION_PERSUASION_CLOSED"
    // Text-input focus (native, windowmanagerimp.cpp). OPEN carries the field's current
    // caption after the colon (may be empty); CLOSED has no payload. Drives the bottom-screen
    // Android-keyboard panel in EngineActivity via GameStateRepository.textInputRequest.
    const val P_TEXT_INPUT_OPEN = "COMPANION_TEXT_INPUT_OPEN:"
    const val P_TEXT_INPUT_CLOSED = "COMPANION_TEXT_INPUT_CLOSED"
    private const val P_EQUIPMENT = "COMPANION_EQUIPMENT:"
    private const val P_ACTIVE_EFFECTS = "COMPANION_ACTIVE_EFFECTS:"
    const val P_CHARACTER = "COMPANION_CHARACTER:"
    // Player standing (reputation/bounty/factions). Single small line, merged onto
    // the character in GameStateRepository and re-merged on each fresh CHARACTER.
    const val P_PLAYER_STATUS = "COMPANION_PLAYER_STATUS:"
    private const val P_TARGET = "COMPANION_TARGET:"
    const val P_INFO = "COMPANION_INFO:"
    const val P_JOURNAL_START = "COMPANION_JOURNAL_START:"
    const val P_JOURNAL_ENTRY = "COMPANION_JOURNAL_ENTRY:"
    const val P_JOURNAL_END = "COMPANION_JOURNAL_END:"
    // Finished-quest set, emitted natively (androidmain.cpp) on CMP:questStatus.
    // Streamed START/QUEST/END; ids are RefId.serializeText() → match JOURNAL_ENTRY's q.
    const val P_JOURNAL_FINISHED_START = "COMPANION_JOURNAL_FINISHED_START:"
    const val P_JOURNAL_FINISHED_QUEST = "COMPANION_JOURNAL_FINISHED_QUEST:"
    const val P_JOURNAL_FINISHED_END = "COMPANION_JOURNAL_FINISHED_END:"
    // Known dialogue topics with their seen responses, exported natively
    // (androidmain.cpp) on CMP:refreshTopics. Streamed one small line each so a
    // long topic list / long response never trips the engine's 4096-byte stdout
    // flush. TOPICS_START/_END bracket the whole batch; TOPIC_START/_ENTRY/_END
    // bracket each topic. The trailing "S" on TOPICS_* keeps the outer and inner
    // prefixes from colliding under contains() (TOPIC_START ⊄ TOPICS_START, etc.).
    const val P_TOPICS_START = "COMPANION_TOPICS_START:"
    const val P_TOPICS_END = "COMPANION_TOPICS_END"
    const val P_TOPIC_START = "COMPANION_TOPIC_START:"
    const val P_TOPIC_ENTRY = "COMPANION_TOPIC_ENTRY:"
    const val P_TOPIC_END = "COMPANION_TOPIC_END"
    // Streamed teleport-door markers for the companion minimap (one per line). Each ITEM is a
    // small JSON object {x,y,name}. Change-detected on the Lua side (only re-emits on cell change).
    const val P_DOORMARKER_START = "COMPANION_DOORMARKER_START:"
    const val P_DOORMARKER_ITEM = "COMPANION_DOORMARKER_ITEM:"
    const val P_DOORMARKER_END = "COMPANION_DOORMARKER_END:"
    // Streamed dialogue topic list (one topic per line so a long list never trips
    // the engine's 4096-byte stdout flush). Topic payloads are plain strings, not JSON.
    // CLOSED signals the conversation ended → clear the list.
    const val P_DIALOGUE_START = "COMPANION_DIALOGUE_START"
    const val P_DIALOGUE_TOPIC = "COMPANION_DIALOGUE_TOPIC:"
    const val P_DIALOGUE_END = "COMPANION_DIALOGUE_END"
    const val P_DIALOGUE_CLOSED = "COMPANION_DIALOGUE_CLOSED"
    // Current NPC disposition (0-100 int), single small line. Emitted natively from
    // DialogueWindow::updateDisposition() on dialogue open and after each persuasion
    // attempt (change-detected engine-side). Backs the conversation disposition bar.
    const val P_DIALOGUE_DISPOSITION = "COMPANION_DIALOGUE_DISPOSITION:"
    // Player gold (int), emitted natively alongside disposition (change-detected) so the
    // persuasion popup's Gold readout stays live after a bribe without an inventory re-export.
    const val P_DIALOGUE_GOLD = "COMPANION_DIALOGUE_GOLD:"
    // Service entries (Barter/Spells/Travel/...), streamed separately from topics.
    // The colon on _SERVICE disambiguates it from _SERVICES_START/_END under contains().
    const val P_DIALOGUE_SERVICES_START = "COMPANION_DIALOGUE_SERVICES_START"
    const val P_DIALOGUE_SERVICE = "COMPANION_DIALOGUE_SERVICE:"
    const val P_DIALOGUE_SERVICES_END = "COMPANION_DIALOGUE_SERVICES_END"
    // Persuasion availability flag (NPCs only), emitted inside the services block in place
    // of a routable service. Presence = the bottom-screen persuasion popup is offered.
    const val P_DIALOGUE_PERSUADE_AVAILABLE = "COMPANION_DIALOGUE_PERSUADE_AVAILABLE"
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
    const val P_CHARDETAIL_BIRTHSIGN = "COMPANION_CHARDETAIL_BIRTHSIGN:"
    const val P_CHARDETAIL_CLASS = "COMPANION_CHARDETAIL_CLASS:"
    const val P_CHARDETAIL_LEVEL = "COMPANION_CHARDETAIL_LEVEL:"
    const val P_CHARDETAIL_END = "COMPANION_CHARDETAIL_END"

    // Controller-navigation signals for the DS overlays (companion-controller-nav.patch). Emitted
    // natively while a DS overlay owns input (companionNavActive()); each is a payload-less line
    // (the trailing colon is just the log convention). P_NAV is the shared discriminator used by
    // GameStateRepository.onRawLine to route to parseNav(). None is a contains() substring of any
    // other (SLIDER_LEFT/RIGHT don't collide with LEFT/RIGHT since "SLIDER_" breaks the match).
    // Crime message (native, windowmanagerimp.cpp bounty-increase edge). Tail = the resolved
    // sCrimeMessage text; surfaced as a DS toast because the native message hides behind the
    // looting/barter panel windows.
    const val P_CRIME_MSG = "COMPANION_CRIME_MSG:"
    const val P_NAV = "COMPANION_NAV_"
    const val P_NAV_LEFT = "COMPANION_NAV_LEFT:"
    const val P_NAV_RIGHT = "COMPANION_NAV_RIGHT:"
    const val P_NAV_UP = "COMPANION_NAV_UP:"
    const val P_NAV_DOWN = "COMPANION_NAV_DOWN:"
    const val P_NAV_CONFIRM = "COMPANION_NAV_CONFIRM:"   // A button
    const val P_NAV_ACTION1 = "COMPANION_NAV_ACTION1:"   // X button
    const val P_NAV_ACTION2 = "COMPANION_NAV_ACTION2:"   // Y button
    const val P_NAV_L1 = "COMPANION_NAV_L1:"
    const val P_NAV_R1 = "COMPANION_NAV_R1:"
    const val P_NAV_L2 = "COMPANION_NAV_L2:"
    const val P_NAV_R2 = "COMPANION_NAV_R2:"
    const val P_NAV_SLIDER_LEFT = "COMPANION_NAV_SLIDER_LEFT:"
    const val P_NAV_SLIDER_RIGHT = "COMPANION_NAV_SLIDER_RIGHT:"
    const val P_NAV_SCROLL_UP = "COMPANION_NAV_SCROLL_UP:"       // right stick up (vertical lists)
    const val P_NAV_SCROLL_DOWN = "COMPANION_NAV_SCROLL_DOWN:"   // right stick down (vertical lists)
    const val P_NAV_SCROLL_LEFT = "COMPANION_NAV_SCROLL_LEFT:"   // right stick left (horizontal grids)
    const val P_NAV_SCROLL_RIGHT = "COMPANION_NAV_SCROLL_RIGHT:" // right stick right (horizontal grids)
    const val P_NAV_CANCEL = "COMPANION_NAV_CANCEL:"             // B while a quantity selector is open
    const val P_NAV_INFO = "COMPANION_NAV_INFO:"                 // R3 (right stick click) — item info popup

    /**
     * Maps a COMPANION_NAV_* line to a factory that builds the [NavEvent] once the repo stamps it
     * with a sequence number. Returns null if the line is not a recognised nav signal. SLIDER_* are
     * matched before LEFT/RIGHT purely defensively (they cannot actually collide under contains()).
     */
    fun parseNav(line: String): ((Long) -> NavEvent)? = when {
        line.contains(P_NAV_SCROLL_UP) -> { seq -> NavEvent.ScrollUp(seq) }
        line.contains(P_NAV_SCROLL_DOWN) -> { seq -> NavEvent.ScrollDown(seq) }
        line.contains(P_NAV_SCROLL_LEFT) -> { seq -> NavEvent.ScrollLeft(seq) }
        line.contains(P_NAV_SCROLL_RIGHT) -> { seq -> NavEvent.ScrollRight(seq) }
        line.contains(P_NAV_CANCEL) -> { seq -> NavEvent.Cancel(seq) }
        line.contains(P_NAV_INFO) -> { seq -> NavEvent.Info(seq) }
        line.contains(P_NAV_SLIDER_LEFT) -> { seq -> NavEvent.SliderLeft(seq) }
        line.contains(P_NAV_SLIDER_RIGHT) -> { seq -> NavEvent.SliderRight(seq) }
        line.contains(P_NAV_LEFT) -> { seq -> NavEvent.Left(seq) }
        line.contains(P_NAV_RIGHT) -> { seq -> NavEvent.Right(seq) }
        line.contains(P_NAV_UP) -> { seq -> NavEvent.Up(seq) }
        line.contains(P_NAV_DOWN) -> { seq -> NavEvent.Down(seq) }
        line.contains(P_NAV_CONFIRM) -> { seq -> NavEvent.Confirm(seq) }
        line.contains(P_NAV_ACTION2) -> { seq -> NavEvent.Action2(seq) }
        line.contains(P_NAV_ACTION1) -> { seq -> NavEvent.Action1(seq) }
        line.contains(P_NAV_L1) -> { seq -> NavEvent.L1(seq) }
        line.contains(P_NAV_R1) -> { seq -> NavEvent.R1(seq) }
        line.contains(P_NAV_L2) -> { seq -> NavEvent.L2(seq) }
        line.contains(P_NAV_R2) -> { seq -> NavEvent.R2(seq) }
        else -> null
    }

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
        return (0 until arr.length()).map { i -> spellFromJson(arr.getJSONObject(i)) }
    }

    /** Parses a single COMPANION_SPELLS_ITEM payload. Null if malformed. */
    fun parseSpellItem(payload: String): SpellEntry? = try {
        spellFromJson(JSONObject(payload))
    } catch (e: Exception) {
        Log.w(TAG, "Bad spell item: $payload", e); null
    }

    private fun spellFromJson(o: JSONObject): SpellEntry = SpellEntry(
        id = o.getString("id"),
        name = o.optString("name", ""),
        type = o.optString("type", "spell"),
        icon = o.optString("icon", ""),
        isItem = o.optBoolean("isItem", false),
        charge = o.optInt("charge", 0),
        maxCharge = o.optInt("maxCharge", 0),
        effect = o.optString("effect", ""),
        school = o.optString("school", ""),
        cost = o.optInt("cost", 0)
    )

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
            // gold + encumbrance are optional so pre-update Lua (no fields) keeps working.
            gold = o.optInt("gold", current.gold),
            encumbrance = if (o.has("encumbrance"))
                Dynamic(o.optDouble("encumbrance", 0.0).toFloat(), o.optDouble("capacity", 0.0).toFloat())
            else current.encumbrance,
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

    /** Optional enchantment object ("ench") shared by inventory/container/barter item payloads.
     *  Null when absent (non-enchanted item, or older engine). Backs the info popup's enchant section. */
    private fun parseEnchant(parent: JSONObject): ItemEnchant? {
        val e = parent.optJSONObject("ench") ?: return null
        val effects = mutableListOf<ItemEnchantEffect>()
        val arr = e.optJSONArray("effects")
        if (arr != null) for (i in 0 until arr.length()) {
            val eo = arr.optJSONObject(i) ?: continue
            effects.add(
                ItemEnchantEffect(
                    id = eo.optString("id", ""),
                    name = eo.optString("n", ""),
                    mag = eo.optString("mag", ""),
                    durationSecs = eo.optInt("dur", 0),
                    area = eo.optInt("area", 0),
                    icon = eo.optString("ic", ""),
                    harmful = eo.optBoolean("h", false)
                )
            )
        }
        return ItemEnchant(id = e.optString("id", ""), type = e.optString("type", ""), effects = effects)
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
            cond = if (o.has("cond")) o.optDouble("cond", 1.0).toFloat() else null,
            weight = o.optDouble("weight", 0.0).toFloat(),
            enchant = parseEnchant(o)
        )
    } catch (e: Exception) {
        null
    }

    /** Header from a COMPANION_CONTAINER_OPEN line (name + corpse/pickpocket flags + put-gate data).
     *  `capacity` = -1 (default) means no limit / not a container (older Lua omits it). */
    data class ContainerHeader(
        val name: String, val isCorpse: Boolean, val isPickpocket: Boolean,
        val isOrganic: Boolean = false, val capacity: Float = -1f
    )

    fun parseContainerOpen(json: String): ContainerHeader? = try {
        val o = JSONObject(json)
        ContainerHeader(
            o.optString("name", ""),
            o.optBoolean("isCorpse", false),
            o.optBoolean("pickpocket", false),
            o.optBoolean("organic", false),
            o.optDouble("capacity", -1.0).toFloat()
        )
    } catch (e: Exception) {
        null
    }

    /** The two vanilla put-restriction GMST strings (COMPANION_GMST, one-shot): `putOrganic` =
     *  sContentsMessage2, `putFull` = sContentsMessage3. Backs the loot put-blocked banner text. */
    data class PutMessages(val organic: String, val full: String)

    fun parseGmst(json: String): PutMessages? = try {
        val o = JSONObject(json)
        PutMessages(o.optString("putOrganic", ""), o.optString("putFull", ""))
    } catch (e: Exception) {
        null
    }

    /** Header from a COMPANION_BARTER_OPEN line (vendor name + both gold amounts). */
    data class BarterHeader(val vendorName: String, val vendorGold: Int, val playerGold: Int)

    fun parseBarterOpen(json: String): BarterHeader? = try {
        val o = JSONObject(json)
        BarterHeader(o.optString("vendor", ""), o.optInt("vendorGold", 0), o.optInt("playerGold", 0))
    } catch (e: Exception) {
        null
    }

    /** A single COMPANION_BARTER_ITEM. Optimistic selection fields default unselected. */
    fun parseBarterItem(json: String): BarterItem? = try {
        val o = JSONObject(json)
        val side = if (o.optString("side", "vendor") == "player") BarterSide.PLAYER else BarterSide.VENDOR
        BarterItem(
            id = o.optString("id", ""),
            stackId = o.optString("sid", ""),
            name = o.optString("name", ""),
            count = o.optInt("count", 1),
            value = o.optInt("value", 0),
            category = o.optString("cat", "misc"),
            icon = o.optString("icon", ""),
            side = side,
            worn = o.optBoolean("worn", false),
            // Absent (older engine) → default true (unrestricted).
            sellable = o.optBoolean("sellable", true),
            enchant = parseEnchant(o)
        )
    } catch (e: Exception) {
        null
    }

    /**
     * A single COMPANION_REPAIR_ITEM payload: name|sid|condition|maxCondition|cost.
     * Pipe-delimited (not JSON) — the engine sanitizes '|' out of the name, so a plain split
     * yields exactly 5 fields.
     */
    fun parseRepairItem(payload: String): RepairItem? = try {
        val parts = payload.split('|')
        if (parts.size < 5) null
        else RepairItem(
            name = parts[0],
            sid = parts[1],
            condition = parts[2].trim().toInt(),
            maxCondition = parts[3].trim().toInt(),
            cost = parts[4].trim().toInt()
        )
    } catch (e: Exception) {
        null
    }

    /**
     * A single COMPANION_TRAVEL_DEST payload: index|name|cost|interior(1/0).
     * Pipe-delimited (not JSON) — the engine sanitizes '|' out of the name, so a plain split
     * yields exactly 4 fields.
     */
    fun parseTravelDest(payload: String): TravelDest? = try {
        val parts = payload.split('|')
        if (parts.size < 4) null
        else TravelDest(
            index = parts[0].trim().toInt(),
            name = parts[1],
            cost = parts[2].trim().toInt(),
            interior = parts[3].trim() == "1"
        )
    } catch (e: Exception) {
        null
    }

    /**
     * A COMPANION_SLEEP_OPEN payload: <mode>|<dateString>|<warning>|<untilHealed>|<hoursToHeal>.
     * Pipe-delimited (the engine sanitizes '|' out of each field). The last two fields back the
     * "Rest Until Healed" button; getOrElse defaults keep an older 3-field line (no button) valid.
     */
    fun parseSleepOpen(payload: String): SleepSession? = try {
        val parts = payload.split('|')
        if (parts.isEmpty()) null
        else SleepSession(
            mode = if (parts[0].trim() == "rest") SleepMode.REST else SleepMode.WAIT,
            dateString = parts.getOrElse(1) { "" }.trim(),
            warning = parts.getOrElse(2) { "" }.trim(),
            untilHealedAvailable = parts.getOrElse(3) { "0" }.trim() == "1",
            hoursToHeal = parts.getOrElse(4) { "0" }.trim().toIntOrNull() ?: 0
        )
    } catch (e: Exception) {
        null
    }

    /**
     * A single COMPANION_TRAINING_SKILL payload: index|skillName|currentLevel|cost|capped(1/0).
     * Pipe-delimited (the engine sanitizes '|' out of the name) → exactly 5 fields.
     */
    fun parseTrainingSkill(payload: String): TrainingSkill? = try {
        val parts = payload.split('|')
        if (parts.size < 5) null
        else TrainingSkill(
            index = parts[0].trim().toInt(),
            skillName = parts[1],
            currentLevel = parts[2].trim().toInt(),
            cost = parts[3].trim().toInt(),
            capped = parts[4].trim() == "1"
        )
    } catch (e: Exception) {
        null
    }

    /**
     * A single COMPANION_SPELLBUYING_SPELL payload: index|spellName|school|cost|known(1/0)|id.
     * Pipe-delimited (the engine sanitizes '|' out of name/school) → 5 or 6 fields. The trailing
     * spell record id (field 6) was added for the info popup; older 5-field lines still parse
     * (id defaults to "").
     */
    fun parseSpellForSale(payload: String): SpellForSale? = try {
        val parts = payload.split('|')
        if (parts.size < 5) null
        else SpellForSale(
            index = parts[0].trim().toInt(),
            spellName = parts[1],
            school = parts[2],
            cost = parts[3].trim().toInt(),
            known = parts[4].trim() == "1",
            id = parts.getOrNull(5) ?: ""
        )
    } catch (e: Exception) {
        null
    }

    /** The authoritative running offer from COMPANION_BARTER_OFFER. */
    data class BarterOffer(
        val merchantOffer: Int, val balance: Int, val extraGold: Int,
        val vendorGold: Int, val playerGold: Int
    )

    fun parseBarterOffer(json: String): BarterOffer? = try {
        val o = JSONObject(json)
        BarterOffer(
            o.optInt("merchantOffer", 0), o.optInt("balance", 0), o.optInt("extraGold", 0),
            o.optInt("vendorGold", 0), o.optInt("playerGold", 0)
        )
    } catch (e: Exception) {
        null
    }

    /** The reason field from COMPANION_BARTER_OFFER_REJECTED (e.g. "haggle"). */
    fun parseBarterRejectReason(json: String): String = try {
        JSONObject(json).optString("reason", "")
    } catch (e: Exception) {
        ""
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
                icon = o.optString("icon", ""),
                magnitude = o.optInt("magnitude", 0),
                source = o.optString("source", "")
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

    /**
     * Parses a COMPANION_TOPIC_ENTRY payload ("<actorName>|<text>"). actorName is
     * the first field (never contains a pipe) and may be empty; everything after
     * the first pipe is the response text. Null only if the pipe is missing.
     */
    fun parseTopicEntry(payload: String): TopicEntry? {
        val sep = payload.indexOf('|')
        if (sep < 0) return null
        return TopicEntry(
            actorName = payload.substring(0, sep),
            text = payload.substring(sep + 1)
        )
    }

    /** One COMPANION_DOORMARKER_ITEM payload → a DoorMarker. Null if malformed. */
    fun parseDoorMarker(json: String): DoorMarker? = try {
        val o = JSONObject(json)
        DoorMarker(
            worldX = o.getDouble("x").toFloat(),
            worldY = o.getDouble("y").toFloat(),
            name = o.optString("name", "")
        )
    } catch (e: Exception) {
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

    /** Parsed COMPANION_PLAYER_STATUS payload; merged onto CharacterInfo by the repo. */
    data class PlayerStatus(val reputation: Int, val bounty: Int, val factions: List<FactionMembership>)

    fun parsePlayerStatus(json: String): PlayerStatus? = try {
        val o = JSONObject(json)
        val arr = o.optJSONArray("factions")
        val factions = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            FactionMembership(
                id = f.optString("id", ""),
                name = f.optString("name", ""),
                rank = f.optInt("rank", 0),
                rankName = f.optString("rankName", "")
            )
        }
        PlayerStatus(o.optInt("reputation", 0), o.optInt("bounty", 0), factions)
    } catch (e: Exception) {
        null
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

    fun parseDetailBirthSign(json: String): DetailBirthSign? = try {
        val o = JSONObject(json)
        DetailBirthSign(o.optString("desc", ""), strArray(o, "spells"), o.optString("texture", ""))
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
    data class DetailBirthSign(val desc: String, val spells: List<String>, val texture: String)
    data class DetailClass(
        val desc: String, val spec: String,
        val attrs: List<String>, val major: List<String>, val minor: List<String>
    )
}
