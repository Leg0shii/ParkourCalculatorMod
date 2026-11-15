# Numerical request ledger

Every worker diagnostic in the two completed reports. Accepted-variant aliases and repeated saved frame locations are not counted as new calls. Worker status and the final result of its enclosing program evaluation are separate columns. A program may require multiple calls: that final result does not indicate whether an individual earlier call passed the corridor/scene checks. See the [pruning audit](PRUNING_AUDIT.md) for per-call acceptance of initial0 calls5–8. Full rows, numerical requests, scene checks, repair state and continuation are in the linked raw reports. Hashes below are checked against the stored requests.

## cold

[Raw report](evidence/cold.json.gz)

| Call | Goal | Inputs | Grounded jumps | Run inputs | Hard rows | Worker status | Final program result | Hash |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | b2 | 17 | 8 | 8 | 80 | feasible | feasible: lower_bound_reached | c341eef87b4b |
| 2 | b2 | 21 | 0,12 | 0 | 24 | feasible | feasible: lower_bound_reached | e838b866eae6 |
| 3 | b3 | 27 | 8,18 | 9 | 140 | unknown | unknown: contact_search_exhausted | b1a196f2215e |
| 4 | b3 | 28 | 8,19 | 10 | 148 | feasible | feasible: lower_bound_reached | 3d90295dffac |
| 5 | b4 | 40 | 8,19,30 | 12 | 188 | feasible | feasible: lower_bound_reached | b6c9cb2b87a0 |
| 6 | b5 | 57 | 8,19,30,42 | 14 | 230 | unknown | unknown: contact_search_exhausted | 0ba4282df584 |
| 7 | b5 | 58 | 8,19,30,43 | 15 | 239 | feasible | feasible: lower_bound_reached | cb6d4aa98146 |
| 8 | landing_height_region | 67 | 8,19,30,43,58 | 15 | 272 | feasible | feasible: deadline | c5170ff44f2c |
| 9 | landing_height_region | 67 | 8,19,30,43,58 | 15 | 278 | feasible | feasible: deadline | f05d5e3a7884 |
| 10 | b5 | 59 | 8,19,30,44 | 16 | 248 | feasible | feasible: lower_bound_reached | 6f0e1abfd372 |
| 11 | b5 | 63 | 8,19,30,48 | 20 | 286 | feasible | unknown: geometry_deadline | 864e0365f128 |
| 12 | b5 | 64 | 8,19,30,49 | 21 | 294 | feasible | feasible: lower_bound_reached | 0d2b1237380f |

## initial0-cold

[Raw report](evidence/initial0-cold.json.gz)

| Call | Goal | Inputs | Grounded jumps | Run inputs | Hard rows | Worker status | Final program result | Hash |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | b2 | 21 | 0,12 | 0 | 24 | feasible | feasible: lower_bound_reached | e838b866eae6 |
| 2 | b3 | 30 | 0,12,21 | 0 | 48 | feasible | feasible: lower_bound_reached | cd67e26a4283 |
| 3 | b4 | 40 | 0,12,21,30 | 0 | 64 | feasible | feasible: lower_bound_reached | e86beb066f43 |
| 4 | b5 | 55 | 0,12,21,30,40 | 0 | 83 | feasible | feasible: lower_bound_reached | ce3128f5749e |
| 5 | landing_height_region | 64 | 0,12,21,30,40,55 | 0 | 104 | feasible | feasible: deadline | 84a71a7b56a9 |
| 6 | landing_height_region | 64 | 0,12,21,30,40,55 | 0 | 106 | feasible | feasible: deadline | 5015668e3d15 |
| 7 | landing_height_region | 64 | 0,12,21,30,40,55 | 0 | 110 | feasible | feasible: deadline | 7cd11c3cb9d5 |
| 8 | landing_height_region | 64 | 0,12,21,30,40,55 | 0 | 112 | feasible | feasible: deadline | 2c4b509f2505 |
| 9 | b5 | 56 | 0,12,21,30,41 | 1 | 92 | feasible | feasible: lower_bound_reached | 917c6e4c990e |
| 10 | b5 | 57 | 0,12,21,30,42 | 2 | 102 | feasible | feasible: lower_bound_reached | 1e4e4f328e0d |
| 11 | b5 | 58 | 0,12,21,30,43 | 3 | 111 | feasible | feasible: lower_bound_reached | c67cde4d7ccf |
| 12 | b5 | 59 | 0,12,21,30,44 | 4 | 121 | feasible | feasible: lower_bound_reached | f9d4e74273a8 |
| 13 | b5 | 60 | 0,12,21,30,45 | 5 | 131 | feasible | unknown: deadline | 457c34ee3bc7 |
| 14 | b5 | 62 | 0,12,21,30,47 | 7 | 146 | feasible | unknown: deadline | eb53024b654a |
| 15 | b5 | 63 | 0,12,21,30,48 | 8 | 154 | feasible | unknown: deadline | d088393aca9e |
