# j154 objective-bounded B&B: M-A prototype is a rigorous NO-GO; the blocker is under-constraint, not the bound (2026-08-10 night)

Continuation of `j154-global-optimizer-handoff-2026-08-10.md`. This session executed milestone **M-A** (the go/no-go prototype the handoff mandated before building the B&B tree) and the answer is a firm, quantitatively-backed **NO-GO**. Per the handoff's own instruction ("If NO-GO, STOP and report, do not build the tree") the tree was not built. This doc records what was built, what was measured, the mechanism, and why the conclusion redirects to the grammar prior. Everything in worktree `.claude/worktrees/coldsearch`, branch `worktree/coldsearch`, uncommitted; 7 pins + full slowTests still green.

## 0. TL;DR

- **The objective-bounded B&B does not work for j154 (or j716).** The LP-relaxation objective lower bound was built (`ColdBound`) and measured against the human oracle at every prefix depth, both wall-free and wall-aware (fed by ArcSweep's real form propagation). It is sound and monotone but far too loose to prune: at half the ticks fixed it excludes only ~30-34% of the objective range on the hard targets; the floor gap never closes.
- **The mechanism is decisive and matches the handoff's own section-5 diagnosis.** The objective (landing X) is a *real-arithmetic* quantity. It is (a) floored by the shared landing box (`X >= -1600.137`, which is BELOW the human optimum `-1599.700`, so every node's LB sits below any feasible incumbent and prunes nothing), and (b) realized in the *free-yaw tail* (11 independent air yaws) which the relaxation can freely aim to satisfy `X>=@34` then dive to the box floor. So the objective LB is essentially the same constant for every momentum key-pattern; it neither prunes (LB < incumbent always) nor guides (LBs undifferentiated). Even the *full* LP with all tail constraints has this property, because the free tail yaws are more numerous than the tail constraints.
- **The unified root cause, now measured: j154's momentum is under-constrained.** The momentum checkpoints (ticks 14, 27) are the *same 1.6-block box as the start*, so partial-trajectory feasibility barely prunes. Measured directly: the per-cycle family enumeration at glideMax=12 passed **2.58M of 3.43M cycle-1 partials (75%)** through the box-threading feasibility filter. The full 3-cycle cross-product is ~12 billion and cannot even be enumerated.
- **Why nothing real-arithmetic can win:** the only signals that separate the byte-exact knife edge (probe, certify, objective) need a *complete* sig, so they cannot prune the ~12B cross-product during construction; and the only per-partial signal (box feasibility) has a 75% pass rate. This is a structural impossibility for all bound/feasibility/enumeration approaches, not a tuning problem.
- **Conclusion / recommendation:** j154 cold requires a strong **structural / grammar prior** that pre-restricts the per-cycle family alphabet to a small likely set BEFORE enumeration, so the cross-product is small enough to complete-then-probe-then-certify. This is exactly the user's standing ruling ("grammar prior REQUIRED, general") and section-10's conclusion, now with quantitative proof of necessity. The build needs the corpus family frequencies mapped onto the coldsearch coast/glide/press representation.

## 1. What was built (all in `core/.../anglesolver/coldsearch/`)

- `ColdBound.java` (main): the LP-relaxation landing-X lower bound primitive.
  - Landing X = `startX + sum_k L_k * accelX_k(theta)`, with fixed friction gains `L_k = 1 + f4[k]*L_{k+1}` (whole-trajectory, tail included). Momentum ticks `[0,last)` share the facing theta; the free tail `[last,nT)` has independent facings.
  - `lowerBoundX(key, sprint, d, loRad, hiRad)`: wall-free bound. Fixed prefix as one `A*sin+B*cos` form (exact `formMin` over the arc), free momentum ticks relaxed to the min over the 9-combo hull (sprint/walk/boost variants) over the arc, free tail as the theta-independent max-magnitude retreat. Sound (each term minimized independently over the arc), decoupled-loose.
  - `lowerBoundXFromState(lowerX, dxs, dxc, vxs, vxc, tick, arcs)`: wall-aware bound. Same objective but fed ArcSweep's wall-constrained start-X lower forms + accumulated displacement/velocity forms + narrowed arc, so start X is pinned from below by the tightest wall form (removing the rect-width slack) and the arc is already wall-cut. This is the bound the B&B tree would evaluate at an internal node.
  - `formMin`/`formMax`/`minFormOverArcs`: exact min of `s*sin+c*cos` over an interval / arc set (endpoints + interior trough).
  - `forwardTrace(...)`: diagnostic per-tick forward at a fixed facing with/without the inertia gate (used to localize divergence).
  - `gateMargin()`: threshold * sum-of-gains, the provable soundness margin for ignoring the inertia gate.
- `ColdBoundProbeScreen.java` (test, PKC_COLD_BOUND_FILES): wall-free go/no-go. Derives human keys/theta/objective (validation only), checks true-lower-bound + tightening + fraction-excluded at prefix depths and shrinking arcs.
- `ColdBoundWallScreen.java` (test, PKC_COLD_BOUND_FILES): wall-aware go/no-go using `ArcSweep.walkStatesPerTick`.
- `ColdCycleBeamScreen` gained `PKC_COLD_BEAM_LOG` (live file logging) and was used with glideMax=12 to measure the family-enumeration explosion.

## 2. Measurements (all validation-only; human lines never fed a solver)

### Wall-free bound (`ColdBoundProbeScreen`), frac = fraction of objective range excluded

| capture | rootLB span | frac @ half-depth | floor gap (all fixed, 1-bucket arc) | verdict |
| --- | --- | --- | --- | --- |
| j154 (target) | 13.75 | 0.344 | 3.10 | NO-GO |
| j716 (target) | 14.25 | 0.489 | 2.00 | NO-GO |
| j1150 (sane)  | 14.49 | 0.707 | 4.08 | GO |
| j925 | (no `result` block, skipped) | | | |

Sound (LB <= humanX everywhere) and monotone everywhere. j1150 passes; the two hard targets fail. The floor gap is dominated by `startX=rectXLo` slack (rect is 1.6 wide) + free-tail retreat.

### Wall-aware bound (`ColdBoundWallScreen`, ArcSweep forms)

| capture | frac @ half-depth | note |
| --- | --- | --- |
| j154 | 0.300 | arc stays 55-83 deg wide; floor gap 5.4 with the natural (wall-cut) arc |
| j716 | 0.331 | same |
| j1150 | 0.512 | marginal |

Pinning start X from the walls barely helped, because j154's momentum checkpoints (14, 27) are the *same 1.6-block box as start* (handoff already noted "loose ~1.6 blocks"), so they do not pin. The binding constraints (`X>=@30,33,34`, clearance 1e-9 at 34) live in the free-yaw tail, which the momentum-node bound treats as free retreat.

### Objective cannot prune or guide

Human landing X (objective) = **-1599.7000695**; landing box = `X[-1600.137, -1599.700]`. Every momentum-node LB measured is **-1605 to -1613** (5-13 below the human). A feasible incumbent at -1599.700 prunes a node only if its LB >= -1599.700; no node qualifies. And the LBs are dominated by the free-tail constant, nearly identical across momentum patterns, so best-first ordering does not distinguish the certifiable pattern either.

### Feasibility barely prunes (measured)

`ColdCycleBeamScreen` on j154 at glideMax=12 (enough to express j154's 11-tick WA glide; its exact sig IS in the family set): cycle-0 kept 921/921; cycle-1 kept **2,579,521 survivors from 3,432,417 extensions (75%)** before the 120s budget cut it off mid-cycle. Full cross-product ~12e9. Box-threading feasibility is toothless (loose boxes).

## 3. Diagnostics worth keeping

- The per-tick divergence trace (`ColdBoundProbeScreen`) proved the linearized-form vs byte-exact-model gap (~0.03-0.13 block on j154/j716/j1150) is NOT the inertia gate (gate-on == gate-off at every tick) but the **air-sprint 1-tick lag**: air accel uses `factorSprintAt` (previous-tick sprint), not the current tick. `ColdBound`'s exact-trace helpers use the current tick and so diverge; ArcSweep's `stepInto` already uses the correct lag (`airSprint = k!=0 && st.sprintPrev`), so any bound fed by ArcSweep forms is unaffected. (Left as a noted caveat in `ColdBound`'s exact-trace helpers; it does not affect the *bound* soundness, which uses the larger sprint accel as a worst case.)

## 4. Why this is a structural impossibility, not a tuning gap

1. Partial-trajectory feasibility (the only signal available *during* enumeration) passes 75% of structured partials -> no pruning.
2. The discriminating signals (byte-exact certify; the recoverStart `probe` that is finite only in certifiable basins; the objective) all require a *complete* momentum sig -> they cannot prune the cross-product as it is built.
3. The cross-product is ~12e9 -> it cannot be completed, so (2) can never be applied to all of it.
4. Therefore the enumeration must be pre-restricted to a small likely family set BEFORE it is built. That restriction is a PRIOR.

The objective was proposed (handoff section 6) as the separator that section 5 said real-arithmetic bounds lack. But the objective is itself real-arithmetic and, being floored by the shared landing box and realized in the free-yaw tail, inherits exactly section-5's limitation. So section 6's premise does not hold for j154.

## 5. The grammar prior is necessary BUT the corpus does not contain j154's tech

The only lever that can beat the impossibility in section 4 is a prior that pre-restricts the per-cycle family alphabet. But the corpus-frequency prior CANNOT do it for j154, and this was checked:

- The parsed corpus (`strat_parsed.json`, 6163 worded strats from the prior session's scratchpad) has families **{hold 3608, jam 3485, run 2683, pessi 1904, walk 1649, mark 627, c45 557, fmm 285, bwmm 230, mm 98}** - all FORWARD-momentum techs.
- **"butterfly" appears 0 times** in the parsed strats AND 0 times in the raw `hpk_strat_index.json`; "neo"/"sidestep" likewise absent. j154 is "Head **Butterfly** Neo": its momentum tech (alternating held-facing sidesteps: `A / SD-glide / S / press / A / WA-glide / W`) is **not represented in the corpus at all**.
- `StratPlans` (the existing family grammar) is likewise forward/single-press-centric and does not express it.

So a corpus-derived prior would rank j154's pattern as ~impossible. To solve j154 cold, the **butterfly / neo tech must be hand-encoded** as a general family generator (from the MC parkour wiki or the user's domain knowledge: the exact held-facing sidestep key sequence and how it parametrizes), added to the coldsearch grammar, so the (now tech-restricted, small) per-cycle cross-product is completable and certifiable. That encoding needs the user's input; it is not derivable from the constraints, the corpus, or (by the rules) the capture's own line.

Recommended build once the tech is defined:
- Per cycle, generate a SMALL set of butterfly/neo family variants (parametrized by glide length + side), NOT the full 4701-family coast/glide/press grid.
- Cross-product the small per-cycle sets (completable), probe (recoverStart) each complete sig, certify in probe order (105 ms each), stop at first solve. One general code path; gate on j154 AND j716.

Alternative framing worth a product decision: exotic momentum-tech jumps (butterfly neo, winged neo) may be intended to be solved by RECOGNIZING the tech from an encoded library, not by cold search. The certify already turns "recognized key-pattern" into a byte-exact TAS in 105 ms; the missing half is the pattern library, i.e. the prior.

## 6. How to reproduce

- Bounds: `PKC_COLD_BOUND_FILES=<j154,j716,j925,j1150 csv>` then `:core:test --tests "*ColdBoundProbeScreen"` and `--tests "*ColdBoundWallScreen"` (fast, not slow-tagged). Read stdout from the TEST-*.xml `<system-out>`.
- Feasibility explosion: `PKC_COLD_BEAM_FILE=<j154> PKC_COLD_BEAM_GLIDE=12 PKC_COLD_BEAM_CAP=5000000 PKC_COLD_BEAM_CERTCAP=0 PKC_COLD_BEAM_BUDGET_MS=120000` then `:core:test --tests "*ColdCycleBeamScreen"`.
- Pins: `.\gradlew --configure-on-demand :core:test --tests "*ColdSearchRegressionTest" "-PslowTests" "-PtestHeap=3g" --rerun`.
