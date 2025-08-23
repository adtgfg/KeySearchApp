#include <jni.h>
#include <string>
#include <thread>
#include <vector>
#include <atomic>
#include <mutex>
#include <fstream>
#include <openssl/sha.h>
#include <openssl/ripemd.h>
#include <android/log.h>
#include <chrono>
#include <algorithm>

#define LOG_TAG "KeySearch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

std::atomic<bool> g_found(false);
std::atomic<bool> g_pause(false);
std::mutex file_mutex;

const char* BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

std::string base58_encode(const std::vector<unsigned char>& input) {
    std::vector<unsigned char> b(input.begin(), input.end());
    int zeroes = 0;
    while (zeroes < (int)b.size() && b[zeroes] == 0) zeroes++;

    std::string result;
    if (b.empty()) return result;

    std::vector<unsigned char> temp;
    while (!b.empty()) {
        int carry = 0;
        temp.clear();
        for (size_t i = 0; i < b.size(); ++i) {
            int val = (int)b[i] + carry * 256;
            carry = val / 58;
            temp.push_back((unsigned char)(val % 58));
        }
        result.push_back(BASE58_ALPHABET[carry]);
        // divide b by 58
        std::vector<unsigned char> newb;
        int rem = 0;
        for (size_t i = 0; i < b.size(); ++i) {
            int val = rem * 256 + b[i];
            int q = val / 58;
            rem = val % 58;
            if (!newb.empty() || q != 0) newb.push_back((unsigned char)q);
        }
        b.swap(newb);
    }

    std::reverse(result.begin(), result.end());
    for (int i = 0; i < zeroes; ++i) result.insert(result.begin(), BASE58_ALPHABET[0]);
    return result;
}

void save_result_to_dir(const std::string& dirPath, const std::string& key) {
    std::lock_guard<std::mutex> lock(file_mutex);
    std::string full = dirPath;
    if (!full.empty() && full.back() != '/') full += "/";
    full += "found_keys.txt";
    std::ofstream ofs(full, std::ios::app);
    if (ofs.is_open()) {
        ofs << key << std::endl;
        LOGI("Saved key to %s: %s", full.c_str(), key.c_str());
    } else {
        LOGI("Failed to open file for writing: %s", full.c_str());
    }
}

struct ThreadParams {
    JavaVM* jvm;
    jobject callbackGlobal;
    uint64_t start;
    uint64_t end;
    std::string target_address;
};

void search_range_thread(ThreadParams* params) {
    JavaVM* jvm = params->jvm;
    jobject callback = params->callbackGlobal;
    uint64_t start = params->start;
    uint64_t end = params->end;
    std::string target_address = params->target_address;

    JNIEnv* env = nullptr;
    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        LOGI("Failed to attach thread to JVM");
        // cleanup: delete params and global ref using original JVM? best-effort: try to get env from main thread not possible here
        // Attempt to delete global ref via a temporary attach/detach if possible
        // For simplicity, avoid undefined behavior — leak params in this rare failure case
        delete params;
        return;
    }

    jclass cls = env->GetObjectClass(callback);

    jmethodID onKeyFound_mid = env->GetMethodID(cls, "onKeyFound", "(Ljava/lang/String;)V");
    jmethodID onProgressUpdate_mid = env->GetMethodID(cls, "onProgressUpdate", "(J)V");
    jmethodID onSearchFinished_mid = env->GetMethodID(cls, "onSearchFinished", "()V");
    jmethodID getExternalFilesDir_mid = env->GetMethodID(cls, "getExternalFilesDir", "(Ljava/lang/String;)Ljava/io/File;");

    jclass fileCls = nullptr;
    jmethodID getAbsolutePath_mid = nullptr;
    if (getExternalFilesDir_mid != nullptr) {
        jclass localFileCls = env->FindClass("java/io/File");
        if (localFileCls != nullptr) {
            fileCls = (jclass)env->NewGlobalRef(localFileCls);
            env->DeleteLocalRef(localFileCls);
            getAbsolutePath_mid = env->GetMethodID(fileCls, "getAbsolutePath", "()Ljava/lang/String;");
        }
    }

    uint64_t keys_checked = 0;
    auto last_update = std::chrono::steady_clock::now();

    for (uint64_t k = start; k <= end && !g_found.load(); k++) {
        while (g_pause.load()) std::this_thread::sleep_for(std::chrono::milliseconds(100));

        unsigned char privkey[32] = {0};
        for (int i = 24; i < 32; ++i) privkey[i] = (k >> ((31 - i) * 8)) & 0xFF;

        unsigned char pubhash[SHA256_DIGEST_LENGTH];
        SHA256(privkey, 32, pubhash);
        unsigned char ripemd[RIPEMD160_DIGEST_LENGTH];
        RIPEMD160(pubhash, SHA256_DIGEST_LENGTH, ripemd);

        std::vector<unsigned char> addr_bytes(1 + RIPEMD160_DIGEST_LENGTH + 4);
        addr_bytes[0] = 0x00;
        memcpy(&addr_bytes[1], ripemd, RIPEMD160_DIGEST_LENGTH);

        unsigned char checksum_full[SHA256_DIGEST_LENGTH];
        SHA256(addr_bytes.data(), RIPEMD160_DIGEST_LENGTH + 1, checksum_full);
        SHA256(checksum_full, SHA256_DIGEST_LENGTH, checksum_full);

        memcpy(&addr_bytes[RIPEMD160_DIGEST_LENGTH + 1], checksum_full, 4);
        std::string current_address = base58_encode(addr_bytes);

        keys_checked++;

        if (keys_checked % 50000 == 0 || std::chrono::steady_clock::now() - last_update > std::chrono::seconds(1)) {
            if (onProgressUpdate_mid != nullptr) env->CallVoidMethod(callback, onProgressUpdate_mid, (jlong)keys_checked);
            last_update = std::chrono::steady_clock::now();
        }

        if (current_address == target_address) {
            g_found.store(true);

            std::string dirPath;
            if (getExternalFilesDir_mid != nullptr && getAbsolutePath_mid != nullptr) {
                jobject fileObj = env->CallObjectMethod(callback, getExternalFilesDir_mid, (jstring) nullptr);
                if (fileObj != nullptr) {
                    jstring pathJ = (jstring)env->CallObjectMethod(fileObj, getAbsolutePath_mid);
                    if (pathJ != nullptr) {
                        const char* pathC = env->GetStringUTFChars(pathJ, nullptr);
                        if (pathC != nullptr) dirPath = pathC;
                        env->ReleaseStringUTFChars(pathJ, pathC);
                        env->DeleteLocalRef(pathJ);
                    }
                    env->DeleteLocalRef(fileObj);
                }
            }

            std::string foundStr = std::to_string(k);
            if (!dirPath.empty()) {
                save_result_to_dir(dirPath, foundStr);
            } else {
                LOGI("Found key (no dir): %s", foundStr.c_str());
            }

            if (onKeyFound_mid != nullptr) {
                jstring jkey = env->NewStringUTF(foundStr.c_str());
                env->CallVoidMethod(callback, onKeyFound_mid, jkey);
                env->DeleteLocalRef(jkey);
            }
            break;
        }
    }

    if (onSearchFinished_mid != nullptr) env->CallVoidMethod(callback, onSearchFinished_mid);

    if (fileCls != nullptr) env->DeleteGlobalRef(fileCls);
    env->DeleteGlobalRef(callback);

    jvm->DetachCurrentThread();

    delete params;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_keysearchapp_MainActivity_startSearchNative(JNIEnv *env, jobject thiz,
                                                             jlong start, jlong end,
                                                             jstring targetAddr,
                                                             jobject callback) {
    const char* target = env->GetStringUTFChars(targetAddr, 0);
    g_found.store(false);
    g_pause.store(false);

    JavaVM* jvm = nullptr;
    if (env->GetJavaVM(&jvm) != JNI_OK) {
        LOGI("Failed to get JavaVM");
        env->ReleaseStringUTFChars(targetAddr, target);
        return;
    }

    jobject callbackGlobal = env->NewGlobalRef(callback);

    ThreadParams* params = new ThreadParams();
    params->jvm = jvm;
    params->callbackGlobal = callbackGlobal;
    params->start = (uint64_t)start;
    params->end = (uint64_t)end;
    params->target_address = std::string(target ? target : "");

    std::thread t(search_range_thread, params);
    t.detach();

    env->ReleaseStringUTFChars(targetAddr, target);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_keysearchapp_MainActivity_pauseSearchNative(JNIEnv *env, jobject thiz) {
    g_pause.store(true);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_keysearchapp_MainActivity_resumeSearchNative(JNIEnv *env, jobject thiz) {
    g_pause.store(false);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_keysearchapp_MainActivity_stopSearchNative(JNIEnv *env, jobject thiz) {
    g_found.store(true);
}
