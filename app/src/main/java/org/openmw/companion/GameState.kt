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
    /** Per-unit weight (record weight; gold = 0). Backs the loot overlay's optimistic
     *  encumbrance delta. Defaults 0 so older lines without the field are inert. */
    val weight: Float = 0f,
    /** Base record value in gold. Backs the inventory tab's Price sort ONLY — it is the base value,
     *  not a merchant-adjusted price, and never feeds a transaction. Defaults 0 so older Lua without
     *  the field still parses (those items simply sort as worthless). */
    val value: Int = 0,
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
    val maxCharge: Int = 0,     // enchantment charge capacity (0 = not an item)
    // Extra display stats for the spell row (added July 2026). effect = first-effect name (all
    // types); school + cost = governing school + magicka cost (learned spells/powers only —
    // scrolls/enchanted items leave them empty/0).
    val effect: String = "",
    val school: String = "",
    val cost: Int = 0
)

data class ActiveEffect(
    val name: String,
    val harmful: Boolean,
    val icon: String = "",  // VFS icon path, empty = no icon
    /** Rounded effect magnitude for display; 0 = unknown/not applicable. */
    val magnitude: Int = 0,
    /** Display name of the source spell/ability/item (e.g. "Warwyrd"); "" if unknown. */
    val source: String = "",
    /**
     * Remaining time in seconds, already QUANTIZED by the exporter to what the UI draws — whole
     * seconds below a minute, whole minutes (a multiple of 60) above. See `quantizeRemaining` in
     * companion.lua for why the quantization happens there rather than here.
     *
     * **null means this effect has no timer**, and that is the engine's own determination, not a
     * heuristic: the exporter omits the field exactly when `ActiveSpellEffect.durationLeft` is nil,
     * i.e. for abilities, diseases, curses and constant-effect worn enchantments (`mDuration < 0`)
     * and for NoDuration effect records. Treat null as "permanent / not applicable" and render no
     * timer; never substitute 0, which is a real value meaning "expiring this second".
     *
     * NOT to be extrapolated locally against a wall clock. The underlying counter is decremented by
     * the simulation frame dt, which stops while the game is paused — and the companion is used
     * over paused screens constantly (looting, dialogue, barter, the pause menu). A local countdown
     * would drift for as long as any of those stays open and then snap back on the next export.
     */
    val remainingSeconds: Int? = null
)

/**
 * The ammo stack currently loaded in the ammunition slot, as reported by `exportAmmo`.
 *
 * [count] is that ONE stack's count — deliberately not a total across every arrow the player owns,
 * since only the stack in the slot is what the next shot draws from.
 *
 * The exporter sends this only when the weapon/ammo pairing is one the engine would actually let
 * you fire, so its mere presence is the "show a counter" signal — the UI does not (and must not)
 * re-derive which weapons use ammo. Absence covers melee, thrown, unarmed, an empty ammo slot and
 * a weapon/ammo mismatch alike.
 */
data class EquippedAmmo(val id: String, val count: Int)

/**
 * Enchantment charge of the item in the equipped-item (weapon) slot, backing the HUD corner
 * icon's charge meter. Null whenever no meter should be drawn — the exporter decides that
 * (only Cast on Strike / Cast on Use enchantments actually consume charge), so do not re-derive
 * it here; see COMPANION_EQUIPPED_CHARGE.
 */
data class EquippedCharge(val charge: Int, val maxCharge: Int) {
    /** 0f..1f fill ratio, guarded against divide-by-zero. */
    val ratio: Float get() = if (maxCharge > 0) (charge.toFloat() / maxCharge).coerceIn(0f, 1f) else 0f
}

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
    val effects: List<InfoEffect>,
    /** Enchantment charge for the popup's charge bar, mirroring vanilla's "Charges" bar.
     *  `maxCharge == 0` means no bar — which is the case for an unenchanted item AND for the two
     *  enchantment types that never drain (Cast Once, Constant Effect), exactly as vanilla. A
     *  snapshot taken when the popup was opened; it does not track live. */
    val charge: Int = 0,
    val maxCharge: Int = 0
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
    val isVisible: Boolean = true,
    /** Vanilla container-put restrictions (mirror `ContainerItemModel::onDropItem`), gating only
     *  the PUT direction and only for genuine ESM::Container targets. `isOrganic` = the record's
     *  Organic flag (no items may be placed). `capacity` = the container's weight limit
     *  (`mBase.mWeight`); **-1 = no limit / not a container** (corpse/NPC — puts unrestricted).
     *  A put is blocked when organic, or capacity in [0..) and current-contents-weight +
     *  incoming-weight would exceed it. Added Jul 2026. */
    val isOrganic: Boolean = false,
    val capacity: Float = -1f
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
    /** Per-unit record weight (native `Class::getWeight`), matching [InventoryItem.weight]. Backs
     *  the Weight sort chip; 0 for an older engine that predates the field on the barter export. */
    val weight: Float = 0f,
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
    // "Rest Until Healed" affordance, straight from the engine (COMPANION_SLEEP_OPEN). [untilHealedAvailable]
    // mirrors vanilla's mUntilHealedButton visibility (canRest && !full — REST mode with health OR magicka
    // below max); [hoursToHeal] is getHoursToRest() verbatim, replayed via CMP:sleep <hours>. Both default
    // off/0 so an older 3-field line (no version skew in practice) degrades to "no button".
    val untilHealedAvailable: Boolean = false,
    val hoursToHeal: Int = 0,
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
/**
 * One attribute row on the DS level-up screen.
 *
 * [mult] is ALREADY clamped to what is reachable (`100 - base`), matching what the native window
 * puts in its caption — an attribute at 98 shows x2 even when the raw GMST table says x5. A value
 * of 1 or less means NO caption is drawn, again as vanilla. [count] is the raw per-attribute skill
 * increase tally that produced it.
 *
 * [skills] are the skills that GOVERN this attribute — deliberately not a per-skill contribution
 * breakdown, because the engine stores only [count] and naming the actual contributors would be
 * invented (see the level-up notes in CLAUDE.md).
 */
data class LevelUpAttribute(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "",
    val base: Int = 0,
    val count: Int = 0,
    val mult: Int = 1,
    val disabled: Boolean = false,
    val skills: List<String> = emptyList()
) {
    /** Value after spending a coin here, clamped exactly as the commit clamps it. */
    val projected: Int get() = (base + mult).coerceAtMost(100)
}

/**
 * A live level-up screen.
 *
 * [coinCount] and [selected] both come from the NATIVE window (COMPANION_LEVELUP_SELECTION), not
 * from anything derived here. That is deliberate: coinCount is `min(3, attributes below 100)` and
 * genuinely drops below 3 late-game, and it is the same number the native gate compares against, so
 * taking it from there removes any chance of the DS gate and the real gate disagreeing.
 * [selected] is echoed after every pick, so the toggle / replace-last-at-quota semantics are the
 * native ones rather than a re-implementation.
 */
data class LevelUpSession(
    val level: Int = 0,
    val flavour: String = "",
    /** Level-up image NAME (e.g. "warrior"); the VFS path is textures/levelup/<name>.dds. */
    val image: String = "",
    val attributes: List<LevelUpAttribute> = emptyList(),
    val coinCount: Int = 3,
    val selected: List<String> = emptyList()
) {
    /** The Done gate: exactly the native condition (`mSpentAttributes.size() < mCoinCount`). */
    val canConfirm: Boolean get() = selected.size >= coinCount

    /**
     * Is this attribute currently picked?
     *
     * Compared CASE-INSENSITIVELY on purpose. Attribute ids reach the two sides through different
     * accessors — Lua's `Attribute.record.id` is `RefId::serializeText()`, which LOWERCASES a
     * StringRefId, while the native echo originally used `StringRefId::getValue()`, which keeps the
     * ESM's own casing ("Strength"). A plain `==` silently never matched, so picks registered
     * natively but nothing ever rendered as selected. The native side now sends the lowercase form
     * too; this stays as the backstop so a future accessor change cannot resurrect that bug.
     */
    fun isSelected(attrId: String): Boolean = selected.any { it.equals(attrId, ignoreCase = true) }
}

/* ---------------- DS Alchemy (COMPANION_ALCHEMY_*) ---------------- */

/**
 * One magic effect on the alchemy screen — either a created-potion effect or one of an
 * ingredient's four.
 *
 * [known] is skill-gated and the ONLY thing that decides whether the player may see it. When it is
 * false the exporter sends no [name] and no [icon] at all and the UI draws "?", which is exactly
 * what vanilla's MWSpellEffect widget does with `mKnown = false`.
 *
 * The two gates are DIFFERENT formulas and must not be conflated:
 *  - an INGREDIENT's slot k is known iff `Alchemy >= fWortChanceValue * (k + 1)` (so 15/30/45/60
 *    with the stock GMST) — per record and per slot, never per instance, and nothing is ever
 *    "identified" by use;
 *  - a CREATED effect at list position i uses `Alchemy::knownEffect(i, player)`: i<=1 needs
 *    fWortChanceValue, i<=3 needs x2, i<=5 needs x3, i<=7 needs x4, and 8+ is never known.
 * Both are evaluated natively; this class only carries the answer.
 */
data class AlchemyEffect(
    val name: String = "",
    val icon: String = "",
    val known: Boolean = false
)

/**
 * One of the four apparatus slots, read from the live `MWMechanics::Alchemy::mTools`.
 *
 * [type] is `ESM::Apparatus::AppaType`: 0 Mortar & Pestle, 1 Alembic, 2 Calcinator, 3 Retort. The
 * engine hardcodes four and throws on anything outside that range, so [slot] == [type] always and
 * four slots is safe to assume.
 *
 * Only the Mortar & Pestle is REQUIRED (see [AlchemyReady.NO_MORTAR]); the other three are optional
 * quality modifiers.
 */
data class AlchemyApparatus(
    val slot: Int,
    val present: Boolean = false,
    val id: String = "",
    val type: Int = 0,
    val name: String = "",
    val icon: String = "",
    val quality: Float = 0f
)

/** One apparatus the player is carrying — the source for the per-type picker, matching vanilla's
 *  ItemSelectionDialog (Filter_OnlyAlchemyTools + setApparatusTypeFilter). */
data class AlchemyTool(
    val id: String,
    val type: Int = 0,
    val name: String = "",
    val icon: String = "",
    val quality: Float = 0f
)

/**
 * One of the four ingredient slots.
 *
 * Emitted per slot INCLUDING the empty ones, and never compacted: slot order is gameplay-significant
 * (it decides the order of the created effects, and therefore the order they apply when the potion
 * is drunk), so a gap in the middle is real state and is drawn where it is.
 */
data class AlchemySlot(
    val slot: Int,
    val present: Boolean = false,
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val count: Int = 0
)

/** One row of the browse list. Ingredients already placed in a slot are omitted by the exporter,
 *  matching vanilla (which drops them from its own list via addDragItem of the full stack). */
data class AlchemyIngredient(
    val id: String,
    val name: String = "",
    val icon: String = "",
    val count: Int = 0,
    val effects: List<AlchemyEffect> = emptyList()
)

/** `MWMechanics::Alchemy::Result`, in the engine's own order — which is also the order
 *  `getReadyStatus()` checks them in, so the first failing condition is the one reported. */
object AlchemyReady {
    const val SUCCESS = 0
    const val NO_MORTAR = 1
    const val TOO_FEW_INGREDIENTS = 2
    const val NO_NAME = 3
    const val NO_EFFECTS = 4
    const val RANDOM_FAILURE = 5
}

/**
 * A live DS alchemy screen; null when GM_Alchemy is not open.
 *
 * Everything here is read from the live `MWMechanics::Alchemy` after every mutation — nothing is
 * tracked or recomputed on this side. In particular:
 *
 * [apparatus] must come from the native mTools rather than being derived from [tools], because the
 * prefill is SESSION-STICKY: `Alchemy::clear()` deliberately leaves mTools alone and the window
 * keeps one Alchemy for the whole game session, so `setAlchemist()` re-selects whatever the player
 * last hand-picked (as long as they still own it) and only falls back to highest quality otherwise.
 *
 * [chance] is EXPORTED BUT DELIBERATELY NOT DISPLAYED. It was shown next to Create for a while and
 * was removed, for two reasons worth keeping written down:
 *
 *  1. It is a property of the CHARACTER, not of the recipe — `factor` is
 *     `Alchemy(modified) + 0.1*Intelligence + 0.1*Luck` and nothing else, so it does not move as
 *     ingredients or apparatus change. Sitting beside Create it read as being about the potion.
 *     (Apparatus affects potion STRENGTH through `updateEffects`, never the odds.)
 *  2. More seriously, it was not the whole truth. `createSingle()` has TWO failure paths and this
 *     covers only the roll: it returns Result_RandomFailure without rolling at all when the
 *     QUANTIFIED effect list is empty, i.e. when every effect's magnitude or duration rounded to
 *     <= 0 (low skill and/or a poor mortar against an expensive effect). The real chance there is
 *     0% while this still reported `floor(factor) + 1`, and the ingredients are destroyed anyway.
 *     The Created Effects preview cannot be used to detect that either — it is built from the
 *     UNQUANTIFIED `listEffects()`, so it lists effects that then get nullified.
 *
 * If it is ever brought back, fix (2) first: it needs a `companionHasQuantifiedEffects()` accessor
 * alongside the two already added to MWMechanics::Alchemy, and must show 0% when that is false.
 *
 * [maxBrew] is `countPotionsToBrew()` = the smallest selected-ingredient stack, or 0 whenever the
 * recipe is not ready — INCLUDING when the name is empty. The DS quantity spinner is capped to it
 * and follows it down after a brew; vanilla's own spinner is uncapped and silently brews only
 * `min(maxBrew, spinner)`. That clamp still runs natively either way, so the cap is a UI affordance
 * rather than the thing enforcing the limit. See AlchemyUiState.qty.
 */
data class AlchemySession(
    val factor: Float = 0f,
    val chance: Int = 0,
    /** Alchemy skill, MODIFIED (fortify effects included) — the value every gate here uses. */
    val skill: Float = 0f,
    /** `fWortChanceValue`, read live: it is content data and mods change it. */
    val wort: Float = 15f,
    val maxBrew: Int = 0,
    /** The native spinner's current value, echoed back so the two can never disagree. */
    val brewCount: Int = 1,
    val ready: Int = AlchemyReady.NO_MORTAR,
    val name: String = "",
    /** `suggestPotionName()` — the first created effect's display string, or "" when there is none.
     *  The native window auto-fills the name field with it whenever it CHANGES, which is what lets a
     *  hand-typed name survive; that behaviour lives natively and is not re-implemented here. */
    val suggested: String = "",
    val apparatus: List<AlchemyApparatus> = emptyList(),
    val slots: List<AlchemySlot> = emptyList(),
    val created: List<AlchemyEffect> = emptyList(),
    val tools: List<AlchemyTool> = emptyList(),
    val ingredients: List<AlchemyIngredient> = emptyList()
) {
    /** Free ingredient slots. 0 means a further tap can do nothing — vanilla's addIngredient()
     *  silently returns -1 in that case, and so the row is simply not offered. */
    val freeSlots: Int get() = slots.count { !it.present }

    /** Is an ingredient of this record already placed? `addIngredient()` rejects a duplicate RefId,
     *  so the browse list must not offer one (the exporter already omits them; this is the guard for
     *  anything built from a stale batch). */
    fun isSlotted(id: String): Boolean =
        slots.any { it.present && it.id.equals(id, ignoreCase = true) }

    /** The owned apparatus of one type, for that slot's picker. Sorted by name to match vanilla's
     *  SortFilterItemModel, whose comparator falls through to the lowercased display name once the
     *  items share a type. */
    fun toolsOfType(type: Int): List<AlchemyTool> =
        tools.filter { it.type == type }.sortedBy { it.name.lowercase() }
}

/* ---------------- DS Map (COMPANION_MAP_*) ---------------- */

/** One player-authored map note. [index] is its position in the last export and is the handle every
 *  `CMP:map_note_*` command uses — safe as an ordinal because notes change only through those
 *  commands, and each one re-exports. */
data class MapNote(
    val index: Int,
    val worldX: Float,
    val worldY: Float,
    /** True when the note's cell is an EXTERIOR one — computed natively by asking what cell id this
     *  note's own position would generate as an exterior and comparing, so it cannot disagree with
     *  how the note was filed. Decides which map draws it: exterior notes belong to the world map,
     *  interior ones to the local map. */
    val exterior: Boolean = true,
    val cell: String = "",
    val note: String = ""
)

/** A discovered-location marker on the world map, ALREADY CLUSTERED by the engine.
 *
 *  [count] is how many same-named locations were merged onto this point; [x]/[y] are the barycentre
 *  the native side computed. The clustering is not re-derived here on purpose — `addVisitedLocation`
 *  builds MyGUI widgets directly and aggregates them, so a second implementation would be free to
 *  drift from the one the native map draws. [visible] mirrors vanilla's own size-based hiding of
 *  clusters too small to draw. */
data class MapPlace(
    val x: Float,
    val y: Float,
    val count: Int = 1,
    val name: String = "",
    val visible: Boolean = true
)

/** Header of a DS map push. Grid bounds are the local map's segment grid; [cellSize] is world units
 *  per cell, which is what converts a world position into a segment-relative one. */
data class MapDsState(
    val interior: Boolean = false,
    // Explicit MIN/MAX, deliberately NOT MyGUI's left/top/right/bottom. In an IntRect `top` is the
    // MINIMUM Y and `bottom` the maximum (screen convention); carrying those names across meant the
    // consumer looped bottom..top, which is an empty Kotlin range — the local map drew nothing.
    val gridXMin: Int = 0,
    val gridXMax: Int = 0,
    val gridYMin: Int = 0,
    val gridYMax: Int = 0,
    val cellSize: Float = 8192f,
    val playerX: Float = 0f,
    val playerY: Float = 0f
)

/** Where the global map sits in cell space, so a world position can be placed on it.
 *  `pixel = (worldCell - min) * cellPixels`. */
data class GlobalMapInfo(
    val width: Int,
    val height: Int,
    val minX: Int,
    val minY: Int,
    val cellPixels: Int
)

/** Which map. As of the Aug 20 2026 redesign this is the identity of a SURFACE'S CONTENT, not a
 *  mode a screen is in: BOTH maps are on screen simultaneously, one per physical display, and
 *  `MapDsUiState.swapped` decides which display each occupies. The engine keeps its own
 *  single-view equivalent in `Settings::map().mGlobal`, a PERSISTED device-wide setting the DS map
 *  deliberately never writes. */
enum class MapView { LOCAL, GLOBAL }

/* ---------------- DS Enchanting (COMPANION_ENCHANTING_*) ---------------- */

/**
 * `ESM::MagicEffect::Flags` — the bits the enchanting screen reads. The RAW flag word is exported
 * rather than precomputed booleans because which range options and which sliders are legal depends
 * on the LIVE cast style as well as the record: Constant Effect force-allows Self, force-denies
 * Touch/Target and hides Duration, and the player flips that with the Type button while the browse
 * list stands still. Recompute with [EnchantSession.allowSelf] and friends, never cache.
 */
object MagicEffectFlag {
    const val TARGET_SKILL = 0x1
    const val TARGET_ATTRIBUTE = 0x2
    const val NO_DURATION = 0x4
    const val NO_MAGNITUDE = 0x8
    const val CAST_SELF = 0x40
    const val CAST_TOUCH = 0x80
    const val CAST_TARGET = 0x100
}

/** `ESM::RangeType`. */
object EnchantRange {
    const val SELF = 0
    const val TOUCH = 1
    const val TARGET = 2

    fun label(range: Int): String = when (range) {
        SELF -> "Self"
        TOUCH -> "Touch"
        else -> "Target"
    }
}

/** `ESM::Enchantment::Type`. Which of these are REACHABLE depends on the item and the soul — the
 *  engine's own `nextCastStyle()` state machine — so the DS side never derives it; the exporter
 *  sends the current one plus [EnchantSession.castCycle]. */
object EnchantCastType {
    const val CAST_ONCE = 0
    const val WHEN_STRIKES = 1
    const val WHEN_USED = 2
    const val CONSTANT_EFFECT = 3
}

/** Fixed slider bounds, straight out of `openmw_edit_effect.layout`. They are NOT adjusted at
 *  runtime — not by skill, not by the soul gem, not by the effect's base cost — so these are plain
 *  constants on both sides of the bridge (the native `companionEffectSet` clamps to the same
 *  numbers, so a malformed command cannot write out of range either). */
object EnchantBounds {
    const val MAG_MIN = 1
    const val MAG_MAX = 100
    const val DURATION_MIN = 1
    const val DURATION_MAX = 1440
    const val AREA_MIN = 0
    const val AREA_MAX = 50
}

/** The item or the soul-gem slot. A cleared slot is `present = false`; [maxPoints] is only
 *  meaningful on the item slot and [charge]/[soul] only on the soul slot. */
data class EnchantSlotItem(
    val present: Boolean = false,
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    /** Item slot: `mData.mEnchant * fEnchantmentMult` — the capacity THIS item would give. */
    val maxPoints: Int = 0,
    /** Soul slot: the soul's raw `mData.mSoul`, NOT the gem's own capacity. */
    val charge: Int = 0,
    /** Soul slot: the trapped creature's display name. */
    val soul: String = ""
)

/** One row of the Magic Effects browse list — the union of every effect appearing in any
 *  `ST_Spell`-type spell the player knows, filtered to `AllowEnchanting`, deduplicated by record and
 *  sorted by display name. Built ONCE at window open and never rebuilt, matching vanilla. */
data class EnchantAvailEffect(
    val id: String,
    val name: String,
    val icon: String,
    val flags: Int
)

/** One effect currently on the enchantment. [index] is its position in the native `mEffects` vector
 *  and is the handle every `CMP:enchant_effect_*` command uses — safe as an ordinal because that
 *  vector is mutated only by the player and GM_Enchanting pauses the sim. [text] is composed
 *  NATIVELY (a transcription of `MWSpellEffect::updateWidgets`) so it cannot drift from the wording
 *  vanilla shows. */
data class EnchantEffect(
    val index: Int,
    val id: String,
    val skill: String = "",
    val attribute: String = "",
    val range: Int = EnchantRange.SELF,
    val magMin: Int = 1,
    val magMax: Int = 1,
    val duration: Int = 1,
    val area: Int = 0,
    val flags: Int = 0,
    val icon: String = "",
    val text: String = ""
)

/** One row of the item or soul picker. Both browse the PLAYER's own inventory, never the
 *  enchanter's, and are addressed by serialized RefId rather than by ordinal. */
data class EnchantPickOption(
    val id: String,
    val name: String,
    val icon: String,
    val count: Int = 1,
    /** Item picker: the capacity this item would give. */
    val maxPoints: Int = 0,
    /** Soul picker: the trapped soul's value and name. */
    val charge: Int = 0,
    val soul: String = ""
)

/** A request from the engine to open the Skill or Attribute selector — the third popup type, raised
 *  when the tapped effect carries `TargetSkill` / `TargetAttribute`. Vanilla opens SelectSkillDialog
 *  / SelectAttributeDialog at this point; the DS screen draws its own and answers with
 *  `CMP:enchant_effect_skill` / `_attribute`. NOTE there is deliberately no duplicate check on this
 *  path — vanilla has none, so the same skill may be added twice. */
data class EnchantArgPick(val kind: String, val effectId: String) {
    val isSkill: Boolean get() = kind == "skill"
}

/** A request from the engine to open the effect editor. [isNew] marks an effect that `_add` just
 *  pushed onto `mEffects` — it is ALREADY on the enchantment and shows in the top-screen readout
 *  while the editor is open, exactly as vanilla's `eventEffectAdded` does it, and Cancel is what
 *  removes it again. `isNew = false` means an existing effect was tapped; Cancel restores it. */
data class EnchantEditRequest(val index: Int, val isNew: Boolean)

/**
 * A live DS enchanting screen; null when GM_Enchanting is not open.
 *
 * PRESENTATION ONLY. Everything here is read off the live `MWMechanics::Enchanting` after every
 * mutation. The cast-style state machine, the accumulating per-effect cost, the capacity check, the
 * price / charge / chance formulas, the seven Buy validations and the self-enchant roll (which
 * consumes the soul gem whatever the outcome) all stay native.
 *
 * Two visibility rules are the ENGINE's and are exported rather than decided here:
 *  - [showPrice] is on only when buying from an enchanter (vanilla hides it when self-enchanting).
 *  - [showChance] additionally requires OpenMW's own `[Game] show enchant chance` setting, which
 *    ships FALSE in both the engine defaults and this app's `settings.fallback.cfg`. [chance] is
 *    exported regardless, so if that setting is ever turned on the DS screen starts showing it too.
 */
data class EnchantSession(
    /** Self-enchanting (entered from a filled soul gem) vs buying from an enchanter NPC. */
    val self: Boolean = true,
    val enchanter: String = "",
    val gold: Int = 0,
    val name: String = "",
    /** `int(getEnchantPoints(false))` — the FLOORED-per-effect sum. This is what the native label
     *  shows and what the Buy capacity check compares, and it is deliberately NOT the precise=true
     *  value the chance / item-count / type-multiplier paths use. */
    val points: Int = 0,
    /** The selected item's `mData.mEnchant * fEnchantmentMult`. Item-dependent, NOT soul-dependent. */
    val maxPoints: Int = 0,
    val castCost: Int = 0,
    /** The soul's raw value. Unaffected by the effects or the cast type. */
    val charge: Int = 0,
    val price: Int = 0,
    val chance: Int = 0,
    val showPrice: Boolean = false,
    val showChance: Boolean = false,
    val castType: Int = EnchantCastType.CAST_ONCE,
    val castLabel: String = "",
    /** Can the Type button do anything for this item? Books/scrolls and ammo/thrown are locked, and
     *  bows only unlock a second option with a soul >= iSoulAmountForConstantEffect. Exported, never
     *  re-derived here. */
    val castCycle: Boolean = false,
    val constant: Boolean = false,
    val effectCap: Int = 8,
    val item: EnchantSlotItem = EnchantSlotItem(),
    val soul: EnchantSlotItem = EnchantSlotItem(),
    val available: List<EnchantAvailEffect> = emptyList(),
    val effects: List<EnchantEffect> = emptyList(),
    val itemOptions: List<EnchantPickOption> = emptyList(),
    val soulOptions: List<EnchantPickOption> = emptyList()
) {
    /** Range availability, recomputed from the raw flags against the LIVE cast style — the exact
     *  expressions `EditEffectDialog::onRangeButtonClicked` uses. */
    fun allowSelf(flags: Int): Boolean = (flags and MagicEffectFlag.CAST_SELF) != 0 || constant
    fun allowTouch(flags: Int): Boolean = (flags and MagicEffectFlag.CAST_TOUCH) != 0 && !constant
    fun allowTarget(flags: Int): Boolean = (flags and MagicEffectFlag.CAST_TARGET) != 0 && !constant

    /** The legal ranges in Self -> Touch -> Target order. Empty means the effect is unusable under
     *  the current cast style, which vanilla handles by silently ignoring the tap (its own
     *  unresolved TODO) — so no error message is shown here either. */
    fun ranges(flags: Int): List<Int> = buildList {
        if (allowSelf(flags)) add(EnchantRange.SELF)
        if (allowTouch(flags)) add(EnchantRange.TOUCH)
        if (allowTarget(flags)) add(EnchantRange.TARGET)
    }

    /** The three slider-visibility tests, independent of each other — `EditEffectDialog::updateBoxes`
     *  verbatim. Area is keyed to the CURRENT range rather than to a flag of its own, so it appears
     *  and disappears live as the player cycles range inside the editor. */
    fun showMagnitude(flags: Int): Boolean = (flags and MagicEffectFlag.NO_MAGNITUDE) == 0
    fun showDuration(flags: Int): Boolean = (flags and MagicEffectFlag.NO_DURATION) == 0 && !constant
    fun showArea(range: Int): Boolean = range != EnchantRange.SELF

    /** Over capacity — the same comparison the Buy validation makes before rejecting with
     *  sNotifyMessage29. Shown as a warning colour; Buy is still pressable, because pressing it is
     *  how vanilla produces that message. */
    val overCapacity: Boolean get() = item.present && points > maxPoints
}

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
    val known: Boolean,
    // Spell record id (RefId serializeText) — used to request COMPANION_INFO via CMP:info spell:<id>
    // for the R3/long-press detail popup. "" for old 5-field export lines (backward compatible).
    val id: String = ""
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

/** A teleport-door marker for the companion minimap: the door's world position + destination cell
 *  name (COMPANION_DOORMARKER_*). Transient — own StateFlow in the repo. worldX/worldY are placed
 *  on the map with the same transform as the player position (exterior grid / interior rotation). */
data class DoorMarker(
    val worldX: Float,
    val worldY: Float,
    val name: String
)

/** Live state of the two engine-side Developer Tools toggles (god mode / player collision), from
 *  the change-detected COMPANION_DEV_STATE line. Both are real engine flags owned by
 *  `openmw.debug` (`isGodMode()` / `isCollisionEnabled()`), NOT preferences — the console, another
 *  mod, or a reload can change them behind our back, so the options pills read this rather than a
 *  locally remembered value. `noclip` is the INVERSE of isCollisionEnabled (collision off = noclip
 *  on) so it matches the button's label. Defaults are the engine's own start-of-game state. */
data class DevToggleState(
    val godMode: Boolean = false,
    val noclip: Boolean = false
)

/**
 * The current in-game date (COMPANION_GAMEDATE), read from the same three MWScript globals the
 * engine stamps real journal entries with. [day] is the monotonic DaysPassed counter — the value
 * the chronological journal groups pages by — while [month] (1-based) and [dayOfMonth] are the
 * display date. Manual journal entries are stamped from this and nothing else.
 */
data class GameDate(
    val day: Int,
    val month: Int,
    val dayOfMonth: Int
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
    /** L3 (left stick click) — cycle the focused column's sort mode/direction (looting + barter).
     *  Drives the SAME InvSortState the on-screen sort chips do, so the two stay in sync. */
    data class Sort(override val seq: Long) : NavEvent()
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
    /**
     * The equipped ammo stack, when the equipped weapon actually fires ammo and the equipped ammo
     * matches it; null in every other case (melee/thrown/unarmed, empty ammo slot, or ammo that
     * does not match the weapon). Backs the HUD's ammo counter — see [EquippedAmmo].
     */
    val ammo: EquippedAmmo? = null,
    /**
     * Enchantment charge of the equipped (weapon-slot) item, when that item's enchantment is one
     * that drains; null in every other case. Backs the HUD's equipped-item charge meter — see
     * [EquippedCharge].
     */
    val equippedCharge: EquippedCharge? = null,
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
