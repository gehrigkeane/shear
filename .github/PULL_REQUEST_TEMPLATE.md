<!--
Shear is a personal project. Pull requests are welcome but there is no support commitment and no roadmap; see
CONTRIBUTING.md. Title the PR as a Conventional Commit, e.g. `feat(core): unwrap skimresources redirects`.
-->

## What

<!-- One or two sentences: what changes for a user or for a contributor. -->

## Why

<!-- The motivation. If an upstream Brave rule change or an Android behavior prompted this, link it. -->

---

## Privacy and network

<!-- Shear's contract: nothing leaves the device unless the user enabled redirect resolution; history stays local. -->

- [ ] No new network calls, permissions, or stored data
- [ ] Adds or changes network calls, permissions, or stored data; described above and reflected in the README

## Testing

<!-- Rule or engine changes: name the golden tests. UI or share-flow changes: device or emulator and Android version. -->

- [ ] `mise run ci` passes locally
- Tested on:

## Screenshots

<!-- UI changes only, light and dark. Delete this section otherwise. -->

| Before | After |
| ------ | ----- |
|        |       |

## AI disclosure

<!-- Select exactly one. -->

- [ ] No AI assistance
- [ ] AI-assisted (drafting, review, or refactoring with human authorship)
- [ ] AI-generated (substantially produced by an agent, reviewed by a human)

## Checklist

- [ ] Commits are Conventional Commits and each is reviewable on its own
- [ ] Cleaning behavior changes only through Brave rules; no ad-hoc heuristics were added
- [ ] Brave snapshot changes were produced by the rules-update task and `UPSTREAM` is updated
- [ ] README or AGENTS.md updated, or no documentation change is needed
