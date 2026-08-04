// JNI bridge from AndroidCraft's NativeDiffusionGenerator to stable-diffusion.cpp.
// The entire diffusion pipeline (tokenizer, scheduler, UNet, VAE) runs in the C++ library;
// this file only marshals a prompt in and ARGB pixels out. Runs fully on-device (CPU).

#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstdlib>
#include <vector>

#include "stable-diffusion.h"

#define LOG_TAG "sdjni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jintArray JNICALL
Java_com_androidcraft_studio_engine_image_NativeDiffusionGenerator_nativeTxt2Img(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jModelPath,
    jstring jPrompt,
    jint steps,
    jlong seed,
    jint width,
    jint height) {

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);

    sd_ctx_params_t ctxParams;
    sd_ctx_params_init(&ctxParams);
    ctxParams.model_path = modelPath;

    sd_ctx_t* ctx = new_sd_ctx(&ctxParams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    if (ctx == nullptr) {
        LOGE("new_sd_ctx failed (model load)");
        env->ReleaseStringUTFChars(jPrompt, prompt);
        return nullptr;
    }

    sd_img_gen_params_t genParams;
    sd_img_gen_params_init(&genParams);
    genParams.prompt = prompt;
    genParams.negative_prompt = "";
    genParams.width = width;
    genParams.height = height;
    genParams.seed = static_cast<int64_t>(seed);
    genParams.batch_count = 1;
    genParams.sample_params.sample_steps = steps;

    sd_image_t* images = nullptr;
    int numImages = 0;
    bool ok = generate_image(ctx, &genParams, &images, &numImages);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    if (!ok || images == nullptr || numImages < 1) {
        LOGE("generate_image failed");
        free_sd_ctx(ctx);
        return nullptr;
    }

    const sd_image_t& img = images[0];
    const uint32_t w = img.width;
    const uint32_t h = img.height;
    const uint32_t c = img.channel;
    const int count = static_cast<int>(w * h);

    jintArray result = env->NewIntArray(count);
    if (result == nullptr) {
        free_sd_images(images, numImages);
        free_sd_ctx(ctx);
        return nullptr;
    }

    std::vector<jint> pixels(count);
    for (int i = 0; i < count; ++i) {
        const uint8_t r = img.data[i * c + 0];
        const uint8_t g = c > 1 ? img.data[i * c + 1] : r;
        const uint8_t b = c > 2 ? img.data[i * c + 2] : r;
        pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
    env->SetIntArrayRegion(result, 0, count, pixels.data());

    free_sd_images(images, numImages);
    free_sd_ctx(ctx);
    LOGI("generated %dx%d image", w, h);
    return result;
}
