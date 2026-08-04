# AndroidCraft

**An on-device generative AI studio for Android.** Chat, generate images, synthesize
speech, and preview motion clips — all running locally on the phone, with models you
download and manage yourself. Every APK is built in CI, never on a device or laptop.

> Built and tuned with a Samsung Galaxy S25 Ultra class device in mind (flagship RAM +
> arm64), but it adapts to whatever hardware it runs on.

---

## What it does

| Tab | Capability | Engine |
| --- | --- | --- |
| **Chat** | Fully offline LLM chat | Google AI Edge / MediaPipe GenAI (LiteRT `.task`) |
| **Image** | Text-to-image | Bundled on-device procedural renderer (diffusion-ready) |
| **Voice** | Text-to-speech to a WAV file | Android on-device neural TTS |
| **Video** | Prompt-seeded motion clip | Experimental on-device frame renderer |
| **Models** | Download / delete models, device-fit checks, HF token | Download manager + `ModelRepository` |

Everything runs on the device. Nothing is sent to a server for inference.

## Architecture

```
com.androidcraft.studio
├── core/            DeviceCapabilities (RAM/ABI + fit checks), MediaSaver
├── data/            ModelSpec, ModelCatalog, ModelRepository, ModelDownloader
├── engine/          TextEngine (MediaPipe LLM), AudioEngine (TTS),
│                    ImageEngine (procedural), VideoEngine (experimental)
└── ui/              Compose screens + ViewModels per modality, theming, nav
```

- **Jetpack Compose + Material 3**, single-activity, bottom-nav across five destinations.
- **MVVM**: each screen has an `AndroidViewModel`; heavy engines and in-flight downloads
  live in an app-scoped `AppContainer` so they survive navigation.
- **Downloads** stream to a `.part` file and atomically rename on success, report progress
  through a `StateFlow`, support cancel/delete, and send a Hugging Face bearer token for
  gated models.
- **Engines are swappable.** Each modality is behind a small engine class with a stable
  method signature, so a stronger backend can be dropped in without touching the UI.

## Models

The catalog (`ModelCatalog.kt`) ships curated on-device models:

- **Text:** Gemma 3 1B Instruct (int4 / int8) and Hammer 2.1 1.5B — LiteRT `.task`
  bundles the MediaPipe GenAI runtime executes directly.
- **Voice:** the system TTS engine (no download).
- **Image / Video:** bundled renderers (no download).

Some text models are **gated** on Hugging Face. Paste a read token on the **Models**
screen (stored only on-device) to enable those downloads. You can also change or extend
the catalog by editing `ModelCatalog.kt` — any direct-download URL works.

### Making image generation photoreal

`ImageEngine.render()` is a deterministic procedural stand-in with the same signature a
real diffusion call would have. To enable photoreal output, drop in Stable Diffusion
LiteRT weights and replace the body of `render()` with a MediaPipe/LiteRT image-generation
call — the UI, prompt box, and gallery save all keep working unchanged.

## Building

### In CI (the intended path)

Pushing a commit that touches the app or its build config triggers
[`.github/workflows/android-build.yml`](.github/workflows/android-build.yml), which:

1. sets up JDK 17 + the Android SDK (platform 35, build-tools 35),
2. runs the unit tests,
3. assembles the **debug APK**, and
4. uploads it as the `androidcraft-debug-apk` build artifact.

Tagging a release (`git tag v0.1.0 && git push origin v0.1.0`) runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds the APKs and
publishes a GitHub Release with the installable artifact attached.

**Optional release signing** — add repository secrets and the release job signs the APK:

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of your `.jks` keystore |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Without them, the debug-signed APK is still published and is installable.

### Locally (optional)

```bash
./gradlew assembleDebug     # requires the Android SDK + ANDROID_HOME
```

The Gradle wrapper is pinned to 8.9; the toolchain is AGP 8.7.3 / Kotlin 2.0.21 /
compileSdk 35 / minSdk 26.

## Device requirements

- Android 8.0 (API 26) or newer, arm64 recommended.
- LLM chat is memory-heavy: ~3 GB RAM for the int4 1B model, more for larger ones. The
  **Models** screen shows a per-model "Runs well / Tight fit / Not enough RAM" verdict
  based on your device's reported memory.

## Roadmap

- Streaming token output for chat (MediaPipe async session API).
- Real on-device diffusion for the Image tab.
- MP4 export for the Video tab via `MediaCodec` / `MediaMuxer`.
- `llama.cpp` GGUF engine as an alternate text backend for broader model support.
