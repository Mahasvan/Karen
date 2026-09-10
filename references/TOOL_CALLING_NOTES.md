# Function calling with LiteRT-LM, and driving the system clock — developer notes

Status: verified working on the device below, from a debug build of this app. A `set_timer` call
issued from chat reached the system clock app and created a real timer.

|         |                                                                                          |
|---------|------------------------------------------------------------------------------------------|
| Device  | Pixel 10 (`ro.product.model = Pixel 10`), Android 17, SDK 37                             |
| Runtime | `com.google.ai.edge.litertlm:litertlm-android:0.17.0`                                    |
| Model   | `litert-community/gemma-4-E2B-it-litert-lm` → `gemma-4-E2B-it_Google_Tensor_G5.litertlm` |
| Backend | `Backend.NPU` (Tensor TPU — see `TENSOR_TPU_NOTES.md`)                                   |
| Code    | `app/src/main/java/com/example/llmtest/tools/ClockTools.kt`                              |

This document covers two separate things that happen to meet in this app:

1. **§1–§5** — how function calling works in LiteRT-LM. Model- and domain-agnostic.
2. **§6–§8** — what the Android clock API actually permits, which is far less than it looks.

---

## 1. The headline gotcha: `supportsFunctionCalling()` returns `false` and tool calling works anyway

The model container's own capability probe says function calling is unsupported:

```
I LlmEngineManager: Model capabilities: speculativeDecoding=true, thinking=false,
                    functionCalling=false, modalities=SupportedModalities(text=true, …)
W LlmEngineManager: Model does not advertise function calling; the clock tools will be
                    ignored or hallucinated rather than invoked.
```

The very next tool call succeeded:

```
D ClockTools: setTimer: durationSeconds=120, label='two minutes'
```

**Do not gate tool registration on `Capabilities.supportsFunctionCalling()`.** The flag reflects a
metadata section in the `.litertlm` container, not the runtime's actual ability to drive a tool
loop. LiteRT-LM's automatic tool calling is prompt-driven — it injects the tool schemas into the
prompt and parses the model's emitted call syntax — so any instruction-tuned model that can follow
the format will work regardless of what the container advertises.

Keep the probe as a **warning** (a genuine `false` still predicts flakier tool use on a weaker
model), never as a hard gate. That is exactly how `LlmEngineManager.readSpeculativeDecodingSupport`
uses it in this app.

> Corollary: because dispatch is prompt-driven rather than a constrained-decoding grammar, tool
> calling here is best-effort. A 2B-class model will occasionally emit prose instead of a call, or
> invent arguments. §5 covers how the tool contract absorbs that.

---

## 2. Wiring

Three pieces, no extra dependencies — `kotlin-reflect` arrives transitively from
`litertlm-android` (declared `compile` scope in its POM), which matters because dispatch is
reflection-based.

**Declare a `ToolSet`:**

```kotlin
class ClockTools(private val context: Context) : ToolSet {
    @Tool(description = "Starts a countdown timer in the device's clock app…")
    fun setTimer(
        @ToolParam(description = "How long the timer should run, in seconds…") durationSeconds: Int,
        @ToolParam(description = "A short label…") label: String,
    ): Map<String, String> { … }
}
```

**Wrap and register it** — `tool()` returns a `ToolProvider`:

```kotlin
private val tools = listOf(tool(ClockTools(application)))
```

**Pass it to the conversation**, not the engine:

```kotlin
engine.createConversation(
    ConversationConfig(
        systemInstruction = buildSystemInstruction(),
        samplerConfig = null,        // required on NPU — see TENSOR_TPU_NOTES.md §5
        tools = tools,
    )
)
```

`ConversationConfig.automaticToolCalling` defaults to **on**. The runtime invokes the matching
method and feeds its return value back as a tool response without the caller driving a loop.
`sendMessageAsync`'s flow still emits only model text, so the existing streaming UI needed no
changes — a tool round-trip simply shows up as a pause before the reply.

Tools are bound at **conversation** scope. Changing the toolset means recreating the conversation
(`Engine.createConversation`), not reloading the engine.

---

## 3. The reflection contract

`ReflectionTool` builds a JSON schema from the Kotlin function signature. Practical consequences:

| Concern         | Behaviour                                                                                                                                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tool name       | Method name is converted camelCase → snake_case. `setTimer` is exposed to the model as `set_timer`. Write system prompts using the **snake_case** name.                                                     |
| Parameter names | Same conversion. `durationSeconds` → `duration_seconds`.                                                                                                                                                    |
| Supported types | `String`, `Int`/`Long` (`integer`), `Float`/`Double` (`number`), `Boolean`, `List<T>` (`array`), and nested data classes (`object`).                                                                        |
| Descriptions    | `@Tool(description=…)` and `@ToolParam(description=…)` are the *only* thing the model sees. They are prompt text — write them for the model, not for a code reviewer.                                       |
| Return value    | Any object, serialized to JSON via Gson and returned as the tool response. `Map<String, String>` is the simplest thing that works.                                                                          |
| Minification    | Dispatch is `kotlin.reflect` over `KFunction`. Release builds here are safe only because `optimization { enable = false }`. **Enabling R8 will need keep rules for every `ToolSet` class and its methods.** |

### Optional parameters: don't

Kotlin default values are not reliably surfaced as "optional" in the generated schema. Declare every
parameter as required and document a sentinel in the description instead — empty string for an
absent label, empty list for a non-repeating alarm. This is the pattern the vendored Gallery uses in
`customtasks/mobileactions/MobileActionsTools.kt`, and it is more predictable for a small model than
hoping it omits the right key.

---

## 4. The system instruction carries the clock

The conversation is created **once at model load**, so anything time-dependent in the system
instruction is frozen at that moment:

```kotlin
private fun buildSystemInstruction(): Contents {
    val now = LocalDateTime.now()
    return Contents.of(
        "You are a helpful, concise assistant that can control the device's clock app…\n" +
        "Current date and time: ${now.format(…)} (${now.format(DateTimeFormatter.ofPattern("EEEE"))}).\n" +
        …
    )
}
```

This is load-bearing: without a wall-clock reference the model cannot turn "set an alarm for three
hours from now" into an `hour`/`minute` pair. It is also the known weak point — leave the app
resident across midnight and the model's idea of "today" is stale. Fixes, in increasing order of
effort: recreate the conversation on resume; or expose a `get_current_time` tool and instruct the
model to call it before any relative-time reasoning.

The instruction also has to state the platform's limits explicitly (§6), because the model will
otherwise happily fabricate a list of alarms it never retrieved:

> Android only exposes the single next alarm, so `get_next_alarm` cannot list every alarm, and
> timers cannot be read at all — call `show_timers` to open the clock app instead. Never invent
> alarms or timers you did not get back from a function.

---

## 5. Make tools fail conversationally, not exceptionally

Every tool in `ClockTools` returns `status: "success" | "error"` rather than throwing:

```kotlin
if (hour !in 0..23) return failure("Hour must be between 0 and 23, got $hour.")
```

A thrown exception aborts the turn; a returned error is fed back to the model, which can apologise
or retry with corrected arguments. Given that a small model will occasionally pass `hour = 25` or
`"Funday"`, this is the difference between a recoverable exchange and a dead conversation.

> **Kotlin trap:** do not name that helper `error()`. `kotlin.error()` is a stdlib function that
> throws `IllegalStateException`, and a private member named `error` silently shadows it inside the
> class — so the failure path compiles and does the opposite of what a reader expects. Named
> `failure()` here.

---

## 6. What the Android clock API actually allows

`android.provider.AlarmClock` is **write-and-navigate only**. This asymmetry drives the whole tool
design and is not a limitation of this app.

| Capability            | Available? | Mechanism                                                             |
|-----------------------|------------|-----------------------------------------------------------------------|
| Create an alarm       | yes        | `ACTION_SET_ALARM`                                                    |
| Create a timer        | yes        | `ACTION_SET_TIMER`                                                    |
| Dismiss / snooze      | yes        | `ACTION_DISMISS_ALARM`, `ACTION_DISMISS_TIMER`, `ACTION_SNOOZE_ALARM` |
| **List alarms**       | **no**     | `ACTION_SHOW_ALARMS` only opens the UI and returns nothing            |
| **List timers**       | **no**     | `ACTION_SHOW_TIMERS` likewise                                         |
| Read the *next* alarm | partially  | `AlarmManager.getNextAlarmClock()` — one alarm, system-wide           |

The clock app's own `ClockContentProvider` / `AlarmProvider` (`com.google.android.deskclock`) is not
exported, so there is no supported query path. Confirmed on device — the provider exists but is
unreachable:

```
$ adb shell dumpsys activity provider com.google.android.deskclock
PROVIDER ContentProviderRecord{… com.android.deskclock.provider.ClockContentProvider}
    Client:
      nothing to dump
```

`getNextAlarmClock()` returns an `AlarmManager.AlarmClockInfo` covering alarms scheduled by **any**
app via `setAlarmClock()`, which includes the system clock app. It gives a trigger time and a show
intent — nothing else, and only for the soonest one.

**Therefore "retrieve my alarms" tops out at "when is my next alarm", and "retrieve my timers" is
not implementable at all.** The honest mapping used here:

| Tool                                          | Backing API                                                |
|-----------------------------------------------|------------------------------------------------------------|
| `set_timer(duration_seconds, label)`          | `ACTION_SET_TIMER`, validated to 1s–24h                    |
| `set_alarm(hour, minute, label, repeat_days)` | `ACTION_SET_ALARM`, `EXTRA_DAYS` from `Calendar` constants |
| `get_next_alarm()`                            | `AlarmManager.getNextAlarmClock()`                         |
| `show_timers()`                               | `ACTION_SHOW_TIMERS` — hands off to the clock app          |

If genuine alarm/timer CRUD is ever required, the only route is to stop using the system clock and
implement alarms in-app with `AlarmManager` + notifications, owning the state. That is a different
product decision, not a bug fix.

---

## 7. Intent mechanics that bite

**Permission.** Install-time, no runtime prompt. The clock app's `HandleApiCalls` activity requires
it for every `AlarmClock` action:

```xml
<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
```

Verify: `adb shell dumpsys package <appId> | grep SET_ALARM` → `granted=true`.

**`FLAG_ACTIVITY_NEW_TASK` is mandatory.** Tools run against the *application* context (the
`ToolSet` is constructed in the ViewModel), and `startActivity` from a non-activity context throws
without it.

**Catch `ActivityNotFoundException`; do not pre-check with `resolveActivity`.** Under Android 11+
package visibility, `resolveActivity` returns `null` for the clock app unless a `<queries>` entry is
added to the manifest — so the defensive check *is* the bug. Try the intent and handle the failure:

```kotlin
try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    onSuccess()
} catch (e: ActivityNotFoundException) {
    failure("This device has no clock app that can handle that request.")
}
```

**`EXTRA_SKIP_UI` is a request, not a guarantee.** Observed on this device: `ACTION_SET_TIMER` with
`EXTRA_SKIP_UI = true` still brought Google Clock to the foreground —

```
22:02:11 D ClockTools: setTimer: durationSeconds=120, label='two minutes'
22:02:17 D Mindful.LaunchTrackingManager: Opened app:com.google.android.deskclock
22:02:21 V WindowManagerShell: … TO_FRONT … cmp=com.example.llmtest/.MainActivity
```

The timer was created correctly, but the user had to swipe back. AOSP's `HandleApiCalls` honours
`skipUi` for timers; the shipping Google Clock evidently does not, at least on this build. Budget
for the foreground switch in the UX rather than assuming the flag holds.

---

## 8. Verification

What has actually been exercised end-to-end, and what has not:

| Tool             | Status                                                            |
|------------------|-------------------------------------------------------------------|
| `set_timer`      | **verified** — model-initiated call created a real 2-minute timer |
| `set_alarm`      | not yet exercised on device                                       |
| `get_next_alarm` | not yet exercised on device                                       |
| `show_timers`    | not yet exercised on device                                       |

Useful commands:

```bash
# Watch tool dispatch and the capability probe
adb logcat -s ClockTools:* LlmEngineManager:* AndroidRuntime:E

# Confirm the permission merged and was granted
adb shell dumpsys package com.example.llmtest | grep SET_ALARM[TOOL_CALLING_NOTES.md](TOOL_CALLING_NOTES.md)

# Independently confirm an alarm exists (matches what get_next_alarm reads)
adb shell settings get system next_alarm_formatted
```

The `D ClockTools: …` line at the top of each tool is the ground truth for "did the model really
call this, with what arguments" — worth keeping, since a model describing a timer it never set is
otherwise indistinguishable from success in the chat transcript.

---

## 9. Open items

- **`maxNumTokens` is still 1024.** Tool schemas are injected into the prompt and each call/response
  round-trip consumes context. Four tools fit; a larger toolset will need headroom. Symptom to watch
  for is truncation or degraded tool use as the conversation grows.
- **R8 keep rules** before any minified release build (§3).
- **Stale system-clock time** across long-running sessions (§4).
- **No user confirmation step.** Tools fire as soon as the model emits a call. Gallery gates
  higher-risk tools behind `McpToolCallPermissionDialog`; worth adopting if destructive actions
  (dismissing alarms, deleting timers) are added.
