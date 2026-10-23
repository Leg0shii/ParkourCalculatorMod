# Angle-solver tests: the map

A saved jump (capture) lives in a check folder, and `ProblemsTest` validates it for that check. To add
coverage you drop a capture (or a tiny sidecar) into a folder. No Java change, no capture name in any test.

`ProblemsTest` and the other engine-driving suites are tagged `@Category(SlowSolverTests.class)` and
excluded from the default `:core:test` run; pass `-PslowTests` to run them (CI always does). See
AGENTS.md "Tests" for the full slow list and when a full run is required.

The env-gated probes, dumps, screens and benches are not tests and live in their own source set,
`core/src/research/` (map and run commands in its README).

```
anglesolver/
  ProblemsTest.java        every capture under resources/problems/<check>/ is validated for that check
  OptimizeVsFastTest.java  gh-398 invariant on captures/gh398-optimize-2jump: Optimize's answer is never
                           worse than Fast's, and the run publishes at least two incumbents so the live
                           panel moves and Cancel keeps the best found so far
  SeedSweepTest.java       gh-486 gates on captures/gh486-cross2-freestart (1.8.9, 2 jumps, free start): the
                           "Fast (multi-start)" preset (seedSweep node, best-of-N parallel Fast over the start
                           box) is never worse than a single Fast and reaches the in-game Fast basin that
                           motivated the ticket; Optimize adopts the previous successful solve as its
                           incumbent (solver chain starts with "incumbent") and never ends worse than it;
                           a pinned start passes through the sweep instantly (gh398-optimize-2jump)
  YawDirectionConstraintTest.java gh-553 (SlowSolverTests): dF >= 0 / dF <= 0 over a tick range (the
                           yaw may only turn one way, no turn-out). YawOrder carries consecutive-tick F
                           inequalities and one-sided absolute F walls that the YawTies fold does not
                           absorb; the SLP solves them as exact yaw rows, the certified B&B propagates
                           them between the per-tick arcs and splits overlapping pairs in order, the
                           closed form runs the tie cascade (YawOrderDp). Pins the carry rules, the
                           projection, an SLP, a closed-form and a B&B solve of a one-way range on j121,
                           the wrong-way range staying rejected, and the j121 neo engine solve; the
                           pattern B&B accepting an order spec and landing j345 (gh553-j345-dfneo).
  YawDirectionCrossCheckTest.java  gh-553 (SlowSolverTests, one case per FAST solve/ capture under a 60 s
                           budget): re-solves the capture with one-way dF rows its own solved path
                           already satisfies, so a solution exists by construction and a miss is a
                           regression: three random turning ticks (twice), every turning tick, the
                           longest one-way run, the first (seam) and last tick, zero-turn ticks held
                           >= 0 and <= 0, a minimum turn 0.01 and 1e-3 deg under the real one, a
                           dF = 0 next to one-way rows, and the random rows on a 3 s Optimize (up to
                           60 ticks). Found the free-start gap (df-openchain-free-start, j925, j335)
                           and the relaxation-recovery gate (j022 min turn) on 2026-10-18. The
                           Optimize budget is three times the FAST time of the same rows, at least
                           6 s: the dual chain only runs its relaxation recovery with 3 s left
                           (RecoveryLadder.RELAX_MIN_REMAINING_NANOS), so a 3 s Optimize skipped
                           the stage that lands j022 and j024 in milliseconds on FAST.
  OverConstrainedChainTest.java  gh-454 (SlowSolverTests): a segment whose dF = 0 constraints tie every
                           tick to one heading (gh454-p2s-overconstrained capture, THOROUGH) fails
                           within 20 s and carries the "Overconstrained" notice label with the
                           "one heading ... misses by" explanation behind it
  GraphPathObjectiveGateTest.java  objective gates that solve THROUGH the full Optimize graph (not dualChain),
                           asserting the ENGINE's shipped objective (getObjectiveValue), which is computed with the
                           post-solve scenario and so honors yaw-lock. j021-rinav1-01 must reach the deterministic
                           optimum (engineObj >= 1067.8637, div 0 = not yaw-locked, so the recompute is valid); the
                           degenerate-tick ascent in the fold stage carries it. loopmm-3jump-lands LANDS per the
                           engine (engineObj >= -279.3 block edge via the pattern B&B stage). NOTE: the leaf snap
                           can adopt loopmm as a fully YAW-LOCKED stage, so the harness recompute (toGameFacings via
                           the non-yaw-locked lastSpecDebug scenario) may re-round short (bounded div); the test
                           bounds that divergence and flags that landing is yaw-lock-dependent - VERIFY IN-GAME. Also a
                           determinism guard (j021 solved twice, engineObj+recompute bit-identical)
  FreeStartTranslationTest.java  translation-aware free-start scoring: zero-width translated score
                           byte-equals the pinned score (razor-proof, j004, j318); the
                           free-translate-edge synthetic solves and adopts its known +X box-edge
                           optimal translation end to end (terminal adoption step)
  WrapWindowIlsTest.java   wrap-window lattice ILS stage (|gf| <= 360 hard cap): candidate sets are
                           distinct/capped/deterministic; bounded span-16 descent from the rung snap
                           point (resources/points/) reaches translated viol <= 3.5e-5 at a fixed
                           eval cap; wrap-class results survive locked-rows JSON round trip
                           byte-exact; PKC_WRAPILS_FULL=1 + PKC_WRAPILS_TAG runs the env-gated full
                           replication with kicks (1.5e-5 bar on rung)
  LegalModeTest.java       legal/record objective mode: goal-wall selection (X@49lo on the proof
                           spec; velocity/EQ/cap/off-tick/off-axis never selected; ties refuse);
                           deterministic legal solve on the synth-legal-shortfall fixture with the
                           reported shortfall and every hard wall met
  CrossAxisSignTest.java   dX vs dZ constraints (mcc11-cross-axis-sign capture, 1.21.3): the recorded
                           path is feasible and needs opposite dominant signs on its two cross-axis
                           constraints; the closed form enumerates the per-constraint sign branches (all +1,
                           all -1, then the rest) and solves; SLP keeps the two half-walls per constraint
                           with the sign of the current iterate and solves from its dual seed; the two
                           half-wall slacks reproduce the exact |dX| - |dZ| slack
  LegalModeGh459Test.java  gh-459 (SlowSolverTests): legal mode on a landable jump (gh459-legal-mode
                           capture, FAST) lands 15/15 at the goal wall with a non-positive shortfall,
                           both on the plain Fast graph and with the seed stage starved to 1 ms (the
                           receding-horizon path that used to stop 0.92 short once the wall was dropped)
  RazorLegalReplayTest.java  byte-exact replay pins for the three delivered rung legal attempts
                           (legal / wrap720 / turn360): locked RAW rows realized without wrapping,
                           hard walls feasible under the rung patch, shortfall within 1e-9 of the
                           recorded value (model-drift tripwire)
                           (solve/loopmm-tight-t39: the loopmm misses capture with its shipped-
                           disabled pad wall ENABLED; the tight Z@71 >= -279.3 pad is reachable
                           only while the Z inertia gate is held open across the momentum
                           reversal, so this pins the pattern B&B's keep-alive branch,
                           THOROUGH 45 s)
                           (solve/loopmm-tight-t39-fast: the SAME capture under FAST effort,
                           same keep-alive pin on the shorter chain, budget 90 s)
                           (solve/gh313-j121-dfneo: gh-313 / gh-553; hpk j121 neo with dF <= 0
                           on T39..T46, the yaw may only decrease through the neo. Retired by
                           ruling in issue 372 (dF = 0 only), carried again since issue 553: the
                           SLP holds the walls as exact yaw rows and the certified B&B as ordered
                           sine-table arcs, so it solves on FAST through the certified B&B)
                           (solve/gh553-j345-dfneo: gh-553; hpk j345 3jmm neo with dF <= 0 on
                           T39..T43 and T46, X MAX against a knife-edge X wall on T41 (the human
                           path clears it by 9e-6). The certified B&B exhausts its FAST budget
                           without an incumbent; the pattern B&B lands it in ms on the zx1@6
                           pattern, so this pins that the pattern B&B accepts an order spec and
                           that its per-pattern closed form runs the ordered tie cascade)
                           (solve/gh553-j925-dfle: gh-553; the gh283-j925-farseed free-start capture
                           with dF <= 0 on T15..T17, which its own cold solve already satisfied; the
                           free-start joint solve declined every order spec, now it runs the tie
                           cascade around the unordered joint solve, 0.3 s)
                           (solve/gh553-j335-dfle: gh-553; the gh386-j335-lowx free-start run with
                           dF <= 0 on T5, T11 and T13 from its own solve. The unordered joint solve
                           flips tick 4 by 180 deg, so the cascade must tie a link that sits on the
                           wrap, and the landing comes from the free rescue's centre chain through
                           the relaxation recovery, which declined every F row before; 5 s)
                           (solve/gh553-j022-minturn: gh-553; j022-1bmhbfly with a minimum turn
                           0.01 deg under the solved turn on T34 (the seam), T36 and T37 against
                           its knife-edge X@44 wall. The SLP stalls at 1e-8 on a sine-table bucket
                           there, so the closed form's seed must land through the relaxation
                           recovery; pins that stage carrying the order, 70 ms)
                           (solve/nix-full-t1: long multi-jump free start; accepted as not solving
                           after the CMA-ES removal (issue 374) until the multi-jump seam work lands)
                           (solve/gh283-j925-farseed: gh-283; hpk j925 momentum+neo with the start
                           dragged ~2 blocks outside its tick-0 footprint box, cold rows; pins the
                           seed-position-independent free-start solve, FAST 20 s: bestTranslate's
                           conflict fallback must place conflicted candidates at the min-violation
                           translation, never at the seed)
                           (solve/gh283-j990-cold: gh-283; hpk j990 dF=0 momentum chain with the
                           seed ~2 blocks outside the start box, cold rows; pins the exact
                           prefix-arc theta enumeration, the recoverStart-scored ladder fractions,
                           and the theta micro-polish certify in the joint free-start dual,
                           FAST 20 s)
                           (solve/inertia-1tick-neo: 1.8.9 neo whose landing is only feasible via
                           a single-tick X inertia zeroing (vX dips under the 0.005 threshold
                           mid-flight and re-accelerates after); the free relaxation is truly
                           infeasible, so this pins the B&B rescue's single-tick zx1@k patterns
                           and its dF=0 entry through the FacingPrefold-gated facing-wall check,
                           FAST 20 s)
                           (solve/inertia-1tick-neo-t31: the SAME capture with the window started
                           ON the zeroing tick (startTick 30), so the seed vX 0.00498 is below the
                           threshold and dead on arrival; pins the seed-velocity normalization
                           (ExactJumpModel.zeroSubThresholdVelocity at buildPhys), without which
                           every relaxation carries the phantom carry and no k>=1 pattern can
                           represent a tick-0 zeroing, FAST 20 s)
                           (solve/thousand-1-dup2: #422 M1 acceptance; free-start multi-jump
                           MAX X, THOROUGH 30 s, byte-exact at objective >= 6523.307 (reliably
                           6523.3080, beats both releases); the 10 s budget was timing-flaky on
                           this floor, 30 s is the honest budget; sole canonical thousand gate,
                           the FoldDriver/CertifiedBnb engine-test duplicates were removed)
                           (solve/j1150-noturn-inner: #422 M1 acceptance on the PRECISE
                           inputs_gone spec with the p2 no-turn schedule, THOROUGH 10 s,
                           byte-exact at objective >= -2805.2990460856336, the p2 witness;
                           FAST is deliberately not gated here, 3.3e-4 short, an M2a item)
                           (solve/j154-noturn-ja-inner: #422 M1 acceptance on the corrected
                           t34-plate spec, FAST, byte-exact at objective <= -1599.7001161289918,
                           the in-game-verified F3 witness)
  LevelSetAscentTest.java  level-set objective ascent (gh-290): on keep-out-wall captures where the
                           chosen Solve For degenerates the dual recovery (j003 X/MIN, j012 Z/MAX,
                           j008-bfneo Z/MIN, taser-80t X/MIN), the goal-wall bisection strictly beats
                           the plain SLP hug the reseeded path produces and reaches near the dual
                           bound (j003 X/MIN: -27 hug -> -31.3 optimum). The dF gate (facing wall =>
                           skip + info notice) is unit-tested fast in
                           core/anglesolver/DfDirectionGateTest
  FoldReplayDriverTest.java  #422 M1 fold driver: thousand-1-dup2 warm replay bit-exact
                           (6523.307720901059, viol 0.0), cold driver byte-exact >= 6523.30 and
                           bit-identical across 5 in-process runs, loopmm fold lands byte-exact,
                           j003 chord-narrowed FAST/objective-rounds gates
  WallHomotopyLadderTest.java  #422 M1 wall-homotopy ladder (rungs 0.05/0.01/0.002/0, warm-chained,
                           true-spec scoring): hpk_precise witness replays bit-exact, j154 cold
                           ladder lands byte-exact at or beyond the in-game F3 witness, collision
                           wall classification, ladder determinism
  BucketWalkTest.java      #422 M1 era-aware bucket machinery: gate-banded integer walk + objective
                           walk + window-scan leaf + free-start ulp nudge; perturbed-witness anchors
                           land byte-exact on both hpk_precise fixtures; polishFromAnchor
                           bit-identical; center snapping sub-half-bucket across eras
  DiskSocpKernelChordTest.java  fast-suite kernel coverage: two-axis ChordRow solves, the hardened
                           IPM jitter ladder, and the weak-duality infeasibility certificate
                           (FAIL_UNBOUNDED) that replaced the old fake-convergence
  FoldDriverEngineTest.java  #422 M1 engine-level gates through engine.solve() (driver wired as
                           primary recovery): j003 FAST + THOROUGH-10s
                           in-deadline, j1150-noturn-inner THOROUGH beats the p2 witness,
                           j154-noturn-ja-inner FAST solves (feasibility only, FAST has no
                           objective bar), FAST re-solve determinism
  CertifiedBnbTest.java    #422 M2a certified B&B fast units: SineTableGeometry containment (realized
                           inputs inside boxes/chords/supports across all four eras incl. the legacy
                           jump-tick double cast), a tiny 2-tick instance certifying at the enumerated
                           optimum with gap <= CERT_EPS, near-threshold gate behavior, determinism
  CertifiedBnbEngineTest.java  #422 M2a certified-search gates (VerySlowSolverTests, -PverySlowTests; PRs to main only): all 5 small
                           captures reach certified gap <= 1.5e-4 with dominating bounds and
                           feasible incumbents (per-capture numbers printed; the 1e-9 plateau
                           mechanism is in ARCH2-M2A-CERTIFIED-BNB.md), dF specs decline cleanly,
                           j1150-noturn-inner and j154-noturn-ja-inner solve under FAST
                           (feasibility only, FAST has no objective bar)
  PipelineShapeTest.java   #422 M2b G3 enforcement (VerySlowSolverTests, -PverySlowTests; PRs to main only):
                           every corpus capture visits the one linear
                           pipeline stage sequence at BOTH built-in tiers (THOROUGH probed at 1 s);
                           deadline-truncated runs must be a prefix of it
  NoTurnFinderTest.java    #424 outer no-turn stratfinder spike (core/.../anglesolver/noturn):
                           machineryCertifiesAByteExactNoTurn drives the inner engine as the byte-exact
                           oracle on the j1150 structure with a dF=0 chain and asserts a clean no-turn
                           (viol 0, obj near the -2805.2990 pure-no-turn optimum); finderReturnsAByteExactNoTurn
                           runs the full cold beam + full-jump screen + certify ladder and asserts a
                           byte-exact no-turn comes back (cold, or the warm seed of the current inputs)
  NoTurnRankingTest.java   stratfinder list order (core/.../anglesolver/noturn/NoTurnRanking): Easiest =
                           no jump-angle flick first, then fewest input changes (key edges incl. the first
                           press and the air hold, plus sprint toggles; a key change landing on a jump
                           tick is free, the WAD rhythm), then fewest backward ticks, then smallest turn,
                           offset as the tie-break; Furthest = largest offset past the goal wall on the
                           objective axis, easiest order as the tie-break; goal-wall pick and the
                           MIN-sense offset sign; fast, no capture
  NoTurnPlayableTest.java  Human yaws mode of the no-turn certifier (slow): certifies the j1150 pure and
                           j154 jump-angle structures plain and playable through the search cascade and the
                           Optimize graph, prints objective, reversals, max turn and the air yaw deltas, and
                           asserts the playable line stays byte-exact with no more turn reversals
  NoTurnPlayableUnitTest.java  reversal count, the reversal key in StructurePoolDriver.betterResult, and
                           NoTurnCertifier.landingGiveBack (margin above the near landing wall); fast
  harness/                 shared plumbing; no test lives here (NoTurnCapture loads a capture by path or
                           pool key into model + inputs + state, builds the engine spec, and adds the
                           synthetic free-start box the no-turn benches share)
resources/
  problems/<check>/        one folder per check; holds captures or .expect.json sidecars
  captures/                the shared capture library (one copy of each saved jump)
```

## The checks (folder = check)

| Folder          | Validates that the capture... |
|-----------------|-------------------------------|
| `solve/`        | still solves through the live engine (optionally for every Solve-For direction), within a time budget |
| `closedform/`   | closed-form-solves byte-exact feasible, on objective, and fast |
| `dualrecovery/` | is byte-exact-solved by the deterministic dual chain (closed form, SLP, reseeded SLP, relaxation recovery), no CMA-ES, no warm start; a sidecar `bnbSeconds` adds a bounded blind pattern-B&B feasibility fallback on chain miss; the hpk capture library (gh-204) is wired here, with `shouldSolve: false` marking the known frontier misses; since 2026-10-18 the hpk captures are the hand-corrected human strats (human inputs, real start box and dF=0 over the run-up on the start tick, corrected constraints), which the chain misses on 13 of them (j147, j153, j298, j318, j320, j335, j344, j345, j346, j347, j424, j703, j718) |

## How to add a capture

1. Put `<name>.json` in the check folder (e.g. `problems/closedform/`), or drop it in `resources/captures/`
   and put a `<name>.expect.json` sidecar in the check folder.
2. Done. `ProblemsTest` discovers it and runs the folder's check. Tune with the sidecar
   (see `resources/problems/README.md`).

## Plumbing: `harness/`

| File | Role |
|------|------|
| `Fixtures` | read a capture off the classpath; turn a recorded tick into a `TickState` |
| `ProblemFixture` | load a capture + drive the engine (solve / directed); times it |
| `Expect` | parse `<name>.expect.json`; supply defaults |
| `ProblemCatalog` | discover check folders and the captures in them |

## hpk_precise fixtures (#422 M1)

PRECISE-spec knife-edge fixtures under `resources/captures/hpk_precise/`, each a triple
`{orig,inner,witness}`; the `inner` variants are wired to `solve/` acceptance expects:

- `j1150-noturn-*`: the PRECISE j1150 inputs_gone spec with the solved pure no-turn schedule
  (#422 M0/M1). `inner` is the fixed-schedule inner problem (startTick 0, DF constraints
  stripped); `witness` is the p2 decode that replays byte-exact at X@49 = -2805.2990460856336,
  viol 0.0; `orig` is the untouched save. Wired via solve/j1150-noturn-inner.expect.json.
- `j154-noturn-ja-*`: the corrected j154 spec with the X GE -1599.2 plate at t34 (user ruling
  2026-08-27; the deeper D2 result is invalid against it). `inner` is the fixed-schedule inner
  problem (DF stripped, debug synthesized from the solve artifacts); `witness` is the F3 decode,
  the in-game-verified X@39 = -1599.7001161289918 at viol 0.0. Wired via
  solve/j154-noturn-ja-inner.expect.json.

## Library-only captures (no check yet)

Some captures in `resources/captures/` are committed as data for upcoming work and are not yet wired to a
check (no sidecar, so `ProblemsTest` does not run them):

- `deserthard-sine262.json` / `deserthard-planrealization.json`: two recorded runs of the same 26.2
  desert-hard solve; near-duplicates in content, but each pins what the other cannot.
  `deserthard-sine262` contains the one jump (t=243, yaw 93.587) where the pre-26 and 26.x sine
  chains pick different buckets, so `CaptureReplayRegressionTest` goes red if the Mth.sin/cos
  rewrite port regresses (`loopmm-3jump-lands` is the legacy-chain control; the planrealization
  run replays byte-exact under BOTH chains and cannot catch that). Its tail rows predate its
  stored solve, so it cannot validate plan realization. `deserthard-planrealization` has rows in
  sync with its result, so `PlanRealizationRegressionTest` rebuilds the plan path (spec +
  toGameFacings) and requires the recorded resim to sit on it byte-exact; that pinned the 26.x
  square-movement input rewrite. `ApplyYawRowsTest` feeds on the sine262 run's yaw sequence.

See `docs/research/anvil-solver-quality-decision.md`.
