# Choosing a whole-route reference strategy

Latest simplification: [GEOMETRIC_DECISION_GUIDANCE.md](GEOMETRIC_DECISION_GUIDANCE.md) supersedes the initializer-first recommendation below. The existing cell-route polyline may already provide adequate direction for local decisions. Test that cheap guide first; provisional physical drafts and mixed-input references should address a measured shortcoming rather than be prerequisites.

2026-10-31. User clarification: the relaxed solve exists to provide a useful general trajectory before local decisions are optimized. A mixed-input baseline must include unresolved ceiling, stepping, landing, input and timing alternatives together. Picking those methods first defeats that baseline's purpose. The alternative is to choose provisional variants, optimize a complete draft, then revisit local decisions using its whole-route context.

Recommendation: build the provisional-draft/improvement pipeline next and retain a bounded mixed-decision comparison. This is a recommendation awaiting the user's selection, not an assertion that they have chosen one option. A comprehensive mixed SOCP should not block the first-pass baseline. No runtime source or measurements changed in this evaluation turn.

## Goal and current evidence

For each already shortlisted broad spatial route, obtain a reference with plausible positions, useful momentum, vertical/contact phases and a continuation toward the goal. Use that context to shortlist local interaction methods and timings while retaining alternatives. All facings remain free and every numerical solve starts from the original full initial state.

The desired outcome is fewer expensive physical decision evaluations and better final elapsed time/objective. A tight bound, convex formulation, low relaxed solve time or nearly integral selectors is useful only insofar as it contributes to that outcome. A reference and a bound have different roles; a provisional physical reference need not bound the optimum.

Existing experiments do not settle this comparison. The mixed pilot varies ordinary departures, run allocations and passage gates on a fixed support/flight family. It has not left ceiling/stepping methods unresolved in one baseline solve. It also used the old inertia hull, while the current agreed direction omits thresholds and carries momentum normally. Earlier measured errors and the narrow-support counterexample therefore do not establish the performance of a full no-threshold mixed-method model. The current default generic generator likewise is not an implemented route-guided provisional-draft improvement algorithm.

## Option M: one mixed-decision baseline

Represent all relevant methods/timings in a shared model and relax the unresolved choices. At the initial solve, no ceiling/stepping method is preselected. Geometry may conservatively identify possible opportunities, but it must not choose their outcome in advance. The model can use fractional alternatives while solving for a common whole-route objective/reference.

Selecting a candidate and rerunning a conditional model is a later use of that reference. It is not a requirement to pick every contact before constructing the baseline. Exact-case enumeration is a tiny-scene benchmark oracle, not the proposed initializer.

Benefits:

- A single solve can account for interactions between a ceiling choice, landing time, run allocation and later jumps.
- It can offer context before any complete feasible discrete program has been discovered.
- It may propose alternatives that conservative draft policies never try; fractional weights and sensitivities can inform proposals, though weights are not probabilities.
- An inclusive outer formulation may also supply bounds, separately from reference quality.

Risks:

- Correctly modeling the union of contact effects is substantial work. Omitting useful methods or their successors can invalidate both coverage and the route picture.
- Fractional mode resets, relaxed guards and state mixing can give a physically meaningless combination of beneficial effects. Removing inertia-hull freedom will not remove these other effects.
- Good objective values do not imply a useful trajectory or local ordering. Hard contacts can make the relaxed winner unrelated to the best actual strategy.
- The full model can require more time and memory than obtaining several reasonable complete drafts. The existing passage-only benchmark does not measure this tradeoff.

Do not make an SOCP mandatory solely because the last experiment used one. For reference construction, a compact smooth mixed NLP with shared state is also a legitimate experimental implementation under the accepted IPOPT-quality premise. It still must encode the intended method alternatives. Its fidelity/runtime must be measured, and its primal value is not automatically a computed bound. Convexity is a potential speed/bound advantage, not the purpose of the work.

## Option P: provisional complete drafts, then improve

The normal solver optimizes facings given discrete decisions. It does not by itself solve the discrete selection problem. Option P uses that solver first to build context, then searches decision alternatives with a complete continuation available.

1. Generate a few inexpensive, structurally diverse complete drafts inside the chosen spatial corridor. Heuristic policies may favor simple stable landings, fewer interactions, or useful stepping/ceiling shortcuts. Choose nominal timings without exhaustively optimizing each event. These are provisional choices, not commitments or exclusions of other legal methods.
2. Optimize each draft's full trajectory from the original state. Keep complete position, velocity, contact phase and method information. Prefer feasible references; retain the best useful draft if bootstrap feasibility is still unresolved.
3. Revisit a decision or a small coupled group. Substitute alternatives, rebuild the affected vertical/control schedule, retime the suffix and reoptimize all facings globally. Keep unrelated future method intents as a provisional continuation, with timing/phase repair as needed. An earlier ceiling change must not inherit impossible later timestamps or an old Y trace.
4. Accept improvements according to elapsed ticks first, then the configured equal-time objective. Update the reference immediately. Repeat sweeps until no useful improvement is found under the experiment's work budget.
5. Retain several references and try small coupled method changes to escape one-choice local traps. A landing-plus-next-jump change may be useful even when neither isolated edit works with the old continuation.

Position and momentum from the reference guide proposal ordering and warm starts. They are not hard intermediate endpoints. Comparing full-route trial results is the initial evaluator; direction-only or short continuation scores can be added later if measured useful. When timings differ, align by route/contact progress or account for the remaining horizon rather than comparing raw event-relative distance.

The bootstrap is the main uncertainty. Finding a good complete draft may itself be hard. Do not hide that work in an exhaustive initializer. Generate only a few coherent coarse strategies first and record discovery failures. If a geometrically inconsistent draft has otherwise valid dynamics, an optional penalized-feasibility solve can expose where it fails; such a path is a reference, not an incumbent. If its chosen ground/Y/control history is itself invalid, regenerate the methods/suffix instead of expecting geometric slack to repair it.

Benefits:

- Reuses the existing ceiling/stepping compiler, continuous solve and physical checks.
- A feasible draft reflects actual contact effects and useful momentum rather than averaged incompatible modes.
- It directly tests the hypothesis that knowing a roughly correct route makes forward local decisions easier.
- It provides accepted incumbents and a useful baseline independently of success with a new mixed formulation.

Risks:

- Initialization bias can hide a substantially better method combination.
- One-decision sweeps can reject good methods because the provisional suffix is incompatible. Retiming, suffix repair, coupled moves and diverse drafts are essential controls.
- Bootstrap may cost as much as the original search on difficult routes.
- It supplies no global upper bound or proof that discarded neighborhoods cannot improve. A failed fixed-suffix trial does not prove the action itself is bad.

If the current feasible draft is retained among the candidates and every accepted edit improves the actual full-route result, accepted quality need not decrease. This does not give global convergence or a guarantee of finding the first feasible draft. An infeasible/slack reference has no such feasible-incumbent property.

## Both, with distinct roles

Prioritize P as the next complete baseline. Continue M on small cases that genuinely require changing dynamics, not more passage-only controls. The mixed model is successful only if it improves discovery, useful proposal diversity or final performance enough to justify its cost.

A combined method may use a provisional draft to warm-start the mixed problem while leaving its decisions free, then use the mixed result to propose revisions or an alternative draft. It may also use a mixed reference when provisional drafts cannot establish a useful continuation. Optional local bounds can prioritize these trials. Neither guide should permanently veto another method merely because it disagrees.

This combination is not a mandate to run both solvers on every decision. First establish the marginal benefit. If P already gives a reliable reference, call M only on ambiguous, coupled or failed regions. If M cannot retain useful real alternatives or its reference repeatedly exploits unavailable contact combinations, strengthen or redesign it before a broad integration.

## Comparison experiment

Use common geometry, spatial corridor, movement opportunities, no-threshold physics, objective/horizon policy and final whole-route solver. Include optional ceiling-hit versus avoidance, jump versus normal-step/slab-boost/grind, optional landing versus overflight, and a case requiring two neighboring changes. Keep ladder and intentional wall-hit tests excluded.

Compare three arms:

- P: a few provisional drafts and repeated local improvement;
- M: an initial solve with all tested contact/input choices unresolved, followed by the same local improvement machinery;
- P+M: provisional initialization plus mixed suggestions, followed by that same improvement machinery.

Use tiny exhaustive physical families or retained strong witnesses as evaluation references. Give every arm the same total budget, include initialization/model construction and repair costs, and distinguish held-out comparisons from initializer choices. The main outputs are first useful/feasible full reference, coverage of good method combinations, top-k local ranking, final ticks/objective, physical solve/generation work and elapsed time. Record failed drafts and numerical unknowns. Geometric reference RMSE and relaxed-bound gaps are diagnostics, not the primary success criterion.

Keep the rollout/improvement machinery common so mixed-model coverage is not confused with a better local search implementation. A mixed warm start must not fix provisional contact selectors; otherwise it merely reproduces P under another name.

Proceed with broad mixed integration only if it helps on genuine dynamic-method cases under these matched conditions. If P works well and M adds no benefit, defer M. If initialization traps remain but M supplies useful alternatives or bootstraps a missing continuation, retain both. This answers the user's practical question with measured decision quality rather than a prior preference for convexity or certification.

## Primary-source context

[Tedrake's hybrid trajectory optimization notes](https://underactuated.mit.edu/contact.html) distinguish optimization with a supplied mode sequence from discovering that sequence. That distinction is exactly why the fixed ordinary-profile pilot does not yet test M. The state/reset/guard framework describes the modeling obligation; it does not establish our formulation's performance.

[Bertsekas's rollout discussion](https://www.mit.edu/~dimitrib/Slides_Extended_RL_Lecture.pdf), around displayed slides 34-35, evaluates alternatives using a base-policy continuation. This motivates P's use of a provisional future. Our implementation would also globally reoptimize facings and repair suffixes, so standard rollout guarantees are not assumed for arbitrary infeasible drafts or restricted neighborhoods. The recommendation to prioritize P is an inference from our existing capabilities and missing evidence, not a theorem from these sources.
