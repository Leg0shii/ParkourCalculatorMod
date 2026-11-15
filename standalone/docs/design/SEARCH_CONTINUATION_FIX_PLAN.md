# Resume unfinished solves and reject provably dead prefixes

Current consolidated implementation scope/order: [ORCHESTRATOR_IMPLEMENTATION_PLAN.md](ORCHESTRATOR_IMPLEMENTATION_PLAN.md). This document supplies supporting detail; all workstreams included by the consolidated plan remain required even where an older section describes a separate implementation slice.

2026-11-02. Both fixes are implemented in the standalone checkpoint orchestrator, including outer symbolic-generation continuation. Details and remaining acceptance limits are in [the consolidated status](ORCHESTRATOR_IMPLEMENTATION_PLAN.md#implemented-status) and [implementation results](experiments/orchestrator-fixes/README.md). The diagnosis and planned acceptance criteria below are retained as the design record. No earlier facings are frozen and no candidate caps were increased.

## Terminology

The inner repair search is the current orchestrator loop over constraint variants for one fixed movement program. It can add or change selected avoidance/corridor rows after a validation failure, then call IPOPT again from the original state with free facings. Each variant is a different fixed problem, not another IPOPT iteration. repairState means saved search progress: pending variants, generated/seen keys, unresolved numerical variants, incumbent/warm guesses and compatibility context. Seen keys include generated queued variants, not only completed requests. The term describes the existing lazy orchestration and does not endorse rejecting a preliminary solve for a deliberately omitted future requirement. The scope/upfront-constraint fixes may reduce the loop, but any remaining search must still resume correctly.

## A. Preserve actual terminal repair progress

### Current gap

`solve_contact_branches` already returns pending/seen/unresolved branches, an incumbent, warm guesses and a compatibility context. Intermediate unknown histories can carry this state. The terminal records in `checkpoint_search.py` instead retain the incoming `decision_program` and summary but lose the outgoing terminal repair state. `fixed_clock_experiment.py --resume` loads retained parents from the last comparison window and generates finals again.

On Real11 this repeats18 final candidate IDs and spends335 of383 resumed requests on those old candidates. A controlled24-call final evaluation leaves174 pending repairs, but the old outer resume starts from the incoming55-input parent's125, losing the64-input progress. See [the numerical investigation](experiments/linkcraft11-opening/OTHER_FAILURES.md).

### First implementation slice

1. Give each terminal program a stable identity from its full physical/discrete specification, independent of mutable repair progress, warm guesses and session counters. Include geometry/body/physics, original state, movement/contact schedule, horizon, objective, selected requirements and route/scope in the compatibility identity. Reuse existing physical/context key machinery; do not use a progress-dependent search key as the persistent candidate identity.
2. Save an explicit terminal-work entry containing the program and the latest outgoing `result.repairState`, including pending branches, seen keys, unresolved numerical requests, incumbent branch/witness and warm guesses. Save the outgoing state even when a feasible incumbent exists but alternatives remain. Preserve the exact branch constraints, selected passage/hit choices and failure diagnostics. Distinguish branch exhausted, feasible-with-pending, unresolved and queued.
3. Persist after each completed repair branch, not only after a comparison window or the entire run. A popped branch must remain identifiable as in-flight until its result is recorded; a crash/cancellation restores it as unresolved rather than losing it. Use the existing atomic report-writing pattern and a versioned checkpoint. Keep cumulative and current-session work counters separate; a new session receives a new deadline rather than restoring an expired timestamp.
4. Add a terminal-work resume path to the engine and CLI, separate from the existing earlier-prefix `initial_programs` path. Restore saved complete programs and their outgoing repair state directly. Continue their actual unfinished work under the normal ordering. Cache completed branch outcomes in an exact compatible context; do not dispatch them again as fresh work. An unresolved numerical request may be retried deliberately, with retry identity/reason logged. Preserve the best validated incumbent across interruption, retries and later failures.
5. For a changed program, geometry, scope or objective, do not silently treat the saved frontier/results as exact continuation. Reuse only what the existing compatibility rules justify, otherwise use legal guesses as seeds and revalidate. A seed is never a fixed arrival state or an accepted solution merely because it was previously successful.
6. Keep useful newly selected work from being starved by old repairs. The corridor/upfront-constraint fix changes which relevant branches exist; obsolete late B2 exit alternatives must not block the new B6 decision. Persistence alone makes an unhelpful queue resumable, not effective. Preserve distinct pending alternatives and the user's candidate processing policy.

This saves orchestration progress between fixed IPOPT requests. It does not serialize IPOPT's internal iteration/Hessian state. A request interrupted mid-solve may have to restart, using a returned guess when available; completed requests and the surrounding search frontier should not restart.

### Separate outer-generation requirement

Saving emitted final programs fixes the demonstrated lost-terminal-progress bug. It does not preserve a candidate that the generator never emitted. Keep generators alive across batches within one process; subsequently persist versioned pending/ready prefixes, dedup/serial state or a deterministic enumeration continuation with cached outcomes. Do not call this full-search resumption until both terminal work and outer generation resume. Legacy reports lacking outgoing final state can support an explicitly labelled reconstruction from old parents, not exact continuation.

### Modules and acceptance

- `block_contacts.py`: progress/checkpoint callback after branch transitions, saved in-flight branch, existing inner resume and diagnostic preservation.
- `repair_state.py`: reuse/strengthen exact context identity and compatibility; distinguish reusable guesses from reusable completed outcomes.
- `cell_routes/checkpoint_search.py`: terminal-work records, direct continuation queue, stable identities and retained incumbents.
- `cell_routes/fixed_clock_experiment.py` and report writer: atomic saves, exact terminal restore, explicit legacy reconstruction.
- Existing repair/resume tests: split-run versus uninterrupted request/outcome sequence under deterministic fake workers; save/load cancellation with an in-flight branch; accepted incumbent plus pending work; incompatible objective/geometry/scope; deliberate unknown retry; no fresh dispatch of completed branches.
- Real11 control: resume the outgoing64-input repair frontier, not its55-input parent, and log which pending branch each call continues. No expected335-request repeat expenditure. Do not require bit-identical wall-clock behavior from IPOPT or claim persistence alone fixes repair ordering.

## B. Reject genuinely dead prefixes

### Current gap and definition

Some accepted free-fall prefixes score well horizontally but cannot complete mandatory later interactions. The near-goal run retained a44-input history at feetY=-8.606; two other retained28-input histories were falling below usable supports. Existing normal/stepping catalogue probes found no next finite events, but their configured generation limits are not by themselves a universal impossibility proof.

Dead means no completion of the declared remaining task within the remaining horizon, allowed movement families and supplied geometry. It does not mean poor progress, no numerical solution under a budget, or no direct jump to the next required block.

### Conservative first implementation

1. Derive the outstanding task requirements from the current decision history. Do not add an implicit final landing: after all required Real11 supports are completed, an airborne fixed-clock continuation can be valid without another contact. Do not drop such a history merely because no future landing exists.
2. Before expensive scoring and again before retention, inspect the fixed scalar boundary and possible next interaction windows over the remaining horizon. In airborne free motion, Y evolution before the first new contact is known independently of facing. Include every allowed way a first event could occur: normal contacts, self returns, run/jump delays where grounded, steps, ceilings and any other enabled family. Ladders/water/intentional wall hits remain outside the current declared scope. Do not extrapolate one no-contact Y trace through a possible earlier event that could change the future.
3. Use the same shared `interaction_reachability(envelopes, interaction_constraints)` helper selected for target/avoid screening. Use all compatible horizontal possibilities from the original state and selected prefix constraints, not one solved endpoint. Reuse existing event builders rather than creating special dead-prefix physics.
4. Reject only with an explicit necessary-condition exclusion. A useful first case is: another physical contact is mandatory, but every possible first interaction is excluded throughout the remaining horizon. A falling player already below all possible contacts with no enabled mechanism to regain height is one generic instance. Record excluded event windows and the vertical/reachability reason.
5. If any first event remains possible, keep the prefix. An optimistic event graph can later reject disconnected mandatory goals, but only if it overapproximates every allowed continuation, including intermediate supports, self jumps, waits and coupled stepping events. A lack of a direct transition to the next required block is insufficient.
6. A generator cap, deadline, unsupported movement test or incomplete event enumeration yields unknown/not-ruled-out, not dead. Never infer death from zero returned choices in a truncated catalogue. Cache by the discrete prefix/model/geometry, outstanding requirements and remaining horizon. Record proof exclusions separately from resource stops and heuristic rank decisions.

### Acceptance

- Replay the three saved dead-end histories: obtain a genuine exclusion for the enabled finite task, or honestly return unknown if the check cannot certify it.
- Keep a falling history that can land on a lower support and then jump/step to the required continuation.
- Keep a prefix needing a self jump, delay or intermediate support before the next required block.
- Keep a valid airborne terminal objective with no outstanding contact requirement, including optional B7 landing.
- Different earlier facings/arrival velocities represented by the same prefix remain covered; one unfavorable witness cannot kill the history.
- Cutoff/unsupported catalogue controls return unknown. Small exhaustible movement controls have no feasible completion removed.
- Re-run Real11 with unchanged margins and active sizes; record how many dead-prefix exclusions avoid numerical calls/retention slots and independently replay every accepted final witness.

## Order and limits

Implement terminal outgoing-state persistence first, because it closes a concrete loss of work using existing machinery. Implement the shared interaction screen once, then use it for conservative prefix viability and target/avoid filtering. Scope-aware corridor/constraint fixes remain in [their own plan](CORRIDOR_CONSTRAINT_FIX_PLAN.md). Add outer-generation continuation explicitly after the first terminal slice; it is not silently included in that milestone. No global objective bound, new weighted score or arbitrary cap increase is part of these fixes.
