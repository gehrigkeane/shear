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
  Shear targets the current Android release and nothing older.
- **120 columns everywhere.**
  Every formatter and linter in the repository agrees on the width.

## Development Workflow

`mise` owns the toolchain.
Install it, then run the gate:

```bash
mise install
mise run ci
```

| Task        | Purpose                                              |
| ----------- | ---------------------------------------------------- |
| `fmt`       | Format markdown and TOML in place                    |
| `fmt-check` | Verify formatting without writing                    |
| `lint`      | Lint GitHub workflows and spelling                   |
| `ci`        | `fmt-check` + `lint`; identical to what CI executes  |

### Verification Requirement

**ALWAYS run `mise run ci` before declaring any task complete.**
CI runs the same command, so a green local run is the only acceptable evidence that work is done.

## Commit Conventions

- [Conventional Commits](https://www.conventionalcommits.org): `type(scope): subject`,
  with `!` before the colon for breaking changes.
  Scopes are `app`, `core`, `rules`, `build`, `ci`, `docs`.
- Work lands as stacks of small, reviewable branches via [`gh stack`](https://github.com/github/gh-stack),
  merged bottom-up with squash.
  Each pull request holds one concern.
- Pull request descriptions start with a WHAT section, then a WHY section, then a horizontal rule before anything
  else.
