#if defined(stderr) && (__ANDROID_API__ < 23)
int stderr = 0; // Hack: fix linker error
#endif

#include "SDL_main.h"
#include "engine.hpp"
#include "mwbase/environment.hpp"
#include "mwbase/journal.hpp"
#include "mwbase/luamanager.hpp"
#include "mwbase/windowmanager.hpp"
#include "mwbase/world.hpp"
#include "mwdialogue/quest.hpp"
#include "mwdialogue/topic.hpp"
#include "mwsound/soundbridge.hpp"
#include "mwworld/ptr.hpp"
#include <components/esm/refid.hpp>
#include <SDL_events.h>
#include <SDL_gamecontroller.h>
#include <SDL_hints.h>
#include <SDL_mouse.h>
#include <components/vfs/pathutil.hpp>
#include <components/settings/settings.hpp>
#include <components/settings/values.hpp>
#include <components/debug/debugging.hpp>
#include <components/resource/resourcesystem.hpp>
#include <components/resource/imagemanager.hpp>

#include <osg/GraphicsContext>
#include <osg/GL>
#include <osg/Image>
#include <osg/OperationThread>
#include <osgDB/WriteFile>

#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

/*******************************************************************************
 * Functions called by JNI
 *******************************************************************************/

#include <jni.h>

// --- In-process companion log sink -------------------------------------------
// Intercepts COMPANION_* lines written by the Lua mod and delivers them to
// Kotlin without touching openmw.log at all.

static JavaVM*   g_companionVm     = nullptr;
static jclass    g_companionClass  = nullptr;
static jmethodID g_companionMethod = nullptr;
static jmethodID g_mapTextureMethod = nullptr;
// DS map delivery, resolved alongside the others in installCompanionSink. All three stay nullptr
// when no second screen exists, and every delivery function checks its own before allocating.
static jmethodID g_globalMapBaseMethod = nullptr;
static jmethodID g_globalMapOverlayMethod = nullptr;
static jmethodID g_mapFogMethod = nullptr;
static jmethodID g_hudVisibilityMethod = nullptr;

// Mirrors OpenMW's in-game Hide UI state (mHudEnabled), updated on every toggle
// from companionDeliverHudVisibility(). Read by ControllerManager via the
// companionHudEnabled() bridge below to suppress the gamepad GUI cursor while
// Hide UI is active (the left thumbstick would otherwise re-summon it on every
// axis event). std::atomic: written on the engine thread, read on the input
// thread. See companion-hideui-gamepad-cursor.patch.
static std::atomic<bool> g_companionHudEnabled{ true };

// Mirrors the companion "Game cursor" option (UiPreferences "gameCursor"), pushed
// from Kotlin via setCompanionCursorEnabled(). Read by the engine through the
// companionCursorEnabled() bridge to suppress the top-screen SDL cursor (touch +
// both thumbsticks) while the option is off. Default true = cursor allowed, matching
// UiPreferences' shipped default; this initial value only covers the window before the
// Kotlin collector pushes the persisted one, and is also what a failed push falls back to.
// std::atomic: written on a JNI thread, read on the input/engine threads. See
// companion-gamecursor-suppress.patch.
static std::atomic<bool> g_companionCursorEnabled{ true };

// Mirrors the companion "Touch input" option (UiPreferences "touch_input"), pushed from Kotlin via
// setCompanionTouchClick(). Read by the SDL event pump (companion-touch-click.patch) through the
// companionTouchClick() bridge: when on AND a menu is open, a finger tap becomes an absolute mouse
// click at the tap point (touchscreen-style). Default true = on. std::atomic: written on a JNI
// thread, read on the input thread.
static std::atomic<bool> g_companionTouchClick{ true };

// Per-element native HUD visibility (companion "Vanilla HUD Elements" options), pushed from
// Kotlin. true = the native top-screen element is shown; false = hidden (companion bottom-screen
// version is the sole display). Default true. Read by the engine (companion-hud-elements.patch)
// via the companionHud*() bridges below. std::atomic: written on a JNI thread, read on the
// GUI/engine thread. "Equipped" gates BOTH the weapon and spell boxes.
static std::atomic<bool> g_companionHudHms{ true };
static std::atomic<bool> g_companionHudEquipped{ true };
static std::atomic<bool> g_companionHudMinimap{ true };
static std::atomic<bool> g_companionHudEffects{ true };
static std::atomic<bool> g_companionHudSneak{ true };
static std::atomic<bool> g_companionHudCrosshair{ true };
static std::atomic<bool> g_companionHudEnemy{ true }; // target/enemy health bar
static std::atomic<bool> g_companionHudControllerTooltips{ true }; // controller button-hint bar

// Per-element DS-mode suppression flags (companion "Game UI" options), pushed from Kotlin.
// true = the element is in DS mode -> the companion draws it on the bottom screen and the
// native top-screen window is SUPPRESSED; false = VANILLA -> the native window shows as normal.
// Read by the engine (companion-hide-*-on-hideui.patch and the Phase-2 suppression patches) via
// the companionDs*() bridges below. Default false = show native (safe until Kotlin pushes the
// persisted value at startup). std::atomic: written on a JNI thread, read on the GUI/engine thread.
static std::atomic<bool> g_companionDsConversation{ false };
static std::atomic<bool> g_companionDsLooting{ false };
static std::atomic<bool> g_companionDsBarter{ false };
// Phase 2 elements. These currently have no companion (DS) overlay yet — their Kotlin GameUiElement
// is "pending" (locked to VANILLA), so Kotlin always pushes false and the native suppression stays
// dormant until an overlay lands and the element is un-pended. Default false = show native.
static std::atomic<bool> g_companionDsRepair{ false };      // GM_MerchantRepair
static std::atomic<bool> g_companionDsLevelUp{ false };     // GM_Levelup
static std::atomic<bool> g_companionDsSpellmaking{ false }; // GM_SpellCreation
static std::atomic<bool> g_companionDsEnchanting{ false };  // GM_Enchanting
static std::atomic<bool> g_companionDsAlchemy{ false };     // GM_Alchemy
// DS MAP. Two flags, ANDed by companionDsMapActive(), and both are required:
//   g_companionDsMap    -- the "game_ui_map" element is DS (the player's preference).
//   g_companionMapMounted -- the DS map overlay is actually on screen right now.
// The mount flag is what keeps this off the combined GM_Inventory view and the pinned HUD map:
// the DS map only mounts from the app's own CMP:openmap path, so both of those see false. It is
// deliberately NOT a one-shot armed at command time -- that open can fail to happen at all
// (char-gen suppression, a close toggle), and a stale one-shot would then suppress a later map
// open that came from a vanilla path.
static std::atomic<bool> g_companionDsMap{ false };
static std::atomic<bool> g_companionMapMounted{ false };
// Has GM_Inventory actually been observed open since the mount flag was raised? Gates the
// self-clear above, which would otherwise fire in the window between raising the flag and Lua's
// deferred AddUiMode creating the mode.
static std::atomic<bool> g_companionMapModeSeen{ false };
static std::atomic<bool> g_companionDsRestWait{ false };    // GM_Rest
static std::atomic<bool> g_companionDsCrimeAlerts{ false }; // crime "reported" message: DS toast vs native
static std::atomic<bool> g_companionDsSpellBuying{ false }; // GM_SpellBuying
static std::atomic<bool> g_companionDsTraining{ false };    // GM_Training
// Travel HAS a companion (DS) overlay (TravelOverlay + companion-travel-export /
// companion-hide-travel-on-dsmode patches), so its Kotlin GameUiElement is non-pending (default DS):
// the native GM_Travel window is suppressed and the bottom screen is the sole surface.
static std::atomic<bool> g_companionDsTravel{ false };      // GM_Travel

// True while any DS overlay (looting/barter/dialogue/travel/repair/rest/…) owns controller
// navigation, pushed from Kotlin via setCompanionNavActive() whenever such an overlay becomes
// visible/hidden. Read by ControllerManager (companion-controller-nav.patch) through the
// companionNavActive() bridge: while true, the controller D-pad/A/X/R1/L2/R2/left-stick are
// intercepted in GUI mode and re-emitted as COMPANION_NAV_* log lines for the bottom-screen
// Compose UI instead of driving the hidden native window. Default false = vanilla controller
// behaviour. std::atomic: written on a JNI thread, read on the input thread.
static std::atomic<bool> g_companionNavActive{ false };

// True while a bottom-screen quantity selector is open (pushed from Kotlin). Read by
// ControllerManager (companion-controller-nav.patch): while set, the controller B button is
// intercepted as a CANCEL of just the selector (COMPANION_NAV_CANCEL) instead of closing the
// whole overlay. std::atomic: written on a JNI thread, read on the input thread.
static std::atomic<bool> g_companionQtySelectorOpen{ false };

// Set true from Kotlin (companionResetAxes()) when a DS overlay closes (companionNavActive
// true->false). Consumed once by ControllerManager::update() (companion-controller-nav.patch, FIX 1)
// which then injects a neutral value-0 event for both sticks, so a stick-release that was swallowed
// while the menu was open doesn't leave the player moving/looking after the menu closes.
// std::atomic: written on a JNI thread, exchanged(false) on the input thread.
static std::atomic<bool> g_companionResetAxes{ false };

// One-shot request to make the top-screen map the ACTIVE (on-screen) controller window after a
// CMP:openmap. The Lua AddUiMode that opens the map is DEFERRED, so GM_Inventory is not open yet
// when CMP:openmap is drained -- set this here and let the per-frame poll in drainCompanionCommands
// consume it once the mode is confirmed open (companionTryActivateMap()). Without it, the vanilla
// decrement-then-cycle mode-entry logic skips setActiveControllerWindow when the last native tab was
// Inventory, leaving the map open-but-parked-off-screen. std::atomic: written/read on the engine
// thread only (drainCompanionCommands), atomic for consistency with the sibling flags.
static std::atomic<bool> g_companionPendingMapActive{ false };

// Pending value for the engine's own `Shaders/minimum interior brightness` (the vanilla interior
// ambient floor), pushed from the DS options slider. NEGATIVE = nothing pending.
//
// It cannot be applied straight from the JNI setter: that runs on the Android UI thread, while
// Settings::Manager and RenderingManager::configureAmbient are engine-thread state. So the setter
// only parks the value here and the per-frame poll in drainCompanionCommands (which IS the engine
// main thread) applies it -- the same deferral the map-activation flag above uses.
static std::atomic<float> g_companionPendingInteriorBrightness{ -1.f };

// Applies a pending interior-brightness change on the ENGINE thread. Mirrors what
// MWGui::SettingsWindow::apply() does for this setting: write it, then hand the changed key to the
// world so RenderingManager re-runs configureAmbient for the current cell and the player sees it
// immediately instead of on the next cell load.
static void companionApplyPendingInteriorBrightness()
{
    const float v = g_companionPendingInteriorBrightness.exchange(-1.f, std::memory_order_relaxed);
    if (v < 0.f)
        return;

    Settings::shaders().mMinimumInteriorBrightness.set(v);

    // Consume ONLY our own key. resetPendingChanges() with no argument clears the whole pending
    // set, which would swallow an unrelated change some other system had queued and not yet seen.
    const Settings::CategorySettingVector ours{ { "Shaders", "minimum interior brightness" } };
    MWBase::Environment::get().getWorld()->processChangedSettings(ours);
    Settings::Manager::resetPendingChanges(ours);
}

// --- Companion command queue -------------------------------------------------
// JNI thread pushes commands here; engine thread drains via drainCompanionCommands().
// g_luaManagerPtr is set once, when the first COMPANION_STATS line arrives,
// guaranteeing Lua is fully initialized before we ever call handleConsoleCommand.

static std::deque<std::string>         g_commandQueue;
static std::mutex                      g_commandMutex;
static std::atomic<MWBase::LuaManager*> g_luaManagerPtr{nullptr};

// Companion dialogue selection bridges (defined in mwgui/dialogue.cpp). Safe to
// call here because drainCompanionCommands() runs on the engine main thread.
extern "C" void companionDialogueSelectEntry(const char* entry);
extern "C" void companionDialogueGoodbye();
extern "C" void companionDialogueChoice(int id);
extern "C" void companionPersuade(int type);
// Bottom-screen barter (tradewindow.cpp). Items matched by serialized RefId.
extern "C" void companionBarterBorrow(const char* side, const char* refId, int count);
extern "C" void companionBarterReturn(const char* side, const char* refId, int count);
extern "C" void companionBarterSetGold(int extra);
extern "C" void companionBarterOffer();
extern "C" void companionBarterCancel();
// Bottom-screen LEVEL UP (levelupdialog.cpp). The pick takes the attribute's serialized ID rather
// than an ordinal so the bottom screen's grid order (core.stats.Attribute.records) cannot silently
// desync from the native window's (the ESM attribute store). Both bridges forward to the window's
// OWN handlers, so selection semantics, the coin-count gate and the entire commit stay native.
extern "C" void companionLevelupPick(const char* attrId);
extern "C" void companionLevelupOk();
// Bottom-screen ALCHEMY (alchemywindow.cpp). Apparatus and ingredients are addressed by SERIALIZED
// RefId, never by a list ordinal: the browse list is a SortFilterItemModel that reorders as stacks
// are consumed, so an ordinal would silently target the wrong item after any mutation (unlike
// travel/training, whose lists are stable because the sim is paused). Every bridge forwards to the
// window's OWN handlers, so the combination rule, the ready-status order, countPotionsToBrew(), the
// per-potion success roll and the ingredient consumption all stay native.
extern "C" void companionAlchemySetApparatus(int slot, const char* refId);
extern "C" void companionAlchemyClearApparatus(int slot);
extern "C" void companionAlchemyAddIngredient(const char* refId);
extern "C" void companionAlchemyClearIngredient(int slot);
extern "C" void companionAlchemySetName(const char* text);
extern "C" void companionAlchemyCreate(int count);
extern "C" void companionAlchemyCancel();
// DS MAP (mapwindow.cpp). companionMapExportAll pushes the WHOLE map state in one go -- global
// base + overlay, per-segment fog, notes and the clustered discovered locations. A full push rather
// than live deltas is correct because GM_Inventory is a GUI mode: the sim is paused for as long as
// the DS map is up, so none of it can change underneath. That is also why nothing hooks
// GlobalMap::copyResult -- with no live overlay delta there is no camera-cleanup ordering to get
// wrong. Notes are addressed by their index in the last export; every mutation re-exports.
extern "C" void companionMapExportAll();
extern "C" void companionMapUpdateVisible();
// True while GM_Inventory is the active mode (the mode the companion map view runs in).
extern "C" bool companionMapModeOpen();
extern "C" void companionMapAddNote(float worldX, float worldY, bool exterior, const char* note);
extern "C" void companionMapEditNote(int index, const char* note);
extern "C" void companionMapDeleteNote(int index);
// DS ENCHANTING (enchantingdialog.cpp). The item and the soul gem are addressed by SERIALIZED
// RefId — their pickers browse a container store whose order is not stable — while EFFECTS are
// addressed by INDEX, which is safe because mEffects is an ordered vector the player mutates
// directly and GM_Enchanting pauses the sim. Every bridge forwards to the window's OWN handlers, so
// the cast-style state machine, the accumulating cost, the capacity check, the price/charge/chance
// formulas, the seven Buy validations (including the stolen-item confiscation) and the self-enchant
// roll that consumes the soul gem whatever the outcome all stay native.
extern "C" void companionEnchantSelectItem(const char* refId);
extern "C" void companionEnchantClearItem();
extern "C" void companionEnchantSelectSoul(const char* refId);
extern "C" void companionEnchantClearSoul();
extern "C" void companionEnchantSetName(const char* text);
extern "C" void companionEnchantEffectAdd(const char* effectId);
extern "C" void companionEnchantEffectArg(const char* effectId, const char* argId, bool isSkill);
extern "C" void companionEnchantEffectEdit(int index);
extern "C" void companionEnchantEffectSet(int index, int range, int magMin, int magMax, int duration, int area);
extern "C" void companionEnchantEffectDelete(int index);
extern "C" void companionEnchantEffectOk();
extern "C" void companionEnchantEffectCancel();
extern "C" void companionEnchantCastTypeNext();
extern "C" void companionEnchantBuy();
extern "C" void companionEnchantCancel();
// Spellmaking (companion-enchanting-export.patch, spellcreationdialog.cpp). The effect-editing half
// routes at the SAME EffectEditorBase methods enchanting uses -- there is no second implementation
// of the editor, only a second window pointer to drive it through.
extern "C" void companionSpellmakingSetName(const char* text);
extern "C" void companionSpellmakingBuy();
extern "C" void companionSpellmakingCancel();
extern "C" void companionSpellmakingEffectAdd(const char* effectId);
extern "C" void companionSpellmakingEffectArg(const char* effectId, const char* argId, bool isSkill);
extern "C" void companionSpellmakingEffectEdit(int index);
extern "C" void companionSpellmakingEffectSet(int index, int range, int magMin, int magMax, int duration, int area);
extern "C" void companionSpellmakingEffectDelete(int index);
extern "C" void companionSpellmakingEffectOk();
extern "C" void companionSpellmakingEffectCancel();
// Bottom-screen merchant repair (merchantrepair.cpp). Items addressed by ordinal index.
extern "C" void companionRepairItem(int index);
extern "C" void companionRepairAll();
extern "C" void companionRepairCancel();
// Bottom-screen rest/wait (waitdialog.cpp). hours from the bottom-screen slider.
extern "C" void companionSleep(int hours);
extern "C" void companionSleepCancel();
// Bottom-screen travel (travelwindow.cpp). Destinations addressed by ordinal index.
extern "C" void companionTravelGo(int index);
extern "C" void companionTravelCancel();
// Bottom-screen training (trainingwindow.cpp). Skills addressed by ordinal index in the best-3 list.
extern "C" void companionDeleteSpell(const char* idText);
extern "C" void companionTrainSkill(int index);
extern "C" void companionTrainingCancel();
// Bottom-screen spell buying (spellbuyingwindow.cpp). Spells addressed by ordinal index.
extern "C" void companionBuySpell(int index);
extern "C" void companionSpellBuyingCancel();
// Bottom-screen text input (windowmanagerimp.cpp). companionSetFocusedText writes the confirmed
// text into the focused MyGUI EditBox (name / class / save-name entry) then injects Return to
// accept/advance the modal dialog; companionCancelTextInput injects Escape to back out (discard).
// Both close the dialog, which emits COMPANION_TEXT_INPUT_CLOSED so the bottom panel dismisses.
// (Merely clearing key focus does not work — the modal re-grabs a cleared field the next frame.)
extern "C" void companionSetFocusedText(const char* utf8);
extern "C" void companionCancelTextInput();

// Forces the top-screen map window into its maximized (fullscreen) rect (windowmanagerimp.cpp).
// Called when the companion opens the map so it always shows fullscreen, regardless of the
// persisted [Windows] map maximized flag — OpenMW silently resets that flag to false on any
// map-window move/resize, so it can't be trusted. Idempotent (no-op when already maximized).
extern "C" void companionForceMapMaximized();

// Makes the top-screen map the ACTIVE (on-screen) controller window once GM_Inventory is open
// (windowmanagerimp.cpp). Returns true iff GM_Inventory is currently the active mode -- so the
// per-frame poll below can consume its one-shot g_companionPendingMapActive request the moment the
// deferred map-open actually lands. Only repositions the map when it's the companion map-only view
// and not already active (idempotent), so a stale request no-ops on a normal native inventory open.
extern "C" bool companionTryActivateMap();

// Exports the set of FINISHED (completed) quests as a streamed COMPANION block.
// Quest completion status is NOT exposed to Lua in this build (types.Player.journal
// carries only text entries — see the note in companion.lua), so it must be read
// from the C++ journal here. Triggered on demand by the CMP:questStatus command
// (sent by the Kotlin JournalPanel alongside CMP:journal), NOT per frame.
// Streamed one small line each (START/QUEST/END) to stay clear of the 4096-byte
// stdout-flush truncation that bites single long COMPANION_ lines.
// Quest ids use RefId::serializeText() so they match the questId the Lua journal
// export emits (mTopic.serializeText(), see mwlua/types/player.cpp).
static void exportFinishedQuests()
{
    MWBase::Journal* journal = MWBase::Environment::get().getJournal();
    if (!journal) return;

    const auto& quests = journal->getQuests();
    int finished = 0;
    for (const auto& it : quests)
        if (it.second.isFinished()) ++finished;

    Log(Debug::Info) << "COMPANION_JOURNAL_FINISHED_START:" << finished;
    for (const auto& it : quests)
    {
        if (!it.second.isFinished()) continue;
        Log(Debug::Info) << "COMPANION_JOURNAL_FINISHED_QUEST:" << it.second.getTopic().serializeText();
    }
    Log(Debug::Info) << "COMPANION_JOURNAL_FINISHED_END:" << finished;
}

// Replaces newlines/carriage returns with spaces so a response body always
// fits on a single COMPANION_ log line (same 4096-byte single-line constraint
// the other streamed exports respect).
static std::string flattenText(std::string_view in)
{
    std::string out(in);
    for (char& c : out)
        if (c == '\n' || c == '\r') c = ' ';
    return out;
}

// Exports the set of KNOWN dialogue topics (with every seen response entry) as a
// streamed COMPANION block. Known topics are not exposed to Lua in this build, so
// this reads the C++ journal's topic store directly — the same source the in-game
// journal "Topics" list uses (journalwindow.cpp). Triggered on demand by the
// CMP:refreshTopics command (sent by the Kotlin JournalPanel when the TOPICS tab
// is first opened), NOT per frame — topics change rarely and can be numerous, so
// on-demand keeps the log clean and matches the existing CMP:journal/questStatus
// pattern. Streamed one small line each (START/ENTRY/END) to stay clear of the
// 4096-byte stdout-flush truncation that bites single long COMPANION_ lines.
// Topics are sorted alphabetically by display name before emitting so the Kotlin
// side can just store them in received order.
static void exportTopics()
{
    MWBase::Journal* journal = MWBase::Environment::get().getJournal();
    if (!journal) return;

    const auto& topics = journal->getTopics();

    // getTopics() is keyed by RefId, not display name — collect pointers and sort
    // by mName so emission order is alphabetical.
    std::vector<const MWDialogue::Topic*> sorted;
    sorted.reserve(topics.size());
    for (const auto& it : topics)
    {
        if (it.second.size() == 0) continue; // only topics with at least one entry
        sorted.push_back(&it.second);
    }
    std::sort(sorted.begin(), sorted.end(),
              [](const MWDialogue::Topic* a, const MWDialogue::Topic* b) {
                  return a->getName() < b->getName();
              });

    Log(Debug::Info) << "COMPANION_TOPICS_START:" << sorted.size();
    for (const MWDialogue::Topic* topic : sorted)
    {
        Log(Debug::Info) << "COMPANION_TOPIC_START:" << topic->getName();
        for (auto it = topic->begin(); it != topic->end(); ++it)
        {
            // actorName may be empty — always emit the pipe so the parser sees it.
            Log(Debug::Info) << "COMPANION_TOPIC_ENTRY:" << it->mActorName << "|"
                             << flattenText(it->mText);
        }
        Log(Debug::Info) << "COMPANION_TOPIC_END";
    }
    Log(Debug::Info) << "COMPANION_TOPICS_END";
}

// Called from InputWrapper::capture() every frame on the engine thread.
void drainCompanionCommands()
{
    MWBase::LuaManager* lua = g_luaManagerPtr.load(std::memory_order_acquire);
    if (!lua) return;

    // Per-frame poll for the pending "make the map the active controller window" request set by
    // CMP:openmap below. The Lua AddUiMode that opens the map is deferred, so GM_Inventory is not
    // open yet when CMP:openmap is drained -- poll here (this runs BEFORE the queue-empty
    // early-return, so it fires on every frame, not just frames that carry a command) and act once
    // the mode is confirmed open. companionTryActivateMap() returns true as soon as GM_Inventory is
    // the active mode, at which point we consume the request; it only repositions the map when it's
    // the companion map-only view and not already active, so a stale request (close toggle /
    // char-gen-suppressed open) harmlessly no-ops on the next all-tabs native inventory open.
    if (g_companionPendingMapActive.load(std::memory_order_relaxed) && companionTryActivateMap())
        g_companionPendingMapActive.store(false, std::memory_order_relaxed);

    // Self-clear for the suppression flag, so an open that never happened cannot leave the native
    // map suppressed forever. Two steps on purpose: the flag is raised BEFORE the mode exists (see
    // CMP:openmap), so it may only be cleared once the mode has actually been SEEN open -- clearing
    // on "not in GM_Inventory" alone would fire in the gap between the two and undo itself.
    if (g_companionMapMounted.load())
    {
        if (companionMapModeOpen())
            g_companionMapModeSeen.store(true);
        else if (g_companionMapModeSeen.load())
        {
            g_companionMapModeSeen.store(false);
            g_companionMapMounted.store(false);
            companionMapUpdateVisible();
        }
    }

    // Same rationale: runs before the queue-empty early-return below, so it is a true per-frame
    // poll rather than something that only fires on frames carrying a command.
    companionApplyPendingInteriorBrightness();

    std::deque<std::string> pending;
    {
        std::lock_guard<std::mutex> lock(g_commandMutex);
        if (g_commandQueue.empty()) return;
        pending.swap(g_commandQueue);
    }

    for (auto& cmd : pending)
    {
        // Dialogue commands (CMPDLG:) are handled natively — Lua has no way to read the
        // filtered topic list or select a topic. Everything else goes to Lua as before.
        if (cmd.rfind("CMPDLG:topic:", 0) == 0)
        {
            std::string arg = cmd.substr(sizeof("CMPDLG:topic:") - 1);
            companionDialogueSelectEntry(arg.c_str());
        }
        else if (cmd.rfind("CMPDLG:service:", 0) == 0)
        {
            std::string arg = cmd.substr(sizeof("CMPDLG:service:") - 1);
            companionDialogueSelectEntry(arg.c_str());
        }
        else if (cmd.rfind("CMPDLG:choice:", 0) == 0)
        {
            const int id = std::atoi(cmd.c_str() + (sizeof("CMPDLG:choice:") - 1));
            companionDialogueChoice(id);
        }
        else if (cmd.rfind("CMPDLG:goodbye", 0) == 0)
        {
            companionDialogueGoodbye();
        }
        else if (cmd.rfind("CMPDLG:persuade:", 0) == 0)
        {
            // Persuasion is driven from the bottom-screen popup; the native modal is
            // never shown. type 0..5 = Admire/Intimidate/Taunt/Bribe10/Bribe100/Bribe1000.
            const int type = std::atoi(cmd.c_str() + (sizeof("CMPDLG:persuade:") - 1));
            companionPersuade(type);
        }
        else if (cmd.rfind("CMP:questStatus", 0) == 0)
        {
            // Quest completion is C++-only in this build; handle natively rather
            // than forwarding to Lua (which has no way to answer it).
            exportFinishedQuests();
        }
        else if (cmd.rfind("CMP:refreshTopics", 0) == 0)
        {
            // Known topics are not exposed to Lua; read them from the C++ journal.
            exportTopics();
        }
        // NOTE: there is deliberately no CMP:dev_* branch here. Every Developer Tools action is
        // handled in companion.lua; the one that was not (dev_resurrect, which needed
        // MechanicsManager::resurrect for want of a Lua binding) was removed Aug 11 2026 because
        // reviving required pausing the game and left the session broken afterwards.
        // Barter (CMP:barter_*) is driven natively — the merchant Ptr, the gold pool, the
        // mercantile-adjusted prices and the haggle result all live in the C++ TradeWindow,
        // none of which Lua can reach. See companion-barter-export.patch.
        else if (cmd.rfind("CMP:barter_borrow ", 0) == 0 || cmd.rfind("CMP:barter_return ", 0) == 0)
        {
            // arg = "<count>|<side>|<refId>". refId may contain spaces (it is the tail), so
            // split only the first two '|' fields off the front.
            const bool isBorrow = (cmd.rfind("CMP:barter_borrow ", 0) == 0);
            std::string arg = cmd.substr(
                (isBorrow ? sizeof("CMP:barter_borrow ") : sizeof("CMP:barter_return ")) - 1);
            const std::size_t p1 = arg.find('|');
            const std::size_t p2 = (p1 == std::string::npos) ? std::string::npos : arg.find('|', p1 + 1);
            if (p1 != std::string::npos && p2 != std::string::npos)
            {
                const int count = std::atoi(arg.substr(0, p1).c_str());
                const std::string side = arg.substr(p1 + 1, p2 - p1 - 1);
                const std::string refId = arg.substr(p2 + 1);
                if (isBorrow)
                    companionBarterBorrow(side.c_str(), refId.c_str(), count);
                else
                    companionBarterReturn(side.c_str(), refId.c_str(), count);
            }
        }
        else if (cmd.rfind("CMP:barter_gold ", 0) == 0)
        {
            const int extra = std::atoi(cmd.c_str() + (sizeof("CMP:barter_gold ") - 1));
            companionBarterSetGold(extra);
        }
        else if (cmd.rfind("CMP:barter_offer", 0) == 0)
        {
            companionBarterOffer();
        }
        else if (cmd.rfind("CMP:barter_cancel", 0) == 0)
        {
            companionBarterCancel();
        }
        // Merchant repair (CMP:repair_*) is driven natively — repair prices come from
        // MechanicsManager::getBarterOffer and the NPC gold pool lives in the C++
        // MerchantRepair window, neither of which Lua can reach. See companion-repair-export.patch.
        else if (cmd.rfind("CMP:repair_item ", 0) == 0)
        {
            const int index = std::atoi(cmd.c_str() + (sizeof("CMP:repair_item ") - 1));
            companionRepairItem(index);
        }
        else if (cmd.rfind("CMP:repair_all", 0) == 0)
        {
            companionRepairAll();
        }
        else if (cmd.rfind("CMP:repair_cancel", 0) == 0)
        {
            companionRepairCancel();
        }
        // Travel (CMP:travel_*) is driven natively — the merchant-adjusted price
        // (MechanicsManager::getBarterOffer), the follower-aware teleport (ActionTeleport), the gold
        // transfer and the time advance all live in the C++ TravelWindow, none of which Lua can
        // reach. See companion-travel-export.patch. Check _cancel before the space-arg _go form.
        else if (cmd.rfind("CMP:travel_cancel", 0) == 0)
        {
            companionTravelCancel();
        }
        else if (cmd.rfind("CMP:travel_go ", 0) == 0)
        {
            const int index = std::atoi(cmd.c_str() + (sizeof("CMP:travel_go ") - 1));
            companionTravelGo(index);
        }
        // Rest/wait (CMP:sleep*) is driven natively — the canRest flags, the fade + progress
        // time advance, sleep interruption and level-up all live in the C++ WaitDialog, none of
        // which Lua can reach (world.advanceTime does no healing/level-up). See
        // companion-restwait-export.patch. Check _cancel before the space-arg form.
        else if (cmd.rfind("CMP:sleep_cancel", 0) == 0)
        {
            companionSleepCancel();
        }
        else if (cmd.rfind("CMP:sleep ", 0) == 0)
        {
            const int hours = std::atoi(cmd.c_str() + (sizeof("CMP:sleep ") - 1));
            companionSleep(hours);
        }
        // Training (CMP:training_*) is driven natively — the best-3 skill selection, iTrainingMod
        // pricing + getBarterOffer, the skill/attribute caps, skillLevelUp and the timed fade/advance
        // all live in the C++ TrainingWindow, none reachable from Lua. See
        // companion-trainingwindow-open-signal.patch. Check _cancel before the colon-arg form.
        // Level up (CMP:levelup_*) is driven natively: the ordered commit in LevelupDialog --
        // level-progress reduction, clearing the per-attribute skill-increase counters, the
        // Endurance-dependent health gain and setLevel(+1) -- must not be reimplemented in Lua.
        // Check _ok before the colon-arg pick form.
        else if (cmd.rfind("CMP:levelup_ok", 0) == 0)
        {
            companionLevelupOk();
        }
        else if (cmd.rfind("CMP:levelup_pick:", 0) == 0)
        {
            companionLevelupPick(cmd.c_str() + (sizeof("CMP:levelup_pick:") - 1));
        }
        // Alchemy (CMP:alchemy_*) is driven natively — MWMechanics::Alchemy owns the shared-effect
        // combination rule and its slot ORDER, the session-sticky apparatus prefill (Alchemy::clear
        // deliberately does not clear mTools), countPotionsToBrew(), the validation order and the
        // per-potion success roll that consumes the ingredients whatever the outcome. None of that
        // is reachable from Lua and none of it is reimplemented. See companion-alchemy-export.patch.
        // _cancel is checked before the colon-arg _create form so the shared "CMP:alchemy_c" prefix
        // cannot mis-route.
        else if (cmd.rfind("CMP:alchemy_cancel", 0) == 0)
        {
            companionAlchemyCancel();
        }
        else if (cmd.rfind("CMP:alchemy_apparatus_set:", 0) == 0)
        {
            // arg = "<slot>|<refId>". The refId is the tail (item ids contain spaces), so split
            // only on the first '|'.
            std::string arg = cmd.substr(sizeof("CMP:alchemy_apparatus_set:") - 1);
            const std::size_t bar = arg.find('|');
            if (bar != std::string::npos)
            {
                const int slot = std::atoi(arg.substr(0, bar).c_str());
                companionAlchemySetApparatus(slot, arg.substr(bar + 1).c_str());
            }
        }
        else if (cmd.rfind("CMP:alchemy_apparatus_clear:", 0) == 0)
        {
            companionAlchemyClearApparatus(std::atoi(cmd.c_str() + (sizeof("CMP:alchemy_apparatus_clear:") - 1)));
        }
        else if (cmd.rfind("CMP:alchemy_ingredient_add:", 0) == 0)
        {
            companionAlchemyAddIngredient(cmd.c_str() + (sizeof("CMP:alchemy_ingredient_add:") - 1));
        }
        else if (cmd.rfind("CMP:alchemy_ingredient_clear:", 0) == 0)
        {
            companionAlchemyClearIngredient(std::atoi(cmd.c_str() + (sizeof("CMP:alchemy_ingredient_clear:") - 1)));
        }
        else if (cmd.rfind("CMP:alchemy_name:", 0) == 0)
        {
            // Raw tail — a potion name may contain spaces and ':'.
            std::string text = cmd.substr(sizeof("CMP:alchemy_name:") - 1);
            companionAlchemySetName(text.c_str());
        }
        else if (cmd.rfind("CMP:alchemy_create:", 0) == 0)
        {
            companionAlchemyCreate(std::atoi(cmd.c_str() + (sizeof("CMP:alchemy_create:") - 1)));
        }
        // Enchanting (CMP:enchant_*) is driven natively — MWMechanics::Enchanting owns the cast-style
        // state machine, the accumulating per-effect cost (and its deliberate precise/imprecise
        // split), the enchant-points capacity check, the price/charge/success-chance formulas and the
        // self-enchant roll; EffectEditorBase owns the effect list, the 8-effect cap and the
        // add-then-remove-on-cancel semantics. None of it is reachable from Lua and none of it is
        // reimplemented. See companion-enchanting-export.patch.
        //
        // ORDERING NOTE: the longer prefixes must be tested BEFORE their shorter relatives —
        // "CMP:enchant_cancel" would otherwise swallow nothing, but "CMP:enchant_effect_cancel"
        // shares the "CMP:enchant_" stem with everything here, and "CMP:enchant_item_clear" shares
        // "CMP:enchant_item_" with _item_select. Each branch below matches a full, distinct prefix,
        // and the two _c forms (_cancel vs _casttype_next) are distinct from the third character on.
        else if (cmd.rfind("CMP:enchant_item_select:", 0) == 0)
        {
            companionEnchantSelectItem(cmd.c_str() + (sizeof("CMP:enchant_item_select:") - 1));
        }
        else if (cmd.rfind("CMP:enchant_item_clear", 0) == 0)
        {
            companionEnchantClearItem();
        }
        else if (cmd.rfind("CMP:enchant_soul_select:", 0) == 0)
        {
            companionEnchantSelectSoul(cmd.c_str() + (sizeof("CMP:enchant_soul_select:") - 1));
        }
        else if (cmd.rfind("CMP:enchant_soul_clear", 0) == 0)
        {
            companionEnchantClearSoul();
        }
        else if (cmd.rfind("CMP:enchant_name:", 0) == 0)
        {
            // Raw tail — an enchantment name may contain spaces and ':'.
            std::string text = cmd.substr(sizeof("CMP:enchant_name:") - 1);
            companionEnchantSetName(text.c_str());
        }
        else if (cmd.rfind("CMP:enchant_effect_add:", 0) == 0)
        {
            companionEnchantEffectAdd(cmd.c_str() + (sizeof("CMP:enchant_effect_add:") - 1));
        }
        else if (cmd.rfind("CMP:enchant_effect_skill:", 0) == 0
            || cmd.rfind("CMP:enchant_effect_attribute:", 0) == 0)
        {
            // "<effectId>|<skillId or attributeId>". Both ids can contain spaces, so split on the
            // first '|' only — the effect id never contains one.
            const bool isSkill = cmd.rfind("CMP:enchant_effect_skill:", 0) == 0;
            const std::size_t head
                = isSkill ? sizeof("CMP:enchant_effect_skill:") - 1 : sizeof("CMP:enchant_effect_attribute:") - 1;
            std::string arg = cmd.substr(head);
            const std::size_t bar = arg.find('|');
            if (bar != std::string::npos)
                companionEnchantEffectArg(arg.substr(0, bar).c_str(), arg.substr(bar + 1).c_str(), isSkill);
        }
        else if (cmd.rfind("CMP:enchant_effect_edit:", 0) == 0)
        {
            companionEnchantEffectEdit(std::atoi(cmd.c_str() + (sizeof("CMP:enchant_effect_edit:") - 1)));
        }
        else if (cmd.rfind("CMP:enchant_effect_set:", 0) == 0)
        {
            // "<index>|<range>|<magMin>|<magMax>|<duration>|<area>" — six plain integers.
            std::string arg = cmd.substr(sizeof("CMP:enchant_effect_set:") - 1);
            int v[6] = { -1, 0, 1, 1, 1, 0 };
            std::size_t pos = 0;
            int n = 0;
            while (n < 6)
            {
                const std::size_t bar = arg.find('|', pos);
                v[n++] = std::atoi(arg.substr(pos, bar == std::string::npos ? std::string::npos : bar - pos).c_str());
                if (bar == std::string::npos)
                    break;
                pos = bar + 1;
            }
            if (n == 6)
                companionEnchantEffectSet(v[0], v[1], v[2], v[3], v[4], v[5]);
        }
        else if (cmd.rfind("CMP:enchant_effect_delete:", 0) == 0)
        {
            companionEnchantEffectDelete(std::atoi(cmd.c_str() + (sizeof("CMP:enchant_effect_delete:") - 1)));
        }
        else if (cmd.rfind("CMP:enchant_effect_ok", 0) == 0)
        {
            companionEnchantEffectOk();
        }
        else if (cmd.rfind("CMP:enchant_effect_cancel", 0) == 0)
        {
            companionEnchantEffectCancel();
        }
        else if (cmd.rfind("CMP:enchant_casttype_next", 0) == 0)
        {
            companionEnchantCastTypeNext();
        }
        else if (cmd.rfind("CMP:enchant_buy", 0) == 0)
        {
            companionEnchantBuy();
        }
        else if (cmd.rfind("CMP:enchant_cancel", 0) == 0)
        {
            companionEnchantCancel();
        }
        // Spellmaking (CMP:spellmaking_*) is driven natively for the same reason enchanting is: the
        // magicka-cost accumulation (whose Target x1.5 scales the RUNNING total, so cost depends on
        // effect ORDER), calcSpellBaseSuccessChance, the barter-adjusted price and the four Buy
        // validations all live in SpellCreationDialog, and the effect editor lives in the shared
        // EffectEditorBase. None of it is reachable from Lua and none of it is reimplemented.
        //
        // _buy routes at the REAL onBuyButtonClicked rather than validating here, because two of
        // those checks read the WIDGET CAPTIONS (cost == "0", parseInt(price) > gold) -- a DS-side
        // gate would be testing different numbers from the one that actually refuses.
        //
        // ORDERING NOTE: same rule as the enchanting block above -- longer prefixes first. The two
        // _c forms (_cancel vs nothing else) and _effect_cancel vs _cancel are the cases that matter.
        else if (cmd.rfind("CMP:spellmaking_name:", 0) == 0)
        {
            // Raw tail — a spell name may contain spaces and ':'.
            std::string text = cmd.substr(sizeof("CMP:spellmaking_name:") - 1);
            companionSpellmakingSetName(text.c_str());
        }
        else if (cmd.rfind("CMP:spellmaking_effect_add:", 0) == 0)
        {
            companionSpellmakingEffectAdd(cmd.c_str() + (sizeof("CMP:spellmaking_effect_add:") - 1));
        }
        else if (cmd.rfind("CMP:spellmaking_effect_skill:", 0) == 0
            || cmd.rfind("CMP:spellmaking_effect_attribute:", 0) == 0)
        {
            // "<effectId>|<skillId or attributeId>". Both ids can contain spaces, so split on the
            // first '|' only — the effect id never contains one.
            const bool isSkill = cmd.rfind("CMP:spellmaking_effect_skill:", 0) == 0;
            const std::size_t head = isSkill ? sizeof("CMP:spellmaking_effect_skill:") - 1
                                             : sizeof("CMP:spellmaking_effect_attribute:") - 1;
            std::string arg = cmd.substr(head);
            const std::size_t bar = arg.find('|');
            if (bar != std::string::npos)
                companionSpellmakingEffectArg(arg.substr(0, bar).c_str(), arg.substr(bar + 1).c_str(), isSkill);
        }
        else if (cmd.rfind("CMP:spellmaking_effect_edit:", 0) == 0)
        {
            companionSpellmakingEffectEdit(std::atoi(cmd.c_str() + (sizeof("CMP:spellmaking_effect_edit:") - 1)));
        }
        else if (cmd.rfind("CMP:spellmaking_effect_set:", 0) == 0)
        {
            // "<index>|<range>|<magMin>|<magMax>|<duration>|<area>" — six plain integers.
            std::string arg = cmd.substr(sizeof("CMP:spellmaking_effect_set:") - 1);
            int v[6] = { -1, 0, 1, 1, 1, 0 };
            std::size_t pos = 0;
            int n = 0;
            while (n < 6)
            {
                const std::size_t bar = arg.find('|', pos);
                v[n++] = std::atoi(arg.substr(pos, bar == std::string::npos ? std::string::npos : bar - pos).c_str());
                if (bar == std::string::npos)
                    break;
                pos = bar + 1;
            }
            if (n == 6)
                companionSpellmakingEffectSet(v[0], v[1], v[2], v[3], v[4], v[5]);
        }
        else if (cmd.rfind("CMP:spellmaking_effect_delete:", 0) == 0)
        {
            companionSpellmakingEffectDelete(std::atoi(cmd.c_str() + (sizeof("CMP:spellmaking_effect_delete:") - 1)));
        }
        else if (cmd.rfind("CMP:spellmaking_effect_ok", 0) == 0)
        {
            companionSpellmakingEffectOk();
        }
        else if (cmd.rfind("CMP:spellmaking_effect_cancel", 0) == 0)
        {
            companionSpellmakingEffectCancel();
        }
        else if (cmd.rfind("CMP:spellmaking_buy", 0) == 0)
        {
            companionSpellmakingBuy();
        }
        else if (cmd.rfind("CMP:spellmaking_cancel", 0) == 0)
        {
            companionSpellmakingCancel();
        }
        // DS map. map_mount is the MOUNT-SCOPED suppression flag (see g_companionMapMounted):
        // Kotlin sets it when the overlay mounts and clears it when it unmounts, and
        // updateVisible() reads the live value. Mounting also triggers the one full state push.
        else if (cmd.rfind("CMP:map_mount:", 0) == 0)
        {
            const bool on = cmd[sizeof("CMP:map_mount:") - 1] == '1';
            g_companionMapMounted.store(on);
            if (!on)
                g_companionMapModeSeen.store(false);
            // Re-assert window visibility immediately so the native map hides/shows on the same
            // frame the overlay appears/disappears, rather than waiting for the next GUI change.
            companionMapUpdateVisible();
            if (on)
                companionMapExportAll();
        }
        else if (cmd.rfind("CMP:map_refresh", 0) == 0)
        {
            companionMapExportAll();
        }
        else if (cmd.rfind("CMP:map_note_add:", 0) == 0)
        {
            // "<worldX>|<worldY>|<note>" -- the note is the raw tail (it may contain spaces and ':').
            // "<worldX>|<worldY>|<exterior 0/1>|<note>". The exterior flag is what lets a note
            // dropped on the WORLD map be filed against the exterior cell under the tap even while
            // the player is standing in an interior — see companionAddNote.
            std::string arg = cmd.substr(sizeof("CMP:map_note_add:") - 1);
            const std::size_t b1 = arg.find('|');
            const std::size_t b2 = (b1 == std::string::npos) ? std::string::npos : arg.find('|', b1 + 1);
            const std::size_t b3 = (b2 == std::string::npos) ? std::string::npos : arg.find('|', b2 + 1);
            if (b3 != std::string::npos)
                companionMapAddNote(static_cast<float>(std::atof(arg.substr(0, b1).c_str())),
                    static_cast<float>(std::atof(arg.substr(b1 + 1, b2 - b1 - 1).c_str())),
                    arg.substr(b2 + 1, b3 - b2 - 1) == "1",
                    arg.substr(b3 + 1).c_str());
        }
        else if (cmd.rfind("CMP:map_note_edit:", 0) == 0)
        {
            std::string arg = cmd.substr(sizeof("CMP:map_note_edit:") - 1);
            const std::size_t bar = arg.find('|');
            if (bar != std::string::npos)
                companionMapEditNote(std::atoi(arg.substr(0, bar).c_str()), arg.substr(bar + 1).c_str());
        }
        else if (cmd.rfind("CMP:map_note_delete:", 0) == 0)
        {
            companionMapDeleteNote(std::atoi(cmd.c_str() + (sizeof("CMP:map_note_delete:") - 1)));
        }
        else if (cmd.rfind("CMP:training_cancel", 0) == 0)
        {
            companionTrainingCancel();
        }
        // Delete a spell from the DS spells list. Native because Spells::remove's modifyBase
        // default is what makes the deletion survive a save/reload -- see companion-spell-delete
        // .patch. The tail after the prefix is the serialized RefId, raw (ids can contain spaces).
        else if (cmd.rfind("CMP:spell_delete:", 0) == 0)
        {
            companionDeleteSpell(cmd.c_str() + (sizeof("CMP:spell_delete:") - 1));
        }
        else if (cmd.rfind("CMP:training_train:", 0) == 0)
        {
            const int index = std::atoi(cmd.c_str() + (sizeof("CMP:training_train:") - 1));
            companionTrainSkill(index);
        }
        // Spell buying (CMP:spellbuying_*) is driven natively — the spell-cost formula, getBarterOffer
        // price, spells.add and the NPC gold pool all live in the C++ SpellBuyingWindow. See
        // companion-spellbuyingwindow-open-signal.patch. Check _cancel before the colon-arg form.
        else if (cmd.rfind("CMP:spellbuying_cancel", 0) == 0)
        {
            companionSpellBuyingCancel();
        }
        else if (cmd.rfind("CMP:spellbuying_buy:", 0) == 0)
        {
            const int index = std::atoi(cmd.c_str() + (sizeof("CMP:spellbuying_buy:") - 1));
            companionBuySpell(index);
        }
        // Text input (CMPTEXT:set:<text>) is driven natively — the focused MyGUI EditBox
        // lives in the C++ WindowManager and is not reachable from Lua. The text is the raw
        // tail after the prefix (may contain spaces and ':'), so take the substring verbatim.
        else if (cmd.rfind("CMPTEXT:set:", 0) == 0)
        {
            std::string text = cmd.substr(sizeof("CMPTEXT:set:") - 1);
            companionSetFocusedText(text.c_str());
        }
        else if (cmd.rfind("CMPTEXT:cancel", 0) == 0)
        {
            // Cancel/discard: inject Escape to back out of the modal without committing.
            companionCancelTextInput();
        }
        // Map open (CMP:openmap). Force the native map window fullscreen BEFORE forwarding to Lua,
        // which does the actual open/close mode toggle via AddUiMode. The persisted [Windows] map
        // maximized flag is unreliable (OpenMW resets it to false on any map-window move/resize),
        // so trackWindow() may have sized the window small at load; companionForceMapMaximized()
        // re-applies the maximized rect here (idempotent — no-op when already maximized). Doing it
        // on the engine thread this frame resizes the still-hidden window before Lua's later
        // AddUiMode makes it visible, so the map appears fullscreen with no shrink→grow flicker.
        // On a close toggle it is a harmless no-op (the window is about to hide). The prefix still
        // forwards to Lua (fall through into the generic handler below) for the mode toggle itself.
        else if (cmd.rfind("CMP:openmap", 0) == 0)
        {
            // ANTI-FLICKER: raise the DS-map suppression flag HERE, not when the Kotlin overlay
            // mounts. Kotlin only learns the map opened via COMPANION_MAPMODE, and its
            // CMP:map_mount:1 then has to come back the other way -- a full round trip during
            // which the native map window is already visible, which is the frame of vanilla map
            // that flickers. Setting it on this side of the trip means the pushGuiMode that Lua's
            // (deferred) AddUiMode triggers already sees the flag at its tail call to
            // updateVisible, so the window is never painted at all.
            //
            // This does NOT re-introduce the one-shot lifetime bug the mount-scoped flag exists to
            // avoid: the flag is still cleared by Kotlin on unmount, AND self-clears below once the
            // mode has been seen open and then closed -- which covers the case where the open never
            // happens at all (char-gen suppression), where no overlay ever mounts to clear it.
            if (g_companionDsMap.load())
                g_companionMapMounted.store(true);
            companionForceMapMaximized();
            // Request that the map become the active (on-screen) controller window once GM_Inventory
            // actually opens (deferred via the Lua AddUiMode this command triggers). The per-frame
            // poll at the top of this function consumes it; the map-only-signature guard there makes
            // it safe on a close toggle or a char-gen-suppressed open (a later native open clears it).
            g_companionPendingMapActive.store(true, std::memory_order_relaxed);
            lua->handleConsoleCommand("Companion", cmd, MWWorld::Ptr());
        }
        else
        {
            lua->handleConsoleCommand("Companion", cmd, MWWorld::Ptr());
        }
    }
}

// Shared companion->Kotlin forward path. Caches the LuaManager once Lua is provably
// running, then pushes the line straight into Kotlin (GameStateRepository) via the static
// onCompanionLine JNI method. Called from BOTH:
//   (1) the engine log listener below — companion data that still rides print()/Log(), and
//   (2) the core.companionPush Lua binding (see the corebindings companionPush patch) —
//       companion data that BYPASSES Log()/print() so it never touches openmw.log on disk.
// Runs on the engine thread in both cases. No-op until installCompanionSink has resolved
// g_companionMethod (guarded), so an early call is safely dropped.
static void companionForwardLine(std::string_view msg)
{
    // Cache LuaManager once Lua is provably running (first stats export). Keyed on
    // COMPANION_STATS, which is the first/most-frequent export and now arrives via
    // companionPush — but this helper serves both paths, so the caching is path-agnostic.
    if (!g_luaManagerPtr.load(std::memory_order_relaxed)
            && msg.find("COMPANION_STATS") != std::string_view::npos) {
        MWBase::LuaManager* lm = MWBase::Environment::get().getLuaManager();
        g_luaManagerPtr.store(lm, std::memory_order_release);
    }

    if (g_companionMethod == nullptr) return;
    if (msg.find("COMPANION_") == std::string_view::npos) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);
    jstring s = e->NewStringUTF(std::string(msg).c_str());
    if (s) {
        e->CallStaticVoidMethod(g_companionClass, g_companionMethod, s);
        e->DeleteLocalRef(s);
    }
}

// Lua-callable (core.companionPush) direct push. Declared extern "C" so
// apps/openmw/mwlua/corebindings.cpp can call it without including this translation unit
// (mirrors how dialogue.cpp exposes its companion bridges to this file). This is the whole
// point of the disk-logging elimination: companion export lines reach Kotlin WITHOUT going
// through Log()/print(), so they are never written to openmw.log.
extern "C" void companionPushLine(const char* s)
{
    if (s != nullptr)
        companionForwardLine(std::string_view(s));
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_installCompanionSink(JNIEnv* env, jobject /*thiz*/)
{
    env->GetJavaVM(&g_companionVm);

    jclass cls = env->FindClass("org/openmw/EngineActivity");
    g_companionClass  = static_cast<jclass>(env->NewGlobalRef(cls));
    g_companionMethod = env->GetStaticMethodID(g_companionClass, "onCompanionLine",
                                               "(Ljava/lang/String;)V");
    g_mapTextureMethod = env->GetStaticMethodID(g_companionClass, "onCompanionMapTexture",
                                                "(IIIIIFFFFF[B)V");
    g_globalMapBaseMethod = env->GetStaticMethodID(g_companionClass, "onCompanionGlobalMapBase",
                                                   "(IIIIII[B)V");
    g_globalMapOverlayMethod = env->GetStaticMethodID(g_companionClass, "onCompanionGlobalMapOverlay",
                                                      "(II[B)V");
    g_mapFogMethod = env->GetStaticMethodID(g_companionClass, "onCompanionMapFog",
                                            "(IIIII[B)V");
    g_hudVisibilityMethod = env->GetStaticMethodID(g_companionClass, "onHudVisibilityChanged",
                                                   "(Z)V");
    env->DeleteLocalRef(cls);

    // Companion lines that still ride print()/Log() (everything not yet migrated to
    // companionPush) reach Kotlin through this listener. Once every exporter uses
    // companionPush, this listener carries no COMPANION_ traffic — but it stays installed
    // (harmless; also the desktop DebugWindow path upstream relies on the same hook).
    Debug::setLogListener([](Debug::Level, std::string_view /*prefix*/, std::string_view msg) {
        companionForwardLine(msg);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_sendCompanionCommand(JNIEnv* env, jclass /*cls*/, jstring jcmd)
{
    const char* raw = env->GetStringUTFChars(jcmd, nullptr);
    {
        std::lock_guard<std::mutex> lock(g_commandMutex);
        g_commandQueue.push_back(std::string(raw));
    }
    env->ReleaseStringUTFChars(jcmd, raw);
}

// Called from the OSG render thread (MapCaptureCallback in localmap.cpp) once per
// cell entry with raw RGBA pixels from glReadPixels. Delivers to Kotlin via JNI.
// segX/segY are the map segment grid coordinates; isInterior distinguishes interior
// cells (where segments are 0,0 0,1 etc.) from exterior grid cells. boundsMinX/Y are
// the interior's mBounds min corner in world units (0.0f for exterior, unused there).
// angle/centerX/centerY are the interior map's rotation (radians) and rotation center
// (world units) — the companion applies rotatePoint(pos, center, angle) to the player
// position and adds angle to the arrow so the dot/arrow match the rotated interior
// texture (0.0f for exterior, unused there).
extern "C" void companionDeliverMapTexture(
    int width, int height, int segX, int segY, int isInterior, float boundsMinX, float boundsMinY,
    float angle, float centerX, float centerY, const unsigned char* rgba)
{
    // COMPANION_DEBUG: prefix, NOT the old "companion map:" wording. The disk-log filter in
    // DebugOutputBase::write matches msg.starts_with("COMPANION_") (companion-skip-disk-log.patch),
    // and lowercase-with-a-space missed it -- so every segment capture wrote a line to openmw.log.
    // Captures fire continuously while walking outdoors, which is precisely the unbounded disk
    // growth that filter exists to prevent.
    //
    // COMPANION_DEBUG is the established channel for a trace like this rather than a new prefix of
    // its own: it skips the disk write AND reaches logcat, because onRawLine Log.d()s any line
    // containing COMPANION_DEBUG. A bespoke prefix would skip disk but then match nothing on the
    // Kotlin side, i.e. cost the JNI hop and show the developer nothing.
    //
    // Same fields in the same order -- only the prefix changed.
    Log(Debug::Info) << "COMPANION_DEBUG: map w=" << width << " h=" << height
                      << " segX=" << segX << " segY=" << segY << " interior=" << isInterior
                      << " boundsMinX=" << boundsMinX << " boundsMinY=" << boundsMinY
                      << " angle=" << angle << " centerX=" << centerX << " centerY=" << centerY;

    if (!g_companionVm || !g_companionClass || !g_mapTextureMethod) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);

    const jsize size = static_cast<jsize>(width) * height * 4;
    jbyteArray arr = e->NewByteArray(size);
    if (!arr) return;

    e->SetByteArrayRegion(arr, 0, size, reinterpret_cast<const jbyte*>(rgba));
    e->CallStaticVoidMethod(g_companionClass, g_mapTextureMethod,
                            (jint)width, (jint)height, (jint)segX, (jint)segY,
                            (jint)isInterior, (jfloat)boundsMinX, (jfloat)boundsMinY,
                            (jfloat)angle, (jfloat)centerX, (jfloat)centerY, arr);
    if (e->ExceptionCheck()) {
        e->ExceptionDescribe();
        e->ExceptionClear();
    }
    e->DeleteLocalRef(arr);
}

// DS MAP binary payloads. These bypass the COMPANION_ text push because they are megabytes; each
// bails before any JNI allocation when the sink is not installed.
extern "C" void companionDeliverGlobalMapBase(
    int width, int height, int minX, int minY, int cellSize, int channels, const unsigned char* data)
{
    Log(Debug::Info) << "COMPANION_DEBUG: global map base " << width << "x" << height
                     << " minX=" << minX << " minY=" << minY << " cellSize=" << cellSize
                     << " ch=" << channels;
    if (!g_companionVm || !g_companionClass || !g_globalMapBaseMethod || !data) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);
    const jsize size = static_cast<jsize>(width) * height * channels;
    jbyteArray arr = e->NewByteArray(size);
    if (!arr) return;
    e->SetByteArrayRegion(arr, 0, size, reinterpret_cast<const jbyte*>(data));
    e->CallStaticVoidMethod(g_companionClass, g_globalMapBaseMethod, (jint)width, (jint)height,
        (jint)minX, (jint)minY, (jint)cellSize, (jint)channels, arr);
    if (e->ExceptionCheck()) { e->ExceptionDescribe(); e->ExceptionClear(); }
    e->DeleteLocalRef(arr);
}

extern "C" void companionDeliverGlobalMapOverlay(int width, int height, const unsigned char* rgba)
{
    if (!g_companionVm || !g_companionClass || !g_globalMapOverlayMethod || !rgba) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);
    const jsize size = static_cast<jsize>(width) * height * 4;
    jbyteArray arr = e->NewByteArray(size);
    if (!arr) return;
    e->SetByteArrayRegion(arr, 0, size, reinterpret_cast<const jbyte*>(rgba));
    e->CallStaticVoidMethod(g_companionClass, g_globalMapOverlayMethod, (jint)width, (jint)height, arr);
    if (e->ExceptionCheck()) { e->ExceptionDescribe(); e->ExceptionClear(); }
    e->DeleteLocalRef(arr);
}

// ALPHA ONLY -- 1024 bytes for a 32x32 segment rather than 4096. LocalMap::updatePlayer writes
// `val = alpha << 24` and never touches RGB, so the other three channels are provably zero.
extern "C" void companionDeliverMapFog(
    int segX, int segY, int isInterior, int width, int height, const unsigned char* alpha)
{
    if (!g_companionVm || !g_companionClass || !g_mapFogMethod || !alpha) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);
    const jsize size = static_cast<jsize>(width) * height;
    jbyteArray arr = e->NewByteArray(size);
    if (!arr) return;
    e->SetByteArrayRegion(arr, 0, size, reinterpret_cast<const jbyte*>(alpha));
    e->CallStaticVoidMethod(g_companionClass, g_mapFogMethod, (jint)segX, (jint)segY,
        (jint)isInterior, (jint)width, (jint)height, arr);
    if (e->ExceptionCheck()) { e->ExceptionDescribe(); e->ExceptionClear(); }
    e->DeleteLocalRef(arr);
}

// Called from WindowManager::setHudVisibility (windowmanagerimp.cpp) whenever the
// player toggles OpenMW's in-game Hide UI. Mirrors mHudEnabled onto the Alpha3
// second-screen overlay (touch controls / gear icon) via a static Kotlin method.
extern "C" void companionDeliverHudVisibility(bool visible)
{
    // Cache for ControllerManager's gamepad-cursor gate (companionHudEnabled()).
    g_companionHudEnabled.store(visible);

    if (!g_companionVm || !g_companionClass || !g_hudVisibilityMethod) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);
    e->CallStaticVoidMethod(g_companionClass, g_hudVisibilityMethod, (jboolean)visible);
    if (e->ExceptionCheck()) {
        e->ExceptionDescribe();
        e->ExceptionClear();
    }
}
// Read by ControllerManager (companion-hideui-gamepad-cursor.patch) to gate the
// gamepad GUI cursor: returns false while OpenMW's Hide UI is active so the left
// thumbstick can't re-enable the cursor over barter/map/service windows.
extern "C" bool companionHudEnabled()
{
    return g_companionHudEnabled.load();
}
// Read by the engine (companion-gamecursor-suppress.patch) to gate the top-screen
// SDL cursor: returns false while the companion "Game cursor" option is off, so
// touch and both thumbsticks can't summon the cursor over the game.
extern "C" bool companionCursorEnabled()
{
    return g_companionCursorEnabled.load();
}
// Read by ControllerManager (companion-controller-nav.patch) to gate controller interception:
// returns true while a DS overlay owns navigation, so the D-pad/A/X/R1/L2/R2/left-stick become
// COMPANION_NAV_* signals for the bottom screen instead of driving the hidden native window.
extern "C" bool companionNavActive()
{
    return g_companionNavActive.load();
}
// Read by ControllerManager (companion-controller-nav.patch): true while a quantity selector is up,
// so B cancels just the selector (COMPANION_NAV_CANCEL) rather than closing the whole overlay.
extern "C" bool companionQtySelectorOpen()
{
    return g_companionQtySelectorOpen.load();
}
// Read by ControllerManager::update() (companion-controller-nav.patch, FIX 1): atomic exchange(false),
// returns true exactly once per Kotlin companionResetAxes() request so the neutral stick-reset fires a
// single time when a DS overlay closes.
extern "C" bool companionConsumeResetAxes()
{
    return g_companionResetAxes.exchange(false);
}
// Pushed from Kotlin (EngineActivity) whenever the "Game cursor" option changes,
// and once at startup with the persisted value. Caches into g_companionCursorEnabled
// for companionCursorEnabled() above.
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionCursorEnabled(JNIEnv* /*env*/, jclass /*cls*/, jboolean enabled)
{
    g_companionCursorEnabled.store(enabled == JNI_TRUE);
}
// Pushed from Kotlin (EngineActivity) whenever a DS overlay becomes visible/hidden. Caches into
// g_companionNavActive for companionNavActive() above (controller-nav interception gate).
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionNavActive(JNIEnv* /*env*/, jclass /*cls*/, jboolean active)
{
    g_companionNavActive.store(active == JNI_TRUE);
}
// Pushed from Kotlin whenever a quantity selector opens/closes. Caches into
// g_companionQtySelectorOpen for companionQtySelectorOpen() above (B-cancel interception).
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionQtySelectorOpen(JNIEnv* /*env*/, jclass /*cls*/, jboolean open)
{
    g_companionQtySelectorOpen.store(open == JNI_TRUE);
}
// Called from Kotlin (EngineActivity) when a DS overlay closes (companionNavActive true->false). Flags
// g_companionResetAxes so ControllerManager::update() injects a neutral stick reset on the next frame
// (engine thread) — see companion-controller-nav.patch (FIX 1). One-shot; consumed via exchange(false).
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_companionResetAxes(JNIEnv* /*env*/, jclass /*cls*/)
{
    g_companionResetAxes.store(true);
}
// Read by the SDL event pump (companion-touch-click.patch): true while the "Touch input" option is
// on, so a finger tap in a menu becomes a direct absolute mouse click at the tap point.
extern "C" bool companionTouchClick()
{
    return g_companionTouchClick.load();
}
// Also read by that patch to gate on GUI mode (a menu being open). The pump lives in components/
// and cannot include apps/ headers to call isGuiMode() itself, so this bridge answers it. Null-
// guarded for early startup / teardown when the WindowManager doesn't exist yet.
extern "C" bool companionIsGuiMode()
{
    MWBase::WindowManager* wm = MWBase::Environment::get().getWindowManager();
    return wm && wm->isGuiMode();
}
// Pushed from Kotlin (EngineActivity) whenever the "Touch input" option changes, and once at
// startup with the persisted value. Caches into g_companionTouchClick for companionTouchClick().
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionTouchClick(JNIEnv* /*env*/, jclass /*cls*/, jboolean enabled)
{
    g_companionTouchClick.store(enabled == JNI_TRUE);
}
// Read by SDLSurface.onTouch (Java): true when direct touch-to-click should apply — the "Touch
// input" option is on AND a menu (GUI mode) is open. When true, onTouch skips the right-thumbstick
// drop-gate so the tap flows to SDL and becomes an absolute mouse click at the tap point.
extern "C" JNIEXPORT jboolean JNICALL
Java_org_libsdl_app_SDLActivity_companionTouchClickActive(JNIEnv* /*env*/, jclass /*cls*/)
{
    return (companionTouchClick() && companionIsGuiMode()) ? JNI_TRUE : JNI_FALSE;
}
// Per-element native HUD visibility bridges (companion-hud-elements.patch reads these in
// hud.cpp to gate each element's setVisible). Each returns true when the native element
// should be shown. Pushed from Kotlin (EngineActivity) on change + once at startup.
extern "C" bool companionHudHms() { return g_companionHudHms.load(); }
extern "C" bool companionHudEquipped() { return g_companionHudEquipped.load(); }
extern "C" bool companionHudMinimap() { return g_companionHudMinimap.load(); }
extern "C" bool companionHudEffects() { return g_companionHudEffects.load(); }
extern "C" bool companionHudSneak() { return g_companionHudSneak.load(); }
extern "C" bool companionHudCrosshair() { return g_companionHudCrosshair.load(); }
extern "C" bool companionHudEnemy() { return g_companionHudEnemy.load(); }
extern "C" bool companionHudControllerTooltips() { return g_companionHudControllerTooltips.load(); }

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setMinimumInteriorBrightness(JNIEnv*, jclass, jfloat value)
{
    // Parked for the engine thread to pick up; see companionApplyPendingInteriorBrightness.
    g_companionPendingInteriorBrightness.store(value < 0.f ? 0.f : value, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudHms(JNIEnv*, jclass, jboolean on)
{
    g_companionHudHms.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudEquipped(JNIEnv*, jclass, jboolean on)
{
    g_companionHudEquipped.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudMinimap(JNIEnv*, jclass, jboolean on)
{
    g_companionHudMinimap.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudEffects(JNIEnv*, jclass, jboolean on)
{
    g_companionHudEffects.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudSneak(JNIEnv*, jclass, jboolean on)
{
    g_companionHudSneak.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudCrosshair(JNIEnv*, jclass, jboolean on)
{
    g_companionHudCrosshair.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudEnemy(JNIEnv*, jclass, jboolean on)
{
    g_companionHudEnemy.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionHudControllerTooltips(JNIEnv*, jclass, jboolean on)
{
    g_companionHudControllerTooltips.store(on == JNI_TRUE);
}
// Per-element DS-mode suppression bridges. Each returns true when the element is in DS mode and
// the native top-screen window should therefore be suppressed. Read by the engine suppression
// patches (windowmanagerimp.cpp) via extern "C" declarations. Pushed from Kotlin (EngineActivity)
// on change + once at startup with the persisted GameUiMode.
extern "C" bool companionDsConversation() { return g_companionDsConversation.load(); }
extern "C" bool companionDsLooting() { return g_companionDsLooting.load(); }
extern "C" bool companionDsBarter() { return g_companionDsBarter.load(); }
extern "C" bool companionDsRepair() { return g_companionDsRepair.load(); }
extern "C" bool companionDsLevelUp() { return g_companionDsLevelUp.load(); }
extern "C" bool companionDsSpellmaking() { return g_companionDsSpellmaking.load(); }
extern "C" bool companionDsEnchanting() { return g_companionDsEnchanting.load(); }
extern "C" bool companionDsAlchemy() { return g_companionDsAlchemy.load(); }
extern "C" bool companionDsMapActive()
{
    return g_companionDsMap.load() && g_companionMapMounted.load();
}
extern "C" bool companionDsRestWait() { return g_companionDsRestWait.load(); }
extern "C" bool companionDsCrimeAlerts() { return g_companionDsCrimeAlerts.load(); }
extern "C" bool companionDsSpellBuying() { return g_companionDsSpellBuying.load(); }
extern "C" bool companionDsTraining() { return g_companionDsTraining.load(); }
extern "C" bool companionDsTravel() { return g_companionDsTravel.load(); }

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsConversation(JNIEnv*, jclass, jboolean on)
{
    g_companionDsConversation.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsLooting(JNIEnv*, jclass, jboolean on)
{
    g_companionDsLooting.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsBarter(JNIEnv*, jclass, jboolean on)
{
    g_companionDsBarter.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsRepair(JNIEnv*, jclass, jboolean on)
{
    g_companionDsRepair.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsLevelUp(JNIEnv*, jclass, jboolean on)
{
    g_companionDsLevelUp.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsSpellmaking(JNIEnv*, jclass, jboolean on)
{
    g_companionDsSpellmaking.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsEnchanting(JNIEnv*, jclass, jboolean on)
{
    g_companionDsEnchanting.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsAlchemy(JNIEnv*, jclass, jboolean on)
{
    g_companionDsAlchemy.store(on == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsMap(JNIEnv*, jclass, jboolean on)
{
    g_companionDsMap.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsRestWait(JNIEnv*, jclass, jboolean on)
{
    g_companionDsRestWait.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsCrimeAlerts(JNIEnv*, jclass, jboolean on)
{
    g_companionDsCrimeAlerts.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsSpellBuying(JNIEnv*, jclass, jboolean on)
{
    g_companionDsSpellBuying.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsTraining(JNIEnv*, jclass, jboolean on)
{
    g_companionDsTraining.store(on == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionDsTravel(JNIEnv*, jclass, jboolean on)
{
    g_companionDsTravel.store(on == JNI_TRUE);
}
// Decodes an item icon from the VFS (BSA/loose files) and writes it as a PNG.
// Called from Kotlin on any thread when a new icon path is encountered.
// iconPath is the raw ESM icon path (may use backslashes; VFS::Path::Normalized handles it).
// outputPath is an absolute filesystem path for the PNG output.
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_exportIconToPng(
    JNIEnv* env, jclass /*cls*/, jstring jIconPath, jstring jOutputPath)
{
    Resource::ResourceSystem* rs = MWBase::Environment::get().getResourceSystem();
    if (!rs) {
        Log(Debug::Error) << "exportIconToPng: ResourceSystem not available";
        return;
    }

    const char* iconPath   = env->GetStringUTFChars(jIconPath,   nullptr);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, nullptr);

    try {
        VFS::Path::Normalized normalized(iconPath);
        osg::ref_ptr<osg::Image> image = rs->getImageManager()->getImage(normalized);
        if (!image) {
            Log(Debug::Warning) << "exportIconToPng: image not found for '" << iconPath << "'";
            env->ReleaseStringUTFChars(jIconPath,   iconPath);
            env->ReleaseStringUTFChars(jOutputPath, outputPath);
            return;
        }

        if (image->isCompressed()) {
            // DXT/S3TC compressed — the PNG writer can't handle these. Decompress
            // to RGBA in software. osg::Image::getColor() decodes compressed
            // blocks on the CPU (no GL context required) — the same fallback
            // OpenMW itself uses under OPENMW_DECOMPRESS_TEXTURES; see
            // components/resource/imagemanager.cpp.
            osg::ref_ptr<osg::Image> rgba = new osg::Image;
            rgba->setFileName(image->getFileName());
            rgba->setOrigin(image->getOrigin());
            rgba->allocateImage(image->s(), image->t(), image->r(),
                GL_RGBA, GL_UNSIGNED_BYTE);
            for (int s = 0; s < image->s(); ++s)
                for (int t = 0; t < image->t(); ++t)
                    for (int r = 0; r < image->r(); ++r)
                        rgba->setColor(image->getColor(s, t, r), s, t, r);
            image = rgba;
        }

        bool ok = osgDB::writeImageFile(*image, outputPath);
        if (!ok) {
            Log(Debug::Warning) << "exportIconToPng: writeImageFile returned false for '"
                << outputPath << "' (pixelFormat=0x" << std::hex << image->getPixelFormat() << ")";
        }
    } catch (const std::exception& e) {
        Log(Debug::Error) << "exportIconToPng '" << iconPath << "': " << e.what();
    }

    env->ReleaseStringUTFChars(jIconPath,   iconPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
}
// -----------------------------------------------------------------------------

/* Called before  to initialize JNI bindings  */

extern void SDL_Android_Init(JNIEnv* env, jclass cls);
extern int argcData;
extern const char** argvData;
void releaseArgv();

// ---------------------------------------------------------------------------------------------
// FRAME TIMING for the on-screen FPS counter / frametime graph (Sep 2026)
//
// PULL, not push. The engine writes each frame's timings into this ring and Kotlin reads the whole
// ring when it redraws the overlay. Pushing one COMPANION_ line per frame would be ~60 lines/s of
// JNI + parse + StateFlow churn for a readout nothing consumes faster than a few Hz -- the same
// reasoning that switched the four COMPANION_NAV_ polls off for the DS map. This way an overlay
// that is not on screen costs literally nothing but two stores per frame.
//
// TWO SERIES, and the distinction is the whole point of the feature:
//   work  -- frame start up to just before FrameRateLimiter::limit(), i.e. real work, no cap sleep
//   total -- frame start to frame start, i.e. the interval actually delivered
// Misc::FrameRateLimiter::limit() OVERWRITES its own reported duration with the cap exactly
// whenever a frame finishes early (mLastFrameDuration = mMaxFrameDuration), so the `frametime` the
// engine passes around reads a flat 16.667ms at our shipped `framerate limit = 60` and can never
// show headroom. `work` is what a graph needs; `total` is what an FPS number should say. Note that
// when the cap IS being missed the two are equal, so the work series is a superset of the other.
//
// THREADING: written on the engine thread, read on the Android UI thread, deliberately WITHOUT a
// lock -- the engine thread must never block on the UI. The write index is released after the two
// stores, so a reader that sees index N sees the samples before it; a sample torn by a wrapping
// writer is one wrong bar in a graph and is not worth a mutex on the frame loop.
static constexpr int kCompanionFrameRing = 192; // ~3.2s at 60fps; the graph shows a window of this
static float g_companionFrameWork[kCompanionFrameRing] = {};
static float g_companionFrameTotal[kCompanionFrameRing] = {};
static std::atomic<uint32_t> g_companionFrameCount{ 0 };

// Called once per simulation frame from Engine::go()'s loop -- see companion-frame-timing.patch.
// NOTE this is per SIMULATION frame; a loading screen renders extra viewer frames inside one, so
// the counter under-reports during loads. Accepted: it is a diagnostic, not an accounting tool.
extern "C" void companionRecordFrame(float workMs, float totalMs)
{
    const uint32_t n = g_companionFrameCount.load(std::memory_order_relaxed);
    const int i = static_cast<int>(n % kCompanionFrameRing);
    g_companionFrameWork[i] = workMs;
    g_companionFrameTotal[i] = totalMs;
    g_companionFrameCount.store(n + 1, std::memory_order_release);
}

// Returns [validCount, work x N (oldest..newest), total x N (oldest..newest)], or null before any
// frame has been recorded. Ordering is resolved HERE rather than in Kotlin so the ring's wrap is
// not a second place that can get the arithmetic wrong.
extern "C" JNIEXPORT jfloatArray JNICALL Java_org_openmw_EngineActivity_getCompanionFrameTimes(
    JNIEnv* env, jclass /*cls*/)
{
    const uint32_t n = g_companionFrameCount.load(std::memory_order_acquire);
    if (n == 0)
        return nullptr;

    const int valid = static_cast<int>(std::min<uint32_t>(n, kCompanionFrameRing));
    const int total = 1 + 2 * valid;
    std::vector<float> out(static_cast<size_t>(total));
    out[0] = static_cast<float>(valid);
    // Oldest sample first. When the ring has wrapped the oldest lives at (n % size).
    const int start = static_cast<int>(n % kCompanionFrameRing);
    for (int k = 0; k < valid; ++k)
    {
        const int src = (n < static_cast<uint32_t>(kCompanionFrameRing))
            ? k
            : ((start + k) % kCompanionFrameRing);
        out[1 + k] = g_companionFrameWork[src];
        out[1 + valid + k] = g_companionFrameTotal[src];
    }

    jfloatArray arr = env->NewFloatArray(total);
    if (arr == nullptr)
        return nullptr;
    env->SetFloatArrayRegion(arr, 0, total, out.data());
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL Java_org_openmw_EngineActivity_getLastResourceName(JNIEnv* env, jobject thiz)
{
    return env->NewStringUTF(MWSound::g_lastResourceName.c_str());
}

extern "C" int Java_org_libsdl_app_SDLActivity_getMouseX(JNIEnv* env, jclass cls, jobject obj)
{
    int ret = 0;
    SDL_GetMouseState(&ret, nullptr);
    return ret;
}

extern "C" int Java_org_libsdl_app_SDLActivity_getMouseY(JNIEnv* env, jclass cls, jobject obj)
{
    int ret = 0;
    SDL_GetMouseState(nullptr, &ret);
    return ret;
}

extern "C" int Java_org_libsdl_app_SDLActivity_isMouseShown(JNIEnv* env, jclass cls, jobject obj)
{
    return SDL_ShowCursor(SDL_QUERY);
}

extern "C" int Java_org_libsdl_app_SDLActivity_nativeInit(JNIEnv* env, jclass cls, jobject obj)
{
    setenv("OPENMW_DECOMPRESS_TEXTURES", "1", 1);

    // On Android, we use a virtual controller with guid="Virtual"
    SDL_GameControllerAddMapping(
        "5669727475616c000000000000000000,Virtual,a:b0,b:b1,back:b15,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,dpup:h0.1,"
        "guide:b16,leftshoulder:b6,leftstick:b13,lefttrigger:a5,leftx:a0,lefty:a1,rightshoulder:b7,rightstick:b14,"
        "righttrigger:a4,rightx:a2,righty:a3,start:b11,x:b3,y:b4");

    SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE, "0");
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");

    return 0;
}

extern osg::ref_ptr<osgViewer::Viewer> g_viewer;
static osg::GraphicsContext* ctx;

class CtxReleaseOperation : public osg::Operation {
public:
    virtual void operator()(osg::Object* caller) { ctx->releaseContext(); }
};

class CtxAcquireOperation : public osg::Operation {
public:
    virtual void operator()(osg::Object* caller) { ctx->makeCurrent(); }
};

extern "C" void Java_org_libsdl_app_SDLActivity_omwSurfaceDestroyed(JNIEnv* env, jclass cls, jobject obj)
{
    if (!g_viewer)
        return;

    osg::ref_ptr<CtxReleaseOperation> op = new CtxReleaseOperation();
    ctx = g_viewer->getCamera()->getGraphicsContext();
    ctx->add(op);

    auto win = (MWBase::WindowManager*)MWBase::Environment::get().getWindowManager();
    if (win)
        win->windowVisibilityChange(false);
}

extern "C" void Java_org_libsdl_app_SDLActivity_omwSurfaceRecreated(JNIEnv* env, jclass cls, jobject obj)
{
    if (!g_viewer)
        return;

    osg::ref_ptr<CtxAcquireOperation> op = new CtxAcquireOperation();
    ctx = g_viewer->getCamera()->getGraphicsContext();
    ctx->add(op);

    auto win = (MWBase::WindowManager*)MWBase::Environment::get().getWindowManager();
    if (win)
        win->windowVisibilityChange(true);
}
