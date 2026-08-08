# Strat Finder v1 handoff (2026-08-08, post-QA)

Continuation document for the next session. Read this first; the previous handoff (`stratfinder-handoff-2026-08-08.md`, decisions D1-D12 and the original v1 plan) and `CONTEXT.md` are the background. Everything below happened on `feature/stratfinder` after the rebase onto dev `c2b46dc`.

## 0. State

The branch carries the rebased history (`84e67a7`, `c5a89d0` on top of dev) plus this session's uncommitted working tree, which is the entire Strat Finder product. The user commits themselves; the ready-to-paste message is in section 7. `.claude/` is local session tooling and stays out of the commit. Saves under `loader-forge-1.8.9/run/client/` are untracked test data.

## 1. What shipped

Strat Finder v1 per the original plan, then three in-game QA rounds that reshaped it. Component map:

- `core/.../anglesolver/stratfinder/StratVariants.java`: enumeration. One-tick shifts of existing W/SPRINT/A/D/S events within +-12 ticks of each jump (onsets, releases, holds, single-tick tap moves as two-row one-key edits with edits=1); yaw shapes per the playability rule (section 2); the nine `nt[momentum|air]` strafe patterns (canonical W+SPRINT rebuild of the segment rows, one strafe key per phase, JUMP rows preserved and strafe-free, momentum dF chain on top). Dedup by a signature over row keys + defaultInputs + DF tick set + F tick set. Every variant re-derives input samples row-side (keepify convention, `deriveDebugSamples`) and stamps effort FAST + stopOnFeasible. The witness is always variant "self" (label constant; the UI shows "original").
- `SaveBoxes.java`: `Fixtures.buildBoxes` promoted; test-side Fixtures delegates.
- `StratFinder.java`: the sweep. Witness = `SaveIO.buildSaveFile(store, ..., fullDebug=true)` from the live session; per variant a scratch InputData/AngleSolverState/engine driven HeadlessSolve-style under a per-sweep cancel token; on success `engine.apply()` onto the scratch rows, the bogus apply-deviation cleared (it compares against derived boxes), moved start captured, and the applied state serialized as a snapshot. Ranking: original first, feasible first, fewest edits, then solve time (the objective margin was dropped: it measured the offset past the goal-edge constraint, which is the landing pad's near edge, a constant for every feasible line).
- `StratFinderController.java` (core): wires the widget; apply = `beginTempTrajectory` + `applySnapshotJson`; Find and apply preserve manual edits: the controller tracks `lastAppliedSnapshotJson` and only restores the temp when the live rows still match it row-for-row, otherwise the edited state becomes the new baseline.
- `ui/anglesolver/StratFinderWidget.java`: its OWN window since the last round (View menu > Strat Finder, `settings.viewStratFinder`, rendered from `AngleSolverWindow.render` beside the Velocity Map; closing the window cancels a running sweep). Find/Cancel, budget slider (default 2000 ms), progress, ranked table (Variant / Edits / Solve), row click applies, arrow keys step after the first click, Keep/Reapply banner, canary warning, only-original explanation.
- Test twins delegate to the product: `StratSubstitutions`, `SimplifyLoop.deriveDebugSamples`, `Fixtures.buildBoxes`. `HpkSubstScreen` is the benchmark twin by construction.
- Forge 1.8.9 sim-verify batch is opt-in now: key J is only registered when `PKC_SIMVERIFY` is set (value = directory, `1` = default `simverify/`). It alarmed normal play with stale staged saves failing on the wrong world.

## 2. Domain rules learned this session (treat as spec, they came from the user's hands-on review)

- "No turn" means the playability rule, not a full-segment dF chain: dF=0 pins from startTick+1 THROUGH the last JUMP row; the air phase is a free strafe aerial. The user's hand-found f2f no-turn (D-strafe runway rows 0-4, clean W+SPRINT+JUMP fire row, A-strafe flight with free solver yaws) is the reference shape. Full-segment chains are almost never feasible and were my initial mistake.
- fmm(k) per the validated grammar (`StratTemplates.plans()`, verified against the Plan class: `post`/`patch` entries are ticks AFTER the fire): an UNSPRINTED momentum jump (fire keys W+JUMP, no SPRINT), W-only for the next k-1 ticks, SPRINT engaging exactly k ticks after the fire, mid-air. It is not delayed sprint on the runway. The j012 example "1t fmm to 2t fmm = SPRINT@1later" is consistent.
- The objective margin (offset past the goal-edge constraint) is degenerate as a variant ranking: the edge is the landing pad's near side, so every feasible line shows the same number (the user saw +0.85 on every row). Solve time discriminates; a metric-based ranking needs the combinedV4 port.
- The in-game auto-save owns the open save file: QA edits rewrite it. Witness files used for acceptance baselines change under the user's hands; re-dump before relying on them.
- The test instance cannot join servers, so hpk captures are useless there (live sim diverges from server-world recordings and every canary fails). Test files must be crafted from the user's own local-world saves.

## 3. Approved next task: wire the template timing families in place

The named vocabulary (fmm, pessi, run jams, mark, bwmm) was parked together with the generation layer (D1), but only the generation REALIZATION failed the V-gates (synthesized runways, constraint translation). The user has approved promoting the key-pattern grammar itself, applied in place:

1. Promote the `StratTemplates.plans()` key patterns to `core/main` stratfinder (fmm(k), pessi(k), run(d)+jam, mark(side,k), bwmm composites). Only the per-tick key schedules relative to a fire tick; no runway synthesis, no constraint translation, no time-translation, no new jump ticks. `HpkTemplateScreen` keeps its own parked realization; share only the plan definitions so the vocabulary cannot drift.
2. Apply each family at the witness's EXISTING jump ticks: reinterpret the rows around each fire tick with the family's key patches, clamped to the segment; skip families that need more approach ticks than exist. Constraints exactly as placed (D2), free start within the t0 constraint (D3).
3. Compose with the yaw shapes and strafe patterns so variants like `fmm2/nt[D|A]` exist. Bound the family x shape cross product deliberately and state the sweep cost; today's sweep is already ~30 variants on bf_neo with infeasible ones burning full budget.
4. Acceptance gate: `loader-forge-1.8.9/run/client/parkourcalculator/bf_neo.json`. The user says a no-turn exists there and it requires fmm. Baseline (section 5): every current no-turn form is infeasible.
5. Remaining follow-ups after that, in the user's priority order so far: metric-based ranking (combinedV4 port), per-arc strafe phases for multi-jump strats, sweep pruning (ReachBound rung) so infeasible variants stop burning full budget.

## 4. Product surface (for QA)

View > Strat Finder opens the window (no longer inside the Angle Solver window). Find sweeps the current session state; the original row restores; Keep commits; arrow keys step after the first click. Budget slider 250-10000 ms. Solver hotkeys are dev's V/H/I/O; sim-verify is J only under `PKC_SIMVERIFY`. Headless: `PKC_STRATFIND_FILE=<save> ./gradlew :core:test --tests "*StratFinderFileScreen"` (+`PKC_STRATFIND_MS`), output in the test-results XML system-out.

## 5. Verification state and baselines

- Full `-PslowTests` suite green after the rebase and after every change round; default `:core:test` and `tableStyleCheck` green; all three loaders build (fabric one from the handover round; forge jars rebuilt every round since).
- Tests: `StratVariantsTest` (always-on: enumeration, momentum-chain and pattern invariants, keepify derive), `StratFinderSweepTest` (SlowSolverTests, j012 canary at 3000 ms, streaming, cancel), `StratFinderFileScreen` (env-gated).
- Benchmark twin j012 at 2000 ms: canary -0.000, `SPRINT@1later` +0.146 (byte-consistent with the validated session), plus nt45 and the nine patterns all INFEAS there; a full 34-capture subst sweep now costs roughly 20 s more per capture in full-budget burns.
- f2f acceptance (plain W+SPRINT witness `f2f-stratfinder_test.json`): `nt[D|A]` feasible 153 ms (the user's hand-found no-turn, rediscovered), also `nt[D|-]`, `nt[D|D]`, `ja`; strafe-free `nt` and `nt[-|-]` infeasible, so the D-momentum strafe is the enabler on that jump.
- bf_neo baseline (2000 ms, 30 variants, canary feasible 1441 ms): feasible timing variants `A@12holdlonger` 360 ms, `SPRINT@23holdlonger` 436 ms, `A@12releaseearlier`, `SPRINT@23releaseearlier`, `A@1later`, `A@14later`; EVERY nt/ja/pattern INFEAS. This is the before-picture the fmm wiring must beat.

## 6. Traps and practicalities

- Scratch `engine.apply()` publishes a bogus apply-deviation against the derived boxes; `StratFinder` clears it before serializing. Do not remove that.
- The witness snapshot must be built with `fullDebug=true` or specs degrade to always-sprint (the old Fixtures trap, now product-side).
- `Item.original` is keyed on the label "self", not edits==0 (shape variants of the witness also have zero edits).
- The dedup signature must keep covering whatever new variant dimensions get added (it grew defaultInputs and F ticks for nt45; families changing other solver fields need the same treatment).
- A one-tick W release after the f2f jump makes that line unsolvable; do not use W-releases to craft test files there.
- Sweeps run variants sequentially with the full engine per solve (validated budget semantics); parallelizing engines would change what a 2000 ms budget means.
- Heavy screens still need `-PtestHeap=3g`; solver changes need `-PslowTests`; run supervision stays in-session (never delegated).
- `PKC_SUBST` full-population sweeps: state the cost first; the pattern rows added real burn.

## 7. Ready-to-paste commit message for the working tree

```
feat(anglesolver): Strat Finder, ranked strat variants in their own window

Promotes the validated substitution engine into the product and grows
it through three in-game QA rounds. Core package
anglesolver/stratfinder: StratVariants enumerates one-tick shifts of
existing W/SPRINT/A/D/S events within 12 ticks around each jump
(onsets, releases, holds and single-tick tap moves), yaw shapes per
the playability rule (nt pins dF=0 from the segment start through the
last jump press, ja exempts that press, nt45 switches to Force 45
with F/dF pins stripped) and the nine nt[momentum|air] strafe
patterns that rebuild the segment as a canonical W+SPRINT run with
one strafe key per phase; all variants keep the placed constraints
and tick structure, dedupe on a rows+inputs+dF/F signature, and
re-derive input samples row-side per the keepify convention.
StratFinder solves each variant on a scratch copy of the session
snapshot (FAST, stop on feasible, free start within the t0
constraint, per-sweep cancel token, 2000 ms default budget) and ranks
feasible first, then fewest edits, then solve time; the objective
margin was dropped after proving degenerate (a constant pad offset).
Strat Finder is its own window (View menu): Find with budget and
progress, ranked table with the original pinned on top, row click
applies rows plus result as a temp trajectory and retriggers the sim,
arrow keys step through, Keep/Reapply resolves the temp state, and
manual edits survive Find and apply (temp restore only happens while
the session still matches the last applied variant). Acceptance: the
finder rediscovers a hand-played no-turn (nt[D|A]) from a plain
W+SPRINT witness.

Test twins delegate to the product engine (StratSubstitutions,
SimplifyLoop.deriveDebugSamples, Fixtures.buildBoxes) so
HpkSubstScreen benchmarks the shipped code; j012 reproduces the
validated canary and SPRINT@1later numbers. New tests:
StratVariantsTest (always-on enumeration, shape-chain and pattern
invariants, keepify derive), StratFinderSweepTest (SlowSolverTests)
and StratFinderFileScreen (PKC_STRATFIND_FILE, headless sweep of any
save). The Forge 1.8.9 sim-verify batch is opt-in: key J exists only
when PKC_SIMVERIFY is set (the key moved from V during the dev rebase
because the solver hotkeys own V; docs updated).
Handoff: docs/research/stratfinder-v1-handoff-2026-08-08.md
```

## 8. Kickoff prompt for the next session

```
Read docs/research/stratfinder-v1-handoff-2026-08-08.md fully before doing
anything; it is the authoritative handoff (state, corrected domain rules,
approved next task, traps). Background: stratfinder-handoff-2026-08-08.md
(decisions D1-D12) and CONTEXT.md.

We are on feature/stratfinder; the tree is committed. Build the approved
next task exactly per handoff section 3: promote the StratTemplates
key-pattern grammar (fmm(k), pessi(k), run jams, mark(side,k), bwmm
composites) into the product enumeration as in-place timing families
applied at the witness's existing jump ticks: constraints exactly as
placed, no runway synthesis, no constraint translation, no new jump ticks;
the parked generation realization stays parked. Compose the families with
the yaw shapes and strafe patterns so variants like fmm2/nt[D|A] exist,
bound the cross product deliberately, and keep the benchmark twins
delegating to the product code. Acceptance gate: the finder must find a
feasible no-turn on
loader-forge-1.8.9/run/client/parkourcalculator/bf_neo.json, which
requires fmm (baseline in handoff section 5: every current no-turn form is
infeasible there; re-dump the file first, the in-game auto-save may have
changed it).

Work discipline: spot-check before any full-population run, state the real
cost of any run over 5 minutes before launching it, heavy screens always
get -PtestHeap=3g, and solver changes need the full -PslowTests suite. You
never run git commit, push, or branch commands; ask me at commit points
and provide the message.
```
