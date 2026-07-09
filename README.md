# OpenMW-DS

A second-screen for OpenMW on the AYN Thor handheld. The game plays on the top screen while the bottom screen shows live
stats, inventory, magic, journal, and a minimap. It handles full touch implementation.

This project is a fork of **[Alpha3](#credits)**, a multi-engine Android launcher that can handle OpenMW,
which is itself a fork of the **[openmw-android](#credits)** project, which builds on **[OpenMW](#creadits)**, the open-source Morrowind engine.

If you don't have a thor or don't care about dual-screen Morrowind, you probably want **[Alpha3](#credits)** instead.

![Image of the HUD tab in game](./outputs/hud.jpeg)

---
## Features

- **Minimap**: a live, player-centered, rotating map of the current cell, rendered by the engine itself and streamed to
  the second screen (uses the same system as the vanilla in game minimap would).
- **Live character stats**: health, magicka, fatigue, & active effects, updated continuously.
- **Favourite items & spells**: Added little buttons on the minimap that can be used to store favourites for faster
  swapping of weapons or spells.
- **Inventory**: full list of inventory with tap to equip/unequip, long press for info, drop, add to favourites.
- **Magic**: spells, powers, and scrolls with tap to equip/unequip, long press for info or add to favourites.
- **Journal**: Lower screen version of the in game journal (with all functionality: quests, topics, etc).
- **Optional UI Overhaul**: an overhaul of the in game UI to make it look more like the companion UI on the bottom
  screen. Completely optional and can be turned off in the options menu (on pause). This is just to help with the stark
  contrast between vanilla resolution and OpenMW-DS lower screen resolution.
- **Optional Touch Contols**: Alpha3 uses the left thumbstick as a mouse for menus. This version allows the use of touch
  input instead (Activated at the bottom of the OpenMW-DS Options page).

---
## AI Disclosure

This project was built as a personal project for me to learn how to use AI tools. I am an ICT student and avoid the use
of AI while at Uni, but I will need to know how to use it for employment in the future. Claude.ai was used as well as
Claude Code in Android Studio. Both were used extensively. I take no credit for the work in this, I really just acted as
a manager, making decisions and keeping an eye out for AI mistakes to fix.

If you are opposed to AI usage in programming then I strongly advise you avoid this project.

---
## Requirements

- AYN Thor handheld.
    - https://www.ayntec.com/products/ayn-thor 
- Morrowind data files (you must own a copy of Morrowind)
    - https://store.steampowered.com/app/22320/The_Elder_Scrolls_III_Morrowind_Game_of_the_Year_Edition/ or
    - https://www.gog.com/en/game/the_elder_scrolls_iii_morrowind_goty_edition

---
## Installation

- Download the APK from the Releases page
- Enable "Install from unknown sources" on your device
- Install the APK
- Launch the app and press Play
- Follow the Alpha3 Setup Guide below.

---
## Alpha3 Setup Guide

OpenMW-DS runs on top of Alpha3, which handles game file management and device configuration. These instructions cover
setting up Alpha3 specifically for the AYN Thor running Morrowind.

> I did not create Alpha3, these are just the steps I take to get OpenMW running [Credits](#credits--license-chain).

### Settings up the game files.

- Open the app and allow access to files.
- Up the top of the home screen you will see something along the lines of (select game files), tap that.
- Navigate to your Morrowind folder (NOT DATA FILES), use this folder.
- Tap on settings gear on the right, then settings.cfg on left, go back to home page (not sure why, but this refreshes
  the home page).
- You should see the location of your Morrowind listed in the Data tab. Tap Content to check the esm's are there.
- If you have mods now you may go to home, tap the add folder icon at the bottom of the screen, navigate to your data
  files folder, tap "use this folder".
- Check the content tab again to see if esm's and now mods are there too.

### Recommended Settings

These settings are optional but work well with OpenMW-DS. 
- Tap settings, then go to Settings.cfg on the left.
- Open the GUI drop down from the list and enable controller menus and controller tooltips
- Tap the Controls button on the left menu (where you saw Settings.cfg), then configure controls.
- I find this page very confusing. You can see a scroll wheel there and I cannot find a way to make it bugger off, so:
    - Tap the arrow next to the gear, then the spanner icon, now a spinning icon should appear above the scroll wheel,
      tap that.
    - Go to the colours tab and turn the alpha all the way down (the slider under the colour wheel) and save.
    - It is still visible. So now carefully tap and hold the spinning icon and drag it down so the wheel is as 
      off-screen as you can (It should go fully off-screen with the spinning circle still visible.)
    - Now tap that spanner icon and make sure you can't see the scroll wheel anymore. If it's still slightly visible,
      tap the spanner and move it again.
    - Tap the Gear icon and choose "return to launcher"/
- You should now be able to play the game.
- If you want you can play around with some settings in the settings.cfg again, like adding shadows etc.

### Save Location

- If you want to use your openMW saves. I found the save location to be:
- /storage/emulated/0/Alpha3/saves. (consoles internal storage/Alpha3/saves)

---
## Credits

- **[OpenMW](https://gitlab.com/OpenMW/openmw)** ([GitHub mirror](https://github.com/OpenMW/openmw)) - the open-source
  Morrowind engine reimplementation everything here ultimately depends on. Licensed GPLv3.
- **[openmw-android](https://github.com/xyzz/openmw-android)** (originally by xyzz/sandstranger) - the original Android
  port of OpenMW, compiling the engine as a library and wrapping it for Android. Licensed GPLv3; the Android wrapper
  itself, not just the engine, is GPLv3, since it's a derivative work.
- **[Sisah2/openmw-android](https://github.com/Sisah2/openmw-android)** - a fork carrying forward significant
  OpenMW-on-Android improvements (the original repository this README's license file is sourced from). No longer under
  active development as of writing, but its `LICENSE.txt` and `3rdparty-licenses.txt` are the basis for this project's
  own.
- **[Alpha3](https://gitlab.com/duron27/alpha3)** (Jared Davenport / duron27) - the multi-engine launcher (OpenMW, UQM,
  Dethrace) with native haptics and a Compose UI customization system that this project is directly forked from. Many of
  Alpha3's OpenMW-specific improvements build on the Sisah2 lineage above.

---
# Building from Source

## Prerequisites

- **Android Studio** - (latest stable recommended)
- **NDK 29.0.14206865** - install via Android Studio SDK Manager
- **CMake 3.22.1** - install via Android Studio SDK Manager
- **JDK 21** - bundled with Android Studio, or install separately
- **ADB** - included with Android SDK platform-tools
- **macOS note:** install `gnu-sed` via Homebrew (`brew install gnu-sed`) - the build scripts require it

## First build (fresh clone)

First-time builds compile OpenMW's full C++ source from scratch. This takes **10–90 minutes**. M4 Max MacBook Pro takes
approximately 15 minutes, for reference. Subsequent builds are much faster since only changed files are recompiled.

Clone the git from the link provided by GitHub.

Open the project in Android Studio and let the Gradle sync complete before building. Then build from terminal:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug
```

> **Note on Android Studio's Run button:** use `./gradlew assembleDebug` from terminal rather than the Run button. The
> project's native build setup and custom Gradle tasks are more reliably handled by the terminal build.

## Installing

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

The `-t` flag is required for debug builds. The `-r` flag reinstalls over an existing installation.

### Troubleshooting: app crashes on first launch

If the app crashes immediately after install with an error about missing `settings.cfg`, the OpenMW asset bundle wasn't
included in the APK. This can happen on a fresh clone where CMake's install stamp exists but the assets were never
generated.

Fix:

```bash
find app/.cxx -path "*/openmw-stamp/openmw-install" -delete
./gradlew assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

This forces the CMake install step to rerun and copy the required assets into the APK.

> **Note:** game assets and the companion Lua mod are deployed on the first Play button press, not at install time. The
> app will copy files to your device storage the first time you launch a game.

## Subsequent builds

For Kotlin-only changes (no C++ modifications):

```bash
./gradlew assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

For changes to the Lua companion mod only, you can skip the full build and push directly to the device:

```bash
adb push app/src/main/assets/companion/scripts/companion/ \
  /storage/emulated/0/Alpha3/OpenMW/Mods/companion/scripts/companion/
```

## Native library pinning

Five native libraries (`libbsatool.so`, `libSDL2.so`, `libng_gl4es.so`, `libcollada-dom2.5-dp.so`, `libopenal.so`) are
pinned to known-working versions in `app/src/main/backup-libs/`. The build system automatically restores these after
CMake builds them, since fresh macOS builds of these specific libraries produce binaries incompatible with the device.
This happens automatically, so you don't need to do anything manually.

If you are building inside the project's Docker/Fedora environment (see `Dockerfile`), the pinning is less necessary as
the Linux toolchain produces compatible binaries.

## Pre-install wipe (for clean testing)

To simulate a fresh user install:

```bash
adb shell am force-stop com.alpha3.launcher
adb uninstall com.alpha3.launcher
adb shell rm -rf /storage/emulated/0/Alpha3/
adb shell rm -rf /storage/emulated/0/Android/data/com.alpha3.launcher/
adb install -t app/build/outputs/apk/debug/app-debug.apk
```

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

---

## Device compatibility

- Designed for AYN Thor (dual-screen Android, 1240×1080 bottom touch screen)
- May work on other dual-screen Android devices
- Single-screen Android: untested, companion screen behavior unknown
- Requires a physical controller for gameplay (companion screen is touch-only)


