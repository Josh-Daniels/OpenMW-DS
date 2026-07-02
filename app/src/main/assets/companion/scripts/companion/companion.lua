local types = require('openmw.types')
local self = require('openmw.self')
local ui = require('openmw.ui')
local core = require('openmw.core')
local ambient = require('openmw.ambient')
local camera = require('openmw.camera')
local nearby = require('openmw.nearby')
local util = require('openmw.util')

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

-- ===== Exporters (outbounD) =====

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
            -- Spell records carry no icon of their own; use the first effect's
            -- icon, matching what the in-game spell menu shows.
            local icon = ""
            pcall(function()
                local effs = rec and rec.effects
                if effs and effs[1] and effs[1].effect then
                    icon = effs[1].effect.icon or ""
                end
            end)
            table.insert(parts, string.format(
                '{"id":"%s","name":"%s","type":"%s","icon":"%s"}',
                jsonEscape(spell.id), jsonEscape(nm), typeStr, jsonEscape(icon)))
        end
    end

    for _, item in ipairs(types.Actor.inventory(self):getAll(types.Book)) do
        local rec = types.Book.record(item)
        if rec.isScroll and rec.enchant and rec.enchant ~= "" then
            table.insert(parts, string.format(
                '{"id":"%s","name":"%s","type":"scroll","icon":"%s"}',
                jsonEscape(item.recordId), jsonEscape(itemName(item)),
                jsonEscape(rec.icon or "")))
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
local lastCharacterStr = nil

-- Attribute and skill IDs used as second param for parameterized effects.
local ATTR_IDS = {"strength","intelligence","willpower","agility","speed","endurance","personality","luck"}
local SKILL_IDS = {
    "acrobatics","alchemy","alteration","armorer","athletics","axe","block","bluntweapon",
    "conjuration","destruction","enchant","handtohand","heavyarmor","illusion","lightarmor",
    "longblade","marksman","mediumarmor","mercantile","mysticism","restoration","security",
    "shortblade","sneak","spear","speechcraft","unarmored"
}
-- Resolve the affected attribute/skill display name for a parameterized effect
-- (e.g. "Strength" for Fortify Attribute). Returns nil for plain effects.
local function effectArgName(e)
    local attr = e.affectedAttribute
    if attr then
        local arec = core.stats.Attribute.records[attr]
        return (arec and arec.name and arec.name ~= '') and arec.name or attr
    end
    local skill = e.affectedSkill
    if skill then
        local srec = core.stats.Skill.records[skill]
        return (srec and srec.name and srec.name ~= '') and srec.name or skill
    end
    return nil
end

-- Active magic effects for the HUD/Stats display. We iterate ACTIVE SPELLS
-- (types.Actor.activeSpells — the spells/abilities/enchantments/potions currently
-- in force), NOT the aggregate types.Actor.activeEffects query object. activeSpells
-- is exactly what the vanilla magic menu reads: it exposes each source's display
-- name (params.name, e.g. "Warwyrd") and each individual effect's own magnitude,
-- attribute/skill target, and icon. This lets us render "Fortify Attack —
-- Warwyrd: 10 pts" like the game does, which the query object cannot (it only
-- gives aggregate magnitude per effect id, with no source).
local function exportActiveEffects()
    local parts = {}

    pcall(function()
        for _, params in pairs(types.Actor.activeSpells(self)) do
            local source = ''
            pcall(function() source = params.name or '' end)
            for _, e in ipairs(params.effects) do
                pcall(function()
                    local rec = core.magic.effects.records[e.id]
                    local name = (rec and rec.name and rec.name ~= '') and rec.name or tostring(e.id)
                    local arg = effectArgName(e)
                    if arg and arg ~= '' then name = name .. ' ' .. arg end
                    local harmful = (rec and rec.harmful) or false
                    local icon = (rec and rec.icon) or ''
                    -- ActiveSpellEffect exposes the current magnitude as
                    -- `magnitudeThisFrame` (there is NO `.magnitude` on this usertype);
                    -- it's nil for NoMagnitude effects (water walking, etc.) → 0 = no "pts".
                    local mag = math.floor((e.magnitudeThisFrame or 0) + 0.5)
                    parts[#parts+1] = string.format(
                        '{"name":"%s","source":"%s","harmful":%s,"icon":"%s","magnitude":%d}',
                        jsonEscape(name), jsonEscape(source),
                        harmful and 'true' or 'false', jsonEscape(icon), mag)
                end)
            end
        end
    end)

    local str = table.concat(parts, ',')
    if str == lastActiveEffectsStr then return end
    lastActiveEffectsStr = str
    print('COMPANION_ACTIVE_EFFECTS:['..str..']')
end

-- Character header, attributes and skills for the Stats screen.
-- Verified against real OpenMW 0.52 Lua API (files/lua_api/openmw/types.lua,
-- files/lua_api/openmw/core.lua):
--   types.NPC.record(self)              -> NpcRecord {name, race, class, ...}
--   types.NPC.races.records[raceId]     -> RaceRecord {name, ...}
--   types.NPC.classes.records[classId]  -> ClassRecord {name, majorSkills, minorSkills, ...}
--   types.Player.getBirthSign(self)     -> birth sign id string
--   types.Player.birthSigns.records[id] -> BirthSignRecord {name, ...}
--   types.Actor.stats.level(self)       -> LevelStat {current, progress}
--   types.Actor.stats.attributes[id](self) -> AttributeStat {base, modified, modifier, damage}
--   types.NPC.stats.skills[id](self)       -> SkillStat {base, modified, modifier, damage, progress}
--   core.stats.Attribute.records[id]    -> AttributeRecord {name, ...}
--   core.stats.Skill.records[id]        -> SkillRecord {name, ...}
local function exportCharacter()
    local npcRec = types.NPC.record(self)
    local name = (npcRec and npcRec.name) or ""

    local raceName = ""
    pcall(function()
        local r = types.NPC.races.records[npcRec.race]
        if r and r.name and r.name ~= "" then raceName = r.name end
    end)

    local className = ""
    local majorSet, minorSet = {}, {}
    pcall(function()
        local c = types.NPC.classes.records[npcRec.class]
        if c then
            if c.name and c.name ~= "" then className = c.name end
            for _, s in ipairs(c.majorSkills or {}) do majorSet[s] = true end
            for _, s in ipairs(c.minorSkills or {}) do minorSet[s] = true end
        end
    end)

    local birthSignName = ""
    pcall(function()
        local signId = types.Player.getBirthSign(self)
        if signId and signId ~= "" then
            local b = types.Player.birthSigns.records[signId]
            if b and b.name and b.name ~= "" then birthSignName = b.name end
        end
    end)

    local level = 0
    pcall(function() level = math.floor(types.Actor.stats.level(self).current) end)

    local attrParts = {}
    for _, attrId in ipairs(ATTR_IDS) do
        local ok, stat = pcall(function() return types.Actor.stats.attributes[attrId](self) end)
        if ok and stat then
            local rec = core.stats.Attribute.records[attrId]
            local nm = (rec and rec.name and rec.name ~= "") and rec.name or attrId
            -- NOTE: icon is emitted on the streamed CHARDETAIL_ATTR line, NOT here,
            -- to keep this single COMPANION_CHARACTER line under the 4096-byte
            -- stdout-flush limit (see CLAUDE.md). Only the dynamic `progress`
            -- lives inline (small, and must update live).
            table.insert(attrParts, string.format(
                '{"id":"%s","name":"%s","current":%.1f,"base":%.1f}',
                jsonEscape(attrId), jsonEscape(nm), stat.modified, stat.base))
        end
    end

    local skillParts = {}
    for _, skillId in ipairs(SKILL_IDS) do
        local ok, stat = pcall(function() return types.NPC.stats.skills[skillId](self) end)
        if ok and stat then
            local rec = core.stats.Skill.records[skillId]
            local nm = (rec and rec.name and rec.name ~= "") and rec.name or skillId
            local cat = "misc"
            if majorSet[skillId] then cat = "major"
            elseif minorSet[skillId] then cat = "minor" end
            -- icon streamed on CHARDETAIL_SKILL (see attr note above); progress inline.
            local prog = stat.progress or 0
            table.insert(skillParts, string.format(
                '{"id":"%s","name":"%s","value":%.1f,"cat":"%s","progress":%.4f}',
                jsonEscape(skillId), jsonEscape(nm), stat.modified, cat, prog))
        end
    end

    local str = string.format(
        '{"name":"%s","race":"%s","class":"%s","birthSign":"%s","level":%d,"attributes":[%s],"skills":[%s]}',
        jsonEscape(name), jsonEscape(raceName), jsonEscape(className), jsonEscape(birthSignName),
        level, table.concat(attrParts, ','), table.concat(skillParts, ','))

    if str == lastCharacterStr then return end
    lastCharacterStr = str
    print('COMPANION_CHARACTER:' .. str)
end

-- Description / metadata for the tappable Stats popups. Verified against the
-- OpenMW 0.52 Lua API (files/lua_api/openmw/{core,types}.lua):
--   core.stats.Attribute.records[id] -> {name, description, icon}
--   core.stats.Skill.records[id]     -> {name, description, icon,
--                                        specialization (combat/magic/stealth),
--                                        attribute (governing attribute id)}
--   types.NPC.races.records[id]      -> {description, skills (map id->bonus),
--                                        spells (inherent ability ids)}
--   types.NPC.classes.records[id]    -> {description, specialization,
--                                        attributes (favored), majorSkills, minorSkills}
--   types.Actor.stats.level(self)    -> {current, progress}; total per level from
--                                        the iLevelUpTotal GMST.
--   Health/Magicka/Fatigue tooltips  -> the sHealthDesc / sMagDesc / sFatDesc GMSTs
--                                        (the localized ESM strings, not hardcoded).
--
-- This payload is large (many long paragraphs) so it CANNOT be one line — the
-- engine's stdout sink flushes at 4096 bytes and only the first chunk keeps its
-- COMPANION_ prefix. It is streamed START / <one record per line> / END, exactly
-- like the inventory and journal exports, and change-detected as a whole so it
-- only re-emits when something actually changes (e.g. a level-up).
local lastCharacterDetailStr = nil
local function exportCharacterDetail()
    local function capFirst(s)
        if not s or s == "" then return "" end
        return s:sub(1, 1):upper() .. s:sub(2)
    end

    local npcRec = types.NPC.record(self)

    -- Governed skills per attribute + one line per skill (desc/attr/spec).
    local governed = {}
    for _, aid in ipairs(ATTR_IDS) do governed[aid] = {} end
    local skillLines = {}
    for _, sid in ipairs(SKILL_IDS) do
        local ok, rec = pcall(function() return core.stats.Skill.records[sid] end)
        if ok and rec then
            local gov = rec.attribute or ""
            local govName = gov
            pcall(function()
                local arec = core.stats.Attribute.records[gov]
                if arec and arec.name and arec.name ~= "" then govName = arec.name end
            end)
            local nm = (rec.name and rec.name ~= "" and rec.name) or sid
            if governed[gov] then table.insert(governed[gov], nm) end
            skillLines[#skillLines + 1] = string.format(
                'COMPANION_CHARDETAIL_SKILL:{"id":"%s","desc":"%s","attr":"%s","spec":"%s","icon":"%s"}',
                jsonEscape(sid), jsonEscape(rec.description or ""),
                jsonEscape(govName), jsonEscape(capFirst(tostring(rec.specialization or ""))),
                jsonEscape(rec.icon or ""))
        end
    end

    -- One line per attribute (desc + the list of skills it governs).
    local attrLines = {}
    for _, aid in ipairs(ATTR_IDS) do
        local ok, rec = pcall(function() return core.stats.Attribute.records[aid] end)
        if ok and rec then
            local skJson = {}
            for _, snm in ipairs(governed[aid] or {}) do
                skJson[#skJson + 1] = '"' .. jsonEscape(snm) .. '"'
            end
            attrLines[#attrLines + 1] = string.format(
                'COMPANION_CHARDETAIL_ATTR:{"id":"%s","desc":"%s","skills":[%s],"icon":"%s"}',
                jsonEscape(aid), jsonEscape(rec.description or ""),
                table.concat(skJson, ','), jsonEscape(rec.icon or ""))
        end
    end

    -- Health / Magicka / Fatigue descriptions from the ESM string GMSTs.
    local dynLines = {}
    local dynMap = { health = "sHealthDesc", magicka = "sMagDesc", fatigue = "sFatDesc" }
    for _, id in ipairs({ "health", "magicka", "fatigue" }) do
        local desc = ""
        pcall(function() desc = tostring(core.getGMST(dynMap[id]) or "") end)
        dynLines[#dynLines + 1] = string.format(
            'COMPANION_CHARDETAIL_DYN:{"id":"%s","desc":"%s"}', id, jsonEscape(desc))
    end

    -- Race: description, skill bonuses, and inherent abilities (by display name).
    local raceLine = ""
    pcall(function()
        local r = types.NPC.races.records[npcRec.race]
        if r then
            local skJson = {}
            if r.skills then
                for skid, bonus in pairs(r.skills) do
                    local srec = core.stats.Skill.records[skid]
                    local snm = (srec and srec.name and srec.name ~= "") and srec.name or skid
                    skJson[#skJson + 1] = '"' ..
                        jsonEscape(string.format("%s +%d", snm, math.floor((bonus or 0) + 0.5))) .. '"'
                end
            end
            local abJson = {}
            if r.spells then
                for _, spid in ipairs(r.spells) do
                    local sprec = core.magic.spells.records[spid]
                    local spnm = (sprec and sprec.name and sprec.name ~= "") and sprec.name or spid
                    abJson[#abJson + 1] = '"' .. jsonEscape(spnm) .. '"'
                end
            end
            raceLine = string.format(
                'COMPANION_CHARDETAIL_RACE:{"desc":"%s","skills":[%s],"abilities":[%s]}',
                jsonEscape(r.description or ""), table.concat(skJson, ','), table.concat(abJson, ','))
        end
    end)

    -- Class: description, specialization, favored attributes, major/minor skills.
    local classLine = ""
    pcall(function()
        local c = types.NPC.classes.records[npcRec.class]
        if c then
            local function skillNames(list)
                local out = {}
                for _, sid in ipairs(list or {}) do
                    local srec = core.stats.Skill.records[sid]
                    local snm = (srec and srec.name and srec.name ~= "") and srec.name or sid
                    out[#out + 1] = '"' .. jsonEscape(snm) .. '"'
                end
                return table.concat(out, ',')
            end
            local function attrNames(list)
                local out = {}
                for _, aid in ipairs(list or {}) do
                    local arec = core.stats.Attribute.records[aid]
                    local anm = (arec and arec.name and arec.name ~= "") and arec.name or aid
                    out[#out + 1] = '"' .. jsonEscape(anm) .. '"'
                end
                return table.concat(out, ',')
            end
            classLine = string.format(
                'COMPANION_CHARDETAIL_CLASS:{"desc":"%s","spec":"%s","attrs":[%s],"major":[%s],"minor":[%s]}',
                jsonEscape(c.description or ""), jsonEscape(capFirst(tostring(c.specialization or ""))),
                attrNames(c.attributes), skillNames(c.majorSkills), skillNames(c.minorSkills))
        end
    end)

    -- Level progress toward the next level.
    local levelLine = ""
    pcall(function()
        local lvl = types.Actor.stats.level(self)
        local prog = math.floor((lvl.progress or 0) + 0.5)
        local total = 10
        pcall(function() total = math.floor(core.getGMST("iLevelUpTotal")) end)
        levelLine = string.format('COMPANION_CHARDETAIL_LEVEL:{"progress":%d,"total":%d}', prog, total)
    end)

    local all = {}
    for _, l in ipairs(attrLines) do all[#all + 1] = l end
    for _, l in ipairs(skillLines) do all[#all + 1] = l end
    for _, l in ipairs(dynLines) do all[#all + 1] = l end
    if raceLine ~= "" then all[#all + 1] = raceLine end
    if classLine ~= "" then all[#all + 1] = classLine end
    if levelLine ~= "" then all[#all + 1] = levelLine end

    -- Change-detect the whole batch so it only streams when something changed.
    local blob = table.concat(all, '\n')
    if blob == lastCharacterDetailStr then return end
    lastCharacterDetailStr = blob

    print('COMPANION_CHARDETAIL_START:' .. #all)
    for _, l in ipairs(all) do print(l) end
    print('COMPANION_CHARDETAIL_END:' .. #all)
end

-- Display name of the first parameter of a MagicEffectWithParams entry.
-- Tries the referenced MagicEffect object first, then the effects registry.
local function magicEffectName(eff)
    local nm
    pcall(function()
        if eff.effect and eff.effect.name and eff.effect.name ~= "" then nm = eff.effect.name end
    end)
    if not nm or nm == "" then
        pcall(function()
            local mrec = core.magic.effects.records[eff.id]
            if mrec and mrec.name and mrec.name ~= "" then nm = mrec.name end
        end)
    end
    if nm and nm ~= "" then return nm end
    return nil
end

-- Returns (statVal, statKey, cond) for an inventory item.
--   statVal / statKey  short pre-formatted display strings ("" = no stat)
--   cond               0..1 condition ratio, or nil when no durability
-- Field names verified against openmw_types.html / mwlua source:
--   WeaponRecord.{chop,slash,thrust}{Min,Max}Damage, .type, .health
--   ArmorRecord.baseArmor, .health   Lockpick/ProbeRecord.quality, .maxCondition
--   per-instance condition: types.Item.itemData(item).condition
local function itemStats(item, cat)
    local okr, rec = pcall(function() return item.type.record(item) end)
    if not okr or not rec then return "", "", nil end

    -- Per-instance condition against a record max. A nil instance condition
    -- means the item is pristine (never modified) => treat as full.
    local function condRatio(maxHealth)
        if not maxHealth or maxHealth <= 0 then return nil end
        local cur = maxHealth
        pcall(function()
            local data = types.Item.itemData(item)
            if data and data.condition ~= nil then cur = data.condition end
        end)
        local r = cur / maxHealth
        if r < 0 then r = 0 elseif r > 1 then r = 1 end
        return r
    end

    if cat == "weapon" or cat == "ammo" then
        local WT = types.Weapon.TYPE
        local t = rec.type
        local key, mn, mx
        if t == WT.BluntOneHand or t == WT.BluntTwoClose or t == WT.BluntTwoWide
            or t == WT.AxeOneHand or t == WT.AxeTwoHand then
            key, mn, mx = "CHOP", rec.chopMinDamage, rec.chopMaxDamage
        elseif t == WT.SpearTwoWide or t == WT.MarksmanBow or t == WT.MarksmanCrossbow
            or t == WT.MarksmanThrown or t == WT.Arrow or t == WT.Bolt then
            key, mn, mx = "THRUST", rec.thrustMinDamage, rec.thrustMaxDamage
        else
            key, mn, mx = "SLASH", rec.slashMinDamage, rec.slashMaxDamage
        end
        mn = math.floor((mn or 0) + 0.5)
        mx = math.floor((mx or 0) + 0.5)
        return string.format("%d-%d", mn, mx), key, condRatio(rec.health)
    elseif types.Armor.objectIsInstance(item) then
        return string.format("%d", math.floor((rec.baseArmor or 0) + 0.5)), "ARMOR", condRatio(rec.health)
    elseif cat == "lockpick" then
        return string.format("Q%.1f", rec.quality or 0), "PICK", condRatio(rec.maxCondition)
    elseif cat == "probe" then
        return string.format("Q%.1f", rec.quality or 0), "PROBE", condRatio(rec.maxCondition)
    elseif cat == "potion" then
        local eff = rec.effects and rec.effects[1]
        if eff then
            local nm = magicEffectName(eff)
            if nm then
                local mag = 0
                pcall(function() mag = math.floor((eff.magnitudeMax or eff.magnitudeMin or 0) + 0.5) end)
                return string.format("%d", mag), string.upper(nm), nil
            end
        end
    elseif cat == "ingredient" then
        local n = 0
        pcall(function()
            if rec.effects then for _ in ipairs(rec.effects) do n = n + 1 end end
        end)
        if n > 0 then return tostring(n), "EFFECTS", nil end
    end
    return "", "", nil
end

-- Streamed as START / ITEM* / END (one item per line) rather than one giant
-- JSON array line. The engine's stdout sink flushes in 4096-byte chunks and
-- only the first chunk keeps its COMPANION_ prefix, so a single long inventory
-- line arrives truncated on the app side. Per-item lines stay well under that.
local function exportInventory()
    local all = types.Actor.inventory(self):getAll()
    print('COMPANION_INVENTORY_START:' .. #all)
    for _, item in ipairs(all) do
        local ok, rec = pcall(function() return item.type.record(item) end)
        local icon = (ok and rec and rec.icon) or ""
        local sid = stackId(item)
        local cat = itemCategory(item)
        local statVal, statKey, cond = itemStats(item, cat)
        local condField = ""
        if cond ~= nil then condField = string.format(',"cond":%.3f', cond) end
        print(string.format(
            'COMPANION_INVENTORY_ITEM:{"id":"%s","sid":"%s","name":"%s","count":%d,"cat":"%s","icon":"%s","statVal":"%s","statKey":"%s"%s}',
            jsonEscape(item.recordId), jsonEscape(sid), jsonEscape(itemName(item)),
            item.count, cat, jsonEscape(icon),
            jsonEscape(statVal), jsonEscape(statKey), condField))
    end
    print('COMPANION_INVENTORY_END:' .. #all)
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
        if r then
            -- questName is the proper display name (QS_Name info record).
            -- Fall back to r.name (raw ID) if questName is nil/empty.
            local qn = nil
            pcall(function() qn = r.questName end)
            if qn and qn ~= "" then
                name = qn
            elseif r.name and r.name ~= "" then
                name = r.name
            end
        end
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



-- ===== Combat target export =====
--
-- CONCEPT USED: (a) crosshair target. OpenMW 0.52 Lua does NOT expose a
-- combat/attack target or AI packages for querying (verified: neither the
-- I.Combat interface nor types.Actor exposes getActiveAiPackage / a target
-- field — see openmw.readthedocs.io/en/stable/reference/lua-scripting/
-- interface_combat.html and openmw_types.html, and the master
-- files/lua_api/openmw/types.lua). So concept (b) "most recently hit / actively
-- fought" is unavailable to a player script. Instead we raycast from the camera
-- through the viewport centre (the crosshair) to find the actor under it, and
-- gate on combat stance (weapon or spell readied) so the bar only shows "during
-- combat" rather than for every NPC the player glances at.
--
-- APIs (all verified against the stable docs):
--   camera.getPosition() -> Vector3           (openmw_camera.html)
--   camera.viewportToWorldVector(vector2)      -> Vector3 direction through a
--       viewport point; (0.5,0.5) = crosshair  (openmw_camera.html)
--   nearby.castRay(from, to, {ignore=...})     -> RayCastingResult
--       {hit, hitPos, hitNormal, hitObject}    (openmw_nearby.html)
--   types.Actor.getStance(actor) / STANCE      (openmw_types.html)
--   types.Actor.stats.dynamic.health(actor)    -> {current, base}; works on any
--       actor, not just the player             (files/lua_api/.../types.lua)
local TARGET_RAY_RANGE = 8192  -- ~one cell; covers melee, marksman and spells.

local function currentTargetActor()
    local ok, obj = pcall(function()
        -- Only while a weapon or spell is readied ("during combat").
        if types.Actor.getStance(self) == types.Actor.STANCE.Nothing then
            return nil
        end
        local origin = camera.getPosition()
        local dir = camera.viewportToWorldVector(util.vector2(0.5, 0.5))
        local dest = origin + dir * TARGET_RAY_RANGE
        local r = nearby.castRay(origin, dest, { ignore = self.object })
        if r and r.hit and r.hitObject then
            local o = r.hitObject
            if types.NPC.objectIsInstance(o) or types.Creature.objectIsInstance(o) then
                -- Skip corpses: only report actors that are still alive.
                if types.Actor.stats.dynamic.health(o).current > 0 then
                    return o
                end
            end
        end
        return nil
    end)
    if ok then return obj end
    return nil
end

local lastTargetStr = nil
local function exportTarget()
    local obj = currentTargetActor()
    local str = '{}'
    if obj then
        local nm = ""
        pcall(function()
            local rec = obj.type.record(obj)
            if rec and rec.name and rec.name ~= "" then nm = rec.name end
        end)
        if nm == "" then nm = obj.recordId or "?" end
        local cur, mx = 0, 0
        pcall(function()
            local hp = types.Actor.stats.dynamic.health(obj)
            cur, mx = hp.current, hp.base
        end)
        str = string.format(
            '{"name":"%s","health":{"current":%.1f,"max":%.1f}}',
            jsonEscape(nm), cur, mx)
    end
    -- Change-detection: only print when the target or its health changed.
    if str == lastTargetStr then return end
    lastTargetStr = str
    print('COMPANION_TARGET:' .. str)
end

-- Player standing: reputation, crime bounty, and faction memberships for the
-- Stats screen. Small payload, single line (a handful of factions at most, well
-- under the 4096-byte stdout-flush limit), change-detected. Verified against
-- OpenMW 0.52 mwlua source:
--   reputation:  types.NPC.stats.reputation(self).current   (stats.cpp:660 — on
--                the NPC stats table, NOT types.Actor)
--   bounty:      types.Player.getCrimeLevel(self)            (player.cpp:425 → int)
--   factions:    types.NPC.getFactions(self)                 (npc.cpp:494 → array of
--                serializeText faction ids)
--   rank:        types.NPC.getFactionRank(self, id)          (npc.cpp:311 → 1-based
--                Lua index, so it indexes .ranks directly)
--   name / rank title: core.factions.records[id].name /
--                core.factions.records[id].ranks[rank].name  (factionbindings.cpp:
--                57/61/100 — ranks is a 1-indexed array; rank from getFactionRank
--                is already 1-based, so ranks[rank] is the matching entry)
local lastPlayerStatusStr = nil
local function exportPlayerStatus()
    local reputation = 0
    pcall(function()
        reputation = math.floor((types.NPC.stats.reputation(self).current or 0) + 0.5)
    end)

    local bounty = 0
    pcall(function() bounty = types.Player.getCrimeLevel(self) or 0 end)

    local factionParts = {}
    pcall(function()
        for _, fid in ipairs(types.NPC.getFactions(self)) do
            local name, rank, rankName = fid, 0, ""
            pcall(function()
                local frec = core.factions.records[fid]
                if frec then
                    if frec.name and frec.name ~= "" then name = frec.name end
                    rank = types.NPC.getFactionRank(self, fid) or 0
                    local rankRec = frec.ranks and frec.ranks[rank]
                    if rankRec and rankRec.name and rankRec.name ~= "" then
                        rankName = rankRec.name
                    end
                end
            end)
            factionParts[#factionParts + 1] = string.format(
                '{"id":"%s","name":"%s","rank":%d,"rankName":"%s"}',
                jsonEscape(fid), jsonEscape(name), rank, jsonEscape(rankName))
        end
    end)

    local str = string.format('{"reputation":%d,"bounty":%d,"factions":[%s]}',
        reputation, bounty, table.concat(factionParts, ','))
    if str == lastPlayerStatusStr then return end
    lastPlayerStatusStr = str
    print('COMPANION_PLAYER_STATUS:' .. str)
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

-- ===== On-demand item / spell info (CMP:info) =====

local function capitalize(s)
    if not s or s == "" then return s end
    return s:sub(1, 1):upper() .. s:sub(2)
end

local function rangeName(r)
    local R = core.magic.RANGE
    if R then
        if r == R.Self then return "Self"
        elseif r == R.Touch then return "Touch"
        elseif r == R.Target then return "Target" end
    end
    return nil
end

-- Formats a MagicEffectWithParams as "<name> <mag> pts for <dur>s on <range>".
-- Returns (text, harmful). range suffix only when includeRange is true.
local function formatEffect(eff, includeRange)
    local name = magicEffectName(eff) or (eff.id and tostring(eff.id)) or "?"
    pcall(function()
        if eff.affectedAttribute then name = name .. " " .. capitalize(tostring(eff.affectedAttribute)) end
        if eff.affectedSkill then name = name .. " " .. capitalize(tostring(eff.affectedSkill)) end
    end)
    local mn = math.floor((eff.magnitudeMin or 0) + 0.5)
    local mx = math.floor((eff.magnitudeMax or 0) + 0.5)
    local text
    if mn == mx then text = string.format("%s %d pts", name, mn)
    else text = string.format("%s %d-%d pts", name, mn, mx) end
    local dur = eff.duration or 0
    if dur and dur > 0 then text = text .. string.format(" for %ds", math.floor(dur + 0.5)) end
    if includeRange then
        local rn = rangeName(eff.range)
        if rn then text = text .. " on " .. rn end
    end
    local harmful = false
    pcall(function()
        local mrec = core.magic.effects.records[eff.id]
        if mrec and mrec.harmful then harmful = true end
    end)
    return text, harmful
end

local function appendEffectList(effList, out, includeRange)
    pcall(function()
        for _, eff in ipairs(effList or {}) do
            local text, harmful = formatEffect(eff, includeRange)
            table.insert(out, string.format('{"t":"%s","h":%s}',
                jsonEscape(text), harmful and "true" or "false"))
        end
    end)
end

local function appendEnchantEffects(enchantId, out)
    if not enchantId or enchantId == "" then return end
    pcall(function()
        local ench = core.magic.enchantments.records[enchantId]
        if ench and ench.effects then appendEffectList(ench.effects, out, true) end
    end)
end

local function fmtNum(n)
    if n == nil then return nil end
    if math.floor(n) == n then return string.format("%d", n) end
    return string.format("%.1f", n)
end

-- Morrowind armor class (Light/Medium/Heavy) is not stored on the record; it's
-- derived by comparing the piece's weight to a per-slot base GMST scaled by the
-- fLightMaxMod / fMedMaxMod multipliers. Mirrors OpenMW's Armor::getEquipmentSkill.
local ARMOR_WEIGHT_GMST = nil
local function armorWeightClass(rec)
    if ARMOR_WEIGHT_GMST == nil then
        local AT = types.Armor.TYPE
        ARMOR_WEIGHT_GMST = {
            [AT.Helmet] = "iHelmWeight", [AT.Cuirass] = "iCuirassWeight",
            [AT.LPauldron] = "iPauldronWeight", [AT.RPauldron] = "iPauldronWeight",
            [AT.Greaves] = "iGreavesWeight", [AT.Boots] = "iBootsWeight",
            [AT.LGauntlet] = "iGauntletWeight", [AT.RGauntlet] = "iGauntletWeight",
            [AT.LBracer] = "iGauntletWeight", [AT.RBracer] = "iGauntletWeight",
            [AT.Shield] = "iShieldWeight",
        }
    end
    local cls
    pcall(function()
        local gmst = ARMOR_WEIGHT_GMST[rec.type]
        if not gmst then return end
        local iWeight = math.floor(core.getGMST(gmst))
        local lightMax = core.getGMST("fLightMaxMod")
        local medMax = core.getGMST("fMedMaxMod")
        local w = rec.weight or 0
        local epsilon = 0.0005
        if w <= iWeight * lightMax + epsilon then cls = "Light"
        elseif w <= iWeight * medMax + epsilon then cls = "Medium"
        else cls = "Heavy" end
    end)
    return cls
end

local function addRow(rows, k, v)
    if v ~= nil and v ~= "" then
        table.insert(rows, string.format('{"k":"%s","v":"%s"}', jsonEscape(k), jsonEscape(tostring(v))))
    end
end

local function exportInfo(arg)
    local kind, id = string.match(arg, "^(%a+):(.+)$")
    if not kind or not id then
        print("COMPANION_DEBUG: info - bad arg: " .. tostring(arg))
        return
    end

    local name, rows, effects = "", {}, {}

    if kind == "spell" then
        local rec = core.magic.spells.records[id]
        if not rec then print("COMPANION_DEBUG: info - spell not found: " .. id); return end
        name = (rec.name and rec.name ~= "" and rec.name) or id
        addRow(rows, "Magicka Cost", fmtNum(rec.cost))
        pcall(function()
            local e1 = rec.effects and rec.effects[1]
            if e1 then
                local mrec = core.magic.effects.records[e1.id]
                if mrec and mrec.school and mrec.school ~= "" then
                    addRow(rows, "School", capitalize(tostring(mrec.school)))
                end
            end
        end)
        appendEffectList(rec.effects, effects, true)

    elseif kind == "item" then
        local item = nil
        for _, it in ipairs(types.Actor.inventory(self):getAll()) do
            if it.recordId == id then item = it; break end
        end
        if not item then item = types.Actor.inventory(self):find(id) end
        if not item then print("COMPANION_DEBUG: info - item not found: " .. id); return end
        local okr, rec = pcall(function() return item.type.record(item) end)
        if not okr or not rec then print("COMPANION_DEBUG: info - no record: " .. id); return end
        name = (rec.name and rec.name ~= "" and rec.name) or id

        -- Instance condition (current / max), not the record's max health.
        local function condStr(maxHealth)
            if not maxHealth or maxHealth <= 0 then return nil end
            local cur = maxHealth
            pcall(function()
                local data = types.Item.itemData(item)
                if data and data.condition ~= nil then cur = data.condition end
            end)
            return string.format("%d / %d", math.floor(cur + 0.5), math.floor(maxHealth + 0.5))
        end
        local function enchantPts()
            if rec.enchantCapacity and rec.enchantCapacity > 0 then
                addRow(rows, "Enchant Pts", fmtNum(rec.enchantCapacity))
            end
        end

        -- Weight, with an armor class suffix for armor pieces, e.g. "16 (Heavy)".
        local weightStr = fmtNum(rec.weight)
        if weightStr and types.Armor.objectIsInstance(item) then
            local cls = armorWeightClass(rec)
            if cls then weightStr = weightStr .. " (" .. cls .. ")" end
        end
        addRow(rows, "Weight", weightStr)
        addRow(rows, "Value", fmtNum(rec.value))

        if types.Weapon.objectIsInstance(item) then
            addRow(rows, "Chop", string.format("%d-%d",
                math.floor((rec.chopMinDamage or 0) + 0.5), math.floor((rec.chopMaxDamage or 0) + 0.5)))
            addRow(rows, "Slash", string.format("%d-%d",
                math.floor((rec.slashMinDamage or 0) + 0.5), math.floor((rec.slashMaxDamage or 0) + 0.5)))
            addRow(rows, "Thrust", string.format("%d-%d",
                math.floor((rec.thrustMinDamage or 0) + 0.5), math.floor((rec.thrustMaxDamage or 0) + 0.5)))
            addRow(rows, "Reach", fmtNum(rec.reach))
            addRow(rows, "Speed", fmtNum(rec.speed))
            enchantPts()
            addRow(rows, "Condition", condStr(rec.health))
            appendEnchantEffects(rec.enchant, effects)
        elseif types.Armor.objectIsInstance(item) then
            addRow(rows, "Armor Rating", fmtNum(rec.baseArmor))
            enchantPts()
            addRow(rows, "Condition", condStr(rec.health))
            appendEnchantEffects(rec.enchant, effects)
        elseif types.Clothing.objectIsInstance(item) then
            enchantPts()
            appendEnchantEffects(rec.enchant, effects)
        elseif types.Potion.objectIsInstance(item) then
            appendEffectList(rec.effects, effects, false)
        elseif types.Ingredient.objectIsInstance(item) then
            appendEffectList(rec.effects, effects, false)
        elseif types.Lockpick.objectIsInstance(item) or types.Probe.objectIsInstance(item) then
            addRow(rows, "Quality", fmtNum(rec.quality))
            addRow(rows, "Uses Left", condStr(rec.maxCondition))
        elseif types.Book.objectIsInstance(item) then
            if rec.isScroll then
                appendEnchantEffects(rec.enchant, effects)
            elseif rec.skill and rec.skill ~= "" then
                addRow(rows, "Teaches", capitalize(tostring(rec.skill)))
            end
        end
        -- Misc and anything else: weight/value only (already added).
    else
        print("COMPANION_DEBUG: info - unknown kind: " .. tostring(kind))
        return
    end

    print(string.format('COMPANION_INFO:{"name":"%s","rows":[%s],"effects":[%s]}',
        jsonEscape(name), table.concat(rows, ','), table.concat(effects, ',')))
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
    if payload == "openmap" then
        -- 'Map' is a WINDOW of the 'Interface' mode (GM_Inventory), not a mode
        -- itself. Open Interface showing only the Map window (same as the ui.lua
        -- doc example I.UI.setMode('Interface', {windows = {'Map'}})). Reuses the
        -- AddUiMode event already used by the 'read' handler.
        self:sendEvent('AddUiMode', { mode = 'Interface', windows = { 'Map' } })
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
    elseif action == "info" then
        exportInfo(arg)
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
        exportCharacter()
        exportCharacterDetail()
        exportTarget()
        exportPlayerStatus()
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