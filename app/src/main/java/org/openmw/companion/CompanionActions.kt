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
