# Contributing

The mod uses [Conventional Commits](https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please). Get the commit prefix right and versioning, tagging, the CHANGELOG, and the three loader release jars all happen automatically.

## Workflow

Two standing branches: `main` holds released code only, `dev` is the integration branch.

Features:
1. Branch from `dev` (`feature/<slug>`).
2. Commit freely. Branch commits are discarded on squash, so `wip` messages are fine.
3. Open a PR against `dev` with a **Conventional Commit title** (e.g. `feat: add 1.20.4 loader`). The squash commit takes the PR title as its subject, so the prefix is what drives the version bump. For a breaking change, add `BREAKING CHANGE: <migration note>` to the PR description.
4. QA before merging: a quick in-game pass on each loader whose code changed (core-only change: one loader is enough). A feature that fails QA does not merge; everything on `dev` is always releasable.
5. Squash-merge; the branch auto-deletes.

Fixes:
- Bug exists in the released version: branch from `main` (`fix/<slug>`), PR against `main`, squash-merge, release immediately. Then merge `main` back into `dev` right away so the fix rides along and the next train merge stays clean.
- Bug only exists on `dev`: branch from `dev` and fix it there.

The weekly train: `weekly-train.yml` opens a `dev` to `main` PR on Sundays when `dev` is ahead. Merge it with a **merge commit, never squash**; the changelog is built from the individual commits it carries. A `dev` ruleset blocks the auto-delete-on-merge from removing the branch.

Shipping is automated: every `feat:` / `fix:` / `feat!:` commit reaching `main` updates a rolling `chore(main): release X.Y.Z` PR. Merge that PR to release; release-please tags `vX.Y.Z` and cuts the GitHub Release as a draft. Publishing the draft (after a changelog glance) triggers `release.yml`, which builds and attaches the three loader jars and publishes the three Modrinth versions.

## Commit types

| Prefix | Version bump | Example |
|---|---|---|
| `feat:` | minor | `feat: add 1.20.4 loader` |
| `fix:` | patch | `fix: yaw wraps incorrectly at 180` |
| `feat!:` (or `BREAKING CHANGE:` in body) | minor while 0.x, major from 1.0 | `feat!: switch save format to mothball-string` |
| `chore:` `docs:` `refactor:` `test:` `ci:` | none | `chore: bump fabric-loader` |

Unsure which? Ask what a user updating the jar would notice. Must *do* something to upgrade: `feat!:`. New capability: `feat:`. A bug they hit is gone: `fix:`. Nothing: `chore:` / `refactor:` / `docs:` / `test:` / `ci:`.

Project-specific calls:
- Changing the save format, or removing/renaming an `InputData` field: `feat!:`. It breaks roundtrip with existing saves and Stratfinder/Mothball interop.
- Dropping a Minecraft version (loader removed): `feat!:`. Adding one (new loader module): `feat:`.
- Making the simulator match real MC physics more closely: `fix:`. The simulator is the source of truth, so divergence from MC is by definition a bug.

## Before you start a feature

No form to fill in, just be clear before you branch:
- **Scope.** What is in, and what is explicitly out so the work does not drift mid-stream.
- **Reuse.** Check `core/` and `docs/CODING_GUIDE.md` for existing utilities, ports, and mixins to mirror instead of writing new code.
- **Test plan.** Which loaders you will `runClient` against, and what to look for (UI flow, save/load roundtrip, simulator vs. real MC parity).

A large effort spanning many features over weeks is a "phase" (see `docs/VISION.md`). Phases group features; they are not merged as one.

## Versioning notes

- Bumps are cumulative: three unreleased `feat:` commits make one minor bump, not three.
- Pre-1.0 (`0.x.y`): breaking changes only bump the minor. To declare 1.0, merge a commit whose body contains `Release-As: 1.0.0`. After that, breaking changes bump major.
- Do not hand-edit `mod_version` in `gradle.properties`; release-please manages it.

## ImGui table styling

Every table goes through `ThemeManager` so they all look alike. The `:core:tableStyleCheck` task (part of `./gradlew build`) enforces two rules:
- Open tables with `ThemeManager.beginStandardTable` / `beginStandardClickableRowsTable` / `beginStandardKeyValueTable`, not raw `ImGui.beginTable`.
- Do not push table-slot style colors or write hex / `ImVec4` color literals under `core/.../ui/`. Add a named token to `ThemeManager` instead.
