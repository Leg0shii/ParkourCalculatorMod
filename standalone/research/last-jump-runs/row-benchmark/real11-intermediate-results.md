# Real11 intermediate feasibility benchmark

All distinct intermediate requests in the saved retry; terminal-goal requests are excluded. Source code: `090aa1acab16b61339f9111b64a8423a34c2e36f`. Fresh workers: 5 rotated repetitions per mode, 3.0s per request. Warm first-phase probes: 3 repetitions per mode.

| Goal | Inputs | Seed already feasible | Directional early stop | Pure feasibility | Optimization | Feasible runs per mode |
| --- | --- | --- | --- | --- | --- | --- |
| b2 | 17 | yes | 0.3ms | 0.2ms | 153.7ms | 5/5, 5/5, 5/5 |
| b2 | 21 | yes | 0.2ms | 0.2ms | 153.0ms | 5/5, 5/5, 5/5 |
| b3 | 27 | no | 241.7ms | 174.0ms | 235.7ms | 0/5, 0/5, 0/5 |
| b3 | 28 | yes | 0.4ms | 0.4ms | 163.0ms | 5/5, 5/5, 5/5 |
| b4 | 40 | no | 132.6ms | 138.5ms | 179.7ms | 5/5, 5/5, 5/5 |
| b5 | 57 | no | 274.8ms | 251.9ms | 275.2ms | 0/5, 0/5, 0/5 |
| b5 | 58 | yes | 0.7ms | 0.7ms | 203.4ms | 5/5, 5/5, 5/5 |
| b5 | 58 | no | 143.3ms | 142.6ms | 199.7ms | 5/5, 5/5, 5/5 |
| b5 | 67 | no | 148.1ms | 151.0ms | 207.4ms | 5/5, 5/5, 5/5 |

Times include parent validation, worker startup, construction, solving and shutdown. All modes use the same benchmark worker entry point, original/fallback seeds, hard constraints, bound extraction and numerical-row reduction. Already feasible seeds return before worker startup in the two early-stop modes.

Warm iteration counts come from a separate initial-seed first-phase probe. A fresh feasibility worker can stop before emitting a completed phase message, so missing phase data is not reported as zero iterations. Warm probes do not include fallback exploration or parent seed bypass.

Every accepted candidate is independently scalar-replayed against every original request row; facings, exit position/velocity and measured objective are in the JSON. No scene/corridor/orchestrator callbacks are timed or rerun, so these are fixed continuous-request results, not complete-route or actual-world validation. The 67-input B5 request was numerically feasible but its archived whole-program result was unknown.

Unknown remains unknown, not proof of infeasibility. Pure feasibility does not optimize the reported progress. No new full Real11 route search was run.
