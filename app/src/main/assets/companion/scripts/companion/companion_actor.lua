-- companion_actor.lua — runs on every NPC and creature (registered
-- NPC,CREATURE: in companion.omwscripts).
--
-- Reports a TRUE combat target (concept b) to the player: when THIS actor's
-- active-Combat AI package targets the player, it sends the player a
-- `CompanionCombatTarget` event carrying itself + its current health. The
-- player script (companion.lua) turns that into COMPANION_TARGET.
--
-- Why here and not in companion.lua: openmw.interfaces.AI is @context local —
-- AI.getTargets("Combat") reads the AI sequence of the actor the script is
-- attached to, and the PLAYER has no combat AI package. So a player-only script
-- cannot read who is fighting it; each actor must report itself. This mirrors
-- OpenMW's own battle music (omw/music/actor.lua), which uses the exact same
-- AI.getTargets("Combat") + sendEvent(nearby.players) pattern.
local AI = require('openmw.interfaces').AI
local self = require('openmw.self')
local types = require('openmw.types')
local nearby = require('openmw.nearby')

-- Throttle: the player consumes COMPANION_TARGET on its 0.2s slow tick, so
-- there's no point flooding it with an event every frame.
local SEND_INTERVAL = 0.2
local sinceSend = 0.0

-- True if the player is among this actor's active Combat targets.
local function playerIsCombatTarget()
    local ok, found = pcall(function()
        for _, t in ipairs(AI.getTargets('Combat')) do
            if types.Player.objectIsInstance(t) then return true end
        end
        return false
    end)
    return ok and found
end

local function onUpdate(dt)
    -- Skip dead / out-of-processing-range actors (mirror omw/music/actor.lua).
    if types.Actor.isDeathFinished(self) or not types.Actor.isInActorsProcessingRange(self) then
        return
    end
    -- Cheap early-out: an idle, non-fleeing actor has no combat target, so skip
    -- scanning its AI sequence every frame (the same guard music/actor.lua
    -- uses). The player-side Hit fallback covers the brief window before an
    -- aggroed actor draws into a combat stance.
    if types.Actor.getStance(self) == types.Actor.STANCE.Nothing and not AI.isFleeing() then
        return
    end

    if not playerIsCombatTarget() then return end

    sinceSend = sinceSend + (dt or 0)
    if sinceSend < SEND_INTERVAL then return end
    sinceSend = 0.0

    -- Health as plain numbers: a DynamicStat userdata does NOT serialize across
    -- sendEvent, so pass {current, max}. (The player re-reads fresh from the
    -- actor object when it can; this is the fallback.)
    local hp
    pcall(function()
        local h = types.Actor.stats.dynamic.health(self.object)
        hp = { current = h.current, max = h.base }
    end)
    for _, p in ipairs(nearby.players) do
        p:sendEvent('CompanionCombatTarget', { actor = self.object, health = hp })
    end
end

return {
    engineHandlers = { onUpdate = onUpdate },
}
