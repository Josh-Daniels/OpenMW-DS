local types = require('openmw.types')

local function onDropItem(data)
    local inv = types.Actor.inventory(data.actor)
    local item = inv:find(data.itemId)
    if item then
        local pos = data.actor.position
        pcall(function()
            item:teleport(data.actor.cell, pos, { count = data.count })
        end)
    end
end

return {
    eventHandlers = {
        CompanionDropItem = onDropItem
    }
}
