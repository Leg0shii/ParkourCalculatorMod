# Convex route guidance: scope and first experiment

Current baseline recommendation: test [geometric decision guidance](GEOMETRIC_DECISION_GUIDANCE.md) directly from the existing cell route before investing in another initializer. Mixed dynamics remain a possible later source of additional guidance; they are not required to obtain a rough route direction.

Current evaluation, 2026-10-31: [REFERENCE_STRATEGY_EVALUATION.md](REFERENCE_STRATEGY_EVALUATION.md) restores the primary goal of useful initial whole-route context. Recommendation: implement provisional drafts and iterative decision improvement as the next baseline, and compare a genuine mixed-method initializer on dynamic-contact cases. Convexity and upper bounds are supporting properties; the existing fixed-profile SOCP experiment does not establish that a mixed ceiling/stepping baseline works.

Dynamic-contact clarification, 2026-10-31: [DYNAMIC_CONTACT_DECISIONS.md](DYNAMIC_CONTACT_DECISIONS.md) describes the next substantive model extension: optional ceiling/stepping transitions over shared 3D state, with exact input/ground timing and explicit outer treatment of nonconvex step-winner guards. Passage-timing measurements do not establish this capability.

Latest user direction, 2026-10-31: omit only inertia thresholds for decision selection, carry velocity normally, and defer inertia-pattern refinement. [NO_THRESHOLD_DECISION_MODEL.md](NO_THRESHOLD_DECISION_MODEL.md) supersedes the carry-hull recommendation below for the next model. Existing benchmark results still describe the earlier hull implementation.

Implementation update, 2026-10-31: the restricted experiment is implemented in `route_guidance/` and measured. [Results and recommendations](experiments/route-guidance/README.md) supersede the prospective status below. Clarabel supplies the genuine SOCP backend; all three arms have witness-lift checks. Prefer convex conditional values with retained alternatives. The current helper defaults to width two after a measured greedy timing counterexample. General variable-Y stepping/ceiling coverage and integration with the cell-route opportunity generator remain unfinished. The original experiment specification follows as design history.

2026-10-31. The user welcomes disk controls if they buy convexity and expects reduced acceleration to be rarely advantageous. This supersedes the earlier default of trying the circle model first. Both agents confirmed the distinction between relaxing acceleration and convexifying the entire decision model. This is a design specification, not an implemented solver or a new benchmark.

## Status and recommendation

Try a genuinely convex disk formulation alongside the compact continuous model. The overall route-guidance strategy is settled: fix a spatial family and horizon, solve an optimistic continuation, compare conditional local decisions, commit a promising decision, and reoptimize every facing from the original initial state. Keep close alternatives available for recovery.

The first restricted experiment is specified below. The complete implementation plan for arbitrary stepping, ceiling and landing histories is not finished. A solver backend and the complete branch/domain specification must be settled before claiming that broader implementation is ready.

## Existing mod machinery

Verified in `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/`:

- `DiskSocpKernel.solve` solves a disk-control second-order cone problem with fixed control radii, linear walls and a linear objective. Results include primal controls, multipliers, a dual value and gap/convergence information.
- `RelaxationRecovery.relaxedPrimal` projects controls into per-tick disks. The surrounding recovery constructs facing seeds, refines with SLP and checks physical feasibility. This is an existing example of optimistic controls guiding full-strength facing recovery.
- `JumpLinearModel` supplies affine horizontal couplings for a supplied movement/friction history and, when specified, inertia-zeroing pattern. It does not jointly choose arbitrary movement histories.
- `CostateDualSolver` uses the support function `m*norm(g)`, shared by the circle and disk. A nonzero costate favors full-strength aligned acceleration. This explains why the relaxation can often be tight, but does not prove circle/disk equivalence under additional walls. Zero costates, constrained controls and fractional contacts can produce useful interior controls.

Reuse the fixed-history kernel as a sanity oracle and recovery reference. Its fixed arrays/linear-wall interface does not directly express variable mode masses, perspective cones or state flow. No native Java code is being ported or changed in this planning turn.

## Why changing the circle alone is insufficient

The compact model multiplies fractional mode selectors by unknown carry/state/control values. Replacing `norm(q)=1` with `norm(q)<=1` leaves those products in place. It remains a generally nonconvex NLP.

For a convex alternative, give each mode a weighted copy of the incoming state, carry, acceleration and outgoing state. For modes k at one tick, use the schematic formulation:

```text
z_k >= 0, sum_k z_k = 1
sum_k x_k = x, sum_k x_next_k = x_next
L_k*z_k <= x_k <= U_k*z_k
norm(a_k) <= m_k*z_k
x_next_k = A_k*x_k + B_k*a_k + b_k*z_k
G_k*x_k + H_k*a_k + J_k*x_next_k <= h_k*z_k
```

Carry can be included in the state/auxiliary vector. Its bounded graph hull has a linear perspective representation using vertex weights summing to `z_k`. Add valid bounds for all local state/carry/output copies. Every matrix and radius above is fixed for the selected local branch. Route guards, contact outcomes and event/phase flow must also have affine or conic outer representations. Then a linear objective, affine rows and these norm cones form an SOCP. A branch that retains nonlinear state products prevents this claim.

Use time-expanded event/phase flow: nodes identify supported phases and ticks; arcs identify allowed transitions. Propagate both selection mass and weighted state through each node, so incoming copies sum to outgoing copies. Fix the initial mass/state to the original player state. Several time steps may occupy the same cell. Artificial cell boundaries do not create physical contacts.

This relaxation permits fractional branches to have different states and acceleration directions. Requiring their directions to equal one shared unknown heading would restore nonconvex products. Independent fractional directions can cancel, and splitting/recombining momentum can create extra optimism. This is the price to measure, beyond reduced acceleration alone. At a real one-hot trajectory, only its selected mode is active and its real state/control provides the required lift.

Valid domains must contain every real trajectory in the declared family. Do not derive them from one incumbent/reference, exclude inertia threshold bands, add positive avoidance clearance, or silently treat a capped event family as exhaustive.

## Restricted first experiment

Use a supplied support sequence, ordinary jumps with their authoritative phase tables, normal/run departure choices, and horizontal avoidance/passage choices. Vary departure/pass ticks jointly, retain all relevant inertia regimes through an outer graph hull, and leave effective headings free. This bounds only that declared support/movement family. It does not yet bound every possible contact history in the broader cell corridor.

For each fixed horizon T, maximize the same signed terminal coordinate from the same initial state. Compare horizons separately to preserve elapsed-ticks-first ordering. For a partial commitment D and candidate a, solve the same model with the corresponding event selectors fixed. Future choices remain relaxed. Candidate fixing must shrink the parent feasible set without changing its domains or inventing an unrelated Y schedule.

Use three arms with matched route/horizon/decision families:

| Arm | Controls and coupling | Purpose |
| --- | --- | --- |
| A | Compact shared effective heading, circle | Fidelity/reference control |
| B | Same compact model, disk | Isolate reduced acceleration; still generally nonconvex |
| C | Disaggregated states and disk perspective cones | Test full convexity, including its additional relaxation and variable count |

Only A versus B isolates the control-set change. Under the accepted solve-quality premise, `real_best <= U_A <= U_B`. A real-path lift also gives `real_best <= U_C`; ordering B versus C needs a nesting proof and must not be assumed.

Validation before performance conclusions:

1. Lift real witnesses, including threshold/boundary cases, into each supported formulation. Compare individual one-hot mode transitions with the physical model. An outer model's integral solutions need not all be physical.
2. Exhaust tiny declared timing/passage families and check every relaxed value covers their best accepted objective. Check conditional-fix nesting and empty families separately.
3. Compare fixed-history disk cases against the native kernel or equivalent coefficient-level oracle. Verify state-flow conservation, zero-mass copies and scaled-cone/guard boundaries.
4. Measure build/solve time, model size, relaxed-to-physical objective gap, refinement success, time-aligned and route-aligned trajectory differences, and the ranking of conditional decisions. Record reduced-control usage separately from fractional mode/state splitting.
5. Compare end-to-end accepted results and numerical work with the existing route-local generator. Faster relaxed solves alone do not establish better local decision search.

Choose a formulation from measured ranking/refinement quality and end-to-end cost. No supported numerical threshold or speed prediction exists yet. If C is loose, strengthen state-flow/geometry coupling or use its value with A's reference; do not automatically restore a shared-heading equation and still call C convex. Values and reference trajectories can come from different models only if their roles are recorded explicitly.

## Remaining implementation decisions

- Backend: the inspected standalone environment has CasADi/IPOPT, but no Clarabel, CVXPY, SCS or SciPy. Specify either a conic backend or an IPOPT encoding of the cones and its zero-mass handling before implementation. A convex feasible set does not guarantee a generic squared-cone NLP encoding has well-conditioned derivatives at zero mass. No dependency was installed.
- Complete ordinary-movement event/phase and sprint-state domains, closed guards, conservative state bounds, and a reproducible fixed-history/native oracle adapter remain to be written down as executable model inputs.
- Covering alternate support sequences within one broad route needs an optional-contact/time-expanded support graph or another explicit outer union. The first supplied support sequence is deliberately narrower.
- General variable-Y landing, stepping and ceiling outcomes need branch definitions that couple vertical/ground changes to horizontal clipping, input state and timing. Ordinary-jump templates alone do not cover arbitrary incoming Y/VY. Any non-affine branch needs a valid convex outer representation before inclusion in C.
- Reconstruct and physically refine fractional solutions while preserving the selected spatial family. This recovery is required to evaluate decision quality; the optimistic path itself is not an accepted TAS.

The active target remains stepping, blockages, ceilings, run ticks and landing. Ladders and intentional wall hits remain excluded. No runtime code, dependencies or experiment results changed in this turn.

## Sources and coordination

Primary repository sources are the four native classes above. Both agents independently checked their scope and agreed on the disaggregated convex formulation and three-arm comparison. The mixed-integer worktree was read-only throughout.

The [CVXPY SOCP example](https://www.cvxpy.org/examples/basic/socp.html) gives the norm-cone/affine-row problem form. The [Pyomo GDP reformulation documentation](https://pyomo.readthedocs.io/en/stable/explanation/modeling/gdp/solving.html) explains bounded hull reformulations and why nonconvex branch constraints do not become convex merely by disaggregation. These sources support the mathematical distinctions, not a performance claim for this project.
