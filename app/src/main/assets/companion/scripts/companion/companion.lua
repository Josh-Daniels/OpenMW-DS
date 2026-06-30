local types = require('openmw.types')
local self = require('openmw.self')
local ui = require('openmw.ui')
local core = require('openmw.core')
local ambient = require('openmw.ambient')

local statsTimer = 0
local slowTimer = 0
local journalTimer = 0
local STATS_INTERVAL = 0.1
local SLOW_INTERVAL = 0.2
local JOURNAL_INTERVAL = 5.0

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
    s = string.gsub(s, '\n', '\\n')
    s = string.gsub(s, '\r', '\\r')
    s = string.gsub(s, '\t', '\\t')
    -- strip remaining ASCII control characters (0x00-0x1F except already handled)
    s = string.gsub(s, '%c', '')
    return s
end

local function isCastable(spellId)
    local rec = core.magic.spells.records[spellId]
    if not rec then return false end
    local ST = core.magic.SPELL_TYPE
    return rec.type == ST.Spell or rec.type == ST.Power
end

local function itemName(item)
    local ok, rec = pcall(function() return item.type.record(item) end)
    local nm = (ok and rec and rec.name) or nil
    if nm and nm ~= "" then return nm end
    return item.recordId
end

-- ===== Exporters (outbound) =====

local function exportStats()
    local health = types.Actor.stats.dynamic.health(self)
    local magicka = types.Actor.stats.dynamic.magicka(self)
    local fatigue = types.Actor.stats.dynamic.fatigue(self)
    local cell = self.cell and self.cell.name or ""
    local pos = self.position
    print(string.format(
        'COMPANION_STATS:{"health":{"current":%.1f,"max":%.1f},"magicka":{"current":%.1f,"max":%.1f},"fatigue":{"current":%.1f,"max":%.1f},"cell":"%s","pos":{"x":%.1f,"y":%.1f,"z":%.1f}}',
        health.current, health.base,
        magicka.current, magicka.base,
        fatigue.current, fatigue.base,
        jsonEscape(cell),
        pos.x, pos.y, pos.z
    ))
end

local function exportSpells()
    local parts = {}

    for _, spell in ipairs(types.Actor.spells(self)) do
        if isCastable(spell.id) then
            local rec = core.magic.spells.records[spell.id]
            local typeStr = (rec and rec.type == core.magic.SPELL_TYPE.Power) and "power" or "spell"
            local nm = (rec and rec.name and rec.name ~= "") and rec.name or spell.id
            table.insert(parts, string.format(
                '{"id":"%s","name":"%s","type":"%s"}',
                jsonEscape(spell.id), jsonEscape(nm), typeStr))
        end
    end

    for _, item in ipairs(types.Actor.inventory(self):getAll(types.Book)) do
        local rec = types.Book.record(item)
        if rec.isScroll and rec.enchant and rec.enchant ~= "" then
            table.insert(parts, string.format(
                '{"id":"%s","name":"%s","type":"scroll"}',
                jsonEscape(item.recordId), jsonEscape(itemName(item))))
        end
    end

    print('COMPANION_SPELLS:[' .. table.concat(parts, ',') .. ']')
end

local function exportSelectedSpell()
    local spell = types.Actor.getSelectedSpell(self)
    if spell then
        print('COMPANION_SELECTED_SPELL:"' .. jsonEscape(spell.id) .. '"')
        return
    end
    local item = types.Actor.getSelectedEnchantedItem(self)
    if item then
        print('COMPANION_SELECTED_SPELL:"' .. jsonEscape(item.recordId) .. '"')
        return
    end
    print('COMPANION_SELECTED_SPELL:null')
end

local function itemCategory(item)
    if types.Weapon.objectIsInstance(item) then
        local wt = types.Weapon.record(item).type
        local WT = types.Weapon.TYPE
        if wt == WT.Arrow or wt == WT.Bolt then return "ammo" end
        return "weapon"
    elseif types.Armor.objectIsInstance(item) then
        local t = types.Armor.record(item).type
        local AT = types.Armor.TYPE
        local map = {
            [AT.Helmet]="helmet", [AT.Cuirass]="cuirass",
            [AT.LPauldron]="left_pauldron", [AT.RPauldron]="right_pauldron",
            [AT.Greaves]="greaves", [AT.Boots]="boots",
            [AT.LGauntlet]="left_gauntlet", [AT.RGauntlet]="right_gauntlet",
            [AT.Shield]="shield", [AT.LBracer]="left_gauntlet",
            [AT.RBracer]="right_gauntlet",
        }
        return map[t] or "armor"
    elseif types.Clothing.objectIsInstance(item) then
        local t = types.Clothing.record(item).type
        local CT = types.Clothing.TYPE
        local map = {
            [CT.Amulet]="amulet", [CT.Ring]="left_ring",
            [CT.Shirt]="shirt", [CT.Pants]="pants", [CT.Skirt]="skirt",
            [CT.Robe]="robe", [CT.Shoes]="boots",
            [CT.LGlove]="left_gauntlet", [CT.RGlove]="right_gauntlet",
        }
        return map[t] or "clothing"
    elseif types.Lockpick.objectIsInstance(item) then return "lockpick"
        elseif types.Probe.objectIsInstance(item) then return "probe"
            elseif types.Light.objectIsInstance(item) then return "carried_left"
            elseif types.Book.objectIsInstance(item) then
                local rec = types.Book.record(item)
                if rec.isScroll then return "scroll" end
                return "book"
    else return "misc" end
end

local function exportInventory()
    local parts = {}
    for _, item in ipairs(types.Actor.inventory(self):getAll()) do
        table.insert(parts, string.format(
            '{"id":"%s","name":"%s","count":%d,"cat":"%s"}',
            jsonEscape(item.recordId), jsonEscape(itemName(item)),
            item.count, itemCategory(item)))
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

-- Lazy per-questId lookup from core.dialogue.journal.records.
-- The .name field returns the ESM's original CamelCase id string
-- (e.g. "A1_1_FindSpymaster") which the app prettifies further.
local questNameCache = {}
local journalRecords = nil

local function getQuestName(questId)
    local cached = questNameCache[questId]
    if cached ~= nil then return cached end
    local name = ""
    pcall(function()
        if not journalRecords then
            journalRecords = core.dialogue.journal.records
        end
        local r = journalRecords[questId]
        if r and r.name then name = r.name end
    end)
    questNameCache[questId] = name
    return name
end

-- NOTE: OpenMW 0.52 Lua does not expose quest completion status.
-- Probed: j.questStages, j.quests, entry.stage/isFinished, types.Player.getQuestStage,
-- core.getJournalIndex — all nil or ERR. Active/done split requires a C++ engine change.

local journalExportedCount = -1
local function exportJournal()
    local ok, err = pcall(function()
        local j = types.Player.journal(self)
        local entries = j.journalTextEntries
        local count = #entries
        if count == journalExportedCount then return end
        journalExportedCount = count

        -- Count unique questIds and probe quest stage API on first export.
        local seen = {}; local uniqueQ = 0
        for i = 1, count do
            pcall(function()
                local qid = tostring(entries[i].questId or "")
                if not seen[qid] then seen[qid] = true; uniqueQ = uniqueQ + 1 end
            end)
        end
        print("COMPANION_DEBUG: journal uniqueQ=" .. uniqueQ .. " entries=" .. count)

        print('COMPANION_JOURNAL_START:' .. count)
        for i = 1, count do
            -- Protect each entry individually so one bad entry can't abort the whole export.
            local ok2, err2 = pcall(function()
                local e = entries[i]
                if not e then return end
                local qid = tostring(e.questId or "")
                local name = getQuestName(qid)
                print(string.format(
                    'COMPANION_JOURNAL_ENTRY:{"q":"%s","n":"%s","t":"%s","d":%d,"m":%d,"dom":%d}',
                    jsonEscape(qid), jsonEscape(name), jsonEscape(e.text or ""),
                    e.day or 0, e.month or 0, e.dayOfMonth or 0))
            end)
            if not ok2 then
                print("COMPANION_DEBUG: entry " .. i .. " err=" .. tostring(err2))
            end
        end
        print('COMPANION_JOURNAL_END:' .. count)
    end)
    if not ok then
        print("COMPANION_DEBUG: journal error: " .. tostring(err))
    end
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

-- ===== Inbound actions =====

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
    elseif types.Light.objectIsInstance(item) then
        return 19  -- carried_left (torches, lanterns)
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

local function dispatchCommand(command)
    if string.sub(command, 1, 4) ~= "CMP:" then return end
    local payload = string.sub(command, 5)
    -- no-arg commands first
    if payload == "journal" then
        journalExportedCount = -1
        exportJournal()
        return
    end
    local action, arg = string.match(payload, "^(%S+)%s+(.+)$")
    if not action then return end

    if action == "spell" then
        local spell = core.magic.spells.records[arg]
        if spell then
            types.Actor.setSelectedSpell(self, spell)
            ambient.playSound("Menu Click")
            print("COMPANION_DEBUG: selected spell " .. arg)
        else
            local item = types.Actor.inventory(self):find(arg)
            if item and types.Book.objectIsInstance(item)
                    and types.Book.record(item).isScroll then
                types.Actor.setSelectedEnchantedItem(self, item)
                ambient.playSound("Menu Click")
                print("COMPANION_DEBUG: selected scroll " .. arg)
            else
                print("COMPANION_DEBUG: spell/scroll not found: " .. arg)
            end
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
    elseif action == "read" then
        local item = types.Actor.inventory(self):find(arg)
        if item and types.Book.objectIsInstance(item) then
            self:sendEvent('AddUiMode', { mode = 'Book', target = item })
            print("COMPANION_DEBUG: reading " .. arg)
        else
            print("COMPANION_DEBUG: read - book not found: " .. arg)
        end
    end
end

local function onConsoleCommand(mode, command)
    if mode ~= "Companion" then return end
    dispatchCommand(command)
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
    journalTimer = journalTimer + dt
    if journalTimer >= JOURNAL_INTERVAL then
        journalTimer = 0
        exportJournal()
    end
end

local function onActive()
    ui.setConsoleMode("Companion")
    exportJournal()
end

return {
    engineHandlers = {
        onUpdate = onUpdate,
        onActive = onActive,
        onConsoleCommand = onConsoleCommand,
    }
}