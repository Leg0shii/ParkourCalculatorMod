# Stage B shard: agent B06 (envelope tail + optimality-gap map)

TERRITORY: where the shipped solver leaves objective on the table (headroom vs COPT true) and where it
blows the time envelope. Bridges to Stage E as the benchmark baseline.

FILES / COMMANDS actually run (this session, all measured, no gradle):
- Read: `00-context-pack.md`, `stage0-copt/FINDINGS.md`, `stageB/orchestrator-timings.md`,
  `research/copt/{README.md,run_h1h2.py}`, `EngineFileScreen.java`, `StructureDump.java`,
  `problems/solve/*.expect.json`, `problems/dualrecovery/*.expect.json`.
- Engine (direct `java -cp "$(cat core/build/test-classpath.txt)"`, JDK 25, one fresh JVM per capture,
  serial, no concurrent load): `EngineFileScreen` with `PKC_SOLVE_EFFORT={FAST,THOROUGH}`,
  `PKC_OPTIMIZE_SECONDS=10`. Byte-exact obj is the engine's own `ExactJumpModel` value (obj is the
  achieved position; met=n/n confirms byte-exact feasible at viol<=1e-7).
- COPT oracle (`research/copt`, `COPT_LICENSE_DIR` set): `StructureDump` (env `PKC_STRUCT_FILE/OUT`)
  then `python run_h1h2.py`. Generated 7 NEW hpk references (`solve_qcqp_sphere`, COPT spatial B&B,
  NonConvex=2, gap ~0): j345, j144, j757, j828, j718, j716, j155. Faithfulness gate passed on each
  (model-vs-recorded drift 2e-5 to 2e-2 b, the known half-angle norm-excess, not model error).
- MEASURED CAVEAT (FINDINGS s4) applies: COPT continuous is a near-exact reference, NOT a strict
  byte-exact upper bound; byte-exact over-reaches it via sine-LUT half-angle norm>1.

---

## B06-1: THE STAGE E BASELINE TABLE (every prototype competes against this)

TITLE: Per-capture shipped FAST/THOROUGH byte-exact obj vs COPT true, with headroom and wall-clock.
LOCATION: `EngineFileScreen` + `research/copt/run_h1h2.py`; obj = achieved position at objTick.
CLAIM: This is the measured baseline; headroom = signed gap of THOROUGH to COPT true (POS = real
headroom, shipped below COPT; NEG = byte-exact over-reach above COPT continuous).
EVIDENCE (all measured this session, obj-sense-aware; MIN captures j022/j716 headroom = THOR-COPT):

| capture | n | class | FAST obj | THOROUGH obj | COPT true | headroom THORvsCOPT | FAST ms | THOR ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| j005 | 9 | 1j | -41.298292 | -41.291516 | -41.294900 | -3.38e-3 OVER | 155 | 7599 |
| j016 | 11 | 2jmm | -4.857992 | -4.856110 | -4.857772 | -1.66e-3 OVER | 178 | 7464 |
| j019 | 11 | 3jmm nix | -13.303542 | -13.292335 | -13.302783 | -1.04e-2 OVER | 190 | 7679 |
| j022 | 11 | 1j MIN | -531.700124 | -531.700150 | -531.700200 | +5.0e-5 (matched) | 210 | 7619 |
| j008b | 25 | 2j coupled | -0.215326 | -0.215314 | -0.196938 | +1.838e-2 HEADROOM | 262 | 7640 |
| j021 | 39 | 4j coupled | 1067.684771 | 1067.862279 | 1067.863880 | +1.60e-3 HEADROOM | 472 | 7643 |
| j345 | 49 | 3jmm nix | -660.199361 | -660.156564 | -660.176285 | -1.97e-2 OVER | 709 | 7607 |
| j144 | 42 | triple | 987.701072 | 987.705157 | 987.705611 | +4.54e-4 HEADROOM | 968* | 7649 |
| j757 | 18 | 1bmhh | 1098.116135 | 1098.116241 | 1098.116250 | +9e-6 (matched) | 139 | 7634 |
| j828 | 39 | 1bm | 4978.013121 | 4978.013123 | 4978.013120 | -3e-6 (matched) | 578 | 7671 |
| j718 | 37 | pane-pane neo | -1902.861854 | -1902.861557 | -1902.861400 | +1.57e-4 HEADROOM | 257 | 7646 |
| j716 | 42 | winged neo MIN | -699.950168 | -699.950225 | -699.950366 | +1.41e-4 HEADROOM | 851* | 7619 |
| j155 | 66 | 4jmm | 4984.763274 | 4984.763296 | 4984.763300 | +4e-6 (matched) | 505 | 7684 |
| loopmm-lands | 33 | 3j gate | -279.354398 | -279.300490 | (gate: see B06-9) | +7.63e-4 vs human | 365 | 7642 |

`*` j144 FAST measured 932 and 1004 ms on two runs (median ~968); j716 measured 879 and 823 (median
~851). FAST ms carries ~1.5x run-to-run variance on this machine; the orchestrator's isolated numbers
for the 6 reference captures (j005 115, j016 110, j019 117, j022 137, j008b 202, j021 268; THOROUGH
9087-9126 at its 12s budget) are the low-variance reference. FAST obj is DETERMINISTIC and reproduces
the orchestrator table to the digit. THOROUGH obj has anytime noise ~1e-3 (my j021 1067.862279 vs
orchestrator 1067.862397); reproduces FINDINGS within that.
IMPACT: correctness/robustness reference for all of Stage E; magnitude = the whole gap map.
PROPOSAL: Stage E prototypes must beat, per capture, THOROUGH obj at <7.6s AND close the HEADROOM rows
without regressing the matched/OVER rows. The three HEADROOM rows that matter: j008b (1.84e-2), j021
(1.60e-3), j144 (4.5e-4); plus loopmm vs human (B06-9).
CONFIDENCE: 0.9. DEPENDS-ON: B06-2, B06-3.

## B06-2: 7 new hpk COPT true optima generated (all position-only, valid references)

TITLE: hpk d10/d11 captures are pure position + fixed start, so COPT is a valid ground-truth reference.
LOCATION: `research/copt/data/{struct,h1h2}-{j345,j144,j757,j828,j718,j716,j155}.json`.
CLAIM: All 7 dump `facingWall=false`, `free=false`; COPT spatial B&B solves each to gap ~0 in <12 s.
EVIDENCE (StructureDump summary + run_h1h2, measured): all `hasFacingWall=false`, `startFree=false`.
COPT QCQP-sphere: j345 -660.176285 (11.3s, gap 7.4e-5), j144 987.705611 (0.28s, gap 0), j757
1098.11625 (0.10s, gap 0), j828 4978.01312 (0.08s, gap 3e-7), j718 -1902.8614 (0.34s, gap 6.5e-5),
j716 -699.950366 (0.29s, gap 3e-5), j155 4984.7633 (0.04s, gap 0). H1/H2 on the coupled ones: SDP
rank>1 (eig2/eig1 up to 2.1e-3 on j144, disk throttles 2-18 low-authority ticks by up to 1.7e-2),
consistent with FINDINGS s1 on j008b/j021. Only j345 needed 11.3 s of B&B (rank-1, eig2/eig1 4.2e-5,
but 18 throttled ticks and a redirect at t24).
IMPACT: robustness; extends the COPT reference set from 6 to 13 captures for Stage E.
PROPOSAL: reuse these 7 references directly; no dF/gate modeling needed for them.
CONFIDENCE: 0.95. DEPENDS-ON: none.

## B06-3: Byte-exact OVER-REACH is the dominant "gap" on single/nix jumps; j345 is the largest measured

TITLE: On flat/45-strafe and nix jumps the shipped THOROUGH byte-exact obj EXCEEDS the COPT continuous
optimum; this is norm-excess, not headroom.
LOCATION: sine-LUT `McSineTable` half-angle norm>1 (FINDINGS s4); ticks amplified by air friction.
CLAIM: 4 of 13 rows are OVER (shipped above COPT): j005 +3.38e-3, j016 +1.66e-3, j019 +1.04e-2, and
NEW j345 +1.97e-2 b, the largest over-reach measured so far (bigger than j019).
EVIDENCE: measured this session. j345 (3jmm true nix, 49 ticks, many 45-strafe air ticks): THOROUGH
-660.156564 > COPT -660.176285 AND > the recorded human path -660.158721; the faithfulness diff
model-vs-recorded was 2.12e-2 b, all half-angle accumulation over the nix air ticks. j019 (also
truenix) reproduces at +1.04e-2. The magnitude scales with count of friction-amplified 45-strafe air
ticks, so nix/momentum multi-jumps show the biggest over-reach.
IMPACT: correctness of the benchmark method; magnitude up to 2e-2 b on nix.
PROPOSAL: Stage E MUST byte-exact-round-trip any COPT/continuous solution through `ExactJumpModel`
before scoring; treat COPT as a near-exact reference, not an upper bound. On nix captures the target to
beat is the byte-exact THOROUGH obj, NOT the COPT number.
CONFIDENCE: 0.9. DEPENDS-ON: B06-2.

## B06-4: j008b THOROUGH CANNOT close its headroom (THOROUGH == FAST, both 1.84e-2 b short of COPT)

TITLE: On j008b the full 7.6s THOROUGH (B&B + ILS) gains only 1.2e-5 b over FAST first-feasible and
stays 1.84e-2 b below the COPT-proven optimum.
LOCATION: THOROUGH chain `receding horizon -> branch and bound -> ILS`; COPT `solve_qcqp_sphere`.
CLAIM: The shipped optimizer plateaus in a basin 1.84e-2 b worse than global; more time does not help.
EVIDENCE: measured. FAST -0.215326 (262 ms), THOROUGH -0.215314 (7640 ms), COPT true -0.196938. FAST
to THOROUGH improvement = 1.2e-5 b (noise). COPT gap for j008b sphere was 7.7e-5 (near-global); its
disk is loose 1.5e-3 b and SDP rank>1 (FINDINGS s1), so the true optimum sits on a degenerate face the
closed-form recovery and the ILS both miss. The 1.84e-2 b gap dwarfs the ~few-e-3 half-angle
over-reach, so it is genuine unreached headroom (byte-exact reachability of exactly -0.196938 is
unconfirmed, but a large fraction is certainly reachable).
IMPACT: robustness; j008b is the single worst headroom row and the clearest "shipped optimizer stuck in
a wrong basin" case. Magnitude 1.84e-2 b.
PROPOSAL: PRIME Stage E target. A global method (spatial B&B replica) or a basin-hopping/multistart ILS
seeded off the COPT/disk solution should recover it. Benchmark any prototype on j008b FIRST.
CONFIDENCE: 0.85. DEPENDS-ON: B06-1.

## B06-5: FAST misleads badly on coupled multi-jump; THOROUGH rescues j021 (0.179 -> 1.6e-3) but the
gain is capture-specific

TITLE: FAST first-feasible obj can be 0.179 b short of optimum on coupled multi-jump; THOROUGH closes
most of it on j021 and the hpk multi-jumps, but not j008b.
LOCATION: FAST = first-feasible receding horizon; THOROUGH = anytime B&B/ILS.
CLAIM: FAST is unsafe to show as "the answer" on coupled multi-jump; the FAST->THOROUGH gain is large
and uneven.
EVIDENCE (measured, FAST->THOROUGH obj gain toward optimum, and residual THOROUGH gap to COPT):
- j021: FAST 1067.684771 (0.179 b short) -> THOROUGH 1067.862279 (1.60e-3 short). THOROUGH recovers
  99.1% of the FAST gap.
- j345: FAST -660.199361 -> THOROUGH -660.156564, gain 4.28e-2 b (then over-reaches COPT, B06-3).
- j144: FAST 987.701072 -> THOROUGH 987.705157, gain 4.09e-3 b (residual 4.5e-4 to COPT).
- j008b: FAST -0.215326 -> THOROUGH -0.215314, gain 1.2e-5 b ONLY (residual 1.84e-2, B06-4).
- j718: FAST -1902.861854 -> THOROUGH -1902.861557, gain 3.0e-4 b (residual 1.6e-4).
- j716: FAST -699.950168 -> THOROUGH -699.950225, gain 5.7e-5 b (residual 1.4e-4).
IMPACT: robustness + UX; FAST obj gap up to 0.179 b (j021) is player-visible (a jump reads as missing).
PROPOSAL: for coupled multi-jump (jumps>=2) either escalate FAST past first-feasible with a short
bounded improve, or surface a "FAST is first-feasible; run THOROUGH for the real reach" hint. Stage E
should report BOTH FAST and THOROUGH obj so the multi-jump gap is visible.
CONFIDENCE: 0.9. DEPENDS-ON: B06-1, B06-4.

## B06-6: THE TIME-ENVELOPE TAIL: two ~30-40 s FAST cases, not one

TITLE: Beyond nix-full-t1, loopmm-tight-t39 is a second ~33-38 s FAST case; both blow the 800 ms
envelope by ~40x.
LOCATION: FAST graph escalation into explore-tier `pattern B&B -> ILS`; gate/infeasibility driven.
CLAIM: The FAST tail has TWO members at n~33-40 s, distinct in cause.
EVIDENCE (measured):
- nix-full-t1: FAST 39975 ms (orchestrator authoritative), met 8/15, `shouldSolve=false`, ACCEPTED-FAIL
  (no feasible ever found; receding horizon exhausts). Genuinely infeasible-hard multi-jump; burns the
  full budget then fails.
- loopmm-tight-t39: FAST 33436 ms and 38144 ms on two runs; met 6/6 SUCCEEDS. First-feasible (7/7)
  reached only at 23297 ms, via `pattern B&B -> ILS (explore)`. Under FAST the inertia gate defeats
  receding-horizon and closed-form, so the graph escalates all the way to the pattern-B&B explore node
  just to find ANY feasible. GENUINELY HARD (gate feasibility), not accepted-fail.
IMPACT: speed; two captures at ~40x the 800 ms envelope. Magnitude: 33-40 s.
PROPOSAL: cap/curfew the FAST explore escalation (it is not "fast"); route gate-hard captures to a
dedicated gate solver (MISOCP, Stage E s5) instead of blind pattern B&B. loopmm-tight-t39 is the gate
benchmark; nix-full-t1 is the infeasible-hard benchmark.
CONFIDENCE: 0.9. DEPENDS-ON: none.

## B06-7: FAST >800 ms mid-tail: RH-first-pass-fails-then-relaxation-recovery, plus large-n

TITLE: A cluster of captures sit at ~0.8-2.7 s FAST from either a failed receding-horizon first pass
falling through to relaxation recovery, or large tick count.
LOCATION: FAST chains showing `receding horizon -> closed form -> relaxation recovery`.
CLAIM: Six captures exceed 800 ms at FAST for two identifiable reasons.
EVIDENCE (measured FAST ms, this session, fresh JVM):
- LARGE-N: j001 (353 ticks, 30 jumps, 81 cons) 2687 ms; taser-100t (100 ticks) 1180 ms. Pure size; RH
  stays first-feasible, no escalation.
- RH-FALLTHROUGH: j144 ~968 ms, j716 ~851 ms, nix-t25-setup-tick 981 ms all show `receding horizon ->
  closed form -> relaxation recovery`; the RH window pass does not produce feasible, so it pays for the
  closed-form + relaxation-recovery cascade. j021 (472 ms) and j008b (262 ms) stay under via RH alone.
- UNDER 800 ms and fine: df-chain-free-start 252 ms, synth-free-translate 141 ms, gh283-j990-cold
  435 ms (free-start cold all cheap).
IMPACT: speed; 0.8-2.7 s, ~1-3x over envelope. Not catastrophic but the RH-fallthrough is avoidable.
PROPOSAL: profile why RH first-feasible fails on j144/j716/nix-t25 (coupled neo / setup tick); a cheaper
feasibility repair than full relaxation recovery would pull these back under 800 ms. Large-n (j001,
taser) is inherent and acceptable.
CONFIDENCE: 0.85. DEPENDS-ON: none.

## B06-8: THOROUGH burns the full budget even when FAST already reached the COPT optimum

TITLE: On captures where FAST is already at/within noise of COPT true, THOROUGH still spends the whole
7.6 s budget for a sub-1e-5 b change.
LOCATION: anytime budget loop; no optimality-gap early-exit.
CLAIM: THOROUGH is a fixed-budget anytime optimizer with no bound-matched early exit, wasting ~7.6 s on
easy captures.
EVIDENCE (measured): j757 FAST 1098.116135 (139 ms) already within 1.2e-4 of COPT 1098.11625; THOROUGH
1098.116241 in 7634 ms (gain 1.1e-4). j828 FAST already matched COPT (4978.013121, 578 ms); THOROUGH
7671 ms for +2e-6. j155 FAST matched (505 ms); THOROUGH 7684 ms for +2.2e-5. j005 FAST already past
COPT continuous (over-reach); THOROUGH 7599 ms. In all four THOROUGH burns ~7.6 s for a change below
the certify floor. The engine computes `ClosedFormSolve.dualBound` (tight on single/easy per FINDINGS
s2, within ~1e-6 b) but does not use it to stop.
IMPACT: speed; ~7 s reclaimable on every easy/single capture (a large fraction of the corpus).
PROPOSAL: add an optimality-gap early-exit: when the byte-exact achieved obj is within a small
tolerance of the converged (COPT-grade) convex bound, stop. Reliable where the bound is tight
(single/easy); leave the loose-bound coupled cases running. Stage E lever.
CONFIDENCE: 0.85. DEPENDS-ON: B06-1.

## B06-9: loopmm gate: THOROUGH is 7.6e-4 b short of the HUMAN path; COPT clamp-free is not a reference

TITLE: On loopmm-3jump-lands the shipped THOROUGH underperforms even the recorded human landing, and
the COPT model cannot serve as ground truth because it drops the inertia gate.
LOCATION: inertia gate as a free brake (FINDINGS s5); `JumpLinearModel` clamp-free constructor.
CLAIM: loopmm headroom must be measured against the human path, not COPT; the solver leaves 7.6e-4 b.
EVIDENCE (measured): sense MAX Z. Recorded human landing -279.299727. FAST -279.354398 (5.47e-2 short
of human), THOROUGH -279.300490 (7.63e-4 short of human). COPT clamp-free QCQP -279.299065 is ABOVE the
human path but is a relaxation that ignores the gate brake loopmm actually uses, so it is not an
achievable reference (FINDINGS s5 explicitly warns). THOROUGH improves FAST by 5.39e-2 b but still does
not reach the human landing, consistent with the solver not exploiting the gate as a brake.
IMPACT: robustness; the gate class is where both FAST and the COPT reference are weakest.
PROPOSAL: Stage E must model the gate (MISOCP big-M indicators, FINDINGS s5) to get a true reference AND
to let the solver exploit the brake; benchmark against the human path meanwhile. loopmm-tight-t39
(B06-6) is the hard end of this same class.
CONFIDENCE: 0.8. DEPENDS-ON: B06-6.

## B06-10: Summary reads for Stage E

TITLE: What the gap map says in one paragraph.
CLAIM (all measured above): (1) On 6 of 13 captures FAST is already at the COPT optimum and THOROUGH
just burns 7.6 s (B06-8). (2) Real unreached headroom lives on exactly the coupled/degenerate cases:
j008b 1.84e-2 (THOROUGH stuck, B06-4), j021 1.60e-3, j144 4.5e-4, loopmm 7.6e-4 vs human (B06-9); these
are the Stage E scoreboard. (3) On single/nix jumps the "gap" is byte-exact OVER-reach up to 2.0e-2 b
(j345), so COPT is a reference not a bound and byte-exact round-trip is mandatory (B06-3). (4) The time
tail is two ~35 s FAST cases (nix-full-t1 accepted-fail, loopmm-tight-t39 gate-hard) plus a 0.8-2.7 s
mid-tail from RH-fallthrough and large-n (B06-6, B06-7). THOROUGH is a flat 7.6 s by budget design, not
a tail.
IMPACT: orients Stage E on the 3 headroom rows + 2 tail rows that actually matter.
CONFIDENCE: 0.9. DEPENDS-ON: B06-1 through B06-9.
