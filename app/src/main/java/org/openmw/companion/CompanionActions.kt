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

    // Quest completion status is C++-only (androidmain.cpp handles this natively,
    // NOT Lua); reply arrives as a streamed COMPANION_JOURNAL_FINISHED_* block.
    fun refreshQuestStatus() = runCommand("CMP:questStatus")

    // Request an on-demand detail export; reply arrives as a COMPANION_INFO line.
    fun requestItemInfo(itemId: String) = runCommand("CMP:info item:$itemId")

    fun requestSpellInfo(spellId: String) = runCommand("CMP:info spell:$spellId")

    // Dialogue (bottom-screen) — handled natively in drainCompanionCommands, NOT Lua.
    // The arg is the topic/service DISPLAY string exactly as exported (may contain
    // spaces); the native side matches it against the dialogue window's list.
    fun selectDialogueTopic(topic: String) = runCommand("CMPDLG:topic:$topic")

    fun activateDialogueService(service: String) = runCommand("CMPDLG:service:$service")

    // Goodbye is not a topic — dedicated command mirroring the in-game Bye button.
    fun dialogueGoodbye() = runCommand("CMPDLG:goodbye")

    // Answer a mid-dialogue question/choice by its integer id.
    fun activateDialogueChoice(id: Int) = runCommand("CMPDLG:choice:$id")

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
