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

return {
    eventHandlers = {
        CompanionDropItem = onDropItem
    }
}
