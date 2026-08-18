local types = require('openmw.types')
local self = require('openmw.self')
-- openmw.ui is no longer required: its only use was ui.setConsoleMode("Companion") in onActive,
-- removed because it hijacked the real console for the whole session (see the note there). Kept as
-- a comment rather than silently dropped so the absence reads as deliberate.
local core = require('openmw.core')
local ambient = require('openmw.ambient')
local camera = require('openmw.camera')
local nearby = require('openmw.nearby')
local util = require('openmw.util')
local interfaces = require('openmw.interfaces')
-- openmw.debug is a PLAYER-context package (luabindings.cpp initPlayerPackages), which this
-- script is, so the require succeeds — but it is only used by the Developer Tools actions, so
-- guard it rather than letting a future context change break the whole script at load time.
local debugApi = nil
pcall(function() debugApi = require('openmw.debug') end)

local statsTimer = 0
local slowTimer = 0
local journalTimer = 0
local STATS_INTERVAL = 0.1
local SLOW_INTERVAL = 0.2
local JOURNAL_INTERVAL = 5.0

-- Engine equipment-slot number -> companion slot label.
--
-- These are MWWorld::InventoryStore::Slot_* (inventorystore.hpp:33-51) — NOT an arbitrary
-- list. types.Actor.getEquipment() returns a table keyed by these numbers and setEquipment()
-- consumes the same numbering, so they must match the engine exactly. Max valid slot is 18:
-- setEquipment's loop (mwlua/types/actor.cpp) iterates 0..Slots-1, so any higher key is
-- silently dropped. (Entries [19]/[20] used to exist here and could never match anything.)
--
-- Slots 16/17 are really CarriedRight/CarriedLeft; kept labelled "weapon"/"shield" because
-- that is what they hold in practice and the HUD reads state.equipment["weapon"].
--
-- Keep in sync with slotForItem below — both describe the same engine slots, and a fix to
-- one alone desyncs COMPANION_EQUIPMENT's labels from what is actually equipped.
local SLOT_NAMES = {
    [0] = "helmet", [1] = "cuirass", [2] = "greaves", [3] = "left_pauldron",
    [4] = "right_pauldron", [5] = "left_gauntlet", [6] = "right_gauntlet", [7] = "boots",
    [8] = "shirt", [9] = "pants", [10] = "skirt", [11] = "robe",
    [12] = "left_ring", [13] = "right_ring", [14] = "amulet", [15] = "belt",
    [16] = "weapon", [17] = "shield", [18] = "ammo"
}

-- Ring-slot recency. Rings are the only item with TWO valid slots (12 = LeftRing,
-- 13 = RightRing), so when both are full something has to decide which one a newly equipped
-- ring evicts. We replace the LEAST recently equipped, which needs recency the engine does not
-- track for us.
--
-- lastRingSlot = the slot filled most recently; the other slot is therefore the older one.
-- nil means "unknown" (e.g. both rings were already on when the script loaded).
-- ringOccupants is the last-seen occupant per ring slot, used to notice a change — updated
-- from exportEquipment on the slow tick so rings equipped through the VANILLA menu update
-- recency too, not just ones equipped from the companion.
local lastRingSlot = nil
local ringOccupants = { [12] = nil, [13] = nil }

local function noteRingEquipped(slot)
    if slot == 12 or slot == 13 then lastRingSlot = slot end
end

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

-- Returns the enchantment record id on an item (rings/amulets/clothing/weapons/
-- books), or nil if the item is not enchanted. Wrapped in pcall because the
-- .enchant field only exists on enchantable record types.
local function itemEnchantId(item)
    local ok, enchId = pcall(function()
        local rec = item.type.record(item)
        return rec and rec.enchant
    end)
    if ok and enchId and enchId ~= "" then return enchId end
    return nil
end

-- ===== Exporters (outbounD) =====

-- All companion output goes straight to Kotlin (GameStateRepository) via the native
-- core.companionPush binding (androidmain.cpp companionPushLine → the onCompanionLine JNI
-- method), NOT through print()/Log(). This is the whole point of the disk-logging change:
-- companion lines never get written to openmw.log (print() → Log(Info) → the engine's file
-- sink used to write + flush every line to disk). Kotlin receives each line identically to
-- the old path, one line per call, so streamed exports (START/ITEM/END) are unchanged.
-- Single choke point so a future fallback/heartbeat decision (Phase 3) only touches here.
local function emit(line)
    core.companionPush(line)
end

-- ===== Save identity (COMPANION_SAVE_ID) =====
--
-- A token minted per PLAYTHROUGH and stored INSIDE the save file, giving the companion true
-- per-save identity for the first time.
--
-- Everything save-scoped on the companion side has until now been keyed by CHARACTER NAME
-- (FavouritesRepository, QuestPrefsRepository), because the character name was the only identity
-- the companion had — so two saves of the same character share one bucket. That is merely untidy
-- for a favourites slot, but it would be plainly WRONG for manual journal entries: entries from
-- one playthrough would surface, dated to days that never happened, inside a parallel save of the
-- same character.
--
-- MECHANISM: onSave/onLoad are engine handlers (components/lua/scriptscontainer.cpp — the returned
-- table is serialized into the .omwsave and handed straight back on load), so the token travels
-- with the save FILE. That also means it survives an app uninstall for the same reason the saves
-- themselves do: they live under /storage/emulated/0/OpenMW-DS/, not in app-private storage.
--
-- KNOWN AND ACCEPTED: a save branched from another (save-as, or a copied file) inherits the token,
-- so two branches of one playthrough share a bucket. That is usually the desired reading of "the
-- same playthrough", and is strictly better than keying on character name in either case.
--
-- KNOWN AND ONE-TIME: for a save created BEFORE this feature existed, onLoad is NOT called (the
-- script did not exist when that game was saved — see the engine_handlers docs) and onInit runs
-- instead, minting a fresh token. That token only becomes durable at the next save, so notes
-- written in that first session before saving end up under a bucket the following load will not
-- reuse. One-time cost per pre-existing save, and nothing is destroyed — those entries remain in
-- the JSON, just unreferenced.
local saveId = nil

-- math.random is seeded ONCE by the engine at startup from std::time(nullptr), after which
-- randomseed is deliberately stubbed out (components/lua/luastate.cpp) — so it is usable here but
-- must never be re-seeded. os.time() is pinned in as well: the sandbox exposes only os.date /
-- os.difftime / os.time, and the wall-clock prefix means two characters created in the same
-- session cannot collide even if the RNG stream were to repeat.
local function mintSaveId()
    local parts = {}
    for _ = 1, 4 do parts[#parts + 1] = string.format('%04x', math.random(0, 0xffff)) end
    -- Guarded: a missing `os` would raise "attempt to index global 'os'" and take the whole script
    -- down at load time. Four random chunks are unique enough on their own if it ever comes to it.
    local stamp = 0
    pcall(function() stamp = os.time() end)
    return string.format('%x-%s', stamp, table.concat(parts))
end

-- Emitted from onActive, which fires both at game start and on the freshly recreated player script
-- after every load — so Kotlin always learns the live save's token without polling for it.
local function emitSaveId()
    if saveId then emit('COMPANION_SAVE_ID:' .. saveId) end
end

local function onInit()
    saveId = mintSaveId()
end

local function onSave()
    return { saveId = saveId }
end

local function onLoad(data)
    -- A save written by an older companion build carries no token; mint one now so the rest of the
    -- feature always has an identity to key on.
    saveId = (data and data.saveId) or mintSaveId()
end

-- Change-detection: only emit (→ JNI → parse) when the line actually changed.
-- Same pattern as exportCharacter/exportContainer/etc. During genuinely static play
-- (standing still, full stats) this skips the whole 10 Hz emit; movement/regen still
-- re-emit promptly because pos/rotZ/stat values are in the string.
local lastStatsStr = nil

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

    -- Gold (count of the Gold_001 record) + encumbrance/capacity for the inventory header.
    local gold = 0
    pcall(function() gold = types.Actor.inventory(self):countOf("gold_001") end)
    local encumbrance, capacity = 0, 0
    pcall(function() encumbrance = types.Actor.getEncumbrance(self) end)
    pcall(function() capacity = types.Actor.getCapacity(self) end)

    local line = string.format(
        'COMPANION_STATS:{"health":{"current":%.1f,"max":%.1f},"magicka":{"current":%.1f,"max":%.1f},"fatigue":{"current":%.1f,"max":%.1f},"cell":"%s","pos":{"x":%.1f,"y":%.1f,"z":%.1f},"cellExt":%s,"cellGX":%d,"cellGY":%d,"rotZ":%.5f,"gold":%d,"encumbrance":%.1f,"capacity":%.1f}',
        health.current, health.base,
        magicka.current, magicka.base,
        fatigue.current, fatigue.base,
        jsonEscape(cell),
        pos.x, pos.y, pos.z,
        isExt and "true" or "false",
        gx, gy,
        rotZ,
        gold, encumbrance, capacity
    )
    if line == lastStatsStr then return end
    lastStatsStr = line
    emit(line)
end

local lastSpellsStr = nil

-- First-effect DESCRIPTION + school for a spell/enchantment effects list (effects[1]). The
-- description is the fuller "<name> <mag>pts for <dur>s (<range>)" form (e.g. "Frost Damage 25pts
-- for 1s (touch)"), not just the effect name. Either return may be "" when unavailable. Defined
-- here (before exportSpells) so it's visible to it; the magnitude/duration/range formatting mirrors
-- formatEffect (which is defined later in the file and so can't be referenced from here).
local function firstEffectInfo(effects)
    local text, school = "", ""
    pcall(function()
        local e1 = effects and effects[1]
        if not e1 then return end
        local name = ""
        if e1.effect and e1.effect.name and e1.effect.name ~= "" then name = e1.effect.name end
        local mrec = core.magic.effects.records[e1.id]
        if mrec then
            if name == "" and mrec.name and mrec.name ~= "" then name = mrec.name end
            if mrec.school and mrec.school ~= "" then school = tostring(mrec.school) end
        end
        -- Affected attribute/skill suffix (e.g. "Fortify Attribute Strength").
        local function cap(s) s = tostring(s); return s:sub(1, 1):upper() .. s:sub(2) end
        if e1.affectedAttribute then name = name .. " " .. cap(e1.affectedAttribute) end
        if e1.affectedSkill then name = name .. " " .. cap(e1.affectedSkill) end
        text = name
        -- Magnitude (omitted for no-magnitude effects like Water Walking where both are 0).
        local mn = math.floor((e1.magnitudeMin or 0) + 0.5)
        local mx = math.floor((e1.magnitudeMax or 0) + 0.5)
        if mn > 0 or mx > 0 then
            if mn == mx then text = string.format("%s %dpts", text, mn)
            else text = string.format("%s %d-%dpts", text, mn, mx) end
        end
        -- Duration.
        local dur = math.floor((e1.duration or 0) + 0.5)
        if dur > 0 then text = text .. string.format(" for %ds", dur) end
        -- Range.
        local R = core.magic.RANGE
        local rn = nil
        if R then
            if e1.range == R.Self then rn = "self"
            elseif e1.range == R.Touch then rn = "touch"
            elseif e1.range == R.Target then rn = "target" end
        end
        if rn then text = text .. " (" .. rn .. ")" end
    end)
    return text, school
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
            -- Extra stats for the spell row: first-effect name, school, magicka cost.
            local effName, school = firstEffectInfo(rec and rec.effects)
            local cost = 0
            pcall(function() cost = math.floor((rec.cost or 0) + 0.5) end)
            table.insert(parts, string.format(
                '{"id":"%s","name":"%s","type":"%s","icon":"%s","effect":"%s","school":"%s","cost":%d}',
                jsonEscape(spell.id), jsonEscape(nm), typeStr, jsonEscape(icon),
                jsonEscape(effName), jsonEscape(school), cost))
        end
    end

    for _, item in ipairs(types.Actor.inventory(self):getAll(types.Book)) do
        local rec = types.Book.record(item)
        if rec.isScroll and rec.enchant and rec.enchant ~= "" then
            -- Scrolls: effect only (no magicka cost / school — they're items, not learned spells).
            local effName = ""
            pcall(function()
                local ench = core.magic.enchantments.records[rec.enchant]
                if ench then effName = firstEffectInfo(ench.effects) end
            end)
            table.insert(parts, string.format(
                '{"id":"%s","name":"%s","type":"scroll","icon":"%s","effect":"%s"}',
                jsonEscape(item.recordId), jsonEscape(itemName(item)),
                jsonEscape(rec.icon or ""), jsonEscape(effName)))
        end
    end

    -- Cast-on-use enchanted items (rings, amulets, clothing, weapons whose
    -- enchantment is "cast when used"). These are the "magic items" the vanilla
    -- magic menu lists alongside spells and scrolls. Cast-on-strike and
    -- constant-effect items are excluded (they fire automatically), and scrolls
    -- (CastOnce) are already handled by the loop above. Emitted with type
    -- "scroll" so they land in the UI's existing usable-item section, plus an
    -- isItem flag and charge/maxCharge for future charge display.
    local ENCH = core.magic.ENCHANTMENT_TYPE
    for _, item in ipairs(types.Actor.inventory(self):getAll()) do
        local enchId = itemEnchantId(item)
        if enchId then
            local ench = core.magic.enchantments.records[enchId]
            if ench and ench.type == ENCH.CastOnUse then
                local maxCharge = ench.charge or 0
                local charge = maxCharge
                pcall(function()
                    local d = types.Item.itemData(item)
                    if d and d.enchantmentCharge ~= nil then charge = d.enchantmentCharge end
                end)
                local rec = item.type.record(item)
                local icon = (rec and rec.icon) or ""
                -- Enchanted items: effect + charge (already exported); no cost/school.
                local effName = firstEffectInfo(ench.effects)
                table.insert(parts, string.format(
                    '{"id":"%s","name":"%s","type":"scroll","icon":"%s","isItem":true,"charge":%d,"maxCharge":%d,"effect":"%s"}',
                    jsonEscape(item.recordId), jsonEscape(itemName(item)),
                    jsonEscape(icon),
                    math.floor(charge + 0.5), math.floor(maxCharge + 0.5), jsonEscape(effName)))
            end
        end
    end

    -- Streamed START / ITEM* / END (one small line each) instead of a single array line: the
    -- spell list can grow unbounded (a mage with many spells), and the added per-spell stats
    -- pushed the single line toward the 4096-byte stdout-flush truncation limit. Change-detected
    -- on the joined signature so an unchanged tick emits nothing.
    local sig = table.concat(parts, ',')
    if sig == lastSpellsStr then return end
    lastSpellsStr = sig
    emit('COMPANION_SPELLS_START')
    for _, p in ipairs(parts) do emit('COMPANION_SPELLS_ITEM:' .. p) end
    emit('COMPANION_SPELLS_END')
end

local lastSelectedSpellStr = nil

local function exportSelectedSpell()
    local line
    local spell = types.Actor.getSelectedSpell(self)
    if spell then
        line = 'COMPANION_SELECTED_SPELL:"' .. jsonEscape(spell.id) .. '"'
    else
        local item = types.Actor.getSelectedEnchantedItem(self)
        if item then
            line = 'COMPANION_SELECTED_SPELL:"' .. jsonEscape(item.recordId) .. '"'
        else
            line = 'COMPANION_SELECTED_SPELL:null'
        end
    end
    if line == lastSelectedSpellStr then return end
    lastSelectedSpellStr = line
    emit(line)
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
    -- Apparatus (mortar/retort/…) and Repair tools (hammers/prongs) are NOT worn —
    -- their native use() opens the Alchemy / Repair menu (ActionAlchemy/ActionRepair),
    -- so the UI routes a tap on them to CMP:use, not CMP:equip. Give them distinct
    -- categories so they're separable from generic misc.
    elseif types.Apparatus.objectIsInstance(item) then return "apparatus"
    elseif types.Repair.objectIsInstance(item) then return "repair"
    -- Keys out of the generic misc bucket: they accumulate all game and are pure clutter
    -- among plates/cups/gems. isKey is a real record flag (ESM::Miscellaneous::Key) and the
    -- engine ALSO sets it at load for anything a door/container actually references as its
    -- key (esmstore.cpp), so it catches mod keys whose record flag was never set. Gold is
    -- Miscellaneous too but is not a key, so it still falls through to misc.
    elseif types.Miscellaneous.objectIsInstance(item) and types.Miscellaneous.record(item).isKey then
        return "key"
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

-- Remaining time on a timed effect, QUANTIZED to the granularity the UI actually draws:
-- whole seconds under a minute, whole minutes above (matching the engine's own
-- ToolTips::getDurationString unit convention, which truncates rather than rounds).
--
-- The quantization is load-bearing, not cosmetic. exportActiveEffects is change-detected on the
-- joined effect string, so emitting the raw float would make that string differ on EVERY 0.2s slow
-- tick for as long as any timed effect is active — defeating change detection entirely and putting
-- a 5 Hz emit + GameState churn on the wire to express a countdown nothing can read faster than
-- 1 Hz. Quantized, the string changes only when a DISPLAYED value changes (~1 Hz typical, and 0 Hz
-- when only permanent effects are active). Sampling still happens at the normal slow tick, so a
-- newly-applied effect still appears within ~200ms; only emission is gated.
--
-- Returns nil for anything that must NOT show a timer. That test is the engine's, not ours:
-- ActiveSpellEffect.durationLeft (magicbindings.cpp) already returns nil when the effect's
-- mDuration < 0 — which is how abilities, diseases/blight/curses and constant-effect worn
-- enchantments are stored — or when the effect record carries the NoDuration flag. Do NOT
-- reimplement this from the spell-level `fromEquipment`/`temporary` flags: those are per-SPELL, so
-- they would wrongly suppress the timer on a cast-on-strike enchantment's effect, which is timed
-- even though its source is a worn item.
local function quantizeRemaining(secs)
    if secs == nil then return nil end
    -- mTimeLeft is decremented by the frame dt and can dip slightly below zero in the frame before
    -- the effect is removed; clamp so the last tick reads "0s" rather than a negative.
    if secs < 0 then secs = 0 end
    if secs < 60 then return math.floor(secs) end
    return math.floor(secs / 60) * 60
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
                    -- Remaining time, OMITTED entirely for permanent effects (see
                    -- quantizeRemaining) so those lines stay byte-identical to before — the same
                    -- convention the optional `ench` object uses on item lines.
                    local remaining = quantizeRemaining(e.durationLeft)
                    local remainingJson = remaining and
                        string.format(',"remaining":%d', remaining) or ''
                    parts[#parts+1] = string.format(
                        '{"name":"%s","source":"%s","harmful":%s,"icon":"%s","magnitude":%d%s}',
                        jsonEscape(name), jsonEscape(source),
                        harmful and 'true' or 'false', jsonEscape(icon), mag, remainingJson)
                end)
            end
        end
    end)

    local str = table.concat(parts, ',')
    if str == lastActiveEffectsStr then return end
    lastActiveEffectsStr = str
    emit('COMPANION_ACTIVE_EFFECTS:['..str..']')
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
    emit('COMPANION_CHARACTER:' .. str)
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

    -- Birthsign: description + inherent power/ability display names. The
    -- `spells` table is built by the SAME engine helper as race abilities
    -- (createReadOnlyRefIdTable over rec.mPowers.mList — verified
    -- birthsignbindings.cpp:41 / racebindings.cpp:58), so it is a 1-based numeric
    -- array of serialized spell-id strings, iterated with ipairs exactly like the
    -- race block above. Static text → lives on this CHARDETAIL stream, NOT inline
    -- on COMPANION_CHARACTER (4096-byte stdout-flush limit).
    local birthSignLine = ""
    pcall(function()
        local signId = types.Player.getBirthSign(self)
        if signId and signId ~= "" then
            local b = types.Player.birthSigns.records[signId]
            if b then
                local spJson = {}
                pcall(function()
                    if b.spells then
                        for _, spid in ipairs(b.spells) do
                            local sprec = core.magic.spells.records[spid]
                            local spnm = (sprec and sprec.name and sprec.name ~= "") and sprec.name or spid
                            spJson[#spJson + 1] = '"' .. jsonEscape(spnm) .. '"'
                        end
                    end
                end)
                -- The sign's portrait art. `b.texture` is already run through
                -- correctTexturePath (birthsignbindings.cpp:38) so it comes back as a
                -- uniform VFS path (e.g. textures/tx_bm_apprentice.dds) — feed straight
                -- to exportIconToPng/rememberItemIcon like every other icon.
                local tex = ""
                pcall(function() tex = b.texture or "" end)
                birthSignLine = string.format(
                    'COMPANION_CHARDETAIL_BIRTHSIGN:{"desc":"%s","texture":"%s","spells":[%s]}',
                    jsonEscape(b.description or ""), jsonEscape(tex), table.concat(spJson, ','))
            end
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
    if birthSignLine ~= "" then all[#all + 1] = birthSignLine end
    if classLine ~= "" then all[#all + 1] = classLine end
    if levelLine ~= "" then all[#all + 1] = levelLine end

    -- Change-detect the whole batch so it only streams when something changed.
    local blob = table.concat(all, '\n')
    if blob == lastCharacterDetailStr then return end
    lastCharacterDetailStr = blob

    emit('COMPANION_CHARDETAIL_START:' .. #all)
    for _, l in ipairs(all) do emit(l) end
    emit('COMPANION_CHARDETAIL_END:' .. #all)
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

-- The weapon's CLASS, which is what every damage-display decision actually keys on.
-- Mirrors the engine's own per-type table (mwmechanics/weapontype.cpp mWeaponClass), whose
-- tooltip code (mwclass/weapon.cpp getToolTipInfo) branches on exactly this:
--   melee  -> all THREE attacks (chop/slash/thrust) are real and are all shown
--   thrown -> a single attack, chop x2 (the weapon is its own ammo)
--   ranged -> a single attack, stored in chop (bows/crossbows have thrust = 0)
--   ammo   -> likewise a single attack in chop (arrows/bolts)
-- Shared by itemStats and exportInfo so the two can never disagree about which weapons have
-- one attack and which have three -- they drifted apart once already (Jul 28 2026 fixed the
-- arrow rows by making the popup copy itemStats' single-stat logic wholesale, which silently
-- collapsed every SWORD in the popup from three rows to one; fixed Aug 19 2026).
local function weaponClass(weaponType)
    local WT = types.Weapon.TYPE
    if weaponType == WT.Arrow or weaponType == WT.Bolt then return "ammo" end
    if weaponType == WT.MarksmanThrown then return "thrown" end
    if weaponType == WT.MarksmanBow or weaponType == WT.MarksmanCrossbow then return "ranged" end
    return "melee"
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
        local cls = weaponClass(t)
        local key, mn, mx
        -- ONE stat only -- this is the narrow list column, not the info popup. For a weapon with a
        -- single attack that is simply it; for a MELEE weapon (which really has three) we show its
        -- PRIMARY attack, the one the vanilla stat block leads with per weapon type. The popup shows
        -- all three for melee, matching the vanilla tooltip -- see exportInfo. Marksman/ammo store
        -- their damage in CHOP with thrust = 0, and thrown is chop x2 (it is its own ammo); keying
        -- those off THRUST is what once displayed "THRUST 0-0" on every bow and arrow.
        if cls == "ranged" or cls == "ammo" then
            key, mn, mx = "DMG", rec.chopMinDamage, rec.chopMaxDamage
        elseif cls == "thrown" then
            key = "DMG"
            mn, mx = (rec.chopMinDamage or 0) * 2, (rec.chopMaxDamage or 0) * 2
        elseif t == WT.SpearTwoWide then
            key, mn, mx = "THRUST", rec.thrustMinDamage, rec.thrustMaxDamage
        elseif t == WT.BluntOneHand or t == WT.BluntTwoClose or t == WT.BluntTwoWide
            or t == WT.AxeOneHand or t == WT.AxeTwoHand then
            key, mn, mx = "CHOP", rec.chopMinDamage, rec.chopMaxDamage
        else
            key, mn, mx = "SLASH", rec.slashMinDamage, rec.slashMaxDamage
        end
        mn = math.floor((mn or 0) + 0.5)
        mx = math.floor((mx or 0) + 0.5)
        return string.format("%d-%d", mn, mx), key, condRatio(rec.health)
    elseif types.Armor.objectIsInstance(item) then
        return string.format("%d", math.floor((rec.baseArmor or 0) + 0.5)), "ARMOR", condRatio(rec.health)
    elseif cat == "lockpick" or cat == "probe" then
        -- No quality stat for lockpicks/probes (not gameplay-relevant); keep the uses-left bar.
        return "", "", condRatio(rec.maxCondition)
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

-- Enchantment JSON fragment for the item info popup, appended to itemJson (and mirrored by the
-- native barter export). Shape: ,"ench":{"id","type","effects":[{"id","n","mag","dur","area",
-- "ic","h"}...]}. Returns "" when the item is not enchanted (so non-enchanted item lines stay
-- byte-identical → backward compatible). Self-contained (only earlier helpers) because itemJson
-- is defined before formatEffect/rangeName/capitalize.
local ENCH_TYPE_LABELS = nil
local function enchantJson(item)
    local enchId = itemEnchantId(item)
    if not enchId then return "" end
    local out = ""
    pcall(function()
        local ench = core.magic.enchantments.records[enchId]
        if not ench then return end
        if ENCH_TYPE_LABELS == nil then
            local ET = core.magic.ENCHANTMENT_TYPE
            ENCH_TYPE_LABELS = {
                [ET.CastOnce] = "Cast Once", [ET.CastOnStrike] = "Cast on Strike",
                [ET.CastOnUse] = "Cast on Use", [ET.ConstantEffect] = "Constant Effect",
            }
        end
        local typeLabel = ENCH_TYPE_LABELS[ench.type] or "Cast Once"
        local effs = {}
        for _, eff in ipairs(ench.effects or {}) do
            local nm = magicEffectName(eff) or "?"
            pcall(function()
                if eff.affectedAttribute then
                    nm = nm .. " " .. (tostring(eff.affectedAttribute):gsub("^%l", string.upper))
                end
                if eff.affectedSkill then
                    nm = nm .. " " .. (tostring(eff.affectedSkill):gsub("^%l", string.upper))
                end
            end)
            local mn = math.floor((eff.magnitudeMin or 0) + 0.5)
            local mx = math.floor((eff.magnitudeMax or 0) + 0.5)
            local mag = (mn == mx) and tostring(mn) or string.format("%d-%d", mn, mx)
            local dur = math.floor((eff.duration or 0) + 0.5)
            local area = math.floor((eff.area or 0) + 0.5)
            local ic, harmful, effId = "", false, ""
            pcall(function()
                effId = tostring(eff.id or "")
                if eff.effect then
                    ic = eff.effect.icon or ""
                    if eff.effect.harmful then harmful = true end
                end
            end)
            table.insert(effs, string.format(
                '{"id":"%s","n":"%s","mag":"%s","dur":%d,"area":%d,"ic":"%s","h":%s}',
                jsonEscape(effId), jsonEscape(nm), jsonEscape(mag), dur, area,
                jsonEscape(ic), harmful and "true" or "false"))
        end
        out = string.format(',"ench":{"id":"%s","type":"%s","effects":[%s]}',
            jsonEscape(tostring(enchId)), jsonEscape(typeLabel), table.concat(effs, ','))
    end)
    return out
end

-- Streamed as START / ITEM* / END (one item per line) rather than one giant
-- JSON array line. The engine's stdout sink flushes in 4096-byte chunks and
-- only the first chunk keeps its COMPANION_ prefix, so a single long inventory
-- line arrives truncated on the app side. Per-item lines stay well under that.
-- Serialize a single inventory/container item to the shared item JSON shape
-- (exactly the fields Kotlin's parseInventoryItem understands). Used by both the
-- player inventory export and the container/looting export.
local function itemJson(item)
    local ok, rec = pcall(function() return item.type.record(item) end)
    local icon = (ok and rec and rec.icon) or ""
    local weight = (ok and rec and rec.weight) or 0   -- for the loot overlay's optimistic encumbrance
    -- Base record value in gold, for the inventory tab's Price sort. Base (not condition- or
    -- merchant-adjusted) on purpose: it matches the vanilla tooltip's "Value", and barter's own
    -- per-item value is exported separately by the NATIVE barter path — this field never feeds a
    -- transaction, only the sort order.
    local value = (ok and rec and rec.value) or 0
    local sid = stackId(item)
    local cat = itemCategory(item)
    local statVal, statKey, cond = itemStats(item, cat)
    local condField = ""
    if cond ~= nil then condField = string.format(',"cond":%.3f', cond) end
    return string.format(
        '{"id":"%s","sid":"%s","name":"%s","count":%d,"cat":"%s","icon":"%s","weight":%.2f,"value":%d,' ..
            '"statVal":"%s","statKey":"%s"%s%s}',
        jsonEscape(item.recordId), jsonEscape(sid), jsonEscape(itemName(item)),
        item.count, cat, jsonEscape(icon), weight, value,
        jsonEscape(statVal), jsonEscape(statKey), condField, enchantJson(item))
end

local lastInventoryStr = nil

local function exportInventory()
    local all = types.Actor.inventory(self):getAll()
    -- Build the itemJson batch ONCE, change-detect against the last emit, and skip the
    -- whole streamed export (START/ITEM*/END) when nothing changed. itemJson carries
    -- id/sid/count/cat/condition/enchant/weight/etc., so any add/remove/quantity/identity/
    -- condition change alters the batch. Same pattern as exportContainer (lastContainerJson).
    -- Highest-value change-detect: skips the per-item JSON build + ~N JNI calls per tick on
    -- the engine thread, not just the log write.
    local parts = {}
    for _, item in ipairs(all) do
        parts[#parts + 1] = itemJson(item)
    end
    local batch = table.concat(parts, '\n')
    if batch == lastInventoryStr then return end
    lastInventoryStr = batch
    emit('COMPANION_INVENTORY_START:' .. #all)
    for _, p in ipairs(parts) do
        emit('COMPANION_INVENTORY_ITEM:' .. p)
    end
    emit('COMPANION_INVENTORY_END:' .. #all)
end

-- ===== Container / looting =====
-- containerObj is the open container/corpse/NPC (from the UiModeChanged -> Container
-- transition); nil when no container window is open. moveInto/split are
-- global-script-only, so take/put/take-all are dispatched as CompanionContainerTransfer
-- events to companion_global.lua (mirrors the CompanionDropItem pattern).
local containerObj = nil
local containerIsCorpse = false
-- Living-NPC pickpocket flag (true = conscious/living actor, not a corpse or chest);
-- drives the per-item Sneak hiding and the "Nothing you can lift" empty state.
local containerIsPickpocket = false
-- {[sid]=true} set of items hidden by the pickpocket Sneak roll (living NPC only),
-- rolled ONCE at open and cached for the session; nil = nothing hidden.
local containerHiddenSids = nil
-- >0 = re-enumerate the container for a few ticks after a transfer. A transfer's
-- moveInto runs as a queued (async) action, so the new contents aren't readable on
-- the same frame; we re-export a couple of times afterward. Change-detection
-- (lastContainerJson) means an unchanged batch still prints nothing.
-- CRITICAL: the container GUI PAUSES the game, so onUpdate is called with dt=0 and
-- the whole slow tick (incl. exportInventory + this re-export) freezes while the
-- overlay is open. The re-export is therefore driven from onFrame (which DOES fire
-- while paused — luamanagerimp.cpp), throttled by containerRefreshTimer.
local containerReexportTicks = 0
local containerRefreshTimer = 0
local CONTAINER_REFRESH_INTERVAL = 0.08
local lastContainerJson = nil
-- true = Take All / Dispose queued a bulk transfer and is keeping the container OPEN for the
-- refresh window (scheduleContainerRefresh) so the DEFERRED item:moveInto actions land, then
-- closing it from onFrame once the window elapses. Closing in the SAME frame the transfer was
-- queued raced the deferred add and silently no-oped it (the "Take All does nothing on first
-- use" bug — item:moveInto is a queued LuaManager action, objectbindings.cpp). Also a reentrancy
-- flag: while set, further take/put/take-all/dispose are ignored so we don't double-queue or
-- close before the first batch lands. Reset on container open/close.
local containerCloseAfterRefresh = false

-- Schedule a post-transfer refresh burst (consumed by onFrame). Resets the throttle
-- so the first refresh waits a full interval, giving the async moveInto time to land.
local function scheduleContainerRefresh()
    containerReexportTicks = 3
    containerRefreshTimer = 0
end

local function containerInventory()
    if not containerObj then return nil end
    local ok, inv = pcall(function()
        if types.Actor.objectIsInstance(containerObj) then
            return types.Actor.inventory(containerObj)
        elseif types.Container.objectIsInstance(containerObj) then
            return types.Container.content(containerObj)
        end
        return nil
    end)
    if ok then return inv end
    return nil
end

-- Find a container item by its instance stack id (tostring(item.id)) — same identity scheme
-- companion_global.findBySid uses, so the sound matches the exact stack being taken.
local function containerItemBySid(sid)
    local inv = containerInventory()
    if not inv then return nil end
    local ok, found = pcall(function()
        for _, it in ipairs(inv:getAll()) do
            if tostring(it.id) == sid then return it end
        end
        return nil
    end)
    if ok then return found end
    return nil
end

-- First item in the open container (for Take All's one-sound-per-batch, matching vanilla
-- container.cpp which plays only the first object's sound, not one per item).
local function firstContainerItem()
    local inv = containerInventory()
    if not inv then return nil end
    local ok, first = pcall(function() return inv:getAll()[1] end)
    if ok then return first end
    return nil
end

local function containerDisplayName(obj)
    local ok, rec = pcall(function() return obj.type.record(obj) end)
    if ok and rec and rec.name and rec.name ~= "" then return rec.name end
    return tostring(obj.recordId)
end

-- Replicate vanilla's ContainerItemModel::onDropItem gate for PUTTING an item INTO a
-- container: block (with the matching vanilla GMST message) when the target is an ORGANIC
-- container, has no capacity, or would overflow its weight capacity. Order + messages mirror
-- the engine exactly: organic -> sContentsMessage2; capacity <= 0 -> sContentsMessage3;
-- current-contents-weight + incoming-weight > capacity -> sContentsMessage3. Only genuine
-- ESM::Container targets are gated — corpses/living NPCs (Actors) have NO put restriction
-- (vanilla `return true`s early for non-containers). capacity = the container record weight
-- limit (types.Container.getCapacity = mBase.mWeight); encumbrance = current contents weight
-- (types.Container.getEncumbrance = store weight). Returns the resolved message to show +
-- block, or nil to allow. This is the authoritative Lua backstop; Kotlin also gates
-- client-side (so no command normally reaches here when blocked). Take is never gated.
local function containerPutBlockMessage(item, count)
    if not (containerObj and types.Container.objectIsInstance(containerObj)) then
        return nil                                   -- corpse / NPC / none → puts unrestricted
    end
    local organic = false
    pcall(function() organic = types.Container.record(containerObj).isOrganic end)
    if organic then return core.getGMST('sContentsMessage2') end
    local capacity = 0
    pcall(function() capacity = types.Container.getCapacity(containerObj) end)
    if capacity <= 0 then return core.getGMST('sContentsMessage3') end
    local encumbrance = 0
    pcall(function() encumbrance = types.Container.getEncumbrance(containerObj) end)
    local itemWeight = 0
    pcall(function()
        local rec = item.type.record(item)
        itemWeight = (rec and rec.weight) or 0
    end)
    if encumbrance + itemWeight * (count or 1) > capacity then
        return core.getGMST('sContentsMessage3')     -- too heavy (vanilla nextafter slack ≈ strict >)
    end
    return nil
end

-- One-time export of the two vanilla GMST strings the Kotlin banner needs for the client-side
-- put gate (organic = sContentsMessage2, capacity/weight = sContentsMessage3). Static, so
-- emit once per session (first container open), guarded by this flag.
local companionGmstExported = false

-- Emit the container's item list. announce=true prints COMPANION_CONTAINER_OPEN first
-- (new session header: name + isCorpse). Re-emits (announce=false) are change-detected
-- against the last batch so an unchanged container prints nothing. Streamed one item
-- per line (4096-byte flush safety), same as the player inventory export.
-- For a LIVING NPC (pickpocket) the native UI hides items the NPC is wearing, so
-- the companion overlay must too. Returns a set of equipped instance-ids to skip,
-- keyed by tostring(item.id) (matches the sid used in itemJson). nil for corpses
-- and plain containers (nothing to filter). types.Actor.getEquipment reads any
-- actor (verified against actor.cpp getAllEquipment(const Object&)); wrapped in
-- pcall so a failure just means "don't filter" rather than an empty overlay.
local function equippedItemIds()
    if containerIsCorpse then return nil end
    if not (containerObj and types.Actor.objectIsInstance(containerObj)) then return nil end
    local ok, eq = pcall(function() return types.Actor.getEquipment(containerObj) end)
    if not ok or not eq then return nil end
    local set = {}
    for _, witem in pairs(eq) do
        if witem then set[tostring(witem.id)] = true end
    end
    return set
end

-- Per-item Sneak-based hiding for a LIVING NPC pickpocket, mirroring OpenMW's
-- PickpocketItemModel: for each non-equipped item, hide it when a fresh roll(0..99)
-- exceeds the player's modified Sneak. Rolled ONCE at open and cached
-- (containerHiddenSids) so items don't flicker across refreshes; nil for corpses and
-- plain containers. math.random is pre-seeded per game launch by the engine
-- (luastate.cpp — randomseed is a no-op there), so we just roll. (Native also skips
-- hiding for a KNOCKED-DOWN actor; that state isn't exposed to Lua, so hiding applies
-- to any living NPC here — a rare edge case.)
local function computeHiddenSids()
    if not containerIsPickpocket then return nil end
    local inv = containerInventory()
    if not inv then return nil end
    local ok, all = pcall(function() return inv:getAll() end)
    if not ok or not all then return nil end
    local sneak = 0
    pcall(function() sneak = types.NPC.stats.skills.sneak(self).modified or 0 end)
    local worn = equippedItemIds()
    local hidden = {}
    for _, item in ipairs(all) do
        local sid = tostring(item.id)
        if not (worn and worn[sid]) and math.random(0, 99) > sneak then
            hidden[sid] = true
        end
    end
    return hidden
end

-- Snapshot the hidden set as an array so it can ride the CompanionContainerTransfer
-- event to the global script — Take All / Dispose skip the same items the display hides.
local function hiddenSidList()
    local list = {}
    if containerHiddenSids then
        for sid, _ in pairs(containerHiddenSids) do list[#list + 1] = sid end
    end
    return list
end

local function exportContainer(force)
    local inv = containerInventory()
    if not inv then return end
    local ok, all = pcall(function() return inv:getAll() end)
    if not ok or not all then return end
    -- Filter out worn items (living NPC) AND items hidden by the Sneak roll; nil sets
    -- = no filtering (corpse/chest).
    local worn = equippedItemIds()
    local shown = {}
    local parts = {}
    for _, item in ipairs(all) do
        local sid = tostring(item.id)
        if not (worn and worn[sid]) and not (containerHiddenSids and containerHiddenSids[sid]) then
            shown[#shown + 1] = item
            parts[#parts + 1] = itemJson(item)
        end
    end
    local batch = table.concat(parts, '\n')
    if not force and batch == lastContainerJson then return end
    lastContainerJson = batch
    -- Always emit OPEN, not just on the first open. The Kotlin side then rebuilds the
    -- session atomically on EVERY refresh, identical to the initial open (the known-
    -- working path): the OPEN branch force-resets the item buffer, so a refresh can't
    -- be corrupted by a stale/partial buffer left behind if an END was ever dropped.
    -- `force` now only bypasses change-detection (the first open); refreshes remain
    -- change-detected so an unchanged container still prints nothing.
    -- One-time: ship the vanilla GMST strings the Kotlin put-gate banner uses (static).
    if not companionGmstExported then
        companionGmstExported = true
        emit(string.format('COMPANION_GMST:{"putOrganic":"%s","putFull":"%s"}',
            jsonEscape(core.getGMST('sContentsMessage2')), jsonEscape(core.getGMST('sContentsMessage3'))))
    end
    -- Put-restriction data for the client-side gate: organic flag + weight capacity (mBase.mWeight).
    -- Container-only (Actors have no put restriction) → organic=false, capacity=-1 (sentinel = no
    -- limit) for corpses/NPCs so the Kotlin gate skips them.
    local organic, capacity = false, -1
    if types.Container.objectIsInstance(containerObj) then
        pcall(function() organic = types.Container.record(containerObj).isOrganic end)
        pcall(function() capacity = types.Container.getCapacity(containerObj) end)
    end
    emit(string.format('COMPANION_CONTAINER_OPEN:{"name":"%s","isCorpse":%s,"pickpocket":%s,"organic":%s,"capacity":%s}',
        jsonEscape(containerDisplayName(containerObj)), tostring(containerIsCorpse),
        tostring(containerIsPickpocket), tostring(organic), tostring(capacity)))
    for _, item in ipairs(shown) do
        emit('COMPANION_CONTAINER_ITEM:' .. itemJson(item))
    end
    emit('COMPANION_CONTAINER_END:' .. #shown)
end

local lastEquipmentStr = nil

local function exportEquipment()
    local equipped = types.Actor.getEquipment(self)
    local parts = {}
    for slot, item in pairs(equipped) do
        local slotName = SLOT_NAMES[slot] or ("slot" .. tostring(slot))
        local sid = stackId(item)
        -- Prefer instance id so Kotlin can match the exact equipped stack;
        -- fall back to recordId so the slot is never empty.
        local slotVal = (sid ~= "") and sid or item.recordId
        table.insert(parts, string.format(
            '"%s":"%s"', slotName, jsonEscape(slotVal)))
    end

    -- Watch the two ring slots for occupancy changes so a ring equipped from the VANILLA menu
    -- also updates recency — otherwise "least recently equipped" would only ever reflect
    -- companion equips and would evict the wrong ring after any vanilla change. Runs before the
    -- change-detection early-return below so it is never skipped.
    for _, rslot in ipairs({ 12, 13 }) do
        local item = equipped[rslot]
        local occupant = nil
        if item then
            local sid = stackId(item)
            occupant = (sid ~= "") and sid or item.recordId
        end
        if occupant ~= ringOccupants[rslot] then
            ringOccupants[rslot] = occupant
            -- Only a slot becoming filled counts as "equipped"; clearing one does not.
            if occupant ~= nil then noteRingEquipped(rslot) end
        end
    end

    local line = 'COMPANION_EQUIPMENT:{' .. table.concat(parts, ',') .. '}'
    if line == lastEquipmentStr then return end
    lastEquipmentStr = line
    emit(line)
end

-- The ammo type a weapon FIRES, or nil if it uses no separate ammo. Mirrors the engine's
-- per-weapon-type `mAmmoType` table (mwmechanics/weapontype.cpp): MarksmanBow -> Arrow,
-- MarksmanCrossbow -> Bolt, and EVERYTHING else -> None.
--
-- MarksmanThrown is the case worth stating explicitly: throwing stars/knives are Marksman-skill
-- ranged weapons, so they look like they should qualify, but the engine gives them ammo type None
-- because a thrown weapon consumes ITSELF from its own stack — there is no ammo slot involved and
-- nothing to count separately. Arrow/Bolt records are likewise not ammo USERS, they are the ammo.
local function weaponAmmoType(weapon)
    if weapon == nil then return nil end
    local ok, wt = pcall(function() return types.Weapon.record(weapon).type end)
    if not ok or wt == nil then return nil end
    local WT = types.Weapon.TYPE
    if wt == WT.MarksmanBow then return WT.Arrow end
    if wt == WT.MarksmanCrossbow then return WT.Bolt end
    return nil
end

local lastAmmoStr = nil

-- The EQUIPPED ammo stack for the HUD's ammo counter: `{"id","count"}`, or `{}` whenever no
-- counter should be shown.
--
-- Deliberately reads the ammo SLOT object (slot 18) and reports that one stack's own `count` —
-- not a sum over every arrow in the pack. A player carrying three kinds of arrow has three
-- separate stacks; only the one in the slot is the one that will actually be loosed, so summing
-- them would report a number the next shot does not draw from.
--
-- Emits `{}` — i.e. no counter — in every case where a count would be misleading:
--   * the equipped weapon fires no ammo (melee, thrown, unarmed, or no weapon at all);
--   * the ammo slot is empty (reachable: firing the last arrow removes the stack and clears it);
--   * the equipped ammo does not MATCH the weapon (also reachable — arrows equip into slot 18
--     regardless of what is in the weapon slot, so longsword+arrows and bow+bolts are both real
--     states). This is the same `ammoType != None && slot occupied && record type == ammoType`
--     test the engine itself applies before letting an attack go through
--     (character.cpp), so the counter is present exactly when a shot is actually possible.
--
-- Change-detected like every other slow-tick export, so it costs one ~40-byte line per actual
-- change (i.e. per shot) rather than five a second. No new tick and no throttle of its own: the
-- count is bounded by how fast a bow can fire, which is far below the 0.2s sampling interval.
local function exportAmmo()
    local str = '{}'
    pcall(function()
        local equipped = types.Actor.getEquipment(self)
        local want = weaponAmmoType(equipped[16])
        if want == nil then return end
        local ammo = equipped[18]
        if ammo == nil or not types.Weapon.objectIsInstance(ammo) then return end
        if types.Weapon.record(ammo).type ~= want then return end
        str = string.format('{"id":"%s","count":%d}',
            jsonEscape(ammo.recordId), ammo.count)
    end)

    if str == lastAmmoStr then return end
    lastAmmoStr = str
    emit('COMPANION_AMMO:' .. str)
end

local lastEquippedChargeStr = nil

-- Enchantment charge of the item in the WEAPON slot (16 = CarriedRight — the item the HUD's
-- equipped-item icon shows), as `{"charge":N,"maxCharge":M}`, or `{}` when no charge meter
-- should be drawn.
--
-- Its own small line rather than a field on the streamed inventory items, for the same reason
-- COMPANION_AMMO is one: enchantment charge REGENERATES continuously (the engine tops every
-- enchanted item in the pack up a little each frame), so carrying it on itemJson would make the
-- inventory batch differ on nearly every tick and defeat exportInventory's change detection —
-- the dominant cost this export path was optimised for. Here a change costs one ~40-byte line.
--
-- Emits `{}` unless the equipped item's enchantment actually CONSUMES charge, i.e. Cast on
-- Strike or Cast on Use. Cast Once (scrolls) is spent whole and Constant Effect never drains, so
-- a meter for either would sit permanently full and say nothing. As with COMPANION_AMMO, the
-- PRESENCE of a payload is the "draw a meter" signal — the app does not re-derive which
-- enchantments are charge-bearing.
--
-- Read from the equipped object itself, so it is per-STACK correct — unlike the spells export's
-- charge, which is keyed by recordId.
local function exportEquippedCharge()
    local str = '{}'
    pcall(function()
        local item = types.Actor.getEquipment(self)[16]
        if item == nil then return end
        local enchId = itemEnchantId(item)
        if not enchId then return end
        local ench = core.magic.enchantments.records[enchId]
        if not ench then return end
        local ENCH = core.magic.ENCHANTMENT_TYPE
        if ench.type ~= ENCH.CastOnStrike and ench.type ~= ENCH.CastOnUse then return end
        local maxCharge = ench.charge or 0
        if maxCharge <= 0 then return end
        local charge = maxCharge
        pcall(function()
            local d = types.Item.itemData(item)
            if d and d.enchantmentCharge ~= nil then charge = d.enchantmentCharge end
        end)
        -- Whole points, matching the spells export so the two can never disagree on screen.
        -- Quantizing also protects the change detection above: the raw value is a float that
        -- creeps up every frame while recharging, which nothing reads that finely.
        str = string.format('{"charge":%d,"maxCharge":%d}',
            math.floor(charge + 0.5), math.floor(maxCharge + 0.5))
    end)

    if str == lastEquippedChargeStr then return end
    lastEquippedChargeStr = str
    emit('COMPANION_EQUIPPED_CHARGE:' .. str)
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

        emit('COMPANION_JOURNAL_START:' .. count)
        for i = 1, count do
            -- Protect each entry individually so one bad entry can't abort the whole export.
            local ok2, err2 = pcall(function()
                local e = entries[i]
                if not e then return end
                local qid = tostring(e.questId or "")
                local name = getQuestName(qid)
                emit(string.format(
                    'COMPANION_JOURNAL_ENTRY:{"q":"%s","n":"%s","t":"%s","d":%d,"m":%d,"dom":%d}',
                    jsonEscape(qid), jsonEscape(name), jsonEscape(e.text or ""),
                    e.day or 0, e.month or 0, e.dayOfMonth or 0))
            end)
            if not ok2 then
                emit("COMPANION_DEBUG: entry " .. i .. " err=" .. tostring(err2))
            end
        end
        emit('COMPANION_JOURNAL_END:' .. count)
        -- Marked exported only AFTER the closing END is away. The Kotlin side only commits the
        -- batch on END (it buffers from START), so recording the count up-front meant a batch lost
        -- part-way left the app showing the previous journal with the tick convinced it was current
        -- — no retry until the entry count happened to change again. Now a throw anywhere above
        -- leaves the count stale and the next tick redoes the whole export.
        journalExportedCount = count
    end)
    if not ok then
        emit("COMPANION_DEBUG: journal error: " .. tostring(err))
    end
end



-- ===== Combat target export (concept (b): true combat target) =====
--
-- The combat target is reported to us by companion_actor.lua, a NPC,CREATURE-
-- context script that watches each actor's active-Combat AI package and, when
-- it targets the player, sends a `CompanionCombatTarget` event carrying that
-- actor + a health snapshot. A player script cannot read this itself:
-- openmw.interfaces.AI is @context local (AI.getTargets("Combat") reads the
-- attached actor's AI sequence) and the player has no combat AI package — so
-- each actor must report itself. This replaced the old crosshair raycast
-- (concept a) July 2026; see CLAUDE.md "Combat target" and companion_actor.lua.
--
-- The event handler (onCombatTarget below) stores the latest report and refills
-- a short timeout; exportTarget ages that timeout down each slow tick and
-- clears the target once the actor stops reporting (combat ended / unloaded).
local COMBAT_TARGET_TIMEOUT = 3.0  -- clear if no update within this many seconds
local combatTarget = nil           -- { actor = <GameObject>, health = {current,max} }
local combatTargetTimer = 0.0      -- seconds until the stored target is cleared
local targetIsFocused = false      -- true only when combatTarget was set by a
                                   -- player ATTACK (reason='hit'). A focused
                                   -- target owns the bar: ambient reports from
                                   -- OTHER actors and being-hit by OTHER actors
                                   -- cannot displace it (see onPlayerHit).
local lastTargetStr = nil

-- Build the COMPANION_TARGET JSON from the stored combat target. Reads name +
-- health FRESH from the live actor object when possible (so the bar stays
-- current between the actor's ~0.2s reports), falling back to the health
-- snapshot carried in the event. Returns nil when there's nothing to show.
local function targetJson()
    if not combatTarget or not combatTarget.actor then return nil end
    local obj = combatTarget.actor
    local nm = ""
    pcall(function()
        local rec = obj.type.record(obj)
        if rec and rec.name and rec.name ~= "" then nm = rec.name end
    end)
    if nm == "" then nm = obj.recordId or "?" end
    local cur, mx
    pcall(function()
        local hp = types.Actor.stats.dynamic.health(obj)
        cur, mx = hp.current, hp.base
    end)
    if cur == nil and combatTarget.health then
        cur, mx = combatTarget.health.current, combatTarget.health.max
    end
    if cur == nil then return nil end
    return string.format(
        '{"name":"%s","health":{"current":%.1f,"max":%.1f}}',
        jsonEscape(nm), cur, mx)
end

local function exportTarget()
    -- Age out the stored target if the actor script has gone quiet.
    if combatTargetTimer > 0 then
        combatTargetTimer = combatTargetTimer - SLOW_INTERVAL
        if combatTargetTimer <= 0 then
            combatTarget = nil
            combatTargetTimer = 0.0
            targetIsFocused = false
        end
    end
    local str = targetJson() or '{}'
    -- Change-detection: only print when the target or its health changed.
    if str == lastTargetStr then return end
    lastTargetStr = str
    emit('COMPANION_TARGET:' .. str)
end

-- Priority of the three ways the bar's target gets set (highest first):
--   1. player ATTACKS an actor  (onCombatTarget, reason='hit')  → FOCUSED
--   2. player is HIT by an actor (onPlayerHit)                  → weak
--   3. actor's Combat AI targets the player (reason='ai')       → weak, ambient
-- A FOCUSED target (you're actively swinging at it) owns the bar: neither a
-- different enemy's ambient report nor being hit by a different enemy can pull
-- the bar off it. This keeps the display on the enemy you're fighting during a
-- group brawl instead of flicking to whatever fast attacker last chipped you.
-- Focus is only ever set by an attack, and is dropped when the target ages out.
local function sameAsCurrentTarget(actor)
    if not combatTarget or not combatTarget.actor then return false end
    local same = false
    pcall(function() same = (combatTarget.actor.id == actor.id) end)
    return same
end

local function onCombatTarget(data)
    if not data or not data.actor then return end
    if data.reason == 'hit' then
        -- The player attacked this actor: it becomes the FOCUSED target.
        combatTarget = data
        combatTargetTimer = COMBAT_TARGET_TIMEOUT
        targetIsFocused = true
        return
    end
    -- Ambient AI report: seed if empty, or refresh the same actor (preserving
    -- the focus flag); otherwise leave the current target in place.
    if combatTarget == nil then
        combatTarget = data
        combatTargetTimer = COMBAT_TARGET_TIMEOUT
        targetIsFocused = false
    elseif sameAsCurrentTarget(data.actor) then
        combatTarget = data
        combatTargetTimer = COMBAT_TARGET_TIMEOUT
    end
end

-- The player was hit ("Hit" is a local event delivered to the victim — us).
-- Being hit is a WEAK signal: it must NOT drag the bar off the enemy the player
-- is actively attacking (a focused target). So it only seeds/updates when there
-- is no focused target — or when the attacker already IS the current target
-- (it's hitting you back, so just refresh it). It never sets focus itself; only
-- attacking does. This is the "only fire if I don't already have a focused
-- target" rule, and also covers the window before an attacker's own onUpdate
-- tick reports it when you have no target yet.
local function onPlayerHit(data)
    if not (data and data.attacker) then return end
    if targetIsFocused and not sameAsCurrentTarget(data.attacker) then return end
    local hp
    pcall(function()
        local h = types.Actor.stats.dynamic.health(data.attacker)
        hp = { current = h.current, max = h.base }
    end)
    combatTarget = { actor = data.attacker, health = hp }
    combatTargetTimer = COMBAT_TARGET_TIMEOUT
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
    emit('COMPANION_PLAYER_STATUS:' .. str)
end

-- Door markers for the companion minimap: the teleport doors near the player (interior doors,
-- or doors in nearby exterior cells) with their world position + destination cell name. This is
-- the same data the native map's clickable square markers use (World::getDoorMarkers), but the
-- companion map is zoomed in so the squares are finger-sized. Streamed one per line (a town cell
-- can have many) and change-detected so it only emits on cell transitions. No fog-of-war gating —
-- we show all nearby teleport doors (the companion map has no fog data).
local lastDoorMarkersStr = nil
local function exportDoorMarkers()
    local parts = {}
    pcall(function()
        for _, door in ipairs(nearby.doors) do
            if types.Door.isTeleport(door) then
                local name = ""
                pcall(function()
                    local dc = types.Door.destCell(door)
                    if dc then
                        if dc.name and dc.name ~= "" then
                            name = dc.name
                        elseif dc.region then
                            local reg = core.regions.records[dc.region]
                            if reg and reg.name and reg.name ~= "" then name = reg.name end
                        end
                    end
                end)
                local pos = door.position
                parts[#parts + 1] = string.format('{"x":%.1f,"y":%.1f,"name":"%s"}',
                    pos.x, pos.y, jsonEscape(name))
            end
        end
    end)
    local joined = table.concat(parts, '|')
    if joined == lastDoorMarkersStr then return end
    lastDoorMarkersStr = joined
    emit('COMPANION_DOORMARKER_START:' .. #parts)
    for _, p in ipairs(parts) do
        emit('COMPANION_DOORMARKER_ITEM:' .. p)
    end
    emit('COMPANION_DOORMARKER_END:' .. #parts)
end

-- Play the material/type-specific "pick up" (up) or "put down" (down) UI sound for an item,
-- matching what the native inventory/container/barter windows play. The DS data paths
-- (setEquipment / moveInto / split-teleport) bypass those native windows, which is where the
-- sound normally fires, so we trigger it ourselves. The exact vanilla sound id (e.g.
-- "Item Armor Heavy Up", "Item Weapon Blunt Down") comes from the native binding
-- types.Item.getUpSoundId / getDownSoundId (added in companion-ui-sounds.patch), which returns
-- Class::getUp/DownSoundId(item) — the same per-mwclass mapping vanilla uses. Returns "" if none.
local function playItemSound(item, up)
    if not item then return end
    local ok, err = pcall(function()
        local soundId = up and types.Item.getUpSoundId(item) or types.Item.getDownSoundId(item)
        if soundId and soundId ~= "" then
            ambient.playSound(soundId)
        end
    end)
    if not ok then
        emit("COMPANION_DEBUG: sound error: " .. tostring(err))
    end
end

-- ===== Inbound actions =====

-- Slot lookup for equipping. Numbers are MWWorld::InventoryStore::Slot_* — see the SLOT_NAMES
-- comment at the top of this file; keep the two in sync.
--
-- WHY THIS MATTERS MORE THAN IT LOOKS: a wrong slot number here does NOT fail loudly. The
-- engine's setEquipment (mwlua/types/actor.cpp) silently falls back to the item's first
-- *allowed* slot that it hasn't already marked used, so a wrong number still equips correctly
-- as long as the real slot is free. The mismatch only surfaces once that slot is occupied,
-- where it degrades into a silent no-op. Helmet/amulet/ring were all wrong this way until
-- Jul 2026 (helmet said 12=LeftRing, amulet 13=RightRing, rings 14/15=Amulet/Belt), which is
-- why "equip a second amulet" did nothing while the first one always worked.
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
        return 17  -- Slot_CarriedLeft (torches, lanterns). NOTE: must be 17, not 19 —
                   -- setEquipment's loop only iterates slots 0..(Slots-1)=0..18 and
                   -- looks up equipment[slot], so a key of 19 is silently dropped and
                   -- the torch never equips. 17 = InventoryStore::Slot_CarriedLeft.
    elseif types.Armor.objectIsInstance(item) then
        local t = types.Armor.record(item).type
        local AT = types.Armor.TYPE
        local map = {
            [AT.Helmet]=0, [AT.Cuirass]=1, [AT.LPauldron]=3, [AT.RPauldron]=4,
            [AT.Greaves]=2, [AT.Boots]=7, [AT.LGauntlet]=5, [AT.RGauntlet]=6,
            [AT.Shield]=17, [AT.LBracer]=5, [AT.RBracer]=6,
        }
        return map[t]
    elseif types.Clothing.objectIsInstance(item) then
        local t = types.Clothing.record(item).type
        local CT = types.Clothing.TYPE
        if t == CT.Ring then
            -- The only item with TWO valid slots: 12 = Slot_LeftRing, 13 = Slot_RightRing.
            -- Fill whichever is actually free. (The old condition tested currentEquip[14],
            -- which is the AMULET slot, so it never described ring occupancy at all — the
            -- engine's fallback was placing both rings by accident.)
            if currentEquip[12] == nil then return 12 end
            if currentEquip[13] == nil then return 13 end
            -- Both occupied: replace the ring equipped LEAST recently. Returning a real ring
            -- slot here is what makes this a replace rather than a no-op — the slot is allowed
            -- for a ring, so the engine equips into it and unequips the previous occupant.
            -- lastRingSlot is the most recently filled slot, so the other one is the older.
            if lastRingSlot == 12 then return 13 end
            if lastRingSlot == 13 then return 12 end
            -- Recency unknown (both rings already on before the script started tracking).
            -- Fall back to the left ring, matching which slot we fill first when both are free.
            return 12
        end
        local map = {
            [CT.Amulet]=14, [CT.Belt]=15, [CT.Shirt]=8, [CT.Pants]=9, [CT.Skirt]=10,
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
        emit("COMPANION_DEBUG: equip - not found: " .. arg)
        return
    end
    local equip = types.Actor.getEquipment(self)
    local slot = slotForItem(found, equip)
    if slot == nil then
        emit("COMPANION_DEBUG: equip - no slot for: " .. arg)
        return
    end
    equip[slot] = found
    types.Actor.setEquipment(self, equip)
    -- Record ring recency immediately rather than waiting for exportEquipment's next slow tick,
    -- so two taps in quick succession don't both see the same stale "most recent" slot and
    -- evict the same ring twice.
    noteRingEquipped(slot)
    playItemSound(found, true)
    emit("COMPANION_DEBUG: equipped " .. arg .. " -> slot " .. slot)
end

local function unequipItem(arg)
    local equip = types.Actor.getEquipment(self)
    local changed = false
    local unequipped = nil  -- the item removed, for its material-specific "put down" sound
    for slot, item in pairs(equip) do
        local sid = stackId(item)
        -- Match by instance id first; fall back to recordId for old clients.
        if sid == arg or (sid == "" and item.recordId == arg) then
            unequipped = item
            equip[slot] = nil
            changed = true
        end
    end
    if changed then
        types.Actor.setEquipment(self, equip)
        playItemSound(unequipped, false)
        emit("COMPANION_DEBUG: unequipped " .. arg)
    else
        emit("COMPANION_DEBUG: unequip - not worn: " .. arg)
    end
end

-- "Use" an item exactly like the native inventory (double-click / drag onto the
-- paper doll): potion → drink, ingredient → eat, apparatus → alchemy menu, repair
-- tool → repair menu. There is NO local :use() binding — the only way to trigger
-- the per-type MWWorld::Action is the stock ItemUsage `UseItem` GLOBAL event
-- (omw/usehandlers.lua → world._runStandardUseAction → WindowManager::useItem →
-- item.class.use()). Its doc explicitly supports "any script": sendGlobalEvent(
-- 'UseItem', {object = item, actor = player}). arg is a per-stack instance id
-- (preferred) or a recordId (fallback), same as equipItem.
local function useItem(arg)
    local found = nil
    for _, item in ipairs(types.Actor.inventory(self):getAll()) do
        if stackId(item) == arg then found = item; break end
    end
    if not found then found = types.Actor.inventory(self):find(arg) end
    if not found then
        emit("COMPANION_DEBUG: use - not found: " .. arg)
        return
    end
    core.sendGlobalEvent('UseItem', { object = found, actor = self.object })
    emit("COMPANION_DEBUG: use " .. arg)
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

-- Ingredient alchemy effects with vanilla "reveal" gating: the i-th effect is only shown once
-- Alchemy skill >= fWortChanceValue * i, otherwise it shows as "Unknown" (matches the tooltip).
local function appendIngredientEffects(rec, out)
    local alchemy = 0
    pcall(function() alchemy = types.NPC.stats.skills.alchemy(self).modified end)
    local wort = 15
    pcall(function() wort = core.getGMST("fWortChanceValue") end)
    pcall(function()
        local i = 0
        for _, eff in ipairs(rec.effects or {}) do
            i = i + 1
            if alchemy >= wort * i then
                local text, harmful = formatEffect(eff, false)
                table.insert(out, string.format('{"t":"%s","h":%s}',
                    jsonEscape(text), harmful and "true" or "false"))
            else
                table.insert(out, '{"t":"Unknown","h":false}')
            end
        end
    end)
end

-- Readable weapon type label ("Long Blade, One-Handed" / "Marksman (Bow)" / …) from the record's
-- weapon type enum, for the info popup's "Type" row. Mirrors the vanilla tooltip's skill + handedness.
local WEAPON_TYPE_LABELS = nil
local function weaponTypeStr(rec)
    local label
    pcall(function()
        if WEAPON_TYPE_LABELS == nil then
            local WT = types.Weapon.TYPE
            WEAPON_TYPE_LABELS = {
                [WT.ShortBladeOneHand] = "Short Blade, One-Handed",
                [WT.LongBladeOneHand] = "Long Blade, One-Handed",
                [WT.LongBladeTwoHand] = "Long Blade, Two-Handed",
                [WT.BluntOneHand] = "Blunt, One-Handed",
                [WT.BluntTwoClose] = "Blunt, Two-Handed",
                [WT.BluntTwoWide] = "Blunt, Two-Handed",
                [WT.SpearTwoWide] = "Spear, Two-Handed",
                [WT.AxeOneHand] = "Axe, One-Handed",
                [WT.AxeTwoHand] = "Axe, Two-Handed",
                [WT.MarksmanBow] = "Marksman (Bow)",
                [WT.MarksmanCrossbow] = "Marksman (Crossbow)",
                [WT.MarksmanThrown] = "Thrown",
                [WT.Arrow] = "Arrow",
                [WT.Bolt] = "Bolt",
            }
        end
        label = WEAPON_TYPE_LABELS[rec.type]
    end)
    return label
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
        emit("COMPANION_DEBUG: info - bad arg: " .. tostring(arg))
        return
    end

    local name, rows, effects = "", {}, {}

    if kind == "spell" then
        local rec = core.magic.spells.records[id]
        if not rec then emit("COMPANION_DEBUG: info - spell not found: " .. id); return end
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
        -- Resolve the record. A player item gives both an instance (for live condition) and
        -- a record; a VENDOR's barter item is NOT in the player inventory, so fall back to
        -- probing the item record stores by id so buying-side Info still works (record-level
        -- only — the Condition rows, which need an instance, are omitted). foundType lets the
        -- type checks below work with or without an instance.
        local rec, foundType = nil, nil
        if item then
            local okr; okr, rec = pcall(function() return item.type.record(item) end)
            if okr and rec then foundType = item.type else rec = nil end
        end
        if not rec then
            for _, ty in ipairs({ types.Weapon, types.Armor, types.Clothing, types.Potion,
                                  types.Ingredient, types.Lockpick, types.Probe, types.Book,
                                  types.Apparatus, types.Repair, types.Light, types.Miscellaneous }) do
                local ok, r = pcall(function() return ty.records[id] end)
                if ok and r then rec = r; foundType = ty; break end
            end
        end
        if not rec then emit("COMPANION_DEBUG: info - no record: " .. id); return end
        local function isType(ty) return foundType == ty or (item ~= nil and ty.objectIsInstance(item)) end
        name = (rec.name and rec.name ~= "" and rec.name) or id

        -- Instance condition (current / max), not the record's max health. nil when there is
        -- no instance (vendor items), which omits the Condition row.
        local function condStr(maxHealth)
            if not maxHealth or maxHealth <= 0 or not item then return nil end
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
        if weightStr and isType(types.Armor) then
            local cls = armorWeightClass(rec)
            if cls then weightStr = weightStr .. " (" .. cls .. ")" end
        end
        -- Type-specific stat rows FIRST (vanilla tooltip order), then Weight/Value LAST. Enchantment
        -- is NOT emitted here — it rides the streamed item exports (itemJson "ench") and the popup
        -- renders it from local state, so `effects` here carries only intrinsic potion/ingredient
        -- alchemy effects (avoids showing enchant effects twice).
        if isType(types.Weapon) then
            addRow(rows, "Type", weaponTypeStr(rec))
            -- Damage rows, keyed on the weapon CLASS exactly as the vanilla tooltip does
            -- (mwclass/weapon.cpp getToolTipInfo, which branches on mWeaponClass):
            --   melee  -> all THREE attacks, because all three are real and the player picks
            --             between them with the movement direction. This is the full stat block,
            --             not the list column's one-line summary -- do NOT collapse it to the
            --             primary attack again (that is the Aug 19 2026 regression).
            --   thrown -> ONE row, chop x2 (the weapon is its own ammo)
            --   ranged/ammo -> ONE row from CHOP; their slash/thrust are 0, so emitting all three
            --             printed "Slash 0-0 / Thrust 0-0" on every bow, crossbow, arrow and bolt.
            local function dmgRow(label, mn, mx)
                addRow(rows, label, string.format("%d-%d",
                    math.floor((mn or 0) + 0.5), math.floor((mx or 0) + 0.5)))
            end
            local cls = weaponClass(rec.type)
            if cls == "melee" then
                dmgRow("Chop", rec.chopMinDamage, rec.chopMaxDamage)
                dmgRow("Slash", rec.slashMinDamage, rec.slashMaxDamage)
                dmgRow("Thrust", rec.thrustMinDamage, rec.thrustMaxDamage)
            elseif cls == "thrown" then
                dmgRow("Damage", (rec.chopMinDamage or 0) * 2, (rec.chopMaxDamage or 0) * 2)
            else
                dmgRow("Damage", rec.chopMinDamage, rec.chopMaxDamage)
            end
            addRow(rows, "Reach", fmtNum(rec.reach))
            addRow(rows, "Speed", fmtNum(rec.speed))
            enchantPts()
            addRow(rows, "Condition", condStr(rec.health))
        elseif isType(types.Armor) then
            addRow(rows, "Armor Rating", fmtNum(rec.baseArmor))
            enchantPts()
            addRow(rows, "Condition", condStr(rec.health))
        elseif isType(types.Clothing) then
            enchantPts()
        elseif isType(types.Potion) then
            appendEffectList(rec.effects, effects, false)
        elseif isType(types.Ingredient) then
            appendIngredientEffects(rec, effects)
        elseif isType(types.Lockpick) or isType(types.Probe) then
            addRow(rows, "Quality", fmtNum(rec.quality))
            addRow(rows, "Uses Left", condStr(rec.maxCondition))
        elseif isType(types.Book) then
            if not rec.isScroll and rec.skill and rec.skill ~= "" then
                addRow(rows, "Skill", capitalize(tostring(rec.skill)))
            end
        end
        -- Weight (with armor-class suffix for armor) and Value LAST, matching the vanilla tooltip.
        addRow(rows, "Weight", weightStr)
        addRow(rows, "Value", fmtNum(rec.value))
    else
        emit("COMPANION_DEBUG: info - unknown kind: " .. tostring(kind))
        return
    end

    emit(string.format('COMPANION_INFO:{"name":"%s","rows":[%s],"effects":[%s]}',
        jsonEscape(name), table.concat(rows, ','), table.concat(effects, ',')))
end

-- =============================== Developer Tools ===============================
-- Cheats / test helpers behind the companion's "Developer mode" option (CMP:dev_*).
--
-- These deliberately use ordinary Lua APIs rather than raw console commands. Every stat setter
-- in mwlua/stats.cpp calls ObjectVariant::asSelfObject(), which throws for a global-script
-- object ("Allowed only in local scripts for 'openmw.self'") — so the stat writes MUST live
-- here in the player script, not in companion_global.lua. The one thing Lua genuinely cannot do
-- from this context is delegated: item creation (world.createObject is global-only →
-- CompanionDevGiveItems). There is no raw-console-command channel and deliberately so.
--
-- Every dev action now lands here; nothing is intercepted natively. The sole exception used to be
-- dev_resurrect (MechanicsManager::resurrect has no Lua binding), removed Aug 11 2026 — it needed
-- the game paused to reach, and that interaction left the session broken afterwards.
--
-- Stat writes are DELAYED (cached and applied at the end of the frame, see SelfObject::cacheStat),
-- so a read immediately after a write still returns the old value. Nothing here depends on that.

-- Two gold amounts, not one, because they answer different test questions: 10,000 is a plausible
-- wealthy-player purse (merchant gold limits still bite, prices still mean something), while
-- 100,000 exists to push six-digit values through the gold display, barter and encumbrance.
-- Both are additive per press, like every other dev give.
local DEV_GOLD_SMALL = 10000
local DEV_GOLD_LARGE = 100000
local DEV_MAX_VITAL = 99999
-- How much the attribute / skill buttons ADD per press. Deliberately additive rather than a set-to
-- ceiling: pressing repeatedly keeps stacking, which is what makes the buttons useful for pushing a
-- character far past vanilla limits to test UI at extreme values. 100 is vanilla's ordinary cap, so
-- one press on a fresh character lands roughly there and behaves like the old "max" button did.
-- Nothing clamps this — MWMechanics::AttributeValue::setBase is a plain assignment and the Lua
-- binding calls it directly — so values well above 100 stick.
local DEV_STAT_INCREMENT = 100
local DEV_SET_LEVEL = 20
-- NpcStats::getLevelupAttributeMultiplier does min(10, count) to pick iLevelUp<NN>Mult, so 10 is
-- the top tier (x5 in vanilla). Without this every attribute on the level-up screen reads x1.
local DEV_SKILL_INCREASES = 10
-- How many spells each of the two magic buttons grabs, and how many items the bulk button adds.
-- Clock settings for the two time-of-day buttons. Midday and late evening, i.e. unambiguously
-- lit and unambiguously dark — these exist mainly to exercise lighting-dependent behaviour such
-- as adaptive dimming, so the two ends matter more than the exact hours.
local DEV_DAY_HOUR = 12
local DEV_NIGHT_HOUR = 22
local DEV_SPELLKIT_PER_SCHOOL = 2
local DEV_STACK_EFFECT_COUNT = 12
local DEV_BULK_PER_TYPE = 6

-- Change-detected god-mode / noclip state for the options pills. Both are real ENGINE flags
-- (not preferences), so they can also be changed from the console or by another mod — polling
-- them on the slow tick is what keeps the pills honest. Costs nothing when nothing changes.
local lastDevState = nil
local function exportDevState()
    if not debugApi then return end
    local god, noclip = false, false
    pcall(function() god = debugApi.isGodMode() and true or false end)
    -- Reported as noclip (the button's wording) = the INVERSE of collision being enabled.
    pcall(function() noclip = (not debugApi.isCollisionEnabled()) and true or false end)
    local line = (god and "1" or "0") .. "|" .. (noclip and "1" or "0")
    if line ~= lastDevState then
        lastDevState = line
        emit("COMPANION_DEV_STATE:" .. line)
    end
end

-- Hand a list of { id = recordId, count = n } to the global script, which owns world.createObject
-- + moveInto (both global-only). Same shape as the existing drop / container-transfer events.
local function devGiveItems(items)
    if #items == 0 then
        emit("COMPANION_DEBUG: dev give - nothing to add")
        return
    end
    core.sendGlobalEvent('CompanionDevGiveItems', { actor = self.object, items = items })
    emit("COMPANION_DEBUG: dev give " .. #items .. " item stack(s)")
end

-- Pick up to `perGroup` record ids per group from a record store, using `groupOf(rec)` to bucket
-- and `accept(rec)` to filter. Records are chosen by their properties at runtime rather than from
-- a hardcoded id list: a wrong id would silently add nothing, and hardcoding also breaks on any
-- load order whose base game is not plain Morrowind.esm.
local function devPickRecords(store, accept, groupOf, perGroup, limit)
    local picked, counts, total = {}, {}, 0
    local ok = pcall(function()
        for _, rec in ipairs(store) do
            if total >= (limit or math.huge) then break end
            if (not accept) or accept(rec) then
                local group = groupOf and groupOf(rec) or ""
                if group ~= nil then
                    counts[group] = counts[group] or 0
                    if counts[group] < perGroup then
                        counts[group] = counts[group] + 1
                        total = total + 1
                        picked[#picked + 1] = rec
                    end
                end
            end
        end
    end)
    if not ok then return {} end
    return picked
end

-- Learnable spells (not powers/abilities/diseases), grouped by the school of their first effect,
-- cheapest first within each school so the kit is actually castable at low level.
local function devSpellCandidates()
    local spells = {}
    pcall(function()
        for _, rec in ipairs(core.magic.spells.records) do
            if rec.type == core.magic.SPELL_TYPE.Spell and rec.effects and rec.effects[1] then
                spells[#spells + 1] = rec
            end
        end
    end)
    table.sort(spells, function(a, b)
        local ca, cb = a.cost or 0, b.cost or 0
        if ca ~= cb then return ca < cb end
        return tostring(a.id) < tostring(b.id)
    end)
    return spells
end

local function devSchoolOf(rec)
    local school = nil
    pcall(function() school = rec.effects[1].effect.school end)
    return school and tostring(school) or "other"
end

local function devAddSpellKit()
    local known = {}
    pcall(function()
        for _, s in ipairs(types.Actor.spells(self)) do known[tostring(s.id)] = true end
    end)
    local added = 0
    local counts = {}
    for _, rec in ipairs(devSpellCandidates()) do
        local id = tostring(rec.id)
        local school = devSchoolOf(rec)
        counts[school] = counts[school] or 0
        if not known[id] and counts[school] < DEV_SPELLKIT_PER_SCHOOL then
            counts[school] = counts[school] + 1
            if pcall(function() types.Actor.spells(self):add(id) end) then added = added + 1 end
        end
    end
    exportSpells()
    emit("COMPANION_DEBUG: dev spell kit added " .. added .. " spell(s)")
end

-- Stack a pile of DIFFERENT active effects, for testing the effects summary / dropdown with
-- realistic volume. activeSpells:add applies the effect directly — no magicka cost, no fail
-- chance, no VFX — which is what makes it deterministic where repeated real casts are not.
-- Only spells whose first effect actually LASTS are usable: the API docs warn that an effect
-- with no duration expires instantly (that is what rules out abilities/instant damage here).
local function devStackEffects()
    local added = 0
    for _, rec in ipairs(devSpellCandidates()) do
        if added >= DEV_STACK_EFFECT_COUNT then break end
        local dur, harmful = 0, true
        pcall(function()
            local eff = rec.effects[1]
            dur = eff.duration or 0
            harmful = eff.effect.harmful and true or false
        end)
        -- Beneficial + lasting only, so the test pile can't kill the player it is testing on.
        if dur > 0 and not harmful then
            -- effects indexes are 0-BASED here (they index the record's effect list directly via
            -- .at(index) in magicbindings.cpp), unlike most OpenMW Lua list APIs.
            local ok = pcall(function()
                types.Actor.activeSpells(self):add({
                    id = tostring(rec.id), effects = { 0 }, stackable = true,
                })
            end)
            if ok then added = added + 1 end
        end
    end
    exportActiveEffects()
    emit("COMPANION_DEBUG: dev stacked " .. added .. " active effect(s)")
end

-- Enchanted ring + amulet pair, a torch, and one item per remaining equipment-slot family —
-- the shapes the inventory/HUD code paths special-case (enchant backdrop, charge meter, the
-- two-ring slot logic, carried-left light).
local function devRegressionKit()
    local items = {}
    local function addAll(recs, count)
        for _, rec in ipairs(recs) do items[#items + 1] = { id = tostring(rec.id), count = count or 1 } end
    end

    local T = types.Clothing.TYPE
    local enchanted = function(rec) return rec.enchant ~= nil and rec.enchant ~= "" end
    -- One enchanted ring and one enchanted amulet (grouped by clothing type, one each).
    addAll(devPickRecords(types.Clothing.records,
        function(rec) return enchanted(rec) and (rec.type == T.Ring or rec.type == T.Amulet) end,
        function(rec) return rec.type end, 1))
    -- Torch / lantern (carried_left, and the only item type with a burn-time condition).
    addAll(devPickRecords(types.Light.records, nil, function() return "light" end, 1))
    -- One armour piece per slot family, one weapon, plus shirt/pants for the clothing slots.
    addAll(devPickRecords(types.Armor.records, nil, function(rec) return rec.type end, 1))
    addAll(devPickRecords(types.Weapon.records, nil, function(rec) return rec.type end, 1))
    addAll(devPickRecords(types.Clothing.records,
        function(rec) return rec.type == T.Shirt or rec.type == T.Pants or rec.type == T.Shoes end,
        function(rec) return rec.type end, 1))
    devGiveItems(items)
end

-- A larger varied spread for inventory stress-testing (scrolling, sorting, category tabs).
local function devBulkItems()
    local items = {}
    local function addAll(recs, count)
        for _, rec in ipairs(recs) do items[#items + 1] = { id = tostring(rec.id), count = count or 1 } end
    end
    addAll(devPickRecords(types.Potion.records, nil, function() return "potion" end, DEV_BULK_PER_TYPE), 5)
    addAll(devPickRecords(types.Ingredient.records, nil, function() return "ingredient" end, DEV_BULK_PER_TYPE), 10)
    addAll(devPickRecords(types.Book.records, nil, function() return "book" end, DEV_BULK_PER_TYPE))
    addAll(devPickRecords(types.Miscellaneous.records, nil, function() return "misc" end, DEV_BULK_PER_TYPE), 3)
    addAll(devPickRecords(types.Weapon.records, nil, function(rec) return rec.type end, 1))
    addAll(devPickRecords(types.Armor.records, nil, function(rec) return rec.type end, 1))
    addAll(devPickRecords(types.Apparatus.records, nil, function() return "apparatus" end, 2))
    addAll(devPickRecords(types.Lockpick.records, nil, function() return "lockpick" end, 2))
    addAll(devPickRecords(types.Probe.records, nil, function() return "probe" end, 2))
    addAll(devPickRecords(types.Repair.records, nil, function() return "repair" end, 2))
    devGiveItems(items)
end

local function devDispatch(action)
    if action == "gold" or action == "gold10k" then
        -- createObject lowercases ESM3 ids, so 'gold_001' is the canonical form of Gold_001.
        -- One branch for both amounts, mirroring the day/night pair below: the only difference is
        -- the count, so splitting them would duplicate the id comment and the give call.
        local amount = (action == "gold10k") and DEV_GOLD_SMALL or DEV_GOLD_LARGE
        devGiveItems({ { id = 'gold_001', count = amount } })

    elseif action == "maxhealth" or action == "maxmagicka" or action == "maxfatigue" then
        -- One button per vital (health / magicka / fatigue are independently useful when testing).
        -- .base is the one that matters: it raises the MAX (this mirrors OpenMW's own SetHealth
        -- opcode, which sets base then fills current). Setting only .current would regenerate away.
        local vital = string.sub(action, 4)
        pcall(function()
            local stat = types.Actor.stats.dynamic[vital](self)
            stat.base = DEV_MAX_VITAL
            stat.current = DEV_MAX_VITAL
        end)
        emit("COMPANION_DEBUG: dev max " .. vital)

    elseif action == "addattributes" then
        -- Read-then-add, so each press stacks on the last. Safe despite stat writes being DELAYED
        -- (cached and applied at end of frame): each attribute is a separate stat, and successive
        -- presses are frames apart, so the read always sees the previous press's value.
        for _, attrId in ipairs(ATTR_IDS) do
            pcall(function()
                local stat = types.Actor.stats.attributes[attrId](self)
                stat.base = stat.base + DEV_STAT_INCREMENT
                stat.damage = 0
            end)
        end
        emit("COMPANION_DEBUG: dev add " .. DEV_STAT_INCREMENT .. " attributes")

    elseif action == "addskills" then
        -- Same shape as addattributes: .base is the trained value and .damage is separate
        -- (drain/absorb), so both have to be written for the skill to actually read the new total.
        -- SKILL_IDS is the same list the stats export walks, so this covers exactly the skills
        -- the Stats screen shows. Each write is pcall'd individually — a single unexpected id
        -- must not abort the other 26.
        for _, skillId in ipairs(SKILL_IDS) do
            pcall(function()
                local stat = types.NPC.stats.skills[skillId](self)
                stat.base = stat.base + DEV_STAT_INCREMENT
                stat.damage = 0
            end)
        end
        emit("COMPANION_DEBUG: dev add " .. DEV_STAT_INCREMENT .. " skills")

    elseif action == "god" then
        if debugApi then
            pcall(function() debugApi.toggleGodMode() end)
            exportDevState()
            emit("COMPANION_DEBUG: dev god mode toggled")
        end

    elseif action == "noclip" then
        if debugApi then
            pcall(function() debugApi.toggleCollision() end)
            exportDevState()
            emit("COMPANION_DEBUG: dev noclip toggled")
        end

    elseif action == "setlevel20" then
        -- Raw level jump — identical to the console's SetLevel (both only call
        -- CreatureStats::setLevel), so it deliberately SKIPS the level-up screen and the
        -- attribute gains that come with it. Use dev_levelup to exercise that flow instead.
        pcall(function() types.Actor.stats.level(self).current = DEV_SET_LEVEL end)
        emit("COMPANION_DEBUG: dev set level " .. DEV_SET_LEVEL)

    elseif action == "levelup" then
        -- Fill the level-up bar, then open the real screen. Normally the dialog is pushed by
        -- WaitDialog::onWaitingFinished when a REST ends with progress >= iLevelUpTotal; opening
        -- the mode directly avoids depending on rest being allowed (combat / underwater / no bed).
        local total = 10
        pcall(function() total = math.floor(core.getGMST("iLevelUpTotal")) end)
        pcall(function() types.NPC.stats.level(self).progress = total end)
        -- Without these the screen's attribute multipliers all read x1 (see DEV_SKILL_INCREASES).
        for _, attrId in ipairs(ATTR_IDS) do
            pcall(function()
                types.NPC.stats.level(self).skillIncreasesForAttribute[attrId] = DEV_SKILL_INCREASES
            end)
        end
        -- The stat writes above are DELAYED (applied at the end of this frame), and AddUiMode is
        -- itself deferred, so the dialog opens after they have landed.
        self:sendEvent('AddUiMode', { mode = 'LevelUp' })
        emit("COMPANION_DEBUG: dev level up (progress " .. total .. ")")

    elseif action == "spellkit" then
        devAddSpellKit()

    elseif action == "stackeffects" then
        devStackEffects()

    elseif action == "regressionkit" then
        devRegressionKit()

    elseif action == "bulkitems" then
        devBulkItems()

    elseif action == "day" or action == "night" then
        -- Global variables are global-script-only. Writing 'gamehour' routes through
        -- World::setGlobalFloat -> DateTimeManager::updateGlobalFloat -> setHour, i.e. the exact
        -- same path the console's "set gamehour to 22" takes.
        local hour = (action == "day") and DEV_DAY_HOUR or DEV_NIGHT_HOUR
        core.sendGlobalEvent('CompanionDevSetHour', { hour = hour })
        emit("COMPANION_DEBUG: dev set " .. action .. " (hour " .. hour .. ")")

    else
        emit("COMPANION_DEBUG: dev unknown action " .. tostring(action))
    end
end


-- ===== Exterior night ambient floor =====
--
-- Raises the AMBIENT light of exterior NIGHT so dark outdoor scenes are not pitch black, without
-- touching daytime at all. Companion to the engine's own `Shaders/minimum interior brightness`,
-- which does the same job for interiors and cannot reach exteriors: interior ambient comes from
-- RenderingManager::configureAmbient (cell mood), exterior ambient from the weather system, and
-- configureAmbient is simply never called for an exterior cell.
--
-- WHY THIS IS FLOOR-ONLY BY CONSTRUCTION, not by tuning. Each weather record carries FOUR separate
-- ambient colours (sunrise/day/sunset/night) in a TimeOfDayInterpolator, and its getValue() returns
-- a bare `mDayValue` for the whole day window. So a change to the NIGHT value provably cannot reach
-- midday — there is no blend between them to leak through. Dawn and dusk lerp between the sunrise/
-- sunset value and night, so the lift tapers smoothly across those windows and has vanished by the
-- midpoint where the lerp switches over to the day value.
--
-- WHY IT COMPOSES WITH ADAPTIVE DIMMING FOR FREE: the values written here feed
-- WeatherManager::update -> RenderingManager::setAmbientColour, which is the exact function
-- companion-ambient-export.patch reads COMPANION_AMBIENT from. So a brighter night automatically
-- reports a higher luminance and the companion dims itself LESS, with no extra plumbing. (A
-- post-processing shader would have changed the picture without changing that signal, leaving the
-- bottom screen dimmed for a darkness that is no longer on screen — which is why it was rejected.)
--
-- SEMANTICS: a hue-preserving additive LIFT of the night ambient's Rec.709 relative luminance --
-- scale the colour by (lum + lift)/lum, so the RATIO between channels is untouched. Two properties
-- are deliberate:
--   * Hue-preserving. Adding a flat grey instead would wash the blue out of moonlight, which is
--     exactly what "losing the moonlit look" means in practice. Verified: the b/r ratio is
--     bit-identical before and after at every lift value.
--   * A LIFT, not a hard floor (which is what the interior setting uses). Measured across the ten
--     vanilla weathers, night ambient luminance only spans 0.126 (Blight) to 0.213 (Snow) -- so any
--     floor big enough to be useful would clamp eight or nine of them to the SAME value and erase
--     night weather variety altogether. A lift moves them together and keeps their spacing exact.
-- A lift of 0 leaves every weather bit-identical to vanilla, so the feature is fully off at 0.
local nightLift = 0.0
-- Original night colour per weather recordId, captured BEFORE we ever write. Every application
-- recomputes from this, never from the current value, so repeatedly applying (or LOWERING) the
-- slider can never compound or ratchet. Weather records are engine data and are not stored in the
-- .omwsave, so this snapshot is the vanilla value for the session.
local nightAmbientOriginals = {}
local nightLiftVerified = nil   -- nil = not yet probed, true/false = read-back result

local function relLuminance(c)
    return 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b
end

-- Applies `nightLift` to every weather record. Safe to call repeatedly.
local function applyNightAmbientFloor()
    local ok, err = pcall(function()
        for _, weather in pairs(core.weather.records) do
            local id = weather.recordId
            local interp = weather.ambientColor
            if id and interp then
                -- Snapshot once. Guarded by presence so a re-application never re-captures a value
                -- we already modified.
                if nightAmbientOriginals[id] == nil then
                    local n = interp.night
                    nightAmbientOriginals[id] = { r = n.r, g = n.g, b = n.b, a = n.a }
                end
                local orig = nightAmbientOriginals[id]
                local lum = relLuminance(orig)
                local target = orig
                if nightLift > 0 then
                    if lum > 0.0001 then
                        local k = (lum + nightLift) / lum
                        target = { r = orig.r * k, g = orig.g * k, b = orig.b * k, a = orig.a }
                    else
                        -- Pure black night: no hue to preserve, so a neutral grey is the only
                        -- sensible target. Mirrors configureAmbient's own black special case.
                        target = { r = nightLift, g = nightLift, b = nightLift, a = orig.a }
                    end
                end
                interp.night = util.color.rgba(
                    math.min(target.r, 1), math.min(target.g, 1), math.min(target.b, 1), target.a)

                -- READ-BACK PROBE (one-shot, first weather record only). The engine hands weather
                -- records to Lua as `const MWWorld::Weather*` while the interpolator's setters take
                -- a non-const ref, so whether sol permits this write at all is worth PROVING at
                -- runtime rather than assuming. (Static answer: sol3's pointer pusher keys the
                -- metatable on meta::unqualified_t<T>, which strips const, so the write is allowed.
                -- This confirms it on the actual device.)
                -- Only meaningful when the write actually CHANGES something: at lift 0 the value
                -- written equals the value already there, so a read-back would compare the original
                -- against itself and report success even if the write silently did nothing. Skip
                -- until a lift is set (at which point the very next application probes).
                if nightLiftVerified == nil and math.abs(math.min(target.r, 1) - orig.r) > 0.0005 then
                    local back = interp.night
                    local want = math.min(target.r, 1)
                    nightLiftVerified = math.abs(back.r - want) < 0.002
                    emit('COMPANION_NIGHT_AMBIENT_OK:' .. tostring(nightLiftVerified))
                    emit(string.format(
                        'COMPANION_DEBUG: night ambient lift %.3f - %s: lum %.4f -> %.4f (wrote r=%.4f, read r=%.4f)',
                        nightLift, tostring(id), lum, relLuminance(back), want, back.r))
                end
            end
        end
    end)
    if not ok then
        emit('COMPANION_DEBUG: night ambient lift FAILED: ' .. tostring(err))
        if nightLiftVerified == nil then
            nightLiftVerified = false
            emit('COMPANION_NIGHT_AMBIENT_OK:false')
        end
    end
end

local function setNightLift(value)
    local v = tonumber(value)
    if v == nil then return end
    if v < 0 then v = 0 elseif v > 1 then v = 1 end
    nightLift = v
    applyNightAmbientFloor()
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
        -- TOGGLE: if the Interface mode (the map view) is already the active mode,
        -- a second minimap tap closes it instead of re-opening (mirrors B/Back).
        if interfaces.UI.getMode() == interfaces.UI.MODE.Interface then
            pcall(function() interfaces.UI.removeMode(interfaces.UI.MODE.Interface) end)
        else
            -- Guard: only OPEN the map once the character is created. During character creation the
            -- in-game inventory/map GUI isn't available, and forcing it (AddUiMode Interface) wedges
            -- the game (the top-screen map can't render). The first journal entry is the reliable
            -- "character created" signal. The close branch above is never gated (if the map is open,
            -- the character already exists). The Kotlin map tap gates on this too — defence in depth.
            local ready = false
            pcall(function() ready = #types.Player.journal(self).journalTextEntries > 0 end)
            if ready then
                self:sendEvent('AddUiMode', { mode = 'Interface', windows = { 'Map' } })
            end
        end
        return
    end
    if payload == "container_take_all" then
        -- Reentrancy: ignore if a bulk take-all/dispose close is already pending (its deferred
        -- transfer is still landing) so we don't double-queue or race the pending close.
        if containerObj and not containerCloseAfterRefresh then
            core.sendGlobalEvent('CompanionContainerTransfer',
                { container = containerObj, player = self.object, dir = 'takeall',
                  hiddenSids = hiddenSidList() })
            -- Do NOT removeMode this frame. item:moveInto is a DEFERRED LuaManager action
            -- (objectbindings.cpp) that only adds/removes on a later update(); closing the container
            -- in the same frame tore it down before the add ran, silently no-oping the transfer.
            -- The GLOBAL script sends CompanionContainerClose back AFTER queuing the transfer, and we
            -- close on that event (onContainerCloseRequest). Event-driven, NOT an onFrame timer,
            -- because dt is 0 / frames don't advance while the container is open and idle, so a timed
            -- close never fired. The flag blocks a second take-all/dispose/take/put until we close.
            containerCloseAfterRefresh = true
            -- One "pick up" sound per Take-All batch (vanilla container.cpp plays only the
            -- first object's sound), captured now while the container still holds the items.
            playItemSound(firstContainerItem(), true)
            emit("COMPANION_DEBUG: container take all (await close)")
        end
        return
    end
    -- Developer Tools (CMP:dev_<action>). All no-arg, so they are matched here alongside the
    -- other no-arg commands, before the "<action> <arg>" split below.
    if string.sub(payload, 1, 4) == "dev_" then
        devDispatch(string.sub(payload, 5))
        return
    end
    if payload == "container_close" then
        -- close the container window without taking anything
        pcall(function() interfaces.UI.removeMode(interfaces.UI.MODE.Container) end)
        emit("COMPANION_DEBUG: container close")
        return
    end
    if payload == "container_dispose" then
        -- dispose = take all + REMOVE the corpse (dir='dispose', handled in companion_global.lua).
        -- Same deferred-transfer race as take-all (the moveInto loop AND the container:remove are
        -- queued LuaManager actions), so defer the close the same way instead of removeMode-ing this
        -- frame. Reentrancy-guarded like take-all.
        if containerObj and not containerCloseAfterRefresh then
            core.sendGlobalEvent('CompanionContainerTransfer',
                { container = containerObj, player = self.object, dir = 'dispose',
                  hiddenSids = hiddenSidList() })
            -- Same event-driven close as take-all (see container_take_all). Global sends
            -- CompanionContainerClose after queuing the transfer + corpse removal.
            containerCloseAfterRefresh = true
            playItemSound(firstContainerItem(), true)  -- one up sound per batch (see take-all)
            emit("COMPANION_DEBUG: container dispose (await close)")
        end
        return
    end
    local action, arg = string.match(payload, "^(%S+)%s+(.+)$")
    if not action then return end

    if action == "night_brightness" then
        -- Exterior night ambient lift (0 = off, exact vanilla). Pushed from the DS options slider; also
        -- re-sent on load, so this is the only place the value enters Lua.
        setNightLift(arg)
        return
    elseif action == "spell" then
        local spell = core.magic.spells.records[arg]
        if spell then
            types.Actor.setSelectedSpell(self, spell)
            ambient.playSound("Menu Click")
            emit("COMPANION_DEBUG: selected spell " .. arg)
        else
            local item = types.Actor.inventory(self):find(arg)
            -- Scrolls AND cast-on-use enchanted items (rings/amulets/etc.) both
            -- select via setSelectedEnchantedItem; accept any enchanted item.
            if item and itemEnchantId(item) ~= nil then
                types.Actor.setSelectedEnchantedItem(self, item)
                ambient.playSound("Menu Click")
                emit("COMPANION_DEBUG: selected enchanted item " .. arg)
            else
                emit("COMPANION_DEBUG: spell/enchanted item not found: " .. arg)
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
    elseif action == "use" then
        -- potion/ingredient/apparatus/repair — consume or open the alchemy/repair
        -- menu via the native use() action (see useItem). Re-export so a consumed
        -- potion/ingredient drops out of the list promptly (the slow tick would too).
        useItem(arg)
        exportInventory()
    elseif action == "drop" then
        local id, countStr = string.match(arg, "^(.+)|(%d+)$")
        if id then
            core.sendGlobalEvent('CompanionDropItem',
                { actor = self.object, itemId = id, count = tonumber(countStr) })
            -- Dropping to the world plays the item's material-specific "put down" (down) sound.
            -- Resolve the same item the global drop resolves (inventory:find(id)); still present
            -- here since the actual teleport is a deferred global action.
            playItemSound(types.Actor.inventory(self):find(id), false)
            emit("COMPANION_DEBUG: drop " .. id .. " x" .. countStr)
            exportInventory()
        end
    elseif action == "read" then
        local item = types.Actor.inventory(self):find(arg)
        if item and types.Book.objectIsInstance(item) then
            self:sendEvent('AddUiMode', { mode = 'Book', target = item })
            emit("COMPANION_DEBUG: reading " .. arg)
        else
            emit("COMPANION_DEBUG: read - book not found: " .. arg)
        end
    elseif action == "info" then
        exportInfo(arg)
    elseif action == "container_take" then
        local sid, countStr = string.match(arg, "^(.+)|(%d+)$")
        -- Ignore while a bulk take-all/dispose close is pending (its deferred transfer is landing).
        if sid and containerObj and not containerCloseAfterRefresh then
            core.sendGlobalEvent('CompanionContainerTransfer',
                { container = containerObj, player = self.object, sid = sid,
                  count = tonumber(countStr), dir = 'take' })
            scheduleContainerRefresh()
            playItemSound(containerItemBySid(sid), true)  -- material-specific "pick up" sound
            emit("COMPANION_DEBUG: container take " .. sid .. " x" .. countStr)
        end
    elseif action == "container_put" then
        local sid, countStr = string.match(arg, "^(.+)|(%d+)$")
        if sid and containerObj and not containerCloseAfterRefresh then
            -- The item is in the player's own inventory (needed for the sound AND the capacity check).
            local putItem = nil
            for _, i in ipairs(types.Actor.inventory(self):getAll()) do
                if tostring(i.id) == sid then putItem = i; break end
            end
            local wantCount = tonumber(countStr) or 1
            -- Vanilla container-put gate (organic / capacity / weight) — authoritative backstop.
            -- Kotlin gates client-side too (so normally no blocked put reaches here); if one does,
            -- emit the vanilla message for the banner and DO NOT transfer. Take is never gated.
            local blockMsg = putItem and containerPutBlockMessage(putItem, wantCount) or nil
            if blockMsg then
                emit('COMPANION_PUT_BLOCKED:' .. blockMsg)
            else
                core.sendGlobalEvent('CompanionContainerTransfer',
                    { container = containerObj, player = self.object, sid = sid,
                      count = wantCount, dir = 'put' })
                scheduleContainerRefresh()
                -- Vanilla plays the up ("pick up") sound for a container transfer in either direction.
                playItemSound(putItem, true)
            end
            emit("COMPANION_DEBUG: container put " .. sid .. " x" .. countStr)
        end
    end
end

local function onConsoleCommand(mode, command)
    if mode ~= "Companion" then return end
    dispatchCommand(command)
end

-- Container open/close, driven by the omw/ui.lua UiModeChanged event
-- ({oldMode, newMode, arg}). arg is the container/corpse/NPC being opened.
local function onUiModeChanged(data)
    -- GM_MainMenu ("MainMenu") is the pause/options menu (Start button). It is
    -- distinct from Container/Dialogue/Barter/etc., so it uniquely identifies the
    -- pause menu opening and closing.
    pcall(function()
        local MM = interfaces.UI.MODE.MainMenu
        if data.newMode == MM then
            emit('COMPANION_PAUSE_MENU_OPEN:')
        elseif data.oldMode == MM and data.newMode ~= MM then
            emit('COMPANION_PAUSE_MENU_CLOSED:')
        end
    end)

    local isContainer = false
    local ok = pcall(function()
        isContainer = (data.newMode == interfaces.UI.MODE.Container)
    end)
    if not ok then return end
    if isContainer and data.arg then
        containerObj = data.arg
        containerIsCorpse = false
        pcall(function()
            containerIsCorpse = types.Actor.objectIsInstance(data.arg)
                and types.Actor.isDead(data.arg)
        end)
        -- pickpocket = a living (non-corpse) actor; excludes corpses AND plain chests.
        containerIsPickpocket = false
        pcall(function()
            containerIsPickpocket = types.Actor.objectIsInstance(data.arg) and not containerIsCorpse
        end)
        containerHiddenSids = computeHiddenSids()   -- roll the Sneak-hidden set ONCE
        lastContainerJson = nil
        containerReexportTicks = 0
        containerCloseAfterRefresh = false
        exportContainer(true)
    elseif containerObj ~= nil and not isContainer then
        containerObj = nil
        containerIsPickpocket = false
        containerHiddenSids = nil
        lastContainerJson = nil
        containerReexportTicks = 0
        containerCloseAfterRefresh = false
        emit('COMPANION_CONTAINER_CLOSED:')
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
        exportAmmo()
        exportEquippedCharge()
        exportActiveEffects()
        exportCharacter()
        exportCharacterDetail()
        exportTarget()
        exportPlayerStatus()
        exportDoorMarkers()
        exportDevState()
    end
    journalTimer = journalTimer + dt
    if journalTimer >= JOURNAL_INTERVAL then
        journalTimer = 0
        exportJournal()
    end
end

-- Fires every real frame, INCLUDING while the game is paused (luamanagerimp.cpp
-- calls onFrame with the real frame duration regardless of pause, whereas onUpdate
-- gets dt=0 when paused). The container GUI pauses the game, so this is the ONLY
-- place a post-transfer refresh can run while the looting overlay is open. Only
-- does work when a container is open and a transfer is pending, so it's free
-- otherwise. Refreshes BOTH sides: exportContainer (the container list, change-
-- detected) and exportInventory (the player list, which is otherwise frozen by the
-- paused slow tick). Throttled so a transfer emits ~3 refreshes over ~0.24s, which
-- covers the async moveInto completing (~1-2 frames).
local function onFrame(dt)
    if containerObj and containerReexportTicks > 0 then
        containerRefreshTimer = containerRefreshTimer + dt
        if containerRefreshTimer >= CONTAINER_REFRESH_INTERVAL then
            containerRefreshTimer = 0
            containerReexportTicks = containerReexportTicks - 1
            exportContainer(false)
            exportInventory()
        end
    end
end

local function onActive()
    -- NOTE: we deliberately do NOT call ui.setConsoleMode("Companion") here any more.
    --
    -- That set the REAL console's persistent mode for the whole session, and console.cpp keys three
    -- behaviours off a non-empty mode: it echoes "<mode> <command>" instead of "> <command>", it
    -- routes EVERY typed command to Lua and `return`s before the MWScript path, and it disables Tab
    -- completion. Net effect for a player: typing anything into the console printed
    -- "Companion <command>" and then silently did nothing, because our onConsoleCommand accepted it
    -- (mode matched) and dispatchCommand dropped it for lacking the "CMP:" prefix. It also broke
    -- OpenMW's own Lua console (omw/console/player.lua only recognises `lua`/`luap`/... when
    -- mode == ''), and stomped the Lua[...] modes that script sets for itself.
    --
    -- The CMP: channel does NOT depend on this. Commands injected from Kotlin arrive via
    -- drainCompanionCommands -> handleConsoleCommand("Companion", cmd, ptr), which passes that
    -- string as a literal ARGUMENT; LuaManager::handleConsoleCommand forwards its parameter
    -- straight to PlayerScripts::consoleCommand -> the onConsoleCommand engine handlers, never
    -- reading the console window's own mConsoleMode. So the `mode ~= "Companion"` guard below still
    -- matches injected commands, and now correctly ignores anything the player types.
    exportJournal()
    -- Re-apply the exterior night ambient floor. Weather records are engine data rebuilt from
    -- content files on every game start and are NOT stored in the .omwsave, so the write has to be
    -- redone each session; onActive also fires on the script recreated after a load. Kotlin pushes
    -- the current slider value right after this, which is what actually sets a non-zero floor --
    -- this call re-establishes whatever value this Lua state already knows about.
    applyNightAmbientFloor()
    -- ...and ask Kotlin to re-push it. This script's locals do NOT survive a load (LuaManager::
    -- clear() destroys the player script and a fresh one is built), so nightLift is back at 0 here
    -- and the call above would otherwise silently restore vanilla nights until the slider is next
    -- touched. Kotlin answers with CMP:night_brightness.
    emit('COMPANION_REQUEST_SETTINGS:')
    -- Force-dismiss the bottom-screen options overlay after a game LOAD.
    -- Loading from the pause menu tears down GM_MainMenu, but the resulting
    -- UiModeChanged Lua event is queued via sendEvent and then wiped by the
    -- same-frame loadGame -> LuaManager::clear() (mLuaEvents.clear()), so our
    -- UiModeChanged handler never fires COMPANION_PAUSE_MENU_CLOSED and the
    -- overlay sticks. onActive fires on the freshly re-created player script
    -- after load (objectAddedToScene -> OnActive), which is exactly the moment
    -- to clear stale pause-menu state. Unconditional emit is safe: onActive
    -- can't fire while idle in the pause menu, and CLOSED-when-already-closed
    -- is a no-op on the Kotlin side. (A tracking boolean would not survive the
    -- load — clear() destroys this script instance, so locals reset.)
    emit('COMPANION_PAUSE_MENU_CLOSED:')
    -- Tell Kotlin which save this is. onActive is the right moment: it runs after onInit/onLoad
    -- (script creation always precedes activation), and it fires again on the recreated script
    -- after every load, so switching saves at runtime re-buckets the manual journal automatically.
    emitSaveId()
end

-- Global-script callback: the bulk transfer (take-all / dispose) has been queued in
-- companion_global.lua, so close the container window NOW. Driven by an event (not an onFrame
-- timer) so it fires reliably — while a container is open the game is paused and frames don't
-- advance dt / don't render while idle, so any timed close would stall until the next input.
-- Sent AFTER the moveInto loop, so the transfer's deferred adds are already queued (and run in
-- applyDelayedActions) before this closes. removeMode -> UiModeChanged clears containerObj + the flag.
local function onContainerCloseRequest()
    containerCloseAfterRefresh = false
    pcall(function() interfaces.UI.removeMode(interfaces.UI.MODE.Container) end)
end

return {
    engineHandlers = {
        onUpdate = onUpdate,
        onFrame = onFrame,
        onActive = onActive,
        onConsoleCommand = onConsoleCommand,
        -- Save-identity token (see mintSaveId): minted on a new game, round-tripped through the
        -- .omwsave on save/load. Backs the per-save bucketing of manual journal entries.
        onInit = onInit,
        onSave = onSave,
        onLoad = onLoad,
    },
    eventHandlers = {
        CompanionCombatTarget = onCombatTarget,
        Hit = onPlayerHit,
        UiModeChanged = onUiModeChanged,
        CompanionContainerClose = onContainerCloseRequest,
    },
}