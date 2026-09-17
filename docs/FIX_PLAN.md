# Provider Layer — Fix Plan

Addresses findings 1–6 from the `provider/` review, sequenced so each phase
lands independently. Written with a second provider in mind: an
OpenAI-compatible endpoint alongside the on-device LiteRT backend.

## Guiding principles

**1. Degrade gracefully, escalate deliberately.**
Not every failure deserves an error row. The split:

| Failure | Surface | Why |
| --- | --- | --- |
| Cancellation | Nothing | A new turn started. Never user-visible. |
| Model produced nothing useful / malformed output | Fallback bubble + `Log.e` | Retrying the same prompt rarely helps; an error row is noise. |
| Transport (timeout, 5xx, 429, offline) | `AssistantError.Network` (`recoverable = true`) | Retry genuinely helps. Essential once a remote endpoint exists. |
| Setup (model file missing, bad API key, no auth) | `AssistantError.ModelUnavailable` (`recoverable = false`) | The user must act; retry cannot fix it. |

`respond()` already ships a fallback string (`"I didn't catch that. Could you
rephrase?"`). Promote it to the single constant used by every degrade path.

**2. One canonical tool schema.**
LiteRT discovers tools by reflection over `@Tool`; an OpenAI-compatible endpoint
needs JSON Schema in the request body. Without a single source of truth that is
three hand-synced copies of every tool. `Tool.parameters` already exists and is
currently read by nothing — it becomes canonical.

**3. The interface must describe what implementations can actually do.**
Contracts that only the stub honours are worse than no contract.

---

## Phase 1 — Error taxonomy in `TensorAssistantBackend` (finding 2)

**Problem.** `respond()` wraps generation in `catch (e: Exception)`, emits
`"Error: ${e.message}"` into the conversation, and returns. Three consequences:

- `CancellationException` extends `Exception` in Kotlin, so a cancelled turn is
  caught instead of propagating. The ViewModel cancels `turnJob` on every new
  turn, so this fires in normal use and breaks structured concurrency.
- Raw exception text lands in a chat bubble.
- `AssistantError` / `ErrorRow` / retry never engage for model failures.

**Change.** Replace the catch-all with an ordered chain:

```kotlin
} catch (e: CancellationException) {
    throw e                        // never user-visible
} catch (e: ModelUnavailableException) {
    throw e                        // → ModelUnavailable, recoverable = false
} catch (e: IOException) {
    throw e                        // → Network, recoverable = true
} catch (e: Exception) {
    Log.e(TAG, "generation failed", e)
    emit(AssistantReply.Text(FALLBACK_REPLY, id = responseId))
}
```

Extract `FALLBACK_REPLY` as a constant and reuse it in the existing
"no text and no tools fired" failsafe so both degrade paths read identically.

**Files.** `provider/tensor/TensorAssistantBackend.kt`.

**Note.** Also replace `conversation!!` with a local val + explicit
`ModelUnavailableException` — the assertion is load-bearing and silent.

---

## Phase 2 — Speech errors flow through `AssistantError` (findings 3 + 4)

Do these together: removing the Toasts requires the error path to exist first.

**Problem.** `PlatformSpeechSource.onError` calls `channel.close()` without an
exception, so the flow completes *normally* and the ViewModel's `catch` never
runs. `SpeechEvent` has no error variant, so "no recognition service" is
indistinguishable from "user said nothing." Separately, the provider posts
`Toast`s for permission and language-pack failures — a data-layer class deciding
UX, bypassing the `AssistantError` pipeline that already exists.

**Change.**

1. Add a failure type in `assistant/SpeechSource.kt`:

   ```kotlin
   enum class SpeechFailure { PermissionDenied, LanguageUnavailable, ServiceUnavailable, Network, Unknown }

   class SpeechException(val failure: SpeechFailure, message: String) : Exception(message)
   ```

2. In `onError`, split by intent — silence is not an error:
   - `NO_MATCH`, `SPEECH_TIMEOUT` → `channel.close()` (normal). The user said
     nothing; the ViewModel goes `Idle` silently.
   - everything else → `channel.close(SpeechException(...))`.

3. Add `AssistantError.SpeechUnavailable(detail)` (`recoverable = false` for
   permission/language, `true` for busy/network) and extend `toAssistantError()`
   to map `SpeechException`.

4. Delete the `Toast` calls, the `Toast` import, and the
   `mainHandler.post { Toast... }` blocks. Move the user-facing copy into
   `strings.xml` (`error_speech_permission`, `error_speech_language`,
   `error_speech_unavailable`) and render it through `ErrorRow`.

**Files.** `assistant/SpeechSource.kt`, `assistant/AssistantModels.kt`,
`assistant/AssistantBackend.kt` (`toAssistantError`),
`provider/platform/PlatformSpeechSource.kt`, `res/values/strings.xml`,
`assistant/AssistantViewModel.kt` (catch and map in the `transcribe()` collector).

---

## Phase 3 — Single-source tool schema (finding 5)

The largest change, and the one that unblocks the OpenAI provider.

**Problem.** `Tool.parameters` and `Tool.description` are never read. The real
schema lives in duplicated `@Tool`/`@ToolParam` annotations inside
`TensorAssistantBackend.AssistantToolSet`, with copy-pasted description strings.
Nothing forces the copies to agree — and they already don't:

> `StubAssistantBackend.emitEvent()` sends `start_millis`/`end_millis` as epoch
> millis. `CalendarEventTool` reads `start_time`/`end_time` and expects ISO
> `YYYY-MM-DDTHH:MM:SS`. Both keys are ignored, the computed start time is
> silently discarded, and it falls back to `System.currentTimeMillis()`.

`Map<String, String>` means this compiles clean.

**Change.**

1. **Type the schema.** `ToolParameter` currently has no type, which JSON Schema
   requires:

   ```kotlin
   enum class ToolParamType { String, Integer, Number, Boolean }

   data class ToolParameter(
       val name: String,
       val description: String,
       val required: Boolean,
       val type: ToolParamType = ToolParamType.String,
   )
   ```

2. **Extract a shared executor.** Tool execution currently lives inside
   `AssistantToolSet` (LiteRT-specific). Pull the validate → execute → queue-card
   → return-status sequence into `tools/ToolExecutor.kt` so both the LiteRT
   reflection shim and the OpenAI manual loop call one path.

3. **Generate the OpenAI schema.** Add `tools/OpenAiToolSchema.kt` mapping
   `ToolRegistry.tools` → the `functions` JSON array. No second copy.

4. **Guard the LiteRT shim.** Annotations are compile-time, so `AssistantToolSet`
   must stay hand-written. Add a unit test asserting every `@Tool` method's
   name, parameter names, and descriptions match the canonical `ToolRegistry`
   entry. Drift then fails the build instead of shipping.

5. **Fix the calendar bug** — `start_millis`/`end_millis` → `start_time`/`end_time`
   with ISO formatting.

**Files.** `tools/Tool.kt`, `tools/ToolExecutor.kt` (new),
`tools/OpenAiToolSchema.kt` (new), `tools/calendar/CalendarEventTool.kt`,
`provider/tensor/TensorAssistantBackend.kt`,
`provider/stub/StubAssistantBackend.kt`, new test under `app/src/test/`.

**Tool-loop difference to keep in mind.** LiteRT runs
`automaticToolCalling = on` — the runtime invokes the method and feeds the result
back. OpenAI does not: the backend must send the request, read `tool_calls`,
execute, append results, and re-request. `AssistantBackend` already hides this,
but the `completedTools` queue is a LiteRT-shaped workaround. The OpenAI backend
drives its own loop against `ToolExecutor` and emits cards inline.

---

## Phase 4 — Make the stubs earn their place (finding 6)

**Problem.** `StubAssistantBackend` and `StubSpeechSource` are referenced
nowhere (both `@Suppress("unused")`). The only tests are the generated
`ExampleUnitTest` / `ExampleInstrumentedTest`. `StubAssistantBackend.history`
accumulates and is never read. That absence of a consumer is how the calendar
argument bug in Phase 3 went unnoticed.

**Change.** Keep them — with a remote provider coming they are how the tool loop
gets tested without burning API calls — but wire them to real tests:

- `AssistantViewModel` turn lifecycle against `StubAssistantBackend`
  (turn commits, retry replays, cancellation on a new turn).
- The Phase 3 schema-parity test.
- `SpeechEvent` sequencing against `StubSpeechSource`, including the Phase 2
  failure paths (add a failure mode to the stub).
- Either assert on `StubAssistantBackend.history` in a commit-contract test or
  delete the field.

**Files.** `app/src/test/java/com/mahasvan/karen/...` (new),
`provider/stub/*`.

---

## Phase 5 — Honest history contract (finding 1)

**Problem.** `AssistantBackend` states history is transactional: *"a turn commits
only when the returned Flow completes normally."* `TensorAssistantBackend` cannot
honour that — LiteRT's `Conversation` owns history and appends on
`sendMessageAsync`. The code already admits it: *"a failed turn may leave a
dangling user message… the conversation cannot be rolled back."* Only the stub
implements the documented behaviour, i.e. the abstraction was written around the
fake.

**Change.** Three backends with genuinely different capabilities:

| Backend | History owner | Rollback |
| --- | --- | --- |
| LiteRT | `Conversation` (opaque) | No |
| OpenAI-compatible | Backend holds `List<Message>` | Yes |
| Stub | Backend holds a list | Yes |

1. Relax the interface KDoc to the honest common denominator: a turn *should*
   commit on normal completion; implementations that cannot roll back must say
   so on the class.
2. Document on `retry()` that backends without rollback may replay against a
   context already containing the failed user turn (producing a duplicate turn).
3. Implement true transactional history in the OpenAI backend — append a pending
   list, commit on completion. The documented model then holds wherever it can.

**Files.** `assistant/AssistantBackend.kt`,
`provider/tensor/TensorAssistantBackend.kt` (class-level caveat),
`provider/openai/*` (new, later).

---

## Sequencing

| Phase | Findings | Depends on | Notes |
| --- | --- | --- | --- |
| 1 | 2 | — | Live bug on every interrupted turn. Do first. |
| 2 | 3, 4 | — | Independent of Phase 1; can run in parallel. |
| 3 | 5 | — | Largest. Unblocks the OpenAI provider. |
| 4 | 6 | 3 | Tests validate Phase 3's parity guarantee. |
| 5 | 1 | 3, 4 | Lands with the OpenAI backend. |

## Out of scope (tracked separately)

- `sharedEngine` is process-global and never invalidated, so model/runtime
  changes cannot take effect without a process restart. Blocks the model and
  runtime settings.
- `AssistantViewModel.Factory` hardcodes its provider pair. Provider selection
  (on-device vs. OpenAI-compatible) needs an injection seam and belongs with the
  settings work.
- `ToolRegistry.kt` imports from itself (`import ...ToolRegistry.tool`/`.tools`,
  same file and package). Harmless artifact; delete when next touched.
