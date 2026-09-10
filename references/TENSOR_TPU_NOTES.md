# Running LiteRT-LM models on the Pixel Tensor TPU — developer notes

Status: verified working on the device below, from a debug build of this app.

|         |                                                                                                                |
|---------|----------------------------------------------------------------------------------------------------------------|
| Device  | Pixel 10 (`ro.product.model = Pixel 10`)                                                                       |
| SoC     | `ro.soc.model = Tensor G5`, `ro.soc.manufacturer = Google`                                                     |
| Build   | `ro.build.id = CP2A.260805.005`, Android 17                                                                    |
| Runtime | `com.google.ai.edge.litertlm:litertlm-android:0.17.0`                                                          |
| Model   | `litert-community/gemma-4-E2B-it-litert-lm` → `gemma-4-E2B-it_Google_Tensor_G5.litertlm` (3,113,545,589 bytes) |

Terminology: there is **no TPU delegate** in LiteRT-LM. The Tensor TPU is reached through
`Backend.NPU`. "TPU" is only a UI label Google AI Edge Gallery substitutes for "NPU" on Pixel
hardware. Everything below says NPU when it means the API and TPU when it means the silicon.

---

## 1. The one non-obvious requirement: the dispatch library

**The `litertlm-android` AAR does not contain an NPU dispatch backend.** Verified by unzipping the
artifacts:

- `0.17.0` ships exactly one native library: `jni/{arm64-v8a,x86_64}/liblitertlm_jni.so`
- `0.11.0` ships `liblitertlm_jni.so`, `libLiteRt.so`, `libLiteRtClGlAccelerator.so`

Neither includes a dispatch backend. At engine init, LiteRT scans the directory passed as
`Backend.NPU(nativeLibraryDir = …)` for a `libLiteRt*Dispatch*.so` exporting `LiteRtDispatchGetApi`.
On Google Tensor that library is:

```
libLiteRtDispatch_GoogleTensor.so     (~314 KB, arm64-v8a only)
```

It is a thin shim over the platform Edge TPU libraries (`libedgetpu_*.so`), which is why the
`uses-native-library` manifest entries in §3 are mandatory alongside it.

**Without it**, the failure looks like this and is easy to misread as a memory or context-length
problem:

```
E litert  [litert_dispatch.cc:123] No dispatch library found in /data/app/…/lib/arm64
E litert  [dispatch_delegate.cc:131] Failed to initialize Dispatch API: …
I tflite  Replacing 2 out of 24 node(s) with delegate (TfLiteXNNPackDelegate) …
E tflite  Node number 1 (DELEGATE) failed to prepare.
E litert  [litert_compiled_model.cc:164] Failed to allocate tensors
E native  llm_litert_npu_compiled_model_executor.cc:457] INTERNAL: RET_CHECK failure …
          Inference warmup run for Text Decoder (prefill) failed. Failed to invoke the
          compiled model └ Failed to allocate tensors
```

Every dispatch signature fails, TFLite silently falls back to XNNPACK for the aux subgraphs, and
the darwinn-compiled `main` subgraph then cannot prepare. The RET_CHECK is the symptom, not the
cause.

### Where the library comes from

It is **not** published on Google Maven. Checked `com.google.ai.edge.litert` (artifacts: `litert`,
`litert-api`, `litert-gpu`, `litert-gpu-api`, `litert-metadata`, `litert-support`,
`litert-support-api`) and `com.google.ai.edge.litertlm` (`litertlm`, `litertlm-android`,
`litertlm-jvm`) — no dispatch or NPU artifact in either group index.

Google AI Edge Gallery delivers it as a **Play install-time conditional feature module**:

```
com.google.ai.edge.gallery
├── base.apk
├── split_google_tensor_runtime.apk                      ← module stub, hasCode=false
└── split_google_tensor_runtime.config.arm64_v8a.apk     ← lib/arm64-v8a/libLiteRtDispatch_GoogleTensor.so
```

The module manifest declares:

```xml
<dist:module dist:instant="false" dist:title="@string/…">
  <dist:delivery>
    <dist:install-time>
      <dist:conditions>
        <dist:device-groups>
          <dist:device-group dist:name="Google_LGA101"/>   <!-- Pixel 10 device group -->
        </dist:device-groups>
      </dist:conditions>
    </dist:install-time>
  </dist:delivery>
  <dist:fusing dist:include="true"/>
</dist:module>
<application android:hasCode="false" android:extractNativeLibs="true"/>
```

Two things to carry into production: delivery is **install-time conditional on a device group**, so
non-Pixel-10 installs never download the payload; and `extractNativeLibs="true"` is set explicitly,
which matters for §2.

### For development

Extract it from a Gallery install on the target device:

```bash
adb shell pm path com.google.ai.edge.gallery
adb pull /data/app/~~<hash>/com.google.ai.edge.gallery-<hash>/split_google_tensor_runtime.config.arm64_v8a.apk
unzip -j split_google_tensor_runtime.config.arm64_v8a.apk \
  lib/arm64-v8a/libLiteRtDispatch_GoogleTensor.so \
  -d app/src/main/jniLibs/arm64-v8a/
```

> **Before shipping:** this is a Google-proprietary binary extracted from another app's APK. It is
> fine for local development; there is no basis to assume it is redistributable. Obtain it through
> whatever official LiteRT NPU / Tensor runtime channel applies to your project (Play feature
> delivery, an AI Pack, or an NPU partner program) and confirm the licence before publishing.

---

## 2. Gradle

```kotlin
// gradle/libs.versions.toml
litertlm = "0.17.0"
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
```

```kotlin
// app/build.gradle.kts
android {
    packaging {
        jniLibs {
            // LiteRT dlopens libLiteRtDispatch_*.so by path out of nativeLibraryDir, so it must be
            // a real extracted file rather than a compressed APK entry. Gallery's feature module
            // sets android:extractNativeLibs="true" for the same reason.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.litertlm.android)
}
```

Verify after install — the library must be physically present:

```bash
P=$(adb shell pm path <appId> | head -1 | sed 's|package:||;s|/base.apk||')
adb shell ls -la $P/lib/arm64/
# expect: libLiteRtDispatch_GoogleTensor.so, liblitertlm_jni.so
```

Note the runtime is arm64-only for the TPU path. `liblitertlm_jni.so` also ships x86_64 (emulator,
CPU/GPU only); there is no x86_64 dispatch backend.

---

## 3. Manifest

```xml
<uses-feature android:name="android.hardware.npu" android:required="false" />

<application …>
    <!-- Platform Edge TPU libraries the dispatch backend dlopens. Apps targeting SDK 31+
         cannot load non-NDK system libraries without declaring them. -->
    <uses-native-library android:name="libedgetpu_litert.so"        android:required="false" />
    <uses-native-library android:name="libedgetpu_tachyon.google.so" android:required="false" />
    <uses-native-library android:name="libedgetpu_client.google.so"  android:required="false" />
    <uses-native-library android:name="libedgetpu_util.so"           android:required="false" />
    <uses-native-library android:name="libvndksupport.so"            android:required="false" />
    <uses-native-library android:name="libOpenCL.so"                 android:required="false" />
    <uses-native-library android:name="libcdsprpc.so"                android:required="false" />
</application>
```

`libOpenCL.so` is for the GPU backend and `libcdsprpc.so` is Qualcomm's DSP RPC — neither is used on
Tensor, but they are harmless and keep a single manifest working across NPU vendors.

---

## 4. The model file

Use the **SoC-specific** build. The generic `gemma-4-E2B-it.litertlm` in Gallery's allowlist is a
GPU/CPU model; the TPU build is a separate file:

```
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm
```

Header inspection (first 256 KB is enough — the container lists its sections up front) confirms what
kind of file it is:

| Marker | Meaning |
|---|---|
| `tf_lite_aux`, `tf_lite_prefill_decode` | precompiled NPU model → `Backend.NPU` is the correct backend |
| `darwinn_external_parameter_*`, `Tensor_G5` | compiled for the Google Edge TPU, this SoC generation |
| `prefill_128` (only prefill signature) | fixed 128-token prefill chunking, baked at conversion |
| `tf_lite_mtp_aux`, `tf_lite_mtp_drafter` | multi-token prediction / speculative decoding graphs |
| `tf_lite_vision_encoder`, `tf_lite_audio_encoder_hw` | multimodal sections present |

Useful check without downloading 3 GB:

```bash
curl -sL -r 0-262143 "<model-url>" -o head.bin && strings -a head.bin | head -40
```

Storage rules:

- App-private storage only — `getExternalFilesDir(null)` or `filesDir`. The engine mmaps the file;
  it is never streamed.
- Never a content URI, `/sdcard`, or `Environment.getExternalStorageDirectory()`.
- `getExternalFilesDir()` is FUSE-mounted. That is fine on Tensor G5 (verified), but see §7 — it is
  a known problem on some builds, and it is why `cacheDir` is pinned to internal storage below.
- A different SoC needs a different file. Do not ship one model file for all devices; key the
  download off `Build.SOC_MODEL` the way Gallery's `socToModelFiles` allowlist field does.

---

## 5. Engine configuration

```kotlin
Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)   // dev builds only

val supportsSpeculativeDecoding =
    Capabilities(modelFile.absolutePath).use { it.hasSpeculativeDecodingSupport() }

val engineConfig = EngineConfig(
    modelPath = modelFile.absolutePath,
    backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
    maxNumTokens = 1024,
    cacheDir = context.filesDir.absolutePath,
)

val previous = ExperimentalFlags.enableSpeculativeDecoding
ExperimentalFlags.enableSpeculativeDecoding = supportsSpeculativeDecoding
val engine = Engine(engineConfig)
try { engine.initialize() } finally {
    ExperimentalFlags.enableSpeculativeDecoding = previous
}

val conversation = engine.createConversation(
    ConversationConfig(
        systemInstruction = Contents.of("…"),
        samplerConfig = null,          // required on NPU
    )
)
```

Per-field notes:

- **`nativeLibraryDir`** — not optional. The runtime logs
  `You should provide the DispatchLibraryDir option to use NPU` when it is missing.
- **`samplerConfig = null`** — mandatory on the NPU backend. topK/topP/temperature are not
  configurable; the accelerator samples internally. Gallery enforces the same rule
  (`LlmChatModelHelper.kt`), and its NPU config builder `createLlmChatConfigsForNpuModel()` exposes
  only maxTokens + accelerator.
- **`maxNumTokens`** — KV cache size, allocated on top of the mmapped weights. 1024 matches what
  Gallery feeds an imported model. This is **not** the cause of allocate-tensors failures: the NPU
  executor reconciles a mismatch itself and logs
  `Passed in max_num_tokens (X) with NPU model max sequence length: (Y)` followed by
  `Overriding executor settings max_num_tokens (…)`. Do not leave it null on a 32K-context model.
- **`cacheDir`** — defaults to the model's directory. With the model on external storage that puts
  the NPU JIT cache on FUSE; pin it to `filesDir`.
- **`ExperimentalFlags.enableSpeculativeDecoding`** — a nullable `Boolean` where null means "auto".
  Drive it from `Capabilities`, and restore the previous value afterwards since it is process-global.
  For this model the probe returns `true`. If MTP proves unstable on the NPU path, hardcoding
  `false` is a clean one-line A/B.
- **`visionBackend` / `audioBackend`** — leave null for text-only. The container has vision/audio
  sections, but enabling them selects separate executors that have not been validated here.

Lifecycle: close `Conversation` then `Engine` before initializing another model. Two engines alive
at once means two models' worth of native memory.

---

## 6. Verifying a good load

Success — these lines mean the TPU is genuinely executing:

```
I litert  [dispatch_delegate.cc:98]  DispatchDelegate::DispatchDelegate
I native  SouthBound context created.
I Darwinn [/dev/edgetpu][core0] … Firmware Build Label: lga_tpu_<date>
```

`Darwinn` is logged by the platform (pid 1180 on the test device), not the app, so filter logcat by
tag rather than by app pid when checking whether the TPU came up.

Benign on a healthy load — do not chase these:

- `Failed to apply compiler plugins: No compiler plugin found` — expected; the model is
  precompiled, so there is nothing to JIT.
- `Accelerator returned null hooks.` / `No Google Tensor privileged runtime options found` —
  privileged options are a first-party-only path.
- `Replacing N out of M node(s) with delegate (TfLiteXNNPackDelegate)` on `*_mask`, `*_rope`,
  `*_embedder` subgraphs — those aux graphs legitimately run on CPU; only the `main` subgraph goes
  to the TPU.

Failure signatures worth grepping for:

| Log line | Meaning |
|---|---|
| `No dispatch library found in …` | §1 — dispatch backend missing or not extracted |
| `Node number 1 (DELEGATE) failed to prepare` | downstream of the above |
| `NPU backend requested, but TF_LITE_AUX is not packaged in the model` | wrong model file (GPU build, not the SoC build) |
| `NPU accelerator could not be loaded and registered` | platform Edge TPU libs unreachable — check §3 |
| `Detected NPU prefill size: ` | sanity check, should be 128 for this model |

---

## 7. Adding Pixel 11 support

Not yet attempted — no Pixel 11 hardware was available. Treat this as a checklist, not a
verified recipe. Every item needs confirming on a real device.

**7.1 A second model file.** The SoC build is generation-specific. `liblitertlm_jni.so` contains
target strings for `Tensor G3`, `Tensor G4`, `Tensor G5` and `Tensor G6`, so a Tensor G6 build is
anticipated by the runtime — but confirm the actual filename on the model repo rather than
assuming `…_Google_Tensor_G6.litertlm`. Replace the hardcoded URL/filename constants with a
`Build.SOC_MODEL` → (filename, url, size) lookup, mirroring Gallery's `socToModelFiles` structure.
If no entry matches the device's SoC, do not fall back to another SoC's file — fall back to CPU or
refuse to offer the TPU option, which is what Gallery does when filtering NPU-only models
(`ModelManagerViewModel.kt:1060`).

**7.2 Model storage must move to internal storage on affected builds.** Gallery carries an explicit
workaround:

```kotlin
// Gallery, common/Utils.kt — "On Pixel 11 with CD1A builds, internal app storage (filesDir) is
// used to avoid FUSE filesystem DMA mapping issues."
fun getModelStorageDir(context: Context): File {
    val isCd1aBuild = Build.ID?.startsWith("CD1A") == true
    return if (isPixel11() && isCd1aBuild) context.filesDir
           else context.getExternalFilesDir(null) ?: context.filesDir
}
```

Adopt the same helper rather than the current unconditional `getExternalFilesDir(null)`. The
symptom this guards against — DMA mapping failure on a FUSE-backed file — surfaces as another
allocate-tensors error, so it is easy to confuse with §1. Note the gate is `Pixel 11 AND CD1A`;
treat the build-ID prefix as a moving target and re-check it against current firmware.

**7.3 Re-pull the dispatch library from a Pixel 11 Gallery install.** Do not assume the Pixel 10
copy works. The binary contains a southbound-runtime version gate:

```
Skipped applying edgetpu_performance_mode (%s) — unsupported feature on older southbound
runtime version (got: %d.%d, requires: >=0.18).
```

so an older dispatch shim against newer platform Edge TPU libraries degrades or fails. Pull the
`split_google_tensor_runtime.config.arm64_v8a.apk` from a Pixel 11 with a current Gallery installed
and diff the two `.so` files. If they are identical, one copy in `jniLibs` covers both; if not, see
7.4.

**7.4 Delivery when two dispatch binaries are needed.** `jniLibs` is a single flat directory per
ABI, so two different `libLiteRtDispatch_GoogleTensor.so` files cannot coexist in one APK. Options,
in order of preference:

1. Ship each as its own Play feature module with `<dist:install-time>` conditions on distinct
   `<dist:device-group>` entries — exactly Gallery's structure, with a second group alongside
   `Google_LGA101`. Device groups are defined in Play Console; Pixel 11 will need its own.
2. If only one binary is ever needed at runtime, keep a single module targeting both device groups.
3. Do not attempt to load a dispatch library from `filesDir` at runtime — `Backend.NPU` takes a
   directory and the platform will not `dlopen` an executable mapping from app data storage on
   modern Android. It must arrive as a packaged native library.

**7.5 Accelerator options.** Gallery removes GPU from the offered accelerators on Pixel 10
(`isPixel10()` → CPU and NPU/TPU only). Whether that restriction applies to Pixel 11 is unknown;
verify against a current Gallery build before copying the rule. There is no Pixel 11 equivalent of
the GPU-removal logic in the snapshot in `app/gallery/`.

**7.6 Do not reach for `Backend.GOOGLE_TENSOR`.** litertlm 0.17.0 added it (native name
`GOOGLE_TENSOR_ARTISAN`), and it looks like the obvious Tensor backend. It is not — it selects the
Artisan executor, which expects a `tf_lite_artisan_text_decoder` section. The G5 model has
`tf_lite_aux` + darwinn parameters instead, and `Backend.NPU` is correct for it. Only revisit this
if a future model file's header actually shows an Artisan section.

**7.7 Re-run §6 on the new hardware.** Confirm `SouthBound context created` and a `Darwinn`
firmware label appear, and record the firmware build label and `ro.build.id` in the table at the
top of this document alongside the Pixel 10 row.

---

## 8. Known-good configuration summary

| Item                       | Value                                                              |
|----------------------------|--------------------------------------------------------------------|
| `litertlm-android`         | `0.17.0`                                                           |
| Dispatch library           | `libLiteRtDispatch_GoogleTensor.so` in `jniLibs/arm64-v8a/`        |
| `useLegacyPackaging`       | `true` (jniLibs)                                                   |
| Backend                    | `Backend.NPU(nativeLibraryDir = applicationInfo.nativeLibraryDir)` |
| `samplerConfig`            | `null`                                                             |
| `maxNumTokens`             | `1024`                                                             |
| `cacheDir`                 | `context.filesDir.absolutePath`                                    |
| Speculative decoding / MTP | driven by `Capabilities.hasSpeculativeDecodingSupport()`           |
| Model                      | `gemma-4-E2B-it_Google_Tensor_G5.litertlm`, app-private storage    |
