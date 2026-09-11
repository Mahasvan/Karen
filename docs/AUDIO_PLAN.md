Three phases. Build each when the trigger arrives, not before.

### Phase 1 — now: audio on the existing backend

Two interfaces already exist. Extend one, keep the other. The extension is a
**breaking change** to `AssistantBackend` — it touches every implementor, so
the file list below includes the stub.

```kotlin
interface AssistantBackend {
    val supportsAudio: Boolean
    fun respond(prompt: String, audio: ByteArray? = null): Flow<AssistantReply>
    fun retry(): Flow<AssistantReply>?
    fun close()
}
```

`SpeechSource` stays unchanged. It's the fallback for backends that can't do
audio. The name is deliberate: `AssistantBackend` owns history, retry, and a
native engine; `SpeechSource` just produces a `Flow<String>`. Different
contract weights, different names.

#### Capability gating (derive, don't hardcode)

`TensorAssistantBackend` already probes `capabilities.inputModalities.audio`
at engine creation. Store it; derive `supportsAudio` from it:

```kotlin
override val supportsAudio get() = capabilities.inputModalities.audio
```

`EngineConfig.audioBackend` is gated on the same flag — this matches the
gallery reference (`LlmChatModelHelper.kt:130`: "must be CPU for Gemma 3n"):

```kotlin
audioBackend = if (capabilities.inputModalities.audio) Backend.CPU() else null
```

Never send audio to a model build that can't handle it. Note: `audioBackend`
and `supportsAudio` are both fixed by the model file at engine creation — the
engine is a process-wide singleton, so these are not free per-backend toggles.

#### Recording (injected class, not in the ViewModel)

```kotlin
class VoiceRecorder(private val context: Context) {
    val amplitude: StateFlow<Int>          // 0..32767, feeds the visualizer (optional)
    fun start()                            // begins capture on Dispatchers.IO
    fun stop(): ByteArray                   // returns raw PCM, resets the stream
    fun release()                          // called from ViewModel.onCleared
}
```

- 16 kHz, mono, 16-bit (`AudioRecord` with `CHANNEL_IN_MONO` +
  `ENCODING_PCM_16BIT`).
- Pure top-level `fun pcmToWav(pcm: ByteArray, sampleRate: Int = 16000):
  ByteArray` — copied from the gallery's `genByteArrayForWav()`, refactored
  to a free function taking `audioData`/`sampleRate` as params.

#### Wiring in ViewModel

```kotlin
if (backend.supportsAudio) {
    voiceRecorder.start()
    // on stop: val wav = pcmToWav(voiceRecorder.stop()); backend.respond("", wav)
} else {
    speech.transcribe().collect { ... }   // unchanged
}
```

`speech` stays non-null for Phase 1 (the Factory still wires `StubSpeechSource`
as the fallback path); it's simply unused when `supportsAudio`. It becomes
nullable in Phase 2.

`retry()` replays last audio: store `lastAudio` next to `lastPrompt`:

```kotlin
override fun retry(): Flow<AssistantReply>? = lastPrompt?.let { respond(it, lastAudio) }
```

#### TensorAssistantBackend send path

When `audio != null`, switch from `Message.user(prompt)` to
`Contents.of(...)` and the Flow-based `sendMessageAsync(Contents)` overload:

```kotlin
val contents = buildList {
    add(Content.AudioBytes(audio))
    if (prompt.isNotBlank()) add(Content.Text(prompt))   // omit when voice-only
}
conversation!!.sendMessageAsync(Contents.of(contents)).collect { ... }
```

When `audio == null`, the path is unchanged. Store `lastAudio` for retry.

#### Permission (the part a manifest line alone doesn't cover)

`RECORD_AUDIO` is a dangerous permission. Two parts:

- `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.RECORD_AUDIO" />`.
- `AssistantOverlay`: `rememberLauncherForActivityResult(RequestPermission())`
  requesting `RECORD_AUDIO` on first mic toggle; call `onMicClick` only on
  grant, else show a rationale.

#### Concrete file changes

- `assistant/AssistantBackend.kt` — add `supportsAudio` + `audio` param (interface change).
- `provider/tensor/TensorAssistantBackend.kt` — derive `supportsAudio` from the probed capability; gate `audioBackend`; switch to `Contents.of(...)` when audio present; store `lastAudio`; fix `retry()`.
- `provider/stub/StubAssistantBackend.kt` — update `respond` override signature (ignore audio).
- `assistant/VoiceRecorder.kt` *(new)* — injected PCM recorder class.
- `assistant/AudioWav.kt` *(new)* — `pcmToWav()` pure function.
- `assistant/AssistantViewModel.kt` — branch on `supportsAudio`; inject `VoiceRecorder`; wire retry-with-audio.
- `ui/AssistantOverlay.kt` — runtime permission request on mic toggle.
- `AndroidManifest.xml` — `RECORD_AUDIO` permission.

No new interfaces. No engine separation. One model, one backend.

### Phase 2 — when you add FunctionGemma or OpenAI: model selection

Trigger: second backend arrives. Add a config + factory. Not before.

```kotlin
sealed class BackendConfig(val id: String, val label: String) {
    data class Tensor(val modelFile: File) :
        BackendConfig("tensor-gemma", "Gemma (on-device TPU)")
    data class FunctionGemma(val modelFile: File) :
        BackendConfig("function-gemma", "FunctionGemma (on-device)")
    data class OpenAI(val endpoint: String, val apiKey: String, val model: String) :
        BackendConfig("openai", "OpenAI")
}
```

Factory creates backend + picks SpeechSource:

```kotlin
fun createBackend(config: BackendConfig, context: Context): AssistantBackend = when (config) {
    is BackendConfig.Tensor       -> TensorAssistantBackend(context, config.modelFile)
    is BackendConfig.FunctionGemma -> FunctionGemmaBackend(context, config.modelFile)
    is BackendConfig.OpenAI       -> OpenAIBackend(config.endpoint, config.apiKey, config.model)
}

fun createSpeechSource(backend: AssistantBackend, context: Context): SpeechSource? =
    if (backend.supportsAudio) null else PlatformSpeechSource(context)
```

Each backend implements `AssistantBackend` with its own `supportsAudio`:
- `TensorAssistantBackend`: derived from the probed `inputModalities.audio`.
- `FunctionGemmaBackend`: `false` (text-only, needs SpeechSource).
- `OpenAIBackend`: `true` if GPT-4o, `false` if text-only model.

**Engine lifecycle note:** switching model files requires
`TensorAssistantBackend.releaseEngine()` before a different `EngineConfig`
(with its own `audioBackend`/`supportsAudio`) can take effect — the engine is
a process-wide singleton. `createSpeechSource` returning `null` when
`supportsAudio` makes `speech` nullable from this phase on; Phase 1 keeps it
non-null.

ViewModel stays the same — it already branches on `supportsAudio`. New
backends just plug in.

### Phase 3 — if Gemma-as-transcriber is actually needed: engine separation

Trigger: you want Gemma transcribing while FunctionGemma responds. Two models
loaded simultaneously.

This is the only case where separating the engine from the backend pays off.
Same engine, two roles:

```kotlin
class TensorModelEngine(context: Context, modelFile: File, maxNumTokens: Int) {
    val supportsAudio: Boolean
    fun respond(prompt: String, audio: ByteArray? = null): Flow<Message>
    fun close()
}

class TensorAssistantBackend(private val engine: TensorModelEngine) : AssistantBackend {
    override val supportsAudio get() = engine.supportsAudio
    override fun respond(prompt: String, audio: ByteArray?) = engine.respond(prompt, audio)
    // ...
}

class TensorSpeechSource(private val engine: TensorModelEngine) : SpeechSource {
    override fun transcribe(): Flow<String> =
        engine.respond("Transcribe this audio accurately.", audio)
            .map { it.text() }
}
```

Wiring:

```kotlin
val gemmaEngine = TensorModelEngine(context, gemmaFile, 1024)
val backend = FunctionGemmaBackend(context, functionGemmaFile)
val speech = TensorSpeechSource(gemmaEngine)  // Gemma transcribes
```

Two engines in memory: Gemma (3GB) + FunctionGemma (288MB). Feasible on 8GB+
device.

But question this first: platform `SpeechRecognizer` is free, offline, no
3GB model. Is Gemma transcription worth the memory? Only if platform STT
quality is insufficient for your use case. Build Phase 3 only when you've
tried platform STT and it's not good enough.

### What not to build

- `TextModel` / `AudioModel` interfaces — `respond(prompt, audio?)` covers both
- `ModelRuntime` abstract class — engine separation only matters in Phase 3, build it then
- Capability enum/set — `supportsAudio: Boolean` is enough until a third modality arrives
- Backend registry/scheduler — Phase 2 factory is 10 lines, no framework needed

### Why this works

The two existing interfaces map to two real operations:

| Operation | Interface | Examples |
|---|---|---|
| Generate response from input | `AssistantBackend` | Gemma chat, FunctionGemma, OpenAI chat |
| Convert audio to text | `SpeechSource` | Platform STT, Gemma transcriber, OpenAI Whisper |

Every model falls into one or both. Gemma is `AssistantBackend` (with audio).
FunctionGemma is `AssistantBackend` (text only) + needs a `SpeechSource`. OpenAI
is `AssistantBackend` (maybe audio) + maybe `SpeechSource` (Whisper). No third
interface needed.

### Verification

- [ ] Unit test: `pcmToWav()` produces a valid 44-byte RIFF/WAVE header —
      marker bytes (`RIFF`/`WAVE`/`fmt `/`data`), fmt chunk size 16, PCM format
      1, channel/sample-rate/byte-rate fields, data size = pcm length. One
      small test file; catches endianness/offset bugs.
- [ ] Build: `./gradlew :app:assembleDebug` compiles after the interface change.
- [ ] Manual: on a device with the Gemma model, toggle mic, grant permission,
      speak, observe a response derived from audio (no transcript shown). On a
      text-only model build, confirm the `speech` fallback path still
      transcribes.
