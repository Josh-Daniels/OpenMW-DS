#if defined(stderr) && (__ANDROID_API__ < 23)
int stderr = 0; // Hack: fix linker error
#endif

#include "SDL_main.h"
#include "engine.hpp"
#include "mwbase/environment.hpp"
#include "mwbase/journal.hpp"
#include "mwbase/luamanager.hpp"
#include "mwbase/windowmanager.hpp"
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
// both thumbsticks) while the option is off. Default false = cursor suppressed.
// std::atomic: written on a JNI thread, read on the input/engine threads. See
// companion-gamecursor-suppress.patch.
static std::atomic<bool> g_companionCursorEnabled{ false };

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
            Log(Debug::Info) << "companion: selectTopic " << arg;
            companionDialogueSelectEntry(arg.c_str());
        }
        else if (cmd.rfind("CMPDLG:service:", 0) == 0)
        {
            std::string arg = cmd.substr(sizeof("CMPDLG:service:") - 1);
            Log(Debug::Info) << "companion: activateService " << arg;
            companionDialogueSelectEntry(arg.c_str());
        }
        else if (cmd.rfind("CMPDLG:choice:", 0) == 0)
        {
            const int id = std::atoi(cmd.c_str() + (sizeof("CMPDLG:choice:") - 1));
            Log(Debug::Info) << "companion: selectChoice " << id;
            companionDialogueChoice(id);
        }
        else if (cmd.rfind("CMPDLG:goodbye", 0) == 0)
        {
            Log(Debug::Info) << "companion: goodbye";
            companionDialogueGoodbye();
        }
        else if (cmd.rfind("CMPDLG:persuade:", 0) == 0)
        {
            // Persuasion is driven from the bottom-screen popup; the native modal is
            // never shown. type 0..5 = Admire/Intimidate/Taunt/Bribe10/Bribe100/Bribe1000.
            const int type = std::atoi(cmd.c_str() + (sizeof("CMPDLG:persuade:") - 1));
            Log(Debug::Info) << "companion: persuade " << type;
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
                Log(Debug::Info) << "companion: barter " << (isBorrow ? "borrow " : "return ") << count << " "
                                 << side << " " << refId;
                if (isBorrow)
                    companionBarterBorrow(side.c_str(), refId.c_str(), count);
                else
                    companionBarterReturn(side.c_str(), refId.c_str(), count);
            }
        }
        else if (cmd.rfind("CMP:barter_gold ", 0) == 0)
        {
            const int extra = std::atoi(cmd.c_str() + (sizeof("CMP:barter_gold ") - 1));
            Log(Debug::Info) << "companion: barter gold " << extra;
            companionBarterSetGold(extra);
        }
        else if (cmd.rfind("CMP:barter_offer", 0) == 0)
        {
            Log(Debug::Info) << "companion: barter offer";
            companionBarterOffer();
        }
        else if (cmd.rfind("CMP:barter_cancel", 0) == 0)
        {
            Log(Debug::Info) << "companion: barter cancel";
            companionBarterCancel();
        }
        else
        {
            lua->handleConsoleCommand("Companion", cmd, MWWorld::Ptr());
        }
    }
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
                                                "(IIIIIFF[B)V");
    g_hudVisibilityMethod = env->GetStaticMethodID(g_companionClass, "onHudVisibilityChanged",
                                                   "(Z)V");
    env->DeleteLocalRef(cls);

    Debug::setLogListener([](Debug::Level, std::string_view /*prefix*/, std::string_view msg) {
        // Cache LuaManager once Lua is provably running (first stats export).
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
extern "C" void companionDeliverMapTexture(
    int width, int height, int segX, int segY, int isInterior, float boundsMinX, float boundsMinY,
    const unsigned char* rgba)
{
    Log(Debug::Info) << "companion map: w=" << width << " h=" << height
                      << " segX=" << segX << " segY=" << segY << " interior=" << isInterior
                      << " boundsMinX=" << boundsMinX << " boundsMinY=" << boundsMinY;

    if (!g_companionVm || !g_companionClass || !g_mapTextureMethod) return;

    JNIEnv* e = nullptr;
    g_companionVm->AttachCurrentThread(&e, nullptr);

    const jsize size = static_cast<jsize>(width) * height * 4;
    jbyteArray arr = e->NewByteArray(size);
    if (!arr) return;

    e->SetByteArrayRegion(arr, 0, size, reinterpret_cast<const jbyte*>(rgba));
    e->CallStaticVoidMethod(g_companionClass, g_mapTextureMethod,
                            (jint)width, (jint)height, (jint)segX, (jint)segY,
                            (jint)isInterior, (jfloat)boundsMinX, (jfloat)boundsMinY, arr);
    if (e->ExceptionCheck()) {
        e->ExceptionDescribe();
        e->ExceptionClear();
    }
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
// Pushed from Kotlin (EngineActivity) whenever the "Game cursor" option changes,
// and once at startup with the persisted value. Caches into g_companionCursorEnabled
// for companionCursorEnabled() above.
extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_setCompanionCursorEnabled(JNIEnv* /*env*/, jclass /*cls*/, jboolean enabled)
{
    g_companionCursorEnabled.store(enabled == JNI_TRUE);
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

    Log(Debug::Info) << "exportIconToPng: '" << iconPath << "' -> '" << outputPath << "'";

    try {
        VFS::Path::Normalized normalized(iconPath);
        osg::ref_ptr<osg::Image> image = rs->getImageManager()->getImage(normalized);
        if (!image) {
            Log(Debug::Warning) << "exportIconToPng: image not found for '" << iconPath << "'";
            env->ReleaseStringUTFChars(jIconPath,   iconPath);
            env->ReleaseStringUTFChars(jOutputPath, outputPath);
            return;
        }

        Log(Debug::Info) << "exportIconToPng: loaded '" << iconPath << "' "
            << image->s() << "x" << image->t()
            << " compressed=" << (image->isCompressed() ? 1 : 0)
            << " pixelFormat=0x" << std::hex << image->getPixelFormat() << std::dec;

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
            Log(Debug::Info) << "exportIconToPng: decompressed '" << iconPath
                << "' to RGBA";
        }

        bool ok = osgDB::writeImageFile(*image, outputPath);
        if (!ok) {
            Log(Debug::Warning) << "exportIconToPng: writeImageFile returned false for '"
                << outputPath << "' (pixelFormat=0x" << std::hex << image->getPixelFormat() << ")";
        } else {
            Log(Debug::Info) << "exportIconToPng: wrote '" << outputPath << "'";
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
