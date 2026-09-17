# LiteRT NPU Runtime Deployment Notes

How to ship LiteRT NPU runtime libraries without Google Play, and what it takes
to generalise `TensorAssistantBackend` into a vendor-neutral NPU backend.

Findings below are split into **verified** (read from the files in
`test/litert_npu_runtime_libraries/`, bundletool source, or Google's docs) and
**needs testing on device**.

---

## 0. Decide NPU vs GPU first

Everything below is about **NPU** deployment. If you run the LLM on the **GPU**,
none of it applies — GPU needs *zero* packaging work. Read §1.5 before investing
in any of the NPU machinery.

## 1. TL;DR — the decisive finding

**On Google Tensor you do not need feature delivery, an AAB, bundletool, or an
`.apkm` at all.**

The entire `google_tensor_runtime` feature module is one 315 KB file:

```
google_tensor_runtime/src/main/jni/arm64-v8a/libLiteRtDispatch_GoogleTensor.so
```

Drop that into `app/src/main/jniLibs/arm64-v8a/`, set
`useLegacyPackaging = true`, and ship an ordinary APK. That is the whole job for
a Pixel-only build.

Feature delivery exists to solve a problem you don't have yet: **Qualcomm and
MediaTek need ~105 MB of proprietary vendor blobs per SoC generation**, and you
must not ship all of them to every device. Google Tensor needs none — the NPU
drivers are already in the Pixel system image, and the dispatch shim just talks
to them.

So treat this as two separate projects:

| Goal | Effort | Needs Play? | Needs AAB/splits? |
| --- | --- | --- | --- |
| Ship to your Pixel today | ~10 lines of Gradle | No | No |
| Add Qualcomm/MediaTek later | Substantial | No (see §5) | Yes |

---

## 1.5 The GPU alternative — no packaging at all

**Verified against `litertlm-android-0.17.0.aar` and its compiled API.**

The `Backend` API is self-documenting on this point:

```
Backend$GPU()                              // no-arg
Backend$CPU(Integer, Integer)              // thread counts only
Backend$GOOGLE_TENSOR()                    // no-arg
Backend$NPU(String nativeLibraryDir)       // ← the only one taking a lib dir
```

`nativeLibraryDir` exists on `Backend.NPU` *precisely because* the dispatch shim
has to be located on the filesystem. `Backend.GPU()` takes no arguments because
there is nothing to locate.

The AAR already on the classpath ships everything GPU needs:

```
jni/arm64-v8a/liblitertlm_jni.so    21.8 MB
jni/x86_64/liblitertlm_jni.so      26.0 MB
```

One self-contained library. **No dispatch libraries, no vendor blobs.** GPU and
CPU acceleration are compiled in.

### What GPU removes

| Concern | NPU | GPU |
| --- | --- | --- |
| `jniLibs` / dispatch `.so` | Required | **None** |
| `useLegacyPackaging = true` | Required (§8) | Not needed |
| Feature modules / AAB / bundletool | For multi-vendor | **Never** |
| `.apkm` / split distribution | For multi-vendor | **Never** |
| Qualcomm licensing exposure (§9) | Yes, off-Play | **None** |
| `minSdk 31`, `arm64-v8a` only | Required | Not imposed by the backend |
| Per-SoC AOT model fan-out (§7) | Yes | **No** — one artifact |

For GPU the entire document collapses to one line:

```kotlin
backend = Backend.GPU(),
```

### The real cost is the model, not the packaging

`gemma-4-E2B-it_Google_Tensor_G5.litertlm` is **AOT-compiled for the Tensor G5
NPU** — the SoC is in the filename. It will not run on the GPU. A GPU build of
the same model is needed. That is a download, not a build-system problem, but it
is the one thing to confirm before committing to GPU.

### GPU is not a downgrade

LiteRT 2.2.0 put real work into GPU LLM throughput: ROPE composite ops (~5%
faster decode), and GPU weight-batching that saved ~200 MB on Gemma 4 E2B.
NPU still wins on sustained decode and power efficiency — that is what the Tensor
silicon is for — but GPU is a first-class path, not a fallback.

### Also worth testing: `Backend.GOOGLE_TENSOR()`

It is **no-arg**, unlike `Backend.NPU(nativeLibraryDir)`. That strongly suggests
a higher-level path that resolves the Tensor dispatch itself. If it works, the
NPU route loses its `nativeLibraryDir` plumbing too. Untested — see §11.

### ⚠️ Correction: GPU is disabled on Pixel 10

**Verified in `app/gallery/.../data/ModelAllowlist.kt`.** Google's own app strips
GPU from the accelerator list on Pixel 10:

```kotlin
// Remove GPU from pixel 10 devices.
if (isPixel10()) {
  parsedAccelerators.remove(Accelerator.GPU)
}
```

`isPixel10()` is `Build.MODEL.contains("pixel 10")` — i.e. Tensor G5, the target
device. There is no comment explaining why, but the gallery team clearly does not
consider GPU viable there.

So "ship GPU first" is **not** safe advice for a Pixel 10. Revised:

- **Pixel 10 (Tensor G5):** NPU/TPU is the intended path. Test GPU before
  relying on it — Google disables it.
- **Other devices:** GPU remains the zero-packaging option described above.

The packaging analysis in §1.5 is unchanged and still correct — GPU needs no
runtime libraries. What changed is whether GPU *works* on this particular SoC.

### On Pixel, NPU is called TPU

```kotlin
if (isPixelDevice()) {
  finalDescription = description.replace(Regex("\\bNPU\\b"), "TPU")
  acceleratorsStr = acceleratorsStr?.replace(Regex("\\bnpu\\b"), "tpu")
}
```

Purely a labelling convention (the existing code comment already notes “TPU in
Pixel UI is the same NPU API”), but it means a Pixel-facing settings UI should
say **TPU**. It also lines up with `Backend.GOOGLE_TENSOR()` existing as its own
no-arg variant alongside `Backend.NPU(nativeLibraryDir)`.

### Recommendation

On Pixel 10, keep NPU/TPU as the primary path. GPU stays the fallback worth
measuring, not the default. On any non-Pixel-10 device, GPU removes the entire
deployment problem.

---

## 2. What these libraries actually are

There are **two layers**, and conflating them is the main source of confusion.

**Layer 1 — LiteRT dispatch shim** (`libLiteRtDispatch_<Vendor>.so`)
Ships in the LiteRT release. Apache-2.0, redistributable. Small (315–445 KB).
LiteRT scans a directory at runtime and loads whichever dispatch library it
finds. This is the abstraction that makes `Backend.NPU` vendor-neutral.

**Layer 2 — vendor runtime** (proprietary, vendor-licensed)
- **Google Tensor: none required.** Not in the module, not fetched by any
  script. The Tensor NPU stack is part of the Pixel system image. *(Verified —
  `google_tensor_runtime` contains exactly one `.so`.)*
- **Qualcomm: required and large.** `fetch_qualcomm_library.sh` downloads the
  QAIRT SDK (v2.47.0.260601, ~2.35 GB) and copies four files per HTP generation:
  `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV<N>Skel.so`,
  `libQnnHtpV<N>Stub.so` for N ∈ {69, 73, 75, 79, 81}.
- **MediaTek: required** (NeuroPilot). No module present in your copy.

The size asymmetry *is* the architecture. Google Tensor sits on the easy side.

---

## 3. Inventory of what you have

`test/litert_npu_runtime_libraries/` — **verified**:

| Item | Status |
| --- | --- |
| `google_tensor_runtime` | Complete. One 315 KB dispatch `.so`. |
| `qualcomm_runtime_v{69,73,75,79,81}` | Dispatch shim only — **vendor blobs not fetched** (no `libQnn*.so` present). |
| `runtime_strings` | Complete. Plain `com.android.library` holding `dist:title` strings. |
| `fetch_qualcomm_library.sh` | Present, not run. |
| `device_targeting_configuration.xml` | **Missing.** Required for device groups; the docs say to copy it from the generated AI Pack. |
| MediaTek / Samsung modules | **Missing** (docs reference `mediatek_runtime`, `samsung_runtime`). `runtime_strings` has their titles but the modules aren't here. |
| `libLiteRtCompilerPlugin_*.so` | **Absent** → this is the **AOT** archive (`litert_npu_runtime_libraries.zip`), not the JIT one. |

That last row matters — see §7.

### The module manifests

Each module is a `com.android.dynamic-feature` using **install-time** delivery
with device-group conditions:

```xml
<dist:delivery>
  <dist:install-time>
    <dist:conditions>
      <dist:device-groups>
        <dist:device-group dist:name="Google_Tensor_G3"/>
        <dist:device-group dist:name="Google_Tensor_G4"/>
        <dist:device-group dist:name="Google_Tensor_G5"/>
        <dist:device-group dist:name="Google_Tensor_G6"/>
      </dist:device-groups>
    </dist:conditions>
  </dist:install-time>
</dist:delivery>
<dist:fusing dist:include="true" />
```

---

## 4. Correcting the delivery research

Your notes assumed dynamic *delivery* — downloading modules at runtime. **That
is not what LiteRT does, and it changes the answer substantially.**

These modules are `<dist:install-time>`, not `<dist:on-demand>`. Install-time
splits are installed **in the same `PackageInstaller` session as the base APK**.
Consequences:

- **No `SplitInstallManager`.** No Play Core / Play Feature Delivery library.
- **No `SplitCompat`.** Nothing is added to a running process.
- **LocallyDynamic, Shadow, RePlugin are all irrelevant here.** Those solve
  on-demand download, which LiteRT doesn't use for runtimes.
- **No extra install prompt.** Your table lists "Self-Hosted `PackageInstaller`
  → prompt: Yes". That's true only when *adding a split to an already-installed
  app*. Installing base + splits in one session is an ordinary install.

So the only thing Google Play actually provides is **the decision of which
module to serve**. Play evaluates `dist:device-groups` server-side at install
time. Without Play, that decision has to move somewhere else — and bundletool
can make it (§5).

Revised table:

| Method | Prompt | Play? | Complexity | Applies here |
| --- | --- | --- | --- | --- |
| Single APK, runtime in `jniLibs` | Normal install | No | Trivial | **Yes — Tensor-only** |
| `bundletool` + `adb install-multiple` | Normal install | No | Low | Yes — multi-vendor |
| `.apks`/`.apkm` sideload | Normal install | No | Low | Yes — distribution |
| Play Feature Delivery | Seamless | Yes | Low | Not wanted |
| SplitInstallManager / LocallyDynamic | — | — | — | **N/A (install-time)** |
| Shadow / RePlugin plugin loading | — | — | — | **N/A** |

---

## 5. Doing device-group targeting without Play

`dist:device-groups` conditions are a **Play serving decision**, so they never
fire on a sideloaded install. bundletool can evaluate them instead, moving the
decision to build/install time. Relevant flags (**verified** in
`BuildApksCommand.java`):

- `--device-spec=<json>` — target a specific device profile
- `--device-group=<name>` — override/supply the group for matching
  (only valid with `--device-spec` or `--connected-device`)
- `--fuse-only-device-matching-modules` — **fuse conditional modules into base
  when they match the device spec** (requires `--device-spec`)
- `--local-testing` — makes `install-apks` push module splits locally
- `install-apks --device-groups=a,b` — group selection at install time

`--fuse-only-device-matching-modules` is the key one: it collapses "base + the
one runtime this device needs" into a single APK, with no Play and no split
resolution at install.

### ⚠️ `--mode=universal` is a trap for multi-vendor

Universal mode includes every module with `<dist:fusing dist:include="true"/>` —
which all LiteRT modules set. So a universal APK contains **every vendor's
dispatch library in one `lib/arm64-v8a/` directory**, and LiteRT loads *the
first matching dispatch library it finds*. Result: non-deterministic vendor
selection on every device.

Universal mode is safe only when exactly one runtime module is in the bundle
(i.e. your Tensor-only build). For multi-vendor, use device-spec matching.

---

## 6. Recommended path

### Stage 1 — Pixel-only (do this now)

No AAB, no bundletool, no feature modules.

```kotlin
// app/build.gradle.kts
android {
  defaultConfig {
    minSdk = 31                          // already satisfied
    ndk { abiFilters.add("arm64-v8a") }  // NPU is arm64-only
  }
  packaging {
    jniLibs { useLegacyPackaging = true } // MUST: see §8
  }
}
```

```
app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_GoogleTensor.so
```

`Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` then
resolves the shim with **zero code changes**. This also removes the dependency
on having copied the library out of the AI Edge Gallery install.

### Stage 2 — multi-vendor, self-distributed

1. Write the missing `device_targeting_configuration.xml` (map SoC → group).
2. Add the modules to `settings.gradle.kts` / `dynamicFeatures`.
3. Run `fetch_qualcomm_library.sh` (needs ~2.35 GB download; **do not commit the
   output** — the QAIRT binaries are Qualcomm-licensed and not yours to
   redistribute; see §9).
4. Build and slice:

```bash
./gradlew bundleRelease

# Profile of the target device
java -jar bundletool.jar get-device-spec --output=device.json

# Base + only the matching runtime, fused
java -jar bundletool.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=karen.apks \
  --device-spec=device.json \
  --device-group=Google_Tensor_G5 \
  --fuse-only-device-matching-modules \
  --ks=release.jks --ks-key-alias=karen

java -jar bundletool.jar install-apks --apks=karen.apks
```

### Stage 3 — `.apkm` (only if you want APKMirror Installer compatibility)

`.apks` is *already* a ZIP of splits, so this is repackaging, not conversion:

```bash
unzip karen.apks -d karen_apkm && cd karen_apkm
# author info.json: package name, versionCode, versionName, splits
zip -r ../karen.apkm . && cd ..
```

For your own use `adb install-multiple base.apk split_*.apk` (or
`pm install-create` / `install-write` / `install-commit` on-device) is simpler
and needs no custom format.

---

## 7. AOT vs JIT — an unresolved fork

Your model filename encodes the SoC:

```
gemma-4-E2B-it_Google_Tensor_G5.litertlm
```

That's an **AOT-compiled** artifact, matching the AOT archive you have (no
compiler-plugin `.so`). AOT means one compiled model **per SoC**, which is what
Play AI Packs exist to distribute. Self-distributing AOT to many SoCs means
shipping or downloading N model variants — the model dominates size, not the
runtime.

**JIT** (`litert_npu_runtime_libraries_jit.zip`) ships a plain `.tflite` and
compiles on device. One artifact for all SoCs, at the cost of a slower first run
and extra libraries (`libLiteRtCompilerPlugin_*.so`, plus `libQnnHtpPrepare.so`,
`libQnnIr.so`, `libQnnSaver.so` for Qualcomm).

**For self-distribution outside Play, JIT is likely the better trade** — it
removes per-SoC model fan-out entirely. This needs deciding before Stage 2, and
it's the single biggest open question in this plan.

---

## 8. Gotchas

**`useLegacyPackaging = true` is mandatory.** LiteRT scans a *filesystem
directory* for dispatch libraries. With modern packaging (`extractNativeLibs=false`)
the `.so` stays compressed inside the APK and never appears in
`nativeLibraryDir`, so `Backend.NPU` finds nothing. Google's own docs set this
flag and attribute it to Qualcomm; it applies to the Tensor dispatch scan too.

**Never put two vendor dispatch libraries in one directory.** LiteRT picks the
first match. This is why each vendor gets its own module, and why universal mode
is unsafe for multi-vendor (§5).

**`runtime_strings` must be a dependency of the base module**, not just present.
`dist:title` in each feature manifest resolves against it.

**arm64-v8a only, minSdk 31.** Both already true for this project.

**Copying from the AI Edge Gallery install is not a distribution strategy.** The
Tensor shim is Apache-2.0 and comes from the LiteRT release — take it from
[LiteRT v2.2.0](https://github.com/google-ai-edge/LiteRT/releases/tag/v2.2.0),
not from another app's install directory. For Qualcomm the situation is stricter
(§9).

**`dist:device-groups` silently does nothing when sideloaded.** Not an error —
the module just isn't there, and NPU init fails at runtime. Fail loudly (§10).

---

## 9. Licensing

The **LiteRT dispatch shims** are Apache-2.0 — redistributable.

The **QAIRT/QNN binaries are Qualcomm-licensed**. The upstream tooling downloads
them at build time and deliberately never republishes them; the `flutter_litert`
notes are explicit that their CI "never commits or republishes the Qualcomm
binaries." If you self-distribute an APK containing `libQnn*.so`, **you** are
redistributing Qualcomm's SDK and are responsible for the terms. Review the
QAIRT Community licence before shipping Qualcomm support outside Play.
Google Tensor has no equivalent exposure — nothing proprietary ships.

---

## 10. Code changes: `TensorAssistantBackend` → NPU-neutral

**The backend barely changes**, because `Backend.NPU(nativeLibraryDir = ...)` is
*already* vendor-agnostic — vendor selection happens when LiteRT scans the
directory. What's vendor-specific is packaging and the model file.

Concretely:

1. **Rename** `TensorAssistantBackend` → `NpuAssistantBackend`, package
   `provider/tensor` → `provider/npu`. The `Backend.NPU(...)` call is unchanged.

2. **Stop hardcoding the model filename.** `MODEL_FILENAME` currently pins a
   G5-specific AOT artifact. It should come from settings (the model-selection
   work) with an SoC-aware default.

3. **Detect the SoC** for model choice and diagnostics. `Build.SOC_MODEL` and
   `Build.SOC_MANUFACTURER` are API 31+, which matches `minSdk = 31`:

   ```kotlin
   enum class NpuVendor { GoogleTensor, Qualcomm, MediaTek, Unknown }
   ```

4. **Detect the dispatch library before initialising.** Scan
   `nativeLibraryDir` for `libLiteRtDispatch_*.so`. Absent → the runtime module
   wasn't delivered. Today that surfaces as an opaque engine failure; it should
   throw `ModelUnavailableException` with a message naming the missing runtime.
   More than one → log a warning, because selection is now ambiguous (§8).

5. **Make the runtime a settings-visible fact.** Alongside model and runtime
   (NPU/GPU/CPU) selection, surface the detected vendor and whether a dispatch
   library was found. That turns the most likely field failure — "the split
   didn't get installed" — into something diagnosable.

This dovetails with the `sharedEngine` work already flagged in `FIX_PLAN.md`:
switching model or runtime can't take effect while the engine is a
process-global singleton that never re-reads `modelPath` or `backend`.

---

## 11. Device verification

### ✅ Confirmed on Pixel 10 / Tensor G5

```
Device: model=Pixel 10, soc=Tensor G5, socManufacturer=Google
Dispatch library: libLiteRtDispatch_GoogleTensor.so
Model capabilities: speculativeDecoding=true, thinking=false, functionCalling=false,
                    modalities=SupportedModalities(text=true, vision=true, audio=true, video=false)
```

- **Exactly one dispatch library resolves**, and it is the Google Tensor shim.
  No vendor blob is present or needed — the §1 claim holds in practice.
- **No ambiguity warning**, so the “first matching dispatch library” hazard (§8)
  is not currently in play.
- **`Build.MODEL` is literally `"Pixel 10"`**, so the gallery's `isPixel10()`
  GPU-removal (§1.5) does apply to this device.
- **`Build.SOC_MODEL` is `"Tensor G5"`** → allowlist key `"tensor g5"` (§12).

### Still needs testing

- **That `nativeLibraryDir` resolves correctly from a fused universal APK** as
  well as it does from a plain APK.
- **Whether `useLegacyPackaging = true` is needed for the Tensor path**, or only
  for Qualcomm as Google's comment implies. Test by flipping it and watching for
  `No dispatch library found in` in logcat (tag: `litert`).
- **First-run JIT compile time on Tensor G5**, if Stage 2 goes the JIT route.
- **Whether `Backend.GOOGLE_TENSOR()` resolves the Tensor dispatch without
  `nativeLibraryDir`** (§1.5). If it does, §6 Stage 1 gets simpler still.
- **Whether a GPU-compatible `.litertlm` of Gemma 4 E2B is available**, and its
  decode throughput against the NPU build on the same device.
- **Whether GPU actually works on Pixel 10**, given the gallery removes it
  (§1.5). If it does, GPU becomes viable there too; if not, the correction stands.
- **Whether `Engine.initialize()` actually completes on the NPU.** The logs above
  confirm the dispatch library and model file resolve, but capability reading
  happens *before* engine init — a successful turn is the real proof.

---

## 12. Model allowlist — following the gallery's approach

### The mechanism (verified in `app/gallery/`)

Per-SoC model selection is a single field on each allowlist entry:

```kotlin
data class SocModelFile(
  val modelFile: String?,
  val url: String?,
  val commitHash: String?,
  val sizeInBytes: Long?,
)

// on AllowedModel:
val socToModelFiles: Map<String, SocModelFile>? = null
```

Resolved at runtime against the device SoC:

```kotlin
// Consts.kt — note .lowercase()
val SOC = (if (Build.VERSION.SDK_INT >= VERSION_CODES.S) Build.SOC_MODEL ?: "" else "").lowercase()

// ModelAllowlist.toModel()
socToModelFiles.get(SOC)?.let { info ->
  version          = info.commitHash ?: "-"
  downloadedFileName = info.modelFile ?: "-"
  downloadUrl      = info.url
    ?: "https://huggingface.co/$modelId/resolve/${info.commitHash}/${info.modelFile}?download=true"
  sizeInBytes      = info.sizeInBytes ?: -1
}
```

Key points:
- **Keyed on `Build.SOC_MODEL`, lowercased** — API 31+, matching `minSdk = 31`.
- **Default URL convention** is HuggingFace pinned by commit hash; `url`
  overrides it for models hosted elsewhere.
- **Fallback is the generic entry** — if the SoC isn't in the map, the top-level
  `modelFile`/`commitHash` is used. A device with no AOT build degrades to the
  portable model rather than failing.
- Allowlists are versioned and fetched from
  `https://raw.githubusercontent.com/google-ai-edge/gallery/refs/heads/main/model_allowlists/<version>.json`.

### ⚠️ No public NPU models exist in the allowlist

**The schema supports per-SoC AOT models; nothing in the public allowlist uses
it.** Verified across all 19 bundled allowlists plus `model_allowlist.json`:

| `accelerators` value | Entries |
| --- | --- |
| `gpu,cpu` | 61 |
| `cpu,gpu` | 34 |
| `cpu` | 23 |
| `gpu` | 2 |
| **anything with `npu`/`tpu`** | **0** |

`socToModelFiles` appears in **zero** allowlist JSONs. `1_0_19` is the newest
(1_0_20+ return HTTP 404). The SoC token table in `HfModelUtils.kt:117` —
`GOOGLE_TENSOR(setOf("tensor", "zuma", "gs101", "gs201", "laguna", "lga"))` —
stops before G5.

**Consequence:** `gemma-4-E2B-it_Google_Tensor_G5.litertlm` does not come from
the public allowlist. Its download URL, commit hash, and size are **not
recoverable from this source** and must not be invented. They have to come from
wherever that file was obtained (a preview channel, or the Gallery install
itself).

The *mechanism* is reusable immediately; the *data* for NPU is not yet public.

### Proposed schema for Karen

Strip the gallery's task/capability machinery (none of it applies) and keep the
SoC resolution:

```json
{
  "version": 1,
  "models": [
    {
      "name": "Gemma 4 E2B",
      "modelId": "litert-community/gemma-4-E2B-it-litert-lm",
      "modelFile": "gemma-4-E2B-it.litertlm",
      "commitHash": "6e5c4f1e395deb959c494953478fa5cec4b8008f",
      "sizeInBytes": 2588147712,
      "minDeviceMemoryInGb": 8,
      "accelerators": "gpu,cpu",
      "maxTokens": 4000,
      "maxContextLength": 32000,
      "socToModelFiles": {
        "tensor g5": {
          "modelFile": "gemma-4-E2B-it_Google_Tensor_G5.litertlm",
          "commitHash": "TBD",
          "sizeInBytes": 0,
          "url": "TBD"
        }
      }
    }
  ]
}
```

The generic entry's values above are **real**, copied from allowlist `1_0_19`.

**The SoC key is confirmed from device logs:**

```
Device: model=Pixel 10, soc=Tensor G5, socManufacturer=Google
```

`Build.SOC_MODEL` is `"Tensor G5"`, so the allowlist key — which the gallery
lowercases — is **`"tensor g5"`, with a space**. Don't guess this format; a
underscore or hyphen variant silently misses and falls back to the generic
CPU/GPU model.

Still `TBD` in the block above: the G5 artifact's `commitHash`, `sizeInBytes`,
and `url`. These are not public (see the warning above) and must come from
wherever the file was obtained.

### Rules worth copying

1. **Sampling config is accelerator-dependent.** `createLlmChatConfigsForNpuModel`
   exposes only max-tokens and accelerator — **no topK/topP/temperature**. This
   independently confirms the existing `samplerConfig = null` comment in
   `TensorAssistantBackend`, and means the allowlist must carry `accelerators` so
   the settings UI knows which controls to show.
2. **Gate on RAM.** `minDeviceMemoryInGb` (8 GB for E2B, 12 GB for E4B) prevents
   offering a model that will OOM.
3. **Pin by commit hash**, so a model file can't change under a cached download.
4. **Storage location is device-dependent.** `getModelStorageDir()` uses
   `filesDir` on Pixel 11 with `CD1A` builds “to avoid FUSE filesystem DMA
   mapping issues”, otherwise external files dir. This is the same FUSE hazard the
   existing `cacheDir` comment already works around — worth copying verbatim.

### Where it fits

The allowlist is the data source for the model-selection setting, and
`socToModelFiles` resolution supplies the `modelFile` half of the `EngineSpec`
pair. Since accelerator and model must agree (an AOT NPU model cannot run on
GPU), the allowlist is what makes that pairing checkable rather than a
convention.

---

## References

- [LiteRT v2.2.0 release](https://github.com/google-ai-edge/LiteRT/releases/tag/v2.2.0) — QAIRT SDK 2.47 alignment, ABI version tracking across `libLiteRt.so`/dispatch/compiler
- [NPU acceleration with LiteRT](https://developers.google.com/edge/litert/next/npu) — the Play-based reference path
- [Play for On-device AI](https://developer.android.com/google/play/on-device-ai) — device targeting, AI packs (note: AI packs cannot contain native libraries)
- [bundletool](https://developer.android.com/tools/bundletool)
- `test/litert_npu_runtime_libraries/` — local copy inspected for this document
