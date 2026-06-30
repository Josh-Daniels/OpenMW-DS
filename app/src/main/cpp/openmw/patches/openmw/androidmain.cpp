#if defined(stderr) && (__ANDROID_API__ < 23)
int stderr = 0; // Hack: fix linker error
#endif

#include "SDL_main.h"
#include "engine.hpp"
#include "mwbase/environment.hpp"
#include "mwbase/luamanager.hpp"
#include "mwbase/windowmanager.hpp"
#include "mwsound/soundbridge.hpp"
#include "mwworld/ptr.hpp"
#include <SDL_events.h>
#include <SDL_gamecontroller.h>
#include <SDL_hints.h>
#include <SDL_mouse.h>
#include <components/vfs/pathutil.hpp>
#include <components/debug/debugging.hpp>

#include <osg/GraphicsContext>
#include <osg/OperationThread>

#include <atomic>
#include <deque>
#include <mutex>
#include <string>

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

// --- Companion command queue -------------------------------------------------
// JNI thread pushes commands here; engine thread drains via drainCompanionCommands().
// g_luaManagerPtr is set once, when the first COMPANION_STATS line arrives,
// guaranteeing Lua is fully initialized before we ever call handleConsoleCommand.

static std::deque<std::string>         g_commandQueue;
static std::mutex                      g_commandMutex;
static std::atomic<MWBase::LuaManager*> g_luaManagerPtr{nullptr};

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
        lua->handleConsoleCommand("Companion", cmd, MWWorld::Ptr());
}

extern "C" JNIEXPORT void JNICALL
Java_org_openmw_EngineActivity_installCompanionSink(JNIEnv* env, jobject /*thiz*/)
{
    env->GetJavaVM(&g_companionVm);

    jclass cls = env->FindClass("org/openmw/EngineActivity");
    g_companionClass  = static_cast<jclass>(env->NewGlobalRef(cls));
    g_companionMethod = env->GetStaticMethodID(g_companionClass, "onCompanionLine",
                                               "(Ljava/lang/String;)V");
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
