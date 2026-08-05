package com.androidcraft.studio.data

private const val GB = 1024L * 1024L * 1024L
private const val MB = 1024L * 1024L

/**
 * Curated catalog of models that run fully on-device and download directly in the app — no
 * external links, no account, and no Hugging Face token required. Every entry is a vetted,
 * ungated, single-file (or bundled) model, and the Models screen matches them to the device's RAM.
 *
 * Text models are LiteRT `.task` bundles run by MediaPipe GenAI (the same ones Google's AI Edge
 * Gallery ships). Image models are single-file GGUF weights run by the native stable-diffusion.cpp
 * engine. All URLs resolve without authentication.
 */
object ModelCatalog {

    val models: List<ModelSpec> = listOf(
        // ---- Text / Chat (MediaPipe GenAI, LiteRT .task, ungated) ----
        ModelSpec(
            id = "gemma3-1b-it-q4",
            displayName = "Gemma 3 1B",
            publisher = "Google · litert-community",
            modality = Modality.TEXT,
            runtime = Runtime.MEDIAPIPE_LLM,
            description = "Compact, fast chat model — the best default for phones. Low memory, " +
                "fully offline once downloaded. No account needed.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            fileName = "gemma3-1b-it-q4.task",
            approxBytes = 555L * MB,
            minRamBytes = 3L * GB,
            quantization = "int4",
        ),
        ModelSpec(
            id = "qwen25-1p5b-q8",
            displayName = "Qwen 2.5 1.5B",
            publisher = "Alibaba · litert-community",
            modality = Modality.TEXT,
            runtime = Runtime.MEDIAPIPE_LLM,
            description = "Higher-quality instruction-tuned chat model. Great on flagship phones " +
                "with 6 GB+ RAM. Fully offline, no account needed.",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/" +
                "main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen25-1p5b-q8.task",
            approxBytes = 1600L * MB,
            minRamBytes = 4L * GB,
            quantization = "int8",
        ),

        // ---- Audio / Voice ----
        ModelSpec(
            id = "system-tts",
            displayName = "System Neural Voices",
            publisher = "Android",
            modality = Modality.AUDIO,
            runtime = Runtime.ANDROID_TTS,
            description = "Uses the on-device text-to-speech engine already installed on your " +
                "phone. No download needed — synthesizes speech to a WAV file.",
            downloadUrl = null,
            fileName = "",
            approxBytes = 0,
            minRamBytes = 0,
        ),

        // ---- Image (native stable-diffusion.cpp, single-file GGUF, ungated) ----
        ModelSpec(
            id = "sd15-q8-emaonly",
            displayName = "Stable Diffusion 1.5 (Q8)",
            publisher = "second-state · GGUF",
            modality = Modality.IMAGE,
            runtime = Runtime.DIFFUSION,
            description = "Real text-to-image, fully offline via the on-device diffusion engine. " +
                "Q8 quantized single file — best quality/size balance. One-tap download.",
            downloadUrl = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/resolve/" +
                "main/stable-diffusion-v1-5-pruned-emaonly-Q8_0.gguf",
            fileName = "sd15-q8-emaonly.gguf",
            approxBytes = 1700L * MB,
            minRamBytes = 4L * GB,
            quantization = "Q8_0",
        ),
        ModelSpec(
            id = "sd15-q8-full",
            displayName = "Stable Diffusion 1.5 (Q8, full)",
            publisher = "kostakoff · GGUF",
            modality = Modality.IMAGE,
            runtime = Runtime.DIFFUSION,
            description = "Full (non-EMA) Q8 Stable Diffusion 1.5 for the on-device diffusion " +
                "engine. Slightly larger; alternative to the EMA build. One-tap download.",
            downloadUrl = "https://huggingface.co/kostakoff/stable-diffusion-v1-5-GGUF/resolve/" +
                "main/v1-5-pruned_Q8_0.gguf",
            fileName = "sd15-q8-full.gguf",
            approxBytes = 2000L * MB,
            minRamBytes = 5L * GB,
            quantization = "Q8_0",
        ),
        ModelSpec(
            id = "procedural-image",
            displayName = "Procedural Preview Renderer",
            publisher = "AndroidCraft",
            modality = Modality.IMAGE,
            runtime = Runtime.BUILTIN,
            description = "Bundled deterministic renderer that turns a prompt into abstract art " +
                "on-device with zero download — instant, always available.",
            downloadUrl = null,
            fileName = "",
            approxBytes = 0,
            minRamBytes = 0,
        ),

        // ---- Video ----
        ModelSpec(
            id = "procedural-video",
            displayName = "Motion Preview (Experimental)",
            publisher = "AndroidCraft",
            modality = Modality.VIDEO,
            runtime = Runtime.BUILTIN,
            description = "Generates an animated, prompt-seeded motion clip on-device and exports " +
                "a real MP4. True on-device text-to-video isn't practical on phones yet; this " +
                "scaffolds the flow.",
            downloadUrl = null,
            fileName = "",
            approxBytes = 0,
            minRamBytes = 0,
        ),
    )

    fun byId(id: String): ModelSpec? = models.firstOrNull { it.id == id }

    fun forModality(modality: Modality): List<ModelSpec> = models.filter { it.modality == modality }
}
