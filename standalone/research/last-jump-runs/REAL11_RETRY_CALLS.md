# Fresh Real11 retry: numerical requests

Source: `61355976f98f2091ee26bae53ddf81ebf6820004`. Requests are ordered by dispatch/evaluation, checked against their recorded fingerprints, and reconciled with the worker-call budget. Accepted-variant aliases are not new calls. Final program result includes any later repair; it is not the acceptance verdict of each individual worker return.

| Call | Goal | Inputs | Jump inputs | Local runs before last jump | Final contact | Hard rows | Worker | Final program result | Hash |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | b2 | 17 | 8 | 8 | physical support | 80 | feasible | feasible: lower_bound_reached | c341eef87b4b |
| 2 | b2 | 21 | 0,12 | 0 | physical support | 24 | feasible | feasible: lower_bound_reached | e838b866eae6 |
| 3 | b3 | 27 | 8,18 | 1 | physical support | 140 | unknown | unknown: contact_search_exhausted | b1a196f2215e |
| 4 | b3 | 28 | 8,19 | 2 | physical support | 148 | feasible | feasible: lower_bound_reached | 3d90295dffac |
| 5 | b4 | 40 | 8,19,30 | 2 | physical support | 188 | feasible | feasible: lower_bound_reached | b6c9cb2b87a0 |
| 6 | b5 | 57 | 8,19,30,42 | 2 | physical support | 230 | unknown | unknown: contact_search_exhausted | 0ba4282df584 |
| 7 | b5 | 58 | 8,19,30,43 | 3 | physical support | 239 | feasible | feasible: lower_bound_reached | cb6d4aa98146 |
| 8 | landing_height_region | 67 | 8,19,30,43,58 | 0 | airborne | 272 | feasible | feasible: deadline | c5170ff44f2c |
| 9 | landing_height_region | 67 | 8,19,30,43,58 | 0 | physical support | 278 | feasible | feasible: deadline | f05d5e3a7884 |
| 10 | b5 | 67 | 8,19,30,40,52 | 0 | physical support | 232 | feasible | unknown: deadline | f402313dac1c |
| 11 | b5 | 58 | 8,19,30,43 | 3 | physical support | 240 | feasible | feasible: lower_bound_reached | 19155811fbbf |
| 12 | landing_height_region | 67 | 8,19,30,43,58 | 0 | airborne | 273 | feasible | feasible: deadline | f14436c2c25f |
