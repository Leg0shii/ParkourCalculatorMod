# Informative initial solves with provisional route choices

Latest user clarification, 2026-11-01: sequentially commit/retain discrete decisions within a spatial route, then re-solve from the original state with all earlier facings free for later decisions. No intermediate numerical state is fixed. Weak dependence of earlier decisions on separated later ones is a provisional hypothesis; retain alternatives and reopen/cluster when maps demonstrate coupling. The user selects the time/progress frontier as the next workstream. Earlier proposals to prioritize a new upfront/provisional-rollout mechanism are not the current next step.

Discussion, 2026-11-01. User clarifies that solve-then-refine can be useful: solving an earlier decision reveals its effect on later ones. The failure to avoid is an initial numerical path that phases through an important downstream obstacle and therefore gives misleading information. This is a recommendation, not an implemented new policy or a claim of measured superiority.

## Recommended middle ground

Keep iterative solves, but give the first solve a provisional continuation that respects the selected spatial route and important obstacles. Reuse the existing movement/contact/blockage builders for reasonable initial choices. Do not enumerate or optimize every downstream choice before the first useful solve, and do not remove downstream geometry merely because its best timing is undecided.

Distinguish two meanings of fixed:

- Each IPOPT call has explicit fixed inputs and constraints; only the usual continuous facings are optimized.
- Those orchestrator selections are provisional across calls. Later information can change passage timing, step versus jump, landing and ceiling choices. A selection inside one solve is not an irreversible search commitment.

This preserves the user's solver boundary and the value of initial numerical feedback. A feasible first rollout supplies plausible arrival states under actual momentum and geometry, rather than a path that cuts through a blocker. It is a candidate/reference, not a relaxation bound or proof that its chosen future decisions are best.

## Example and loop

For a route containing step A then blockage B:

1. Pick a reasonable existing step/run/jump variant for A and an existing passage/avoidance variant and provisional timing for B. Respect the route's chosen side of B. Compile both through the current builders and solve from the original full initial state.
2. Inspect the resulting approach to B: arrival timing, available position/velocity and the corresponding geometric constraints. Use that information to propose the nearby existing alternatives, such as an earlier/later passage or another movement method.
3. Re-solve the revised full prefix/rollout, with every earlier facing still free. Preserve the accepted incumbent and pending alternatives. If A and B are tightly coupled, consider their joint variants rather than fixing A from one downstream guess.

Only model the continuation needed to assess the current local choices meaningfully. If B lies beyond the comparison horizon and cannot yet change relevant dynamics or route requirements, it need not be included in that prefix. If a later turn requires earlier preparation, include sufficient lookahead or group the coupled decisions. Choose the horizon consistently for the alternatives being compared; do not reward one candidate with extra elapsed time.

The initial guide can still rank geometric choices before a numerical trajectory exists. A provisional timing is an initial orchestrator choice; it does not pin an exact crossing location or any facing. The old solved X/Z endpoint remains a guess/diagnostic, never a new fixed initial state.

## What must be protected in the rollout

Preserve the spatial route's obstacle side, broad passage order and major required detours through existing selected constraints. For interactions represented in the rollout, select a physically coherent existing default case; do not merely declare a stepping/ceiling outcome while omitting its constraints. Include known relevant avoidance so the route does not shortcut through downstream geometry.

Exact downstream timings and movement methods can be revisited after the solve. “Coarse protection” does not mean a new disjunctive collision constraint or unresolved variables inside IPOPT: select a default supported branch/guard schedule for that call. If broad safe constraints are available without fixing a precise interaction, use those existing constraints. Do not promise that an untimed nonconvex cell union is directly enforceable by the unchanged solver.

Independent checking remains necessary. A path that violates an important route/scene condition is not a comparable accepted decision score. If it reveals a missed blocker, add the existing required constraint and retain the repair state; avoid repeatedly solving the same geometry-incomplete model. Actual early contact or a changed Y/ground regime requires selecting/recompiling the appropriate case, not treating it as a harmless trajectory adjustment.

## Limitations and safeguards

A bad provisional continuation can make an earlier choice look worse or infeasible. Failure of A plus one default B is only failure/unknown for that combined branch. It does not eliminate A generally. Try a small portfolio of materially different downstream choices or couple the decisions when the ranking changes substantially with the default. A portfolio is heuristic coverage, not a dominance proof.

A validated rollout is physically useful but not necessarily representative of the optimum. Conditional arrival information helps propose choices; all earlier facings must still be allowed to change when those choices are reoptimized. Do not greedily choose local timing from velocity/position alone or introduce a new weighted score without evidence.

Repeatedly changing A and B can cycle. Store discrete selections, compiled constraints and pending variants; avoid identical retries without new state/budget/initialization information. Preserve all accepted incumbents. Deferred velocity thresholds and normal momentum rules remain unchanged.

The overhead must stay small enough to justify its information. Use one or a few provisional continuations per spatial route/local cluster, then expand where the result identifies a real ambiguity. Do not assume this reaches the requested small candidate counts on dense scenes without measurement.

## Proposed experiment

Compare the current lazy initialization with geometry-aware provisional continuations under matched total generation/solve work. Use existing builders, existing cell routes, saved-state handling and the current score. Include multiple defaults on the controls where downstream method/timing materially changes feasibility. On small scenes compare against independently optimized alternative completions or a small exhaustive reference; historical solved guards/facings are diagnostic controls, not cold initialization.

Measure first-rollout route/scene validity, required geometry repairs, useful arrival-state information, revisions of provisional choices, sensitivity of earlier-choice ranking to the default, retained successful alternatives, total work and final accepted times/objectives. Verify all accepted witnesses with the shared scene checker. This evaluates whether the initial solve is informative instead of merely counting whether it returned a scalar-feasible path.

No mixed-input/disk relaxation, certified bound, new physics/constraint family, richer score or time/progress-frontier implementation is proposed as a prerequisite. Intentional final wall hits, ladders and velocity thresholds remain excluded in the current tests.

## Close clusters versus separated decisions

The recommendation can apply to both; the discrete decision cluster and the numerical lookahead horizon are different concepts.

Close/coupled A and B: enumerate their compatible joint discrete variants as the current cluster policy already does. A provisional continuation is primarily context beyond that cluster. Do not replace the required joint A/B comparison with a greedy default for B when B is tightly coupled to A.

A and B separated by, for example, ten air inputs: normally keep separate discrete searches. While exploring A, a longer reference/rollout may include a provisional fixed B variant to account for B's obstacle/turn without enumerating every A x B combination. Later explore B's variants for the retained A histories, re-solving all continuous facings from the original full state. The earlier physical input/constraint history is conditional, and its intermediate numerical position/velocity is never frozen. This is separate discrete selection with globally revisable continuous facings, not a requirement to merge every downstream event into one combinatorial cluster.

If the actual evaluated prefix ends before B, B cannot be phased through inside that prefix and its avoidance constraints are not inherently required there. A longer rollout that reaches/passes B must include a supported provisional B interaction. Merely mentioning B in an untimed guide does not enforce it. Numerical lookahead through B while scoring A at an earlier common state would require the orchestrator to request that objective tick explicitly; it is a possible use of the existing objective interface, not behavior silently attributed to the current prefix evaluator. The existing common-tick score need not otherwise change.

Ten air inputs are not a proof of independence: carried momentum and position can make B require preparation earlier. Keep A/B separate initially when appropriate, and increase lookahead, retain multiple downstream defaults, or merge a cluster if actual continuation feasibility or earlier-choice ranking is highly sensitive to B. Do not use a fixed tick-gap threshold as a dominance/independence proof. This remains recommended policy, not newly implemented behavior.
