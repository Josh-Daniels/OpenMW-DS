#pragma once

#include <jni.h>
#include <thread>
#include <string>
#include <array>
#include <vector>
#include <mutex>
#include <future>
#include <dlfcn.h>
#include <android/log.h>
#include <spdlog/spdlog.h>
#include <unordered_map>
#include <Gloss.h>
#include <SymUtils.h>
#include <Memory.h>
#include "java.h"

class main {
public:

private:

};

extern Java* g_java;

#define LOG_TAG "Alpha3"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
