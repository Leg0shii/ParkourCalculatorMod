# HANDOFF: no-turn stratfinder (ARCH-2 outer layer) - design + COPT proof-of-concept findings

Paste-ready handoff. Self-contained; read `DESIGN.md` (the ARCH-2 inner angle solver) and issue #422 first.
This is the OUTER input-search layer that *calls* the #422 certified angle B&B as its inner oracle. The
#422-comment draft (`422-noturn-comment.md`, next to this file) is updated with the PoC results; NOT posted yet.

STATUS 2026-08-26: the PoC is DONE and it WORKS. Section 4 records the corrected findings (the old
"3-block momentum blocker" was an authoring artifact); section 4b records the working pipeline and the
byte-exact-feasible cold no-turn it found on j1150.

## 0. What / where it sits

- #422 (ARCH-2) = the **inner** angle solver: given a FIXED input structure, find the byte-exact global-optimal
  facings (certified spatial B&B; FBBT + costate + arc chord cut + disk kernel).
- This handoff = the **outer** no-turn stratfinder: given a jump's STRUCTURE (jump/`space` positions, run-tick
  placements, ground/air pattern, precise constraints) with **inputs + angles stripped**, FIND a NO-TURN.
- User (Leg0shii) intent, verbatim gist: "the new solver should bruteforce efficiently over keys and additional
  run ticks; the main goal is to figure out if there is a NO-TURN for a jump; I want a GLOBAL OPTIMAL solver."

## 1. The no-turn problem (grounded in real human strats in `hpk_human/`)

NO-TURN = every tick except T1 has **dF = 0 (constant facing θ) up to and including the last `space`**; only after
the last `space` does the facing turn. The whole *setup* is aimless: press keys on time, then execute one turn.
That is the "holy grail" (inhuman line-up precision; aiming reduces to one turn). Variants:
- **pure** (dF=0 even on the final jump tick) - e.g. `j1150-2x2bm_Nix_Neo`: constant 20.53 deg across T1..T39,
  then the turn at T40+. Keys move only at EDGES: nothing -> AS(back) -> WD(fwd, sprint engaged once at T17) -> WA(turn).
- **no-turn + ja** (the last jump tick carries a jump-angle, dF!=0; acceptable, worse) - e.g. `j716`,
  `j716_cold_sibling`, `j716_new` (last uses SNEAK on T1).
Run ticks = grounded ticks inside air ranges. Structure per the user: before jump 1 add 0-5 run ticks; between
jumps 0-1; keep run ticks BEFORE the tick-before-`space` so its block constraint shifts with its tick. Sprint =
engaged once then held (find the tick). Sneak = rare (advanced). The rough structure (`space` positions) is GIVEN
by the user and MUST NOT be searched.

## 2. THE MATH (the key result - the user's own insight, and it is exact)

The keys are not an abstract mode: the strafe offset from W/A/S/D is EXACTLY one of 8 angles
{0,45,90,135,180,225,270,315}; sprint/sneak/cardinal-vs-diagonal/ground-vs-air only scale the MAGNITUDE. The model
already stores this as `mMag(t)` (strength) + `baseArg(t)` (coarse offset); move =
`mMag(t)*(cos(baseArg(t)+yaw(t)), sin(baseArg(t)+yaw(t)))`. For a no-turn `yaw(t)=theta` is constant, so per tick
you choose only an integer `k_t in {0..7}` (baseArg=45*k_t) + a small strength; the ONLY fine-grid unknown is theta.

Substitute `a=cos theta, b=sin theta`:
`u_t = mMag_t*(cos(45k)*a - sin(45k)*b, sin(45k)*a + cos(45k)*b)` is **LINEAR in (a,b)** for fixed (k_t, strength).
Under a fixed gate pattern the whole position chain is linear in the u_t, hence linear in (a,b) + the discrete
choices; walls are linear. **The only nonconvexity left in the entire setup is the single circle `a^2+b^2=1`.**
dF=0 has collapsed n unit-circle constraints (one per facing) to ONE, plus a per-tick coarse integer.

Class: a Mixed-Integer Optimal Control Problem on a switched/hybrid system, but the no-turn collapse + coarse-key
grid reduce the SETUP to a **Mixed-Integer program with a SINGLE nonconvex quadratic** = essentially MILP-hard.
Extra pieces slot in without new nonconvexity:
- **Free start** `(p0x,p0z)`: a *linear* rigid translation (existing `FreeP0` term) -> 2 free continuous vars.
- **Run-ticks**: change horizon length n -> small **prefix-pruned outer enumeration** (reuse `RunTicksSearch`,
  before-1 in {0..5}, between in {0,1}); each vector a fresh inner solve. Or absorb as optional DP steps.
- **Turn**: the few post-`space` ticks keep the fine 65536 grid -> the #422 B&B (small).

## 3. THE BEST GLOBAL-OPTIMAL SOLVER (recommended)

Bilevel certified B&B, setup reduced to single-quadratic hardness:
- Setup MILP/MIQCP: vars `(a,b,p0x,p0z,{k_t},{s_t})`; disk `a^2+b^2<=1` + per-tick offset/strength SOS1 + linear
  walls; recover exact theta by branching the ONE arc with the chord cut `cos(mu)a+sin(mu)b>=cos(Delta)` (~8
  bisections, snap once). Equivalently a coarse-action reachability DP (<=8 actions/tick over (pos,vel), dedup,
  free-start = seed box). Cast transition constraints (sprint-once monotone, contiguous holds, run-tick inserts,
  shared-theta) as a **Graph of Convex Sets** (Marcucci-Tedrake, SIAM J. Opt. 2024) if a tighter-than-MILP global
  relaxation is wanted. Turn = the #422 byte-exact B&B; round-and-simulate through `ExactJumpModel` -> gap=0.
- NOT the MIOC rounding toolkit (outer-convexification + CIA + sum-up rounding, Sager/Bock/Kirches): those are
  bounded-suboptimal, not certified. For GLOBAL, use single-quadratic MILP/GCS + byte-exact turn B&B.

## 4. COPT PROOF-OF-CONCEPT - corrected findings (2026-08-26 session)

Harness: `research/copt/` (`coptlib.py`, COPT installed+working). Struct export = the Java test
`StructureDump` (env `PKC_STRUCT_FILE`, `PKC_STRUCT_OUT`; writes relative to `core/`); it dumps per-tick
`mMag/baseArg/cx/cz` + `walls{coef,bPrime,p0coef,axis,eq}` + `startBox` + warm. NEW: env `PKC_STRUCT_ZERO`
(json `{zeroX:[bool],zeroZ:[bool]}`) folds a momentum-gate zeroing pattern via the pattern-aware
`JumpLinearModel(sc, zeroX, zeroZ)` and appends the `velocityWalls(threshold)` inertia walls.

FINDINGS (items 3 is a CORRECTION of the prior session's record):
1. **The MIQCP model is correct and COPT-encodable.** Single circle `a^2+b^2=1` for the setup facing, setup
   movement linear in (a,b), free-turn `|u|=mMag`, free-start p0, linear walls. It builds and runs.
2. **On an AIR-dominated single jump it is faithful.** (Prior session, `3-other`): COPT continuous == byte-exact
   to ~1e-5; the start-box sweep proved start-independence; the linear relaxation is tight there.
3. **CORRECTION: the old "momentum blocker" (warmViol = 3.15) was an AUTHORING ARTIFACT of that session's
   export, not physics.** On the properly authored structure (the in-repo capture and the `inputs_gone`
   authoring) the recorded human strat replays byte-exact CLEAN (warmViol 0.0, warmObj -2805.2979844800548),
   and the clamp-free linear chain is faithful to ~1e-2 on j1150 (objective delta -0.011, worst wall +0.035
   on the recorded inputs). The residual ~1e-2 infidelity is entirely the per-axis 0.005 inertia GATES firing
   on a handful of low-velocity ticks (each zeroed ~2e-3 velocity is amplified ~11x by the friction chain).
   Z was clean and X drifted +0.008..0.013 on the phase-A anchor; the whole drift traced to gate events at
   exactly 3 ticks.
4. **The offset-search unfold works as designed.** `u_t = accel_t(sprint)*kappa_c*R(45k_c)(a,b) +
   0.2*s_t*i(a,b)` on jump ticks; per-tick accel probed from the Java model via two W-held exports (sprint
   on / off): ground 0.100000/0.130000, air 0.02/0.0259..., cardinal x0.98 / diagonal x1.0, boost 0.2.
   Legacy air factor uses the LAGGED sprint `s_{t-1}` (`factorSprintAt`); ground attr + boost use `s_t`.

## 4b. THE WORKING PIPELINE + RESULT (j1150 `inputs_gone`, cold, no recorded data used)

Files: `research/copt/noturn_miqcp.py` (build/solve/decode; flags: `--relax-circle --disk --min-slack
--margins --fix-combos --trust-decode/--trust-k/--trust-theta --mip-start --dump-vars --sprint-fix`),
`noturn_slp.py` (signed per-wall byte-vs-linear corrections at an anchor), `noturn_mkstart.py` (MIP-start
json from a decode), Java `NoTurnReplay` (env `PKC_NOTURN_CAPTURE/FILE/OUT`: byte-exact replay of a decode
through ExactJumpModel + the spec's compiled constraints; velocities dumped for gate-pattern extraction).

What was LEARNED, in order (each step measured, do not re-litigate):
- **COPT finds NO incumbent on the exact-circle MIQCP cold** (0 incumbents in 500k+ nodes across 3 runs;
  bound converges fine). Incumbent-finding is THE practical bottleneck - this is precisely the gap the
  ARCH-2 costate warm start fills.
- **The convex disk relaxation (`--relax-circle --disk`) finds incumbents via heuristics AND saturates
  |(a,b)| = 1 on its own** (abNorm 1.0000000003): more thrust = more objective. So relax-then-verify is the
  right shape; the EQ model is never needed for search, only the decode.
- Phase-A cold result: theta 20.503 deg (human: 20.535), sprint engage 18 (human 16), free start picked at
  (-2802.735, 4971.058), combo schedule with the same shape as the human's (back-shuffle -> forward rush)
  but a richer SA/SD/S mix. Byte-exact replay: **X@49 = -2805.2906, i.e. 0.0074 blocks BETTER than the
  human**, but 4 walls violated by up to 0.0134 (the gate infidelity).
- **SLP-0 signed margins FAIL across combo flips.** The byte-vs-linear offset is a DISCONTINUOUS function of
  the schedule (the gate pattern flips): a min-slack witness predicted 0.010 viol, actual replay 0.034; and
  trust balls around the anchor (Hamming k<=12, theta +-3 deg) were PROVEN infeasible in seconds under the
  corrections. Do not retry constant-offset SLP on momentum jumps.
- **THE ANSWER: fold the anchor's GATE PATTERN into the linear model** (Fact A of DESIGN.md, implemented via
  the existing pattern-aware `JumpLinearModel` + `velocityWalls`). Fidelity at the anchor went 1.3e-2 ->
  <= 1.5e-4 on every wall (the remainder is float-vs-double + sine-table noise; a uniform 3e-4 wall margin absorbs
  it). The pattern is SPARSE: zeroX@{0,18,46}, zeroZ@{0} on the anchor; {0,2,4,8,9,23} Z after round 1.
- **Gate-pattern fixed-point loop converged in 2 rounds** (solve on pattern -> replay -> extract new pattern
  from replay velocities -> re-fold -> re-solve), each pattern solve reaching OPTIMAL in 14-18 s (trust ball
  k=12 theta 3 deg + MIP start from the previous round's binaries).

**RESULT: a cold, byte-exact FEASIBLE pure no-turn on j1150** (maxViol 0.0 through ExactJumpModel + the full
compiled constraint set, dF=0 exact on T1..T38): **X@49 = -2805.2990460856336**, theta 20.2075 deg, sprint
engage 17, start (-2802.859, 4970.406). Reference: the recorded human reaches X = -2805.2979844800548 but is
NOT feasible for this spec (its dF@38 is 0.052 deg off a pure no-turn). So this is the best KNOWN feasible
point; the human is 1.1e-3 ahead on X only by breaking dF=0 on the last setup tick.

A no-trust confirmation run (MIP-started from the feasible point; needs `--feastol 1e-8`, at 1e-9 COPT
rejects its own solution by 1.6e-9 primal inf) moved the linear optimum only 3.6e-6 and its decode also
replays feasible at X = -2805.29906: the pattern-model optimum neighborhood is found; its own relaxation
bound was still 0.021 open at 300 s.

CAVEATS (what "optimal" means here): certified optimal only within {the fixed gate pattern + trust ball +
3e-4 margins + modeling restrictions}. NOT yet a global certificate: the unmargined relaxation bound was
-2805.2546 after 300 s and still moving. Modeling restrictions in the PoC: monotone sprint with
sprint=>forward-key (re-engage not modeled), no sneak, turn ticks forced to diagonal magnitude (WA/WD
sphere), gate pattern fixed per solve with only the zero side enforced (`velocityWalls`; the keep-alive side
is not, the replay catches violations), sprint-machine legality trusted at the flag level.

## 5. PRIOR ART: `feature/stratfinder` branch (already does the reduction; must be superseded)

Full study in this session's agent report; canonical doc on that branch: `docs/research/stratfinder-coldsearch.md`.
Three code bodies: (A) warm substitution `stratfinder/StratFinder` (shipped "Refine recording"); (B) cold
byte-exact engine `coldsearch/*` (`ColdProblem,ArcSweep,ColdSearch,ColdBeamSolver,ColdMitmSolver,KeyLine`); (C)
block bridge `stratfinder/BlockStratFinder,ProblemCompiler`. KEY POINTS for the redesign:
- (B) ALREADY implements the user's insight: "with facing fixed, the run-up exit factors as R(theta)*S, a
  theta-free 4-real accumulator" -> each fixed-facing momentum is a small global opt; 9-combo alphabet
  {NONE,W,WA,WD,A,D,S,SA,SD}; sprint-engage searched in {0,1,2}; dF=0 enforced structurally (`ColdProblem` throws
  unless momentum ticks 1..lastPress-1 are dF=0); byte-exact judge is `ExactJumpModel` + `JumpConstraintCompiler`.
- **HONEST CEILING: it is an L2-L3 cold solver.** Beam + MITM; explodes at change-level >=4 on the hard jumps
  (j716, j154) in the wide-arc run-up (every pruning avenue measured-dead). NO global certificate.
- => The new global single-quadratic MIQCP/GCS REPLACES the beam/MITM combo search with a certified global bound
  (that is exactly what defeats the wide-arc explosion). Reuse: `KeyLine` alphabet, `ColdProblem.fromSave`
  structure derivation, the `ExactJumpModel` byte-exact judge, `RunTicksSearch/Controller` for run-ticks.
- Two duplicate physics impls on that branch (symbolic `ArcSweep` vs per-theta `ColdSearch.Sweep`) - consolidation
  target. Corpus grammar prior (`CorpusIndex`, `resources/strats/library.json`) is a label lookup, not yet a
  data-driven prior (flagged remaining task).

## 6. GOTCHAS / plumbing facts (cost me real time - do not rediscover)

- **Inputs are sampled from the debug TickStates, NOT the row keys.** `moveForward/moveStrafe/sprinting/onGround`
  live on `debug[t]`; the solver samples input-at-tick-t from `debug[t+1]` (per the "sampled inputs" rule). A probe
  that sets only `rows[].keys` yields **all mMag=0**. Set `debug[i].moveForward/moveStrafe/sprinting` (and keep the
  ground/air `onGround` pattern) to give a probe movement.
- StructureDump uses `state.getStartTick()`; override startTick in a scratch save (`d['angleSolver']['startTick']=0`)
  to export the FULL trajectory. It writes `PKC_STRUCT_OUT` relative to `core/` (move it to `research/copt/data/`).
- COPT default quadratic tolerance is loose (~1e-6): a `|u|=mMag` sphere solve can "cheat" the modulus by ~1e-6/tick
  and fake ~0.001 of reach over ~48 ticks. **Always set `FeasTol=1e-9`** and check `max|u^2-mMag^2|` before trusting
  a below-target COPT result (this bit us on the `3-other` "landing" false positive).
- `Scoring.pinnedScenario(sc,x,z)` is public and equals a copy-with-start; `FreeStartSolve.copyWithStart` is
  package-private (test can't call it).
- The `inputs_gone` capture (`hpk_human/d11/j1150-...inputs_gone.json`, user-authored, PRECISE constraints): n=49,
  X-MAX@49, free-start box X[-2804.3,-2802.7] Z[4969.7,4971.3] at T0, explicit `DF EQ 0` on T1..T38 (76 F
  constraints), momentum landings X/Z-IN at T12/T24/T37 + final at T49 (+ turn walls T41/T42/T48), JUMP kept at rows
  {0,13,25,38}. Structure is EXACT, must NOT be searched.

## 7. ARTIFACTS

- `422-noturn-comment.md` (next to this file): the #422-comment draft with the PoC results folded in. NOT
  posted; ready for the user to review/post.
- `research/copt/`: `coptlib.py` (base harness), `noturn_miqcp.py` / `noturn_slp.py` / `noturn_mkstart.py`
  (the no-turn pipeline, section 4b), `data/` (j1150 struct exports, probes, patterns, decodes, replays;
  `noturn-j1150-p2-decode.json` + `noturn-j1150-p2-replay.json` are the feasible result).
- Java tests: `StructureDump` (now pattern-aware via `PKC_STRUCT_ZERO`), `NoTurnReplay` (byte-exact judge).
- This handoff.

## 8. NEXT STEPS (in order)

1. **Wrap the loop as one driver**: relax-solve -> decode -> replay -> extract gate pattern -> re-export ->
   trust-region re-solve, iterate to the pattern fixed point (it converged in 2 rounds on j1150). Currently
   run by hand (the exact command sequence is reconstructible from `research/copt/data/*.log` names).
2. **Second corpus target**: `j716` (no-turn + ja; the ja tick needs the last setup tick's facing freed) and
   a run-tick-bearing structure (outer `RunTicksSearch` enumeration x this inner solve).
3. **Close the global certificate**: the fixed-pattern iteration is a local method. The certified version
   needs gate-pattern BRANCHING (the #422 3-way gate split) + the costate warm start for incumbents (COPT
   heuristics cannot find EQ-circle incumbents cold, section 4b). That is the actual ARCH-2 build; this PoC
   de-risked the formulation and measured every failure mode.
4. **Post the #422 write-up** (`422-noturn-comment.md`) after user review.
5. **Missing model pieces** before production: keep-alive side of the gate pattern, sneak alphabet, sprint
   re-engage legality via the real sprint machine, turn-tick magnitude choice (W vs WA vs NONE), and the
   objective-vs-easiness ranking (pure > ja, fewest edges/run-ticks).

## Hard rules (unchanged): never git commit/push/branch (user does git); no code comments; no em dashes; core/ MC-free;
byte-exact FEAS_TOL=0; verify byte-exact through ExactJumpModel (self-agreement / COPT-continuous is NOT verification).
