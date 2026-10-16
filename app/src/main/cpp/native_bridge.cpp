// Original JNI adapter for the independently built Chiaki provider.
// No source or binaries from the reference APK are included here.
#include <jni.h>
#include <chiaki/common.h>
#include <chiaki/regist.h>
#include <chiaki/session.h>
#include <chiaki/opusdecoder.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace {
JavaVM *vm = nullptr;
class AttachedEnv {
    JNIEnv *env_ = nullptr;
    bool attached_ = false;
public:
    AttachedEnv() {
        if (!vm) return;
        const auto status = vm->GetEnv(reinterpret_cast<void **>(&env_), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            attached_ = vm->AttachCurrentThread(&env_, nullptr) == JNI_OK;
            if (!attached_) env_ = nullptr;
        } else if (status != JNI_OK) env_ = nullptr;
    }
    ~AttachedEnv() { if (attached_) vm->DetachCurrentThread(); }
    JNIEnv *get() const { return env_; }
};
bool clearException(JNIEnv *env) {
    if (!env->ExceptionCheck()) return false;
    // Never print Java exceptions: callback arguments may contain credentials.
    env->ExceptionClear();
    return true;
}
class Callback {
public:
    jobject object = nullptr;
    explicit Callback(JNIEnv *env, jobject callback) {
        if (callback) object = env->NewGlobalRef(callback);
        clearException(env);
    }
    ~Callback() {
        AttachedEnv attached;
        if (auto *env = attached.get(); env && object) env->DeleteGlobalRef(object);
    }
    jmethodID method(JNIEnv *env, const char *name, const char *signature) {
        if (!object) return nullptr;
        jclass cls = env->GetObjectClass(object);
        const bool failed = clearException(env);
        if (!cls || failed) return nullptr;
        jmethodID result = env->GetMethodID(cls, name, signature);
        env->DeleteLocalRef(cls);
        if (clearException(env)) return nullptr;
        return result;
    }
};
bool bytes(JNIEnv *env, jbyteArray input, void *output, jsize expected) {
    if (!input || env->GetArrayLength(input) != expected) return false;
    env->GetByteArrayRegion(input, 0, expected, static_cast<jbyte *>(output));
    return !clearException(env);
}
std::string text(JNIEnv *env, jstring input) {
    if (!input) return {};
    const char *value = env->GetStringUTFChars(input, nullptr);
    const bool failed = clearException(env);
    if (!value || failed) return {};
    std::string result(value);
    env->ReleaseStringUTFChars(input, value);
    return result;
}
bool validHost(const std::string &host) {
    if (host.empty() || host.size() > 255) return false;
    return host.find_first_of(" \t\r\n/\\") == std::string::npos;
}
jbyteArray byteArray(JNIEnv *env, const void *data, size_t size) {
    if (size > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    auto array = env->NewByteArray(static_cast<jsize>(size));
    const bool failed = clearException(env);
    if (!array || failed) return nullptr;
    env->SetByteArrayRegion(array, 0, static_cast<jsize>(size), static_cast<const jbyte *>(data));
    if (clearException(env)) { env->DeleteLocalRef(array); return nullptr; }
    return array;
}
jstring utf8String(JNIEnv *env, const std::string &value) {
    // Console nicknames are ordinary UTF-8, not JNI's modified UTF-8 encoding.
    auto encoded = byteArray(env, value.data(), value.size());
    auto charset = env->NewStringUTF("UTF-8");
    auto cls = env->FindClass("java/lang/String");
    bool failed = clearException(env);
    jstring result = nullptr;
    if (!failed && encoded && charset && cls) {
        auto constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
        if (!clearException(env) && constructor)
            result = static_cast<jstring>(env->NewObject(cls, constructor, encoded, charset));
        clearException(env);
    }
    if (encoded) env->DeleteLocalRef(encoded);
    if (charset) env->DeleteLocalRef(charset);
    if (cls) env->DeleteLocalRef(cls);
    return result;
}
struct Registration : Callback {
    ChiakiRegist regist{};
    ChiakiLog log{};
    jmethodID success = nullptr, failure = nullptr;
    std::mutex operations;
    bool active = false;
    Registration(JNIEnv *env, jobject cb) : Callback(env, cb) {
        chiaki_log_init(&log, 0, nullptr, nullptr);
        success = method(env, "onSuccess", "(ZLjava/lang/String;[B[B[B)V");
        failure = method(env, "onFailure", "()V");
    }
};
struct Session : Callback {
    ChiakiSession session{};
    ChiakiOpusDecoder opus{};
    ChiakiLog log{};
    std::atomic<unsigned int> channels{0};
    jmethodID connected = nullptr, loginPin = nullptr, quit = nullptr;
    jmethodID video = nullptr, audioSettings = nullptr, audio = nullptr;
    std::mutex operations;
    bool active = false, decoderInitialized = false;
    Session(JNIEnv *env, jobject cb) : Callback(env, cb) {
        chiaki_log_init(&log, 0, nullptr, nullptr);
        connected = method(env, "onConnected", "()V");
        loginPin = method(env, "onLoginPin", "(Z)V");
        quit = method(env, "onQuit", "(I)V");
        video = method(env, "onVideo", "([B)Z");
        audioSettings = method(env, "onAudioSettings", "(II)V");
        audio = method(env, "onAudio", "([S)V");
    }
};
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Registration>> registrations;
std::unordered_map<jlong, std::shared_ptr<Session>> sessions;
std::atomic<jlong> nextHandle{1};
template<class T> std::shared_ptr<T> findHandle(std::unordered_map<jlong, std::shared_ptr<T>> &registry, jlong handle, bool remove = false) {
    std::lock_guard<std::mutex> guard(registryMutex);
    const auto it = registry.find(handle);
    if (it == registry.end()) return {};
    auto value = it->second;
    if (remove) registry.erase(it);
    return value;
}
void registrationEvent(ChiakiRegistEvent *event, void *user) {
    auto *owner = static_cast<Registration *>(user);
    AttachedEnv attached;
    auto *env = attached.get();
    if (!env) return;
    if (event->type == CHIAKI_REGIST_EVENT_TYPE_FINISHED_FAILED) {
        env->CallVoidMethod(owner->object, owner->failure);
        clearException(env);
    } else if (event->type == CHIAKI_REGIST_EVENT_TYPE_FINISHED_SUCCESS && event->registered_host) {
        // The provider owns this event payload; copy it synchronously before returning.
        const auto &host = *event->registered_host;
        std::string nickname(host.server_nickname, strnlen(host.server_nickname, sizeof(host.server_nickname)));
        auto name = utf8String(env, nickname);
        clearException(env);
        auto mac = byteArray(env, host.server_mac, sizeof(host.server_mac));
        auto key = byteArray(env, host.rp_regist_key, sizeof(host.rp_regist_key));
        auto morning = byteArray(env, host.rp_key, sizeof(host.rp_key));
        if (name && mac && key && morning)
            env->CallVoidMethod(owner->object, owner->success, static_cast<jboolean>(chiaki_target_is_ps5(host.target)), name, mac, key, morning);
        else env->CallVoidMethod(owner->object, owner->failure);
        clearException(env);
        if (name) env->DeleteLocalRef(name);
        if (mac) env->DeleteLocalRef(mac);
        if (key) env->DeleteLocalRef(key);
        if (morning) env->DeleteLocalRef(morning);
    }
}
void sessionEvent(ChiakiEvent *event, void *user) {
    auto *owner = static_cast<Session *>(user);
    AttachedEnv attached;
    auto *env = attached.get();
    if (!env) return;
    switch (event->type) {
        case CHIAKI_EVENT_CONNECTED: env->CallVoidMethod(owner->object, owner->connected); break;
        case CHIAKI_EVENT_LOGIN_PIN_REQUEST: env->CallVoidMethod(owner->object, owner->loginPin, static_cast<jboolean>(event->login_pin_request.pin_incorrect)); break;
        case CHIAKI_EVENT_QUIT: env->CallVoidMethod(owner->object, owner->quit, static_cast<jint>(event->quit.reason)); break;
        default: break;
    }
    clearException(env);
}
bool videoSample(uint8_t *data, size_t size, int32_t, bool, void *user) {
    if (!data || size == 0 || size > 8 * 1024 * 1024) return false;
    auto *owner = static_cast<Session *>(user);
    AttachedEnv attached;
    auto *env = attached.get();
    if (!env) return false;
    auto array = byteArray(env, data, size);
    if (!array) return false;
    const auto result = env->CallBooleanMethod(owner->object, owner->video, array);
    const bool failed = clearException(env);
    env->DeleteLocalRef(array);
    return !failed && result == JNI_TRUE;
}
void audioSettings(uint32_t channels, uint32_t rate, void *user) {
    auto *owner = static_cast<Session *>(user);
    if (channels < 1 || channels > 2 || rate < 8000 || rate > 192000) return;
    owner->channels.store(channels);
    AttachedEnv attached;
    if (auto *env = attached.get()) {
        env->CallVoidMethod(owner->object, owner->audioSettings, static_cast<jint>(rate), static_cast<jint>(channels));
        clearException(env);
    }
}
void audioFrame(int16_t *data, size_t frames, void *user) {
    auto *owner = static_cast<Session *>(user);
    const size_t channels = owner->channels.load();
    if (!data || channels == 0 || frames == 0 || frames > 19200) return;
    // Chiaki's callback count is frames per channel, while Java consumes interleaved samples.
    const auto samples = static_cast<jsize>(frames * channels);
    AttachedEnv attached;
    auto *env = attached.get();
    if (!env) return;
    auto array = env->NewShortArray(samples);
    const bool failed = clearException(env);
    if (!array || failed) return;
    env->SetShortArrayRegion(array, 0, samples, reinterpret_cast<const jshort *>(data));
    if (!clearException(env)) env->CallVoidMethod(owner->object, owner->audio, array);
    clearException(env);
    env->DeleteLocalRef(array);
}
int16_t axis(jfloat value) {
    if (!std::isfinite(value)) return 0;
    const auto clamped = std::clamp(value, -1.0f, 1.0f);
    return static_cast<int16_t>(std::lround(clamped * (clamped < 0 ? 32768.0f : 32767.0f)));
}
uint8_t trigger(jfloat value) {
    if (!std::isfinite(value)) return 0;
    return static_cast<uint8_t>(std::lround(std::clamp(value, 0.0f, 1.0f) * 255.0f));
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *machine, void *) {
    vm = machine;
    return chiaki_lib_init() == CHIAKI_ERR_SUCCESS ? JNI_VERSION_1_6 : JNI_ERR;
}
#define BRIDGE(name) Java_com_deploydulupulangnanti_psxremoteplay_connection_NativeBridge_##name
extern "C" JNIEXPORT jstring JNICALL BRIDGE(libraryVersion)(JNIEnv *env, jclass) {
    return env->NewStringUTF("chiaki-ng ca5379f4504af6e63cf5d32b5626de2425b2382e");
}
extern "C" JNIEXPORT jlong JNICALL BRIDGE(register)(JNIEnv *env, jclass, jstring hostString, jboolean ps5, jbyteArray accountId, jint pin, jobject callback) {
    const auto host = text(env, hostString);
    if (!validHost(host) || pin < 0 || pin > 99999999 || !callback) return 0;
    ChiakiRegistInfo info{};
    if (!bytes(env, accountId, info.psn_account_id, CHIAKI_PSN_ACCOUNT_ID_SIZE)) return 0;
    info.host = host.c_str();
    info.target = ps5 ? CHIAKI_TARGET_PS5_1 : CHIAKI_TARGET_PS4_10;
    info.pin = static_cast<uint32_t>(pin);
    info.broadcast = false;
    auto owner = std::make_shared<Registration>(env, callback);
    if (!owner->object || !owner->success || !owner->failure) return 0;
    if (chiaki_regist_start(&owner->regist, &owner->log, &info, registrationEvent, owner.get()) != CHIAKI_ERR_SUCCESS) return 0;
    owner->active = true;
    const auto handle = nextHandle.fetch_add(1);
    std::lock_guard<std::mutex> guard(registryMutex);
    registrations.emplace(handle, std::move(owner));
    return handle;
}
extern "C" JNIEXPORT void JNICALL BRIDGE(stopRegistration)(JNIEnv *, jclass, jlong handle) {
    const auto owner = findHandle(registrations, handle, true);
    if (!owner) return;
    std::lock_guard<std::mutex> guard(owner->operations);
    if (!owner->active) return;
    owner->active = false;
    chiaki_regist_stop(&owner->regist);
    // fini joins the registration worker before releasing the Java callback.
    chiaki_regist_fini(&owner->regist);
}
extern "C" JNIEXPORT jlong JNICALL BRIDGE(startSession)(JNIEnv *env, jclass, jstring hostString, jboolean ps5, jbyteArray registrationKey, jbyteArray morning, jint width, jint height, jint fps, jint bitrate, jboolean enableAudio, jobject callback) {
    const auto host = text(env, hostString);
    if (!validHost(host) || !callback || width < 320 || width > 1920 || height < 180 || height > 1080 || (fps != 30 && fps != 60) || bitrate < 1000 || bitrate > 100000) return 0;
    ChiakiConnectInfo info{};
    if (!bytes(env, registrationKey, info.regist_key, CHIAKI_SESSION_AUTH_SIZE) || !bytes(env, morning, info.morning, sizeof(info.morning))) return 0;
    info.host = host.c_str();
    info.ps5 = ps5;
    info.video_profile.width = static_cast<unsigned int>(width);
    info.video_profile.height = static_cast<unsigned int>(height);
    info.video_profile.max_fps = static_cast<unsigned int>(fps);
    info.video_profile.bitrate = static_cast<unsigned int>(bitrate);
    info.video_profile.codec = CHIAKI_CODEC_H264;
    info.video_profile_auto_downgrade = true;
    auto owner = std::make_shared<Session>(env, callback);
    if (!owner->object || !owner->connected || !owner->loginPin || !owner->quit || !owner->video || !owner->audioSettings || !owner->audio) return 0;
    if (chiaki_session_init(&owner->session, &info, &owner->log) != CHIAKI_ERR_SUCCESS) return 0;
    chiaki_session_set_event_cb(&owner->session, sessionEvent, owner.get());
    chiaki_session_set_video_sample_cb(&owner->session, videoSample, owner.get());
    if (enableAudio) {
        chiaki_opus_decoder_init(&owner->opus, &owner->log);
        owner->decoderInitialized = true;
        chiaki_opus_decoder_set_cb(&owner->opus, audioSettings, audioFrame, owner.get());
        ChiakiAudioSink sink{};
        chiaki_opus_decoder_get_sink(&owner->opus, &sink);
        chiaki_session_set_audio_sink(&owner->session, &sink);
    }
    if (chiaki_session_start(&owner->session) != CHIAKI_ERR_SUCCESS) {
        chiaki_session_fini(&owner->session);
        if (owner->decoderInitialized) chiaki_opus_decoder_fini(&owner->opus);
        return 0;
    }
    owner->active = true;
    const auto handle = nextHandle.fetch_add(1);
    std::lock_guard<std::mutex> guard(registryMutex);
    sessions.emplace(handle, std::move(owner));
    return handle;
}
extern "C" JNIEXPORT void JNICALL BRIDGE(stopSession)(JNIEnv *, jclass, jlong handle) {
    const auto owner = findHandle(sessions, handle, true);
    if (!owner) return;
    std::lock_guard<std::mutex> guard(owner->operations);
    if (!owner->active) return;
    owner->active = false;
    chiaki_session_stop(&owner->session);
    chiaki_session_join(&owner->session);
    chiaki_session_fini(&owner->session);
    if (owner->decoderInitialized) chiaki_opus_decoder_fini(&owner->opus);
}
extern "C" JNIEXPORT void JNICALL BRIDGE(setController)(JNIEnv *, jclass, jlong handle, jint buttons, jfloat lx, jfloat ly, jfloat rx, jfloat ry, jfloat lt, jfloat rt) {
    const auto owner = findHandle(sessions, handle);
    if (!owner) return;
    std::lock_guard<std::mutex> guard(owner->operations);
    if (!owner->active) return;
    ChiakiControllerState state;
    chiaki_controller_state_set_idle(&state);
    state.buttons = static_cast<uint32_t>(buttons) & 0xffffu;
    state.left_x = axis(lx); state.left_y = axis(ly);
    state.right_x = axis(rx); state.right_y = axis(ry);
    state.l2_state = trigger(lt); state.r2_state = trigger(rt);
    chiaki_session_set_controller_state(&owner->session, &state);
}
extern "C" JNIEXPORT void JNICALL BRIDGE(setLoginPin)(JNIEnv *env, jclass, jlong handle, jstring pinString) {
    const auto pin = text(env, pinString);
    if (pin.size() < 4 || pin.size() > 8 || pin.find_first_not_of("0123456789") != std::string::npos) return;
    const auto owner = findHandle(sessions, handle);
    if (!owner) return;
    std::lock_guard<std::mutex> guard(owner->operations);
    if (owner->active) chiaki_session_set_login_pin(&owner->session, reinterpret_cast<const uint8_t *>(pin.data()), pin.size());
}
