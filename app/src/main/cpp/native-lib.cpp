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

#define LOG_TAG "KeySearch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

std::atomic<bool> g_found(false);
std::atomic<bool> g_pause(false);
std::mutex file_mutex;

const char* BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

std::string base58_encode(const std::vector<unsigned char>& input) {
    std::vector<unsigned char> b(input.begin(), input.end());
    int zeroes = 0;
    while (zeroes < b.size() && b[zeroes] == 0) zeroes++;

    std::string result;
    while (!b.empty()) {
        int carry = 0;
        std::vector<unsigned char> b58;
        for (size_t i = 0; i < b.size(); i++) {
            int val = (int)b[i] + carry * 256;
            carry = val / 58;
            b58.push_back(val % 58);
        }
        result += BASE58_ALPHABET[carry];
        b.clear();
        for (auto it = b58.rbegin(); it != b58.rend(); ++it) {
            if (*it != 0 || !b.empty()) b.push_back(*it);
        }
    }
    for (int i = 0; i < zeroes; i++) result = BASE58_ALPHABET[0] + result;
    return result;
}

void save_result(const std::string& key) {
    std::lock_guard<std::mutex> lock(file_mutex);
    std::ofstream ofs("/sdcard/Download/found_keys.txt", std::ios::app);
    ofs << key << std::endl;
    LOGI("Saved key: %s", key.c_str());
}

void search_range(uint64_t start, uint64_t end, const std::string& target_address, JNIEnv* env, jobject callback) {
    jclass cls = env->GetObjectClass(callback);
    jmethodID onKeyFound_mid = env->GetMethodID(cls, "onKeyFound", "(Ljava/lang/String;)V");
    jmethodID onProgressUpdate_mid = env->GetMethodID(cls, "onProgressUpdate", "(J)V");
    jmethodID onSearchFinished_mid = env->GetMethodID(cls, "onSearchFinished", "()V");

    uint64_t total_keys = end - start + 1;
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
            env->CallVoidMethod(callback, onProgressUpdate_mid, (jlong)keys_checked);
            last_update = std::chrono::steady_clock::now();
        }

        if (current_address == target_address) {
            g_found.store(true);
            save_result(std::to_string(k));

            jstring jkey = env->NewStringUTF(std::to_string(k).c_str());
            env->CallVoidMethod(callback, onKeyFound_mid, jkey);
            env->DeleteLocalRef(jkey);
            break;
        }
    }

    env->CallVoidMethod(callback, onSearchFinished_mid);
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
    std::thread t(search_range, (uint64_t)start, (uint64_t)end, std::string(target), env, callback);
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

