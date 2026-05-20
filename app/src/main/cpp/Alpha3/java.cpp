//
// Created by 33688 on 2024/12/4.
//
#include <jni.h>
#include "main.h"
#include "java.h"

Java::Java(JavaVM* g_java_vm, JNIEnv* env) {
    m_Env = env;
    javaVM = g_java_vm;
}

/**
 * Setup Context
 * @param thiz SDLActivity
 * @param env
 */
void Java::setupContext(jobject thiz, JNIEnv* env) {
    spdlog::info("setup context...");
    m_Env = env;
    activity = env->NewGlobalRef(thiz);
    jclass sdlClass = env->GetObjectClass(activity);
    if (!sdlClass)
    {
        LOGI("SDL class not found");
        return;
    }
    m_Env->DeleteLocalRef(sdlClass);
}

JNIEnv *Java::getEnv()
{
    if (!javaVM) {
        LOGI("No java vm");
        return nullptr;
    }
    JNIEnv *env;
    javaVM->GetEnv((void **)&env, JNI_VERSION_1_4);
    return env;
}

jobject Java::getContext() {
    return activity;
}

std::string Java::getVersion() {
    if (m_Env == nullptr) {
        return "";
    }
    jclass cls = m_Env->FindClass("org/openmw/Constants");
    if (cls == NULL) {
        return "";
    }
    jfieldID fieldId = m_Env->GetStaticFieldID(cls, "RANDOM_NUM", "Ljava/lang/String;");
    if (fieldId == NULL) {
        return "";
    }
    jstring value = (jstring)m_Env->GetStaticObjectField(cls, fieldId);
    const char* nativeStr = m_Env->GetStringUTFChars(value, 0);
    std::string result(nativeStr);
    m_Env->ReleaseStringUTFChars(value, nativeStr);
    return result;
}



