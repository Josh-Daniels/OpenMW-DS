//
// Created by 33688 on 2024/12/4.
//

#ifndef Alpha3_JAVA_H
#define Alpha3_JAVA_H


class Java {

public:
    Java(JavaVM *g_java_vm, JNIEnv *env);

    void setupContext(jobject thiz, JNIEnv *env);
    jobject getContext();
    std::string getVersion();

    std::string getExternalStoragePath(JNIEnv* env) {
        jclass envClass = env->FindClass("android/os/Environment");
        jmethodID getExtStorageDir = env->GetStaticMethodID(envClass, "getExternalStorageDirectory", "()Ljava/io/File;");
        jobject fileObj = env->CallStaticObjectMethod(envClass, getExtStorageDir);

        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getPathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathString = (jstring)env->CallObjectMethod(fileObj, getPathMethod);

        const char* pathCStr = env->GetStringUTFChars(pathString, nullptr);
        std::string path(pathCStr);
        env->ReleaseStringUTFChars(pathString, pathCStr);

        env->DeleteLocalRef(envClass);
        env->DeleteLocalRef(fileClass);
        env->DeleteLocalRef(fileObj);
        env->DeleteLocalRef(pathString);

        return path;
    }

private:
    JNIEnv *m_Env = nullptr;
    jobject activity;
    JavaVM* javaVM;

    JNIEnv *getEnv();

};


#endif //Alpha3_JAVA_H
