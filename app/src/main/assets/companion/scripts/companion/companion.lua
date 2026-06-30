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
    if cell == "" and self.cell then
        pcall(function()
            local regionId = self.cell.region
            if regionId then
                local reg = core.regions.records[regionId]
                if reg and reg.name and reg.name ~= "" then cell = reg.name end
            end
        end)
    end
    local pos = self.position

    local isExt = self.cell and self.cell.isExterior or false
    local gx, gy = 0, 0
    if isExt then
        pcall(function() gx = self.cell.gridX end)
        pcall(function() gy = self.cell.gridY end)
    end

    local rotZ = 0
    pcall(function() rotZ = self.rotation:getYaw() end)

    print(string.format(
        'COMPANION_STATS:{"health":{"current":%.1f,"max":%.1f},"magicka":{"current":%.1f,"max":%.1f},"fatigue":{"current":%.1f,"max":%.1f},"cell":"%s","pos":{"x":%.1f,"y":%.1f,"z":%.1f},"cellExt":%s,"cellGX":%d,"cellGY":%d,"rotZ":%.5f}',
        health.current, health.base,
        magicka.current, magicka.base,
        fatigue.current, fatigue.base,
        jsonEscape(cell),
        pos.x, pos.y, pos.z,
        isExt and "true" or "false",
        gx, gy,
        rotZ
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
    elseif types.Potion.objectIsInstance(item) then return "potion"
    elseif types.Ingredient.objectIsInstance(item) then return "ingredient"
    else return "misc" end
end

-- Returns a stable per-stack instance identifier (distinct from recordId so
-- two stacks of the same item type can be told apart). Uses item.id (OpenMW
-- instance RefId). Falls back to "" if the API is unavailable.
local function stackId(item)
    local ok, v = pcall(function() return tostring(item.id) end)
    if ok and v and v ~= "" then return v end
    return ""
end

local lastActiveEffectsStr = nil
local effectsDiagDone = false

-- Attribute and skill IDs used as second param for parameterized effects.
local ATTR_IDS = {"strength","intelligence","willpower","agility","speed","endurance","personality","luck"}
local SKILL_IDS = {
    "acrobatics","alchemy","alteration","armorer","athletics","axe","block","bluntweapon",
    "conjuration","destruction","enchant","handtohand","heavyarmor","illusion","lightarmor",
    "longblade","marksman","mediumarmor","mercantile","mysticism","restoration","security",
    "shortblade","sneak","spear","speechcraft","unarmored"
}
-- Effects that require a second param and are handled explicitly below.
local ATTR_EFFECT_IDS = {"absorbattribute","damageattribute","drainattribute","fortifyattribute","restoreattribute"}
local SKILL_EFFECT_IDS = {"absorbskill","damageskill","drainskill","fortifyskill","restoreskill"}

local function exportActiveEffects()
    local parts = {}
    local effectsObj = types.Actor.activeEffects(self)

    -- One-shot diagnostic: understand what's actually iterable.
    if not effectsDiagDone then
        effectsDiagDone = true
        -- Can we pairs() over EFFECT_TYPE?
        local d1 = {}
        local ok1 = pcall(function()
            local n = 0
            for k, v in pairs(core.magic.EFFECT_TYPE) do
                n = n + 1
                if n <= 5 then d1[#d1+1] = tostring(k)..':'..type(v)..':'..tostring(v) end
            end
            d1[#d1+1] = 'n='..n
        end)
        print('COMPANION_DEBUG_EFFECT: EFFECT_TYPE ok='..tostring(ok1)..' '..table.concat(d1,'|'))
        -- Can we pairs() over effects.records?
        local d2 = {}
        local ok2 = pcall(function()
            local n = 0
            for k, _ in pairs(core.magic.effects.records) do
                n = n + 1
                if n <= 5 then d2[#d2+1] = type(k)..':'..tostring(k) end
            end
            d2[#d2+1] = 'n='..n
        end)
        print('COMPANION_DEBUG_EFFECT: records ok='..tostring(ok2)..' '..table.concat(d2,'|'))
        -- Does getEffect return an object with magnitude, and what is it for a known ID?
        pcall(function()
            local p = effectsObj:getEffect('restorehealth')
            print('COMPANION_DEBUG_EFFECT: getEffect restorehealth type='..type(p)
                ..' nil='..(p==nil and 'y' or 'n')
                ..' mag='..tostring(p and p.magnitude))
        end)
        -- getEffect with attribute param
        pcall(function()
            local p = effectsObj:getEffect('fortifyattribute','strength')
            print('COMPANION_DEBUG_EFFECT: fortifyattr/strength type='..type(p)
                ..' mag='..tostring(p and p.magnitude))
        end)
    end

    -- Mark which effectIds are handled as parameterized (skip in non-param loop).
    local paramSet = {}
    for _, e in ipairs(ATTR_EFFECT_IDS) do paramSet[e] = true end
    for _, e in ipairs(SKILL_EFFECT_IDS) do paramSet[e] = true end

    -- Collect non-parameterized active effects.
    -- Strategy: build a list of effectIds from EFFECT_TYPE (preferred) or records.
    -- IMPORTANT: getEffect() always returns a non-nil ActiveEffect — check magnitude > 0
    -- to distinguish genuinely active effects from inactive defaults.
    local effectIds = {}

    -- Try EFFECT_TYPE: if values are strings they ARE the effectIds; otherwise try keys.
    local gotFromType = false
    pcall(function()
        for k, v in pairs(core.magic.EFFECT_TYPE) do
            if type(v) == 'string' then
                effectIds[#effectIds+1] = v
            elseif type(k) == 'string' then
                effectIds[#effectIds+1] = k:lower()
            end
        end
        gotFromType = #effectIds > 0
    end)

    -- Fallback: keys of effects.records.
    if not gotFromType then
        pcall(function()
            for k, _ in pairs(core.magic.effects.records) do
                effectIds[#effectIds+1] = tostring(k)
            end
        end)
    end

    for _, eid in ipairs(effectIds) do
        if not paramSet[eid] then
            pcall(function()
                local p = effectsObj:getEffect(eid)
                if p and p.magnitude > 0 then
                    local rec = core.magic.effects.records[eid]
                    local name = (rec and rec.name and rec.name ~= '') and rec.name or eid
                    local harmful = (rec and rec.harmful) or false
                    parts[#parts+1] = string.format('{"name":"%s","harmful":%s}',
                        jsonEscape(name), harmful and 'true' or 'false')
                end
            end)
        end
    end

    local function cap(s) return s:sub(1,1):upper() .. s:sub(2) end

    -- Attribute-parameterized effects (e.g. "Fortify Strength" = fortifyattribute + strength).
    for _, eid in ipairs(ATTR_EFFECT_IDS) do
        local rec = nil
        pcall(function() rec = core.magic.effects.records[eid] end)
        local baseName = (rec and rec.name and rec.name ~= '') and rec.name or eid
        local harmful = (rec and rec.harmful) or false
        for _, attr in ipairs(ATTR_IDS) do
            pcall(function()
                local p = effectsObj:getEffect(eid, attr)
                if p and p.magnitude > 0 then
                    parts[#parts+1] = string.format('{"name":"%s %s","harmful":%s}',
                        jsonEscape(baseName), cap(attr), harmful and 'true' or 'false')
                end
            end)
        end
    end

    -- Skill-parameterized effects (e.g. "Fortify Acrobatics" = fortifyskill + acrobatics).
    for _, eid in ipairs(SKILL_EFFECT_IDS) do
        local rec = nil
        pcall(function() rec = core.magic.effects.records[eid] end)
        local baseName = (rec and rec.name and rec.name ~= '') and rec.name or eid
        local harmful = (rec and rec.harmful) or false
        for _, skill in ipairs(SKILL_IDS) do
            pcall(function()
                local p = effectsObj:getEffect(eid, skill)
                if p and p.magnitude > 0 then
                    parts[#parts+1] = string.format('{"name":"%s %s","harmful":%s}',
                        jsonEscape(baseName), cap(skill), harmful and 'true' or 'false')
                end
            end)
        end
    end

    local str = table.concat(parts, ',')
    if str == lastActiveEffectsStr then return end
    lastActiveEffectsStr = str
    print('COMPANION_ACTIVE_EFFECTS:['..str..']')
end

local iconDiagDone = false
local stackDiagDone = false
local function exportInventory()
    local parts = {}
    for _, item in ipairs(types.Actor.inventory(self):getAll()) do
        local ok, rec = pcall(function() return item.type.record(item) end)
        local icon = (ok and rec and rec.icon) or ""
        local sid = stackId(item)
        if not iconDiagDone and ok and rec then
            iconDiagDone = true
            local keys = {}
            for k, _ in pairs(rec) do keys[#keys+1] = tostring(k) end
            print('COMPANION_DEBUG_ICON: id=' .. tostring(item.recordId)
                .. ' icon=' .. tostring(rec.icon)
                .. ' keys=' .. table.concat(keys, ','))
        end
        if not stackDiagDone then
            stackDiagDone = true
            print('COMPANION_DEBUG_STACK: recordId=' .. tostring(item.recordId)
                .. ' sid=' .. tostring(sid)
                .. ' same=' .. tostring(sid == item.recordId))
        end
        table.insert(parts, string.format(
            '{"id":"%s","sid":"%s","name":"%s","count":%d,"cat":"%s","icon":"%s"}',
            jsonEscape(item.recordId), jsonEscape(sid), jsonEscape(itemName(item)),
            item.count, itemCategory(item), jsonEscape(icon)))
    end
    print('COMPANION_INVENTORY:[' .. table.concat(parts, ',') .. ']')
end

local function exportEquipment()
    local parts = {}
    for slot, item in pairs(types.Actor.getEquipment(self)) do
        local slotName = SLOT_NAMES[slot] or ("slot" .. tostring(slot))
        local sid = stackId(item)
        -- Prefer instance id so Kotlin can match the exact equipped stack;
        -- fall back to recordId so the slot is never empty.
        local slotVal = (sid ~= "") and sid or item.recordId
        table.insert(parts, string.format(
            '"%s":"%s"', slotName, jsonEscape(slotVal)))
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

local function equipItem(arg)
    -- arg is preferably a per-stack instance id (from stackId()); fall back to
    -- recordId so old clients still work.
    local found = nil
    for _, item in ipairs(types.Actor.inventory(self):getAll()) do
        if stackId(item) == arg then
            found = item
            break
        end
    end
    if not found then
        -- Fallback: find by recordId (matches first stack when arg is a recordId).
        found = types.Actor.inventory(self):find(arg)
    end
    if not found then
        print("COMPANION_DEBUG: equip - not found: " .. arg)
        return
    end
    local equip = types.Actor.getEquipment(self)
    local slot = slotForItem(found, equip)
    if slot == nil then
        print("COMPANION_DEBUG: equip - no slot for: " .. arg)
        return
    end
    equip[slot] = found
    types.Actor.setEquipment(self, equip)
    playEquipSound(true)
    print("COMPANION_DEBUG: equipped " .. arg .. " -> slot " .. slot)
end

local function unequipItem(arg)
    local equip = types.Actor.getEquipment(self)
    local changed = false
    for slot, item in pairs(equip) do
        local sid = stackId(item)
        -- Match by instance id first; fall back to recordId for old clients.
        if sid == arg or (sid == "" and item.recordId == arg) then
            equip[slot] = nil
            changed = true
        end
    end
    if changed then
        types.Actor.setEquipment(self, equip)
        playEquipSound(false)
        print("COMPANION_DEBUG: unequipped " .. arg)
    else
        print("COMPANION_DEBUG: unequip - not worn: " .. arg)
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
        exportActiveEffects()
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