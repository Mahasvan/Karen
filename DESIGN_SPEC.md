# Assistant — Google Assistant Clone: Design Specification

Derived from the Figma community mockup exports:

- `Google Assistant Clone (Community).svg` — 10 frames, each `1080 × 1920` px
- `Group 3.svg` — the 4-dot listening indicator, isolated

**Unit convention:** the mockup frames are `1080 × 1920` px, i.e. a 360 × 640 dp phone at
`xxhdpi`. **Every px value in the source SVG is divided by 3 to get dp.** All dp figures below
are already converted; raw px values are given in parentheses where useful for cross-checking.

---

## 1. Overview & Technical Stack

**Goal:** a fluid, state-driven clone of the Google Assistant interface that bridges the
provided mockups with modern Assistant/Gemini behaviour.

- **UI framework:** Jetpack Compose, Material 3.
- **State management:** `ViewModel` + `StateFlow` over a sealed `AssistantState`
  (`Idle`, `Listening`, `Processing`, `DisplayingResult`).
- **Target paradigm:** *not* a rigid fullscreen app as the mockup frames imply. The Assistant is
  an overlay: a translucent scrim fills the screen, content is pinned to the **bottom half**, and
  the top half is transparent so the launcher/app behind stays visible.
- **AI backend:** stubbed behind an interface. A LiteRT on-device backend drops in later without
  touching UI code.

---

## 2. The Transparent Overlay (core behaviour)

This is the defining trait of the Assistant UI and the mockups do **not** show it — the Figma
frames are opaque white for presentation purposes only.

| Property                | Value                                                                                              |
|-------------------------|----------------------------------------------------------------------------------------------------|
| Window background       | fully transparent (`android:windowIsTranslucent`, `windowBackground = @android:color/transparent`) |
| Activity                | `enableEdgeToEdge()`, no action bar, `windowSoftInputMode=adjustResize`                            |
| Scrim                   | vertical gradient, `Color.Transparent` → `Color.Black @ 0.55α`, top → bottom                       |
| Scrim gradient midpoint | ~40 % of screen height; above that it is effectively clear                                         |
| Sheet occupancy         | bottom **50 %** of screen height, growing to a 90 % cap as conversation content accumulates        |
| Sheet corner radius     | `28.dp` top-start / top-end, `0.dp` bottom                                                         |
| Dismiss                 | tap anywhere in the transparent upper region, or swipe the sheet down                              |
| Enter transition        | `slideInVertically(initialOffsetY = { it })` + `fadeIn`, `300 ms` `FastOutSlowInEasing`            |

The top half must remain hit-testable-through for dismiss but must not capture scroll gestures
intended for the sheet.

---

## 3. Theme & Design Tokens

### 3.1 Accent colours — Material You

The app uses **wallpaper-derived Material You colours**, not the Google brand palette.
`minSdk` is 36, so `dynamicLightColorScheme` / `dynamicDarkColorScheme` are always available and
no version guard is needed.

Four distinguishable accents drive the logo dots, the voice visualizer, the glow bar and the
suggestion grid. The three system accent families only yield three hues, so the fourth is a
lighter tone of `accent1`:

| Slot                 | Light                | Dark                 |
|----------------------|----------------------|----------------------|
| 1 (largest logo dot) | `system_accent1_600` | `system_accent1_200` |
| 2                    | `system_accent3_600` | `system_accent3_200` |
| 3                    | `system_accent2_600` | `system_accent2_200` |
| 4                    | `system_accent1_400` | `system_accent1_400` |

Exposed as `LocalBrandColors`; every component reads the list by index, so the ordering above is
the contract. A Material You build therefore reads as **tonal** rather than four-colour — that is
expected, not a bug.

**Fallback palette.** The original Google brand colours, taken verbatim from the SVG fills, remain
as the non-dynamic fallback (`AppTheme(dynamicColor = false)`) and for previews:

| Token         | Hex       |
|---------------|-----------|
| Google Blue   | `#4285F4` |
| Google Red    | `#EA4335` |
| Google Yellow | `#FBBC04` |
| Google Green  | `#34A853` |

Near-duplicate variants in the export (`#4286F5`, `#EA4235`, `#FABD03`) are Figma rounding
artefacts and collapse onto the four values above.

### 3.2 Surfaces

Surfaces derive from the active `ColorScheme` so they follow the wallpaper too. The mapping:

| Token                             | Material 3 role                 |
|-----------------------------------|---------------------------------|
| Sheet surface                     | `surface`                       |
| Bubble — assistant                | `surfaceContainerHigh`          |
| Bubble — user                     | `surfaceVariant`                |
| Card surface                      | `surfaceContainer`              |
| Action button                     | `surfaceContainerHighest`       |
| Text primary                      | `onSurface`                     |
| Text secondary / action icon tint | `onSurfaceVariant`              |
| Scrim                             | `scrim` @ 55 % light, 65 % dark |

**Icon-grid backgrounds:** the slot's accent at **15 % opacity** behind a fully saturated glyph.

**Semantic exceptions**, which stay off the wallpaper palette by design:

- The extracted mic and Lens rasters are tinted flat (`primary` and `onSurfaceVariant`), which
  deliberately discards their original four-colour Google artwork.

### 3.3 Typography

- **Family:** Google Sans, falling back to `Roboto` / `FontFamily.Default`.
- Measured ink height in the mockup bubbles is ~50–52 px (~17 dp) ascender-to-descender, which
  corresponds to a **16 sp** body size.

| Role            | Size  | Weight | Line height |
|-----------------|-------|--------|-------------|
| Bubble text     | 16 sp | Normal | 22 sp       |
| Card title      | 18 sp | Medium | 24 sp       |
| Card secondary  | 14 sp | Normal | 20 sp       |
| Grid item label | 12 sp | Medium | 16 sp       |

### 3.4 Shapes

- Action chips, icon buttons, avatars, bubbles: `CircleShape`.
- Rich cards (alarm, timer, calendar event): `RoundedCornerShape(16.dp)`
  — the mockup uses `rx=25 px` ≈ `8.dp` for cards and `rx=20 px` for frames; `16.dp` is the
  modernised value.
- Chat bubbles: fully rounded pill, with a reduced radius on the corner facing the speaker.

### 3.5 Elevation / shadow

Every elevated element in the SVG shares one drop-shadow filter:

```
dx = -4px   dy = +4px   blur stdDeviation = 15px   colour = #000000 @ 25%
```

In dp: `x = -1.33`, `y = +1.33`, blur ≈ `5`. Approximate in Compose with
`shadow(elevation = 3.dp, shape = <shape>)`.

---

## 4. Component Geometry (measured from the mockup)

### 4.1 Assistant avatar — 4-dot logo (small, inline)

Appears to the left of every assistant response. Circle radii and centres, relative to the
avatar's own bounding box (raw px → dp):

| Dot    | Colour    | Radius px | Radius dp | Centre offset px (x, y) |
|--------|-----------|-----------|-----------|-------------------------|
| Large  | `#4285F4` | 39.51     | 13.2      | (0, 0) reference        |
| Medium | `#FBBC04` | 23.22     | 7.7       | (+65, +67)              |
| Small  | `#EA4335` | 19.55     | 6.5       | (+65, +19)              |
| Tiny   | `#34A853` | 10.59     | 3.5       | (+94, −3)               |

Overall avatar footprint ≈ **`40 × 40 dp`** (≈ 120 px). It sits with its blue dot roughly on the
bubble's vertical centre, `~14 dp` from the sheet's left edge.

### 4.2 Assistant logo — large / hero

Frame A (the "blob" resting state):

| Dot    | Colour    | Radius px | Radius dp |
|--------|-----------|-----------|-----------|
| Large  | `#4285F4` | 75.78     | 25.3      |
| Medium | `#FBBC04` | 44.53     | 14.8      |
| Small  | `#EA4335` | 37.50     | 12.5      |
| Tiny   | `#34A853` | 20.31     | 6.8       |

Hero footprint ≈ **`80 × 80 dp`**, centred horizontally, sitting ~`290 dp` down the frame.

### 4.3 Listening indicator — 4 dots (Frame B / `Group 3.svg`)

Four equal circles, `48 × 48 px` = **`16 dp`** diameter, `rx = 24 px`.

Left edges at x = `0, 87, 170, 255` px → dp `0, 29, 56.7, 85`; total width `303 px` = **`101 dp`**.
Order left→right: **Blue, Red, Yellow, Green**. The green dot is 1 px taller (`49`) and offset
1 px up — a Figma artefact; render all four identically.

### 4.4 Listening indicator — 4 bars (Frame C, amplitude state)

Same four x positions, width `28 px` = **`9.3 dp`**, `rx = 14 px` (fully rounded).

| Bar | Colour    | Height px | Height dp | Top y px |
|-----|-----------|-----------|-----------|----------|
| 1   | `#4285F4` | 194       | 64.7      | 856      |
| 2   | `#EA4335` | 102       | 34.0      | 901      |
| 3   | `#FBBC04` | 164       | 54.7      | 873      |
| 4   | `#34A853` | 125       | 41.7      | 894      |

All four are **vertically centred** on the same axis (y ≈ 953 px). These heights are the
animation's amplitude extremes — treat `16 dp` (the dot diameter) as the resting height and
`64 dp` as the maximum.

### 4.5 Chat bubbles

| Property                     | px        | dp                                  |
|------------------------------|-----------|-------------------------------------|
| Height                       | 114       | **38**                              |
| Corner radius                | 57        | 19 (fully rounded pill)             |
| Observed widths              | 458 – 875 | 153 – 292                           |
| Horizontal text padding      | 49 – 84   | **16 – 20** (use 20)                |
| Vertical gap between bubbles | ~107      | **36**                              |
| Assistant bubble left inset  | 294       | **98** (leaves room for the avatar) |
| User bubble left inset       | 308       | **103** (right-aligned in practice) |

Multi-line / rich text bubbles grow to `131 px` (**44 dp**) with `rx = 65.5 px`.

### 4.6 Bottom action bar

Consistent across **every** frame — the single most stable element in the mockup.

| Property                   | px                    | dp              |
|----------------------------|-----------------------|-----------------|
| Button diameter            | 135                   | **45**          |
| Button centre x            | 299.5 / 539.5 / 779.5 | 100 / 180 / 260 |
| Spacing between centres    | 240                   | **80**          |
| Top y                      | 1692                  | 564             |
| Distance from frame bottom | 93                    | **31**          |

Row is horizontally centred on the sheet. Icons, left → right:

1. **Google Lens** — 4-colour bracket glyph, drawn at `120 × 120 px` = **40 dp**
2. **Microphone** — 4-colour Google mic, drawn at `90 × 100.286 px` = **30 × 33.4 dp**
3. **Keyboard** — vector, `#4285F4`, two rows of five `~12 px` keys plus a spacebar,
   overall ≈ `70 × 40 px` = **23 × 13 dp**

Both raster icons are embedded in the SVG as PNGs and can be extracted directly:
`image0_0_1` (473 × 527, mic) and `image1_0_1` (240 × 240, Lens).

In the dark variant (Frame F) the same three buttons render as `#1F1F1F` circles with white glyphs.

### 4.7 App/Action launcher grid (top section)

Frames E, G, I show a grid of large circular buttons above the conversation.

| Property      | px                    | dp                        |
|---------------|-----------------------|---------------------------|
| Circle radius | 78.5                  | **26.2** (52 dp diameter) |
| Centre x      | 166 / 406 / 646 / 886 | 55 / 135 / 215 / 295      |
| Column pitch  | 240                   | **80**                    |
| Row centre y  | 172, 402              | 57, 134                   |
| Row pitch     | 230                   | **76.7**                  |

So: **4 columns × 2 rows**, `80 dp` pitch horizontally, `77 dp` vertically. Colour cycle per
column is Blue, Red, Yellow, Green. Label text sits directly above/below each row.

Implement with `LazyVerticalGrid(GridCells.Fixed(4))`; each cell is a circular surface filled with
the brand colour at **15 % opacity** carrying a saturated glyph, with a `12 sp` label beneath.

### 4.8 Rich cards

The app supports **four** capabilities and nothing else: alarms, timers, calendar events, and
placing a call. The mockup's calling and music-player cards are **not built** — see §4.9.

All three cards share one `ActionCard` shape rather than each being bespoke:

| Slot          | Value                                                                      |
|---------------|----------------------------------------------------------------------------|
| Card shape    | `RoundedCornerShape(16.dp)`, `ElevatedCard` on `cardSurface`               |
| Leading glyph | `40 dp` circle, accent @ 15 % opacity, `20 dp` icon                        |
| Title         | `titleMedium` on `textPrimary`                                             |
| Subtitle      | `bodyMedium` on `textSecondary`                                            |
| Trailing      | optional; only the alarm card uses it (a Material 3 `Switch`, per Frame J) |
| Footer        | right-aligned `TextButton` that hands off to the system app                |

| Card           | Title       | Subtitle            | Footer            |
|----------------|-------------|---------------------|-------------------|
| Alarm          | `7:00 AM`   | label               | "Set in Clock"    |
| Timer          | `10:00`     | label               | "Start in Clock"  |
| Calendar event | event title | `EEE d MMM, h:mm a` | "Add to Calendar" |

The Frame J alarm pill (`244 × 92 px` = **81 × 30.7 dp**, `rx = 46`, with an `89 px` ≈ **30 dp**
knob) is what the alarm card's trailing `Switch` corresponds to.

### 4.9 Capabilities not built

The mockup's **calling card** (Frame F: `270 × 333 dp`, dark gradient, 3 × 2 dialer grid, red
end-call button) and **music selector card** (Frame H: provider rows with brand avatars) are
deliberately **out of scope**.

There is no in-app calling UI at all. A call request is a *handoff*: the app emits an
`AssistantAction.Dial` and the native dialer takes over — see §7.2.

Also dropped from the suggestion grid: weather, navigation, translate, reminders and forecast.
The grid is now **one row of four** (alarm, timer, event, call) rather than the mockup's 4 × 2.
The grid's height is derived from the row count, so restoring items reflows it automatically.

### 4.10 Assistant logo

The 4-dot logo (§4.1, §4.2) is slated for removal. `AssistantAvatar` and `AssistantHeroLogo`
remain in `ui/components/AssistantLogo.kt` and are still wired into the conversation stream and
the idle sheet, but no further work should be invested in them.

---

## 5. UI Architecture

Layered inside the bottom sheet, top → bottom:

```
Box (fills screen, transparent)
├── Scrim              — gradient, click-to-dismiss
└── Column (align bottom, 50%–90% height, rounded top corners, surface)
    ├── DragHandle
    ├── SuggestionGrid         — LazyVerticalGrid, 4 columns  (§4.7)
    ├── ConversationStream     — LazyColumn, weight(1f)       (§5.1)
    └── BottomActionBar        — fixed Row of 3 buttons        (§4.6)
└── GlowingLightBar    — absolute bottom edge, Listening only  (§6.1)
```

### 5.1 Conversation stream

- **User query:** right-aligned, rounded pill, faint blue/grey background.
- **Assistant response:** left-aligned. **Correction to the mockup:** modern Assistant renders
  response text directly on the surface *without* a bubble, preceded by the 4-dot avatar. The
  mockup's white assistant bubbles are dated — drop them.
- **Rich cards:** rendered inline in the stream, inset by the `40 dp` avatar gutter so they line
  up with the response text. Alarm, timer and calendar event only.
- **Handoffs** produce no stream item of their own — just the spoken line that precedes them.

---

## 6. Modernization Directives

The Figma mockups are dated; these four additions are required for the clone to feel authentic.

### 6.1 Glowing light bar (bottom edge)

On entering `Listening`, a glowing animated gradient bar appears at the **absolute bottom edge of
the screen** — below the sheet, spanning full width.

- `Box` with `Brush.horizontalGradient` over the four `LocalBrandColors` accents
- `infiniteRepeatable` animation on **alpha** (0.6 → 1.0) and **height** (4 dp → 10 dp) to
  simulate voice amplitude
- gradient offset also animates horizontally for a travelling-glow effect

### 6.2 Voice visualization

The 4 dots (§4.3) must not be static. Drive them with `Animatable` / `updateTransition`:

- bounce vertically, staggered phase per dot
- morph in width between the dot form (§4.3) and the bar form (§4.4) when amplitude rises
- return to the resting 4-dot row when amplitude falls

### 6.3 Haptics

Inject `LocalHapticFeedback`. Fire a short `TextHandleMove`/`LongPress`-class tick when:

- the microphone is activated or deactivated
- a rich action card is interacted with (end call, provider selected, alarm toggled)

### 6.4 Transitions

Wrap rich cards entering the stream in `AnimatedVisibility` with `expandVertically() + fadeIn()`
so they expand smoothly rather than snapping in.

---

## 7. AI Backend Boundary

### 7.1 The interface

The assistant brain is **stubbed** for now and sits behind a narrow interface so a LiteRT
on-device backend can replace it without UI changes.

```kotlin
interface AssistantBackend {
    fun respond(prompt: String): Flow<AssistantReply>
    fun transcribe(): Flow<String>
}
```

- `StubAssistantBackend` is keyword matching, not language understanding — it is throwaway.
  It delays before replying so the `Processing` state is observable, and `transcribe()` dribbles
  out partial transcripts word by word so `Listening` is too.
- The `ViewModel` depends only on `AssistantBackend`; swapping in `LiteRtAssistantBackend` is a
  one-line change in `AssistantViewModel.Factory`.
- `AssistantReply` is a sealed type with three arms, keeping rendering data-driven:
  `Text`, `Card` (alarm / timer / calendar event), and `Handoff`.

### 7.2 Platform handoff

The app never rings an alarm or places a call itself. `ToolAction` values travel from the
ViewModel to the activity over a one-shot `Channel`, and `Context.perform(action)` turns each
into a system intent:

| Action                    | Intent                                      | Notes                                                                                      |
|---------------------------|---------------------------------------------|--------------------------------------------------------------------------------------------|
| `Dial`                    | `ACTION_DIAL` (`tel:`)                      | **not** `ACTION_CALL` — needs no runtime permission and leaves the final press to the user |
| `Dial` (name, not digits) | `ACTION_VIEW` on `Phone.CONTENT_FILTER_URI` | opens contacts filtered to the name; no `READ_CONTACTS` needed on our side                 |
| `SetAlarm`                | `AlarmClock.ACTION_SET_ALARM`               | requires the normal `SET_ALARM` permission                                                 |
| `StartTimer`              | `AlarmClock.ACTION_SET_TIMER`               |                                                                                            |
| `CreateEvent`             | `ACTION_INSERT` on `Events.CONTENT_URI`     | no permission required                                                                     |

`EXTRA_SKIP_UI` is left `false` so the clock app always confirms with the user. Every launch is
wrapped against `ActivityNotFoundException` and falls back to a toast.

**Two different confirmation models**, deliberately:

- **Dial** is emitted as a `Handoff` and fires *immediately* — no card, no confirmation step.
- **Alarm / timer / event** render a card first; the intent only fires when the user taps the
  card's footer button.

The manifest declares `<queries>` for all four intents so the handoffs stay visible under
package visibility filtering.
