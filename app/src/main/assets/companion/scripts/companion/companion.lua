local types = require('openmw.types')
local self = require('openmw.self')
local ui = require('openmw.ui')
local core = require('openmw.core')
local ambient = require('openmw.ambient')

local statsTimer = 0
local slowTimer = 0
local STATS_INTERVAL = 0.1
local SLOW_INTERVAL = 0.2

local SLOT_NAMES = {
    [1] = "cuirass", [2] = "greaves", [3] = "left_pauldron", [4] = "right_pauldron",
    [5] = "left_gauntlet", [6] = "right_gauntlet", [7] = "boots", [8] = "shirt",
    [9] = "pants", [10] = "skirt", [11] = "robe", [12] = "helmet",
    [13] = "amulet", [14] = "left_ring", [15] = "right_ring", [16] = "weapon",
    [17] = "shield", [18] = "ammo", [19] = "carried_left", [20] = "carried_right"
}

local function jsonEscape(s)
    s = string.gsub(s, '\\', '\\\\')
    s = string.gsub(s, '"', '\\"')
    return s
end

local function isCastable(spellId)
    local rec = core.magic.spells.records[spellId]
    if not rec then return false end
    local ST = core.magic.SPELL_TYPE
    return rec.type == ST.Spell or rec.type == ST.Power
end

local function exportSpells()
    local parts = {}
    for _, spell in ipairs(types.Actor.spells(self)) do
        if isCastable(spell.id) then
            table.insert(parts, '"' .. jsonEscape(spell.id) .. '"')
        end
    end
    print('COMPANION_SPELLS:[' .. table.concat(parts, ',') .. ']')
end

-- ===== Exporters (outbound, unchanged) =====

local function exportStats()
    local health = types.Actor.stats.dynamic.health(self)
    local magicka = types.Actor.stats.dynamic.magicka(self)
    local fatigue = types.Actor.stats.dynamic.fatigue(self)
    local pos = self.position
    local cell = self.cell.name
    if cell == "" then cell = "Wilderness" end
    print(string.format(
        'COMPANION_STATS:{"health":{"current":%.1f,"max":%.1f},'
        .. '"magicka":{"current":%.1f,"max":%.1f},'
        .. '"fatigue":{"current":%.1f,"max":%.1f},'
        .. '"cell":"%s","pos":{"x":%.1f,"y":%.1f,"z":%.1f}}',
        health.current, health.base,
        magicka.current, magicka.base,
        fatigue.current, fatigue.base,
        jsonEscape(cell), pos.x, pos.y, pos.z
    ))
end

local function exportSelectedSpell()
    local spell = types.Actor.getSelectedSpell(self)
    if spell then
        print('COMPANION_SELECTED_SPELL:"' .. jsonEscape(spell.id) .. '"')
    else
        print('COMPANION_SELECTED_SPELL:null')
    end
end

local function exportInventory()
    local parts = {}
    for _, item in ipairs(types.Actor.inventory(self):getAll()) do
        table.insert(parts, string.format(
            '{"id":"%s","count":%d}', jsonEscape(item.recordId), item.count))
    end
    print('COMPANION_INVENTORY:[' .. table.concat(parts, ',') .. ']')
end

local function exportEquipment()
    local parts = {}
    for slot, item in pairs(types.Actor.getEquipment(self)) do
        local slotName = SLOT_NAMES[slot] or ("slot" .. tostring(slot))
        table.insert(parts, string.format(
            '"%s":"%s"', slotName, jsonEscape(item.recordId)))
    end
    print('COMPANION_EQUIPMENT:{' .. table.concat(parts, ',') .. '}')
end

-- Play a generic equip/unequip sound (the data path skips the engine's
-- normal equip sound, so we trigger one ourselves). Polish per-item later.
local function playEquipSound(equipping)
    local soundId = equipping and "Item Misc Up" or "Item Misc Down"
    local ok, err = pcall(function()
        ambient.playSound(soundId)
    end)
    if not ok then
        print("COMPANION_DEBUG: sound error: " .. tostring(err))
    end
end

-- ===== Inbound actions (CMP: channel) =====

-- Best-effort slot lookup for equipping. This is the part most likely to
-- need tweaking if some item types don't equip — watch the COMPANION_DEBUG log.
local function slotForItem(item, currentEquip)
    if types.Weapon.objectIsInstance(item) then
        local wt = types.Weapon.record(item).type
        local WT = types.Weapon.TYPE
        if wt == WT.Arrow or wt == WT.Bolt then
            return 18
        end
        return 16
    elseif types.Lockpick.objectIsInstance(item) then
        return 16  -- carried_right, used with attack button
    elseif types.Probe.objectIsInstance(item) then
        return 16  -- carried_right, used with attack button
    elseif types.Armor.objectIsInstance(item) then
        local t = types.Armor.record(item).type
        local AT = types.Armor.TYPE
        local map = {
            [AT.Helmet]=12, [AT.Cuirass]=1, [AT.LPauldron]=3, [AT.RPauldron]=4,
            [AT.Greaves]=2, [AT.Boots]=7, [AT.LGauntlet]=5, [AT.RGauntlet]=6,
            [AT.Shield]=17, [AT.LBracer]=5, [AT.RBracer]=6,
        }
        return map[t]
    elseif types.Clothing.objectIsInstance(item) then
        local t = types.Clothing.record(item).type
        local CT = types.Clothing.TYPE
        if t == CT.Ring then
            if currentEquip[14] ~= nil then return 15 else return 14 end
        end
        local map = {
            [CT.Amulet]=13, [CT.Shirt]=8, [CT.Pants]=9, [CT.Skirt]=10,
            [CT.Robe]=11, [CT.Shoes]=7, [CT.LGlove]=5, [CT.RGlove]=6,
        }
        return map[t]
    end
    return nil
end

local function equipItem(itemId)
    local item = types.Actor.inventory(self):find(itemId)
    if not item then
        print("COMPANION_DEBUG: equip - not found: " .. itemId)
        return
    end
    local equip = types.Actor.getEquipment(self)
    local slot = slotForItem(item, equip)
    if slot == nil then
        print("COMPANION_DEBUG: equip - no slot for: " .. itemId)
        return
    end
    equip[slot] = item
    types.Actor.setEquipment(self, equip)
    playEquipSound(true)
    print("COMPANION_DEBUG: equipped " .. itemId .. " -> slot " .. slot)
end

local function unequipItem(itemId)
    local equip = types.Actor.getEquipment(self)
    local changed = false
    for slot, item in pairs(equip) do
        if item.recordId == itemId then
            equip[slot] = nil
            changed = true
        end
    end
    if changed then
        types.Actor.setEquipment(self, equip)
        playEquipSound(false)
        print("COMPANION_DEBUG: unequipped " .. itemId)
    else
        print("COMPANION_DEBUG: unequip - not worn: " .. itemId)
    end
end

local function onConsoleCommand(mode, command)
    if mode ~= "Companion" then return end
    if string.sub(command, 1, 4) ~= "CMP:" then return end
    local payload = string.sub(command, 5)
    local action, arg = string.match(payload, "^(%S+)%s+(.+)$")
    if not action then return end

    if action == "spell" then
        local spell = core.magic.spells.records[arg]
        if spell then
            types.Actor.setSelectedSpell(self, spell)
            print("COMPANION_DEBUG: selected spell " .. arg)
        else
            print("COMPANION_DEBUG: spell not found: " .. arg)
        end
        exportSelectedSpell()
    elseif action == "equip" then
        equipItem(arg)
	exportEquipment()
        exportInventory()
    elseif action == "unequip" then
        unequipItem(arg)
	exportEquipment()
        exportInventory()
    elseif action == "drop" then
        local id, countStr = string.match(arg, "^(.+)|(%d+)$")
        if id then
            core.sendGlobalEvent('CompanionDropItem',
                { actor = self.object, itemId = id, count = tonumber(countStr) })
            print("COMPANION_DEBUG: drop " .. id .. " x" .. countStr)
	exportInventory()
        end
    end
end

-- ===== Handlers =====

local function onUpdate(dt)
    statsTimer = statsTimer + dt
    if statsTimer >= STATS_INTERVAL then
        statsTimer = 0
        exportStats()
    end
    slowTimer = slowTimer + dt
    if slowTimer >= SLOW_INTERVAL then
        slowTimer = 0
        exportSpells()
	exportSelectedSpell()
        exportInventory()
        exportEquipment()
    end
end

local function onActive()
    ui.setConsoleMode("Companion")
    print("COMPANION_DEBUG: companion console mode active")
end

return {
    engineHandlers = {
        onUpdate = onUpdate,
        onActive = onActive,
        onConsoleCommand = onConsoleCommand
    }
}