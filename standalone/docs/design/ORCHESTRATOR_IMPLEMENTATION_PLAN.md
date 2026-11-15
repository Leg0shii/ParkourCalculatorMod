# Consolidated orchestrator implementation plan

Current implementation authority, 2026-11-03: [DFS_INTERACTION_IMPLEMENTATION_PLAN.md](DFS_INTERACTION_IMPLEMENTATION_PLAN.md). The user replaces shortest-prefix/window-drain traversal with ordered depth-first completion and backtracking, together with the shared early interaction gate. The seven-workstream record below describes the prior implementation; its old global ordering/window behavior is superseded, not a requirement to preserve.

2026-11-02. This is the authoritative implementation scope and ordering. The seven behavioral workstreams below are now wired into the standalone checkpoint orchestrator. Numerical validation is recorded in [the implementation results](experiments/orchestrator-fixes/README.md). Cold Real11 completion and broad false-pruning measurement remain open acceptance items; implementing persistence does not establish efficient complete discovery. Corridor failure logging originated in3082f8fc.

## Implemented status

| Workstream | Runtime implementation | Validation and limits |
| --- | --- | --- |
| 1 | solve_spec.py plus evaluate_program scoped assembly/validation and physical/context identities | Numerical omitted-wall guidance passes only as preliminary; complete alternative avoids it and independently replays. |
| 2 | routes.py projects geometry-derived free space without waypoint clips; local.py scopes landmarks/projection | Remote free-space and existing corner/edge tests pass. Projection is deliberately broad; landmark signatures still distinguish routes and sometimes reject physically valid other-route results. |
| 3 | reachability.py, screen.py and linear_reachability.py | Phase sweeps, interval/norm propagation and optional conservative acceleration-polygon LP. Random feasible trajectories and selected clips survive; B2 extra avoidance excluded in reference control. |
| 4 | constraint_variants.py feeds complete fixed alternatives to the existing contact/constraint builders | Known reference recovered with B6 alternatives already supplied. Useful validated facing guesses replace stale guesses. No signature-side shortcut removes vertical passage alternatives. Follow-up identifies an unfinished generation requirement: inactive faces/gates remain enumerable and early per-obstacle/partial-state canonicalization is not implemented. |
| 5 | time_progress.py protections and solve_spec.processing_key | Same-witness position/velocity, schedule/phase gates, OR protections, exact order and saved deferrals. Existing margins retained. |
| 6 | block_contacts repair checkpoint, movement_frontier symbolic cursor, checkpoint_search searchState and fixed-clock CLI | Split/resume, in-flight interruption, cached incumbent and explicit unknown-retry tests. Numerical reference resumes2+8 calls with zero repeated completed branches. Capped exploratory spatial probes remain comparison guidance with unknown outcomes, not exhaustive continuation certificates. |
| 7 | prefix_viability.py before generation scoring and numerical evaluation | Descending below all possible first floors rejects43 distinct historical falls, including the three reported cases. Grounded/rising, lower-support and optional terminal-air controls survive. More general future event-chain impossibility remains unknown. |

All original acceptance criteria below remain the research target. In particular, neither the fixed-reference replay nor unit tests close cold-discovery or heuristic-optimality acceptance.

## Preliminary generator follow-up

User clarification: the governing fix is one shared early block/tick/phase interaction gate across all builders and checks. A provably unreachable interaction produces no block-derived choice, condition or interaction check. Duplicate generation and P04/P05 are consequences of incomplete enforcement of that same invariant, not separate landing/avoid relevance cases.

[GENERATION_RELEVANCE_PLAN.md](GENERATION_RELEVANCE_PLAN.md) records the requested early relevance/canonicalization correction and the P04/P05 ownership/truncation bug. Both remain planned. The full P04/P05 proposals fail the existing shared screen before truncation, while their orphan approach-row prefixes pass afterward. This is not an above-B4 case: their feet remain below B4 top7.5.

## Fixed requirements

- Work in the existing isolated research/ipopt-cell-routes branch/worktree. Make commits at meaningful implementation boundaries, not every discussion turn. No other-agent communication is requested.
- Keep the existing continuous equations and hardcoded discrete inputs/constraints per IPOPT call. Reuse existing normal/run/jump/landing/stepping/ceiling/contact/avoid builders, coefficient compiler and scene checker. No second physics implementation.
- Re-solve from the full original state; all earlier facings remain variable. Carry momentum normally, with inertia thresholds zero. No ladders, water or intentional wall-hit search in this milestone.
- Keep existing0.25-block progress and1-tick spatial-time margins. Compare fixed-space goals by achieved time and fixed-time goals by objective. Do not design separate completion-time candidate families.
- Keep corridor checks enabled, with the implemented diagnostics. Their eventual removal/weakening is undecided; gather counterchecks rather than assuming they establish physical validity.
- Existing active/batch sizes are resource settings, not permanent eligibility limits. Do not solve failures by blindly raising them. Accuracy and processing useful alternatives take precedence over short runtime.

## 1. One explicit solve specification and validation scope

**Change:** extend the existing fixed-program/record machinery with a common specification: original state, geometry/body/physics identity, complete discrete movement/contact schedule, objective/horizon, selected interaction constraints, modeled interactions/time spans, route requirements and preliminary-versus-complete purpose. Keep mutable queue state, guesses and counters outside the stable identity. Reuse existing physical keys and repair context instead of introducing another solver framework.

For a preliminary model, enforce only the interactions and route requirements it includes. An omitted B6 requirement must not reappear in a projection, landmark, readiness or physical-acceptance gate for that preliminary result. Preserve checks on earlier modeled requirements. A full-scene diagnostic may report an omitted future collision without converting successful preliminary guidance into failure. A complete witness must pass the full declared scene and actual task requirements.

The existing partial=True flag is insufficient: it only allows a signature prefix and still checks the entire projection. Scope both forms of route checking explicitly. Log model-feasible, route-scope-valid and full-scene-valid separately. Preliminary results cannot become accepted final incumbents.

**Acceptance:** same B6-omitted trajectory is usable preliminary guidance but cannot pass as a complete physical witness. An included earlier condition remains enforced. Changing objective, geometry, inputs or scope cannot silently reuse an incompatible finished result/checkpoint.

## 2. Spatial regions from geometry and route choices

**Change:** eliminate bounds introduced only by example waypoints, including Real11X728.9 and its arbitrary terminal-Z envelope. Expand across neighboring free cells consistent with the selected route; merely removing the waypoint clip leaves another artificial limit at selected-cellX728.7. Keep real scene coverage and other block constraints. Do not turn rectangle unions into intersections or physical walls along cell seams.

The B6 route distinguishes right X>=731.3, central729.7..731.3 with Z<=-1095.3, and left X<=729.7 where vertically relevant. Leftward extent is not fixed to an example point. Current scene allows centers toX726.3; changing scene coverage is a separate explicit input. Record whether each bound comes from an obstacle, selected route decision, explicit task constraint or coverage. Make scoped route checks use the selected requirements, not the entire future reference corridor.

**Acceptance:** a physically valid route extending farther left than the helper points remains available; B1's union/corner and normal edge jumps remain valid. Real11 B6's actual forbidden contact is still rejected for a complete candidate.

## 3. One shared interaction-reachability helper

**Change:** precompute conservative phase-aware position/velocity envelopes per fixed movement/constraint context. Reuse the compiler's acceleration/friction and existing interval/travel mechanisms. Query the existing interaction constraint bundles with one helper:

```text
interaction_reachability(envelopes, interaction_constraints)
    -> ruled_out(reason, tick, phase, separating_bound)
    or not_ruled_out
```

Use it for target opportunities, unwanted contacts and selected avoid-side/timing variants. The helper has no separate target/obstacle policy. Caller actions differ: impossible mandatory contact rejects the candidate; impossible unwanted contact needs no avoidance; an impossible avoid-side branch is discarded while other branches remain. Unsupported/loose cases remain not ruled out. Use full input sweeps and correct Y->X->Z phases, not endpoint-only checks or an optimized witness as the envelope.

Start with the cheap compiled-magnitude/friction travel bound and vertical overlap tests. Reuse/tighten interval propagation with selected contact bounds when justified. Cache by context; do not reuse one scalar schedule's envelope for a step/ceiling alternative that changes it. No IPOPT call is needed for the screen.

**Acceptance:** immediate0 Real11 needs no additional B2 avoidance (padded early separation~0.077698), while slow5 retains potentially relevant inputs15..19. Keep planned B2 landing rows. Small exhaustible controls lose no feasible interaction, including contact tangencies and finite-phase cases. See [shared interface/evidence](OBSTACLE_RELEVANCE.md).

## 4. Coherent constraint variants and useful repair scheduling

**Change:** use shared reachability to find relevant interaction windows, then select the existing side/timing/contact constraints before each complete candidate call. Eliminate vertically irrelevant late B2 exit gates; select actual B6 passage alternatives for a complete Real11 model. Use the common specification to deliberately omit future B6 only in preliminary guidance. Continuous facings remain free.

A queue item means one fixed input+constraint alternative. Assemble all selected mandatory rows and finite contacts; do not send the known incomplete equivalent of the old24 B2 variants and wait for the checker to rediscover B6. Do not use optional B7 as an obligatory backward landing envelope. Existing guide trajectories may rank variants but cannot provide hard reach bounds.

Retain iterative feedback where it informs later choices or demonstrated coupling. An unexpected violation of a claimed modeled requirement is logged as a missing-constraint/model discrepancy. Any resulting alternate choices are explicitly assembled before their next call. Numerical seed retries are separately identified.

Keep inherited work only in compatible contexts. Give the current candidate's complete active constraints and useful current guesses work alongside genuinely useful older alternatives. "Base problem" means the current specified problem, not an empty set that ignores active obstacles. Prevent obsolete B2 repairs and stale seeds from starving a new B6 decision. Preserve distinct pending alternatives and incumbents.

**Acceptance:** no dispatch of complete candidates with a known relevant B6 requirement missing. The saved old24-call trace becomes a regression for exact supplied rows/scope rather than a cap-inflation target. Preserve a validated incumbent across invalid polish, errors and cancellation; keep legitimate early B2 avoidance where the shared screen cannot exclude it. See [constraint/scope details](CORRIDOR_CONSTRAINT_FIX_PLAN.md).

## 5. Comparison protections and prior processing order, superseded

**Change:** extract position, stored velocity, schedule, counts and phase/context from one accepted witness. Use the existing common direction to obtain forward position, forward velocity and horizontal speed magnitude. Do not mix witnesses or add velocity-objective solves.

Absolute-clock comparisons require matching ground/air/jump schedules and compatible route/phase. Preserve a candidate if position is within the existing progress margin or ahead, forward velocity is greater, or horizontal speed is greater. These are OR protections. Across different schedules do not infer inferiority from absolute-clock position/velocity; record fewer-run/more-jump protections and preserve meaningful alternatives for event/continuation comparison. Equal counts are not schedule equality. Missing data is unknown/incomparable, not zero. Final fully accepted results use the actual requested objective.

Process available candidates by `(elapsed inputs, -effective grounded jumps, grounded nonjump inputs, stable ID)`. No support-progress or feasibility-first hook may silently take precedence. Keep equal/close time/progress alternatives. Preserve eligible overflow as deferred work; a protection cannot be undone by a resource-cap eviction. Derive effective jumps/runs from the composed inputs, including outer runs; ignored airborne jump presses do not increase the count.

**Acceptance:** greater forward velocity, greater speed, close position, schedule differences and unavailable metadata all get the agreed treatment. Preserve the actual Real110/8 setup tradeoff. A deferred winner is automatically continued even when another active branch has found an incumbent. See [precise policy/tests](STATE_PROTECTION_IMPLEMENTATION_PLAN.md).

## 6. Resume all unfinished work, including generation

**Change:** save each final candidate's outgoing pending/seen/unresolved work, in-flight branch, incumbent/guesses, context and detailed records. Checkpoint branch transitions atomically and restore the complete terminal program directly. Do not resume from the old55-input parent when64-input work exists. Generated/seen keys are not the same as completed outcomes. Cache finished branch results only in an exact compatible context; record intentional numerical-unknown retries.

Persist active/deferred candidate queues, comparison records, parent/window positions and stable priorities. Keep generator iterators alive across batches rather than closing after20 candidates. Refactor to persist the relevant symbolic pending/ready/cursor/dedup state across processes, without serializing Python generator frames. Preserve useful candidates hidden behind earlier raw-choice/selection limits; count generated, screened, deduplicated, emitted, deferred and dispatched work separately. Work limits can stop with pending state, not silently delete it.

Continue with fresh session deadlines and cumulative work accounting. Preserve incumbents while exploring additional eligible work. Mark old report recovery as reconstruction where outgoing state is absent. Do not claim exact continuation of unavailable state or serialize IPOPT's internal Hessian/iterations. Re-solve from the original state, using guesses only as guesses.

**Acceptance:** deterministic split-run versus uninterrupted continuation tests cover inner branches, outer generation, deferred work, in-flight cancellation and accepted incumbents. Exact compatible completed requests are not dispatched again. The controlled Real11 outgoing64 frontier resumes directly; old18 finals do not consume another335 requests through a restart. The useful B3-wait continuation previously hidden at proposal125/135 becomes reachable through continued generation at unchanged active batch sizes. This requires generation continuation, not only terminal persistence. See [persistence details](SEARCH_CONTINUATION_FIX_PLAN.md).

## 7. Reject provably dead prefixes

**Change:** derive outstanding mandatory task requirements and use the same interaction helper with existing event builders before scoring/retention. Initially reject a prefix only when another contact is required but every possible first interaction is excluded over the remaining horizon. Free-air Y before a first event can be known, but a possible earlier contact can change later height and must not be skipped in the reasoning.

Cover enabled normal/stepping/ceiling families, self jumps, waits and intermediate supports. No direct transition to the next required block is not a death certificate. A truncated catalogue, deadline, unsupported case or numerical unknown remains unknown/not ruled out. Do not use one accepted horizontal endpoint to represent every facing history.

If all mandatory contacts are done, keep valid airborne continuation to the fixed objective tick, including optional B7 landing. Do not add a required final contact implicitly. Record the actual exclusion and its geometry/horizon/movement-family scope separately from heuristic drops.

**Acceptance:** the saved dead falls are excluded with explicit evidence or honestly remain unknown. Recoverable lower-support, self-return, delayed, intermediate-support and terminal-air controls survive. Exhaustible small continuation controls lose no feasible completion. See [viability details](SEARCH_CONTINUATION_FIX_PLAN.md).

## Implementation boundaries and dependency order

1. **Common specification and terminal persistence:** establish stable context/scope fields from1 and implement the inner/final checkpoint part of6. This prevents losing work during later numerical experiments. Explicitly keep the rest of6 open.
2. **Shared screen, corrected regions and complete assembly:** implement3 and2, wire scoped validation from1, then4. Existing diagnostics identify any remaining missing requirement. Do not widen resource limits to hide a failure.
3. **Protected search continuation:** implement5 together with active/deferred and outer-generation continuation from6. Guards are incomplete if caps still erase protected candidates or generators restart.
4. **Conservative dead-prefix pruning:** implement7 using the same helper and complete-enough event coverage; integrate before scoring/retention. Do not infer impossibility from a capped probe.
5. **Full regression and experiments:** run the checks below, document all remaining limitations and update this plan/runbook/handoff at each implementation boundary. Commit completed boundaries. Completion of logging or a subset of these fixes is not completion of the consolidated plan.

## Regression and numerical acceptance

- Use focused standalone contracts for each change, then the appropriate wider standalone orchestration/physics suites. No mod Gradle gate or game launch. Reuse the shared Java compiler/scene helper.
- Exercise normal air, run ticks, landings, stepping and ceilings across multiple geometries. Include generic versions of Real11 failures rather than only matching its block IDs, ticks or recorded yaws. Keep the original state and threshold assumptions.
- In tractable finite domains, complete retained alternatives and candidates the heuristic would discard, then compare actual final time/objective. This is required to measure false pruning; passing unit tests or a single successful route does not establish heuristic optimality.
- Run Real11 cold with declared geometry and the user-selected initial0/8 alternatives, generating later decisions from existing builders. The saved full solution is an independent replay/reference control, not the seed/timing answer for cold discovery. Require valid recovery of the known chain or better accepted objective within the declared domain; classify any remaining unknown by missing generation, numerical uncertainty, route/scope failure, physical failure, heuristic exclusion or resource stop.
- Save every fixed numerical problem identity, modeled scope, chosen rows/passages, provenance of reach exclusions, comparison/protection decisions, dispatch/defer/resume sequence and independent replay of accepted full witnesses. Preserve incumbent and pending state at stops. Reinspect corridor-only failures with physical counterchecks; keep the check pending that evidence.
- Keep existing active sizes/margins and allow minutes or longer for useful work. Report generated/evaluated/deferred/pending counts, repeated versus intentional retry calls, time to first valid route and best final result. Do not claim a global optimum or completeness beyond the exhausted finite domain.

## Still deferred

Global objective/lower-bound machinery, mixed-input/disk model research, inertia-threshold pattern search, automatic redesign of route directions/clustering, and expansion to every spatial route are not part of these fixes. More sophisticated future scoring or reopening policies can be revisited using the new counterexample logs. The conservative travel envelope in3 is a feasibility/relevance bound, not a restart of the deferred global-bound project. Previously fixed bugs and the implemented corridor logger are preserved, not reimplemented.
