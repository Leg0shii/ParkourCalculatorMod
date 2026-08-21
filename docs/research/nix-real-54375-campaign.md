# The real 5.4375bm: campaign record (2026-07-08)

Addendum 2026-08-21: the proof was later seeded and improved in-tool (+2.4e-5, user-confirmed; see razor-campaign-2026-07-09-handoff.md) and is pinned in-repo as captures/razor-proof.json. Section 5's "DLS over significant angles" description of Sheepram was corrected by source audit (nix-solver-handoff.md section 2): no significant-angle prioritization exists in their code. All section 2/6 harnesses except CoefDump were deleted in the 2026-08 cleanup.

Status: **resolved by external proof, campaign closed**. The user hand-recreated the prover's run (5.4375bm_nix_proof.json); it replays byte-exact in ExactJumpModel (posDiff 8.9e-16, feasible viol 0, pad margin 1.64e-4) and satisfies our entire constraint encoding. Our spec was correct throughout; our best search residual was 2.59e-4 because the solution basin is a five-wall razor no search stage could thread. See section 6 for the post-mortem.

## 1. The problem

Land the 5.4375bm nix jump: from the momentum area (walking boxes X [9.699999988079071, 11.300000011920929], Z [1.512499988079071, 7.3]), build momentum, and reach the pad.

Exact constraints (all user-verified against the real map, including a final re-check of the walls):

- Pad, display T62 (internal 61): X in [8.7, 9.7], Z in [9.550000011921, 10.3]. The Z floor was originally mis-entered as 9.4875 and corrected by the user; the 9.4875 version WAS solved (see 3.1).
- Approach floor, display T61 (internal 60): Z >= 9.550000011921.
- Blockage: Z <= 7.949999988079 at display T54 (internal 53), X <= 8.7 at display T55 (internal 54). The route must U-turn around this.
- Walking rule: the land tick and every grounded walking tick must satisfy the boxes; jump ticks are exempt (their position may extend one tick of velocity past the box, coyote).
- Seed for the window attack: t24 state (11.300000011692488, 1.512500004242992), vel (0, -0.26917396471768845). Start free for full-route attacks.

Timeline semantics (hard-won, verified against the user's language):

- Display tick = internal tick + 1.
- A jump cycle on flat lands on the 12th display tick inclusive of the space tick: space internal k lands at internal k+11.
- "Jumps without running" = land internal k, space k+1 (12-tick space gap). "Runs a tick then jumps" = land k, walk k+1, space k+2 (13-gap).
- The route pattern (space gaps 13, 12, 13): spaces internal 12, 25, 37, 50; land states internal 23, 36, 48; run ticks internal 24 and 49.
- Land-tick physics: the land tick's input is still air physics (onGround flips at move end); the first ground-accel tick is the next one. The file's slip-override set (0, 12, 24, 25, 37, 49, 50) plus boxes at the land states (23, 36, 48) is exactly right.

## 2. Machinery built for this campaign

All in core/src/test, byte-exact via ExactJumpModel, runnable direct-JVM (no gradle) with MSYS2_ARG_CONV_EXCL="*":

- NixWeirdClose.close(): window solver. Staged global entry (SolveCore on HomotopyCloser.relax at eps = 0.5x and 0.1x warm violation), HomotopyCloser ladder, seam ladder. Env: PKC_WC_FILE, WARMDUMP (TASROW-format warm), JUMPVAR (move a jump, extending ground when moving later), START0 + STARTX/STARTZ (full-route from pinned start), GATECANCEL (inertia-gate cancel constraint at a tick), HEAVY.
- HEAVY mode: BnB feasTol ladder (0, 1e-3, 5e-3, 2e-2, 180 s each), 768-restart megasearches on eps-relaxed specs (3e-2, 1e-2), then per-candidate intense close: 4 cycles of (300 s ladder + 12 kick perturbations with feasibility-only CMA), then seam-split pinned closes at seams 26, 25, 24, 13.
- NixArcMap: final-arc feasibility map over entry (position, velocity) grid; the entry velocity convention is the pre-jump t-start velocity; displacement-to-velocity conversion must use f4 of the previous tick (ground 0.546 vs air 0.91).
- NixMomentumScan: all-inputs reachability frontier (48 actions: 16 facings x {W, W+A} sprint plus non-sprint S/A/W/idle at cardinals), ~300 seeded starts, dedup grid on (pos, vel), packed 6-bit action history, phase-aware retention with kinematic box-debt projection.
- NixZPlan: 1-D Z-axis bang-bang planner (hop switch tick, cancel trim, exact stepper).
- CoefDump: per-tick (f4, mMag, baseArg) from JumpLinearModel for external solvers.
- Wolfram QCQP (momentum_qcqp.wl): forward phase as 26 angles + free entry state, exact coefficients, NMaximize DifferentialEvolution (killed before producing results; superseded by events).

## 3. What was tried, in order, with results

### 3.1 The uncorrected pad (Z >= 9.4875): SOLVED

- Heavy basin search bracketed the gap; the arc map showed landing demands vz 0.30-0.40 at the last jump while a no-run-tick lead physically caps at vz 0.236.
- The user supplied the key: the 5.5bm strat has a ground run tick before the final jump. With land T50, run T50->T51, jump T51, pad T62: closed in 41 s, viol 0, X@T62 = 8.7086713 (margin 8.7e-3). Delivered as SOLVED_REAL_5.4375bm_runtick.json (locked game-facing rows).
- Lesson: the pad constraint tick depends on the jump tick (jump T50 -> pad state T61, jump T51 -> pad T62); the user's two UNSOLVED files were the two encodings of the same physical pad.

### 3.2 The corrected pad (Z >= 9.550000011921): the 2.6e-4 plateau

The +0.0625 shift killed the margin. Heavy runs on the honest encoding:

- BnB basin at tol 5e-3 (viol 4.96e-3), megas relaxed-feasible at 3e-2 and 1e-2, nothing at tighter.
- Intense close ground three INDEPENDENT basins (BnB, mega-3e-2, mega-1e-2) down to 2.88e-4, 2.80e-4, 3.09e-4 respectively, all plateauing; seam-splits from every plateau missed.
- Same on the prover-structure encoding: plateau 2.59e-4 (the overall best).
- The recurring ~3e-4 residual across independent basins and encodings is the signature of a genuinely (near-)infeasible spec, not a search failure.

### 3.3 Arc feasibility analysis

- From fixed realistic entries the arc alone is 8e-3 to 9e-3 short; full-window co-optimization (hotter arrival into the run tick) accounts for the improvement to ~3e-4.
- The physical pinch: z@48 and z@49 <= 7.3 cap the launch runway; the ground run tick caps entry vz at ~0.20 (friction 0.546 eats the displacement); the pad needs Z >= 9.55 eleven moves after the jump; X must simultaneously U-turn around the 8.7 wall.
- The pz-vz trade along the reachable entry manifold: higher entry z reduces the needed vz but trips the Z@53 <= 7.95 wall.

### 3.4 Full-route attacks

- t0 solve from the file's stale warm (NixLadder): ceiling 8.5896, failed.
- t0 solve pinned at the prover's approximate start (11.0819, 2.973): ceiling 8.6803, failed (pinning at approximate coordinates plus a wandering forced-W prefix hurts).
- Prover structure decoded from the user's description: start ~(11.08, 2.97) at rest, backward-net hop (space T13, jump toward +Z then air-turn to minimize Z), land T24, run, space T26, land T37, space T38 (no run), land T49, run, space T51, pad T62. This maps EXACTLY onto the file's jump grid; the only differences from the user's route are the start and the small hop replacing the long backward run.

### 3.5 The false solve (retracted)

- Wrongly concluded the boxes at internal 23, 36, 48 were phantom (off-by-one in cycle landing arithmetic), dropped them, and "solved" both routes (prover 8.7050804 via BnB tol 0 in 5.1 s; seed route 8.7000066 via the closer). The user caught it: display T37 is a land tick.
- Diagnostic value: the exploited freedom was a +1.46 block X-excursion at the t36 land (12.76 vs 11.3 max), far beyond legal coyote. Land states t23 (11.11, 2.38) and t48 (9.95, 6.69) were legal.

### 3.6 Structural variants (all refuted, all on the honest encoding)

| Variant | Rationale | Result |
| --- | --- | --- |
| Pattern (12,13,13): spaces 12, 24, 37, 50, run ticks at 36 and 49 | later ground accel survives friction better | plateau 4.3e-3, 16x worse |
| +X weave launch at jump 37 (x@37 >= 11.45, legal coyote) | direction the false solve pointed to | BnB NULL at 5e-3, mega infeasible at 1e-2 |
| +X weave launch at jump 25 (x@25 >= 11.45) | same | same, worse |
| Forced deeper -X launch at final jump (x@50 <= 9.38, <= 9.32) | more X-room for the U-turn | plateau 8.4e-3; the natural launch x ~9.43 is already optimal |
| Gate-cancel at t24 (reversal displacement small enough that the inertia gate zeros vz, from the prover start) | the user's inertia-abuse idea; a measure-zero target made searchable as a constraint region | best 7.0e-4, worse than baseline; t23 and t25 configs stopped when the campaign ended |
| 1-D pure-Z forward phase | principled bang-bang | caps vz@50 at 0.169 against the z-ceiling; the route must build speed on the diagonal, confirming 2-D search is necessary |

The sideways/coyote freedom at the final jump is already fully exploited by the incumbent solutions (run tick at x = 9.7 edge with vx ~ -0.26, jump tick at x ~ 9.43).

### 3.7 Momentum scan (all input combinations)

The reachability frontier survived the hop and forward build but collapsed at t46-t47: the z-budget razor (z@24 ~ 1.5-2.5 to z@48 <= 7.3 in 24 ticks while accelerating) plus the x >= 9.7 box admits only razor-thin threading lineages that the 2.5 cm / 3e-4 dedup grid cannot reliably retain. The scan independently confirmed the geometry is a razor; it did not beat the continuous solvers. Parked; finer grids explode memory.

## 4. Conclusion and open ends

- On the confirmed constraint set, with the confirmed timeline semantics and every structural lever we know (jump timing moves, run-tick placement, weaves, coyote launches, gate cancel, free start, prover structure), the best byte-exact trajectory is 2.59e-4 short of feasible. Three independent basins agree to within 0.5e-4.
- The user has verified all constraint values (pad, approach floor, both blockage walls) against the real map.
- What would move this forward: concrete data from the prover's actual run (coordinates at the mid landing ticks T24/T37/T49, the hop's deepest z, or a recording) to pin the search to his corridor; or any legality detail not yet modeled (Y-axis effects, block-edge interactions, a different jump grid).
- If the prover's solve is real and the constraints are right, he is using a degree of freedom this model does not capture. The model is X/Z only by design; anything Y-coupled (landing a tick early or late off different block heights, jump arcs interacting with the blockage's vertical profile) is outside it.

## 5. Post-mortem: the proof (added after the campaign stopped)

The user obtained and hand-recreated the prover's actual run. Frame mapping: our coords = theirs + (-204, +3); our ticks = theirs + 12 (their hop fires at t0, no prefix). Verified byte-exact in our model with defaultInputs=KEEP.

The solution basin, and why every stage missed it:

- The hop lands at z = 1.51250 exactly (the box lo), and the jump-2 tick launches from z = 1.248: backward bbox-coyote, center 0.26 past the strip's rear edge with the bounding box still grounded. This freedom was legal in our spec (jump ticks unconstrained, slip authored) but no stage ever moved the dip below ~1.37; the basin volume in continuous space is near zero because the hop-land must simultaneously hug the box edge.
- Jump-3 launches at x = 11.3055, a +0.0055 forward coyote past the x-hi edge. Our forced +X weave probe (x >= 11.45) overshot the optimum 30x and concluded the direction was bad.
- Five constraints bind exactly: hop-land z-lo, both blockage walls (Z 7.95 at display T54-equivalent, X 8.7 at T55-equivalent), the 9.55 approach floor, and the pad edge (margin 1.64e-4).
- Their rows flip strafe A to D for one section; this is expressiveness-neutral (a -90 look-yaw shift makes the game facing identical) but breaks FORCE_45 replay with a global strafe sign; KEEP mode replays it exactly.
- The route was solved externally by Sheepram (ALM + BFGS) after it gained Discrete Local Search over significant angles, i.e. the sine-LUT bucket boundaries where quantized speed steps. A lattice DLS walks the constraint manifold in discrete steps and can thread razor basins that continuous CMA (zero basin volume) and pattern B&B (wrong branching granularity) both miss.

Engine consequence: our bucket machinery (BucketAscentPolish, the closer's sine-bucket descent) is local polish only (1-tick and 2-tick joint moves). Importing DLS over the bucket lattice as a first-class search primitive (multi-tick neighborhoods, prioritized by speed-significant boundaries, max-slack objective, plateau kicks) is the concrete upgrade; this jump is the known-answer regression case for it.

## 6. Where things live

- Problem files (scratchpad): corrected_real54375_baked.json (honest encoding, run-tick structure), prover_encoding.json (prover start anchor variant), patternB.json, weave_*.json, coyote_land.json (illegal, kept for reference).
- Dumps (scratchpad): wc_heavy*.txt, wc_runtick*.txt, wc_gc2_*.txt, wc_patternB.txt, wc_weave_*.txt, arcmap_*.txt, mscan_*.txt.
- Delivered TAS files (game folder): SOLVED_REAL_5.4375bm_runtick.json (valid, uncorrected 9.4875 pad), SOLVED_REAL_5.4375bm_prover.json (INVALID reference, violates the t36 land box; regenerated on request for inspection).
- Harnesses: core/src/test/.../anglesolver/ NixWeirdClose, NixArcMap, NixMomentumScan, NixZPlan, NixLadder, CoefDump.
