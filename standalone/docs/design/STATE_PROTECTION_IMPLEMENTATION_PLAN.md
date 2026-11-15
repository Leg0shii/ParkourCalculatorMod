# Implementation plan: velocity and schedule protection

2026-11-03 scheduling update: [the consolidated DFS/interaction plan](DFS_INTERACTION_IMPLEMENTATION_PLAN.md) supersedes the old global prefix-length processing key/window traversal below. Preserve the comparison protections as specified, but compare time at a shared spatial goal and explore the selected branch in depth.

Current consolidated implementation scope/order: [ORCHESTRATOR_IMPLEMENTATION_PLAN.md](ORCHESTRATOR_IMPLEMENTATION_PLAN.md). This document supplies supporting detail; all workstreams included by the consolidated plan remain required even where an older section describes a separate implementation slice.

2026-11-02. The new protections and saved deferrals are implemented on isolated branch `research/ipopt-cell-routes`. See [the consolidated status](ORCHESTRATOR_IMPLEMENTATION_PLAN.md#implemented-status) and [results](experiments/orchestrator-fixes/README.md). Existing IPOPT equations, movement/constraint builders, full-original-state re-solves, variable earlier facings and normal momentum with zero thresholds are retained. The diagnosis and original acceptance criteria below remain the design record.

Subsequent [controlled failure investigation](experiments/linkcraft11-opening/OTHER_FAILURES.md) demonstrates additional blockers this protection plan alone cannot fix: useful earlier timing choices miss the proposal batch, dead discrete falls occupy retention, inherited geometry repairs starve the same fixed program's base problem, and terminal repair progress is not restored by the55-input resume. Address or separately track these in the Real11 acceptance milestone. In particular, a same8-second/24-call ordering control recovers a valid solution without changing movement or unique repair-branch pool; do not attribute every remaining unknown to a low budget. The plan's persistence scope must include final per-program repair state/cursors and fair fresh-base scheduling, not only saved intermediate candidate records.

Further improvement recommendation after the user's early-stop/pruning question: protect emitted histories and continue the still-unemitted generator frontier too. Within a run, keep the generator alive across evaluated batches rather than closing it at20; across saved runs restore pending/ready prefixes or deterministic enumeration cursors and cached results. Keep existing active sizes/margins, and preserve the user's(n,-J,R) evaluation order. The earlier narrow emitted-pool protection scope below remains a useful implementation slice, but is not sufficient to close the observed proposal125/135 coverage failure. Track this additional continuation work explicitly rather than claiming all search state is suspended when only comparison-window parents are saved. Safe finite future-event viability rejection and fair base/warm repair work complete the evidence-backed follow-ups. This historical recommendation is now implemented within the conservative scope summarized above.

## 1. Keep the current margins and define the scope

- Progress margin: existing0.25 blocks. The proposed0.05 value is not adopted.
- Spatial-goal time margin: existing1 tick.
- Use the existing arithmetic tolerance for floating comparisons. Introduce no tunable velocity margin and no weighted position/velocity objective.
- Protect a candidate from intermediate heuristic exclusion when it has greater forward velocity, greater horizontal speed magnitude, or position within the existing progress margin of the peer or farther forward.
- Absolute-clock state comparisons require matching ground/air/jump schedules and compatible route/contact context. Across schedules protect fewer-run OR more-jump candidates, and preserve the already selected0/8 setup alternatives generically.
- Processing order is `(total elapsed inputs, -effective jump count, grounded nonjump count, stable candidate ID)`.

The guards apply to intermediate heuristic pruning. A completed physically valid final solution is still judged by the actual requested terminal objective, including across different schedules. Feasibility screens and exact equivalence remain valid rejection mechanisms. A missing/unknown measurement never counts as zero or inferiority.

## 2. Extract one consistent state record per accepted witness

Extend existing comparison records; reuse stored positions/velocities rather than adding numerical calls. At the predeclared comparison stateh, record:

| Field | Definition/source |
| --- | --- |
| full position and velocity | accepted `positions[h]` and stored `velocity[h]`, both X/Z |
| forward position s | `d dot(p-a)` for shared unit directiond and anchora |
| forward velocity u | `d dot v` |
| horizontal speed m | `hypot(vx,vz)`; vertical velocity is not part of this magnitude |
| lateral position/velocity | projections on perpendicular(-d_z,d_x), kept for context/diagnosis |
| schedule | ordered run/jump/air classification through the compared prefix |
| counts R,J | grounded nonjump inputs and effective grounded jump inputs; include composed outer inputs |
| phase/context | horizon, route/direction/anchor, current movement/contact mode, selected vertical/contact event types/offsets/owners and accepted Y/vy phase |
| provenance | accepted witness/problem identity and validation status |

Position and velocity must come from the same accepted trajectory. Do not take position from the clock solve and velocity from its different spatial-probe witness. Keep event-aligned arrival records separate, with their actual arrival times. Retain the full ordered schedule, or its losslessly recoverable hash plus data; equal R/J counts are not schedule equality. Ignored airborne jump presses do not increaseJ.

Use the direction already declared for the comparison, identical for all candidates in that context. This change does not introduce automatic segment selection, alter Real11's early reference vectors, or rank each candidate in its own favorable direction. Direction-selection improvements can be measured separately. The first implementation reads velocity from existing position-optimized witnesses; extra velocity-objective solves and continuous p/v-frontier sampling remain outside this change.

## 3. Apply protections before the existing worse-than-peer test

For a candidate c and a proposed pruning peer b, return a structured decision rather than a bare boolean. The protections are OR guards: any applicable one blocks that peer's heuristic exclusion of c.

```text
if required comparison metadata or accepted state is missing:
    keep as unknown/incomparable

if schedules differ:
    record fewer-runs protection if R(c) < R(b)
    record more-jumps protection if J(c) > J(b)
    do not use their absolute-clock p/v scores for exclusion
    retain for shared-event/continuation evaluation

if clock, schedule or route/contact context is incompatible:
    keep as incomparable

if s(c) >= s(b) - existing_progress_margin:
    keep: position_close_or_ahead
if u(c) > u(b), beyond arithmetic tolerance:
    keep: higher_forward_velocity
if m(c) > m(b), beyond arithmetic tolerance:
    keep: higher_horizontal_speed

otherwise:
    run the existing complete-known time/progress peer test
    using unchanged one-tick and0.25-block margins
    drop only if one same peer supplies all required comparisons
```

The position guard is an explicit veto: an arrival-time disadvantage cannot bypass it. Faster arrival still protects a genuine time/progress tradeoff under the existing peer test. A gain in horizontal speed protects even when its direction is less favorable, as the user requested; do not silently replace the magnitude guard with the directional guard. Greater velocity is retention evidence, not proof that the candidate will win.

If a record has multiple required comparison contexts, preserve the existing complete-matching-evidence requirement and apply protections within each context. A protection in any required shared context blocks that peer's exclusion. Never combine one context's position with another's velocity or use unrelated fastest/farthest peers to manufacture dominance.

Different-schedule candidates with equal R/J counts still cannot be dropped from an absolute-clock comparison. For this first implementation, shared-event arrival states provide useful time/state labels and ordering evidence; they do not gain a new aggressive cross-schedule dominance test. Explicit infeasibility, exact equivalence or a fully evaluated terminal result can still settle a branch. This keeps the policy finite over the generated domain without inventing new score weights.

Legacy saved records without velocity/schedule data must either reconstruct them from their accepted witness/program or remain incomparable. Never silently use the old position-only pruning path for missing new metadata.

## 4. Make protection survive retention limits

Do not solve the problem by increasing beam widths or deleting an eligible protected history under a different reason. Keep the current active work batch size, but treat eligible overflow as persistent deferred work. Preserve each emitted/evaluated deferred candidate's symbolic program, parent/window, accepted seed, comparison records, repair state, priority and protection reasons.

At every scheduling opportunity, select the next available history by the same processing key across ready and deferred work, resuming it from its saved discrete prefix/window. A higher-priority deferred history must run before a lower-priority active branch's newly proposed continuation. The active limit bounds work in flight, not permanent eligibility. The continuation still re-solves from the original initial state with all earlier facings free. Save deferred work at deadlines and report pending coverage. A feasible incumbent is retained while other eligible work is considered; finding one incumbent does not erase the pending alternatives.

The old scored/unknown slot limits must not become permanent losses of protected candidates. They may govern active work allocation, but cannot silently override the selected processing order or remove an overflow history. Full exploration of never-emitted generator choices is a separate existing coverage limitation; this change guarantees persistence for the candidate pool it actually emits/evaluates, not every ungenerated legal route.

Use the existing candidate/repair persistence where possible, extending it with an explicit active/deferred cursor. The existing one-item failure rescue is insufficient because it can miss a protected history or stop revisiting after a successful solve. The queue must resume deferred work during normal search too.

Terminal persistence must store the outgoing result's repair state rather than the `decision_program` copy of its incoming parent state. Concrete Real11 candidate1a006630c0... consumes24 requests and gets24 again on the outer resume from its55-input checkpoint; candidate2396e5d72c... consumes16 before a deadline, then restarts with24. A controlled true continuation of the former starts with174 pending64-input repairs, not the parent's125. Save pending/seen/unresolved branches, accepted incumbent/seeds and outer cursor under a compatible physical-program/context key. This is orchestration-work persistence; it does not require storing IPOPT's internal optimizer iterations. See [the concrete examples](experiments/linkcraft11-opening/OTHER_FAILURES.md#concrete-examples-of-the-lost-terminal-progress).

The explicit implementation slices and viability proof conditions are now in [SEARCH_CONTINUATION_FIX_PLAN.md](SEARCH_CONTINUATION_FIX_PLAN.md). Terminal outgoing-state persistence does not by itself preserve un-emitted generation, and a capped no-event catalogue is not a dead-prefix certificate. Reuse the single shared interaction helper; preserve optional-terminal-air continuations and original-state freedom.

## 5. Use the exact evaluation order

Sort generated ready candidates and resumed work by `(n,-J,R,id)` before numerical evaluation; use the same ordering for selected parent work. At a common clock n ties. At a shared spatial event the different elapsed times remain primary. Keep stable IDs through save/load so ordering is deterministic.

No measured spatial-probe arrival time, position score, feasibility-known flag or support-progress priority may secretly precede those three keys. The previous Real11 support-progress hook must not override this policy. Existing necessary generation screens and completion-preserving queues remain; ordering is over generated available candidates, not a claim that ungenerated possibilities are globally sorted. Preserve generation-limit diagnostics.

## 6. Change the existing modules rather than building another search

| Location | Change |
| --- | --- |
| `cell_routes/time_progress.py` | enrich clock state records, comparison guards and auditable protection reasons |
| `cell_routes/checkpoint_search.py` | attach schedule/context metadata, preserve and drain deferred candidates, preserve accepted incumbents |
| `cell_routes/checkpoints.py` and existing profile helpers | derive schedule/count summaries from existing composed inputs; retain original-state semantics |
| existing generation/frontier scheduling | apply ready-candidate processing key without replacing builders or necessary screens |
| `cell_routes/fixed_clock_experiment.py` / existing experiment drivers | save new comparison/deferred metadata and reproducible processing trace |
| existing time-progress/resume/audit tests | verify behavior, persistence, ordering and complete continuation quality |

Add a small shared pure helper for counts/signatures if several callers need it. Do not duplicate movement classification in each queue, change IPOPT equations or add velocity into its objective in this work.

## 7. Audit every retention decision

Record candidate and peer IDs, comparison key, compatibility verdict, s/u/m values, actual schedule signatures, R/J counts, unchanged margins and all applicable keep reasons. Distinguish `protected`, `incomparable`, `markedly_worse`, `active`, `deferred`, `infeasible` and `exact_equivalent`. Record resource stops separately.

For the Real11 rerun, also save each numerical request's fixed discrete program and selected constraints/contact branches, problem hash, validation verdict and processing/defer/resume sequence. The previous checkpoint-only summaries lost subattempt detail; do not reproduce that audit gap. Record accepted and rejected solver returns separately so scalar feasibility cannot be mistaken for scene acceptance.

## 8. Acceptance tests before a numerical rerun

1. Matching schedule, worse position/time but greater forward velocity: retain.
2. Matching schedule, lower forward velocity but greater X/Z speed norm: retain.
3. Position loss at/below0.25: retain even if the spatial-goal time is worse; test arithmetic-tolerance boundaries. A clearly behind, slower, otherwise inferior peer can still be dropped under the existing rule.
4. Faster spatial-goal time versus better state: retain the tradeoff. Missing velocity/schedule/validation: no inferred inferiority.
5. Same absolute clock but different run/jump timing, including equal counts: no cross-phase exclusion.
6. Different schedule, fewer runs OR more actual jumps: record the corresponding protections independently. Test a run/jump tradeoff and ignored air jump presses.
7. Recorded Real11 state10:0 has key(10,-1,0),8 has(10,-1,8);0 goes first and is never marked worse than8. AtB2:8(17,-1,8) goes before0(21,-2,0), while0 remains protected. Neither requires fixture-specific IDs.
8. More eligible candidates than the active cap: all emitted protected histories persist; a deferred winner is automatically resumed and can replace an incumbent. Save/load, deadline and a successful active branch must not lose it.
9. Exact processing-key precedence and stable ties, including cases where the old spatial-time or support-progress sort would disagree.
10. Position translation and joint rotation of p/v/d leave comparisons invariant. Full state comes from one witness; embedded ground runs are counted even when the inner run allocation is zero. Original start and all earlier facing variables remain unfixed.

Run focused standalone contracts and the broader standalone gate appropriate to these Python search changes, keeping documented unrelated environment/ladder exclusions explicit. No mod Gradle gate or game launch is required for this standalone scope.

## 9. Numerical acceptance and evidence

First reprocess the saved Real11 pool to show exactly which historical exclusions are prevented and why, without IPOPT calls. In particular, the old0-versus8 state10 loss is a schedule/run-count protection case; the velocity and proximity guards alone do not rescue it. Do not claim a velocity fix explains that example.

Then rerun the existing short controls and Real11 with the same geometry, initial state, horizon, configured directions, physical checks and current margins. Keep existing active batch sizes and report all generated/emitted/active/deferred/unknown work. Compare accepted final time/objective, not only unit-test pass counts. Both setup families must receive meaningful continuation work; reference checks continue deferred/would-be-discarded histories in a declared finite domain. Extra elapsed time to drain saved useful work is allowed; do not arbitrarily raise generation/retention counts to hide a failure.

Replay every accepted witness with the existing scalar and finite-scene validators. Require recovery of the known valid Real1164-tick chain and compare against its savedZ=-1093.2675533415547, while reporting numerical/coverage unknowns honestly. If cold discovery still fails, distinguish missing generation, unresolved fixed programs, scheduling and heuristic loss before claiming the protection policy is sufficient. Unchanged solver assumptions do not turn unknown into impossible.

Completion means the guards are wired into the runtime, protected overflow is actually processed/resumable, the selected order is observed, meaningful regressions pass and Real11 has a reviewable full trace. Documentation alone is not that implementation milestone.
