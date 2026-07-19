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
