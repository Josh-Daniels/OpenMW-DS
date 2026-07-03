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

-- Take/put/take-all between the container and the player. moveInto/split are
-- global-only, which is why this lives here (mirrors onDropItem). moveInto moves
-- the whole stack; split(N):moveInto moves only N (same idiom as the drop path).
local function onContainerTransfer(data)
    local container, player = data.container, data.player
    if not container or not player then return end
    if data.dir == 'takeall' then
        local inv = containerInv(container)
        if not inv then return end
        for _, item in ipairs(inv:getAll()) do
            pcall(function() item:moveInto(player) end)
        end
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
