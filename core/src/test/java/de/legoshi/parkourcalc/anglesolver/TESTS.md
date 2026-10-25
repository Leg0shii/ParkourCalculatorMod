# Angle-solver tests: the map

A saved jump (capture) lives in a check folder, and `ProblemsTest` validates it for that check. To add
coverage you drop a capture (or a tiny sidecar) into a folder. No Java change, no capture name in any test.

`ProblemsTest` and the engine-driving suites that take about 4 s or more are tagged
`@Category(SlowSolverTests.class)` and excluded from the default `:core:test` run; pass `-PslowTests` to
run them (CI always does). Quicker engine tests stay in the fast suite. See AGENTS.md "Tests" for the full
slow list, the shard split and when a full run is required.

The env-gated probes, dumps, screens and benches are not tests and live in their own source set,
`core/src/research/` (map and run commands in its README).

```
anglesolver/
  ProblemsTest.java        every capture under resources/problems/<check>/ is validated for that check; the check
                           classes live in checks/ (one per folder, Checks maps the folder name to the class)
  YawOrderTest.java        gh-553: dF >= 0 / dF <= 0 over a tick range (the
                           yaw may only turn one way, no turn-out). YawOrder carries consecutive-tick F
                           inequalities and one-sided absolute F walls that the YawTies fold does not
                           absorb; the SLP solves them as exact yaw rows, the certified B&B propagates
                           them between the per-tick arcs and splits overlapping pairs in order, the
                           closed form runs the tie cascade (YawOrderDp). Pins the carry rules, the
                           projection, an SLP, a closed-form and a B&B solve of a one-way range on j121,
                           the wrong-way range staying rejected, and the j121 neo engine solve; the
                           pattern B&B accepting an order spec and landing j345 (gh553-j345-dfneo);
                           also the dF gate: LevelSetAscent skips a spec with a facing wall, which is
                           what the dF direction notice is shown for
  YawDirectionCrossCheckTest.java  gh-553 (SlowSolverTests, one case per FAST solve/ capture under a 60 s
                           budget; CI cuts it into 8 shard parts, ShardParts): re-solves the capture with one-way dF rows its own solved path
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
  WrapWindowIlsTest.java   wrap-window lattice ILS stage (|gf| <= 360 hard cap): candidate sets are
                           distinct/capped/deterministic; bounded span-16 descent from the rung snap
                           point (resources/points/) reaches translated viol <= 3.5e-5 at a fixed
                           eval cap; wrap-class results survive locked-rows JSON round trip
                           byte-exact; PKC_WRAPILS_FULL=1 + PKC_WRAPILS_TAG runs the env-gated full
                           replication with kicks (1.5e-5 bar on rung)
  LegalModeTest.java       legal/record goal-wall selection on synthetic constraint lists: velocity, EQ, cap,
                           off-tick and off-axis walls are never selected, the tightest reach wall wins, ties and
                           an empty set refuse (the capture-backed legal gates are problems/legal/ and the
                           goalWall fields of problems/replay/)
  RangeMetTest.java        gh-485: the result panel gates a range constraint's ends strictly like the walls, so a
                           1e-7 wide range missed by 9e-7 (the 2paradise_1135 Z @T1134 range) is not counted as met
                           and N/N can never sit next to No solution
  MotionObjectiveTest.java gh-551 Angle goal objective: Mot is the velocity along the angle at the objective
                           tick (gh551-motion-angle, a 2-jump capture with Angle/Mot), Pos the position,
                           translation leaves Mot alone, withTick keeps angle and type, the Angle goal has no
                           legal goal wall; the objective tick (gh551-motion-jumptick, objectiveTick 14 in a
                           span to 21) lands inside the span while the constraints still run to the goal tick,
                           survives the save file and follows its row on insert and remove. The live FAST
                           solve and the cold Optimize of both captures are problems/solve/ and
                           problems/optimize/ sidecars
  CrossAxisSignTest.java   dX vs dZ constraints (mcc11-cross-axis-sign capture, 1.21.3): the recorded
                           path is feasible and needs opposite dominant signs on its two cross-axis
                           constraints; the closed form enumerates the per-constraint sign branches (all +1,
                           all -1, then the rest) and solves; SLP keeps the two half-walls per constraint
                           with the sign of the current iterate and solves from its dual seed; the two
                           half-wall slacks reproduce the exact |dX| - |dZ| slack
  FoldReplayDriverTest.java  #422 M1 fold driver: thousand-1-dup2 warm replay bit-exact
                           (6523.307720901059, viol 0.0), cold driver byte-exact >= 6523.30 and
                           bit-identical across 5 in-process runs, loopmm fold lands byte-exact,
                           j003 chord-narrowed FAST/objective-rounds gates
  WallHomotopyLadderTest.java  #422 M1 wall-homotopy ladder (rungs 0.05/0.01/0.002/0, warm-chained,
                           true-spec scoring) and the era-aware bucket machinery behind its anchor
                           polish: hpk_precise witness replays bit-exact, j154 cold ladder lands
                           byte-exact at or beyond the in-game F3 witness, collision wall
                           classification, ladder determinism; perturbed-witness anchors land
                           byte-exact on both hpk_precise fixtures through the anchor SLP and the
                           full polishFromAnchor ladder (perturb 50 reaches the deep basin), the
                           engine-seeded polish beats the j154 witness and holds j1150 feasible,
                           polishFromAnchor bit-identical, center snapping sub-half-bucket across eras
  DiskSocpKernelChordTest.java  fast-suite kernel coverage: two-axis ChordRow solves, the hardened
                           IPM jitter ladder, and the weak-duality infeasibility certificate
                           (FAIL_UNBOUNDED) that replaced the old fake-convergence
  FoldDriverEngineTest.java  #422 M1 engine-level gates through engine.solve() (driver wired as
                           primary recovery): j003 THOROUGH-10s in-deadline, j1150-noturn-inner
                           FAST solves (feasibility only, FAST has no objective bar), j154 FAST
                           re-solve determinism; the j154 FAST and j1150 THOROUGH gates live in
                           problems/solve
  CertifiedBnbTest.java    #422 M2a certified B&B fast units: SineTableGeometry containment (realized
                           inputs inside boxes/chords/supports across all four eras incl. the legacy
                           jump-tick double cast), a tiny 2-tick instance certifying at the enumerated
                           optimum with gap <= CERT_EPS, near-threshold gate behavior, determinism;
                           #462 the infeasibility certificate: a wall 0.5 and 1e-3 past the tiny
                           instance's certified optimum is certified infeasible (exhausted tree, no
                           unresolved drop), a feasible instance never carries one, an expired
                           deadline or a zero node cap leaves infeasibility uncertified
  SolveFailureDiagnosticsTest.java  what a failed j004 solve reports: every constraint and the missed tick, the
                           closest attempt's values, the dF-band decline, the same-tick conflict; #462
                           the verdict end to end through the engine: the conflict and a wall half a
                           block past the optimum are INFEASIBLE (the latter certified by the B&B in a
                           handful of nodes), a wall 1e-3 past it is UNDETERMINED with no notice and
                           the "No solution" headline, the dF decline is UNDETERMINED, a solve
                           is FEASIBLE
  CertifiedBnbEngineTest.java  #422 M2a certified-search gates (VerySlowSolverTests, -PverySlowTests; PRs to main only): all 5 small
                           captures reach certified gap <= 1.5e-4 with dominating bounds and
                           feasible incumbents (per-capture numbers printed; the 1e-9 plateau
                           mechanism is in ARCH2-M2A-CERTIFIED-BNB.md), foldable dF chains are
                           handled, not declined
  PipelineShapeTest.java   #422 M2b G3 enforcement (VerySlowSolverTests, -PverySlowTests; PRs to main only):
                           every corpus capture visits the one linear
                           pipeline stage sequence at BOTH built-in tiers (THOROUGH probed at 1 s);
                           deadline-truncated runs must be a prefix of it, and so must a run the
                           certified B&B ends early with an infeasibility certificate (#462)
  NoTurnDriversTest.java   #424 the no-turn drivers on the two hpk_precise structures (slow):
                           the certifier drives the inner engine as the byte-exact oracle on j1150
                           with a dF=0 chain and lands a clean no-turn (viol 0, obj near the
                           -2805.2990 pure-no-turn optimum); NoTurnFinder's cold beam + full-jump
                           screen + certify ladder returns a byte-exact no-turn (cold, or the warm
                           seed of the current inputs); StructurePoolDriver cracks j1150 cold at
                           min edges with an interior free start; Human yaws mode certifies the j1150
                           pure and j154 jump-angle structures plain and playable through the search
                           cascade and the Optimize graph and the playable line stays byte-exact with
                           no more turn reversals
  NoTurnRankingTest.java   stratfinder list order (core/.../anglesolver/noturn/NoTurnRanking): Easiest =
                           no jump-angle flick first, then fewest input changes (key edges incl. the first
                           press and the air hold, plus sprint toggles; a key change landing on a jump
                           tick is free, the WAD rhythm), then fewest backward ticks, then smallest turn,
                           offset as the tie-break; Furthest = largest offset past the goal wall on the
                           objective axis, easiest order as the tie-break; goal-wall pick and the
                           MIN-sense offset sign; fast, no capture
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

| Folder            | Validates that the capture... |
|-------------------|-------------------------------|
| `solve/`          | still solves through the live engine (optionally for every Solve-For direction), within a time budget, and a result that counts every constraint met is a success; sidecar extras pin the failure notice of a capture that must not solve (`noticeLabel`, `noticeContains`), bound the re-simulation divergence of the shipped objective (`maxRecomputeDivergence`), solve twice (`reproducibleWithin`) or re-solve from six seed placements inside the start box (`boxSeeds`) |
| `closedform/`     | closed-form-solves byte-exact feasible, on objective, and fast |
| `dualrecovery/`   | is byte-exact-solved by the deterministic dual chain (closed form, SLP, reseeded SLP, relaxation recovery), no CMA-ES, no warm start; a sidecar `bnbSeconds` adds a bounded blind pattern-B&B feasibility fallback on chain miss; the hpk capture library (gh-204) is wired here, with `shouldSolve: false` marking the known frontier misses; since 2026-10-18 the hpk captures are the hand-corrected human strats (human inputs, real start box and dF=0 over the run-up on the start tick, corrected constraints), which the chain misses on 13 of them (j147, j153, j298, j318, j320, j335, j344, j345, j346, j347, j424, j703, j718) |
| `replay/`         | replays its saved rows byte-exact through the solver model: the rows' float yaw chain forwarded from the launch state matches the recorded debug positions, is feasible (or violates by a pinned amount), hits its recorded objective, names the expected legal goal wall and, with `legal`, keeps every hard wall and lands at the recorded shortfall (the razor rung legal attempts, the razor proof and weirdpane pins, the proof under the rung 5.375 walls) |
| `transitions/`    | replays every clean recorded SimulatorEntity transition of a debug-enabled capture through ExactJumpModel byte-exact (loopmm-3jump-lands is the legacy-chain control, deserthard-sine262 pins the 26.2 Mth.sin/cos rewrite) |
| `planpath/`       | has its recorded resim sitting byte-exact on the plan path rebuilt from its saved result yaws (deserthard-planrealization pinned the 26.x square-movement input rewrite) |
| `legal/`          | legal mode lands at the goal wall with a non-positive shortfall and every wall met (gh459-legal-mode, plain and with the seed stage starved to 1 ms, normal mode too), or on an unlandable capture holds every hard wall with only the dropped goal wall unmet and a shortfall in the expected band, deterministic bit for bit (synth-legal-shortfall) |
| `optimize/`       | Optimize is never worse than Fast: cold (two fresh engines, at least two published incumbents, gh398-optimize-2jump) or on the Fast engine (the chain starts from the incumbent, gh486-cross2-freestart) |
| `multistart/`     | the `Fast (multi-start)` preset is never worse than Fast and reaches the in-game basin when the sweep adopts a candidate (gh486), and a pinned start passes through the sweep instantly (gh398) |
| `smooth/`         | the Smooth button runs only the smooth stage from the incumbent on a landed Fast solve, keeps the landing, reports the smoothing and is withdrawn once the range changes |
| `freestart/`      | builds a start box that frees the start with no footprint wall leaking, lands, re-references a seed dragged outside the box (`displaceSeedX`) and adopts the start where the sidecar says (`startXLo`/`startXHi`, `startZLo`/`startZHi`, `pinnedAfterAdoption`) |
| `levelset/`       | the level-set ladder on the keep-out-wall direction named by the sidecar (`axis`, `goal`) strictly beats the plain SLP hug by `minGain` and stays under the dual bound (gh-290: j003 X/MIN, j012 Z/MAX, j008-bfneo Z/MIN, taser-80t X/MIN) |
| `ils/`            | the ILS polish stays byte-exact feasible and never regresses from the closed-form seed, and returns an infeasible seed untouched |
| `translation/`    | translation-aware free-start scoring: the zero-width translated score byte-equals the pinned score, and with `cap` the closed-form best translation matches a brute-force grid |
| `gapattribution/` | a cold Optimize's reported dual-bound gap is honest: credited yaws re-simulate to the reported objective, the gap never prints negative and is never tighter than the true shortfall to a byte-exact-attainable `refObjective` (the hpk_human j1150 and j155 strats) |

## Notes on individual solve/ cases

- solve/loopmm-tight-t39: the loopmm misses capture with its shipped-disabled pad wall ENABLED; the tight Z@71 >= -279.3 pad is reachable only while the Z inertia gate is held open across the momentum reversal, so this pins the pattern B&B's keep-alive branch, THOROUGH 45 s
- solve/loopmm-tight-t39-fast: the SAME capture under FAST effort, same keep-alive pin on the shorter chain, budget 90 s
- solve/gh313-j121-dfneo: gh-313 / gh-553; hpk j121 neo with dF <= 0 on T39..T46, the yaw may only decrease through the neo. Retired by ruling in issue 372 (dF = 0 only), carried again since issue 553: the SLP holds the walls as exact yaw rows and the certified B&B as ordered sine-table arcs, so it solves on FAST through the certified B&B
- solve/gh553-j345-dfneo: gh-553; hpk j345 3jmm neo with dF <= 0 on T39..T43 and T46, X MAX against a knife-edge X wall on T41 (the human path clears it by 9e-6). The certified B&B exhausts its FAST budget without an incumbent; the pattern B&B lands it in ms on the zx1@6 pattern, so this pins that the pattern B&B accepts an order spec and that its per-pattern closed form runs the ordered tie cascade
- solve/gh553-j925-dfle: gh-553; the gh283-j925-farseed free-start capture with dF <= 0 on T15..T17, which its own cold solve already satisfied; the free-start joint solve declined every order spec, now it runs the tie cascade around the unordered joint solve, 0.3 s
- solve/gh553-j335-dfle: gh-553; the gh386-j335-lowx free-start run with dF <= 0 on T5, T11 and T13 from its own solve. The unordered joint solve flips tick 4 by 180 deg, so the cascade must tie a link that sits on the wrap, and the landing comes from the free rescue's centre chain through the relaxation recovery, which declined every F row before; 5 s
- solve/gh553-j022-minturn: gh-553; j022-1bmhbfly with a minimum turn 0.01 deg under the solved turn on T34 (the seam), T36 and T37 against its knife-edge X@44 wall. The SLP stalls at 1e-8 on a sine-table bucket there, so the closed form's seed must land through the relaxation recovery; pins that stage carrying the order, 70 ms
- solve/nix-full-t1: long multi-jump free start; accepted as not solving after the CMA-ES removal (issue 374) until the multi-jump seam work lands
- solve/gh283-j925-farseed: gh-283; hpk j925 momentum+neo with the start dragged ~2 blocks outside its tick-0 footprint box, cold rows; pins the seed-position-independent free-start solve, FAST 20 s: bestTranslate's conflict fallback must place conflicted candidates at the min-violation translation, never at the seed
- solve/gh283-j990-cold: gh-283; hpk j990 dF=0 momentum chain with the seed ~2 blocks outside the start box, cold rows; pins the exact prefix-arc theta enumeration, the recoverStart-scored ladder fractions, and the theta micro-polish certify in the joint free-start dual, FAST 20 s
- solve/inertia-1tick-neo: 1.8.9 neo whose landing is only feasible via a single-tick X inertia zeroing (vX dips under the 0.005 threshold mid-flight and re-accelerates after); the free relaxation is truly infeasible, so this pins the B&B rescue's single-tick zx1@k patterns and its dF=0 entry through the FacingPrefold-gated facing-wall check, FAST 20 s
- solve/inertia-1tick-neo-t31: the SAME capture with the window started ON the zeroing tick (startTick 30), so the seed vX 0.00498 is below the threshold and dead on arrival; pins the seed-velocity normalization (ExactJumpModel.zeroSubThresholdVelocity at buildPhys), without which every relaxation carries the phantom carry and no k>=1 pattern can represent a tick-0 zeroing, FAST 20 s
- solve/thousand-1-dup2: #422 M1 acceptance; free-start multi-jump MAX X, THOROUGH 30 s, byte-exact at objective >= 6523.307 (reliably 6523.3080, beats both releases); the 10 s budget was timing-flaky on this floor, 30 s is the honest budget; sole canonical thousand gate, the FoldDriver/CertifiedBnb engine-test duplicates were removed
- solve/j1150-noturn-inner: #422 M1 acceptance on the PRECISE inputs_gone spec with the p2 no-turn schedule, THOROUGH 10 s, byte-exact at objective >= -2805.2990460856336, the p2 witness; FAST is deliberately not gated here, 3.3e-4 short, an M2a item
- solve/j154-noturn-ja-inner: #422 M1 acceptance on the corrected t34-plate spec, FAST, feasibility only (FAST has no objective bar; the in-game-verified F3 witness X@39 = -1599.7001161289918 is the capture's own result)
- solve/gh462-j004-pastwall: gh-462; j004 with an extra X >= wall at the landing tick half a block past its certified optimum, FAST; must not solve within 20 s and must carry the INFEASIBLE verdict with the "Certified infeasible" notice label (the certified B&B exhausts its tree in a handful of nodes and the graph walk ends on the certificate instead of running the 40 s pattern B&B), the end-to-end gate that a certificate reaches the result and that a mere miss never would
- solve/gh454-p2s-overconstrained: gh-454; a segment whose dF = 0 constraints tie every tick to one heading, THOROUGH; must fail within 20 s and carry the "Overconstrained" notice label with the "one heading ... misses by" explanation
- solve/j021-rinav1-01: the Optimize graph (THOROUGH 12 s) must reach the deterministic optimum 1067.8637 (the degenerate-tick ascent in the fold stage carries it) and reproduce it within 1e-2 on a second solve
- solve/loopmm-3jump-lands: the Optimize graph (THOROUGH 60 s) lands at the -279.3 block edge through the pattern B&B stage; the leaf snap can adopt loopmm as a fully yaw-locked stage, so a naive re-simulation of the result yaws through the non-yaw-locked scenario may re-round short, the divergence is bounded at 1e-3 and the landing is yaw-lock-dependent, VERIFY IN-GAME

## How to add a capture

1. Put `<name>.json` in the check folder (e.g. `problems/closedform/`), or drop it in `resources/captures/`
   and put a `<name>.expect.json` sidecar in the check folder (`capture` points at a library path, or at an
   absolute test resource such as `/hpk_human/d11/<name>`).
2. Done. `ProblemsTest` discovers it and runs the folder's check. Tune with the sidecar
   (see `resources/problems/README.md`).

## Plumbing: `harness/`

| File | Role |
|------|------|
| `Fixtures` | read a capture off the classpath; turn a recorded tick into a `TickState` |
| `ProblemFixture` | load a capture + drive the engine (solve / directed); times it; `rig()` is a live engine over the capture for checks that solve more than once |
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

## The deserthard pair

Two captures that look like duplicates and are not:

- `deserthard-sine262.json` / `deserthard-planrealization.json`: two recorded runs of the same 26.2
  desert-hard solve; near-duplicates in content, but each pins what the other cannot.
  `deserthard-sine262` contains the one jump (t=243, yaw 93.587) where the pre-26 and 26.x sine
  chains pick different buckets, so `problems/transitions/` goes red if the Mth.sin/cos
  rewrite port regresses (`loopmm-3jump-lands` is the legacy-chain control; the planrealization
  run replays byte-exact under BOTH chains and cannot catch that). Its tail rows predate its
  stored solve, so it cannot validate plan realization. `deserthard-planrealization` has rows in
  sync with its result, so `problems/planpath/` rebuilds the plan path (spec +
  toGameFacings) and requires the recorded resim to sit on it byte-exact; that pinned the 26.x
  square-movement input rewrite. `ApplyYawRowsTest` feeds on the sine262 run's yaw sequence.

See `docs/research/anvil-solver-quality-decision.md`.
