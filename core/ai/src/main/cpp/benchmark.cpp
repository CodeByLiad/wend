#include <jni.h>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

namespace {
struct State {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    std::atomic<bool> cancelled{false};
    ~State() { if (context) llama_free(context); if (model) llama_model_free(model); }
};
void fail(JNIEnv *env) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native model operation failed"); }
bool abort_decode(void *data) { return static_cast<State *>(data)->cancelled.load(); }
void quiet_log(enum ggml_log_level, const char *, void *) {}
std::once_flag initialized;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeLoad(JNIEnv *env, jobject, jstring path) {
    std::call_once(initialized, [] { llama_log_set(quiet_log, nullptr); llama_backend_init(); });
    auto *state = new State;
    const char *name = env->GetStringUTFChars(path, nullptr);
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    state->model = llama_model_load_from_file(name, model_params);
    env->ReleaseStringUTFChars(path, name);
    if (!state->model) { delete state; fail(env); return 0; }
    auto params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 512;
    params.n_threads = 4;
    params.n_threads_batch = 4;
    params.abort_callback = abort_decode;
    params.abort_callback_data = state;
    state->context = llama_init_from_model(state->model, params);
    if (!state->context) { delete state; fail(env); return 0; }
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeGenerate(JNIEnv *env, jobject, jlong handle, jstring prompt) {
    auto *state = reinterpret_cast<State *>(handle);
    if (!state) { fail(env); return nullptr; }
    state->cancelled.store(false);
    llama_memory_clear(llama_get_memory(state->context), true);
    const auto *vocab = llama_model_get_vocab(state->model);
    const char *text = env->GetStringUTFChars(prompt, nullptr);
    const int bytes = env->GetStringUTFLength(prompt);
    std::vector<llama_token> tokens(bytes + 16);
    int size = llama_tokenize(vocab, text, bytes, tokens.data(), tokens.size(), true, true);
    env->ReleaseStringUTFChars(prompt, text);
    if (size <= 0 || size > 512) { fail(env); return nullptr; }
    tokens.resize(size);
    if (llama_decode(state->context, llama_batch_get_one(tokens.data(), size)) != 0) { fail(env); return nullptr; }
    auto *sampler = llama_sampler_init_greedy();
    int count = 0;
    const auto started = std::chrono::steady_clock::now();
    while (count < 128 && !state->cancelled.load()) {
        llama_token token = llama_sampler_sample(sampler, state->context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        ++count;
        if (llama_decode(state->context, llama_batch_get_one(&token, 1)) != 0) {
            llama_sampler_free(sampler); fail(env); return nullptr;
        }
    }
    llama_sampler_free(sampler);
    const auto nanos = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - started).count();
    jlong values[] = {count, nanos};
    auto result = env->NewLongArray(2);
    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeCancel(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<State *>(handle)->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeClose(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<State *>(handle);
}
