@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package org.openmw.companion

import android.util.Log
import org.openmw.EngineActivity

object CompanionActions {

    fun equipItem(itemId: String) = runCommand("CMP:equip $itemId")

    fun unequipItem(itemId: String) = runCommand("CMP:unequip $itemId")

    // count is delimited with | because item ids contain spaces
    fun dropItem(itemId: String, count: Int = 1) = runCommand("CMP:drop $itemId|$count")

    fun selectSpell(spellId: String) = runCommand("CMP:spell $spellId")

    fun readItem(id: String) = runCommand("CMP:read $id")

    // "Use" an item the way the native inventory does (double-click / drag onto the
    // paper doll): potion → drink, ingredient → eat, apparatus → alchemy menu,
    // repair tool → repair menu. Lua fires the stock ItemUsage `UseItem` global
    // event, which dispatches per item type. Distinct from equip (worn gear) and
    // from the merchant repairItem() above.
    fun useItem(id: String) = runCommand("CMP:use $id")

    fun refreshJournal() = runCommand("CMP:journal")

    // Opens the in-game world map (Lua handles CMP:openmap via AddUiMode).
    fun openWorldMap() = runCommand("CMP:openmap")

    // Quest completion status is C++-only (androidmain.cpp handles this natively,
    // NOT Lua); reply arrives as a streamed COMPANION_JOURNAL_FINISHED_* block.
    fun refreshQuestStatus() = runCommand("CMP:questStatus")

    // Known dialogue topics are C++-only (not exposed to Lua); handled natively in
    // androidmain.cpp. Reply arrives as a streamed COMPANION_TOPICS_* block.
    fun refreshTopics() = runCommand("CMP:refreshTopics")

    // Request an on-demand detail export; reply arrives as a COMPANION_INFO line.
    fun requestItemInfo(itemId: String) = runCommand("CMP:info item:$itemId")

    fun requestSpellInfo(spellId: String) = runCommand("CMP:info spell:$spellId")

    /** A MAGIC EFFECT record (the enchanting browse list's R3 / long-press). Its reply carries the
     *  effect's description in COMPANION_INFO's `desc`, which no other kind uses. */
    fun requestEffectInfo(effectId: String) = runCommand("CMP:info effect:$effectId")

    // Looting / pickpocketing transfers (Lua handles CMP:container_* and dispatches
    // moveInto via companion_global.lua). sid = per-stack instance id; count is
    // delimited with | because ids contain spaces (same convention as dropItem).
    fun containerTake(stackId: String, count: Int = 1) = runCommand("CMP:container_take $stackId|$count")

    fun containerPut(stackId: String, count: Int = 1) = runCommand("CMP:container_put $stackId|$count")

    fun containerTakeAll() = runCommand("CMP:container_take_all")

    // Dispose of corpse = take all + close the container window (Lua removeMode).
    fun containerDispose() = runCommand("CMP:container_dispose")

    // Close the container window without taking anything (Lua removeMode).
    fun containerClose() = runCommand("CMP:container_close")

    // Dialogue (bottom-screen) — handled natively in drainCompanionCommands, NOT Lua.
    // The arg is the topic/service DISPLAY string exactly as exported (may contain
    // spaces); the native side matches it against the dialogue window's list.
    fun selectDialogueTopic(topic: String) = runCommand("CMPDLG:topic:$topic")

    fun activateDialogueService(service: String) = runCommand("CMPDLG:service:$service")

    // Goodbye is not a topic — dedicated command mirroring the in-game Bye button.
    fun dialogueGoodbye() = runCommand("CMPDLG:goodbye")

    // Answer a mid-dialogue question/choice by its integer id.
    fun activateDialogueChoice(id: Int) = runCommand("CMPDLG:choice:$id")

    // Persuasion action, driven by the bottom-screen popup (the native modal is never
    // shown). type 0..5 = Admire / Intimidate / Taunt / Bribe10 / Bribe100 / Bribe1000.
    fun persuade(type: Int) = runCommand("CMPDLG:persuade:$type")

    // Barter (bottom-screen) — handled NATIVELY in drainCompanionCommands, NOT Lua (the
    // merchant Ptr, gold pool, mercantile-adjusted prices and haggle all live in the C++
    // TradeWindow). borrow/return wire format is "<count>|<side>|<sid>": count leads and the
    // PER-INSTANCE sid (BarterItem.stackId — "<refId>#<ordinal>", may contain spaces but no '|')
    // is the tail. sid (not the bare record id) is what disambiguates two same-record stacks, e.g.
    // a worn vs. unworn copy (Bug 2). The engine re-exports COMPANION_BARTER_OFFER after each,
    // reconciling the authoritative balance.
    fun barterBorrow(side: BarterSide, sid: String, count: Int = 1) =
        runCommand("CMP:barter_borrow $count|${side.wire}|$sid")

    fun barterReturn(side: BarterSide, sid: String, count: Int = 1) =
        runCommand("CMP:barter_return $count|${side.wire}|$sid")

    // Manual extra-gold offset (the +/- gold row); may be negative.
    fun barterSetExtraGold(extra: Int) = runCommand("CMP:barter_gold $extra")

    // Submit the current staged offer; reply is COMPANION_BARTER_OFFER_ACCEPTED/_REJECTED.
    fun barterOffer() = runCommand("CMP:barter_offer")

    // Cancel barter (aborts the staged offer + closes the native window).
    fun barterCancel() = runCommand("CMP:barter_cancel")

    private val BarterSide.wire: String
        get() = if (this == BarterSide.VENDOR) "vendor" else "player"

    // Merchant repair (CMP:repair_*) — handled natively in drainCompanionCommands (repair
    // prices via getBarterOffer and the NPC gold pool live in the C++ MerchantRepair window).
    // [sid] is the item's ordinal index in the exported damaged list; the engine re-exports
    // COMPANION_REPAIR_* after each repair.
    fun repairItem(sid: String) = runCommand("CMP:repair_item $sid")

    fun repairAll() = runCommand("CMP:repair_all")

    // Cancel repair (closes the native window + emits COMPANION_REPAIR_CLOSED).
    fun repairCancel() = runCommand("CMP:repair_cancel")

    // Travel (CMP:travel_*) — handled natively in drainCompanionCommands (the merchant-adjusted
    // price, follower-aware teleport, gold transfer and time advance all live in the C++
    // TravelWindow). [index] is the destination's ordinal in the exported list; travelGo reuses the
    // native onTravelButtonClick path. The engine emits COMPANION_TRAVEL_CLOSED on completion/cancel.
    fun travelGo(index: Int) = runCommand("CMP:travel_go $index")

    fun travelCancel() = runCommand("CMP:travel_cancel")

    // Rest/wait (CMP:sleep*) — handled natively in drainCompanionCommands (the canRest flags,
    // the fade + progress time advance, sleep interruption and level-up all live in the C++
    // WaitDialog; world.advanceTime from Lua would skip healing/level-up). The mode (rest vs
    // wait) is already known engine-side from the open; [hours] is the slider value (1..24).
    fun sleep(hours: Int) = runCommand("CMP:sleep $hours")

    // Cancel rest/wait (closes the native window + emits COMPANION_SLEEP_CLOSED).
    fun sleepCancel() = runCommand("CMP:sleep_cancel")

    // Training (CMP:training_*) — handled natively in drainCompanionCommands (the best-3 skill
    // selection, iTrainingMod pricing via getBarterOffer, the skill/attribute caps, skillLevelUp and
    // the timed fade/advance all live in the C++ TrainingWindow). [index] is the skill's ordinal in
    // the exported best-3 list. Training is one-shot: the engine emits COMPANION_TRAINING_CLOSED after
    // the 2-hour advance completes (or immediately if it rejects the train).
    // Level up. Both drive the NATIVE LevelupDialog's own handlers, so selection semantics
    // (toggle, replace-last-at-quota), the coin-count gate and the ordered commit are the game's,
    // not ours. Picking by attribute ID rather than an ordinal keeps the DS grid order independent
    // of the native window's. A pick on an attribute already at 100 is refused natively.
    fun levelUpPick(attrId: String) = runCommand("CMP:levelup_pick:$attrId")

    // Confirm. Deliberately routed to the same entry point as the native OK button: under quota it
    // refuses and shows vanilla's own message, so the DS Done gate can never commit something the
    // real gate would have rejected.
    fun levelUpConfirm() = runCommand("CMP:levelup_ok")

    // --- DS Alchemy ---------------------------------------------------------------------------
    // All handled natively in androidmain.cpp → alchemywindow.cpp, which drives the REAL
    // MWMechanics::Alchemy. Nothing about alchemy is reimplemented on this side: the combination
    // rule and its slot order, the duplicate-ingredient rejection, the validation order,
    // countPotionsToBrew() and the per-potion success roll all stay in the engine.
    //
    // Apparatus and ingredients are addressed by RefId, NEVER by a list ordinal — the browse list
    // reorders as stacks are consumed, so an ordinal would target the wrong item after any mutation.

    /** Put an owned apparatus in its slot. The engine derives the slot from the record and uses
     *  [slot] only as a sanity filter, so the two can never disagree. */
    fun alchemySetApparatus(slot: Int, itemId: String) =
        runCommand("CMP:alchemy_apparatus_set:$slot|$itemId")

    fun alchemyClearApparatus(slot: Int) = runCommand("CMP:alchemy_apparatus_clear:$slot")

    /** Place an ingredient in the first free slot. Silently does nothing when all four are full or
     *  an ingredient of this record is already placed — exactly what vanilla's addIngredient() does
     *  (it returns -1 and the window shows no feedback). */
    fun alchemyAddIngredient(itemId: String) = runCommand("CMP:alchemy_ingredient_add:$itemId")

    /** Empty one ingredient slot IN PLACE. The remaining ingredients keep their positions — slot
     *  order decides the created-effect order, so nothing is ever compacted. */
    fun alchemyClearIngredient(slot: Int) = runCommand("CMP:alchemy_ingredient_clear:$slot")

    /** Set the potion name. Raw tail after the prefix, so spaces and ':' survive. */
    fun alchemySetName(text: String) = runCommand("CMP:alchemy_name:$text")

    /** Brew. Routed to the native Create button, which clamps to min(countPotionsToBrew(), count)
     *  and produces vanilla's own messages and sounds. The DS spinner is capped to the craftable
     *  maximum so it should never send more than that, but the native clamp remains the authority —
     *  it is what makes an over-large count harmless rather than something to guard against here. */
    fun alchemyCreate(count: Int) = runCommand("CMP:alchemy_create:$count")

    fun alchemyCancel() = runCommand("CMP:alchemy_cancel")

    // --- DS Enchanting -------------------------------------------------------------------------
    // All handled natively in androidmain.cpp → enchantingdialog.cpp, which drives the REAL
    // MWMechanics::Enchanting and EffectEditorBase. Nothing about enchanting is reimplemented on
    // this side: the cast-style state machine, the accumulating per-effect cost, the enchant-points
    // capacity check, the price/charge/success-chance formulas, the seven Buy validations (including
    // the stolen-item confiscation) and the self-enchant roll that consumes the soul gem whatever
    // the outcome all stay in the engine.
    //
    // The item and the soul gem take a SERIALIZED RefId (their pickers browse a container store
    // whose order is not stable); EFFECTS take an INDEX into the native mEffects vector, which is
    // safe because only the player mutates it and GM_Enchanting pauses the sim.
    fun enchantSelectItem(itemId: String) = runCommand("CMP:enchant_item_select:$itemId")

    fun enchantClearItem() = runCommand("CMP:enchant_item_clear")

    fun enchantSelectSoul(itemId: String) = runCommand("CMP:enchant_soul_select:$itemId")

    fun enchantClearSoul() = runCommand("CMP:enchant_soul_clear")

    fun enchantSetName(text: String) = runCommand("CMP:enchant_name:$text")

    /** Tap a browse-list effect. The ENGINE decides what happens next and says so: a
     *  TargetSkill/TargetAttribute effect answers with COMPANION_ENCHANTING_ARGPICK (open the
     *  selector), anything else is added and answers with COMPANION_ENCHANTING_EDIT. The 8-effect
     *  cap, the no-legal-range check and the plain-effect duplicate rule are all applied there. */
    fun enchantAddEffect(effectId: String) = runCommand("CMP:enchant_effect_add:$effectId")

    /** Answer an ARGPICK. No duplicate check runs on this path — vanilla has none, so the same skill
     *  or attribute may deliberately be added more than once. */
    fun enchantEffectSkill(effectId: String, skillId: String) =
        runCommand("CMP:enchant_effect_skill:$effectId|$skillId")

    fun enchantEffectAttribute(effectId: String, attributeId: String) =
        runCommand("CMP:enchant_effect_attribute:$effectId|$attributeId")

    /** Open the editor on an existing effect. The engine snapshots it so _cancel can restore it. */
    fun enchantEditEffect(index: Int) = runCommand("CMP:enchant_effect_edit:$index")

    /** Live slider/range write. Values are clamped to the fixed native bounds on both sides. */
    fun enchantSetEffect(index: Int, range: Int, magMin: Int, magMax: Int, duration: Int, area: Int) =
        runCommand("CMP:enchant_effect_set:$index|$range|$magMin|$magMax|$duration|$area")

    fun enchantDeleteEffect(index: Int) = runCommand("CMP:enchant_effect_delete:$index")

    /** Editor OK — keep what the sliders wrote. */
    fun enchantEffectOk() = runCommand("CMP:enchant_effect_ok")

    /** Editor Cancel — the two halves of EditEffectDialog::exit(): a newly-added effect is removed,
     *  an edited one is restored to the values it had when the editor opened. */
    fun enchantEffectCancel() = runCommand("CMP:enchant_effect_cancel")

    /** Cycle the cast type. Switching INTO Constant Effect force-rewrites every already-added
     *  effect's range to Self and does NOT restore them on the way back out — vanilla's own
     *  destructive side effect, deliberately preserved. */
    fun enchantCastTypeNext() = runCommand("CMP:enchant_casttype_next")

    /** Buy / Create. Routed at the REAL handler, so all seven validations run in their exact order
     *  with their exact messages; there is no separate "validate without committing" path. */
    fun enchantBuy() = runCommand("CMP:enchant_buy")

    fun enchantCancel() = runCommand("CMP:enchant_cancel")

    // --- DS Spellmaking -------------------------------------------------------------------------
    // The effect-editing half mirrors enchanting's because it drives the SAME native code: both
    // families land on EffectEditorBase, which owns mEffects, the 8-effect cap and the
    // add-then-remove-on-cancel semantics. Only the window pointer differs.
    fun spellmakingSetName(text: String) = runCommand("CMP:spellmaking_name:$text")
    fun spellmakingAddEffect(effectId: String) = runCommand("CMP:spellmaking_effect_add:$effectId")
    fun spellmakingEffectSkill(effectId: String, skillId: String) =
        runCommand("CMP:spellmaking_effect_skill:$effectId|$skillId")
    fun spellmakingEffectAttribute(effectId: String, attributeId: String) =
        runCommand("CMP:spellmaking_effect_attribute:$effectId|$attributeId")
    fun spellmakingEditEffect(index: Int) = runCommand("CMP:spellmaking_effect_edit:$index")
    fun spellmakingSetEffect(index: Int, range: Int, magMin: Int, magMax: Int, duration: Int, area: Int) =
        runCommand("CMP:spellmaking_effect_set:$index|$range|$magMin|$magMax|$duration|$area")
    fun spellmakingDeleteEffect(index: Int) = runCommand("CMP:spellmaking_effect_delete:$index")
    fun spellmakingEffectOk() = runCommand("CMP:spellmaking_effect_ok")
    fun spellmakingEffectCancel() = runCommand("CMP:spellmaking_effect_cancel")
    /** Routes at the REAL onBuyButtonClicked — two of its four validations read the native widget
     *  CAPTIONS, so a DS-side gate would be testing different numbers from the one that refuses. */
    fun spellmakingBuy() = runCommand("CMP:spellmaking_buy")
    fun spellmakingCancel() = runCommand("CMP:spellmaking_cancel")

    // --- DS Map ---------------------------------------------------------------------------------
    /** Mount/unmount the DS map. This is the MOUNT-SCOPED suppression flag the native side reads in
     *  updateVisible(): true hides the native map window, false restores it. Deliberately not a
     *  one-shot armed when the map is asked to open — that open can fail to happen (char-gen
     *  suppression, a close toggle) and the stale flag would then suppress a later map open from a
     *  vanilla path. Mounting also triggers the one full state push. */
    fun mapMount(mounted: Boolean) = runCommand("CMP:map_mount:${if (mounted) 1 else 0}")

    /** Re-push the whole map state. Rarely needed — the sim is paused while the DS map is up, so
     *  nothing can change except through the note commands below, which re-export themselves. */
    fun mapRefresh() = runCommand("CMP:map_refresh")

    /** [exterior] forces the note to be filed against the EXTERIOR cell under the position, which
     *  is what a world-map note always is. Without it the engine uses the player's active cell, and
     *  `getCellIdInWorldSpace` ignores x/y for an interior — so a note dropped on the world map
     *  while standing indoors would be filed under that interior and never drawn. */
    fun mapAddNote(worldX: Float, worldY: Float, exterior: Boolean, note: String) =
        runCommand("CMP:map_note_add:$worldX|$worldY|${if (exterior) 1 else 0}|$note")

    fun mapEditNote(index: Int, note: String) = runCommand("CMP:map_note_edit:$index|$note")

    fun mapDeleteNote(index: Int) = runCommand("CMP:map_note_delete:$index")

    fun trainSkill(index: Int) = runCommand("CMP:training_train:$index")

    // Cancel training (closes the native window + emits COMPANION_TRAINING_CLOSED). Idempotent —
    // native guards on containsMode(GM_Training), so it no-ops if the mode already popped.
    fun trainCancel() = runCommand("CMP:training_cancel")

    // Spell buying (CMP:spellbuying_*) — handled natively in drainCompanionCommands (the spell-cost
    // formula, getBarterOffer price, spells.add and the NPC gold pool live in the C++
    // SpellBuyingWindow). [index] is the spell's ordinal in the exported list; the engine re-exports
    // COMPANION_SPELLBUYING_* after each purchase (bought spell flips to known=1, keeps its slot).
    fun buySpell(index: Int) = runCommand("CMP:spellbuying_buy:$index")

    // Cancel spell buying (closes the native window + emits COMPANION_SPELLBUYING_CLOSED).
    fun spellBuyingCancel() = runCommand("CMP:spellbuying_cancel")

    // Text input (CMPTEXT:*) — handled natively in drainCompanionCommands (the focused MyGUI
    // EditBox is C++-only, unreachable from Lua). submit = write text into the field then
    // defocus it (commit); cancel = defocus without writing (discard). Both make the top-screen
    // field stop flashing and emit COMPANION_TEXT_INPUT_CLOSED so the bottom panel dismisses.
    // submit's text is the raw tail after the prefix, so spaces and ':' inside it survive.
    fun submitTextInput(text: String) = runCommand("CMPTEXT:set:$text")

    fun cancelTextInput() = runCommand("CMPTEXT:cancel")

    /** Tell native whether a cancelable bottom-screen modal (quantity selector / persuasion popup)
     *  is open, so the controller B button cancels just that modal (COMPANION_NAV_CANCEL) instead of
     *  closing the whole overlay/conversation. (Native symbol kept as setCompanionQtySelectorOpen.) */
    fun setModalCancelOpen(open: Boolean) {
        try {
            EngineActivity.setCompanionQtySelectorOpen(open)
        } catch (t: Throwable) {
            Log.e(TAG, "setCompanionQtySelectorOpen failed", t)
        }
    }

    // --- Developer Tools ------------------------------------------------------------------
    // Cheats / test helpers behind the Developer Tools "Developer mode" opt-in. All of these are
    // handled in companion.lua (dispatchCommand's "dev_" branch) — see the note there for why the
    // Lua APIs are used rather than raw console commands. Nothing here is intercepted natively:
    // the one action that was (devResurrect, which needed MechanicsManager::resurrect for want of
    // a Lua binding) was removed Aug 11 2026, so drainCompanionCommands now has no CMP:dev_ branch.
    private fun dev(action: String) = runCommand("CMP:dev_$action")

    // Two amounts for different test needs — see DEV_GOLD_SMALL/DEV_GOLD_LARGE in companion.lua.
    fun devAddGold() = dev("gold")
    fun devAddGold10k() = dev("gold10k")

    // One per vital rather than a single "max everything" — each is independently useful when
    // testing (e.g. magicka only, to exercise casting without also removing damage pressure).
    // The Lua side derives the stat name from the action suffix, so these three names matter.
    fun devMaxHealth() = dev("maxhealth")
    fun devMaxMagicka() = dev("maxmagicka")
    fun devMaxFatigue() = dev("maxfatigue")

    fun devAddAttributes() = dev("addattributes")
    fun devAddSkills() = dev("addskills")
    fun devToggleGodMode() = dev("god")
    fun devToggleNoclip() = dev("noclip")
    fun devSetLevel20() = dev("setlevel20")
    fun devTriggerLevelUp() = dev("levelup")
    fun devAddSpellKit() = dev("spellkit")
    fun devStackEffects() = dev("stackeffects")
    fun devAddRegressionKit() = dev("regressionkit")
    fun devAddBulkItems() = dev("bulkitems")

    // --- Enchanting / Spellmaking test kit ---------------------------------------------------------
    // Enchanting branches on more record properties than any other DS screen — five item categories
    // with different cast-type cycling rules, seven range/slider combinations, and a duplicate rule
    // that behaves differently for skill/attribute effects — and none of that is reachable on an
    // ordinary low-level character. All three pick their records by PROPERTY at runtime; see the
    // helpers of the same name in companion.lua.
    fun devAddSoulGems() = dev("soulgems")

    fun devAddEnchantItems() = dev("enchantitems")

    /** ONE kit for BOTH crafting screens. `EffectEditorBase::startEditing` is literally the same
     *  function for Enchanting and Spellmaking — same known-spell pool, same dedupe, same sort — and
     *  the only difference is which flag it requires (`AllowEnchanting` vs `AllowSpellmaking`), so a
     *  spell taught for one screen guarantees nothing about the other. The Lua side covers both. */
    fun devAddCraftSpells() = dev("craftspells")
    fun devSetDay() = dev("day")
    fun devSetNight() = dev("night")

    fun exportIconToPng(iconPath: String, outputPath: String) {
        Log.d(TAG, "exportIconToPng iconPath='$iconPath'")
        try {
            EngineActivity.exportIconToPng(iconPath, outputPath)
            Log.d(TAG, "exportIconToPng returned for '$iconPath'")
        } catch (t: Throwable) {
            Log.e(TAG, "exportIconToPng threw for '$iconPath'", t)
        }
    }

    private fun runCommand(command: String) {
        try {
            EngineActivity.sendCompanionCommand(command)
            Log.d(TAG, "Queued: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
        }
    }

    private const val TAG = "CompanionActions"
}
