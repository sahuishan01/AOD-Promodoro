# AOD Pomodromo — Development Plan

**Document:** Development plan & architecture blueprint
**Scope:** Always-On-Display-style Pomodoro timer for Android
**Baseline:** minSdk 34 (Android 14) · targetSdk 35 · Foreground Service for background timer · Local-first, zero network
**Owner input applied:** API 34+ floor · FGS-based background execution · Delivered as plan doc

---

## 1. Scope Summary (Hard Boundaries)

| In scope | Out of scope |
|---|---|
| Pomodoro engine with user-set work/rest durations (persisted) | Accounts, auth, cloud sync |
| Backgrounds: bundled solid colors & gradients + user-picked images | Social features, telemetry, ads |
| Audio: bundled ambient/lo-fi tracks + optional user-picked audio, play/pause + volume | Analytics, crashlytics, third-party CDNs |
| AOD-grade timer screen with burn-in mitigation + battery care | OAuth, WebViews, in-app purchases |
| Foreground Service for background running | Network access of any kind (no `INTERNET` permission) |

---

## 2. Tech Stack & Architecture

### 2.1 Language & UI Toolkit

| Concern | Decision | Rationale |
|---|---|---|
| Language | **Kotlin 2.x (K2 compiler)** | First-class coroutines for the timer engine; null safety shrinks the bug class for state machines. |
| UI | **Jetpack Compose (BOM)** | A reactive state-driven UI is the natural fit for a ticking, phase-aware timer screen. Views/XML was rejected: more boilerplate, harder dynamic theming for custom backgrounds, more fragile accessibility wiring. KMP/Flutter/React Native: overkill and adds surface area. |
| Async model | **Coroutines + StateFlow** | Deterministic timer ticks; cancellable scopes bind engine to service lifecycle. |
| Dependency Injection | **Hilt** (manual DI acceptable fallback) | Standard patterns — injectable `TimerEngine`, `SettingsRepository`, service binding. Manual DI is viable given the 1-app-module shape. |

### 2.2 Architecture Pattern: MVVM + Unidirectional Data Flow (UDF)

Single-Activity architecture. One `MainActivity` hosting a Compose `NavHost` with two destinations (`Timer` and `Settings`). All state flows down; events flow up.

```
User events → Screen/ViewModel → Repository/Engine → StateFlow
                                                     ↓
                                                Compose UI
Service (FGS) ──binds──> TimerEngine (single source of truth)
Media (ExoPlayer/Media3) ──> ambient playback + media session
```

**State ownership rules:**

- `TimerEngine`: the only source of truth for phase/time. Lives in the FGS once started; ViewModels are thin clients of its `StateFlow`.
- `SettingsRepository`: wraps `DataStore<Preferences>`; screens never touch DataStore directly.
- `AmbientAudioRepository`: exposes bundled track metadata; playback is delegated to a Media3 `MediaSessionService`.
- Screens render a snapshot `UiState` data class — no business logic in composables.

### 2.3 Key Dependency Choices

| Layer | Library | Version-family notes |
|---|---|---|
| UI kit | `androidx.compose.*` (Material 3, Navigation) | Compose BOM pinned in version catalog |
| Image loading | **Coil 3** (`io.coil-kt.coil3:coil-compose`) | Bounded-size decode (`size(w,h)`), system decoders only — safe media decode surface |
| Media playback | **Media3 ExoPlayer + Session** (`androidx.media3:exoplayer`, `:session`) | `MediaSessionService` for `mediaPlayback` FGS typing; better codec/lifecycle hygiene than raw `MediaPlayer` |
| Persistence | `androidx.datastore:datastore-preferences` | Single-file proto-free settings; replaces fragile `SharedPreferences` |
| Picker (images) | `PickVisualMedia` Activity Result Contract (Photo Picker) | Zero-permission image picking on minSdk 34 |
| Picker (audio) | `OpenDocument` Activity Result Contract (SAF) | Zero-permission audio picking; persistable URI grants |
| Logging | Timber (DEBUG tree only) | Stripped in release via R8 rules |
| DI | `com.google.dagger:hilt-android` | kapt-free via KSP |
| DI runtime | Kotlin coroutines (`kotlinx-coroutines`) | Engine tick on monotonic clock |
| Time/coroutine testing helpers | `kotlinx-coroutines-test` + Turbine | Deterministic tick assertions in JVM tests |
| UI test helpers | Compose `uiTest` + Paparazzi (screenshot) + UiAutomator | Pyramid: JVM unit → Robolectric → instrumented Compose/UIA |

### 2.4 Module / Package Structure (single `:app` module)

Packages enforce boundaries. Multi-module is rejected for this scope; a pure-JVM `:core-timer` split *may* be added later purely for test speed.

```
com.aod.pomodromo
├── core/
│   ├── di/                  # Hilt modules: CoreModule, DataModule, MediaModule
│   └── util/                # Formatters, tick helpers, result wrappers
├── timer/                   # JVM-pure engine — no Android imports
│   ├── TimerEngine.kt       # State machine: Idle → Working → Resting → Complete
│   ├── TimerPhase.kt        # enum + phase rules + transition validator
│   └── TickClock.kt         # Monotonic clock interface (fakeable for tests)
├── data/
│   ├── settings/            # SettingsRepository, DataStore accessors, keys
│   ├── background/          # BackgroundCatalog (bundled defs), PickedImagePolicy
│   └── media/               # AmbientAudioRepository, BundledTrack catalog
├── service/
│   ├── TimerForegroundService.kt   # FGS, foregroundServiceType=specialUse
│   └── NotificationDelegate.kt     # LOW-priority notification, pause/skip actions
├── media/
│   ├── AmbientPlaybackService.kt   # MediaSessionService (Media3)
│   └── PlaybackController.kt       # Play/pause/volume/skip API surface
├── ui/
│   ├── theme/               # Color pair catalog, burn-in-safe palettes, Type tokens
│   ├── screens/
│   │   ├── timer/           # TimerScreen + TimerViewModel + PhaseIndicator
│   │   └── settings/        # SettingsScreen + SettingsViewModel (+ pickers)
│   └── components/          # BackgroundSurface, AodClock, VolumeSlider
├── MainActivity.kt          # Only Activity; exported=false
├── PomodoroApp.kt           # Application; Hilt entry point
└── pomodoro.kt              # Optional: internal helpers
```

**Visibility rule:** `core`, `timer`, `data`, `service`, `media` must not reference `ui`. `ui` screens talk only to ViewModels + lightweight facade classes.

---

## 3. Phase-by-Phase Implementation Plan

Durations are effort estimates, not commitments. Exit criteria must all pass before phase closes.

### Phase 0 — Foundation & Tooling (0.5–1 wk)

**Goal:** reproducible, leak-safe, signing-ready project skeleton.

- [ ] Gradle Version Catalog (`gradle/libs.versions.toml`) with pinned Kotlin 2.x, Compose BOM, Hilt, Media3, Coil, DataStore, Timber, Turbine, coroutines-test.
- [ ] `compileSdk 35`, `minSdk 34`, `targetSdk 35`.
- [ ] `.gitignore` rules for `local.properties`, keystore files, `.env`.
- [ ] Generate upload keystore (RSA 4096, ≥10y validity) **outside the repo**; wire signing via env/`local.properties` — never hardcode.
- [ ] Blank `MainActivity` + Compose scaffold; theme tokens skeleton.
- [ ] Timber debug-tree bound to `BuildConfig.DEBUG` only.
- [ ] Security lint gate (below) + Dependence/audit task wired in CI.
- [ ] Define global "Definition of Done": all unit/UI tests green, no `StrictMode` violations on main thread, permission manifest stays at the Phase-7 budget.

**Testing strategy:** skeleton compiles; `assembleDebug`/`assembleRelease` green; lint + dependency verification tasks defined.

---

### Phase 1 — Timer Engine (0.75 wk)

**Goal:** a deterministic, JVM-pure, drift-proof state machine.

- [ ] `TimerPhase`: `Idle`, `Working`, `Resting`, `Complete` with validated transitions (`pause/resume/reset/skipPhase`).
- [ ] `TimerEngine.start/pause/resume/reset/skip/configure` public API; emits `StateFlow<EngineSnapshot>` with total elapsed as `Duration`.
- [ ] Ticking built on a **monotonic clock** (`System.nanoTime`/`Kotlin TimeSource`), not wall clock; immune to device time edits.
- [ ] Tick interval engine-side: 1 s cadence with drift-correction (single scheduled phase-end alarm, resched resumed ticks).
- [ ] Duration validation: work ∈ [1..120] min, rest ∈ [1..60] min; invalid config rejected at boundary.
- [ ] Public event sealed class (`PhaseCompleted`, `Tick`, `StateChanged`) for UI + service consumption.

**Testing strategy (heaviest JVM-first phase):**

- [ ] `kotlinx-coroutines-test` + Turbine virtual-time tests: every expected transition covered, full pomodoro cycle, pause mid-tick, reset, skip.
- [ ] Drift proof: fake monotonic clock advancing >1 s builds correct elapsed time.
- [ ] Property-style randomized phase sequences never end in invalid states.
- Coverage target ≥ **85%** on `timer/`.

---

### Phase 2 — Core UI: AOD-Grade Screen Shell (1 wk)

**Goal:** minimal, high-contrast timer UI with burn-in & battery hygiene, before backgrounds/audio plug in.

- [ ] Material 3 adaptive theme: fixed contrast-checked color pairs with preferred dim variants (no pure-white on black requirements).
- [ ] `BackgroundSurface` abstraction (solid/gradient/image implementations added in Phase 4).
- [ ] `AodClock` digits: monospace rendering with fixed-width spans to kill layout jitter; `String` formatting lives in `core.util.TimeFormatter`.
- [ ] Burn-in mitigation scaffolding:
  - [ ] **Pixel shift**: small periodic randomized offset (±N px) applied at intervals ≥ 60 s when idle-view long.
  - [ ] **Auto-dim**: optional session dim after configurable minutes; brightness clamped, never forced to max.
  - [ ] Palette preference defaults to dark background + tinted accent; stark white avoidable by user choice.
- [ ] `PhaseIndicator`: screen-readable phase badge with contentDescription.
- [ ] Screen-on behavior via `View.keepScreenOn = true` (no WakeLock) + graceful on-phase-change haptic tick (no sound without user opt-in).
- [ ] Navigation shell, `Timer` ↔ `Settings` destinations.

**Testing strategy:**

- [ ] Paparazzi screenshot tests per theme & per phase state.
- [ ] Compose UI test: phase badge semantics, clock digits don't jitter width, keepScreenOn flag set.
- [ ] Accessibility scan on screenshots (touch targets ≥ 48dp, contrast ≥ 4.5:1 for primary text).

---

### Phase 3 — Settings Persistence (0.5 wk)

**Goal:** crash-proof settings with safe defaults, validation at boundary.

- [ ] `SettingsRepository` over `DataStore<Preferences>` with keys: work/rest duration, volume, background categorical choice (`bundled` vs `userImage`), UI prefs (auto-dim, haptics).
- [ ] Read-time validation: persisted values outside accepted bounds → fallback to default + log-free-safe recovery (no user-exposed crash).
- [ ] Picked background **URI persisted as string** only after permission persistence attempt (Phase 4 hardens behavior).
- [ ] No passwords, tokens, or PII — settings-only scope.
- [ ] `onPause/done` resumes idempotently; UI reflects persisted values at startup.

**Testing strategy:**

- [ ] JVM tests with fake `DataStore`: defaults, persistence round-trip, out-of-bounds reset.
- [ ] Robolectric pass for repository against real DataStore op semantics.

---

### Phase 4 — Background Customization (1 wk)

**Goal:** bundled solids/gradients + user-picked images, with the full media-safety chain.

- [ ] `BackgroundCatalog` (code-defined); hex specs attached to theme entries; gradients deterministic.
- [ ] **Image pick path:**
  - `PickVisualMedia(ImageOnly)` contract → user picks image → persist string `Uri` **after** attempting `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` (success on Photo Picker; falls back to session-only grant on providers that refuse).
  - Handling revoke/re-pick: catch `SecurityException`/`FileNotFoundException`, fallback to bundled default, toast user, reset the preference.
- [ ] Media safety policy `PickedImagePolicy` (see §4.4): MIME from `ContentResolver.getType` + magic-byte sniff; accepted `image/jpeg|png|webp`; size cap (e.g. ≤ 25 MB); megapixels bounded (avoids OOM bomb).
- [ ] Coil wiring: bounded decode `size(w,h)`; disable SVG/network fetchers; `diskCachePolicy` conservative; never render file-path strings.
- [ ] `BackgroundSurface` implementations: `SolidSurface`, `GradientSurface`, `ImageSurface` (user image), `PickImageButton` sheet entry in Settings.

**Security notes:** no storage permission needed (Photo Picker), URI lifecycle honestly handled.

**Testing strategy:**

- [ ] Compose UI: each surface type renders without crash; pick flow via mocked `ActivityResultLauncher`.
- [ ] Policy tests: MIME spoofing rejected, oversized image rejected, transparent fallback on revoked URI; screenshot tests per bundled background.

---

### Phase 5 — Ambient Audio (1 wk)

**Goal:** bundled tracks + optional user-picked audio with proper session & FGS typing.

- [ ] Bundled tracks in `assets/ambient/` with manifest metadata (title/id) — code-resident list, strings in `strings.xml`.
- [ ] `AmbientPlaybackService`: Media3 `MediaSessionService`; owns ExoPlayer; exposes `MediaController` API to ViewModel/PlaybackController.
- [ ] Playback features: play/pause, volume (0..1.0 persisted in Settings), skip optional.
- [ ] **User-picked audio path (optional):** SAF `OpenDocument` contract; MIME sniff (`audio/*` requires `audio/mpeg|ogg|wav|m4a|flac`), size cap (e.g. ≤ 100 MB) plus duration guard; same persistable-URI discipline as Phase 4.
- [ ] When audio plays → service typed `mediaPlayback`; notification with play/pause.
- [ ] Volume changes flow through Media3 directly (no `AudioManager` global channel games).

**Testing strategy:**

- [ ] Robolectric/JVM: controller state, volume clamping, catalog validity.
- [ ] Instrumented: MediaController round-trip; dismissed-provider revoke fallback; volume persistence round-trip.
- [ ] Policy tests mirrored to image path: spoofed MIME, oversize rejection.

---

### Phase 6 — Foreground Service & Notifications (1 wk)

**Goal:** timer survives backgrounding with honest FGS typing and low-battery-cost behavior.

- [ ] `TimerForegroundService` (`foregroundServiceType="specialUse"`, `android:stopWithTask="false"`), holding the `TimerEngine` scope; ViewModels bind via explicit `Binder`/`ServiceConnection` (or Hilt-injected facade).
- [ ] A `LOW` priority notification: phase line + reset/skip/pause actions, `FLAG_UPDATE_CURRENT`; updates **throttled** (phase change / every 5 s max) — battery mindful.
- [ ] Notification runtime permission (`POST_NOTIFICATIONS`) requested permission-rationalized on first run; FGS still runs if denied, UI fallback dialog shown.
- [ ] No `AlarmManager.setExactAndAllowWhileIdle` — phase-end scheduled in-app via the engine's monotonic clock; docD battery-optimization prompt gated as opt-in in Settings (with explicit, honest rationale).
- [ ] Pause-from-notification flows through the same engine events; `Service.onDestroy` cancels scope and stops ticker.
- [ ] Cold launch from notification → deep link to the `Timer` screen.

**Testing strategy:**

- [ ] UiAutomator: start timer → background app → verify phase progression continues; act on notification buttons.
- [ ] Robolectric: service binding on boot; notification construction only when engine active.
- [ ] Battery heuristics documented (e.g., tick cadence, notification cadence) — manual battery profile checklist.

---

### Phase 7 — Security Hardening & Release (1 wk)

**Goal:** obfuscated, signed, leak-free, Play-ready build; audit-complete.

- [ ] R8 full-mode obfuscation + resource shrinking; rules strip `Log/Timber` (see §4.5).
- [ ] Manifest hardening: `android:exported="false"`, no WebViews/no exported receivers, `usesCleartextTraffic="false"`, `allowBackup="false"` documented (settings are disposable).
- [ ] Dependency audit pass; lock verification metadata (`enableDependencyLocking`).
- [ ] `SigningConfig` uses env/local keystore; ignore actual keystore files from VCS; CI secrets for pipeline.
- [ ] `apksigner verify --print-certs` on release AAB; verify version code drift.
- [ ] Play Data Safety: complete form → "**No data collected, no data shared**"; verify library manifest doesn't smuggle network.
- [ ] Content rating (Everyone); declare "no ads, no IAP/OAuth, no analytics".
- [ ] MASVS mapping sheet (§4.7) completed and archived in `/docs/security/`.
- [ ] Screenshot set + release notes for Play listing.

**Testing strategy:**

- [ ] Release-mode smoke suite: start → set durations → pick background → play bundled audio → background app → notification → uncloggable exit.
- [ ] Binary check: zero network access calls at runtime expectations (instrumented tracing on debug build with leak assertions).

---

## 4. Security Measures (Consolidated)

Every measure below is binding. Deviations need explicit waiver + rationale logged in `/docs/security/waivers.md`.

### 4.1 Permission Budget (smallest possible set)

| Permission | Required? | Justification |
|---|---|---|
| `FOREGROUND_SERVICE` | Yes | Mandatory for any FGS. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Yes | Enables `specialUse` FGS typing for the timer path (API 34+ requirement). |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Only if ambient playback runs in background | Prefer `MediaSessionService` w/o code-level permission; declare only when playing audio in background. |
| `POST_NOTIFICATIONS` | Runtime, optional | Requested with rationale; FGS keeps ticking even on user denial (UI fallback). |
| `VIBRATE` | Optional | Phase-change haptic cue. |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*` | **NO** | Explicitly forbidden by scope; Photo Picker + SAF cover all media access. |

Any addition requires a documented, approved waiver.

### 4.2 Data Storage

- Single `DataStore<Preferences>` file in app-internal storage (`/data/data/...Files/` path), never on shared/external volumes.
- Keys: durations, volume, background mode flags, picked-URI string, UI prefs. **None of these are sensitive.**
- No DB in scope (Room/SQLite unused → no crypto discussion needed). If a future DB lands, apply SQLCipher/Tink before release.
- `allowBackup="false"` (default documented: plain user settings, sync-safe anywhere doesn't matter.) Flip + document if behavior changes.

### 4.3 Offline-First & External Media Discipline

- **Zero network usage:** `usesCleartextTraffic="false"` + `networkSecurityConfig` deny cleartext — defense in depth, plus no `INTERNET` permission. Verify no fetched-fetcher registered in Coil/ExoPlayer slot.
- Third-party media reaches the app only via Photo Picker or SAF.
- **Persisted URI discipline:** for any picked file, attempt `takePersistableUriPermission` immediately; on `SecurityException`, downgrade to session-only grant and re-pick eligible. Catch all revoke paths.
- Picked-URI strings live exclusively in on-device DataStore. Never sent outside the app boundary (no export, no share intent on the URI).

### 4.4 Third-Party Media Validation (The Media Gate)

Applies to both image and audio Picked policies — one shared class (`data/media/PickedMediaPolicy`):

- MIME determination uses `ContentResolver.getType(uri)`; **never** trust the extension.
- Secondary magic-byte pass for picked images (JPEG/PNG/WebP byte sniff) — a spoofed name fails loudly.
- Allow-list only:
  - Images: `image/jpeg`, `image/png`, `image/webp`
  - Audio: `audio/mpeg`, `audio/ogg`, `audio/wav`, `audio/x-m4a`, `audio/flac` (finalize after test validation)
- Size caps: images ≤ 25 MB; audio ≤ 100 MB; decode dimensions bounded via Coil `size()` to prevent pixel bombs.
- Decoder surface: Coil's system decoders only (disable network fetchers/SVG); ExoPlayer's upstream decoder chain only (no custom extension loaders).
- Graceful reject: on violation → single toast + reset preference to bundled default. Never crash-loop.
- Media "path" values (`File.getPath`) never used — content URIs only.

### 4.5 Release Hardening

- **R8 full mode:** obfuscation + resource shrinking. ProGuard rule strip-logging (`-assumenosideeffects` on `android.util.Log` / Timber when `BuildConfig.DEBUG` is false).
- **Keystore:** generated per §Phase 0 outside repo; loaded from `local.properties`/CI secrets; gitignored; AAB v2/v3 signed; verify `apksigner verify --print-certs`.
- **Manifest hardening:** `android:exported="false"` on Activity (only one); no exported providers/receivers/services other than the FGS + MediaSessionService which require explicit exported configuration (carefully enumerated).
- **Debug hygiene:** `BuildConfig.DEBUG`-gated Timber only; `data/media/dev` paths unreachable in release; no stack traces in user paths.
- **Leak checks:** leak assertions in release smoke tests; `StrictMode` sanity in debug instrumentation.
- Dependency hygiene: lock verification metadata + dependency audit task scheduled on CI mainline.

### 4.6 Play Store Compliance

- **Data safety form:** "No data collected, shared, or linked" — validate against `mergedrelease` manifest & a runtime audit; recheck every library graph bump.
- **Target SDK:** 35 (current policy floor honored); Track policy changes.
- **Ads/IAP:** not applicable; declare so (if IAP/ads arrive, route through a new waiver).
- **Content rating:** Everyone.
- **Privacy policy:** point the listing to a local in-repo `/docs/privacy-policy.md` (static "no data collected" statement).

### 4.7 OWASP MASVS Mapping (audit must list per item)

| Measure | MASVS Category | Control(s) |
|---|---|---|
| Settings-only storage, app-internal, no sensitive data | MASVS-STORAGE | STORAGE-1, STORAGE-2 |
| `allowBackup="false"` documented | MASVS-STORAGE | STORAGE-2 |
| Single Activity, exported=false; no IPC leaks | MASVS-PLATFORM | PLATFORM-1, PLATFORM-3 |
| URI persistence discipline (SAF/Photo Picker) | MASVS-PLATFORM | PLATFORM-3 |
| No WebView, no JS surface | MASVS-PLATFORM | PLATFORM-2 |
| Media validation gate + bounded decode | MASVS-RESILIENCE, MASVS-PLATFORM | RESILIENCE-4 (input validation), PLATFORM-3 |
| No cleartext traffic, no network | MASVS-NETWORK | NETWORK-1, NETWORK-2 |
| R8 obfuscation, signed build, no debug leaks | MASVS-CODE | CODE-1, CODE-2, CODE-3, CODE-4 |
| Root/debug/tamper detection | MASVS-RESILIENCE | RESILIENCE-1..3 — **documented N/A** (non-sensitive app), waiver logged |
| Crypto (none required) | MASVS-CRYPTO | CRYPTO-1 — **documented N/A** (no sensitive data) |
| Auth flows (none) | MASVS-AUTH | **N/A, waiver logged** |
| Privacy (no collection) | MASVS-PRIVACY | PRIVACY-1..4 validated via Data Safety audit |

Per-release completion required; output archived in `/docs/security/masvs-audit.md`.

---

## 5. Risk Register (Lightweight)

| Risk | Likelihood / Impact | Mitigation |
|---|---|---|
| OLED screen burn-in on long sessions | High / High | Periodic pixel shift (§Phase 2), auto-dim option, avoided stark-white defaults, recommended theme defaults; user chooses themes |
| Battery drain vs AOD look | Medium / High | No WakeLock; screen-on via View flag; LOW-prio notification; throttled notification updates; FGS typed `specialUse` honestly |
| FGS denied/blocked (e.g., aggressive OEM power policy) | Medium / High | Engine still ticks when screen on; opt-in battery-optimize rationale; UI fallback messaging on `POST_NOTIFICATIONS` denial |
| Picked media URI revoked / file moved | Medium / Medium | Permission persistence attempt, `SecurityException` fallback, deterministic toast + reset |
| Malicious/spoofed media picks | Low / High | Media Gate (§4.4): MIME+magic, size caps, bounded decode, deterministic fallback |
| URI leakage to external share/logging | Medium / High | URIs stored only in on-device DataStore; no share-intent on URI; log strips (§4.5) |
| Keystore/secrets leak | Low / Critical | Keystore outside repo, gitignored, env-based loading, CI secrets |
| Dependency chain smuggles network | Low / Medium | Dependency locking + audit; manifest & runtime network audit on release |
| Time drift / jumpy ticks | Medium / Medium | Monotonic clock + drift-correction tested in Phase 1 virtual time |
| Usability regressions from over-contrast themes | Medium / Medium | Paparazzi snapshot suite; default palette safety enforced in Phase 2 |
| Play review rejection (Data Safety mismatch) | Low / High | §4.6 audit per release; signed screenshot set |

---

## 6. MVP Cut Line (Smallest Shippable)

Ship with **Phases 0–3 + slice of Phase 2 completed** — tops ~3.5 weeks:

| In MVP | Deferred post-MVP |
|---|---|
| Timer engine (work/rest, phase transitions, pause/resume/reset/skip) | User-picked image backgrounds (Phase 4 variant) |
| AOD-grade UI with bundled **solid-color backgrounds** (no gradients/images) | Gradients catalog |
| Settings persistence (durations + default theme choice) | Ambient audio (Phase 5) |
| Burn-in mitigation (pixel shift + auto-dim scaffolding) | Foreground Service + notifications (Phase 6), assuming screen-on usage validated |
| MASVS audit N/A items cleaned up | Media3 integration rollback risk management |

MVP deliberately excludes media player and FGS to hit the smallest compliant ship; those reenter in Phase-reentry with dedicated hardening tracks.

---

## 7. Global Testing Pyramid

| Tier | Scope | Tools | Targets |
|---|---|---|---|
| JVM unit | Engine, repository, policy, value validation | JUnit4 + coroutines-test + Turbine | ≥ 85% on `timer/`, ≥ 70% on data/repository |
| Screenshot | Theme × phase grid, backgrounds | Paparazzi | Golden on CI |
| Instrumented Compose | Navigation, picker flows, semantics | `androidTest` + Compose test, `ActivityScenario` | All critical screens |
| Instrumented Service/Media | FGS lifetime, MediaSession round-trip | Robolectric/UiAutomator + Media3 Unit | Service contract |
| E2E happy path (smoke) | Run timer → pick bg → play bundled → background → notification | UiAutomator, manual battery checklist | Per release |

Standardize all tests through shared Fixtures + `TestHooks`; never `sleep()` real time — virtual time only.

---

## 8. Open Items & Decisions Deferred

- Hilt vs manual DI — Hilt assumed; revisit after Phase 0 bootstrap.
- Bundled audio license vetting — before Phase 5 start, require license-clean or record-at-own risk waiver.
- Exact "LOW" notification cadence — confirm performance per device before locking the phase.
- Media Gate allow-list final audio MIME set — finalize with representative-device soak tests.
- Battery-optimization opt-in copy — draft in Phase 6, reviewed MASVS-wise before ship.

---

*End of plan. All phases include explicit checklists, testing discipline, and audit output. Approval to start Phase 0 requested.*
