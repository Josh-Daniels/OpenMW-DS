package org.openmw.companion

/**
 * Typed representation of everything the Lua mod exports to openmw.log.
 * One immutable snapshot of the player's current state.
 */

data class Dynamic(val current: Float, val max: Float) {
    /** 0f..1f fill ratio, guarded against divide-by-zero. */
    val ratio: Float get() = if (max > 0f) (current / max).coerceIn(0f, 1f) else 0f
}

data class Vec3(val x: Float, val y: Float, val z: Float)

data class InventoryItem(
    val id: String,
    /** Per-stack instance identifier (OpenMW item.id); empty if unavailable. */
    val stackId: String = "",
    val name: String = "",
    val count: Int = 1,
    val category: String = "misc",
    val icon: String = "",
    /** Pre-formatted stat value for display (e.g. "2-15", "30"); "" = no stat. */
    val statVal: String = "",
    /** Pre-formatted stat label (e.g. "SLASH", "ARMOR"); "" = no stat. */
    val statKey: String = "",
    /** Condition ratio 0..1; null = item has no durability (no cond bar). */
    val cond: Float? = null,
    /** Enchantment (id + type label + effects) for the info popup; null = not enchanted. */
    val enchant: ItemEnchant? = null
)

/** One enchantment effect line for the item info popup (from the streamed item exports). */
data class ItemEnchantEffect(
    val id: String = "", val name: String = "", val mag: String = "",
    val durationSecs: Int = 0, val area: Int = 0,
    val icon: String = "", val harmful: Boolean = false
)

/** An item's enchantment: type label (Cast Once / Cast on Strike / Cast on Use / Constant Effect)
 *  + its effect list. Carried on InventoryItem/BarterItem so the info popup renders it instantly. */
data class ItemEnchant(
    val id: String = "", val type: String = "",
    val effects: List<ItemEnchantEffect> = emptyList()
)

data class SpellEntry(
    val id: String,
    val name: String = "",
    val type: String = "spell",
    val icon: String = "",   // VFS icon path, empty = no icon
    // Cast-on-use enchanted item (ring/amulet/clothing/weapon), NOT a learned
    // spell/power/scroll. Emitted with type "scroll" so it renders in the magic
    // list, but split into its own "Enchanted Items" section via this flag.
    val isItem: Boolean = false,
    val charge: Int = 0,        // current enchantment charge
    val maxCharge: Int = 0      // enchantment charge capacity (0 = not an item)
)

data class ActiveEffect(
    val name: String,
    val harmful: Boolean,
    val icon: String = "",  // VFS icon path, empty = no icon
    /** Rounded effect magnitude for display; 0 = unknown/not applicable. */
    val magnitude: Int = 0,
    /** Display name of the source spell/ability/item (e.g. "Warwyrd"); "" if unknown. */
    val source: String = ""
)

/** One effect row in an item/spell info popup. */
data class InfoEffect(val text: String, val harmful: Boolean)

/**
 * Transient detail popup contents, produced on demand by a CMP:info request.
 * Not part of the live GameState — held in its own StateFlow. `rows` is an
 * ordered list of (label, value) pairs; `effects` are formatted effect lines.
 */
data class ItemInfo(
    val name: String,
    val rows: List<Pair<String, String>>,
    val effects: List<InfoEffect>
)

/** Current combat/crosshair target, shown as a name + health bar on the HUD. */
data class TargetInfo(val name: String, val health: Dynamic)

/**
 * One NPC utterance in the bottom-screen dialogue history (greeting or a topic
 * response). Transient — held in its own StateFlow, not part of GameState.
 * `topic` is the bold sub-header (empty for the greeting); `text` keeps its
 * newlines; `hyperlinks` are the display phrases the engine flagged as tappable
 * topic links within `text`.
 */
data class DialogueSay(
    val topic: String = "",
    val text: String,
    val hyperlinks: List<String> = emptyList(),
    /** true = an in-dialogue system message box (e.g. gold removed), not NPC speech. */
    val isMessage: Boolean = false
)

/** A question/answer choice offered mid-dialogue (shown instead of topics while active). */
data class DialogueChoice(val text: String, val id: Int)

/**
 * An open looting/pickpocketing session (container, corpse, or living NPC).
 * Transient — held in its own StateFlow, not part of GameState; null = no
 * container open.
 *
 * `items` are the container's contents. They parse with the exact same JSON
 * shape as the player inventory, so we reuse [InventoryItem] rather than a
 * parallel type — there is no "side" field because the two columns come from
 * two lists: left = GameState.inventory (the player), right = this.items (the
 * container). `isCorpse` toggles the "Dispose of Corpse" button.
 */
data class ContainerSession(
    val containerName: String,
    val isCorpse: Boolean,
    /** True = a living NPC (pickpocket); items may be hidden by the Sneak roll. Drives
     *  the "Nothing you can lift" empty state. False for corpses and plain chests. */
    val isPickpocket: Boolean = false,
    val items: List<InventoryItem> = emptyList(),
    /** True while a session is active; the overlay AND-gates this with Hide UI. */
    val isVisible: Boolean = true
)

/** Which side of a barter transaction an item belongs to. */
enum class BarterSide { PLAYER, VENDOR }

/**
 * One item in a barter session. `value` is the merchant's actual per-unit barter
 * price (mercantile-/disposition-adjusted, from the engine) — NOT the base value —
 * so the displayed net matches what the merchant charges. `isSelected`/`selectedCount`
 * are Kotlin-owned optimistic UI state (the sim pauses during barter, so selection is
 * tracked locally and reconciled against the authoritative COMPANION_BARTER_OFFER).
 */
data class BarterItem(
    val id: String,
    val stackId: String = "",
    val name: String = "",
    val count: Int = 1,
    val value: Int = 0,
    val category: String = "misc",
    val icon: String = "",
    val side: BarterSide = BarterSide.VENDOR,
    /** Currently equipped (player side only); vendor items are always false. */
    val worn: Boolean = false,
    /** Whether the merchant will buy this item (player side: canSell vs. the merchant's services;
     *  vendor items are always true). Player items with sellable=false can't be offered. */
    val sellable: Boolean = true,
    val isSelected: Boolean = false,
    val selectedCount: Int = 0,
    /** Enchantment (id + type label + effects) for the info popup; null = not enchanted. */
    val enchant: ItemEnchant? = null
)

/**
 * An open barter session (the native GM_Barter TradeWindow, mirrored to the bottom
 * screen). Transient — held in its own StateFlow, not part of GameState; null = not
 * bartering.
 *
 * `balance` is the engine's authoritative running offer (= merchantOffer + extraGoldOffer):
 * positive = the player receives gold, negative = the player pays. It is what haggle()
 * ultimately compares, so it — not the Kotlin-computed [netTotal] — is the real offer.
 * [netTotal] is a per-item estimate for instant feedback during the optimistic-selection
 * window before the engine re-exports COMPANION_BARTER_OFFER.
 */
data class BarterSession(
    val vendorName: String,
    val vendorGold: Int,
    val playerGold: Int,
    val playerItems: List<BarterItem> = emptyList(),
    val vendorItems: List<BarterItem> = emptyList(),
    /** Engine fair-price offer for the currently-staged items (signed like [balance]). */
    val merchantOffer: Int = 0,
    /** Engine authoritative net offer (merchantOffer + extraGoldOffer). */
    val balance: Int = 0,
    /** Player's manual gold adjustment (the +/- gold row); starts at 0. */
    val extraGoldOffer: Int = 0,
    val isVisible: Boolean = true
) {
    /** Value the player is giving up (selected player items). */
    val playerItemsValue: Int
        get() = playerItems.filter { it.isSelected }.sumOf { it.value * it.selectedCount }

    /** Value the player is receiving (selected vendor items). */
    val vendorItemsValue: Int
        get() = vendorItems.filter { it.isSelected }.sumOf { it.value * it.selectedCount }

    /** Kotlin-side net estimate (positive = player receives gold). See [balance] for the
     *  authoritative value. */
    val netTotal: Int get() = playerItemsValue - vendorItemsValue + extraGoldOffer
}

/** Outcome of a submitted barter offer (transient — drives the rejection alert / close). */
sealed interface BarterResult {
    data class Rejected(val reason: String) : BarterResult
    data object Accepted : BarterResult
}

/**
 * One damaged, repairable item in the merchant-repair overlay (COMPANION_REPAIR_ITEM).
 * [sid] is the item's ordinal index in the engine's exported damaged list — the handle
 * passed back to [CompanionActions.repairItem] (stable because GM_MerchantRepair pauses the
 * sim; the list is re-exported with fresh indices after each repair). [condition] /
 * [maxCondition] are the item's current / max durability; [cost] is the merchant's price.
 */
data class RepairItem(
    val name: String,
    val sid: String,
    val condition: Int,
    val maxCondition: Int,
    val cost: Int
) {
    /** 0..1 durability ratio for the condition bar. */
    val ratio: Float get() = if (maxCondition > 0) (condition.toFloat() / maxCondition).coerceIn(0f, 1f) else 0f
}

/**
 * An open merchant-repair session (the native GM_MerchantRepair window, mirrored to the
 * bottom screen). Transient — its own StateFlow, not part of GameState; null = not repairing.
 * Driven entirely by COMPANION_REPAIR_* + COMPANION_PLAYER_GOLD from the engine.
 */
data class RepairSession(
    val npcName: String,
    val playerGold: Int,
    val items: List<RepairItem> = emptyList(),
    val isVisible: Boolean = true
) {
    /** Total cost to repair every listed item (the "Repair All (Xg)" figure). */
    val totalCost: Int get() = items.sumOf { it.cost }
}

/**
 * One travel destination in a travel session. [index] is the destination's ordinal position in the
 * native TravelWindow (a stable handle used by CMP:travel_go — GM_Travel pauses the sim). [cost] is
 * the merchant-adjusted, follower-inclusive price the engine computed. [interior] flags a
 * Mages-Guild (interior) destination vs. a silt-strider/boat (exterior) one.
 */
data class TravelDest(
    val index: Int,
    val name: String,
    val cost: Int,
    val interior: Boolean
)

/**
 * An open travel session (the native GM_Travel window, mirrored to the bottom screen). Transient —
 * its own StateFlow, not part of GameState; null = not travelling. Driven entirely by
 * COMPANION_TRAVEL_* + COMPANION_PLAYER_GOLD from the engine.
 */
data class TravelSession(
    val npcName: String,
    val playerGold: Int,
    val destinations: List<TravelDest> = emptyList(),
    val isVisible: Boolean = true
)

/** REST = sleeping is allowed here (heals, can level up); WAIT = time-pass only. */
enum class SleepMode { REST, WAIT }

/**
 * An open rest/wait session (the native GM_Rest WaitDialog, mirrored to the bottom screen).
 * Transient — its own StateFlow, not part of GameState; null = not resting/waiting. Driven by
 * COMPANION_SLEEP_* from the engine. [dateString] is the already-resolved in-game date/time
 * (e.g. "24 Last Seed (Day 9) 10 a.m."); [warning] is the illegal-rest message shown only in
 * WAIT mode ("" otherwise). Confirming a rest/wait dismisses this overlay — the engine runs
 * the actual fade + time advance on the top screen.
 */
data class SleepSession(
    val mode: SleepMode,
    val dateString: String,
    val warning: String = "",
    val isVisible: Boolean = true
)

/**
 * One trainable skill in a training session. [index] is the skill's ordinal in the trainer's
 * best-3 list (a stable handle used by CMP:training_train — GM_Training pauses the sim).
 * [currentLevel] is the player's current base skill; [cost] the merchant-adjusted price.
 * [capped] = the player is already at/above the trainer's skill OR at/above the skill's governing
 * attribute — the native window would reject training it, so the row is greyed and non-tappable.
 */
data class TrainingSkill(
    val index: Int,
    val skillName: String,
    val currentLevel: Int,
    val cost: Int,
    val capped: Boolean
)

/**
 * An open training session (the native GM_Training window, mirrored to the bottom screen).
 * Transient — its own StateFlow, not part of GameState; null = not training. Driven entirely by
 * COMPANION_TRAINING_* + COMPANION_PLAYER_GOLD from the engine. [isTraining] is set true when a
 * train command is sent and drives the in-progress "Training…" popup; the engine runs the actual
 * 2-hour fade + time advance on the top screen, then emits COMPANION_TRAINING_CLOSED.
 */
data class TrainingSession(
    val npcName: String,
    val playerGold: Int,
    val skills: List<TrainingSkill> = emptyList(),
    val isTraining: Boolean = false
)

/**
 * One spell for sale in a spell-buying session. [index] is the spell's ordinal in the exported
 * list (a stable handle used by CMP:spellbuying_buy — GM_SpellBuying pauses the sim; a purchase
 * only flips [known], keeping the slot). [school] is the spell's effective governing-skill name
 * (e.g. "Destruction"). [cost] is the merchant-adjusted price. [known] = the player already knows
 * it, so the row is greyed ("Already known") and non-tappable.
 */
data class SpellForSale(
    val index: Int,
    val spellName: String,
    val school: String,
    val cost: Int,
    val known: Boolean
)

/**
 * An open spell-buying session (the native GM_SpellBuying window, mirrored to the bottom screen).
 * Transient — its own StateFlow, not part of GameState; null = not buying. Driven entirely by
 * COMPANION_SPELLBUYING_* + COMPANION_PLAYER_GOLD from the engine.
 */
data class SpellBuyingSession(
    val npcName: String,
    val playerGold: Int,
    val spells: List<SpellForSale> = emptyList()
)

data class AttributeStat(
    val id: String, val name: String, val current: Float, val base: Float,
    /** In-game description (from the streamed CHARDETAIL batch); "" until it lands. */
    val desc: String = "",
    /** Display names of the skills this attribute governs. */
    val governedSkills: List<String> = emptyList(),
    /** VFS icon path from core.stats.Attribute.records[id].icon; "" when none. */
    val icon: String = ""
)

/** category: "major", "minor", or "misc" per the player's class. */
data class SkillStat(
    val id: String, val name: String, val value: Float, val category: String,
    val desc: String = "",
    /** Display name of the governing attribute (e.g. "Agility"). */
    val governingAttribute: String = "",
    /** "Combat", "Magic", or "Stealth". */
    val specialization: String = "",
    /** VFS icon path from core.stats.Skill.records[id].icon; "" when none. */
    val icon: String = "",
    /** [0-1] progress toward the next skill increase (types.NPC SkillStat.progress). */
    val progress: Float = 0f
)

/**
 * One faction the player belongs to. `rank` is the (1-based) rank index;
 * `rankName` is the localized rank title (e.g. "Operative"), "" if unknown.
 */
data class FactionMembership(
    val id: String,
    val name: String,
    val rank: Int,
    val rankName: String = ""
)

data class CharacterInfo(
    val name: String = "",
    val race: String = "",
    val className: String = "",
    val birthSign: String = "",
    val level: Int = 0,
    val attributes: List<AttributeStat> = emptyList(),
    val skills: List<SkillStat> = emptyList(),
    // --- Player standing (streamed separately via COMPANION_PLAYER_STATUS,
    // merged in by GameStateRepository the same way as CHARDETAIL). ---
    val reputation: Int = 0,
    /** Crime bounty; > 0 means wanted by guards. */
    val bounty: Int = 0,
    val factions: List<FactionMembership> = emptyList(),
    // --- Description / metadata for the tappable Stats popups (streamed
    // separately via COMPANION_CHARDETAIL_*, merged in by GameStateRepository). ---
    val healthDesc: String = "",
    val magickaDesc: String = "",
    val fatigueDesc: String = "",
    val raceDesc: String = "",
    /** e.g. "Alchemy +5". */
    val raceSkillBonuses: List<String> = emptyList(),
    /** Inherent racial ability/spell display names. */
    val raceAbilities: List<String> = emptyList(),
    val birthSignDesc: String = "",
    /** Birthsign inherent power/ability display names. */
    val birthSignSpells: List<String> = emptyList(),
    /** VFS path to the birthsign portrait art (e.g. textures/tx_bm_apprentice.dds). */
    val birthSignTexture: String = "",
    val classDesc: String = "",
    val classSpecialization: String = "",
    val classFavoredAttributes: List<String> = emptyList(),
    val classMajorSkills: List<String> = emptyList(),
    val classMinorSkills: List<String> = emptyList(),
    /** Skill-increase count toward the next level, and the total needed. */
    val levelProgress: Int = 0,
    val levelTotal: Int = 0
)

/**
 * One seen response for a known dialogue topic. `actorName` is who said it (may
 * be empty). Transient — held in its own StateFlow, not part of GameState.
 */
data class TopicEntry(
    val actorName: String,
    val text: String
)

/** A known dialogue topic with all its seen response entries (received order). */
data class TopicInfo(
    val name: String,
    val entries: List<TopicEntry>
)

data class JournalEntry(
    val questId: String,
    val questName: String = "",  // display name from core.dialogue; empty = fall back to prettified ID
    val text: String,
    val day: Int,
    val month: Int,
    val dayOfMonth: Int
)

/**
 * A one-shot controller-navigation signal for the DS overlays, produced natively
 * (companion-controller-nav.patch → COMPANION_NAV_* log lines) while a DS overlay owns input
 * (companionNavActive()). Exposed as GameStateRepository.navEvent: StateFlow<NavEvent?>.
 *
 * [seq] is a monotonic counter stamped by the repo so two identical presses in a row (e.g. Down
 * then Down) are DISTINCT StateFlow values and both re-emit — otherwise StateFlow would dedupe the
 * second equal value and the consumer would miss it. Consumers react to every change; there is no
 * need to clear the flow between presses.
 *
 * Semantic mapping (see the controller scheme): Confirm = A, Action1 = X, R1/L2/R2 the shoulders/
 * triggers, SliderLeft/SliderRight the left-stick nudges. B is deliberately absent — it is handled
 * by companion-b-button-choice-fix.patch, never intercepted here.
 */
sealed class NavEvent {
    abstract val seq: Long
    data class Up(override val seq: Long) : NavEvent()
    data class Down(override val seq: Long) : NavEvent()
    data class Left(override val seq: Long) : NavEvent()
    data class Right(override val seq: Long) : NavEvent()
    data class Confirm(override val seq: Long) : NavEvent()      // A button
    data class Action1(override val seq: Long) : NavEvent()      // X button
    data class Action2(override val seq: Long) : NavEvent()      // Y button (looting: Dispose of Corpse)
    data class L1(override val seq: Long) : NavEvent()           // left shoulder (barter/looting: prev category)
    data class R1(override val seq: Long) : NavEvent()
    data class L2(override val seq: Long) : NavEvent()
    data class R2(override val seq: Long) : NavEvent()
    data class SliderLeft(override val seq: Long) : NavEvent()
    data class SliderRight(override val seq: Long) : NavEvent()
    data class ScrollUp(override val seq: Long) : NavEvent()      // right stick up (vertical lists)
    data class ScrollDown(override val seq: Long) : NavEvent()    // right stick down (vertical lists)
    data class ScrollLeft(override val seq: Long) : NavEvent()    // right stick left (horizontal grids)
    data class ScrollRight(override val seq: Long) : NavEvent()   // right stick right (horizontal grids)
    data class Cancel(override val seq: Long) : NavEvent()        // B while a quantity selector is open
    data class Info(override val seq: Long) : NavEvent()          // R3 (right stick click) — item info popup
}

data class GameState(
    val health: Dynamic = Dynamic(0f, 0f),
    val magicka: Dynamic = Dynamic(0f, 0f),
    val fatigue: Dynamic = Dynamic(0f, 0f),
    val cell: String = "—",
    val pos: Vec3 = Vec3(0f, 0f, 0f),
    /** True when the player is in an exterior cell. */
    val cellIsExterior: Boolean = false,
    /** Exterior cell grid X coordinate (meaningful only when cellIsExterior). */
    val cellGridX: Int = 0,
    /** Exterior cell grid Y coordinate (meaningful only when cellIsExterior). */
    val cellGridY: Int = 0,
    /** Player yaw in radians (Z-axis Euler angle from self.rotation.z). */
    val rotZ: Float = 0f,
    /** Player gold (count of Gold_001), from COMPANION_STATS. */
    val gold: Int = 0,
    /** Player encumbrance: current = carried weight, max = carry capacity. */
    val encumbrance: Dynamic = Dynamic(0f, 0f),
    val spells: List<SpellEntry> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val equipment: Map<String, String> = emptyMap(),
    val selectedSpell: String? = null,
    val activeEffects: List<ActiveEffect> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val character: CharacterInfo = CharacterInfo(),
    /** Current combat target under the crosshair; null = no target. */
    val target: TargetInfo? = null,
    /** Wall-clock time we last parsed a STATS line; 0 = no data yet. */
    val lastUpdateMs: Long = 0L
) {
    val hasData: Boolean get() = lastUpdateMs > 0L
}
