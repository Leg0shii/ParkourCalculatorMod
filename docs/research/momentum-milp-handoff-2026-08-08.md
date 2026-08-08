# Cold momentum search handoff (2026-08-08)

Continuation document for the goal session. Read this fully before doing anything. Background: `CONTEXT.md` (nomenclature; use ONLY these terms, never invent names), `docs/research/stratfinder-v1-handoff-2026-08-08.md` (Strat Finder product state), `docs/research/stratfinder-levers-2026-08.md` (LP relaxation certificates precedent), `docs/reference/mcpk/` (physics ground truth).

## 0. Goal and acceptance gate

Build a GENERALIZED cold search that, given ONLY a capture's placed constraints (the tick-0 X/Z start rect is already among them, plus the per-tick boxes the player placed as geometry oracle) and the segment bounds (startTick, landingTick), produces a full input line (key rows, press ticks, one fixed momentum facing, jump-angle turn and air yaws) that satisfies every constraint, verified byte-exact through `ExactJumpModel`.

Acceptance captures, all under `core/src/test/resources/captures/hpk_human/`:

1. `d11/j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl.json` (2 presses, simplest; start here)
2. `d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo.json` (3 presses)
3. `d11/j1150-2x2bm_Nix_Neo.json` (4 presses, loop momentum with a backwards press)
4. `d12/j154_1bm_Head_Butterfly_Neo.json` (3 presses, backwards sidestep opening, knife-edge entry; the finale)

Rules, all user rulings:
- COLD: the human keys, yaws and press ticks must never be used as seeds or warm starts. Constraints and segment bounds only. Warm-starting from the answer is cheating.
- GENERALIZED: one method, shared code and settings across all four captures. If the approach degenerates into per-capture bespoke logic, stop and report.
- Byte-exact: a real-arithmetic solution is a proposal, not a result. Every candidate line must be verified through `ExactJumpModel`; only verified lines count.
- Any valid line threading the constraints counts as solving a capture. Rediscovering the human structure is the expected shape but not required.

## 1. Why this is believed tractable (the formulation)

The general angle-solver problem is nonconvex for exactly one reason: free yaw per tick puts sin/cos inside every tick's acceleration (see `docs/research/` notes; the repo established the movement formulas are linear in (sin, cos) with the integer sine LUT). The strat class here is no turn: ONE fixed facing through the whole momentum, one optional turn on the LAST space press (jump angle, ja), free yaws only in the air after it.

Fix the facing and the momentum side becomes linear:
- Per tick the input is one key combination from a small finite alphabet: W, WA, WD, A, D, S, SA, SD, none; sprint on/off multiplies magnitude; sneak scales. Each (keys, sprint, ground/air) pair is a KNOWN constant acceleration vector once facing is fixed (0.98 forward scale, 1.0 strafe, diagonal normalization, ground accel ~0.1 vs air ~0.02, sprint-jump takeoff boost 0.2 along facing on the press tick).
- Velocity at any tick is a friction-decayed linear sum of the chosen per-tick vectors; position is the linear sum of velocities. The placed constraint boxes are linear. The start position enters affinely through the t0 rect.
- Decision variables: one facing angle (snaps to significant angles, so effectively discrete; treat as an outer 1-D sweep), 2-D start position in the rect (continuous, affine), per-tick key choice (discrete, ~9-18 options), press ticks (discrete, outer layer).

That is a small mixed-integer linear program per (facing, press schedule) pair: at most ~40 momentum ticks with a handful of constraint boxes. Problems of this shape solve exactly in milliseconds to seconds. The pattern vocabulary (pessi, fmm, mark, jam, bwmm, their S-substituted backwards forms) is just particular assignments of these per-tick choices; the exact search covers all of them plus unnamed lines.

Known couplings that break pure per-tick independence, all manageable:
- Sprint is a state machine, not a free per-tick bit: needs W held and forward motion, disengages on S/sneak/collision, and the sprint FACTOR lags isSprinting by one tick (`factorSprintAt` in the model). Handle with a small DP state (sprint on/off) or constraint rows tying SPRINT to W sequences.
- Ground/air pattern per tick derives from the press schedule alone, because Y physics is independent of X/Z, EXCEPT at block edges: the edge-jump mechanic (Y collision resolves at pre-move X/Z, so onGround persists past the edge; jump legality is the tick BEFORE the press within block hitbox +0.3; the press tick may overhang). This couples groundedness to horizontal position at cycle boundaries. Branch on the boundary tick (2-3 cases per press).
- Jump cycle durations come from ceilings: 12 ticks flat, 9 at +1, 3 headhitter, etc. The press-schedule outer layer is bounded by these.
- Once keys are frozen, position and velocity are affine in the start position, so a knife-edge miss after byte-exact verification gets repaired by a tiny 2-D re-solve of the start position inside the rect, then re-verify.

## 2. Decomposition and the coupling question

The final press carries the actual jump: ja turn allowed on that press, free solver yaws in the air. That part is the existing solver's home turf. The momentum before it is the new exact search. Coupling options at the last press (state is 4-D: x, z, vx, vz), in rough order of preference; the right choice is a design decision for this session:

- (a) Lazy probe: for each momentum candidate (or MILP incumbent), probe the jump side with a direct `ClosedFormSolve` call given the momentum end state; milliseconds each, usable inside branch and bound.
- (b) Velocity-map target set: `VelocityFinder` already sweeps entry velocity with one closed-form solve per cell and its feasible region's boundary IS the binding-constraint envelope (nobody has to guess which wall binds). Would need extension from (vx, vz) to the 4-D entry state or per-candidate takeoff positions.
- (c) Joint certify: full `AngleSolverEngine` solve of the whole segment only for finalists.

Fast tools inventory (all existing, all core/main unless noted):
- `ClosedFormSolve.optimize` / `optimizeRobustGraded`: direct closed-form solve, no engine scaffolding; this is the velocity map's per-cell path (`VelocityFinder.java:116`), millisecond scale.
- `ClosedFormSolve.dualBound`: infeasibility bound in microseconds (used as reachBound in `GraphContext`).
- `LongRunSolver` (receding horizon; window/commit ladder, closed-form per window) and its graph node `RecedingHorizonNode`.
- `FreeStartSolve`: existing start-position handling within the t0 constraint.
- LP relaxation certificate precedent: scipy HiGHS 24-gon outer approximation (see levers doc); sound infeasibility supersets.
- Test-side `metriclab/ReachBound`: sound speed-norm necessary condition, ~70 us/instance.

Cost context: the Strat Finder pays 30-150 ms engine scaffolding per FEASIBLE variant and burns its whole budget (default 2000 ms) on infeasible ones. The direct paths above are why an exact search can afford wide enumeration.

## 3. Verified domain facts (this session, from per-tick dumps of rows + debug yaw/onGround)

These were verified against the captures after a wrong earlier claim; treat as ground truth and re-derive if in doubt (a ~60-line script reading rows[].keys, debug[].yaw, debug[].onGround per tick suffices; debug[t] is the state at the start of tick t).

- Facing is DEAD FLAT through every momentum press in all 34 hpk_human captures. Nobody turns in momentum. The only facing changes are on the LAST press (ja: j140 -70.5 deg, j335 -91.1, j925 -62.3, j154 -102.9) or in the air after it. On distance jumps the 45 strafe engages the tick AFTER the last press (j030, j066: 0.0 to 45.0, then held).
- ja means a turn on the LAST space press ONLY. Never on intermediate presses. (User ruling, confirmed by data.)
- Strafe side changes per jump cycle under the one fixed facing: j345 runs W+D momentum then W+A air; j154 S+D, then W+A, then W+A. Strafe orders use community names: WAD / DAW, key-on-jump WDWA / WAWD, chinese (same side held for both).
- Backwards presses are real: j154 press 1 is S+D+SPRINT+JUMP (backwards sidestep jump), j038 press 1 is S+JUMP, j1150 does a neutral hop (JUMP, no movement keys) then A+S glide into A+S+JUMP. S-substituted timing families exist in the community (pessi with S, mark with S); fmm with S does not (cannot sprint backwards).
- Cycle lengths in the pool: 3 (headhitter), 6, 12-13 ticks. 27 of 34 captures are multi-press (2 to 5 presses).
- Acceptance capture structures (facing fixed value, then presses):
  - j925: facing 66.7. Press SPRINT+JUMP at t0 (no W), glide SPRINT, W+SPRINT from t2, W+D from t4; last press W+JUMP at t13 with ja -62.3 into W+A air settling -49.9.
  - j716: presses [5, 18, 31], first press A+JUMP (strafe-only press).
  - j1150: facing 20.5. Neutral hop JUMP at t0, A+S glide, backwards press A+S+JUMP at t13, turnaround W+D+SPRINT from t16, press W+D+SPRINT+JUMP at t25, last press W+SPRINT+JUMP at t38, W+A air settling -45. Loop momentum.
  - j154: facing -76.6. Press S+D+SPRINT+JUMP at t2 (backwards sidestep), S+D glide, S+SPRINT at t14; press W+A+SPRINT+JUMP at t15 with W released at t16 (A+SPRINT); last press W+SPRINT+JUMP at t28, ja -102.9, W+A air settling -225 (raw). Entry threads ~1e-5 in front of the first wall constraint: the knife edge that motivates exact search over pattern substitution.

## 4. What was rejected or parked this session (do not resurrect without cause)

- Per-press family cross product and any "steer by overshoot/undershoot alone" ladder: substituting timing families (pessi1 vs pessi3) changes speed and phase together, non-monotonically; a scalar miss signal can walk the wrong way. The target-set / exact formulation replaces steering.
- The invented "turn at every press" camera shape: contradicted by the data; deleted from discussion. Camera vocabulary is nt, ja (last press), 45.
- Fixed +-12 shift window in `StratVariants`: wrong for short cycles; the correct window is the jump cycle duration minus 1 per press. Parked as a Strat Finder follow-up, not part of this goal.
- S-substituted families in `StratPlans`: real vocabulary, parked as a Strat Finder follow-up; the exact search subsumes them here.
- The pattern grammar itself stays valuable as playability prior, warm starts (for non-cold uses), and human-readable labels; it is NOT the search mechanism for this goal.

## 5. Traps and practicalities

- Working tree state: branch `feature/stratfinder` carries UNCOMMITTED Strat Finder filter/window UX changes in 5 files (StratVariants, StratFinder, StratFinderController, StratFinderWidget, StratVariantsTest), headless-green, awaiting the user's commit and in-game QA. Do not clobber; new work should live in new files/packages where possible.
- j154 was once flagged for re-save in the difficulty-metric thread; sanity-check the four captures load and their stored segments are consistent before building on them.
- Captures' debug arrays exist because they were saved with fullDebug; debug[t] is the state at the start of tick t (`sprintAtFireMatches` reads debug[fire+1]).
- The model is X/Z + tick only; collisions are never simulated; the placed constraints ARE the geometry. Never add collision simulation.
- `:core:test` is the gate; solver changes need the full `-PslowTests` suite; heavy screens need `-PtestHeap=3g`; gradle ignores PKC_* env changes without `--rerun`. PowerShell: quote `-P` args.
- Run supervision stays in-session; never delegate long-run supervision. State the cost of any run over 5 minutes before launching.
- Never run git commit/push/branch; ask at commit points and provide the message. No agent attribution in commits.
- Use community nomenclature ONLY (CONTEXT.md, docs/reference/mcpk). If a concept has no community name, describe it in plain math terms (e.g. "mixed-integer linear program"); do not coin words.

## 6. Suggested milestone ladder

1. M1: formulation spike. Momentum-side exact search (fixed facing sweep, press schedule enumeration, per-tick key MILP or DP, start position affine) + lazy closed-form jump probe. Gate: j925 cold, byte-exact.
2. M2: j716 (strafe-only press), then j1150 (backwards press, neutral hop, loop momentum; sprint state machine and edge-jump branching earn their keep here).
3. M3: j154. Knife-edge entry; expect the start-position affine re-solve and byte-exact repair loop to matter.
4. M4: regression: the solved four as fixture tests (SlowSolverTests category), plus a sanity pass over a few more multi-press hpk_human captures to defend "generalized".
