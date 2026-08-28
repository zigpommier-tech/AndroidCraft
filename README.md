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
| **Image** | Text-to-image, **selectable engine** | Procedural (instant) **or real diffusion via stable-diffusion.cpp** |
| **Voice** | Text-to-speech to a WAV file | Android on-device neural TTS |
| **Video** | Prompt-seeded motion clip → **MP4** | On-device frame renderer + MediaCodec encoder |
| **Models** | Download / delete, device-fit checks, **add any model by URL** | Download manager + `ModelRepository` |

Every generated image, voice clip, and video can be **saved to the gallery/Music/Movies
and shared**. Model downloads need **no login** by default (an optional Hugging Face token
covers gated repos).

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

Everything is **one-tap from inside the app** — no account, no token, no links to find,
and no manual URL entry. The catalog (`ModelCatalog.kt`) ships only vetted, **ungated,
direct-download** models, and the **Models** screen sorts them by how well they fit your
phone's RAM and badges the best pick with **"Best for your device."**

- **Text (chat):** Gemma 3 1B (int4) and Qwen 2.5 1.5B (int8) — LiteRT `.task` bundles the
  MediaPipe GenAI runtime executes directly (the same models Google's AI Edge Gallery ships).
- **Image:** Stable Diffusion 1.5 as single-file **GGUF** weights (Q8) for the native
  diffusion engine.
- **Voice:** the system TTS engine (no download).
- **Image/Video previews:** bundled procedural renderers (no download).

### Image generation engines

The Image tab has a **method selector**:

- **Procedural** — instant, no download, abstract art (always available).
- **Diffusion · SD.cpp** — real Stable Diffusion running fully on-device via a native
  [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) library (arm64,
  compiled in CI). One-tap download a Stable Diffusion GGUF model from the Models tab, then
  generate offline. CPU-bound, so expect tens of seconds per image on a flagship.
- **Diffusion · ONNX** — a second diffusion backend (ONNX Runtime, full SD 1.5 pipeline). Only
  shown when an ONNX bundle is present, so the picker stays limited to engines you can use now.

The native engine lives in `app/src/main/cpp` (`sdjni.cpp` JNI bridge + the
`stable-diffusion.cpp` submodule); the whole pipeline — tokenizer, scheduler, UNet, VAE —
runs in C++.

## Building

### In CI (the intended path)

Pushing a commit that touches the app or its build config triggers
[`.github/workflows/android-build.yml`](.github/workflows/android-build.yml), which:

1. initialises the native submodules (`stable-diffusion.cpp` + `ggml`),
2. sets up JDK 17 + the Android SDK (platform 35, build-tools 35, **NDK 27 + CMake**),
3. runs the unit tests,
4. compiles the native diffusion library and assembles the **debug APK**, and
5. uploads it as the `androidcraft-debug-apk` build artifact.

> Cloning the repo for local builds needs `git clone --recurse-submodules` (or
> `git submodule update --init` after cloning) so the native sources are present.

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
- Second diffusion backend: ONNX Runtime SD 1.5 pipeline behind the existing selector.
- `llama.cpp` GGUF engine as an alternate text backend for broader model support.

Done: real on-device diffusion (stable-diffusion.cpp), MP4 video export, save/share for
all media, add-model-by-URL, optional (not required) Hugging Face token.

## Contributing

Issues and PRs welcome. Test changes on a physical device when touching native or engine code.

## License

MIT
