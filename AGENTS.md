# AGENTS.md

Development guide for Shear.
`CLAUDE.md` is a symlink to this file, so every agent reads the same guidance.

## Project Overview

**Shear is an Android share intermediary that cleans URLs with Brave's rules and returns the result to the native
Sharesheet.**
It receives `ACTION_SEND text/plain`, transforms every HTTP(S) URL in the payload, preserves all surrounding text,
records the transformation in an on-device history, and launches `Intent.createChooser()`.

Shear deliberately has no custom share picker, no clipboard monitoring, no background service, no accounts,
no telemetry, and no cloud component.

## Architecture

```text
shear/
├── app/   # Android application: activities, Compose UI, Room history, DataStore settings. Package dev.gswizz.shear.
└── core/  # Pure Kotlin/JVM: URL model, Brave rules, cleaning pipeline, redirect resolver. Package dev.gswizz.shear.core.
           # Its tests run without an Android SDK.
```

`:app` depends on `:core`.
`:core` depends on nothing from Android.

### `:core` packages

| Package  | Contents                                                                                                   |
| -------- | ---------------------------------------------------------------------------------------------------------- |
| `url`    | `UrlParts` byte-preserving split, `UrlReference` RFC 3986 resolution, `PercentCodec` Chromium unescaping |
| `psl`    | `PublicSuffixList`, ICANN section by default; decides "same site" for debouncing                           |
| `rules`  | `MatchPattern` (Chromium extension patterns), `BraveRules` loader with diagnostics, `RulesSource`          |
| `engine` | `Debouncer`, `QueryFilter`, `QuerySanitizer`, `UrlCleaner` fixed-point loop, trace model                    |
| `net`    | `RedirectResolver`, `RedirectTransport`, `OkHttpRedirectTransport` with `GuardedDns`, `AddressPolicy`      |
| root     | `UrlExtractor`, `Shear` facade (`clean`, `process`, `needsNetwork`)                                        |

### `:app` components

| Component                 | Responsibility                                                                                |
| ------------------------- | --------------------------------------------------------------------------------------------- |
| `ShearApplication`        | Owns the hand-wired `AppGraph`; loads the engine off the main thread                          |
| `ShareReceiverActivity`   | Accept `ACTION_SEND`, clean, optionally resolve with progress, record, launch chooser, finish |
| `ChosenComponentReceiver` | Attach the Sharesheet's chosen app to the history event                                       |
| `MainActivity`            | Navigation 3 shell over History, event detail, and Settings; prunes history on open           |
| `data.HistoryRepository`  | Room-backed history with retention and redaction on one ordered worker                        |
| `data.SettingsRepository` | DataStore Preferences: redirect mode, retention, retain originals                              |
| `ui.*Screen`              | One file per screen: ViewModel, `*Route` (collects state), stateless `*Screen`                 |

## Key Design Decisions

- **Brave's datasets are the sole authority.**
  `core/src/main/resources/brave/` holds byte-for-byte snapshots of `clean-urls.json`, `debounce.json`,
  and `query-filter.json`, plus `conditional-trackers.json` extracted from brave-core; `UPSTREAM` pins the commits.
  Cleaning behavior changes only by running `mise run rules-update`.
  No ad-hoc heuristics, ever.
- **Brave's strict clean, in Brave's order.**
  Debounce, then query filter, then sanitizer, repeated until the URL is a fixed point.
  That fixed point is what makes cleaning idempotent.
- **Never emit a damaged URL.**
  `UrlParts` reassembles exactly the text it parsed.
  On any failure the last valid URL is kept and the reason is recorded in the trace.
- **Same site by public suffix.**
  `core/src/main/resources/mozilla/public_suffix_list.dat`
  (refreshed by `mise run psl-update`) answers the eTLD+1 question the way Brave's debouncer asks it.
- **Network only on request.**
  Redirect resolution is off by default.
  When on, `OkHttpRedirectTransport` follows nothing itself, sends no cookies, credentials, or referrer,
  reads no bodies, and refuses non-public addresses at DNS time.
  Smart mode fetches only hosts in `core/src/main/resources/shear/opaque-redirectors.txt`.
- **Native Sharesheet only.**
  Shear never renders or ranks destinations; it excludes itself from the chooser.
- **Persistence never blocks the share.**
  History writes run on a single ordered worker;
  the event id is allocated before any I/O so the chooser callback can attach after process death.
- **Latest Android only.**
  `minSdk = targetSdk = compileSdk = 37`.
- **AGP built-in Kotlin.**
  `:app` does not apply `org.jetbrains.kotlin.android`; AGP 9 compiles Kotlin itself,
  using the Kotlin Gradle Plugin version that `:core` puts on the build classpath.
- **Strict by default.**
  Kotlin warnings are errors in both modules, Android Lint warnings are errors, `:core` uses explicit API mode,
  and deprecated Gradle API use fails the build.
- **MPL-2.0 header on every Kotlin file.**
  Spotless inserts it on `mise run fmt` and `mise run fmt-check` fails without it.
- **One formatter, no options.** ktfmt in kotlinlang style formats Kotlin and Gradle scripts through Spotless.
  There is nothing to configure and nothing to argue about.
- **120 columns everywhere.** ktfmt, `.editorconfig`, rumdl, and taplo all agree on the width.

## Development Workflow

`mise` owns the toolchain: JDK 25, the Android command-line tools, and the file linters.
Gradle runs through the wrapper.
`ANDROID_HOME` and `JAVA_HOME` come from mise, so there is no `local.properties`.
SDK packages and the emulator lifecycle go through the `android` CLI, the successor to `sdkmanager`;
`avdmanager` remains only for AVD creation because the CLI's `create` cannot pin a system image.

```bash
mise install          # toolchain
mise run sdk          # SDK platform 37 and platform-tools
mise run ci           # the gate
```

| Task            | Purpose                                                          |
| --------------- | ---------------------------------------------------------------- |
| `sdk`           | Install SDK platform 37.2, build-tools, and platform-tools       |
| `sdk-emulator`  | Add the emulator and an arm64 API 37 system image                |
| `avd`           | Create the `shear` virtual device                                |
| `emulator`      | Boot the `shear` virtual device and return once it is ready      |
| `emulator-stop` | Shut down the `shear` virtual device                             |
| `build`         | Assemble the debug APK                                           |
| `install`       | Install the debug APK on the connected device                    |
| `run`           | Install and launch the app                                       |
| `test`          | Unit tests in all modules                                        |
| `fmt`           | Format Kotlin, Gradle scripts, XML, markdown, and TOML in place  |
| `fmt-check`     | Verify formatting and license headers without writing            |
| `lint`          | Android Lint, workflow lint, spelling                            |
| `ci`            | `fmt-check` + `lint` + `test` + `build`; identical to CI         |
| `rules-update`  | Refresh the Brave snapshots and record the upstream commits      |
| `psl-update`    | Refresh the Public Suffix List and record its checksum           |
| `clean`         | Remove Gradle build outputs                                      |

IntelliJ IDEA opens the project directly.
Point its Gradle JVM at the mise-installed JDK once (`mise where java`).

### Verification Requirement

**ALWAYS run `mise run ci` before declaring any task complete.**
CI runs the same command, so a green local run is the only acceptable evidence that work is done.
Check the exit code, not the log: a filtered log can hide a failed task.

## Testing

- `:core` uses JUnit 6 through Gradle's test-suite DSL.
  Every rule in the vendored snapshots is a golden test synthesized from its own match pattern
  (`engine/GoldenSupport.kt`), and `IdempotenceTest` proves `clean(clean(x)) == clean(x)` over the whole corpus.
  The resolver is tested against a scripted `FakeTransport` under `runTest`;
  `OkHttpRedirectTransportTest` uses MockWebServer under `runBlocking`, because real sockets need real time.
- `:app` uses Robolectric 4.17 on SDK 37 with the JUnit 4 runner.
  The test JVM needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`
  (set in `app/build.gradle.kts`) because Robolectric's SDK 37 emulation reaches into JDK internals.
  Tests that show the progress overlay pause the shadow choreographer,
  otherwise the indeterminate animation keeps the main looper from idling.
  Compose screens are tested with the v2 `createComposeRule` under `@GraphicsMode(NATIVE)`;
  Espresso is pinned to 3.7 because older releases reflect on `InputManager.getInstance`, which Android 17 removed.
  Content below Robolectric's short viewport is asserted with `assertExists`, not `assertIsDisplayed`.
- Share-flow changes are also verified on the emulator: share a page from Chrome, pick Shear, confirm the Sharesheet
  reopens with the cleaned text and the destination appears in History.

## Commit Conventions

- [Conventional Commits](https://www.conventionalcommits.org): `type(scope): subject`,
  with `!` before the colon for breaking changes.
  Scopes are `app`, `core`, `rules`, `build`, `ci`, `docs`.
- Work lands as stacks of small, reviewable branches via [`gh stack`](https://github.com/github/gh-stack),
  merged bottom-up with squash.
  Each pull request holds one concern.
- Pull request descriptions start with a WHAT section, then a WHY section, then a horizontal rule before anything
  else.
