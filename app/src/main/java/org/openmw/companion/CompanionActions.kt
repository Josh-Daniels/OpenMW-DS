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
    // TradeWindow). borrow/return wire format is "<count>|<side>|<refId>": count leads and
    // refId is the tail because refIds contain spaces (but no '|'). The engine re-exports
    // COMPANION_BARTER_OFFER after each, reconciling the authoritative balance.
    fun barterBorrow(side: BarterSide, refId: String, count: Int = 1) =
        runCommand("CMP:barter_borrow $count|${side.wire}|$refId")

    fun barterReturn(side: BarterSide, refId: String, count: Int = 1) =
        runCommand("CMP:barter_return $count|${side.wire}|$refId")

    // Manual extra-gold offset (the +/- gold row); may be negative.
    fun barterSetExtraGold(extra: Int) = runCommand("CMP:barter_gold $extra")

    // Submit the current staged offer; reply is COMPANION_BARTER_OFFER_ACCEPTED/_REJECTED.
    fun barterOffer() = runCommand("CMP:barter_offer")

    // Cancel barter (aborts the staged offer + closes the native window).
    fun barterCancel() = runCommand("CMP:barter_cancel")

    private val BarterSide.wire: String
        get() = if (this == BarterSide.VENDOR) "vendor" else "player"

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
