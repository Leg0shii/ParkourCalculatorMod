# ARCH-2 M2a: the certified arc + gate branch-and-bound (issue #422)

Date 2026-08-27. The M2a deliverable of the #422 M2 batch: a certified spatial branch-and-bound over
per-tick facing arcs and inertia-gate states, returning byte-exact incumbents with a weak-duality bound
and a running certified gap. Components: `solver/SineTableGeometry` (era-aware, table-exact node
geometry), `solver/CertifiedBnb` (the search), `graph/nodes/CertifiedBnbNode` (wiring). This document
is the mechanism and soundness record; measured results are appended at the end.

## Problem and variable space

Fixed inputs, find facings. The physics consumes one float game facing gf_t per tick
(ExactJumpModel casts the solver yaw to float per tick; the row realization accumulates float deltas,
and every sine bucket is reachable by that accumulation, so bounding over all float gf per tick is a
relaxation of the realizable set). The B&B therefore brackets gf_t directly. Scope: the certificate
covers |gf| <= 720 degrees (the wrap-legality band; the plus-minus-1 cos-index slop argument holds
there). Multi-turn wrapped solutions beyond that stay WrapWindowIls territory. Captures with
non-NORMAL surfaces (fluid, ladder, web, soulsand) and dF-chain specs decline; the graph falls
through to the existing nodes.

## Node relaxation and the bound

A node fixes, per tick, an optional gf bracket [lo, hi] and, per (axis, tick), a gate state in
{FREE, ZERO, OPEN+, OPEN-} (legacy per-axis; the modern combined gate uses
{FREE, ZERO, 4 sector states, OPEN-any}). The bound solve is DiskSocpKernel.solveChords on:

- the spec walls compiled at margin 0 (incumbent-side machinery such as anchor margins, wallTighten,
  clearance, specWallRelax never appears in a bound solve),
- zero-consistency rows |v_model_t| <= thr + D_t and keep-alive rows |v_model_t| >= thr - D_t for the
  fixed and interval-resolved gates (built from JumpLinearModel.keepAliveWall on the node's
  pattern-aware model, whose zNext cuts encode the fixed zeros),
- per-tick component box rows and one chord row per bracketed tick, from the exact table scan below,
- the per-tick cone radius set to the exact maximum realizable input norm.

The emitted value is then post-evaluated with exact supports: for the kernel's final multipliers
lambda (a feasible dual point at any iterate, weak duality), the bound is
sum_t max_{u in U_t} g_t . u + sum_j lambda_j b_j + exact start-box support + gate slack, where U_t
is the exact set of realizable inputs for the node bracket and g the emitted costates. The kernel's
smoothed free-start support term is replaced by the exact box support (the smoothed form understates
and is only used to shape the iterates). On kernel failure the CostateDualSolver value on the same
wall rows post-evaluates the same way. FAIL_UNBOUNDED certifies node infeasibility.

## Table-exact geometry (SineTableGeometry)

The realized input at tick t is an accel-scaled base vector rotated by TABLE sin/cos values whose
pair sits off the unit circle (legacy pair-norm error up to ~4.8e-5, 26.x ~4e-8), with the cos index
offset by 16384 plus a rounding slip of at most one index (verified slop, |gf| <= 720), and legacy
jump-tick boosts running a second float rad cast with its own index chain. SineTableGeometry
replicates the model's input arithmetic bit-exactly per candidate index (legacy float expressions,
modern double pipeline) and derives every containment quantity by direct scan:

- narrow brackets (up to 40000 indices): exact component boxes, exact minimum chord projection onto
  the mid direction, and exact supports, all over the index range widened by SLOP_IDX = 3 and the
  cos slop d in {-1, 0, +1}; jump ticks scan the fly and boost parts separately and sum (an
  independent relaxation of the coupled pair),
- full circle: supports via a window scan of 512 indices around the query direction, sound because
  rMin cos((SLOP+2) delta) > rMax cos((512-SLOP) delta) for the measured per-era norm extremes (the
  inequality is asserted at construction; delta is one index angle),
- brackets wider than the scan cap fall back to the full-circle geometry (looser, still sound).

Because every scanned point is evaluated with the model's own float arithmetic, no float-slop
inflation is needed on the input side; narrow brackets converge to the exact lattice hull and the
bound floor vanishes at depth.

## Gate soundness

Clamp-free dynamics are not an upper bound of the gated dynamics (measured on thousand: zeroing helps
MAX X), so FREE gates are never silently dropped. Per node, interval propagation pushes the exact
initial velocity plus per-tick input boxes through the gated recursion; a velocity interval strictly
single-cased against the threshold band resolves its gate without branching (the surviving H1 role).
Unresolved FREE gates keep clamp-free propagation in the model and pay a correction slack: the
difference between the model and any node trajectory obeys D_{t+1} = f4 D_t with an injection
I_t = thr + D_t at each FREE gate (a zeroing removes a carry below thr), so every wall row j is
relaxed by sum over FREE gates of I_t |coef_j[t]| plus a flat 1e-10 rearrangement allowance, and the
objective by the same sum against |c_t|. Fixed ZERO resets D to zero (both sides zero the carry).
The modern keep-alive side (||v|| >= thr, nonconvex) branches as ZERO plus four per-axis sector
halfplanes at thr/sqrt(2), a cover of the keep-alive region since max(|vx|, |vz|) >= thr/sqrt(2)
whenever ||v|| >= thr.

## Search

Best-first on the bound (ties by node id, fully deterministic). Branch selection: FREE gates that are
band-critical in the node's decoded replay (0.25x..4x thr, or a sign flip: the GateMip band factors)
or carry the largest slack impact branch 3-way (5-way modern); otherwise the arc of the tick with the
largest of throttle slack (radius minus realized modulus, the interior-modulus signal), costate
degeneracy (|g| below 1e-5 of the maximum, the ResidualRescue detector), and bracket width, bisecting
at the midpoint (the first split centers a half-circle on the decoded direction). Incumbents:
round-and-simulate the node's decoded solution through ExactJumpModel (replay is the only
certificate), then FoldReplayDriver.polishFromAnchor under a polish budget; infeasible bests feed
ClosestMiss. Leaves (no FREE gates, pinned start, every input tick under 64 index spans) close via a
wall-aware lattice DFS: per-tick candidate cells from FacingLattice with exact per-cell inputs, the
node's linear rows enforced with suffix-min pruning, the argmax realized through ulp-corrected float
accumulation and replayed; the node closes on min(kernel bound, lattice bound). The pattern-fixed
linear model is float-friction exact (the JumpLinearModel floatFriction flag), so the lattice bound
matches replays to double-rounding scale. Free-start nodes never leaf-enumerate (the translation is
continuous); they close by bound.

Termination: gap = best open bound minus incumbent; CERT_EPS = 1e-9 blocks is the declared certified
gap tolerance, absorbing the double-rounding rearrangement between the float-friction linear model
and the sequential byte replay (each bound row additionally carries a flat 1e-10 allowance). FIRST_FEASIBLE mode exits on the first feasible
incumbent under a fixed node cap; OPTIMIZE runs to the deadline at node granularity and always
returns the best incumbent plus the running gap (the G4 contract), published via GraphContext
dual gap and CERT trace events.

## Wiring

NodeCatalog id `certBnb`. Final M2a placement (orchestrator ruling): the M1 recovery order is
fully preserved on both tiers, and the certified FIRST_FEASIBLE nodes live in the true
return-nothing lane only: FAST ladderArm NONE -> certFF (node cap 32) -> cap2 on found, emit on
miss; THOROUGH ladderArm NONE -> certFFcold likewise. certOpt (OPTIMIZE) stays between foldImprove
and the exhaustive continuation as the G4 gap provider. The feasible paths never touch the
certified FF nodes, so the solvable-corpus FAST cost is zero (a headroom-gated improve arm and a
rescue-miss placement were built, measured, and removed; findings 5 and 7).
BnbNode/GateMip/BoundPrunedRecovery stay wired until M2b deletes them; at M2b the certified node
takes the primary slot, with loopmm-tight-t39 at its expect budgets a named must-hold for that
cutover (orchestrator ruling; the 2.85 s corpus row is a knife-edge, see finding 7, and rescueSec
stays at 3).

## Declared judgment calls

1. CERT_EPS = 1e-9 with the rearrangement caveat above.
2. The certificate scope: |gf| <= 720, NORMAL surfaces, no dF chains, position walls only (DXZ/DZX
   rows are dropped from the relaxation, which only loosens the bound).
3. The FAST headroom arm was removed on measurement (finding 5); the epsilon-extended headroom
   predicate remains as the M2b knob.
4. The small-capture engine gate asserts all five pool captures reach a certified gap at or under
   the measured ceiling 1.5e-4 with valid dominating bounds; CERT_EPS certification is asserted on
   the synthetic instance only (finding 3 records the real-capture plateau and its mechanism).
5. Leaf enumeration cap 20000 combos; sector cover m = 4.
6. FAST determinism relies on the node caps as the primary termination (the wall-clock budget is a
   safety net that rarely binds on the wired caps), the same acceptance the existing BnbNode budget
   carries; the engine-level FAST re-solve guard is the tripwire.

## Measured findings (2026-08-27; do not rediscover)

1. Model-fidelity discovery: JumpLinearModel computes friction as double (slip * 0.91) while the
   game runs float (slipF * 0.91F), a 2.6e-8 relative discrepancy per tick that surfaces as a
   ~4e-9 objective error per 40 coordinate units on a 9-tick chain. The driver's anchor margins
   absorb it on the incumbent side; a certified bound cannot. Fixed by the additive
   JumpLinearModel float-friction constructor flag, used only by CertifiedBnb (all other callers
   unchanged; the fast suite is bit-identical).
2. Search discipline: FIFO tie-breaking on flat bound frontiers degenerates best-first into
   breadth-first (measured: depth 9 after 3000 nodes); LIFO ties plus fold-first gate branching
   with the replay-preferred child pushed last restores diving (gap 0.69 to 8.7e-3 in 4000 nodes
   on j005). Arc-first branching without gate fixing stalls (uniform ~0.72 gaps): every fixed gate
   removes its slack term, which is the dominant bound reduction, exactly the H1 fold-first
   prescription.
3. Certification results (60 s OPTIMIZE, node caps off): j022 gap 3.2e-7, j005 1.51e-5,
   j004 2.73e-5, j008-bfneo 3.9e-5, j006 7.5e-5; all five hold byte-exact feasible incumbents that
   improve on their closed-form seeds, all bounds dominate. The gaps PLATEAU (unchanged from 60 s
   to 240 s): the floor is leaf-level, where the wall-feasible lattice bound over boundary-cast
   cells exceeds what the incumbent machinery realizes through the float delta accumulation.
   CERT_EPS = 1e-9 is reached only on the synthetic 2-tick instance (fast unit test); real-capture
   certified gaps live at 3e-7..1e-4. The blocking mechanism for the last mile is boundary-cell
   reachability (which exact float cells the accumulation can land on, prefix-dependent), the same
   phenomenon behind the legacy plus-minus-50-bucket leaf windows.
4. Cross-basin reach targets: thousand 6523.30772 (TH10), j1150-noturn-inner FAST at the v1.9.0
   bar -2805.298946354, and j154-noturn-ja-inner FAST at the v1.10.0 bar -1599.700435371 do NOT
   fall. Mechanism, uniform: multi-jump and free-start captures carry ~2(n-1) root-ambiguous gates
   whose correction slack is 15..57 blocks (measured root-minus-sphere margins below), so
   basin-discriminating bounds require deep gate fixing far beyond FAST/THOROUGH stage budgets at
   n = 39..176. Within budgets the certified stage still improves locally (j1150 FAST +2.3e-5 with
   the since-removed headroom arm; thousand TH10 6523.307714525, +1.5e-6 over M1; j003 TH10
   -31.299999990, slightly better than M1, in-deadline).
5. FAST headroom arm, measured and REMOVED: wired as cap2 -> HAS_REACH_HEADROOM(2.5e-3) ->
   certBnb OPTIMIZE (24 nodes, 2 s), it fired on 6 of 8 sampled corpus captures at 0.3..3 s each
   and delivered no reach bar. Against the v1.10.0 FAST floor (median 55 ms) that cost is
   disqualifying, so the arm is unwired; certFF (fold-miss rescue) and certOpt (THOROUGH) remain.
   The epsilon-extended HAS_REACH_HEADROOM router predicate stays (additive, default-compatible)
   as the M2b tuning knob if the floor budget ever affords a bounded improve arm.
6. j003 kernel cost (n = 176): 51 ms per node-relaxation solve (27 nodes in the certOpt window,
   1389 ms kernel). k-gon verdict: DO NOT BUILD. Node throughput is not the wall; the root gate
   slack (57 blocks on j003) is, and no node-speed factor closes it.
7. Budget discipline, measured and fixed: certFF ahead of the proven rescue cost 0.3..1.4 s on
   fold-miss captures and, worse, resolved-open keep-alive rows (one per interval-resolved open
   gate, ~2(n-1) rows) cubed the kernel cost per node and hung loopmm-tight-t39 FAST past the 30 s
   harness cap (FAST has no overall engine deadline). Fixes: keep-alive rows only for branch-FIXED
   open gates (dropping resolved-open rows only loosens the relaxation, sound), deadline checks
   inside the leaf DFS (every 4096 steps) and the argmax sweep, and the ordering ruling: rescue
   first, certified node on the rescue's miss (both tiers) until M2b promotes it. Post-fix
   measurements: certFF on loopmm-tight-t39 is bounded at 623 ms / 64 nodes; warm-path captures sit
   at or under the M1 cost (taser-100t 2139 vs 2270 ms); the residual add is 0.2..1.0 s on
   rescue-miss captures only (bfsetup2 844, j024 655 vs 191, j346 3046 vs 2045 ms), the inherent
   price of the certified relaxation in the miss lane. The loopmm-tight-t39 corpus timeout itself
   is NOT an M2a regression: an A/B on the M1 commit (06f794f6, fresh worktree, same machine)
   shows the identical primary rescueBnb miss at its 3 s budget (907 to 3313 ms) with the explore
   arm solving at 26.1 s, so the M1 TSV's 2.85 s row was a knife-edge run; the capture solves at
   the expect config (90 s budget) on the current tree in 32.6 s and its keep-alive expects stay
   green. The ladder-miss placement was subsequently ruled in and applied on both tiers, removing
   the j346/j024/bfsetup2-class residual from every solvable capture.
8. COPT root-bound validation (research/copt/certbnb_rootcheck.py on certbnb-roots.tsv,
   sphere QCQP, modulus residual checked): 8 of 8 comparable captures OK, 0 failures; root bound
   minus sphere optimum = the designed free-gate slack (j005 0.72, j016 0.92, j019 0.89, j022 0.83,
   j021 9.6, j008b 6.0, loopmm 7.6, j003 57.2). j001 and thousand skip (COPT sphere returned no
   solution inside the time limit; thousand's free start is not encoded in the sphere model).

## Measurements table pointers

- Root bounds: research/copt/data/certbnb-roots.tsv (CertBnbProbe, nodeCap=1).
- Certification probes: CertBnbProbe with PKC_CERTBNB_NODES/MS; per-capture numbers above.
