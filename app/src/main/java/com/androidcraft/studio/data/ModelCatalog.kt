package com.androidcraft.studio.data

private const val GB = 1024L * 1024L * 1024L
private const val MB = 1024L * 1024L

/**
 * Curated catalog of models that run fully on-device.
 *
 * Text models are LiteRT `.task` bundles that the MediaPipe GenAI runtime executes directly.
 * Some are gated on Hugging Face and require a personal access token (set it on the Models
 * screen); the download manager sends it as a bearer token when present.
 */
object ModelCatalog {

    val models: List<ModelSpec> = listOf(
        // ---- Text / Chat (MediaPipe GenAI, LiteRT .task) ----
        ModelSpec(
            id = "gemma3-1b-it-q4",
            displayName = "Gemma 3 1B Instruct",
            publisher = "Google · litert-community",
            modality = Modality.TEXT,
            runtime = Runtime.MEDIAPIPE_LLM,
            description = "Compact instruction-tuned chat model. Great default for phones; " +
                "fast, low memory, fully offline once downloaded.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            fileName = "gemma3-1b-it-q4.task",
            approxBytes = 555L * MB,
            minRamBytes = 3L * GB,
            quantization = "int4",
            gated = true,
        ),
        ModelSpec(
            id = "hammer-2p1-1p5b",
            displayName = "Hammer 2.1 (1.5B)",
            publisher = "litert-community",
            modality = Modality.TEXT,
            runtime = Runtime.MEDIAPIPE_LLM,
            description = "Function-calling / agentic small model in LiteRT format. " +
                "Good for structured, tool-style responses on device.",
            downloadUrl = "https://huggingface.co/litert-community/Hammer2.1-1.5b/resolve/main/" +
                "Hammer2.1-1.5b_seq128_q8_ekv1280.task",
            fileName = "hammer-2p1-1p5b-q8.task",
            approxBytes = 1600L * MB,
            minRamBytes = 4L * GB,
            quantization = "int8",
            gated = false,
        ),
        ModelSpec(
            id = "gemma3-1b-it-q8",
            displayName = "Gemma 3 1B Instruct (int8)",
            publisher = "Google · litert-community",
            modality = Modality.TEXT,
            runtime = Runtime.MEDIAPIPE_LLM,
            description = "Higher-quality int8 build of Gemma 3 1B. Needs a bit more RAM " +
                "than the int4 variant; strong quality on flagship devices.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
                "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
            fileName = "gemma3-1b-it-q8.task",
            approxBytes = 1050L * MB,
            minRamBytes = 4L * GB,
            quantization = "int8",
            gated = true,
        ),

        // ---- Audio / Voice ----
        ModelSpec(
            id = "system-tts",
            displayName = "System Neural Voices",
            publisher = "Android",
            modality = Modality.AUDIO,
            runtime = Runtime.ANDROID_TTS,
            description = "Uses the on-device text-to-speech engine already installed on your " +
                "phone (Samsung/Google). No download needed — synthesizes speech to a WAV file.",
            downloadUrl = null,
            fileName = "",
            approxBytes = 0,
            minRamBytes = 0,
        ),

        // ---- Image ----
        ModelSpec(
            id = "sd15-diffusion",
            displayName = "Stable Diffusion 1.5",
            publisher = "Runwayml · single-file",
            modality = Modality.IMAGE,
            runtime = Runtime.DIFFUSION,
            description = "Full Stable Diffusion 1.5 weights for the on-device diffusion engine " +
                "(stable-diffusion.cpp). Real text-to-image, fully offline. Large download; " +
                "generation is CPU-bound so expect tens of seconds per image on a flagship. " +
                "You can also add a smaller quantized GGUF via ‘Add by URL’.",
            downloadUrl = "https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/" +
                "resolve/main/v1-5-pruned-emaonly.safetensors",
            fileName = "sd15.safetensors",
            approxBytes = 4265L * MB,
            minRamBytes = 6L * GB,
            quantization = "fp16",
        ),
        ModelSpec(
            id = "sd15-onnx",
            displayName = "Stable Diffusion 1.5 (ONNX)",
            publisher = "ONNX Runtime bundle",
            modality = Modality.IMAGE,
            runtime = Runtime.DIFFUSION,
            description = "ONNX export of Stable Diffusion 1.5 (text encoder + UNet + VAE + " +
                "tokenizer) for the ONNX Runtime engine. Downloaded as a .zip and extracted " +
                "on-device. Large & experimental; if the link is unavailable, host your own " +
                "Optimum ONNX export as sd15-onnx.zip.",
            downloadUrl = "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/onnx.zip",
            fileName = "sd15-onnx.zip",
            approxBytes = 3400L * MB,
            minRamBytes = 6L * GB,
            quantization = "fp32",
            archive = true,
            extractDir = "sd15-onnx",
        ),
        ModelSpec(
            id = "procedural-image",
            displayName = "Procedural Preview Renderer",
            publisher = "AndroidCraft",
            modality = Modality.IMAGE,
            runtime = Runtime.BUILTIN,
            description = "Bundled deterministic renderer that turns a prompt into abstract " +
                "art on-device with zero download. Placeholder for a full diffusion pipeline " +
                "(drop in Stable Diffusion LiteRT weights to enable photoreal generation).",
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
            description = "Generates an animated, prompt-seeded motion clip on-device as a " +
                "preview of the video pipeline. True on-device text-to-video is not yet " +
                "practical on mobile hardware; this scaffolds the flow.",
            downloadUrl = null,
            fileName = "",
            approxBytes = 0,
            minRamBytes = 0,
        ),
    )

    fun byId(id: String): ModelSpec? = models.firstOrNull { it.id == id }

    fun forModality(modality: Modality): List<ModelSpec> = models.filter { it.modality == modality }
}
