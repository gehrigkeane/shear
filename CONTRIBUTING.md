# Contributing

Shear is open source under the MPL-2.0 so the code can be inspected, audited, and reused.
It is not a community project.
It exists to remove one person's inconvenience and iterates on exactly that.

There is no roadmap, no support commitment, and no expectation of a response.
If Shear almost does what you want, fork it.

## If you open a pull request anyway

- Set up with `mise install` and keep `mise run ci` green.
  CI runs the same command.
- One concern per pull request.
  Title it as a Conventional Commit, for example `feat(core): unwrap skimresources`.
- Fill in the pull request template, including the AI disclosure.
- URL cleaning behavior comes from Brave's datasets only.
  Pull requests that add heuristics are declined.
- Refresh Brave snapshots with the provided task; never edit them by hand.

## Licensing

Contributions are licensed under the [MPL-2.0](./LICENSE).
Every Kotlin source file carries the MPL header and the build fails without it.
