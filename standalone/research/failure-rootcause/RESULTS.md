# Generic causes of the bounded-search failures

The investigation separates constructed programs, scheduled verification, inner numerical feasibility and full physical acceptance. The evidence supports three distinct mechanisms, rather than a special score for the two original maps. Production/default behavior is unchanged; the fixes below are research prototypes or recommendations.

Scope: ten generation scenes, fourteen warm program variants over ten geometries, two objective directions, invalid-seed controls, six analytic scalar controls and matched repair-budget probes. Reversed objectives are acceptance-contract stress tests, not independent routing maps. These are bounded diagnostic matrices, not a general benchmark or global-optimality study. Source/runtime/artifact hashes are in the [manifest](evidence/manifest.json); exact inputs and outcomes are in [summary.json](evidence/summary.json) and the compressed reports.

## 1. Complete proposals are starved and evicted

The earlier description that no complete numerical proposal was reached was accurate at the solver interface, but incomplete as a root cause: the grind generator had already constructed 146 complete schedules. They waited in the same queue as partial prefixes. The queue orders by elapsed ticks so all shorter partials stay ahead, and its cap retains the smallest elapsed ticks, deleting longer complete schedules too. Of those 146 complete proposals, 115 were evicted; none was yielded before 512 expansions. The remaining ones were still waiting when the expansion cap stopped the search.

This repeats on a separate repeated-ceiling scene: the instrumented baseline constructed 228 complete schedules and evicted all 228 before yielding one. That observer hit its 30-second limit after 444 expansions; its time includes diagnostic overhead and is not a production timing measurement.

The problem is therefore not simply inability to construct complete programs. Prefix-cost ordering controls both search expansion and admission to expensive validation, without a separate incumbent-discovery allocation. A hard queue cap then permanently removes the very completions that could supply an incumbent.

The generation probes use the same grammar, screens, domains and per-scene caps. They change only key identity and queue operations in a private instrumented function. Earliest complete proposal yielded, measured in expanded queue entries:

| Scene | Original shortest-prefix queue | Complete-first | Complete-first with tick-bucket fairness |
| --- | ---: | ---: | ---: |
| Open | 14 | 2 | 2 |
| Ceiling | 16 | 2 | 2 |
| Multiple supports | 77 | 5 | 5 |
| Stepping | 103 | 3 | 3 |
| Blockage turn | 14 | 2 | 2 |
| Dense grind/run | None | 5 | 4 |
| Additional run-up | 34 | 7 | 7 |
| Additional remote supports | 14 | 2 | 2 |
| Additional grind/blip | 27 | 2 | 2 |
| Additional repeated ceiling | None | 2 | 2 |

Getting a complete profile is not physical success. A separate cold pass sorts each policy's first twelve emitted proposals by elapsed ticks and validates them with matched four-call candidate limits. No solved facing sequence is injected. Best accepted results in those bounded pools:

| Scene | Original queue | Complete-first | Tick-fair discovery |
| --- | ---: | ---: | ---: |
| Dense grind/run | Unknown, no proposals | Unknown | 11 ticks |
| Multiple supports | 14 ticks | 15 ticks | 15 ticks |
| Blockage turn | Unknown | Unknown | Unknown |
| Additional grind/blip | 5 ticks | 5 ticks | 5 ticks |
| Additional repeated ceiling | Unknown, no proposals | Unknown | Unknown |

All accepted proposals pass independent replay. Verification is offline on emitted pools, not an integrated live-scheduler performance benchmark; it deliberately validates later proposals too rather than stop after an incumbent.

The fair-discovery pool exposes the known 11-tick grind pattern as its fourth completion and finds a physically valid 11-tick solve. But naive goal prioritization loses the 14-tick support result under the same twelve-proposal cutoff. The hypothesis that one queue policy universally improves quality is rejected.

Generic solution: separate a completed-candidate validation queue from partial expansion storage; protect useful complete proposals from partial-prefix eviction; process ready completions by elapsed time while reserving work to discover better ones. Add backpressure and fair continuation quanta rather than always preferring goals or always preferring short partials. Apply the proposed event/time frontiers at meaningful intermediate boundaries, and retain a diversity reserve because scalar progress is not physical dominance.

### Deduplication was a secondary hypothesis

Full-cluster generation uses a legacy profile key while checkpoint generation uses a canonical key. The experiment canonicalizes full-cluster keys, including interaction-budget context, and checks sampled duplicates against compiled coefficients, vertical replay, contact and support semantics. All 16 sampled equivalence checks pass.

However the original dense grind scene has zero detected metadata duplicates, and canonicalization leaves its starvation unchanged. The additional repeated-ceiling scene has twelve observed metadata duplicates, but canonicalization still yields no completion under its cap. Key consistency is worthwhile cleanup; it is not the primary fix for these failures.

## 2. The numerical incumbent and physical incumbent use different validity contracts

The low-level numerical solver already preserves its seed when that seed satisfies the supplied inner problem. The defect is between layers: the inner solver keeps its best scalar-model candidate; corridor, optional-floor and finite collision checks are applied later. A higher-scoring scalar candidate can fail those checks. Returning only that scalar winner hides the fully valid seed from the outer incumbent.

Warm probes start with 28 explicitly revalidated feasible seed/objective cases, plus eight perturbed cases whose initial seeds all fail full validation. The shared audit covers scalar constraints, Y schedule, support/exit conditions, optional floors, corridor and finite scene geometry. The broader warm matrix tests that shared contract; the earlier focused turn probe also verifies its checkpoint stage.

| Research mode | Valid-seed cases recovered | Perturbed cases physically solved | Objective losses versus successful baseline cases |
| --- | ---: | ---: | --- |
| Unchanged optimizer/repair | 25 / 28 | 6 / 8 | Reference |
| Replace rejected output with valid seed | 28 / 28 | 6 / 8 | Two cases lose improvement |
| Stop at inner feasibility | 28 / 28 | 6 / 8 | Several cases lose improvement |
| Separate validated incumbent and unchanged repair proposal | 28 / 28 | 6 / 8 | None observed |

The three outright feasible-seed failures remain concentrated in turn variants. A separate grind/blip control also exhibits a rejected scalar winner, although the baseline eventually repairs it. Most ordinary controls are unaffected. These counts are conditional contract tests, not a 3/28 cold-search failure rate.

Simply replacing the invalid optimizer output with the seed suppresses useful repair proposals: it loses about 0.969 and 0.861 blocks in two reverse-objective stress cases the baseline successfully improved. Feasibility-only solving loses still more objective quality in several stress cases. Neither simplification should become the generic production fix.

The separate-channel prototype records fully validated seeds independently, leaves the optimizer's proposals and repair search unchanged, and compares the final validated result with the preserved incumbent. It restores all three seeded failures without lowering any successful baseline objective in this matrix. The invalid initial seeds are never accepted by the fallback; every returned feasible result gets independent physical replay. The two unresolved perturbed turn cases stay unresolved, showing that incumbent retention alone does not create a good cold start.

Generic solution: one complete acceptance predicate for initial seeds, emitted feasible candidates and final incumbents, including context-specific checkpoint readiness. Keep accepted physical incumbents separate from optimizer proposals used to discover new guards. A failed polish, rejected proposal or exhausted repair allowance must not erase an incumbent that remains valid for the same problem. Do not carry a seed across changed profiles/constraints without revalidation.

## 3. No-threshold mode bypasses fallback seeds and exposes angular degeneracy

An additional cold turn probe uses a complete 16-tick program with exactly the same jump, Y and contact schedule as the known accepted witness. Raising its call allowance from 4 to 16 or 32 does not help: it makes one numerical call and returns unknown before an outer candidate can be repaired. The inner result is `Infeasible_Problem_Detected`, not a physical infeasibility certificate.

The captured initial angles make every input's horizontal acceleration point exactly along +X. The initial trajectory violates a goal upper-X wall by about 0.34726 blocks. At that angular extremum, the first derivative of forward displacement with respect to facing is zero. This is a poor feasibility-restoration start for the nonlinear angle model.

`search.py` has already supplied a perturbed `fallbackYaw`, but `continuation.run` returns from its threshold-zero carry path before the fallback-seed loop. With the caller's zero retries, this model gets one initial start. Merely allowing more branch calls does not cause another start when no repair candidate was returned.

The mechanism is checked without Minecraft geometry. Six scalar controls use one, four and twelve inputs on each of X and Z, with a required positive forward interval below the axis-aligned maximum. Each has an analytic feasible facing sequence. The diagnostic compares initial-only, the same initial plus a declared fallback, and the fallback used explicitly as the initial guess:

| Scalar family | Axis-aligned initial | Declared fallback | Perturbation used as initial |
| --- | --- | --- | --- |
| X controls, 1 / 4 / 12 inputs | All unknown | All unknown | All feasible |
| Z controls, 1 / 4 / 12 inputs | All feasible | All feasible | All feasible |
| Captured turn model | Unknown | Unknown | Feasible |

The perturbation is five degrees in this diagnostic, not a proposed magic production setting. Source and phase traces show that only the initial phase runs when fallback is merely declared. The successful Z controls show orientation/numerical sensitivity rather than a universal failure of no-threshold motion. The captured turn's perturbed solve is inner-model feasibility; it is not credited as a collision-valid route.

Generic solution: decouple seed exploration from inertia-pattern continuation. Threshold zero should skip unnecessary clamp-regime enumeration, while still allowing a bounded restart/seed portfolio when the first start fails. Prefer validated warm/geometry-informed seeds, and use generic perturbations to escape stationary infeasible starts. Honor the actual directional objective when constructing seeds. Keep all earlier facings variable. This does not require changing acceleration magnitude, reinstating thresholds or replacing the physical model.

### Relevant geometry must reach the model early enough

In one matched diagnostic, the raw turn program has 20 positional guards. The accepted historical program has the identical physical decision schedule but 52 guards, including earlier learned corridor conditions. Copying those guards, without copying a solved yaw or freezing any intermediate position, produces an independently validated 16-tick result in one call at each allowance 4, 16 and 32. The raw program remains unknown at every allowance.

This is oracle-assisted constraint reuse, not a cold-discovery result or proof that a single set of learned guards covers the route family. It demonstrates that the physical schedule is present and that earlier useful guard information can make the same continuous problem much easier. Generic candidates are route-informed initialization, short feasibility-continuation probes and reusable symbolic guards under matching decision context. Preserve alternative guard branches; an accepted restricted branch is not a universal route constraint.

## What to fix first

1. Preserve fully validated incumbents separately from proposal/repair state. Add regressions that accepted feasibility and objective cannot disappear merely because polishing fails.
2. Restore bounded fallback/seed exploration in the no-threshold path. Add scalar analytic controls on both axes and account for every restart in the same time/call budget.
3. Separate completed-proposal admission from short-prefix expansion and eviction. Couple time-first verification with a reserved discovery allocation; measure both early incumbents and shorter-plan coverage.
4. Then implement the user's event-ready time/progress frontier on top of those corrected contracts. Stage readiness must enter full acceptance before incumbent comparison. Revisit per-parent/bucket budgets and useful guard reuse based on measured progress.

Blindly increasing call caps, switching off optimization, applying a new weighted score, canonicalizing metadata or always preferring goals is insufficient in these probes. The queue and incumbent mechanisms are generic; the experiments do not establish that all other search failures have these causes or that the architecture scales to dense maps.

No production/default search behavior, physical acceptance rule, inertia threshold, facing freedom or objective was changed. Bounds, mixed initializers, ladders, intentional wall hits and actual-world replay remain deferred.

## Evidence and limits

The research matrices contain 40 generation configurations, 144 warm-seed trials, 15 offline proposal-pool verifications, 21 repair-budget/guard conditions and 21 scalar-seed solves. These are repeated variants of a limited corpus, not 241 independent maps. All 171 returned physical witness checks pass independent replay. Scalar-only feasibility is reported separately.

The generation observer's private AST hook has baseline-equivalence tests. Timings are instrumented and should not be used to claim production speedups. The first-twelve proposal cutoff intentionally exposes scheduling tradeoffs; it is not an exhaustive enumeration. Successful controls establish existence, not optimality. Unknown numerical outcomes remain unknown.

The focused compiler/IPOPT gate passes 43 tests. Broader autonomy completes 376 tests with 82 environment skips and no failures after excluding the known pre-existing ladder failure. [Focused gate](evidence/failure-rootcause-focused.log), [autonomy gate](evidence/failure-rootcause-autonomy.log). These test results cover the shared existing controls and new instrumentation invariants; actual diagnostic matrices separately validate the candidate-handling prototypes.

Raw data: [generation](evidence/failure-generation-final.json.gz), [warm contracts](evidence/failure-warm-final.json.gz), [cold physical verification](evidence/failure-verify-final.json.gz), [repair/constraint controls](evidence/failure-repair-final.json.gz), [scalar starts](evidence/failure-scalar-final.json.gz), [captured scalar input](evidence/failure-scalar-capture.json.gz). Reproduction and scope are in the [README](README.md). Dataset sources are pinned separately because the repair and scalar diagnostics were added after the first matrices; no production model change occurred between them.
