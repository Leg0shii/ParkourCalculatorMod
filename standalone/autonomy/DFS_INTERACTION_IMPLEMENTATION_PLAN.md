# Consolidated plan: depth-first decision search and early interaction eligibility

2026-11-03. This is the current authoritative implementation plan requested by the user. It combines the traversal correction with [the interaction-generation fix](GENERATION_RELEVANCE_PLAN.md). The previous shortest-prefix processing rule and checkpoint-window drain requirement are superseded. Runtime implementation has not started under this plan. Continue in the existing isolated research/ipopt-cell-routes worktree; no other-agent work is requested.

## 1. Intended search behavior

Latest Real11 task correction, 2026-11-03: the requested terminal objective is now earliest actual B7 landing, then maximum Z at that same tick. Support order must emerge from generated feasible decisions, not a required B2/B3/B4/B5 fixture list. Existing0/8 and B2 timing examples below are historical controls, not an exhaustive initial domain or a prescribed support chain. Reuse existing automatic routing, opportunity-window, segment-direction and landing/region-probe components; automatic decision-to-goal/range integration remains work. The stored B6 box omits the user's reported rightward extension, whose exact bounds are still unknown. This correction supersedes fixed64/optional-B7 assumptions without rewriting old measurements. Intentional wall-hit search stays deferred.

For one chosen spatial route, compare the alternatives for the current decision at one common spatial goal, select the most promising candidate, and follow its decisions in depth toward a fully accepted terminal solve. Save sibling alternatives for backtracking. Do not explore all candidates to one absolute-clock checkpoint before letting any candidate proceed.

For the historical Real110/8 control, the common comparison is landing on B2, not how many inputs have been simulated so far. Existing accepted comparison witnesses reach B2 at21 for P01's self-hop setup and17 for P02's delayed setup. Therefore P02 is the first family to follow in that two-family control. P01 remains a saved root sibling. These times are evidence for expected ordering, not hardcoded timings or proofs of global minimum arrival. Broader generated alternatives must not be excluded merely because they are absent from this control.

Within the active family, each next decision is handled the same way: compare its relevant local alternatives at a shared point, choose one, and descend. Once a full path is solved, backtrack to the nearest unresolved alternative and eventually the other initial family. Finding a first incumbent does not delete remaining work. A deadline saves the active path and siblings instead of restarting or silently declaring them inferior.

## 2. Compare candidates at the same point

Each comparison frame explicitly identifies its spatial goal: for example the next required landing/support or an outgoing portal/region after a turn. All sibling timing comparisons must share that goal and task context. For a shared spatial goal, use `(achieved absolute arrival time, -effective jump count, run count, stable identity)`. Thus earlier arrival comes first, then more jumps and fewer runs for ties. The earlier fewest-ticks rule is not a preference for shorter partial histories.

Existing fixed-clock objectives remain available as secondary guidance where schedules/phases are compatible. They cannot silently replace arrival time or compare unrelated goals/phases. Retain current0.25-block/1-tick margins and agreed position/velocity/schedule protections. Keep close alternatives, incomparable outcomes and initial P01 fallback for later exploration. Final results are judged by the actual requested terminal objective: earliest actual B7 landing, then maximum Z at that same tick. A configured horizon is a search limit, not a forced terminal64 clock. The existing `candidate_rank` time-first ordering and landing-mode validation should be reused.

Do an initial scoring pass over the current decision alternatives; do not enumerate their Cartesian products with later decisions. Separate the work needed to obtain a validated local comparison from optional additional refinement of an already scored candidate. Extra facing polish or unvisited constraint alternatives remain saved work, not a prerequisite for every candidate to advance. Goal-local generation may require intermediate supports/self-hops, but must remain separate from outer full-route traversal.

An observed arrival is a feasible measurement, not an admissible lower bound or proof of earliest arrival. Missing/unknown comparisons remain explicit. If local generation or numerical scoring is suspended, preserve its iterator and unresolved alternatives; do not label a selected evaluated child the globally fastest unseen child.

## 3. Promote actual decision programs, then solve from the original state

The selected arrival result must carry its concrete discrete program and owned interaction constraints into the child frame. Do not store only the arrival score and then continue an unrelated open-air prefix which omits that arrival. For example an accepted B2 landing at17 supplies that selected landing/input schedule as the active P02 branch.

Only decisions are committed along the current branch. Every continuous re-solve still starts from the full original position/velocity and leaves all prior facings variable. Do not pin the saved witness's arrival X/Z/velocity or copy comparison-only goal/clock rows into permanent task constraints. Existing movement/contact/constraint builders and IPOPT equations remain unchanged. Normal momentum is carried with zero inertia thresholds.

Each actual fixed numerical alternative contains its selected inputs and complete in-scope constraints before IPOPT is called. A successful local witness or soft guide does not exempt the next solve from assembly/validation.

## 4. Implement a persistent DFS stack instead of a window barrier

A frame stores: parent/ancestor decision identity; route and shared comparison goal; local alternative generator/cursor; comparison results and provenance; ordered untried siblings; active child; numerical pending/in-flight/unresolved state; any remaining refinement for already scored candidates; exhausted/proven-dead/suspended status. Use pure serializable state and exact compatible contexts, reusing current persistence machinery.

Traversal:

1. Open the next decision frame on the active branch.
2. Generate relevant local alternatives and obtain their comparison measurements.
3. Select the best comparable child, preserving all eligible siblings and unresolved work.
4. Descend immediately to that child's next decision. Do not drain a global checkpoint pool or every optional repair/refinement source first.
5. At the true task endpoint, fully solve and independently validate the selected complete program; update the incumbent.
6. If the active choice is impossible/exhausted, or after a completed path, backtrack through the saved stack to the next alternative. A numerical unknown is suspended work, not a proof that a subtree is dead.

Remove shortest-prefix ordering as the global traversal policy. Local generation still has to organize unsolved proposals, but that internal work order must not preempt the selected active branch or be presented as a fastest-route comparison. Replace the current default checkpoint-drain path rather than adding a rollout mode that leaves the old behavior authoritative. Checkpoints can remain diagnostic/comparison records without imposing a breadth barrier.

Persist transitions before dispatch, after accepted local/terminal results and while backtracking. Preserve accepted incumbents and all eligible siblings at stops. Version changed search/generator schemas; old raw queues are historical evidence or explicitly reconstructed inputs, not silently exact new-stack continuations.

## 5. One interaction gate, before every consumer

For each block/tick/collision phase, compute conservative interaction eligibility in the actual fixed or proposed movement context before creating alternatives, block-derived rows, prerequisites or interaction-specific checks. If the interaction is provably unreachable, none of those consumers may introduce it. Unknown/not-ruled-out preserves coverage.

Reuse the existing shared helper, compiled coefficients and Y->X->Z sweep semantics. Context includes geometry/body, original state, selected inputs/scalar/contact history, existing decisions and scope; recompute when it changes. A guide trajectory is not a reach envelope. Safely above a block means the relevant swept phase is clear, not just one endpoint sample. Do not accidentally remove actual support/landing/contact requirements.

Apply the same eligibility result to normal, stepping/blip, ceiling, passage/contact builders and interaction-derived corridor checks. An alternate builder may not bypass it, and validation/truncation may not resurrect a discarded interaction. Preserve explicit task/route-region constraints in their declared scope. Keep physical acceptance validation; do not remove corridor checking wholesale as an unrelated policy change.

If a proposed required interaction cannot occur, discard that proposal. If a block cannot interact during a phase, generate no prevention choice or derived check for that phase. These follow from the one interaction invariant; do not create separate landing-versus-avoid relevance systems.

## 6. Generate only distinct relevant models; preserve ownership across scope

Generate face changes only where they change constraints on eligible states/phases. Canonicalize each obstacle's exact effective row sets before combining blocks, and canonicalize equivalent partial combinations before costly refinement. Include remaining resource/cap state and committed interaction metadata when relevant; do not merge different physics, scopes or future capabilities based on similar trajectories. Check previously handled exact complete keys before repeating expensive refinement.

Screen a whole proposal's necessary interactions before throwing away information through checkpoint projection. Attach lightweight ownership to existing interaction/row bundles. An uncommitted future event must not leave orphan hard approach conditions in a shorter prefix. An explicitly committed future choice retains its necessary earlier prerequisites and explicit scope. Task rows and conditions of modeled events remain intact.

P04/P05 regressions: the broader P03 context already excludes B4 in all84 phases through28. Their blip builder nevertheless creates B4 approach/contact conditions for inputs28/29; truncation removes the events and retains the state28 row. Both full proposals are rejected by the existing shared screen at29/X, while the truncated aliases pass. The fix must exclude that impossible interaction before constructing those aliases and leave the ordinary B2->B3 continuation available.

The P06/P07 duplicate examples are the same early-eligibility/canonicalization requirement: gate18/19 or20/21 can yield identical rows in clear gaps, and B3 has many inactive face prefixes before its only relevant states27/28. Preserve distinct actual models while removing repeated descriptions.

## 7. Pruning, backtracking and explicitly deferred work

Use proven unreachable interactions, inconsistent required conditions, genuine dead-prefix exclusions and exact model/state equivalence to eliminate work irreversibly. Use local timing/progress/state measurements to order exploration; retain protected/near alternatives and record any heuristic exclusions separately for complete regression audit. A later B2 arrival alone cannot prove P01 worse at the final objective.

A valid terminal incumbent enables whole-route comparisons and can support any already justified bounds, but does not create a new dominance certificate. No new global objective-bound project is required for this implementation. Do not turn unknowns, generation caps or IPOPT timeouts into infeasibility.

Reusing discrete decisions from a previously completed solve to initialize a sibling/subsequent solve is explicitly deferred. A branch inherits only its own chosen ancestors. Compatible existing facing guesses may remain initialization only, never hard state or facing constraints. Ladders, water, intentional wall-hit expansion and redesign of every spatial route remain out of scope.

## 8. Implementation sequence and modules

| Step | Change | Main locations |
| --- | --- | --- |
| 1 | Define shared goal-aware candidate/frame records, interaction ownership and exact context identities | solve_spec.py; existing comparison/frame records |
| 2 | Wire one early eligibility gate through every block-interaction consumer and projection path | reachability.py; movement_generation/open_movement; stepping/ceiling/contact builders; corridor adapter; movement_composition |
| 3 | Canonicalize meaningful per-block/partial constraint models before products/refinement | constraint_variants.py; block_contacts.py |
| 4 | Make goal-local scoring return the validated decision program, constraints and resumable work | comparison_goals.py; evaluate_program; existing score/protection helpers |
| 5 | Replace global window draining and shortest-prefix priority with the persistent DFS stack | checkpoint_search.py; movement_frontier.py; decision_windows.py |
| 6 | Update CLI/resume, complete request logs and independent validation/audits | fixed_clock_experiment.py; report writers; existing audit tooling |

Avoid a second physics implementation or a new solver API. Build the eligibility contract before integration so the DFS cannot reproduce the old explosion inside its first frame. Commit at coherent implementation/test boundaries in the existing isolated worktree.

## 9. Acceptance and evidence

- Deterministic traversal test: both first-decision candidates are compared at the same point; the earlier arrival is selected; it reaches a terminal solve before its root sibling is continued. Backtracking subsequently reaches that sibling. Optional refinement and absolute-clock diagnostics cannot recreate a breadth barrier.
- Real11 ordering regression: generated/validated B2 arrival17 for P02 precedes P01 arrival21 in exploration, even though the former uses more runs/fewer jumps. P01 remains available. No recorded schedule/yaw may be injected into the cold run.
- Child-program regression: a successful spatial probe's selected landing and inputs really occur in the continued branch; original-state facings remain variable.
- Event-scope/canonicalization controls: P04/P05 excluded before orphan rows exist; reachable adjacent blips, ceilings, near tangencies, required support and true phase crossings remain valid. Small exhaustible cases preserve the union of distinct feasible models and relevant cap states.
- No impossible block interaction may reappear through a constraint name, alternate builder, route signature or truncated owner. Cover above-block and horizontally unreachable cases through the same helper.
- Save/load tests preserve active depth, scored/unknown siblings, generator/refinement cursors, in-flight work and incumbents. Do not repeat completed exact-context requests or lose a suspended branch.
- Audit all calls and transitions: parent/decision/frame IDs, common-goal comparison, chosen child/backtrack, eligibility proof, exact input/constraint context, worker result/objective, validation and pending state. Close the previous probe/per-branch logging gaps.
- Real11 numerical acceptance: reach a validated B7 landing through the first selected family, independently replay it, then continue backtracking under the declared budget. Rank complete candidates by landing tick first, maximum Z at that tick second; an airborne fixed64 witness is not a complete result for this task. Record first-full-solution time, landing tick/Z, per-family calls, duplicates, refinements and unexplored work. Compare against the old201-vs7 family imbalance; do not substitute a larger cap for the traversal correction.
- Complete retained and otherwise deferred/heuristically excluded alternatives on tractable regression maps to measure quality loss. One first-found DFS incumbent is not a global optimality proof.

Run focused standalone contracts, then affected wider standalone/numerical suites. No mod Gradle gate or game launch is needed for this work.
