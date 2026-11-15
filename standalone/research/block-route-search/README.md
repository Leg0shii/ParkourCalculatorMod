# Fixed endpoints, variable intermediate supports

2026-10-26. These cold continuous-model runs exercise the [block-route selection prototype](../../BLOCK_ROUTE_SEARCH.md), with sprint-jump/sprint-45 inputs, free facings, no prescribed block order or air time, and no recorded seeds.

| Scene | Chosen route | Run ticks | Inputs | Prefixes evaluated | Search seconds |
| --- | --- | ---: | ---: | ---: | ---: |
| route-choice | start, middle, goal | 0 | 24 | 2 | .089 |
| momentum-choice | start, around, goal | 0 | 24 | 3 | .210 |

The first scene includes an off-axis alternative and a remote support. The optimistic graph excludes 10 directed edges and establishes that the remote support has no continuation to the goal. The second scene includes a narrow alternative reachable in isolation. Its continuation remained unknown in a separate bounded solve; that is not an infeasibility certificate. Integration coverage compares the pooled result with every one/two-jump route in that small declared family, including jumping again on the start support.

For each scene:

- `*-result.json` is the complete outer result, input scene, graph, chosen route/profile, allocation, scalar-accepted trajectory and prefix trace.
- `*-request.json` is the self-contained horizontal numerical request regenerated from the accepted plan, matching its physical hash. Its yaw is the found result as a warm validation seed; solving this request does not reproduce cold route-search timing.
- `*-vertical.json` is a vertical configuration derived from the chosen schedule: ordinary fired jump impulses, generated landing events and any run-floor events. It contains no recorded Y reference. Adding it to the request passes the existing continuous scalar validator and reproduces the plan's ground flags. This is additional acceptance evidence for the coplanar profile, not a new numerical-worker implementation.
- `summary.json` records source-fixture hashes, numerical hashes, counters and validation outcomes.

Both saved witnesses have zero horizontal and combined vertical constraint violation. Zero added run ticks reaches the nonnegative run-count lower bound in this family; it does not establish the fastest possible traversal or general world feasibility.

Reproduce with the CLI and environment described in the route-search document. Timings can vary. The independent saved-witness tests require only the scalar Python model, not Java, CasADi or Minecraft.
