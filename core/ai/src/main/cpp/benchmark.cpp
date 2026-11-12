#include <jni.h>
#include <atomic>
#include <chrono>
#include <mutex>
#include <memory>
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
Java_com_jinatra_wend_ai_LlamaEngine_nativeLoad(JNIEnv *env, jobject, jstring path, jint context_tokens) {
    if (context_tokens < 512 || context_tokens > 32768) { fail(env); return 0; }
    std::call_once(initialized, [] { llama_log_set(quiet_log, nullptr); llama_backend_init(); });
    auto *state = new State;
    const char *name = env->GetStringUTFChars(path, nullptr);
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    state->model = llama_model_load_from_file(name, model_params);
    env->ReleaseStringUTFChars(path, name);
    if (!state->model) { delete state; fail(env); return 0; }
    auto params = llama_context_default_params();
    params.n_ctx = context_tokens;
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

// JSON syntax constraints only. The Kotlin decoder enforces command fields and IDs.
static const char *json_grammar = R"gbnf(
root ::= object
object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? "}" ws
value ::= object | array | string | number | ("true" | "false" | "null") ws
array ::= "[" ws (value ("," ws value)*)? "]" ws
string ::= "\"" ([^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\"" ws
number ::= "-"? ("0" | [1-9] [0-9]*) ("." [0-9]+)? ([eE] [+-]? [0-9]+)? ws
ws ::= [ \t\n\r]*
)gbnf";

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeComplete(JNIEnv *env, jobject, jlong handle, jbyteArray prompt, jint max_tokens, jboolean constrained) {
    auto *state = reinterpret_cast<State *>(handle);
    const int bytes = env->GetArrayLength(prompt);
    if (!state || bytes <= 0 || bytes > 200000 || max_tokens <= 0 || max_tokens >= static_cast<int>(llama_n_ctx(state->context))) { fail(env); return nullptr; }
    state->cancelled.store(false);
    llama_memory_clear(llama_get_memory(state->context), true);
    std::string text(bytes, '\0');
    env->GetByteArrayRegion(prompt, 0, bytes, reinterpret_cast<jbyte *>(text.data()));
    if (text.find('\0') != std::string::npos) { fail(env); return nullptr; }
    const char *tmpl = llama_model_chat_template(state->model, nullptr);
    if (!tmpl) { fail(env); return nullptr; }
    llama_chat_message message{"user", text.c_str()};
    std::vector<char> formatted(bytes * 2 + 4096);
    int length = llama_chat_apply_template(tmpl, &message, 1, true, formatted.data(), formatted.size());
    if (length > static_cast<int>(formatted.size())) {
        formatted.resize(length);
        length = llama_chat_apply_template(tmpl, &message, 1, true, formatted.data(), formatted.size());
    }
    if (length <= 0 || length > 1000000) { fail(env); return nullptr; }
    const auto *vocab = llama_model_get_vocab(state->model);
    std::vector<llama_token> tokens(length + 16);
    int size = llama_tokenize(vocab, formatted.data(), length, tokens.data(), tokens.size(), true, true);
    if (size <= 0 || size + max_tokens > static_cast<int>(llama_n_ctx(state->context))) { fail(env); return nullptr; }
    for (int offset = 0; offset < size; offset += 512) {
        if (state->cancelled.load() || llama_decode(state->context, llama_batch_get_one(tokens.data() + offset, std::min(512, size - offset))) != 0) { fail(env); return nullptr; }
    }
    auto *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(chain, llama_sampler_free);
    if (constrained) {
        auto *grammar = llama_sampler_init_grammar(vocab, json_grammar, "root");
        if (!grammar) { fail(env); return nullptr; }
        llama_sampler_chain_add(chain, grammar);
    }
    llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    std::string output;
    int count = 0;
    const auto started = std::chrono::steady_clock::now();
    while (count < max_tokens && !state->cancelled.load()) {
        llama_token token = llama_sampler_sample(chain, state->context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) {
            piece.resize(-n);
            n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (n < 0 || output.size() + n > 131072) { fail(env); return nullptr; }
        output.append(piece.data(), n);
        ++count;
        if (llama_decode(state->context, llama_batch_get_one(&token, 1)) != 0) { fail(env); return nullptr; }
    }
    if (state->cancelled.load()) { fail(env); return nullptr; }
    const auto nanos = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - started).count();
    std::vector<jbyte> result(16 + output.size());
    for (int i = 0; i < 8; ++i) {
        result[i] = static_cast<jbyte>((static_cast<uint64_t>(count) >> (56 - i * 8)) & 255);
        result[8 + i] = static_cast<jbyte>((static_cast<uint64_t>(nanos) >> (56 - i * 8)) & 255);
    }
    std::copy(output.begin(), output.end(), result.begin() + 16);
    auto array = env->NewByteArray(result.size());
    if (array) env->SetByteArrayRegion(array, 0, result.size(), result.data());
    return array;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeCancel(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<State *>(handle)->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_com_jinatra_wend_ai_LlamaEngine_nativeClose(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<State *>(handle);
}
