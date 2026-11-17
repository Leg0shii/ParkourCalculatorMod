# Automatically selected run-off profiles

2026-10-26. Cold searches from support geometry and initial state, with no supplied route order, air time, recorded facing or per-tick constraint table. A run-off is a regular no-jump profile alongside ordinary and stair jumps. All profiles share total-ticks-first, objective-second ranking.

| Fixture | Selected profiles | Durations | Added runs | Total inputs | Search seconds |
| --- | --- | --- | --- | ---: | ---: |
| blocks-runoff | run-off | 4 | 4 | 8 | 1.855 |
| blocks-jump-runoff | ordinary, run-off | 12, 4 | 0, 0 | 16 | .969 |

The first fixture drops half a block. The Y simulation derives a four-input fall without a jump impulse. The search finds four supported ground inputs before departing, for eight inputs total. An ordinary jump to the same target takes 13 inputs before any added runs. Its optimistic reach radius is about 5.559 blocks, versus 1.867 for the run-off. These bounds include carried momentum and movement after the support check; they are conservative filters, not measured travel or a fixed candidate radius.

The second fixture discovers the intermediate support, jumps onto it and immediately runs off toward the lower goal. The second transition inherits the first transition's position, horizontal momentum and grounded flag. There is no extra delay or velocity reset. Only input 0 has Jump; the run-off has none.

Run-off duration includes its first input, which starts grounded and uses ground acceleration/drag. Subsequent inputs use air constants until landing. Earlier-floor checks include the departure source, and the chosen exit requires the player center to be outside the source support footprint. All four exit faces remain candidate alternatives. Motion coefficients are cached by jump/no-jump plus duration, so a fall and a stair jump with the same duration cannot share incorrect constants. A separate integration regression covers a one-input run-off onto a one-pixel drop.

Each `*-request.json` is a self-contained continuous numerical request with generated Y contacts. Each `*-result.json` retains the scene, profile graph, selected supports, run allocation, facings and full accepted trajectory. `summary.json` records fixture hashes, horizontal/full numerical hashes, profile reach bounds and search counters. Both saved requests pass the independent scalar validator with zero violation, matching ground flags, strict support/exit checks and no conflicting earlier-floor contacts.

The saved request's yaw is the found witness for convenient validation. Re-solving it is warm validation, not a cold route-search reproduction. Use the [block-route CLI](../../BLOCK_ROUTE_SEARCH.md) with the fixtures under `../../fixtures` for cold selection. The reported times include graph compilation and search using the persistent Java coefficient compiler and the actual IPOPT runtime.

These saved results predate the explicit preceding-state run-off support rows; that state is already checked by their run or landing rows. Their departure exit is checked, but subsequent Y-dependent source-obstacle clearance is not. Leaving a nearer side edge may avoid the first fixture's four forward run inputs. That alternative needs full source obstacle geometry and constraints, not a permanent departure half-plane. See [run-off obstacle requirements](../../RUN_OFF_OBSTACLES.md).

Neither result reaches its graph-only tick lower bound, so neither claims a proven minimum. The numerical objective is also locally optimized under a budget. These are continuous support-plane results; ceiling and general finite side-collision modeling remain separate work.
