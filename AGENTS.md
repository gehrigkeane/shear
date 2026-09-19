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
├── app/   # Android application: activities, Compose UI, Room history. Package dev.gswizz.shear.
└── core/  # Pure Kotlin/JVM: URL extraction, Brave rules engine, offline debouncer, redirect resolver.
           # Package dev.gswizz.shear.core. Its tests run without an Android SDK.
```

`:app` depends on `:core`.
`:core` depends on nothing from Android.

| Component                | Module  | Responsibility                                                              |
| ------------------------ | ------- | --------------------------------------------------------------------------- |
| `ShareReceiverActivity`  | `:app`  | Accept `ACTION_SEND`, coordinate transformation, launch the chooser, finish |
| `MainActivity`           | `:app`  | Compose UI for History and Settings                                         |
| `UrlExtractor`           | `:core` | Locate HTTP(S) URLs without changing surrounding text                       |

## Key Design Decisions

- **Brave's datasets are the sole authority.**
  Cleaning behavior changes only by refreshing the vendored snapshots of `clean-urls.json` and `debounce.json`.
  No ad-hoc heuristics, ever.
- **Never emit a damaged URL.**
  On any parse or transformation failure the original URL is preserved.
  The engine is idempotent: cleaning an already clean URL is a no-op.
- **Native Sharesheet only.**
  Shear never renders or ranks destinations.
- **Latest Android only.**
  `minSdk = targetSdk = compileSdk = 37`.
- **AGP built-in Kotlin.**
  `:app` does not apply `org.jetbrains.kotlin.android`; AGP 9 compiles Kotlin itself,
  using the Kotlin Gradle Plugin version that `:core` puts on the build classpath.
- **Strict by default.**
  Kotlin warnings are errors in both modules, Android Lint warnings are errors, `:core` uses explicit API mode,
  and deprecated Gradle API use fails the build.
- **120 columns everywhere.**
  Every formatter and linter in the repository agrees on the width.

## Development Workflow

`mise` owns the toolchain: JDK 25, the Android command-line tools, and the file linters.
Gradle runs through the wrapper.
`ANDROID_HOME` and `JAVA_HOME` come from mise, so there is no `local.properties`.

```bash
mise install          # toolchain
mise run sdk          # SDK platform 37 and platform-tools
mise run ci           # the gate
```

| Task           | Purpose                                                          |
| -------------- | ---------------------------------------------------------------- |
| `sdk`          | Install SDK platform 37 and platform-tools, accept licenses      |
| `sdk-emulator` | Add the emulator and an arm64 API 37 system image                |
| `avd`          | Create the `shear` virtual device                                |
| `emulator`     | Boot the `shear` virtual device                                  |
| `build`        | Assemble the debug APK                                           |
| `install`      | Install the debug APK on the connected device                    |
| `run`          | Install and launch the app                                       |
| `test`         | Unit tests in all modules                                        |
| `fmt`          | Format Kotlin, Gradle scripts, XML, markdown, and TOML in place  |
| `fmt-check`    | Verify formatting and license headers without writing            |
| `lint`         | Android Lint, workflow lint, spelling                            |
| `ci`           | `fmt-check` + `lint` + `test` + `build`; identical to CI         |
| `rules-update` | Refresh the Brave snapshots and record the upstream commit       |
| `clean`        | Remove Gradle build outputs                                      |

IntelliJ IDEA opens the project directly.
Point its Gradle JVM at the mise-installed JDK once (`mise where java`).

### Verification Requirement

**ALWAYS run `mise run ci` before declaring any task complete.**
CI runs the same command, so a green local run is the only acceptable evidence that work is done.

## Testing

- `:core` uses JUnit 6 through Gradle's test-suite DSL.
  Tests are plain JVM and fast; this is where the engine's golden tests derived from Brave's datasets will live.
- `:app` has no instrumented tests yet.
  Share-flow changes are verified on the emulator: share a page from Chrome, pick Shear,
  confirm the Sharesheet reopens with the cleaned text.

## Commit Conventions

- [Conventional Commits](https://www.conventionalcommits.org): `type(scope): subject`,
  with `!` before the colon for breaking changes.
  Scopes are `app`, `core`, `rules`, `build`, `ci`, `docs`.
- Work lands as stacks of small, reviewable branches via [`gh stack`](https://github.com/github/gh-stack),
  merged bottom-up with squash.
  Each pull request holds one concern.
- Pull request descriptions start with a WHAT section, then a WHY section, then a horizontal rule before anything
  else.
