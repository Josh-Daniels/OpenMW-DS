# OpenMW-DS

A second-screen companion for OpenMW on the AYN Thor handheld. The game plays on the top screen, while the bottom screen
carries the character sheet, inventory, magic, journal and map. It also offers optional bottom-screen replacements for
the game's own menus (conversation, bartering, looting, alchemy, enchanting, spellmaking, level up, the world map and
more), several of which use both screens at once. There is full touch and controller support. 

The current build has an experimental option for Retroid Devices using a Dual Screen Add-On. In the launcher go to Settings > 
scroll to the bottom and choose "Retroid Dual Screen (Experimental)". It is experimental so use at your own risk. Known issues 
where touching the screen takes focus away from the main screen for controller. Tap the top screen again as a temp fix. Will 
be patched in next update.

This project is a fork of **[Alpha3](#credits)**, a multi-engine Android launcher that can handle OpenMW,
which is itself a fork of the **[openmw-android](#credits)** project, which builds on **[OpenMW](#credits)**, the open-source Morrowind engine.

If you don't have a Thor, or don't care about dual-screen Morrowind, you probably want **[Alpha3](#credits)** instead.

![Image of the HUD tab in game](outputs/OpenMW-DS%20Thumnail.jpg)
> Image is more blurry than in real life.

---
## Features

### The bottom screen

Five tabs, live-updating while you play.

- **HUD**: a live, player-centred minimap of the current cell, rendered by the engine itself and streamed across (the
  same system the in-game minimap uses). Around it sit health, magicka and fatigue, active effects, your equipped weapon
  and spell, a sneak indicator, the tracked quest, and a combat target's health.
- **Inventory**: the full item list with tap to equip, use or read, and long press for info, drop or favourite. Sortable
  and filterable by category, showing per-item weight, condition and enchantment charge.
- **Spells**: spells, powers, scrolls and enchanted items. Tap to select, long press for info, add to favourites, delete spell. Learned
  spells show their magicka cost, school and casting chance; scrolls and enchanted items show their charge. Every row
  summarises its first effect.
- **Stats**: the character sheet, where every row opens a detail popup. Attributes, skills, health, magicka and
  fatigue, race, class, birthsign, level, reputation, bounty, faction ranks and active effects.
- **Journal**: the in-game journal with quests, topics and dated entries, plus a few things the original doesn't have.
  Hide a quest you're done with (also useful for those vanilla Morrowind bugs), follow one so it shows on the HUD, and write your own notes into the journal, dated
  with the real in-game date and saved per playthrough. Topic names inside entry text are tappable links. It's also animated!

**Favourites**: up to four gear and four magic slots on the HUD (two of each by default) for fast weapon and spell
swapping without opening a menu.

### DS game menus

The game's own menus can be moved onto the bottom screen. Every menu ships set to Vanilla and you opt
in per menu, or use the `[All DS]` preset to switch the lot and `[All Native]` to put them all back.

> Conversation · Looting · Bartering · Persuasion · Repair · Travel · Level up · Spell buying · Training ·
> Spellmaking · Enchanting · Alchemy · Map · Rest / Wait · Crime alerts

Several use both screens at once. Bartering and looting put the two inventories on separate screens, while alchemy,
enchanting and spellmaking keep the live readout up top while you work on the bottom.

**The game's rules are untouched.** Every DS screen forwards to the engine's own window: the alchemy success roll and
its apparatus quality maths, the enchantment validations and self-enchant roll, spellmaking's order-dependent magicka
cost, the level-up commit and its Endurance-dependent health gain, the pickpocket catch roll, merchant-adjusted travel
prices. All of it still runs in OpenMW's own C++. The DS side handles presentation and input only, so it behaves exactly
as the original does, quirks included.

### The map

- **Both maps on screen at once**: the world map on the top screen and the local map on the bottom by default, with a
  **Swap Screens** button to exchange them.
- Independent pinch-zoom and pan per map, each remembering its own view when it changes screen.
- Fog of war, discovered locations, and door markers you can tap for their destination.
- **Map notes**: long press anywhere on the world map to drop a marker with your own text, then tap it to edit or
  delete. Notes save with your game.
- A **Centre** button to snap back to the player.

### Controls

- **Touch** throughout the bottom screen, and optionally on the top screen's own menus.
- **Full controller navigation of every DS screen**: D-pad to move, A to confirm, B to back out, shoulder buttons to
  cycle categories, R3 for an item's info, left stick for sliders. You never have to touch the screen if you'd rather
  not.
- An on-screen keyboard for anywhere the game asks you to type (character and class names, save names, potion and spell
  names), so you don't need a hardware keyboard.
- A built-in **DS UI Controls** reference page listing every non-obvious control, in Settings > Controls.
- Sensible controller defaults are already applied for the Thor, and can be changed back in the game's own settings.

### Display & comfort

- **Screen dimming**: both screens track the game's ambient light, so the bottom screen stops glaring at you in a dark
  cave. Three sliders control how dark it gets and how bright it stays, with a separate ceiling for night-time.
- **Game brightness**: optional floors on the game world's own lighting, covering an interior floor, an exterior night
  lift, and per-weather daytime floors, with **Normal / Bright / Blinding** presets. Normal is exact vanilla; the rest
  are there for playing outside on a sunny day.
- **Panel opacity** for the DS overlays drawn over the game view.
- **Game font**: the companion screens, the DS overlays and the launcher all render in the game's own typeface
  (MysticCards, OpenMW's open-source Morrowind lookalike). On by default, switchable off.
- **Interface sounds** with a volume slider, using the game's own UI sound effects.
- **Per-element top-screen HUD toggles**: hide any of the game's own HUD elements (vitals, equipped items, minimap,
  active effects, sneak indicator, target health, crosshair, controller hints) once the bottom screen is showing them.

### Settings

Reachable from the pause menu in game and from the title screen, organised into five categories:

| Category | What's in it |
|---|---|
| **Game Menus** | The Native/DS switch for each of the 15 menus above |
| **Top Display** | Game brightness, the game's own HUD elements, DS overlay opacity |
| **Bottom Display** | Interface sounds, inventory options, HUD favourite slot counts |
| **Controls** | The DS UI Controls reference, touch input, game cursor |
| **Developer Tools** | Screen dimming, the console, and test tools (god mode, noclip, stat and gold cheats, level-up and weather triggers) |

Three presets sit above them: **All DS**, **Custom** and **All Native**, so you can try the whole DS layout in one tap
and go back just as easily.

### The launcher

- **Mod load order**: drag to reorder, with a checkbox per plugin to enable or disable it without removing it.
- **Add and remove mod folders**, with a confirmation that names every plugin a removal would take with it.
- **Import your load order from Alpha3**, if you were using it before.
- **Copy your saves and settings across from Alpha3.**
- **In-app updates**: the launcher checks GitHub for new releases, and downloads and installs them for you. It will only download if you say so though.

---
## AI Disclosure

This project was built as a personal project for me to learn how to use AI tools. I am an ICT student and avoid the use
of AI while at Uni, but I will need to know how to use it for employment in the future. Claude.ai was used as well as
Claude Code in Android Studio. Both were used extensively.

The code here was written by AI under my direction. The architecture, the design decisions, the trade-offs and all of
the testing were mine. A fair amount of what's in this app is the result of rejecting a first attempt and making decisions on more appropriate methods.

If you are opposed to AI usage in programming then I strongly advise you avoid this project.

---
## Requirements

- AYN Thor handheld.
  - https://www.ayntec.com/products/ayn-thor
- Morrowind data files (you must own a copy of Morrowind)
  - https://store.steampowered.com/app/22320/The_Elder_Scrolls_III_Morrowind_Game_of_the_Year_Edition/ or
  - https://www.gog.com/en/game/the_elder_scrolls_iii_morrowind_goty_edition
- All-files storage access. Android will ask for this on first launch. The app browses for your Morrowind folder with
  its own file browser rather than Android's document picker, so it needs the broader permission. Your Morrowind folder
  can live on the SD card or internal memory.

---
## Installation

You will first need a copy of Morrowind on the device. Best downloaded from a PC then transferred to the Thor. You will
need the entire Morrowind folder, not just the Data Files folder.

> **New to OpenMW-DS?** Skip to [First time install](#first-time-install).

### Upgrading from 0.8.6 or newer
- Open the launcher and use the update prompt. It downloads and installs the new version for you.
- Or download the APK from the Releases page and install it manually, as below.

### Upgrading from 0.7.5 to 0.8.5
- Download the APK from the Releases page and install the app.
- Check that your esm files and mods are listed on the home page. If they aren't, follow the first time install guide below.
- If you have Alpha3 saves newer than your OpenMW-DS saves, you can copy them over in Settings.
- Press Play Game to play game...

### Upgrading from 0.7.0 or older, or from Alpha3
These versions used a different app ID, so this installs as a **new app** alongside your old one rather than updating it.
Follow [First time install](#first-time-install). On first launch the app will detect your old install and offer to copy
your saves and settings across.

### First time install
- Download the APK from the Releases page.
- Tap the downloaded APK to install the app. Follow the prompt to allow installs from unknown sources if asked.
- Launch the app.
- Select your Morrowind folder when prompted.
  - It will say "Morrowind.esm Not Found", tap yes.
  - Navigate to your Morrowind folder **(NOT DATA FILES)**, then tap "Use this Folder".
  - Your Data Files folder will also be selected automatically.
- If you have .omwscript mods kept outside the Data Files folder, click the "Manage Folders" link on the top right to select the location of your other folders.
- Check the home page to see if your esm files are in the load order. It can take a moment to appear after selecting a
  folder. If the list is still empty, try selecting the Data Files folder again.
- You can now run the game by pressing the Play Game button.

---
## Setup Guide

OpenMW-DS is its own app (you can have Alpha3 installed separately if you like, this no longer replaces it). It's a fork
of Alpha3, so it inherits Alpha3's launcher, game-file management and device configuration; but it runs as a standalone
app, with its own files, not on top of Alpha3.

> I did not create Alpha3 [Credits](#credits).

### Settings
- The **Settings icon** in the button row on the home screen opens the launcher settings, including the Settings.cfg
  editor. (If you've switched to the Alpha3 launcher layout, it's the left-side menu there instead.)
- Have a look through Settings.cfg to enable or disable anything you like.
- I have already enabled controller support by default.
- I have disabled haptics by default because I found it to vibrate at random even when standing still.
- I have changed some in-game controller inputs for a better in-game experience. You can change them back in game.
- To disable "toggle sneak" go to the in-game Settings > Scripts > OpenMW Controls > Toggle Sneak.
- I haven't changed any graphical settings from default, but I like to turn on shadows (runs fine on Thor Max).
- The bottom screen has its own settings, separate from these. Press Start in game, or use the DS Settings button on the
  title screen.

### Save Location
- If you want to use your existing OpenMW saves, the save location is:
- /storage/emulated/0/OpenMW-DS/saves. (console's internal storage/OpenMW-DS/saves)
- I recommend using LocalSend to quickly transfer files between Mac/PC/Thor over Wi-Fi.

### Mods
Fully compatible with **[Tamriel Rebuilt](https://www.tamriel-rebuilt.org/)**, including Poison Song! Just follow the manual install instructions found **[here](https://www.tamriel-rebuilt.org/content/how-install-tamriel-rebuilt)** to get it working, and make sure your load order is correct. There will be a 30-second delay when loading into the game. 

Most mods should work. Item icons are pulled through the engine's own virtual file system, so any icon the game can draw the
companion screen can draw too, including icons that came from a mod. The minimap is the engine's own map render streamed
across rather than a reimplementation, so it covers modded landmasses the same way it covers the base game. The launcher
handles multiple mod folders, per-plugin enable/disable and load order.

I have tested a number of mods that change animations, add new items, and new landmasses and all have been fine. Mods that edit the actual Morrowind.esm file are often not compatible with OpenMW, so I would suggest a fresh download of Morrowind if you have an issue with a Global Time error.

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
- **MysticCards** (OpenMW Team, derived from **[Pelagiad](https://isaskar.github.io/Pelagiad/)** by Isak Larborn) - the
  typeface used for the companion screens, the DS overlays and the launcher. Bundled with OpenMW and licensed under the
  [SIL Open Font License 1.1](https://scripts.sil.org/OFL); the full licence ships with the app at
  `resources/vfs/fonts/MysticCardsFontLicense.txt`. Note this is *not* Morrowind's own font - that stays in your Data
  Files and is never copied or redistributed. The bundled **DemonicLetters** (also OFL) and **DejaVu LGC Sans Mono**
  fonts come from OpenMW on the same terms, with their licences alongside.
- **[Material Symbols](https://fonts.google.com/icons)** (Google) - the interface icons used in the launcher, licensed
  under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). The full licence text ships with the app
  in `3rdparty-licenses.txt`.

---
# Building from Source (For Developers)

## Prerequisites

- **Android Studio** - (latest stable recommended)
- **NDK 29.0.14206865** - install via Android Studio SDK Manager
- **CMake 3.22.1** - install via Android Studio SDK Manager
- **JDK 21** - bundled with Android Studio, or install separately
- **ADB** - included with Android SDK platform-tools
- **macOS note:** install `gnu-sed` via Homebrew (`brew install gnu-sed`) - the build scripts require it

## First build (fresh clone)

First-time builds compile OpenMW's full C++ source from scratch. This takes **10 to 90 minutes**. M4 Max MacBook Pro
takes approximately 15 minutes, for reference. Subsequent builds are much faster since only changed files are
recompiled.

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
adb push app/src/main/assets/companion/scripts/companion/. \
  /storage/emulated/0/OpenMW-DS/OpenMW/Mods/companion/scripts/companion/
```

> Note the trailing `/.`, which pushes the directory's *contents*. Without it, `adb push` nests the source directory
> inside the destination once the destination exists, and the game keeps loading the old file.

## Changing the engine (C++ patches)

Engine changes live as unified-diff patches in `app/src/main/cpp/openmw/patches/openmw/`, applied to a persistent
checkout that keeps every previously-applied patch as uncommitted working-tree changes.

**A plain `assembleDebug` after editing a patch is silently a no-op.** The whole patch chain is gated behind a single
stamp file, and the build still reports success while packaging the old binary. Deleting the stamp re-runs the *entire*
chain, so any file you have not reset to pristine gets its hunks applied twice (`error: redefinition of ...`).

The safe sequence is to reset the whole checkout first:

```bash
SRC=$(find app/.cxx/Debug -type d -path "*openmw-prefix/src/openmw" -not -path "*-stamp*" | head -1)
git -C "$SRC" checkout HEAD -- .
find "$SRC" \( -name '*.rej' -o -name '*.orig' \) -delete
find app/.cxx -path "*/openmw-prefix/src/openmw-stamp/openmw-patch" -delete
./gradlew assembleDebug
```

Then verify the change landed in the *patched source*, not just that the build succeeded:

```bash
grep -n "<your new symbol>" "$SRC/<path/to/file>"
```

## Native library pinning

Five native libraries (`libbsatool.so`, `libSDL2.so`, `libng_gl4es.so`, `libcollada-dom2.5-dp.so`, `libopenal.so`) are
pinned to known-working versions in `app/src/main/backup-libs/`. The build system automatically restores these after
CMake builds them, since fresh macOS builds of these specific libraries produce binaries incompatible with the device.
This happens automatically, so you don't need to do anything manually.

If you are building inside the project's Docker/Fedora environment (see `Dockerfile`), the pinning is less necessary as
the Linux toolchain produces compatible binaries.

## Release builds

`assembleRelease` **cleans its own output directory**, so anything you copy into `app/build/outputs/apk/release/` is
deleted by the next release build. Stage any build you want to keep somewhere outside that directory.

The release variant also uses a separate native checkout from debug (`app/.cxx/RelWithDebInfo/...`), which only advances
when you actually run `assembleRelease`. If it has sat unbuilt while the patch chain changed, reset it the same way as
the debug checkout above before building.

## Pre-install wipe (for clean testing)

To simulate a fresh user install:

```bash
adb shell am force-stop org.openmw.ds
adb uninstall org.openmw.ds
adb shell rm -rf /storage/emulated/0/OpenMW-DS/
adb shell rm -rf /storage/emulated/0/Android/data/org.openmw.ds/
adb install -t app/build/outputs/apk/debug/app-debug.apk
```

Back up your saves first. `/storage/emulated/0/OpenMW-DS/saves/` goes with the folder.
