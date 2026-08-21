# roc2 reach handoff (2026-08-18)

Prepared for a fresh session (Fable model) to take over the `roc2.json` solve. It is a
reach-limited near-miss: the byte-exact search converges ~0.00045 short of a landing pad, but
the smooth reach bound has large headroom, so it is **not** proven impossible. This doc has the
exact problem, everything tried, the tooling, and the heavy machinery from the 5.375bm nix neo
campaign to bring next.

Branch: `feature/tas-solve-smoothness`. All code below is uncommitted on that branch.
(Update 2026-08-21: committed as 6a4e6d94; the wrap-reserve fix, the MAX_ABS_GF change as
`WrapWindowIls.Config.maxAbsGf`, `rWrapEps`, the `EngineFileScreen` hooks and `Roc2CellSetDump`
are all in tree.)

---

## 1. The problem, precisely

- File: `C:\Users\benja\Desktop\Games\MultiMC\instances\1.8.9\.minecraft\parkourcalculator\roc2.json`, MC 1.8.9.
- `angleSolver` config: `startTick=6796`, `landingTick=6845` (internal; UI shows T6797..T6846),
  49 ticks, 3 jumps, `axis=X`, `goal=MAX`, `effort=THOROUGH`, `optimizeSeconds=4`,
  `legalMode=false`, `defaultSprint=DERIVE`.
- Legal mode auto-engages anyway: the objective axis (X) has a hard constraint at the objective
  tick (T6846), so the engine promotes it to a goal wall. The solve chain shows `(legal)` and
  wrap runs in legal mode minimizing the goal shortfall.
- The one binding constraint (everything else is met byte-exact):

  ```
  X @ T6846 in [-3754.3, -3752.7]   (landing pad near edge = -3754.3)
  best byte-exact reach found:  X = -3754.30045   ->  0.00045 SHORT of the pad
  ```

  This is a pure REACH shortfall on the objective axis. It is NOT a Z@T6822 clip; an earlier
  claim that Z@T6822 was violated was a wrong-seed artifact (see Traps) and is retracted.

## 2. Is it impossible? No (smooth bound has headroom)

`ClosedFormSolve.dualBound(spec)` (weak-duality reach bound on the smooth constant-modulus model,
no duality gap; `ClosedFormSolve.java:437`, backed by `CostateDualSolver`) returns an upper bound
on reachable X: "no feasible path lands beyond this."

```
committed 37-tick window:  dualBound = -3754.219   (+0.081 over the pad edge)
full 49-tick window:       dualBound = -3753.334   (+0.97 over the pad edge)
byte-exact best found:     -3754.30045             (0.00045 short)
```

Both bounds sit well past the pad, so the smooth relaxation does not rule out the pad. The
byte-exact search is stuck at a local optimum inside a ~0.08 to ~0.97 duality gap. Per the 5.375
methodology (Section 6), a plateau is only "not found yet"; proving impossibility needs a NEGATIVE
byte-exact dual bound (the gate-flip exact-cell-set MILP), which has not been run for roc2.

## 3. What was tried, and results

All runs via `EngineFileScreen` (headless engine driver), byte-exact `ExactJumpModel`.

| Approach | best X@T6846 | short of pad | note |
|---|---|---|---|
| user manual solve | -3754.30049 | 0.00049 | the stored `result.yaws` |
| pipeline THOROUGH 45s | -3754.30368 | 0.00368 | wrap never reached (starved) |
| pipeline THOROUGH 120s | -3754.30270 | 0.00270 | wrap never reached (starved) |
| pipeline 8s (finishes early) | -3754.30046 | 0.00046 | wrap DID run ~1s, then cut off |
| committed candidate + Angle Wrap 90s (direct) | -3754.30045 | 0.00045 | wrap plateaued: 9e-6 gain in 90s |
| full 49-tick free solve + Angle Wrap 120s | -3754.30106 | 0.00106 | still improving from a worse start |

Key mechanism finding: **Angle Wrap (`WrapWindowIls`) is the correct reach closer but it is
starved.** The THOROUGH graph spends the whole overall deadline on the warm CMA race (~40% of
budget), seam sweep, and B&B; the walk hits the deadline right after ILS and returns before it ever
reaches the `wrap` node. It only runs when the solve finishes early (the 8s run), where it gained
0.0022 of reach in ~1 second before the deadline cut it off (`rounds=0`). Given real budget on the
committed candidate it converges to -3754.30045; the full free 49-tick problem is much harder and had
not converged.

## 4. Tooling: `EngineFileScreen` env hooks

`core/src/test/.../anglesolver/EngineFileScreen.java`, run with
`gradlew :core:test --tests "*EngineFileScreen*" --no-daemon -x tableStyleCheck --rerun`.
Clear stale `PKC_*` env between runs (they leak); read output from
`core/build/test-results/test/TEST-*EngineFileScreen.xml` and traces from
`core/build/reports/solver-trace-<tag>.txt`.

- `PKC_SOLVE_FILE` (required), `PKC_SOLVE_EFFORT`, `PKC_OPTIMIZE_SECONDS`, `PKC_SOLVE_TIMEOUT_MS`,
  `PKC_SOLVER_TRACE=<tag>`.
- `PKC_START_TICK` / `PKC_LANDING_TICK`: override the window. WARNING: the seed is byte-exact only
  at `file.angleSolver.startTick` (=6796); overriding to any other start tick seeds from stale
  captured `debug` states, which drifts the objective ~0.013 and drops the constraint count 13->12.
  Only ever pass `PKC_START_TICK=6796`.
- `PKC_EVAL_SAVED_YAWS=1`: forward `result.yaws` byte-exact, print viol/obj and a per-constraint
  `CON` dump (shows exactly which wall is violated and by how much). `=2`: also warm-start
  `LatticeRepair` and `WrapWindowIls` from the saved yaws and print `WRAPGF`. `PKC_WRAP_KICKS=0`
  disables wrap kicks.
- `PKC_WRITE_SAVED=1`: re-encode `result.yaws` into the rows via `AngleSolverEngine.writeYawRows`
  (the real Apply path) and dump `APPLYROW` lines (yaw + locked). Use to reconstruct the stored
  solve as a file.
- `PKC_WRAP_PUSH=1` + `PKC_GOAL_RHS=-3754.3` + `PKC_WRAP_SECS=<n>`: after a normal solve, run
  `WrapWindowIls.polish` directly on the result in legal mode with a dedicated budget (bypasses the
  starvation). Prints `WRAPPUSH baseX/newX/short`. This is the fastest way to give wrap real time.
- `PKC_FREE_SOLVE=1` + `PKC_FREE_SECS=<n>`: CUSTOM effort with `useWindowSolver=false`,
  `ilsExhaustive=true`, `polishDepth=EXHAUSTIVE` (full window, nothing committed). Pair with
  `PKC_START_TICK=6796 PKC_LANDING_TICK=6845` to force the true 49-tick free solve.
- `PKC_REACH_BOUND=1` (+ optional `PKC_GOAL_RHS`): print `ClosedFormSolve.dualBound`. Microseconds;
  run this FIRST on any new jump.

## 5. Code changes on the branch (uncommitted)

Update 2026-08-21: no longer uncommitted; everything below was committed as 6a4e6d94 (the cap
now also lives as `WrapWindowIls.Config.maxAbsGf`).

- `WrapWindowIls.MAX_ABS_GF` 360 -> 12000, and the two `WrapWindowIlsTest` cap assertions updated
  to track `MAX_ABS_GF`. Needed because roc2 facings run ~2400 to 2835 degrees: the raw accumulated
  yaw is already ~2470 at T6796 (this jump sits ~6800 ticks into a run), so the working facings are
  inherently deep-wrap. WARNING: this is a GLOBAL behavior change, wrap now admits +-360 wraps for
  any facing on every solve; `ProblemsTest` + `GraphPresetSolveTest` stay green, but it conflicts
  with the "prefer |gf| <= 720" ruling. Consider making it a `WrapWindowIls.Config` field instead of
  a global constant.
- `BuiltinGraphs` `rWrapEps` epsilon 1e-2 -> 5e-2 (lets wrap trigger on wider near-misses). Revert
  or make configurable if not keeping.
- `EngineFileScreen` instrumentation (all the env hooks in Section 4).

## 6. How to tell "impossible" from "not found" (the 5.375 method)

Two dual bounds, escalating. A plateauing CMA/B&B/ILS/wrap is never a proof; only a bound over ALL
feasible points is.

1. `ClosedFormSolve.dualBound` (already run for roc2): smooth constant-modulus reach bound,
   microseconds. Negative-relative-to-pad => smooth-infeasible; unbounded dual
   (`CostateDualSolver.solve` returns null at `LAMBDA_CAP=1e9`) => primal infeasible. For roc2 it is
   POSITIVE with headroom, so it does not certify impossibility; it certifies the pad is reachable in
   the smooth model.
2. Smooth annulus feascenter: `MiqcpDump` (Java) -> `tools/miqcp/build_model.py --feascenter` (COPT,
   gap 1e-8). t* < 0 on this per-tick-capped relaxation binds byte-exact reality (it upper-bounds it).
3. Byte-exact-aware certificate: `CellSetDump` (Java) -> `tools/miqcp/cellset_milp.py`, the gate-flip
   exact-cell-set MILP (enumerates the finite realizable LUT cells per tick, linear, both SCIP and a
   second solver converge). Its LP dual bound gives a certified bracket: a negative max-slack proves
   viol<=0 impossible over the whole in-window feasible set. This is what decided the 5.375 PRIMARY
   dead.

Only the negative direction certifies. A model-feasible selection or t* > 0 is a candidate, never a
claim, until byte-exact AND confirmed in-tool. Any impossibility is window-scoped (facing tube, wrap
bases, gate band, start box); the full-circle bound is open.

## 7. The 5.375bm nix neo arsenal (heavy machinery to bring)

roc2 is a 3-jump neo-style reach problem at the byte-exact sine floor, the same family as the
5.375/5.4375 nix neo jumps (the heaviest the project has done). What that campaign used, and when to
reach for it (classes under `core/src/main/.../anglesolver/solver/`):

(Update 2026-08-21: `MomentumAssembly`, `HomotopyCloser`, `AlmSnapStage` and `NixBackwardMarch`
were since deleted in the 2026-08 cleanup; section 10.4 already proved them inapplicable to roc2,
so nothing is lost.)

- `WrapWindowIls` (Angle Wrap): re-expresses each heading as gf, gf +-360, gf +-720 to ride
  DIFFERENT LUT cells. On nix it was the only source of the 6.5e-5 -> 1.22e-5 gain. The extra reach
  on razor jumps comes from riding norm>1 significant-angle cells (the smooth unit-circle ceiling sat
  ~1.16e-4 below the pad; only norm>1 cells lift it). This is the first thing to give real budget on
  roc2.
- `MomentumAssembly` + `HomotopyCloser`: the cold neo/nix assembler (axis-boost templates, seam
  decomposition, pinned-tail) plus THE byte-exact feasibility closer (relax every inequality by
  epsilon, solve, walk epsilon->0 in halving rungs, finish with sine-bucket max-slack descent). This
  pair cold-solved the free-start 54-tick nix-full-t1 to viol 0. Both stages are load-bearing
  (descent alone stalls ~6e-5; ladder alone ~1.8e-6). Directly applicable to roc2.
- `BoundPrunedRecovery`: deep branch-and-bound over gate/sign cancellation patterns, convex inside
  each (capped MAX_PATTERNS=8). Give it more time / patterns.
- `AlmSnapStage` = `AlmBfgsCore` (Sheepram-class smooth ALM+BFGS with analytic gradients) ->
  `SnapRepairPolish` (snap to LUT buckets, 1-opt/2-opt repair). Alternate basin finder. Caveat:
  smooth-then-snap cannot COLD-solve razors (its surrogate has a ~2e-4 LUT-residual floor and grades
  the exact corner infeasible); use it to seed, not to certify.
- `SeamSweepRecovery`, `LatticeRepair`, `BucketAscentPolish`, `RelaxationRecovery`, `SlpSolve`,
  `FreeStartSolve` (translation-invariant start box), `FacingLattice` (exact realizable-cell
  enumeration), `CostateDualSolver` (the dual + reach bound).
- External: SMT-FP (Bitwuzla) VERIFIES a tight window byte-exact (12-tick pad ~35s), does not
  search. Smooth MIQCP (Gurobi/SCIP/BARON) certifiable global in ~a day, then byte-exact verify.
  Sheepram (ALM+BFGS+discrete local search) solved the corrected 5.4375 externally.
- Note on MITM: there is no meet-in-the-middle solver in the 5.375 docs. The nearest is
  `NixBackwardMarch` (search from the constrained landing back to rest). The byte-exact MITM momentum
  search is a DIFFERENT project (stratfinder, memory `project_momentum_exact_search`); do not assume
  it applies here.

## 8. Recommended next moves (escalation ladder)

1. Give Angle Wrap real budget. Fastest: `PKC_WRAP_PUSH=1 PKC_GOAL_RHS=-3754.3 PKC_WRAP_SECS=600`
   on the committed candidate (`PKC_OPTIMIZE_SECONDS=8` to get a good seed fast). On roc2 the
   committed candidate plateaued at 0.00045 in 90s; a 10-minute run tests whether it truly caps
   there or keeps crawling. Also fix the graph starvation properly (reserve a wrap slice, or run
   wrap before the warm race consumes the deadline).
2. `MomentumAssembly` + `HomotopyCloser` on the full 49-tick window. This is the cold neo assembler
   that solved nix-full-t1; it is the most likely in-repo path to find the extra 0.00045.
3. Deep `BoundPrunedRecovery` with a larger pattern budget on the committed problem.
4. If search still resists, certify or find with the MIQCP/MILP pipeline (Section 6.2 then 6.3).
   A negative gate-flip exact-cell-set MILP bound is the only way to definitively answer whether the
   last 0.00045 is byte-exact reachable. A positive/feasible selection there points the byte-exact
   search at the winning cells.

## 9. Traps

- Seed: never override `startTick`; use 6796. Overriding seeds from stale `debug` states (obj drifts
  ~0.013, cons 13->12, violation mislabels onto the wrong wall).
- File inconsistency: `roc2.json` rows != `result.yaws` != `debug`. The 0.00049 solve lives ONLY in
  `result.yaws` metadata; the rows hold a different path (forwarding the current rows gives
  -3754.3123, not -3754.30049). For faithful work get a FRESH consistent save (load the 0.00049 solve
  in the mod, Save, so rows = the solve and debug = its trajectory), then the full-window byte-exact
  solve reproduces it.
- Deep wrap is inherent (startYaw ~2470 deg), so `MAX_ABS_GF` must exceed the working facing scale
  (bumped to 12000). The "prefer |gf| <= 720" ruling is about wrap depth relative to travel, not the
  absolute accumulated yaw.
- Locked RAW rows only for wrap-class points: unlocked/delta rows cap per-tick travel at 180 deg and
  cannot express wrap-window points; never `wrapAll` in a verify path for them. `SnapRepairPolish`
  wraps to +-180 internally, so use the norm-ILS descent as the polisher for wrap points.
- Certify floor = sine residual ~1e-4 (bucket ~0.0055 deg; +-1 bucket ~1e-4 velocity, enough to flip
  a gate). Certification needs absolute gap ~1e-8, not relative 1e-6.
- Only the NEGATIVE direction certifies impossibility; headless byte-exact viol 0 is necessary, not
  sufficient, until confirmed in-tool.
- Anti-stale: fresh run tag per artifact, grep the applied echo lines, `--rerun --no-daemon`, never
  delegate run supervision to a subagent.
- Never edit `ExactJumpModel` / `McSineTable` / `Constants`. Display tick = internal + 1.

---

## 10. Campaign results (2026-08-19 session, appended)

Executed by the takeover session. Everything below is byte-exact on `roc2-best.json` unless noted.

### 10.1 File supersession

`roc2.json` is STALE for this problem. Its compiled spec carries an artifact sliver wall at T6831
([-3757.9501,-3757.95] x [-1762.05,-1762.049]) where the legit wall (user ruling) is the wide box
([-3759.05,-3757.95] x [-1762.05,-1760.95]); its pad lo still holds the temporary -3754.3005.
The authoritative save is `roc2-best.json` (same dir). The user's 0.00049 solve verifies byte-exact
there: viol=0, X@T6846 = -3754.300493889. The user re-saved the file mid-session (window moved
6831 to 6822); walls unchanged, states before 6822 unchanged, all window seeds re-validated.

### 10.2 New tooling (all uncommitted, test-side unless noted)

Update 2026-08-21: committed as 6a4e6d94 (GraphRunner wrap reserve, EngineFileScreen hooks,
Roc2CellSetDump), with the exceptions noted per item below.

- `GraphRunner` (MAIN): reserves min(3s, budget/4) of the overall deadline for a pending `wrapIls`
  node. Fixes the wrap starvation. Fast + full `-PslowTests` suites green. THOROUGH 45s on roc2
  went from -3754.3037 (wrap never ran) to -3754.3012 (wrap runs).
- `EngineFileScreen` new hooks: `PKC_SEED_CHECK` (forwards captured yaws against captured
  positions, bit-exact gate for any window; prints `SEEDCHECKGF` base facings), `PKC_ASSEMBLY`,
  `PKC_BNB` (deep BnB standalone), `PKC_CLOSER` (HomotopyCloser from the solve result),
  `PKC_ADFLIP_TICKS` (A/D mirror scan through the real solver stack), wrap-push knobs
  (`PKC_WRAP_SPAN/MAXSPAN/HIGH/GATEFLIP/BASE`). (2026-08-21: `PKC_ASSEMBLY` and `PKC_CLOSER`
  are gone; their target classes were deleted in the 2026-08 cleanup.)
- `Roc2CellSetDump` (new): generalizes the 5375 CellSetDump to any save via `PKC_SOLVE_FILE`.
  Emits `tools/miqcp/roc2-cellset-span<sp>-<tag>.json`; aborts unless the cell-constant
  reconstruction is bit-exact vs `ExactJumpModel.forward`. Knobs: multi-point union
  (`PKC_CELLSET_POINT` semicolon list), per-tick wide span (`PKC_CELLSET_WIDE_TICKS/WIDE_SPAN`),
  A/D mirror families (`PKC_CELLSET_ADSUB`), coarse full-circle sampling
  (`PKC_CELLSET_COARSE_TICKS/COARSE_STEP`). Verify mode `PKC_ROC2_VERIFY` re-forwards MILP
  solutions in Java. Solve with `tools/miqcp/cellset_milp.py --goal-wall padGE --zlo-tighten 0`
  (MUST pass zlo-tighten 0: pinned start). COPT and Gurobi licenses reject these sizes; SCIP
  carries everything. LP-vs-byte-exact slop is ~1.5e-6 at corner-riding optima (solutions ride
  Z@6845hi); repair before delivery, harmless vs 1e-4 margins.
- `NormScanScreen` (new): norm landscape scan across wrap re-expressions. (2026-08-21: never
  committed, not in the tree.)

### 10.3 Proven scope ceilings (SCIP optimal, byte-exact recompute drift 0)

Window ladder, +-0.7 deg facing tubes, wraps +-720, gates armed, roc2-best walls, pad -3754.3:

| free from | max X@T6846 | short | note |
| --- | --- | --- | --- |
| T6832 | -3754.3004866 | 4.87e-4 | also proven at +-2.8 deg / 9 wrap bases |
| T6823 | -3754.3004601 | 4.60e-4 | A/D mirror over 21 ticks adds NOTHING (proven equal) |
| T6822 | -3754.3003995 | 4.00e-4 | beats the smooth unit-norm ceiling by 4.4e-6 (half-angle lift, real but tiny) |
| T6808 | -3754.300391 | 3.91e-4 | bracket +-1e-6 (timelimit) |
| T6797 | -3754.3003661 | 3.66e-4 | proven; +-2.8 deg momentum widening adds NOTHING |
| T6791 | -3754.3003529 | 3.53e-4 | +-1.4 deg momentum tube, bound -3754.3003508 (timelimit) |
| T6783 | -3754.3003457 | 3.46e-4 | +-1.4 deg momentum tube, bound -3754.3003450 (timelimit); BEST |

Per-extension gains decay geometrically (2.7e-5, 6.0e-5, 0.9e-5, 2.5e-5, 1.3e-5, 0.7e-5),
asymptoting near 3.4e-4 short.

The user's 0.00049 is within 5e-6 of its own window's proven optimum. Monotone gains, all from
momentum-tick retiming, saturating hard around 3.5e-4 short. Every optimum rides Z@6845hi
exactly: the landing is a diagonal X-vs-Z trade and Z saturates, a geometric ceiling that small
facing changes cannot move.

### 10.4 Mechanisms eliminated (proven within stated scopes)

- Angle Wrap starvation: fixed, and irrelevant to the gap. WrapWindowIls converges in under 1s on
  any candidate (basin-limited, not budget-limited; kicks die on empty high-norm cell sets).
- Deep wraps: norm scan across ALL wrap re-expressions to +-12000 deg shows best norms ~9.6e-5
  (mostly 1e-7): worth ~1e-5-scale reach at best. The ~1.003 large half angles live beyond 12000.
- A/D mirror substitution (user idea): implemented exactly (flipped strafe constants, facings
  rotated by 2*atan2(sF,fF), own wrap bases). Proven zero gain over all 2^21 flip patterns at the
  6822 state; solver-side single-flip scan through WrapWindowIls agrees.
- MomentumAssembly: inapplicable (pinned start rejects templates). HomotopyCloser: eps ladder
  stalls before eps=0. Deep BnB (64 patterns): exhausts in seconds at pipeline-incumbent level.
  Cold free solves (49 to 63 ticks, 300-480s): all land 6.4e-4 or worse, never re-find the
  captured basin.

### 10.5 State at session pause

Best byte-exact legal point: X = -3754.3003457 (3.46e-4 short), 6782-window wide-momentum MILP
solution (needs the ~1.5e-6 Z-corner repair at delivery; Java-verified). In flight: the coarse
full-circle finder (6790 window, one exact cell per degree across +-180 on 17 momentum ticks,
tail at proven tube). Open scope beyond those: facing
changes past +-2.8 deg on momentum ticks with exhaustive menus (model sizes explode), different
key/jump patterns (outside the angle solver's decision space), start-history changes before T6783.

Verdict so far: NOT impossible-certified (smooth headroom +0.39 to +1.4 remains, full-circle
byte-exact unexplored beyond the sampled finder), but every searched-and-proven scope caps
3.5e-4 to 4.9e-4 short of the pad.

### 10.6 Final status addendum (2026-08-21)

Later sessions established: `roc2-best.json` is the authoritative best (.000494 short, viol 0);
`roc2.json` carries stale artifact walls; the tail is proven capped by the cell-set MILP; the
open lever is the momentum windows around ticks 6796/6790.
