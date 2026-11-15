# Generic mixed-integer methods for the standalone solver

2026-10-31. Research prompted by the user's observation that discrete obstacle/contact/schedule choices activate continuous constraints. Canonical implementation: `feature/ipopt-consolidated`, PR #1. This note evaluates alternatives; no production backend or pruning policy was changed. Existing spatial-route research can coexist with a generic mixed-integer local solver or relaxation.

## Finding

The problem naturally fits generalized disjunctive programming (GDP): logical movement/interaction alternatives select constraint systems over continuous trajectories. A mixed-integer nonlinear program (MINLP) is one algebraic representation. With free facing, a useful reformulation may reduce much of the current supported fixed-horizon continuous model to a nonconvex mixed-integer quadratically constrained program (MIQCP). This reformulation is derived below; a complete encoding of every supported case has not been built or verified.

A generic backend would branch over partial integer assignments and propagate/relax their constraints rather than first materializing every complete schedule. This is a substantive alternative to the current eager schedule pools. The number of alternatives and the strength of their formulation still matter. A library cannot infer movement semantics from the existing Java compiler or scalar checker.

## What the variables would mean

- Continuous state per input/state: X/Z position, velocity, control vector and, where not already determined, Y/VY.
- Discrete choices: support/landing occurrence, movement phase, jump/run/run-off, contact face and input, blockage gate timing, ladder occupancy/climbing/stops/Sneak, inertia branch and clamp branch.
- Logic: compatible simultaneous contacts, allowed movement transitions, entry/exit conditions, bounded total duration and per-block blockage caps.
- Selected alternatives activate constraints, for example `hit_x[t] => x[t+1]=face_plane` and `vx[t+1]=0`, with approach, finite-face and input/sprint implications. Non-hit alternatives retain normal recurrence and require appropriate clearance.

Obstacles are fixed geometry; a binary variable selects interaction or clearance conditions, not whether the obstacle exists. A point outside a box at tick endpoints does not establish swept Y-X-Z collision clearance. Multiple contacts/avoids can coexist, physical blocks can recur, and successive visits need distinct event/state occurrences.

The objective stays lexicographic: minimize elapsed ticks, then optimize the configured positional objective at the chosen duration. A prototype can fix the horizon, test shorter durations, then optimize within a chosen duration. Failure of a bounded local solve is not a proof that a shorter duration is infeasible. A bounded-horizon model can alternatively introduce an explicitly gated active prefix and terminal selector; inactive padding is not simulated movement.

## The useful free-facing reformulation

`ipopt/worker.py` currently forms a horizontal control vector for each tick from compiled constants `magnitude[t]` and `base[t]` and the free yaw:

```text
u[t] = m[t] * (cos(base[t] + yaw[t]), sin(base[t] + yaw[t]))
```

With no facing constraints, this can be represented by continuous `ux[t], uz[t]` and:

```text
ux[t]^2 + uz[t]^2 = m[t]^2
yaw[t] = atan2(uz[t], ux[t]) - base[t]
```

The second equation is for recovering a candidate facing after solving, not a constraint needed in the optimization. Zero-magnitude inputs can carry an arbitrary/legal facing. This is a modeling derivation for the current continuous model, not exact Minecraft sine-table arithmetic. If facing restrictions return later, their relationships need to be retained explicitly.

For a selected finite movement mode, ordinary dynamics are affine:

```text
movement = inertia_selected_carry + u
p_next = p + movement
v_next = friction * movement
```

Wall clipping, component caps and the inertia dead zone replace these rows with different affine regimes. Mode-dependent acceleration/friction and sprint/input carry need explicit finite-state transition logic or precompiled local coefficient tables. They cannot be obtained by compiling once and changing binary schedules afterward. The current `FixedMovementCompiler` must remain the source of the coefficient semantics.

The circle equality is nonconvex. It avoids trigonometric expressions but does not turn the problem into MILP or convex MISOCP. If magnitude is one of finitely many mode constants, one possible selected-mode circle row has RHS `sum(mode[j] * m[j]^2)` with a valid one-hot mode selection. Full Y/step/contact compatibility and finite collision-order constraints still need a verified formulation.

Replacing equality by `ux^2 + uz^2 <= m^2` gives an optimistic disk relaxation. It can help bound/rank branches and supply seeds to the existing solver. It is not an accepted fixed-input trajectory: it permits reduced acceleration or zero control, e.g. staying at the same X/Z from rest in an otherwise unblocked air input. A relaxed point needs exact refinement and current scalar/shared-scene acceptance.

## Candidate tools

| Tool | Relevant capability | Proposed role / qualification |
| --- | --- | --- |
| SCIP / PySCIPOpt | Mixed integer and nonlinear constraints, quadratic expressions, indicators and logic; direct sin/cos support if retained | Leading open-source candidate for a unified MIQCP/MINLP prototype or a mixed-integer relaxation. Requires a new symbolic model and useful variable bounds. General nonlinear constraints are supported, but indicator constraints specifically activate linear rows, not arbitrary Python/Java code. |
| Gurobi | Nonconvex quadratic constraints and general supported nonlinear expressions with spatial branch and bound; indicators and MIP machinery | Alternative backend for the same quadratic formulation or a relaxation. A suitable license is needed for unrestricted models; the bundled free license is size-limited. No speed superiority on our cases has been measured. |
| CasADi + Bonmin | Integer-variable masks alongside continuous CasADi expressions; integrates an NLP-based branching approach | Lowest-friction experiment using the current modeling stack. Bonmin is heuristic for nonconvex problems and expects smooth algebraic functions. Thresholds/clamps must be encoded as regimes, not simply left as discontinuous black-box conditionals. |
| Pyomo.GDP / GDPopt | Logical disjunction modeling; Big-M/hull transformations and logic-based decomposition | Useful modeling/algorithm layer, with a separate backend. Hull reformulation does not remove nonconvexity. Globally valid nonconvex decomposition needs appropriate global subproblem/bounding methods; IPOPT alone does not provide those guarantees. |
| OR-Tools CP-SAT | Integer/Boolean timing, sequencing and logic | A possible discrete schedule master. CP-SAT does not directly represent our real-valued trajectory problem. Integer scaling of coefficients is not a proof that continuous positions have been represented exactly. |
| Drake GraphOfConvexSets | Mixed-integer convex graph transcription, convex relaxation and path rounding; explicit convex-surrogate/nonconvex-restriction support | Closely related to spatial-region/event-graph proposals. Use conservative convex dynamics to guide route proposals, followed by the true local solve. Default shortest-path semantics and one continuous state per vertex need attention for revisits. |

SCIP supports global optimization within tolerances for its represented nonlinear model; Gurobi similarly uses spatial branch and bound. Neither statement guarantees timely closure or covers a physical case omitted from the model. Independent acceptance and honest unknown/budget reporting remain necessary.

## Monolithic versus decomposed use

Three approaches are worth distinguishing:

1. A unified nonconvex MIQCP/MINLP selects interactions, timing and continuous motion together. This offers the most integrated propagation and avoids a manually generated complete-schedule list. It also requires the largest verified model and may spend substantial time branching on continuous circle/dynamics relaxations.
2. A mixed-integer convex/linear relaxation carries continuous position/velocity constraints while selecting modes. Selected alternatives are passed to the existing full-history materializer/compiler/IPOPT path. This retains working physics and acceptance while replacing part of schedule selection. Weak relaxations can still produce many infeasible proposals; measure their rejection power and first-solution latency.
3. A purely discrete master chooses spatial/event/timing alternatives and calls IPOPT. It can exploit generic scheduling logic, but without continuous bounds it can degenerate into enumeration. It does not automatically learn that many choices are impossible from an unsuccessful local solve.

For decomposition, distinguish a feasible witness, a proved-infeasible relaxation and an unresolved numerical attempt. A local IPOPT failure, a bad path through an obstacle or a returned nonoptimal objective does not justify a globally valid no-good/optimality cut. Valid feedback can come from an infeasible outer relaxation, an exact logical conflict or a separately derived bound. Heuristic candidate drops are possible if reported as such; they do not produce infeasibility or optimality certificates.

Tight physical bounds and relative coordinates are important for a useful formulation. Pyomo's documented Big-M/hull tradeoff applies directly: broad M values weaken propagation, while lifted formulations add variables. A generic solver replaces search machinery, not the need for good mode/geometry modeling and candidate-region construction.

## Existing-work integration points

- `movement_generation.py`, `open_movement.py`, `open_ladder.py`, `stepping_generation.py`, `ceiling_generation.py`: define the supported alternatives and the semantics an integer model must preserve.
- `ladder_schedules.py`, `movement_contacts.py`: current eager combinations a backend might select symbolically instead. Repeated ladder ceilings and endpoint-stop ownership cannot be dropped.
- `movement_composition.py`, `movement_units.py`, `route.py`: materialize selected event/control assignments, retime once and preserve the original full-state solve.
- `compiler.py`, `CompileRunTicksPhysics.java`, `FixedMovementCompiler`, `ReplayInputState`: authoritative compiled movement/input assumptions.
- `ipopt/solver.py`, `worker.py`, `continuation.py`: existing nonlinear refinement, inertia/clamp branches and incumbent handling.
- `ipopt/model.py`, `obstacle_geometry.py`, shared `StaticScene`: unchanged acceptance for candidates recovered from any new backend.

Spatial passage shortlisting and mixed-integer scheduling are compatible. A finite region/event graph can delimit one model, while a backend selects movement methods, state-coupled transitions and timings inside it. Passing one frozen position/velocity between graph edges would lose the original-state coupling regardless of which library is used. A full-world all-blocks/all-faces/all-ticks model could simply recreate the current combinatorial explosion in algebraic form.

## What was actually checked

The installed runtime is CasADi 3.8.1 and advertises Bonmin, IPOPT and other plugins. PySCIPOpt, Pyomo, gurobipy, OR-Tools and pydrake were not installed in that runtime. No dependencies were installed and no production source was changed for this research.

Bonmin was loaded through CasADi in a temporary process and given this nonconvex toy model:

```text
maximize z
x^2 + z^2 = 1
-1 <= x <= 1, 0 <= z <= 1, b in {0,1}
x - 1.6*b <= -0.6
x - 1.4*b >= -1
```

`b=0` requires `x<=-.6`; `b=1` requires `x>=.4`. With `B-BB`, it returned `b=1`, `x=.3999999900`, `z=.9165151433`, independently consistent within 1e-6 with the analytic best `sqrt(.84)=.9165151390`. Total process-side construction/solve time was about 2.20 seconds. This verifies plugin availability and one integer/continuous formulation only. It is not Minecraft acceptance, an algorithm comparison or evidence of a parkour speedup. Temporary script/log: `/tmp/parkour-bonmin-availability.py` and `/tmp/parkour-bonmin-availability.log`.

## Recommended next experiment, not implemented

Benchmark generic methods before committing further effort to a bespoke branching engine. Start with a small, explicitly scoped scene/horizon and include at least a meaningful route/obstacle-side choice. Compare existing generation+IPOPT, an exact circle MIQCP, and a disk-relaxed master with the existing solver as refinement. Bonmin can establish a baseline without adding a dependency; SCIP is the leading open-source global-backend candidate and Gurobi is an optional licensed comparator.

Then add a case with velocity cancellation and mixed contact timing, a self-jump/revisit case, and a ladder case with clamping and endpoint stops. Measure model-construction time, total time to a shared-scene-accepted solution, number of emitted/attempted schedules, acceptance rate and retained route diversity. Keep physics/candidate coverage identical within each comparison. No version of this experiment should quietly declare a local numerical miss infeasible. Larger-horizon/global-proof work should follow evidence of practical benefit, rather than being assumed necessary to obtain a useful short list.

## Primary sources checked

- [Pyomo GDP solving and reformulations](https://pyomo.readthedocs.io/en/stable/explanation/modeling/gdp/solving.html): Big-M/hull, bounds and nonconvex qualification.
- [Pyomo GDPopt](https://pyomo.readthedocs.io/en/stable/explanation/solvers/gdpopt.html): logic-based decomposition and the requirement for global subproblems when claiming global nonconvex solutions.
- [PySCIPOpt nonlinear expressions](https://pyscipopt.readthedocs.io/en/stable/tutorials/expressions.html) and [constraint types](https://pyscipopt.readthedocs.io/en/stable/tutorials/constypes.html): nonlinear support and linear indicator semantics. [SCIP licensing](https://www.scipopt.org/): Apache 2.0 since 8.0.3, subject to linked-component licenses.
- [Gurobi nonlinear optimization](https://docs.gurobi.com/projects/optimizer/en/current/features/nonlinear.html), [quadratic/indicator constraints](https://docs.gurobi.com/projects/optimizer/en/current/concepts/modeling/constraints.html) and [licensing](https://www.gurobi.com/product/pricing-and-licensing): supported global formulation machinery, not measured performance on this problem.
- [CasADi mixed-integer FAQ](https://github.com/casadi/casadi/wiki/FAQ:-how-to-work-with-discrete-mixed-integer-problems%3F) and [Bonmin manual](https://www.coin-or.org/Bonmin/Intro.html): integer-variable interface and nonconvex heuristic limitations.
- [OR-Tools CP-SAT](https://developers.google.com/optimization/cp/cp_solver): integer-valued modeling domain.
- [Drake GraphOfConvexSets](https://drake.mit.edu/doxygen_cxx/classdrake_1_1geometry_1_1optimization_1_1_graph_of_convex_sets.html): convex graph formulations, relaxation/rounding and nonconvex restriction guidance.
- [Morozov et al., 2025, Shortest Walks in Graphs of Convex Sets](https://arxiv.org/abs/2507.10878): repeated visits with coupled continuous programs, a closer conceptual match than a simple block-only shortest path. Its convexity assumptions still need an appropriate relaxation of our model.
