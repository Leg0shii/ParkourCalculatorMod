# Experimental route guidance

Latest design decision, 2026-10-31: the user wants input-decision search to omit inertia thresholds while retaining all ordinary momentum carry. [The revised model note](../NO_THRESHOLD_DECISION_MODEL.md) explains this direction. The source and measurements documented below still use the earlier carry hull; removing it and rebenchmarking are the next implementation changes. Actual inertia-pattern refinement and tolerance calibration are deferred.

This module implements the first restricted experiment in [CONVEX_ROUTE_GUIDANCE_PLAN.md](../CONVEX_ROUTE_GUIDANCE_PLAN.md). It compares a compact circle NLP, an otherwise identical compact disk NLP, and a disaggregated disk SOCP. It does not replace production route generation.

The input is a supplied support sequence, a fixed final horizon, and optional horizontal passage choices. Ordinary jump profiles come from the existing vertical model. Movement coefficients come from the existing shared Java compiler. A time-expanded phase graph represents run/departure timing without materializing complete histories. Inputs keep W/sprint or the existing Force 45 policy with freely optimized facings. Each solve retains the full original horizontal position and velocity.

Current coverage is ordinary jumps, run timing, landing support and two-face horizontal passages on this declared sequence. Alternate support sequences, arbitrary stepping/ceiling outcomes, ladders and intentional wall hits are outside this implementation. Their omission cannot support a bound over a larger route family.

## Run

From the isolated worktree, with its Java compiler already prepared:

```sh
python3.12 -m venv .venv-guidance
.venv-guidance/bin/python -m pip install -r standalone/autonomy/route_guidance/requirements.txt
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.route_guidance.experiment \
  --input standalone/autonomy/experiments/route-guidance/fixtures \
  --compiler standalone/autonomy/build/compiler.json \
  --output standalone/autonomy/build/route-guidance.json \
  --seconds 3 --refine-seconds 3 --repeats 3
```

The conic backend is Clarabel, using its direct sparse interface. The two compact models use CasADi/IPOPT. Dependencies live in a separate ignored environment. The native mod disk solver is a modeling reference and is not imported or modified.

`build_family` accepts `route`, `ticks`, optional `avoidances`, absolute position `walls`, a `name` and an optional `collisionScene`. The route uses the existing `sceneModel=open_air_supports` format. Each avoidance has an ID, leg, two signed X/Z faces and integer gate-offset choices. The existing Y-X-Z order determines whether a change of face requires clearance on the same state or consecutive states. Supplied collision solids are checked during physical acceptance; omission of their clearance rows from a relaxation is optimistic, not a feasibility assertion. Without a collision scene, rectangular support volumes are generated.

Indices are zero-based: state t is the position before input t, and T inputs produce states 0 through T. Departure decisions identify input indices. Passage gate choices identify position-state offsets relative to that leg's departure. Several inputs can occupy the same spatial region.

`guidance.rank_candidates` fixes one real departure/passage choice and reoptimizes the remaining relaxed trajectory. `guide_route` defaults to retaining two promising partial assignments and physically refines the retained complete choices. The beam width is an explicit heuristic cap. Unknown branches and heuristic drops are recorded; their absence from the beam is not a proof of infeasibility or dominance. The benchmark explicitly requests width one for its greedy control.

## The bound argument

For this declared horizon/family, take any real continuous-model trajectory obeying an allowed allocation and passage selection.

1. Its timing follows one path through the phase graph. Set those edge masses and its passage choices to one and all alternatives to zero.
2. Each active mode uses the authoritative magnitude/friction/base coefficients. Its free facing supplies a unit effective acceleration direction. Both compact control sets contain it; the conic perspective disk contains its acceleration vector.
3. Inertia carry is a point in the bounded closed graph hull, including exact threshold survivors. Its convex vertex weights provide the auxiliary lift.
4. The domain recurrence bounds each component of velocity by the previous magnitude plus the maximum available acceleration, times friction, and bounds position by accumulated displacement. It uses no incumbent endpoints. Those finite domains contain the real path.
5. The real state/control fills the active conic edge copies. Zero copies fill inactive edges; weighted state conservation holds at each phase node. Every active support/passage guard holds. Valid bounded Big-M rows deactivate the unused passage alternatives.
6. Relaxing masses/selectors and enlarging circle controls to disks preserves that assignment. Fixing a candidate only removes assignments inconsistent with it and leaves state domains unchanged.

Therefore the optimum of each relaxed MAX model is at least the best real objective in its declared family. MIN coordinates are negated internally, so all reported `value` and `upperBound` fields refer to the normalized MAX objective. Different horizons are kept separate to preserve elapsed-ticks-first ordering.

The conic model also reports a numerical dual upper value. For minimization `q*x`, project multipliers into the dual cones, form `r=q+A.T*z`, and use finite variable bounds to obtain:

```text
max(-q*x) <= b*z - sum_j min(r_j*lower_j, r_j*upper_j)
```

This corrects bounded stationarity residuals rather than silently treating a primal objective as an upper certificate. A small roundoff cushion is added. This is floating-point numerical evidence for the continuous model, not an interval-arithmetic or byte-exact game certificate. Compact NLP outputs retain their model-inclusion guarantee under the user's accepted solve-quality premise; they are not mislabeled as computed dual certificates.

One-hot witness audits evaluate the actual assembled constraints of all three implementations. A separate analytic fixed-history disk oracle, conditional nesting tests and compiler-equivalence tests check important parts of the construction. Tests are evidence against implementation mistakes; the general inclusion argument above establishes why the supported model is outer.

## What the comparison measures

The benchmark exhausts each small declared discrete family, with whole-trajectory physical IPOPT refinement from multiple seeds. Every accepted witness passes scalar physics, strict support overlap, vertical-profile validation and the shared Java scene collision check. Numerical unknowns remain unknown. The benchmark records their conditional upper values so they cannot silently become infeasibility claims.

For each arm, it records root build/solve time and size, objective gap to the best accepted enumerated trajectory, time-aligned position/velocity differences, arc-length-aligned shape differences, control slack, fractional flow, conditional ranking and physical refinement. Greedy and width-two guidance are compared. Their physical calls include the actual numerical starts. Full source, dependency versions and compiler-config digest accompany the saved output.

Circle versus compact disk isolates the control-set enlargement. Comparing either with the SOCP also changes state/mode coupling. Their relative tightness must be measured; it is not justified by the circle/disk inclusion alone. A good relaxed objective need not identify a unique nearby reference trajectory, and upper-bound ordering alone does not prove an ordering of feasible alternatives.

[Measured results and recommendations](../experiments/route-guidance/README.md) include repeated comparisons, independent replay and an inertia ablation. The latter fixes the inertia pattern observed in an accepted witness while keeping its facings free. `build_conic(..., carry_pattern=...)` explicitly marks that restricted model. It bounds only the specified pattern and must not replace the inclusive carry hull for a route-wide bound.

Focused verification:

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m unittest standalone.autonomy.tests.test_route_guidance -v
```
