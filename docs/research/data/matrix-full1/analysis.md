## Matrix analysis (tag full1)

Problems: 44, presets: [custom-deep60, custom-exh30, custom-nowin60, fast, optimize60]

### Per-preset aggregates

| preset | runs | feasible | censored | mean wall (feas) s | mean regret (feas) |
| --- | --- | --- | --- | --- | --- |
| custom-deep60 | 44 | 43 | 2 | 57.3 | 1.092e-03 |
| custom-exh30 | 44 | 43 | 0 | 16.7 | 1.574e-05 |
| custom-nowin60 | 44 | 41 | 1 | 32.8 | 6.293e-02 |
| fast | 44 | 43 | 1 | 1.3 | 2.523e-02 |
| optimize60 | 44 | 44 | 1 | 35.0 | 1.284e-05 |

### SBS vs VBS

SBS (single best preset): **optimize60** with 44/44 feasible

VBS (per-problem oracle): 44/44 feasible

Feasibility gap (VBS - SBS): 0 problems

Problems where the oracle beats the SBS:

- closedform/j006: VBS=custom-deep60 obj -1599.700112051 beats SBS obj -1599.700111718 (gap 3.329e-07)
- closedform/j011-1.875x1bmdoublecross: VBS=custom-deep60 obj -1.706399839 beats SBS obj -1.706399763 (gap 7.541e-08)
- closedform/j015-1bmdoublewinged_1tier: VBS=custom-deep60 obj -0.289763651 beats SBS obj -0.289767788 (gap 4.137e-06)
- closedform/j016-X2jmmp2p: VBS=custom-deep60 obj -4.855667357 beats SBS obj -4.855671218 (gap 3.861e-06)
- closedform/j017-postwalllightningneo: VBS=custom-deep60 obj -1.216475321 beats SBS obj -1.216615224 (gap 1.399e-04)
- closedform/j018-tds2tdsbf: VBS=custom-nowin60 obj -4.294393406 beats SBS obj -4.294394611 (gap 1.205e-06)
- closedform/j019-3jmmtruenix: VBS=custom-deep60 obj -13.294934405 beats SBS obj -13.295059825 (gap 1.254e-04)
- closedform/j020-panewallsingleblockbf: VBS=custom-deep60 obj -4.299780032 beats SBS obj -4.299780773 (gap 7.401e-07)
- solve/j006: VBS=custom-deep60 obj -1599.700112051 beats SBS obj -1599.700111718 (gap 3.329e-07)
- solve/j007: VBS=custom-deep60 obj -444.300000000 beats SBS obj -444.299999998 (gap 1.503e-09)
- solve/j009-headwazatoanvil: VBS=custom-nowin60 obj -0.297882189 beats SBS obj -0.297882676 (gap 4.871e-07)
- solve/j011-1.875x1bmdoublecross: VBS=custom-deep60 obj -1.706399839 beats SBS obj -1.706399763 (gap 7.541e-08)
- solve/j013-cw2cwwinged: VBS=custom-nowin60 obj -0.049749525 beats SBS obj -0.049754662 (gap 5.137e-06)
- solve/j015-1bmdoublewinged_1tier: VBS=custom-deep60 obj -0.289763651 beats SBS obj -0.289767788 (gap 4.137e-06)
- solve/j016-X2jmmp2p: VBS=custom-deep60 obj -4.855667357 beats SBS obj -4.855671218 (gap 3.861e-06)
- solve/j017-postwalllightningneo: VBS=custom-deep60 obj -1.216475321 beats SBS obj -1.216615224 (gap 1.399e-04)
- solve/j018-tds2tdsbf: VBS=custom-nowin60 obj -4.294393406 beats SBS obj -4.294394611 (gap 1.205e-06)
- solve/j019-3jmmtruenix: VBS=custom-deep60 obj -13.294934405 beats SBS obj -13.295059825 (gap 1.254e-04)
- solve/j020-panewallsingleblockbf: VBS=custom-deep60 obj -4.299780032 beats SBS obj -4.299780773 (gap 7.401e-07)
- solve/j022-1bmhbfly: VBS=custom-deep60 obj -531.700140829 beats SBS obj -531.700137494 (gap 3.336e-06)
- solve/j022-1bmhbfly-noland: VBS=custom-deep60 obj -531.700142863 beats SBS obj -531.700138384 (gap 4.479e-06)
