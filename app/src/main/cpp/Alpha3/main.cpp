#include "main.h"
#include "spdlog/sinks/android_sink.h"
#include "BackTrace.h"
#include "deps/Obfuscator/obfuscator.h"
#include "spdlog/sinks/basic_file_sink.h"


JavaVM* g_java_vm = nullptr;
Java* g_java = nullptr;

void initCrashDump()
{
    // signal handler
    struct sigaction sig_action{};
    sig_action.sa_sigaction = [](int signal, siginfo_t* info, void* ctx) {
        dump_register(signal, info, ctx);
        dump_stack(2);
        exit(signal);
    };
    sigemptyset(&sig_action.sa_mask);
    sig_action.sa_flags = SA_SIGINFO;
    sigaction(SIGSEGV, &sig_action, nullptr);
}

/**
 * init spdlog
 * @param env
 * @return
 */
int initSpdLog(JNIEnv* env) {
    try {
        // android sink (print to logcat)
        auto android_sink = std::make_shared<spdlog::sinks::android_sink_mt>(LOG_TAG);

        // file sink (save to file)
        std::string logPath = g_java->getExternalStoragePath(env) + "/OpenMW-DS/config/native_crash.log";
        auto file_sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(logPath);

        std::vector<spdlog::sink_ptr> sinks { android_sink, file_sink };
        auto logger = std::make_shared<spdlog::logger>("multi_logger", sinks.begin(), sinks.end());
        logger->set_level(spdlog::level::info);
        logger->set_pattern("[%Y-%m-%d %H:%M:%S] [%^%l%$] %v");

        spdlog::set_default_logger(logger);
        spdlog::flush_on(spdlog::level::info);
        spdlog::info("Logger initialized successfully");
        return 1;
    }
    catch (const spdlog::spdlog_ex& ex) {
        LOGE("Log initialization failed: %s", ex.what());
        return 0;
    }
}

jint JNI_OnLoad(JavaVM* vm, [[maybe_unused]] void* reserved)
{
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}
	g_java_vm = vm;

    g_java = new Java(g_java_vm, env);
    if (g_java->getVersion().empty()) {
        return env->GetVersion();
    }

    initCrashDump();
    if (!initSpdLog(env)) {
        return env->GetVersion();
    }

    spdlog::info("Alpha3 version: {}", g_java->getVersion());

    spdlog::info("Alpha3 library loaded! Build time: " __DATE__ " " __TIME__);
	return env->GetVersion();
}

void JNI_OnUnload([[maybe_unused]] JavaVM* vm, [[maybe_unused]] void* reserved)
{
    spdlog::info("Alpha3 library unloaded!");
}

extern "C" JNIEXPORT void JNICALL Java_org_openmw_EngineActivity_initAlpha3(JNIEnv* env, jobject obj)
{
    g_java->setupContext(obj, env);
}
