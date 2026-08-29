# AOD Pomodromo — Handoff (Ishan Sahu)

## Status (as of 2026-08-29)

- Code committed and pushed to `main` at `git@github.com:sahuishan01/AOD-Promodoro.git`.
- Two commits on `main`:
  1. `7cec1e4` — initial codebase
  2. `9665350` — CI: wire release signing via secrets; fix TimerEngineTest imports
- **CI is currently failing.** A GitHub Actions run (`33238680888`) completed `failure` on the latest push.
  - Step 7 (`Unit tests`) failed in the previous run; the current run's exact step is not yet confirmed (GitHub's job-log API returned 403 — "admin rights required" — from this host).
- The fix I was about to push before the handoff: rework `TimerEngineTest` imports (`advanceTimeBy`, `assertThrows`) — syntactically sound locally but not yet validated on a real JVM.

## Quick diagnostic (run from repo root on any machine with Gradle)

```bash
./gradlew testDebugUnitTest --console=plain   # isolate the failing test
./gradlew :app:compileDebugUnitTestKotlin     # compile-only, fast feedback
./gradlew assembleDebug                       # confirm APK still builds
```

If `testDebugUnitTest` fails, look at `app/build/reports/tests/testDebugUnitTest/` for the stack trace.

## What the build does (CI — `.github/workflows/android-build.yml`)

1. `actions/checkout`
2. `actions/setup-java@v4` (Temurin 17)
3. `sdkmanager "platforms;android-35" "build-tools;35.0.0"`
4. `python3 scripts/gen_audio.py` → regenerates `app/src/main/res/raw/{rain_drift,night_pad}.wav` (WAVs are gitignored, not committed)
5. `gradle wrapper --gradle-version 8.9`
6. `./gradlew testDebugUnitTest`
7. `./gradlew assembleDebug` → uploads debug APK artifact
8. **Release signing** (optional, gated on secrets):
   - Decodes `secrets.SIGNING_KEY` (base64) to `$RUNNER_TEMP/pomodromo.jks`
   - Exports `KEYSTORE_PATH`
   - `app/build.gradle.kts` reads `KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` to populate a `release` `signingConfig`; only applied to `bundleRelease` when the path is non-empty
   - If secrets are absent, builds an **unsigned** release AAB (fine for testing the Gradle plumbing; not installable on device or uploadable to Play)
9. Uploads three artifacts: `aod-pomodromo-debug-apk`, `aod-pomodromo-release-aab`, `unit-test-results`

## Secrets to add (Repo → Settings → Secrets and variables → Actions)

| Secret | How to produce |
|---|---|
| `SIGNING_KEY` | Base64 of the `.jks` file: `base64 -w0 pomodromo.jks` |
| `SIGNING_KEY_ALIAS` | Whatever you passed to `keytool -alias` (e.g. `aod-pomodromo`) |
| `SIGNING_STORE_PASSWORD` | Password you entered for the keystore |
| `SIGNING_KEY_PASSWORD` | Password for the key (often the same as store) |

Keystore generation (do this on your own machine, **not** in CI):
```bash
keytool -genkeypair -v \
  -keystore pomodromo.jks \
  -alias aod-pomodromo \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 pomodromo.jks > pomodromo.jks.b64   # copy this whole line into SIGNING_KEY
```
Keep `pomodromo.jks` and `pomodromo.jks.b64` backed up securely; do not commit them.

## Known gaps / open work

1. **CI failing** — see diagnostic above.
2. **Launcher icon** — `app/src/main/res/drawable/ic_launcher.xml` is a plain vector; adaptive-icon XML (`<adaptive-icon>` with `<background>` + `<foreground>`) is not yet created. If Play listing requires adaptive icons, add `ic_launcher_adaptive.xml` in `mipmap-anydpi-v26`.
3. **`takePersistableUriPermission` denial UX** — currently only a Toast on revoke/permission-refusal. Consider adding a persistent snackbar or settings warning banner for a smoother experience.
4. **No instrumentation / screenshot / UI tests** — only JVM unit tests exist. Phase 2 §testing mentions Paparazzi + Compose `androidTest`; those are future.
5. **No ProGuard mapping upload** for Play — `proguard-rules.pro` is wired but the mapping file isn't uploaded to Play Console. Add a step to upload `mapping.txt` after `bundleRelease` if you want obfuscated-stack trace readability.
6. **Release AAB not yet uploadable without signing** — once secrets are in, the workflow signs correctly and the AAB is ready for internal/managed Play track upload. After that, add a Fastlane / `upload` step (separate task).

## Architecture recap (one-liner per layer)

- **Engine** — JVM-pure `TimerEngine`, monotonic clock, drift-proof, exposed as `StateFlow<EngineSnapshot>`.
- **UI** — Jetpack Compose Material 3, single-activity `NavHost`, Hilt `@HiltViewModel`s.
- **Persistence** — `DataStore<Preferences>` via `SettingsRepository` (no Room, no sensitive data).
- **Backgrounds** — 6 bundled dark palettes; user image via Photo Picker with Coil (bounded decode, no network fetchers).
- **Audio** — Media3 `ExoPlayer` + `MediaSessionService`; bundled tracks synthesized in CI; picked audio via SAF; Media Gate validates MIME + magic bytes + size.
- **Foreground Service** — `specialUse` FGS + `MediaPlayback` service; LOW-priority notification throttled to 5s buckets.
- **Security posture** — zero network permission; MASVS mapping in `DEVELOPMENT-PLAN.md §4.7`.

## Environment note

This workspace ran on an **aarch64 (Oracle Ampere)** host. The Android SDK `aapt2` shipped via Maven is x86_64-only, so a native build here required `qemu-x86_64-static` binfmt registration (I installed it then cleaned it up — `/usr/local/bin/qemu-x86_64-static` was removed). **Do not attempt a local build on this host**; use the GitHub Actions workflow instead. If you ever need local emulation again, register binfmt carefully:
```sh
printf ':qemu-x86_64:M::\x7fELF\x02\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x02\x00\x3e\x00:\xff\xff\xff\xff\xff\xff\xff\x00\xff\xff\xff\xff\xff\xff\xff\xff\xfe\xff\xff\xff:/usr/local/bin/qemu-x86_64-static:OCF\n' | sudo tee /proc/sys/fs/binfmt_misc/register
```
⚠️ The `\x` escapes must be emitted as **raw bytes** (e.g. via `printf`, not `echo`), or the kernel interprets the magic incorrectly and **breaks every `execve` on the machine** — including `sshd`, which breaks SSH access (I learned this the hard way). Verify with `file /proc/sys/fs/binfmt_misc/qemu-x86_64` afterward (should report `ASCII text`, not binary garbage). A wrong registration can be removed with `echo -1 | sudo tee /proc/sys/fs/binfmt_misc/qemu-x86_64`; worst case, hard-reboot the instance via the OCI Console.

## What I was about to push next

- A corrected `TimerEngineTest` (verified clean locally) plus a retry of the `bundleRelease`/`assembleDebug` compile path. After that, push and re-trigger the workflow. Then mark CI green.
