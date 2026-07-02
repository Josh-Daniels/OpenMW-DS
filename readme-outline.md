# OpenMW-DS — README Outline

---

## What is this?
- What Morrowind is (briefly)
- What OpenMW is (open-source reimplementation)
- What the AYN Thor is (dual-screen Android handheld gaming device)
- The problem: Morrowind's UI was designed for mouse/keyboard, not touch
- What OpenMW-DS does: moves the game UI to the bottom touch screen
- Screenshot / photo of the device showing both screens

---

## Lineage & Credits
- OpenMW (open-source Morrowind engine, GPL v3)
- openmw-android (Sisah2/xyzz — Android port)
- Alpha3 (duron27 — Android launcher and overlay)
- This fork and what it adds

---

## Features

### HUD Tab (home screen)
- Health, Magicka, Fatigue bars with active effects
- Active effects dropdown (tap to expand, shows effect name, source spell, magnitude)
- Currently equipped weapon and selected spell (corner icons, tap for name)
- Favourite gear and spell quick-slots (long-press to assign, tap to equip/cast)
- Live minimap (exterior and interior, player-centered)
- Tap minimap to open full world map
- Combat target health bar (driven by engine's actual combat state)

### Inventory Tab
- Full item list with real icons
- Category filter tabs (All / Weapons / Armor / Apparel / Tools / Books / Consumables / Misc)
- Item stats per row (damage range, armor rating, condition bar)
- Tap to equip/unequip, long-press for Info / Drop / Add to favourites
- Item info popup (damage, effects, armor class, condition)

### Spells Tab
- Powers, Spells, Scrolls with filter tabs
- Real spell icons
- Tap to select/cast, long-press for Info / Add to favourites
- Spell info popup (cost, school, effects)

### Stats Tab
- Character header (name, race, class, birthsign, level)
- Reputation and bounty (bounty shown in red if > 0)
- Faction memberships with rank titles
- All 8 attributes with current/base values and buff/debuff tinting
- Active effects list with source spell name and magnitude
- All skills grouped by Major/Minor/Misc
- Tapping any stat, effect, faction, race, class, or birthsign opens a detail popup
- Skill and level progress bars toward next increase

### Journal Tab
- Chronological view (paged by day)
- Quests list with active/completed filter toggle
- Quest detail (full entry history per quest)
- Topics browser with alphabet letter dividers
- Topic detail (full response history, who said what)

### Dialogue System
- Full conversation on the bottom screen while game world stays visible on top
- NPC name header, scrollable conversation history
- Tappable hyperlinks within NPC responses
- Topics and services listed on the right (dimmed when choices are active)
- Choice/question prompts appear inline in the conversation flow
- In-dialogue message boxes shown in conversation history
- Goodbye button

---

## Hide UI Integration
- Standard OpenMW Hide UI toggle hides the top screen HUD
- Companion screen takes over all interaction during Hide UI
- NPC floating names remain visible
- Native crosshair remains visible (hides in third-person as normal)
- Crime/notification alerts remain visible
- Gamepad cursor disabled during Hide UI
- Alpha3 touch overlay also hides in sync

---

## How it works (brief technical overview)
- Lua mod exports game state as COMPANION_* lines to openmw.log
- Companion app tails the log and parses updates in real time
- Commands sent back to the engine via console channel (CMP: prefix)
- Dialogue system uses native C++ hooks (topic list not accessible from Lua)
- Combat target detection uses actor-local AI scripts (engine's actual combat state)
- Icon rendering: DXT-compressed DDS textures decompressed in software, cached as PNG
- Engine patches: what's modified and why (summary)

---

## Device compatibility
- Designed for AYN Thor (dual-screen Android, 1240×1080 bottom touch screen)
- May work on other dual-screen Android devices
- Single-screen Android: untested, companion screen behavior unknown
- Requires a physical controller for gameplay (companion screen is touch-only)

---

## Requirements
- Morrowind data files (you must own a copy of Morrowind)
- Alpha3 launcher installed and configured with your game files
- Developer mode enabled on your device (for APK sideloading)

---

## Installation
- Download the APK from the Releases page
- Enable "Install from unknown sources" on your device
- Install the APK
- Launch the app and press Play
- Note: assets deploy on first Play press, not on app launch

---

## Building from source
- Prerequisites (Android Studio, NDK r25+, CMake 3.22, gnu-sed on macOS)
- Note about build time (native OpenMW recompile is long — 30+ minutes first time)
- Clone the repo
- Build command: `./gradlew assembleDebug`
- Install command: `adb install -r -t app/build/outputs/apk/debug/app-debug.apk`
- Note: Lua mod deploys on Play press, not adb install

---

## Known limitations
- Combat target shows whoever is actively fighting you via the engine's AI system
  — may show multiple attackers' last update rather than a strict "primary target"
- Active effect source name requires the engine's activeSpells API
  (available in this build)
- Controller navigation of the companion screen not supported — touch only
- B controller shortcut for Goodbye does not work while Hide UI is active
  — use the companion Goodbye button instead
- Quest names may fall back to prettified IDs for quests without a display name
  in the ESM data
- Topics browser requires pressing Play to refresh after learning new topics

---

## Licence
- GPL v3 (inherited from OpenMW)
- You must provide your own Morrowind data files
- Source code available in this repository per GPL v3 requirements

---

## Links & Credits
- OpenMW project
- openmw-android (Sisah2)
- Alpha3 launcher (duron27)
- AYN Thor device
