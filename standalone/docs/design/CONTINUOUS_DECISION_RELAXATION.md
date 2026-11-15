# Continuous decision relaxation with full-strength controls

Measured follow-up, 2026-10-31: [route-guidance results](experiments/route-guidance/README.md) now compare all three formulations. The convex disk is the recommended first backend; retaining the circle did not repair conditional ranking or consistently improve reference shape. Inertia-hull freedom is a major measured source of optimism. This supersedes the earlier circle-first preference below.

Latest update, 2026-10-31: the user now welcomes disk controls when they buy convexity. [CONVEX_ROUTE_GUIDANCE_PLAN.md](CONVEX_ROUTE_GUIDANCE_PLAN.md) supersedes the circle-first experiment order below, verifies existing native disk machinery, specifies a fully convex disaggregated alternative and records the remaining implementation gaps. The compact circle/disk comparison remains useful, but changing only its control set does not convexify its mode/state products. The earlier coordinated discussion is retained below as design history.

2026-10-31. User priorities: the optimistic reference should resemble the eventual physically optimized trajectory and be easy for the generic continuous solver. The user explicitly authorized renewed coordination with the mixed-integer agent. Solver quality remains an accepted assumption for this design discussion. This note records that exchange; neither implementation was edited or merged, and no new timing experiment was run.

## Agreed recommendation

Keep one shared unit control direction per input. Relax unresolved decision integrality first. Do not replace the control circle by a disk by default.

The optional relaxation discussed previously is circle -> disk: fixed acceleration magnitude becomes a maximum magnitude. It is not required for the inclusion guarantee. A nonconvex continuous relaxation can still contain every real integer trajectory and therefore bound the MAX objective under the stated solve-quality assumption.

Making the control set a disk may simplify a formulation, but it also permits reduced input strength. It does not make the proposed compact model convex by itself, because mode/state/control products remain. Whether it makes direct IPOPT guidance faster is unmeasured.

## Model decisions as event/phase choices

Reuse the mixed-integer work's event/departure choices, phase-flow relationships, passage intervals and conservative state domains. An event's one-hot alternatives select its timing and method without enumerating complete input histories first. Include skip/overflight or other alternatives when they belong to the declared route family.

High-level route choices are already selected. Preserve their geometric side/order information and the original initial state. Relax the unresolved local decisions; do not arbitrarily fix their arrival ticks or intermediate numerical states.

For fractional event selectors, use explicit smooth algebraic constraints. The current SCIP indicator APIs require binary selectors and are not a literal fractional-decision IPOPT model. Start with bounded Big-M activation rows using valid domains. Strengthen important geometry/contact guard groups selectively with hull or McCormick/disaggregation rows. A full trajectory-wide state/control hull is not the first implementation target.

Avoid default complementarity-style lambda*g(x)<=0 gating. For any strictly positive lambda it fully enforces g(x)<=0, which can nearly restore exclusivity and creates degeneracy at zero. A smooth expression is not necessarily an easy decision relaxation.

## Keep one effective heading, not one heading per mode

At input t, use either a unit vector q_t or one angle theta_t:

```text
q_t = (cos(theta_t), sin(theta_t))
```

or equivalently enforce qx_t^2+qz_t^2=1. This is the effective acceleration heading. It is not one physical facing shared across all fractional counterfactual input modes.

Let lambda_tk >= 0 and sum_k lambda_tk=1 select/blend modes with authoritative magnitudes m_k. Use:

```text
m_bar_t = sum_k lambda_tk*m_k
acceleration_t = m_bar_t*q_t
```

Then the acceleration norm is exactly m_bar_t. At one-hot lambda this is the real selected-mode magnitude. After choosing an integer mode k, recover its free facing as atan2(qz,qx)-base_k. In the existing force-45 policy, jump and run/air have different base offsets, so a shared physical yaw would rotate their counterfactual acceleration directions differently. A shared effective heading is the clean first representation while facing is unrestricted. If facing ties or restrictions return, the mapping must be extended explicitly.

Do not give each fractional mode an independent heading. Two half-weight controls pointing in opposite directions can cancel even when each individually lies on its own circle. That recreates reduced-strength control without an explicit disk. Likewise, a shared unit vector plus loose indicator-to-acceleration rows can leave the actual acceleration detached from that vector. Keep the coupling equation explicit.

## Blend complete transitions from the same incoming state

Let c_t be incoming horizontal carry after the local inertia rule. For unclipped ordinary run/jump/air modes with mode magnitude m_k and friction f_k, use:

```text
p_next = p + c + (sum_k lambda_k*m_k)*q
v_next = (sum_k lambda_k*f_k)*c
       + (sum_k lambda_k*f_k*m_k)*q
```

This is the weighted sum of each mode's complete transition from the same incoming state and heading:

```text
s_next = sum_k lambda_k * F_k(s,q)
```

Do not instead multiply an independently averaged friction by an independently averaged acceleration. That introduces cross terms combining coefficients from different counterfactual modes. Both constructions can include the integral trajectories, but the complete-transition blend retains the intended within-mode coefficient pairing.

Also avoid an RMS fractional magnitude sqrt(sum lambda*m_k^2) for the guiding reference: it exceeds the weighted magnitude sum lambda*m_k when magnitudes differ. The recommendation is not a claim that the compact blend is a convex hull. It is a smooth, generally nonconvex construction with correct one-hot behavior.

For landing, stepping or ceiling outcomes, F_k must include the relevant vertical, ground, contact and input-state changes. Mixing a position row into an unrelated old Y schedule is not that transition. Existing templates derived for one incoming Y state cannot establish coverage of all incoming histories.

## Automatic inertia belongs inside the physics model

Inertia regimes should not become orchestration events. But fixing one inertia pattern inferred from a reference trajectory can exclude a valid route decision and invalidate a route-wide bound.

The continuous model must retain every relevant inertia branch. Two proposed treatments are local continuous selectors with closed overlapping guard boundaries, or a bounded convex hull of the carry graph. After discussing both, the agents slightly prefer the exact bounded carry-graph hull for the first compact smooth baseline, followed by a comparison with the local-regime formulation for fidelity. A hard if/else keeps the physical graph but is not smooth; a sigmoid approximation has no automatic inclusion guarantee.

An optional hull example, for valid bounds L < -tau < tau < U and carry(v)=0 inside the dead zone and v outside, has vertices:

```text
(L,L), (-tau,0), (tau,0), (U,U)
```

The real graph, including the nonzero carry at exact thresholds, is contained in this hull. Its carry error relative to v is bounded by tau; it avoids introducing planner-level inertia binaries. Other velocity domains need the corresponding restricted graph hull. This treatment is explicit in the first-model proposal and must be evaluated for fidelity/runtime; it is not a reason to relax the control circle or every other physics rule. Carry errors can accumulate into material route-level optimism. With this hull, even integral event selectors may produce a nonphysical trajectory: the mixed-integer model is already outer. One-hot coverage tests must establish a lift for each real witness, not claim that every integral solution is physical.

## The inclusion guarantee is unchanged

At one-hot event/mode selectors, each supported real path must satisfy the explicit transition and guard formulation. Its free facing maps to the unit effective heading for its selected mode. Allowing fractional selectors preserves every such assignment. The control circle need not be enlarged for this argument.

With otherwise identical constraints, replacing the shared unit circle by a disk creates a further outer relaxation:

```text
real_best <= U_circle_decision_relaxation <= U_disk_decision_relaxation
```

This ordering assumes the two models differ only in that control-set enlargement. Arbitrarily different hull/blend formulations need their own inclusion arguments.

For candidate decision a, fix its selectors to the real discrete choice, leave future selectors fractional, and reoptimize the full trajectory from the original state. All earlier effective headings/facings remain variable. This gives the previously proposed conditional value U(D,a), still without needing a full integer search first.

## Important findings from the other agent

The agent recommends this compact shared-state/common-effective-heading full-transition blend as the first direct IPOPT guidance model. It independently agrees that disk controls are optional and should not be the default for reference fidelity.

Its current joint prototype, source `41342f1784c889ae5abd43b8c15d51be69bd120f` on `spike/ipopt-mixed-integer`, already chooses normal/run departure timing, run allocation and passage/gate choices on a supplied support sequence. Its ordinary-jump phase tables do not yet cover arbitrary landing/stepping/ceiling histories. A fixed support sequence also does not cover every possible contact sequence within a broader cell corridor.

The existing guarded candidate generator cannot be imported unchanged as a guaranteed outer model:

- inertia guards leave 1e-8 excluded bands around the threshold;
- horizontal avoidance uses a 2e-7 clearance guard;
- per-block passage counts cap a declared candidate family;
- scope is the implemented input/movement subset, not all game behavior.

For a bound, close/overlap branch boundaries and use nonpenetration boundaries consistent with the claimed real model. Either retain caps as explicit scope restrictions or remove/justify them. Reuse source-derived domains only for the movement/history scope they cover.

## What the existing measurements do and do not say

The historical fixed-schedule SCIP-plus-IPOPT comparison has no consistent circle/disk speed winner. Selected non-ladder median totals were:

| Case | Circle + refinement | Disk + refinement |
| --- | ---: | ---: |
| Cross neo | 0.790 s | 1.265 s |
| Wide neo | 2.010 s | 0.906 s |
| Interleaved obstacles | 0.679 s | 0.364 s |
| Repeated support, two jumps | 7.784 s | 2.920 s |

Those runs select integer clearance/physics alternatives and then refine their result. They do not measure direct IPOPT solves of fractional timing/decision models, reference-path similarity or objective loss on refinement. No direct-guidance speed conclusion follows.

The joint scaling campaign also has a flat five-jump control whose departures are already fixed but whose SCIP root processing remains expensive. It contains thousands of auxiliary physics/indicator variables. This motivates a compact direct continuous formulation; it does not establish the runtime of relaxed local selectors in IPOPT.

Reviewed reports, read-only: `../ParkourCalculatorMod-ipopt-mip-spike/standalone/autonomy/experiments/mixed-integer-spike/README.md` and `.../joint-scaling/README.md`. Exact paths and source were verified during coordination; no external implementation files were edited.

The agent explicitly confirmed the shared-state/common-heading full-transition blend as its preferred first formulation and the bounded carry-graph hull as its preferred initial smooth inertia treatment. These are coordinated design recommendations, not measured runtime conclusions.

## Agreed first experiment

Use the normal/run/avoidance subset first, with free effective headings and shared original-state trajectories. Then add variable Y-changing transitions before claiming coverage of all current route decisions.

1. Verify one-hot lifts against compiled real witnesses and the authoritative physical checks.
2. Check fractional control norm equals sum lambda*m, including unequal mode magnitudes and base offsets.
3. Check complete-transition blending preserves per-mode magnitude/friction pairing.
4. Include alternative inertia branches and threshold boundaries.
5. Compare conditional relaxed values with tiny enumerated timing/passage families, taking solve quality as given.
6. Compare circle and disk versions of this same direct continuous model: build/solve time, final-objective gap after physical refinement, reference position/velocity similarity, refinement success and decision-ranking agreement.

Only change the control-set constraint in the circle/disk ablation. Keep horizon, route family, decision domains, seeds, guards and evaluation budget matched. Measure both time-aligned states and spatial/route-aligned shape, since arrivals can shift. No speed or closeness improvement for this new model has been measured yet.

## Source context

This design was discussed directly with the existing mixed-integer agent at the user's explicit request. It combines that agent's implementation review and algebra with the route-guidance requirements. Existing reports are observations about their stated experiments, not new experiments in this branch.

[IPOPT documentation](https://www.coin-or.org/Ipopt/) permits smooth nonconvex NLPs; convexifying the circle is not a prerequisite for using that solver. [Pyomo GDP reformulation documentation](https://pyomo.readthedocs.io/en/stable/explanation/modeling/gdp/solving.html) distinguishes bounded Big-M/hull formulations and notes that nonconvex branch constraints remain nonconvex after hull reformulation. These general sources support the modeling context; the shared-heading transition blend is the coordinated proposal recorded here.
