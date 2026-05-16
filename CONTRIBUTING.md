# Contributing

This document covers how work flows from idea to released version in this repo. The mod uses [Conventional Commits](https://www.conventionalcommits.org/) and [release-please](https://github.com/googleapis/release-please): a discipline you learn once, after which version bumping, tagging, CHANGELOG entries, and the three multi-loader release jars all happen automatically.

## Workflow at a glance

```
idea → feature branch → commits → PR (squash-merged) → main
                                                         |
                                                         v
                                                  release-please
                                                         |
                                                         v
                                            rolling Release PR on main
                                                         |
                                                  (merge when ready)
                                                         |
                                                         v
                                v* tag + GitHub Release + 3 loader jars
```

Day-to-day you only think about the top half. The bottom half is automated.

## What is a "feature"?

A **feature** is one self-contained unit of work that:

- Lives on **one branch**.
- Merges into `main` as **one squash commit** carrying **one Conventional Commit type** (`feat:`, `fix:`, or `feat!:`).
- Produces **one entry** in `CHANGELOG.md` when released.
- Is **fully complete at merge time**, not a stepping stone toward a future, separate change.
- Has **bounded scope** decided before work starts. Mid-stream growth becomes a new feature, not an extension of this one.

Distinguish from:

- **Task**: a single edit, refactor, or chore. Maps to a PR with a non-bumping prefix (`chore:`, `docs:`, `refactor:`, `test:`, `ci:`).
- **Phase**: a roadmap milestone (see `docs/ROADMAP.md`) spanning multiple features over weeks. Phases group features; they are not themselves merged.

## Define the feature before opening the branch

Capture the answers below in a GitHub Issue (preferred; gives the feature a tracking link) or a short personal note. Do not start implementing until all eight are answered.

```markdown
### Feature: <short title; becomes the PR title>

**Conventional Commit type**
- [ ] feat:   new functionality (minor bump)
- [ ] fix:    bug fix (patch bump)
- [ ] feat!:  breaking change (minor while 0.x, major from 1.0)

**Scope**
- In:
  - …
- Out (explicit non-goals to prevent drift):
  - …

**Acceptance criteria** (how you will know it is done)
- …

**Affected modules**
- [ ] core
- [ ] forge-core
- [ ] loader-fabric-1.21.10
- [ ] loader-forge-1.8.9
- [ ] loader-forge-1.12.2

**Test plan**
- Manual UI flow:
- Loaders to runClient against:
- Anything else (mixin coverage, save/load roundtrip, simulator vs. real MC parity):

**Breaking changes?**
- [ ] No
- [ ] Yes, migration notes:

**Reuse audit** (existing code to leverage; see `docs/CODING_GUIDE.md`)
- Functions/utilities already in core:
- Patterns to mirror (e.g. another mixin, another port):
```

If a section cannot be filled, the feature is not defined yet. Go answer the missing pieces before opening the branch.

## Branch & merge rules

- Branch from `main`. Name: `feature/<short-slug>` (e.g. `feature/1.20-support`, `fix/yaw-edge-case`).
- Commit freely on the branch; none of those commit messages reach `main` after squash, so they can be `wip`, `oops`, whatever.
- Sub-branches into the feature branch are allowed for genuinely large features but usually overkill for solo work.
- Open a PR with a **conventional title**. The squash commit takes the PR title as its subject and the PR description as its body, so put `BREAKING CHANGE:` in the description when applicable.
- **Squash-merge only.** Repo settings enforce this; merge commits and rebase merges are disabled.
- Head branch auto-deletes on merge.

## Conventional Commits reference

| Prefix | Effect on version | Example |
|---|---|---|
| `feat:` | minor bump (`0.1.0` → `0.2.0`) | `feat: add 1.20.4 loader` |
| `fix:` | patch bump (`0.1.0` → `0.1.1`) | `fix: yaw wraps incorrectly at 180°` |
| `feat!:` or `BREAKING CHANGE:` in body | minor while 0.x, major from 1.0 | `feat!: switch save format to mothball-string` |
| `chore:` | no bump | `chore: bump fabric-loader to 0.17.4` |
| `docs:` | no bump | `docs: clarify CODING_GUIDE port pattern` |
| `refactor:` | no bump | `refactor: extract input validation` |
| `test:` | no bump | `test: cover dirty-flag edge case` |
| `ci:` | no bump | `ci: cache gradle dependencies` |

### Project-specific judgment calls

- **Save/load format change** → `feat!:`. The planned format is mothball-string notation (for Stratfinder / Mothball interop, see `docs/ROADMAP.md` v1.3.0); any change that breaks roundtrip on existing saved sequences counts as breaking even mid pre-1.0.
- **Removing or renaming an `InputData` field** → `feat!:`. Same reason.
- **Dropping a Minecraft version (loader removed)** → `feat!:`. Users on that version lose support.
- **Adding a Minecraft version (new loader module)** → `feat:`. New capability, nothing breaks.
- **Tweaking the simulator to match real MC physics more closely** → `fix:`. The simulator is the source of truth; per `CLAUDE.md`, divergence from MC is by definition a bug.
- **UI polish without behavior change** → `feat:` if visible to users, `refactor:` if pure cleanup.
- **Updating a mixin to track an MC API change** → `chore:` (not user-visible).

When in doubt, ask: would a user updating the jar have to *do* something? Yes → `feat!:`. Would they notice a new capability? → `feat:`. Would they only notice a bug they hit is gone? → `fix:`. Would they not notice at all? → `chore:` / `refactor:` / `test:` / `ci:` / `docs:`.

## Versioning interaction

- Each squash-merged commit with `feat:` / `fix:` / `feat!:` updates a rolling Release PR on `main` titled e.g. `chore(main): release 0.2.0`.
- Bumps are computed cumulatively: three unreleased `feat:` commits produce one minor bump, not three.
- Merge the Release PR when you want to ship → release-please tags `vX.Y.Z`, creates the GitHub Release, and the existing `release.yml` attaches the three loader jars (`pkc-fabric-1.21.10`, `pkc-forge-1.8.9`, `pkc-forge-1.12.2`).
- The current pre-1.0 phase uses `0.x.y` versions; breaking changes only bump minor. When the mod is stable enough to declare 1.0, push a commit whose body contains `Release-As: 1.0.0`. From there, breaking changes start bumping major per standard SemVer.
