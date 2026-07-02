-- companion_actor.lua — runs on every NPC and creature (registered
-- NPC,CREATURE: in companion.omwscripts).
--
-- Reports a combat target to the player. Two kinds of report, distinguished by
-- the event's `reason` field (the player script prioritises them differently):
--
--   reason = 'hit' — the PLAYER just attacked THIS actor (swung at it, hit or
--       miss). Delivered via the `Hit` local event on the victim (this actor).
--       This is the strongest signal of "who the player is fighting right now",
--       so the player script always (re)locks the target bar onto it.
--   reason = 'ai'  — this actor's active-Combat AI package is targeting the
--       player (AI.getTargets("Combat")). Ambient background signal: the player
--       script only uses it to seed an initial target or refresh the one it's
--       already showing — it will NOT let one enemy's ambient report steal the
--       bar from the enemy the player last attacked.
--
-- Why here and not in companion.lua: openmw.interfaces.AI is @context local —
-- AI.getTargets("Combat") reads the AI sequence of the actor the script is
-- attached to, and the PLAYER has no combat AI package. So a player-only script
-- cannot read who is fighting it; each actor must report itself. The `Hit`
-- event is likewise delivered to the victim (this actor), so player-attack
-- detection also has to live here. The ambient path mirrors OpenMW's own battle
-- music (omw/music/actor.lua), which uses the same AI.getTargets("Combat") +
-- sendEvent(nearby.players) pattern.
local AI = require('openmw.interfaces').AI
local self = require('openmw.self')
local types = require('openmw.types')
local nearby = require('openmw.nearby')

-- Throttle: the player consumes COMPANION_TARGET on its 0.2s slow tick, so
-- there's no point flooding it with an ambient event every frame. (Hit reports
-- bypass this — a discrete player attack is sent immediately.)
local SEND_INTERVAL = 0.2
local sinceSend = 0.0

-- Send a CompanionCombatTarget report (this actor + a health snapshot) to the
-- player. Health as plain numbers: a DynamicStat userdata does NOT serialize
-- across sendEvent, so pass {current, max}. (The player re-reads fresh from the
-- actor object when it can; this is the fallback.)
local function sendReport(reason)
    local hp
    pcall(function()
        local h = types.Actor.stats.dynamic.health(self.object)
        hp = { current = h.current, max = h.base }
    end)
    for _, p in ipairs(nearby.players) do
        p:sendEvent('CompanionCombatTarget',
            { actor = self.object, health = hp, reason = reason })
    end
end

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
    -- uses). The Hit handler below covers the player-attack case regardless of
    -- stance, so an idle NPC the player swings at still reports immediately.
    if types.Actor.getStance(self) == types.Actor.STANCE.Nothing and not AI.isFleeing() then
        return
    end

    if not playerIsCombatTarget() then return end

    sinceSend = sinceSend + (dt or 0)
    if sinceSend < SEND_INTERVAL then return end
    sinceSend = 0.0

    sendReport('ai')
end

-- The PLAYER attacked this actor. `Hit` is a LOCAL event delivered to the
-- victim (this actor); data.attacker is who struck it. Fires on to-hit MISSES
-- too (a swing at this actor that a valid line-of-fire lets register), so it
-- tracks "the enemy the player just swung at" even before damage lands. Report
-- immediately with reason='hit' so the player bar locks onto us.
local function onActorHit(data)
    if not data or not data.attacker then return end
    if types.Actor.isDeathFinished(self) then return end
    if not types.Player.objectIsInstance(data.attacker) then return end
    sinceSend = 0.0  -- reset the ambient throttle so we don't double-send
    sendReport('hit')
end

return {
    engineHandlers = { onUpdate = onUpdate },
    eventHandlers = { Hit = onActorHit },
}
