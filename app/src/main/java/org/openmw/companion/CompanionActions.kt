package org.openmw.companion

import android.util.Log
import org.openmw.utils.automateCommands

/**
 * All companion actions now route through the CMP: Lua channel.
 * Companion console mode is always on (set in companion.lua), so vanilla
 * console commands no longer apply here — everything is handled in Lua.
 */
object CompanionActions {

    fun equipItem(itemId: String) = runCommand("CMP:equip $itemId")

    fun unequipItem(itemId: String) = runCommand("CMP:unequip $itemId")

    // count is delimited with | because item ids contain spaces
    fun dropItem(itemId: String, count: Int = 1) = runCommand("CMP:drop $itemId|$count")

    fun selectSpell(spellId: String) = runCommand("CMP:spell $spellId")

    private fun runCommand(command: String) {
        try {
            automateCommands(command)
            Log.d(TAG, "Sent: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Command failed (overlay not ready?): $command", e)
        }
    }

    private const val TAG = "CompanionActions"
}