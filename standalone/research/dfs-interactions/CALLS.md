# DFS cold/resume worker calls

Grouped by frame/candidate context, not wall-clock order. Each row is one worker call. Old cold records retain hashes and fixed program context; resumed calls additionally retain exact normalized requests, verified against their fingerprints. Accepted terminal/prefix replays are summarized separately; unknown or missing per-call validation is not physical infeasibility.

| # | Session | Frame | Goal | Inputs | Worker result | Validation log | Exact request stored | Model SHA-256 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 1 | 0 | b2 | 21 | feasible | not logged in older source | no | e838b866eae6d187e632862e26e0915d0a956a05dde603de2747fae7db74aaca |
| 2 | 1 | 0 | b2 | 17 | feasible | not logged in older source | no | c341eef87b4b285e501e33ddd76cbdb09d8f7116a5ae9712aa02f329a9b7ac88 |
| 3 | 1 | 1 | b3 | 27 | unknown | not logged in older source | no | b1a196f2215e42a202923a3e0984e1079116e78085d626729c7e5a1830f95052 |
| 4 | 1 | 1 | b3 | 28 | feasible | not logged in older source | no | 3d90295dffac384fd1137d4055b37ce6d58e2ea327b770334df9d8feae01a6b8 |
| 5 | 1 | 2 | b4 | 40 | feasible | not logged in older source | no | b6c9cb2b87a0411fd50f9e9ee70ae74f19acd03e7859164a91b68fe6d4dcc659 |
| 6 | 2 | 3 | b5 | 62 | feasible | accepted | yes | fdc3ad21f9140bdf800436fbede0cfaf65f93f234958c583b5dc1e5452ead3af |
| 7 | 1 | 3 | b5 | 59 | feasible | not logged in older source | no | 6f0e1abfd37202386a4ca8f8da3331047f3fbdb3ebf4bde46e2d543e455c29e1 |
| 8 | 1 | 3 | b5 | 57 | unknown | not logged in older source | no | 0ba4282df5842c8fec856d2f808da924a05a22b8e3d95a25ddf3b46e5d58849e |
| 9 | 1 | 3 | b5 | 58 | feasible | not logged in older source | no | cb6d4aa9814663b6ee1450b7fb3d879949789151fda22a43a1cf680a79740986 |
| 10 | 2 | 3 | b5 | 61 | feasible | accepted | yes | a14e6703575afea2f894c277034f0bd1872664a3790dc510250c48380f11cf5a |
| 11 | 1 | 4 | terminal clock | 64 | feasible | not logged in older source | no | 785db3f40f109e7f3c9f9fa65d3f5d29f733fb32669dc9082ba1114bebabddea |
| 12 | 1 | 5 | terminal clock | 64 | feasible | not logged in older source | no | 78630ce1adfa0858890322e6668ae7ddf856fd6f3d390198231d04851478b631 |
| 13 | 2 | 6 | terminal clock | 64 | feasible | accepted | yes | d55e27f901b545bb7567bc97a9790507bdedcd20b5402e719fafab5136529eba |
| 14 | 2 | 6 | terminal clock | 64 | feasible | corridor_signature | yes | 79d699f1a14423ba13c4d7f4c00f7f484821644ef5ca276a028fc5d7eca20e3c |

Use `records(state)[(frame, candidateId)]` and its `numericalAttempts[attempt].diagnostics[diagnostic]` for the exact record; the full IDs/indices are recoverable from the script `calls(state)`. The raw reports preserve all constraints, generator state, witnesses and backtracking transitions.
