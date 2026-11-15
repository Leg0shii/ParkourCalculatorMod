# Route-specific optimistic continuation guidance

Latest user simplification: [GEOMETRIC_DECISION_GUIDANCE.md](GEOMETRIC_DECISION_GUIDANCE.md) uses the already available cell-route polyline to supply an untimed local direction/progress score. This is now the recommended first baseline. A full continuation trajectory or optimistic objective bound is not required before testing whether geometric guidance is adequate.

Goal clarification, 2026-10-31: [REFERENCE_STRATEGY_EVALUATION.md](REFERENCE_STRATEGY_EVALUATION.md) compares two ways to obtain the initial whole-route context: a genuine mixed-method solve with unresolved contact choices, and provisional complete drafts followed by local improvement. The latest recommendation prioritizes the latter baseline while testing whether mixed guidance adds value. The earlier focus on optimistic certificates below is useful background, not the primary acceptance criterion for a reference.

2026-10-31. User proposal after the cell-route prototype: keep all facings variable, but guide discrete decisions forward using an optimistic baseline for the selected spatial route. Compare a candidate interaction/tick choice against a simplified full-route continuation, or approximate its continuation value shortly after the interaction. This reopens a scoped bound/reference question; it does not select a global branch-and-bound engine. Ladders and explicit wall-hit experiments remain outside the active test scope.

This is a design formalization and primary-source review. No solver implementation or new numerical measurement accompanies this turn.

Subsequent user refinement: assume IPOPT is good enough and focus on the model-inclusion guarantee. [ROUTE_MIP_RELAXATION.md](ROUTE_MIP_RELAXATION.md) develops the mixed-integer/continuous-relaxation construction. Earlier numerical-certificate qualifications below remain background, not a request to reopen solver-quality discussion.

## What the user is proposing

The missing context in a local choice, such as passing a blockage on tick 4 versus tick 5, is its effect on the rest of the route. A route-specific optimistic continuation supplies that context. Decisions can be selected largely forward, while numerical optimization continues to adjust every earlier facing from the original initial state.

The baseline should expose three separate objects:

- an optimistic objective certificate, if available;
- a reference trajectory, including positions, velocities, route progress and movement-mode information;
- continuation-value information, or a cheap approximation to how later outcomes depend on an intermediate state.

A scalar upper bound alone supplies no travel direction. One reference trajectory is not a pointwise envelope that must be ahead of every real trajectory at every tick. The optimizer's value and the trajectory's geometric role must remain separate.

## Fix the objective and horizon first

For exposition maximize a signed final coordinate J at a fixed total input count T. Negate the configured coordinate for a MIN objective. Existing cross-route ranking remains minimum elapsed ticks, then the configured equal-time objective.

All comparisons below share T. If the objective instead minimizes elapsed time, the same optimistic relaxation gives a lower bound on time. A larger MAX-coordinate bound at a longer T does not establish a better solution under the existing lexicographic objective.

Let r denote a selected broad spatial route, D the discrete decisions already chosen, and a a proposed next decision. D contains interactions/timing choices, not fixed facing values or optimized intermediate X/Z arrivals.

Let F(r,D,T) contain all true trajectory/control completions consistent with that route and those decisions. Construct an outer model R(r,D,T) satisfying:

```text
F(r,D,T) subset R(r,D,T)
U(r,D,T) = sup { J(z) : z in R(r,D,T) }
```

A globally valid upper bound on this relaxed maximum also bounds every true completion in F. Keep the original state and route-defining broad passage conditions; relax unresolved local restrictions while preserving coverage of all their real possibilities.

A locally optimized relaxed trajectory is useful as a reference or seed. Its attained MAX objective is not automatically an upper bound on the real optimum. IPOPT is a local nonlinear method. A valid objective certificate requires a suitable global bound or, for an appropriate convex relaxation, a valid dual upper certificate. The mathematical bound, numerical best-found value and reference trajectory need separate fields.

## A simpler map must remain an outer model

Dropping avoidance inequalities for nuisance obstacles is a relaxation if all other modeled movement possibilities are preserved. Deleting a physical block and rerunning deterministic collision physics is not automatically a relaxation: that block could supply support, stepping or a ceiling interaction that enables a useful jump sequence.

Likewise, one fixed jump/landing/input-regime schedule bounds only that schedule. A bound over every uncommitted route decision needs an outer representation of their mode/dynamics alternatives. Relaxing fixed-magnitude horizontal acceleration to a disk can help, but it does not alone relax inertia thresholds, support changes or stepping correctly.

Keep route-defining passage side/order conditions broad enough to include allowed arrival timings. A prescribed portal center, a single crossing tick or a convenient Y schedule can exclude real choices and invalidate a route-wide bound. Where preserving these choices requires disjunctions, a convex surrogate must outer-approximate them or a global method must cover them. The formalism does not assume that this construction is free or automatically convex.

## Method 1: add the candidate to the optimistic full-route problem

For each candidate a, promote that decision and its required physical effects into the model. Leave later decisions optimistic. Use a consistent nested construction:

```text
F(r,D+a,T) subset R(r,D+a,T) subset R(r,D,T)
Q(a) = sup { J(z) : z in R(r,D+a,T) }
real_best(r,D+a,T) <= Q(a) <= U(r,D,T)
```

For a pure passage-timing choice, this may amount to adding the exact finite passage/order constraints for tick 4 or tick 5. For a landing, step or ceiling method, materialize its necessary dynamics/ground/input changes as well. Do not insert a position row into an incompatible old movement schedule and call that the decision's effect.

Optimize each conditional problem over the entire trajectory from the original initial state. Warm-start with the baseline, but do not fix its earlier facings, intermediate state or suffix controls.

Rank candidates by their conditional full-route value. An equivalent relative score is the optimistic opportunity loss:

```text
loss(a) = U(r,D,T) - Q(a)
```

A smaller loss retains more optimistic final performance. This is not the true regret of choosing a, and a larger Q(a) does not prove that a's true optimum beats a sibling's true optimum: their remaining relaxation gaps can differ. Best-found local values can rank proposals heuristically, but cannot silently become certified upper bounds.

After selecting a decision, update D and re-solve or update the baseline. The next interaction is judged with that decision's effect included. Retain close alternatives for recovery; an optimistic future need not remain feasible when all later real constraints are restored. This is forward decision selection with full-history control reoptimization, not a guarantee of zero backtracking.

## Method 2: shorten the solve and attach a continuation value

Let h be a common later position tick after all candidate decision times. For tick-4 versus tick-5 passage choices, h might be tick 8. Comparing each candidate three ticks after its own event compares different global times and needs an explicit remaining-time value correction.

For each candidate, optimize its prefix from the original state through h, respecting D and a. All earlier facings remain variables. Score the terminal state x_h with a route- and time-dependent approximation Vhat(r,D,h,x_h) to achievable final objective at T.

A geometric score based on baseline direction is a first approximation. Improve it by including both position and velocity:

```text
score(a) = max over compatible original-state prefixes:
           constant + g_position dot (p_h - p_reference_h)
                    + g_velocity dot (v_h - v_reference_h)
```

The weights should describe the continuation's value, not only the tangent of the geometric route. A candidate can be slightly ahead but already braking, while another is slightly behind with much more useful outgoing momentum. The remaining horizon, later turns and positional restrictions determine how valuable each velocity component is.

The intermediate state also needs any future-relevant ground/input/contact mode and route-progress obligations. Compare at a common continuation mode when possible, or use mode-conditioned continuation values. Position and velocity alone are not universally sufficient. Different h values can be compared using a time-dependent Vhat with the same final horizon, rather than uncorrected distance traveled.

A generic first-order approximation is a ranking heuristic. A convex relaxed suffix offers a more principled option: its dual variables can yield an affine upper model of continuation value. Even then, a local numerical maximization of the prefix score does not certify its maximum.

## Optional dual construction for the continuation value

For a linear relaxed suffix with an intermediate state x as parameter:

```text
V(x) = max c^T z
       subject to A z = b + B x
                  G z <= q + H x
```

Any dual-feasible nu, lambda with lambda >= 0 and A^T nu + G^T lambda = c gives:

```text
V(x) <= nu^T(b+B x) + lambda^T(q+H x)
     = constant + (B^T nu + H^T lambda)^T x
```

This produces position/velocity weights with a continuation interpretation. A dual solution at the reference can remain a valid upper affine function away from that point if dual feasibility and the suffix parameterization remain valid. It may be loose. A nonlinear/nonconvex local costate generally provides sensitivity guidance rather than that global guarantee.

## Proposed experiment sequence

1. Start with one selected route and a fixed movement-regime family, using blockage passage timing alternatives. This makes the bound scope explicit while testing the guidance mechanism.
2. Construct the route baseline and store its certificate status separately from its best-found trajectory/reference.
3. Compare full conditional continuation solves with short prefix solves plus direction-only and position-plus-velocity terminal scores, using the same final T and common comparison ticks.
4. Preserve original-state numerical reoptimization in every method. Never seed a frozen X/Z arrival into the rest of the route.
5. Measure decision-ranking agreement with later full physical solves, wrong early commitments/recovery, first accepted solution, total generation/solver work, and final time/objective. Include the validated multi-support control where blind prefix ordering currently fails.
6. Then extend to landing/step/ceiling choices with a verified outer model of their alternatives. Do not claim that a fixed-schedule bound covers those alternatives before this extension.

The user's proposed full conditional evaluation is the clean reference method. A short-horizon continuation score is the cheaper approximation to evaluate against it. A single ideal trajectory or a greedy coordinate-distance score is not sufficient to establish decision dominance.

## Primary-source context

- [Bertsekas, Ten Key Ideas for Reinforcement Learning and Optimal Control](https://www.mit.edu/~dimitrib/Slides_Extended_RL_Lecture.pdf), rollout/lookahead discussion around displayed slides 34-35: evaluate an action with an approximate continuation. This is the conceptual connection; the usual improvement result for a feasible base policy does not automatically transfer to a relaxed, possibly infeasible future.
- [Boyd and Vandenberghe, Duality](https://see.stanford.edu/materials/lsocoee364a/05Duality.pdf), sections on weak duality and perturbation/sensitivity: valid dual bounds and the interpretation of multipliers. The route/movement adaptation and the displayed suffix formulation above are our proposed application.
- [IPOPT documentation](https://www.coin-or.org/Ipopt/): the existing nonlinear backend provides local solutions; a returned nonconvex objective does not certify a global route-wide upper bound.
