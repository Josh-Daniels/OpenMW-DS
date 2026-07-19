local types = require('openmw.types')

local function onDropItem(data)
    local inv = types.Actor.inventory(data.actor)
    local item = inv:find(data.itemId)
    if item then
        local pos = data.actor.position
        local cell = data.actor.cell
        local want = data.count or 1
        pcall(function()
            -- NOTE: GameObject:teleport IGNORES a { count = N } option (it always
            -- moves the whole stack — verified against objectbindings.cpp), so to
            -- drop only N of a stack we must split the sub-stack off first. split()
            -- reduces the original stack by `want` and returns a new (disabled)
            -- stack of `want`; teleport then places + enables it in the world.
            -- split/teleport are global-script-only, which is why dropping lives
            -- here (companion_global.lua) rather than in the player script.
            local total = item.count or 1
            if want >= total then
                item:teleport(cell, pos)               -- drop the whole stack
            else
                item:split(want):teleport(cell, pos)   -- drop N of the stack
            end
        end)
    end
end

-- Resolve the container store of either an actor (corpse/NPC) or a plain container.
local function containerInv(obj)
    if types.Actor.objectIsInstance(obj) then
        return types.Actor.inventory(obj)
    elseif types.Container.objectIsInstance(obj) then
        return types.Container.content(obj)
    end
    return nil
end

-- Find an item in an inventory by its instance stack id (tostring(item.id)) rather
-- than recordId, so multiple stacks of the same record don't collide (the same
-- stack-identity concern as the pending inventory bug).
local function findBySid(inv, sid)
    for _, it in ipairs(inv:getAll()) do
        if tostring(it.id) == sid then return it end
    end
    return nil
end

-- Items a LIVING NPC is wearing are filtered out of the companion display (see
-- exportContainer's equippedItemIds in companion.lua). Take All must skip them too,
-- or it grabs equipped gear the player can't even see. Keyed by tostring(item.id).
-- nil = no filter: a corpse (dead actor) or plain container, where everything is
-- takeable — matches the display, which only filters for living NPCs.
local function equippedSet(obj)
    if not (types.Actor.objectIsInstance(obj) and not types.Actor.isDead(obj)) then return nil end
    local ok, eq = pcall(function() return types.Actor.getEquipment(obj) end)
    if not ok or not eq then return nil end
    local set = {}
    for _, witem in pairs(eq) do
        if witem then set[tostring(witem.id)] = true end
    end
    return set
end

-- Take/put/take-all between the container and the player. moveInto/split are
-- global-only, which is why this lives here (mirrors onDropItem). moveInto moves
-- the whole stack; split(N):moveInto moves only N (same idiom as the drop path).
local function onContainerTransfer(data)
    local container, player = data.container, data.player
    if not container or not player then return end
    if data.dir == 'takeall' or data.dir == 'dispose' then
        local inv = containerInv(container)
        if not inv then return end
        -- Skip equipped items (living NPC) AND items hidden by the pickpocket Sneak roll
        -- (hiddenSids, computed + passed by companion.lua) — Take All must match exactly
        -- what the overlay shows. Dispose is corpse-only, so both sets are empty there.
        local worn = equippedSet(container)
        local hidden = {}
        if data.hiddenSids then
            for _, sid in ipairs(data.hiddenSids) do hidden[tostring(sid)] = true end
        end
        -- Crime handling per container type: plain container = theft check (Phase 1); living NPC =
        -- pickpocket per-take roll (Phase 2); corpse (dead actor / dispose) = exempt.
        local isPickpocket = types.Actor.objectIsInstance(container) and not types.Actor.isDead(container)
        local isPlainContainer = types.Container.objectIsInstance(container)
        for _, item in ipairs(inv:getAll()) do
            local sid = tostring(item.id)
            if not (worn and worn[sid]) and not hidden[sid] then
                if isPickpocket then
                    -- Phase 2: per-item pick roll (value-scaled) via the native model. On DETECTION,
                    -- STOP the whole Take-All immediately — vanilla ends the interaction at the first
                    -- caught item (nothing after it is taken). The native model already committed the
                    -- crime and popped the container mode (closing the DS overlay via UiModeChanged).
                    local allowed = true
                    pcall(function() allowed = types.Player._runStandardPickpocketTake(player, item, item.count or 1) end)
                    if not allowed then break end
                    pcall(function() item:moveInto(player) end)
                else
                    -- Phase 1: per-item container-theft check (crime value = count × itemValue, so it
                    -- must be per-stack). Plain container only; a corpse is an Actor → skipped (exempt).
                    if isPlainContainer then
                        pcall(function()
                            types.Player._runStandardItemTaken(player, item, container, item.count or 1, true)
                        end)
                    end
                    pcall(function() item:moveInto(player) end)
                end
            end
        end
        -- Dispose also removes the emptied corpse/container (like the native "Dispose
        -- of Corpse" button). Queued after the moveInto actions above, so the items
        -- transfer first; pcall-guarded so anything that can't be removed is a no-op.
        if data.dir == 'dispose' then
            pcall(function() container:remove() end)
        end
        -- Ask the player script to close the container NOW that the bulk transfer is queued. The
        -- player can't safely close in the same frame it sent the transfer (it would race the
        -- deferred moveInto), and a timed close there never fires (paused/idle -> no frames). This
        -- cross-script event lands after the transfer's deferred adds, so the close is safe.
        pcall(function() player:sendEvent('CompanionContainerClose') end)
        return
    end
    local srcInv, dest
    if data.dir == 'take' then
        srcInv = containerInv(container); dest = player
    elseif data.dir == 'put' then
        srcInv = types.Actor.inventory(player); dest = container
    else
        return
    end
    if not srcInv then return end
    local item = findBySid(srcInv, data.sid)
    if not item then return end
    local want = data.count or 1
    local total = item.count or 1
    local take = math.min(want, total)
    -- COMPANION: crime handling for a single TAKE. 'put' (your own item into the container) is never
    -- a crime. Runs BEFORE the move (item still in the container).
    if data.dir == 'take' then
        if types.Actor.objectIsInstance(container) and not types.Actor.isDead(container) then
            -- Phase 2: living NPC = pickpocket. Native per-take pick roll (value-scaled). On DETECTION,
            -- deny this take and return — the item stays with the NPC; the native model already
            -- committed OT_Pickpocket and popped the container mode (DS overlay closes via UiModeChanged).
            local allowed = true
            pcall(function() allowed = types.Player._runStandardPickpocketTake(player, item, take) end)
            if not allowed then return end
        elseif types.Container.objectIsInstance(container) then
            -- Phase 1: plain-container theft (ownership + stolen-flag + commitCrime, alarm=true).
            pcall(function()
                types.Player._runStandardItemTaken(player, item, container, take, true)
            end)
        end
        -- corpse (dead actor): vanilla-exempt, no crime.
    end
    -- COMPANION: vanilla container-put restrictions (organic / capacity / weight) as an
    -- authoritative backstop — the player script already gates puts and shows feedback, but a
    -- stray 'put' event must never bypass the rule. Container-only (Actors have no put gate).
    -- Mirrors ContainerItemModel::onDropItem: organic OR capacity<=0 OR would-overflow → skip.
    if data.dir == 'put' and types.Container.objectIsInstance(container) then
        local organic = false
        pcall(function() organic = types.Container.record(container).isOrganic end)
        local capacity = 0
        pcall(function() capacity = types.Container.getCapacity(container) end)
        local encumbrance = 0
        pcall(function() encumbrance = types.Container.getEncumbrance(container) end)
        local itemWeight = 0
        pcall(function()
            local rec = item.type.record(item)
            itemWeight = (rec and rec.weight) or 0
        end)
        if organic or capacity <= 0 or (encumbrance + itemWeight * take) > capacity then
            return   -- blocked; do not move
        end
    end
    pcall(function()
        if want >= total then
            item:moveInto(dest)               -- move the whole stack
        else
            item:split(want):moveInto(dest)   -- move only N of the stack
        end
    end)
end

return {
    eventHandlers = {
        CompanionDropItem = onDropItem,
        CompanionContainerTransfer = onContainerTransfer,
    }
}
