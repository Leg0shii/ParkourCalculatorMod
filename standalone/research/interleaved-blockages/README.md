# Interleaved wall and ceiling blockages

2026-10-26. Cold search from actual collision boxes and the initial state in `obstacle-interleaved.json`. There are no supplied passage gates, facings or air times. The existing Y model derives an ordinary 12-input jump; the numerical solver chooses free per-input facings under the generated avoidance constraints.

The accepted gate timeline is:

| Physical block | Blockage | First state | Second state | Kind |
| --- | --- | ---: | ---: | --- |
| ceiling | Y->X | 3 | 3 | Vertical |
| wall | X->Z | 4 | 4 | Horizontal |
| ceiling | X->Y | 7 | 8 | Vertical |
| wall | Z->X | 10 | 11 | Horizontal |

The wall's first and second gates are separated by a ceiling gate. Both blocks' face intervals constrain the same trajectory; the planner does not complete the whole wall passage before considering the ceiling. The first/second-state distinction retains the legacy Y-X-Z collision-order rule. These are clearance gates, not selected collision hits.

The wall uses two horizontal and zero vertical blockages. The ceiling uses zero horizontal and two vertical blockages. Each block satisfies its caps of two horizontal, two vertical and three combined. There are four events overall, demonstrating that the combined cap is per block rather than global. Collision sub-boxes and later selected route legs retain their physical block's shared budget.

The search found a 12-input route with zero added runs in about 30.73 seconds, under a 64-branch contact-search cap. It reaches the current graph's 12-input time lower bound within the declared avoidance profile family; objective optimality remains unproved. The search generated 74 retained timing candidates, recorded 18 between-gate reach exclusions and 504 chain-budget exclusions, and returned a validated incumbent when its branch cap was reached.

`request.json` is the accepted full numerical request, including generated X/Z rows and Y contacts. `result.json` preserves the complete scene, graph, passage choices, per-block usage and merged timeline. `summary.json` records the fixture and numerical hashes. The saved facings pass independent scalar validation with zero violation and the shared Java swept-collision check against all actual boxes. They are a warm witness when re-solving this request, not a cold-search input.

The implementation and current contact-family limits are documented in [OBSTACLE_SEARCH.md](../../OBSTACLE_SEARCH.md). Reproduce cold search with `python3 -m standalone.autonomy.solve_blocks --input standalone/autonomy/fixtures/obstacle-interleaved.json --output /tmp/interleaved-result.json --seconds 55 --route-seconds 45 --candidate-seconds 1 --retries 0`, using the prepared compiler and IPOPT runtime from the main README.
