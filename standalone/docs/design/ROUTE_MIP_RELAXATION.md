# A mixed-integer outer model for route guidance

2026-10-31. User direction: assume IPOPT is good enough for the present discussion and focus on the mathematical guarantee that the baseline model is a relaxation. The user connects this with the separate mixed-integer work. This note reviews the existing formulation research without contacting that thread. It extends [ROUTE_BASELINE_GUIDANCE.md](ROUTE_BASELINE_GUIDANCE.md); no runtime code or numerical experiments are added here.

Current test scope remains stepping, blockages, ceilings, run inputs and landing. Ladders and intentional wall-hit candidate families remain deferred.

Latest coordinated recommendation: [CONTINUOUS_DECISION_RELAXATION.md](CONTINUOUS_DECISION_RELAXATION.md) keeps a shared exact control circle and relaxes decision integrality first. Circle-to-disk convexification below is an optional comparison, not the default reference model. The user prioritized reference fidelity and direct IPOPT solve cost, and explicitly authorized this cross-thread design exchange.

## The guarantee is trajectory inclusion

Fix a spatial route r, final input count T, selected discrete decisions D and the signed final-coordinate objective J to maximize. Keep the original initial state and free facings throughout.

Construct a mixed-integer model M(r,D,T) with trajectory variables x and discrete choice variables z. The essential requirement is:

```text
For every real supported trajectory x consistent with r,D,T,
there exists an assignment of z and auxiliary variables
such that (x,z,aux) satisfies M(r,D,T), with the same J.
```

M need not be an exact simulator. It may already allow extra behavior. That can make constructing a useful bound easier than constructing an exact solver. It must not omit a real trajectory in the claimed scope.

Let R be the continuous model obtained by changing the remaining binary domains from {0,1} to [0,1] and applying any additional outer relaxations. Then, assuming optimization quality as requested:

```text
real optimum <= optimum of M <= optimum of R
```

This follows because every real path can be lifted to an integral solution, and every integral solution remains allowed after integrality is relaxed. The result is independent of whether fractional solutions describe realizable movement. For the existing lexicographic objective, this is an upper objective bound at fixed T; elapsed-time minimization has the opposite bound direction.

## What the mixed-integer choices represent

Continuous variables include position, velocity and horizontal control per input, plus the vertical/input-state variables needed when those histories are not fixed.

Discrete variables select:

- air/run/jump and their input/sprint context;
- support and landing, ceiling or stepping outcomes;
- contact/event occurrence and its input/state;
- blockage clearance faces, passage timing and route progress;
- inertia-dead-zone branches and other relevant piecewise movement regimes.

The route restricts the relevant geometric alternatives. Its unresolved portal/contact times remain choices. A whole source-to-goal trajectory is not split into independent fixed-arrival subproblems.

For a mode k, write its valid domain and movement equations as a constraint group C_k. A one-hot selector expresses:

```text
sum_k z[t,k] = 1
z[t,k] = 1 implies C_k(x[t],u[t],x[t+1],input_state)
```

These mode groups must include state/input transition rules. Precompiling one grounded/air schedule and later changing its selectors without changing the coefficients is not a representation of all schedules. Use the authoritative movement compiler/scalar/shared-scene semantics to derive each supported branch or its outer approximation.

## Free facings and continuous nonlinearities

With a fixed effective input/movement mode, the existing continuous model has:

```text
u = m_k * (cos(base_k + facing), sin(base_k + facing))
```

Because facing is unrestricted, replace it by a control vector satisfying ||u|| = m_k. Recover a candidate facing from atan2(u_z,u_x) minus base_k when materializing a real solution.

A mixed-integer model retaining these circle equalities can already be relaxed by dropping integrality. Its continuous model may remain nonconvex; that does not invalidate set inclusion under the stated optimization assumption.

For a simpler convex outer model, replace each control circle by its disk. A useful disaggregated construction is:

```text
u[t] = sum_k u[t,k]
||u[t,k]||_2 <= m_k * z[t,k]
z[t,k] >= 0
sum_k z[t,k] = 1
```

At integral z this permits any acceleration up to the selected magnitude, including the real full-magnitude acceleration. Fractional z allows optimistic mixtures. State/carry variables must be linked to the same mode selectors; this control constraint alone does not linearize switched dynamics.

Ordinary fixed-mode movement is affine. Products of binary selectors with bounded state/control variables can be handled by exact binary-product formulations or disaggregated mode variables. Their continuous hull/envelope formulations are outer relaxations. The inertia threshold must retain all three branches, not be removed by assuming inertia always survives. Closing strict branch boundaries can add possibilities safely; imposing an arbitrary extra exclusion margin can remove real ones.

For the current scope, a construction using affine outer mode rows plus norm-cone controls leads naturally to a mixed-integer conic outer model and a continuous SOCP relaxation. This is a modeling target, not an automatic property of the existing simulator. Nonlinear selection guards, such as the squared-horizontal-distance winner comparison in step assist, need to be retained or outer-relaxed. Dropping such a winner guard while retaining every possible real branch may be valid for a bound, although it permits nonphysical extra choices.

## Big-M versus a disaggregated hull

An implication A_k x <= b_k can be represented by:

```text
A_k x <= b_k + M_k * (1-z_k)
```

M must be large enough that an inactive alternative cannot exclude a real path. Compute it from valid state/control bounds for the declared horizon/route, not from the observed range of one reference trajectory. A too-small M invalidates coverage. Very large M can be valid but makes fractional selectors deactivate almost all useful geometry.

For the initial compact guidance model, prefer shared-state/common-heading complete-transition mixing; strengthen weak geometry/contact guards selectively with disaggregated hull/perspective rows. A full trajectory-wide hull is an alternative, not the default. They retain more of the relationship among mode selection, position, velocity and control, at the cost of additional variables. Hull reformulation does not by itself convexify a nonconvex branch; the circles and any other nonconvex rows still need their appropriate outer treatment.

For example, z_land=.5 and z_air=.5 should represent a constrained mixture of the two modeled transition sets, not a way to activate both modes' most favorable coefficients independently. Local hulls can still mix/unmix behavior across successive inputs, so even a valid root relaxation may be too optimistic to provide useful directional guidance. Coupled mode flow, contact-before/after logic and short multi-input disjunctions can tighten it without fixing the remaining integer decisions.

## Use the root relaxation and conditional relaxations as the baseline

Suppose the remaining passage-time alternatives are inputs/states 4 and 5. Represent their complete timing/geometry conditions with C_4 and C_5:

```text
z_4 + z_5 = 1
z_4 = 1 implies C_4
z_5 = 1 implies C_5
```

Include other timing or skip alternatives if they remain possible. The two-choice equality is valid only for that declared domain.

1. Relax future selectors to [0,1] and solve the route model. Store U(D) and the fractional reference trajectory/state history.
2. Fix z_4=1, enforce the corresponding decision group and solve with later choices still relaxed. This gives U(D,4).
3. Repeat with z_5=1 for U(D,5).
4. Rank their remaining final-objective potential; update the baseline after committing a decision and keep close alternatives for recovery.

All continuous variables, including those before the candidate event, remain variable. The real initial state stays fixed. For consistently nested models:

```text
real_best(D,a) <= U(D,a) <= U(D)
```

This supplies the user's proposed full-route context without enumerating every future discrete schedule. A higher upper bound is more optimistic potential, not a proof that the corresponding true solution is better than a sibling's true solution.

The cheaper position/velocity continuation-value score from ROUTE_BASELINE_GUIDANCE.md can use this relaxation's solution and sensitivities. A fractional reference is not a physical TAS and is not necessarily a pointwise envelope over real trajectories. Its usefulness depends on the relaxation retaining enough coupled route/mode information.

## What must be covered for this to bound all route decisions

- **All remaining movement alternatives:** a fixed Y/ground/contact schedule bounds only that schedule. A route-wide model must represent or outer-approximate the possible land/overfly/run-off/step/ceiling sequences.
- **Beneficial physical effects:** relax avoidance while retaining useful contact/reset/jump possibilities. Deleting the block and removing its dynamics can exclude a real completion.
- **Timing and occurrence domains:** a heuristic shortlist of existing generator outputs is not the full decision domain. Either prove omitted alternatives irrelevant, enlarge the domain, or explicitly limit the bound's scope.
- **Route coverage:** keep the full declared corridor/passage family, original state and actual goal region. Do not replace openings by fixed centers or allow at most one cell crossing per input. A real input can span several cells.
- **Valid domains:** physical state/control bounds, event indexing, simultaneous-effect compatibility and contact boundaries must not cut off real cases.

For an outer model, incomplete swept-collision checks are not automatically an invalidity: they may simply admit tunneling and weaken the bound. They are insufficient for physical acceptance, which still uses the shared scene checker. Conversely, imposing unclipped dynamics everywhere would exclude real landing/ceiling/step trajectories, so omitting the reset/contact branches is an actual coverage error.

## Relationship to the other mixed-integer work

The same formulation core can support two uses: integer assignments propose concrete decisions for real solving/validation; the continuous relaxation supplies this route-specific optimistic baseline. Conditional integer fixes provide decision lookahead without solving the whole integer problem first.

The key interface question is whether the available formulation selects timing and movement regimes itself or consumes a prescribed schedule. A prescribed-schedule circle/disk model is useful starting machinery, but its root relaxation cannot by itself bound unmodeled alternative schedules. No assumption about the other thread's current implementation status is made here, and no cross-thread contact was needed.

## Proposed verification and first implementation slice

Start with a fixed-regime blockage-timing family to validate the construction and conditional ranking. Then add variable landing/run/step/ceiling choices before calling it a bound over every route decision.

For each branch, show that the corresponding real transition has a feasible one-hot lift into its constraints. Show that every reformulation/outer approximation preserves that lift. As regressions, lift the saved validated witnesses, including the multi-support two-jump example, and require the relaxed feasible set to contain them with the same objective. Compare tiny fully enumerated decision families as a second check. Numerical witness tests catch omissions; they supplement rather than replace the branch-inclusion argument.

This turn selects no implementation backend, installs no dependencies and makes no numerical speed/bound-tightness claim. The immediate open work is the branch/mode coverage specification and relaxation strength, taking solver quality as given.

## Primary-source context

- [Pyomo GDP reformulations](https://pyomo.readthedocs.io/en/stable/explanation/modeling/gdp/solving.html): Big-M versus hull, bounded variables, and the distinction between a reformulation and convexity.
- [Castro and Grossmann, Generalized Disjunctive Programming as a Systematic Modeling Framework to Derive Scheduling Formulations](https://egon.cheme.cmu.edu/Papers/CastroGrossmannIndEngChemRes2012.pdf): disjunctive scheduling formulations and hull/Big-M strength. The Minecraft mode adaptation here is our proposed application.
- [Gurobi constraint reference](https://docs.gurobi.com/projects/optimizer/en/current/concepts/modeling/constraints.html): indicator semantics and second-order cone/quadratic constraint forms. No library-specific implementation is selected.
