## Matrix analysis (tag gen1)

Problems: 239, presets: [bnb-heavy60, cma-only20, custom-exh30, fast, optimize60, seed-only15]

### Per-preset aggregates

| preset | runs | feasible | censored | mean wall (feas) s | mean regret (feas) |
| --- | --- | --- | --- | --- | --- |
| bnb-heavy60 | 92 | 21 | 0 | 39.7 | 5.472e-04 |
| cma-only20 | 239 | 77 | 0 | 20.0 | 3.124e-02 |
| custom-exh30 | 92 | 23 | 1 | 26.6 | 1.550e-04 |
| fast | 239 | 99 | 48 | 0.2 | 2.653e-02 |
| optimize60 | 92 | 23 | 13 | 49.6 | 2.426e-03 |
| seed-only15 | 239 | 97 | 0 | 0.1 | 1.097e-01 |

### SBS vs VBS

SBS (single best preset): **fast** with 99/239 feasible

VBS (per-problem oracle): 99/239 feasible

Feasibility gap (VBS - SBS): 0 problems

Problems where the oracle beats the SBS:

- closedform/j004: VBS=cma-only20 obj -1889.049187120 beats SBS obj -1889.049325767 (gap 1.386e-04)
- closedform/j005: VBS=cma-only20 obj -41.291495431 beats SBS obj -41.298292475 (gap 6.797e-03)
- closedform/j006: VBS=cma-only20 obj -1599.700111564 beats SBS obj -1599.700052409 (gap 5.915e-05)
- closedform/j009-headwazatoanvil: VBS=cma-only20 obj -0.297897032 beats SBS obj -0.298045190 (gap 1.482e-04)
- closedform/j010-Xp2hsneo: VBS=cma-only20 obj -0.205394174 beats SBS obj -0.204867610 (gap 5.266e-04)
- closedform/j011-1.875x1bmdoublecross: VBS=cma-only20 obj -1.706399763 beats SBS obj -1.706388082 (gap 1.168e-05)
- closedform/j013-cw2cwwinged: VBS=cma-only20 obj -0.049762570 beats SBS obj -0.049862810 (gap 1.002e-04)
- closedform/j014-11bmreversenix: VBS=cma-only20 obj 0.702026084 beats SBS obj 0.701749480 (gap 2.766e-04)
- closedform/j015-1bmdoublewinged_1tier: VBS=cma-only20 obj -0.289767788 beats SBS obj -0.291412263 (gap 1.644e-03)
- closedform/j016-X2jmmp2p: VBS=cma-only20 obj -4.855671218 beats SBS obj -4.857991791 (gap 2.321e-03)
- closedform/j017-postwalllightningneo: VBS=cma-only20 obj -1.216615224 beats SBS obj -1.217846059 (gap 1.231e-03)
- closedform/j018-tds2tdsbf: VBS=cma-only20 obj -4.294423762 beats SBS obj -4.294532469 (gap 1.087e-04)
- closedform/j019-3jmmtruenix: VBS=cma-only20 obj -13.295059825 beats SBS obj -13.303542122 (gap 8.482e-03)
- closedform/j020-panewallsingleblockbf: VBS=cma-only20 obj -4.299788229 beats SBS obj -4.302029815 (gap 2.242e-03)
- frontier/j155-4jmm_3bcmm_4.9375b: VBS=bnb-heavy60 obj 4984.763295965 beats SBS obj 4984.763262905 (gap 3.306e-05)
- frontier/j335_1bmhh_Single_Fencegat_Butterfly_Neo: VBS=bnb-heavy60 obj -697.295144664 beats SBS obj -697.298066047 (gap 2.921e-03)
- frontier/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo: VBS=custom-exh30 obj -699.950209972 beats SBS obj -699.950039612 (gap 1.704e-04)
- frontier/j717_Panewall_Momentum_Single_Block_Butterfly_Neo: VBS=optimize60 obj -82.151965648 beats SBS obj -82.152011417 (gap 4.577e-05)
- frontier/j828-1bm_5.3125-1.5: VBS=custom-exh30 obj 4978.013122753 beats SBS obj 4978.013114128 (gap 8.625e-06)
- frontier/loopmm-3jump-solver-misses: VBS=custom-exh30 obj -279.299879310 beats SBS obj -279.310517374 (gap 1.064e-02)
- gen/j003~g+1: VBS=seed-only15 obj -31.362497924 beats SBS obj -31.362497271 (gap 6.526e-07)
- gen/j004~g-2: VBS=cma-only20 obj -1889.049187481 beats SBS obj -1889.049325767 (gap 1.383e-04)
- gen/j005~g-2: VBS=cma-only20 obj -41.291499496 beats SBS obj -41.298292475 (gap 6.793e-03)
- gen/j006~g-2: VBS=cma-only20 obj -1599.700111759 beats SBS obj -1599.700052409 (gap 5.935e-05)
- gen/j007~g+1: VBS=cma-only20 obj -444.362499998 beats SBS obj -444.037871149 (gap 3.246e-01)
- gen/j007~g+2: VBS=cma-only20 obj -444.425000000 beats SBS obj -444.311102627 (gap 1.139e-01)
- gen/j007~g+4: VBS=cma-only20 obj -444.549999999 beats SBS obj -444.549998484 (gap 1.515e-06)
- gen/j007~g-2: VBS=cma-only20 obj -444.174999997 beats SBS obj -444.122407821 (gap 5.259e-02)
- gen/j008-bfneo~m0.50: VBS=cma-only20 obj -0.235953661 beats SBS obj -0.261332707 (gap 2.538e-02)
- gen/j008-bfneo~t+1: VBS=cma-only20 obj -1.156904760 beats SBS obj -1.202921935 (gap 4.602e-02)
- gen/j009-headwazatoanvil~m0.50: VBS=cma-only20 obj -0.633023506 beats SBS obj -0.634106539 (gap 1.083e-03)
- gen/j011-1.875x1bmdoublecross~t+1: VBS=cma-only20 obj -1.390284222 beats SBS obj -1.390239628 (gap 4.459e-05)
- gen/j012-pistonbasesidewallbf~m0.0: VBS=cma-only20 obj -0.305236284 beats SBS obj -0.348852591 (gap 4.362e-02)
- gen/j012-pistonbasesidewallbf~m0.50: VBS=cma-only20 obj -0.426678742 beats SBS obj -0.457333435 (gap 3.065e-02)
- gen/j013-cw2cwwinged~m0.50: VBS=cma-only20 obj -0.208080118 beats SBS obj -0.232495226 (gap 2.442e-02)
- gen/j014-11bmreversenix~t+1: VBS=cma-only20 obj 0.565531180 beats SBS obj 0.560822609 (gap 4.709e-03)
- gen/j014-11bmreversenix~t+2: VBS=cma-only20 obj 0.408099333 beats SBS obj 0.381547263 (gap 2.655e-02)
- gen/j016-X2jmmp2p~m0.50: VBS=cma-only20 obj -5.099800814 beats SBS obj -5.118012490 (gap 1.821e-02)
- gen/j016-X2jmmp2p~t+1: VBS=cma-only20 obj -6.288787461 beats SBS obj -6.321039278 (gap 3.225e-02)
- gen/j018-tds2tdsbf~m0.0: VBS=cma-only20 obj -4.526702839 beats SBS obj -4.527399846 (gap 6.970e-04)
- gen/j018-tds2tdsbf~m0.50: VBS=cma-only20 obj -4.311431489 beats SBS obj -4.311509715 (gap 7.823e-05)
- gen/j019-3jmmtruenix~m0.50: VBS=cma-only20 obj -13.819661927 beats SBS obj -13.820534960 (gap 8.730e-04)
- gen/j019-3jmmtruenix~t+1: VBS=cma-only20 obj -13.782919176 beats SBS obj -13.845643164 (gap 6.272e-02)
- gen/j020-panewallsingleblockbf~m0.0: VBS=cma-only20 obj -4.340310521 beats SBS obj -4.340738086 (gap 4.276e-04)
- gen/j020-panewallsingleblockbf~m0.50: VBS=cma-only20 obj -4.301522505 beats SBS obj -4.303332035 (gap 1.810e-03)
- gen/j020-panewallsingleblockbf~t+1: VBS=cma-only20 obj -5.391714621 beats SBS obj -5.414110489 (gap 2.240e-02)
- gen/j022-1bmhbfly-noland~m0.50: VBS=cma-only20 obj -531.411041352 beats SBS obj -531.389314794 (gap 2.173e-02)
- gen/j022-1bmhbfly~g-2: VBS=cma-only20 obj -531.700133142 beats SBS obj -531.700129361 (gap 3.781e-06)
- gen/j023-1bmhbfly-eqlanding~g-2: VBS=cma-only20 obj -531.525100000 beats SBS obj -531.525099985 (gap 1.536e-08)
- gen/j155-4jmm_3bcmm_4.9375b~g-2: VBS=seed-only15 obj 4984.763229150 beats SBS obj 4984.763228896 (gap 2.544e-07)
- gen/j335_1bmhh_Single_Fencegat_Butterfly_Neo~g-2: VBS=cma-only20 obj -697.319324702 beats SBS obj -697.410487370 (gap 9.116e-02)
- gen/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo~g-2: VBS=custom-exh30 obj -699.950127086 beats SBS obj -699.917245229 (gap 3.288e-02)
- gen/j828-1bm_5.3125-1.5~g-2: VBS=cma-only20 obj 4978.008383164 beats SBS obj 4978.005400259 (gap 2.983e-03)
- gen/loopmm-3jump-solver-misses~m0.0: VBS=cma-only20 obj -279.364411577 beats SBS obj -279.533856321 (gap 1.694e-01)
- gen/loopmm-3jump-solver-misses~t+2: VBS=cma-only20 obj -279.483300139 beats SBS obj -279.685009661 (gap 2.017e-01)
- gen/nix-full-t1~g-2: VBS=bnb-heavy60 obj 8.696818156 beats SBS obj 8.623575918 (gap 7.324e-02)
- gen/nix-t25-setup-tick~g-2: VBS=optimize60 obj 8.694372502 beats SBS obj 8.620256759 (gap 7.412e-02)
- gen/nix-t25-setup-tick~m0.0: VBS=custom-exh30 obj 8.696777952 beats SBS obj 8.696549677 (gap 2.283e-04)
- gen/nix-t25-setup-tick~m0.50: VBS=optimize60 obj 8.697638201 beats SBS obj 8.696619688 (gap 1.019e-03)
- gen/razor-proof-t1~g-2: VBS=custom-exh30 obj 212.698360537 beats SBS obj 212.688364032 (gap 9.997e-03)
- gen/razor-weirdpane~g-2: VBS=bnb-heavy60 obj -8.977115865 beats SBS obj -8.986841106 (gap 9.725e-03)
- solve/j003: VBS=bnb-heavy60 obj -31.299997874 beats SBS obj -31.299997667 (gap 2.067e-07)
- solve/j004: VBS=cma-only20 obj -1889.049187120 beats SBS obj -1889.049325767 (gap 1.386e-04)
- solve/j005: VBS=cma-only20 obj -41.291495431 beats SBS obj -41.298292475 (gap 6.797e-03)
- solve/j006: VBS=cma-only20 obj -1599.700111564 beats SBS obj -1599.700052409 (gap 5.915e-05)
- solve/j007: VBS=cma-only20 obj -444.299999998 beats SBS obj -444.299990878 (gap 9.121e-06)
- solve/j008-bfneo: VBS=cma-only20 obj -0.215335204 beats SBS obj -0.218532369 (gap 3.197e-03)
- solve/j009-headwazatoanvil: VBS=cma-only20 obj -0.297897032 beats SBS obj -0.298045190 (gap 1.482e-04)
- solve/j010-Xp2hsneo: VBS=cma-only20 obj -0.205394174 beats SBS obj -0.204867610 (gap 5.266e-04)
- solve/j011-1.875x1bmdoublecross: VBS=cma-only20 obj -1.706399763 beats SBS obj -1.706388082 (gap 1.168e-05)
- solve/j013-cw2cwwinged: VBS=cma-only20 obj -0.049762570 beats SBS obj -0.049862810 (gap 1.002e-04)
- solve/j014-11bmreversenix: VBS=cma-only20 obj 0.702026084 beats SBS obj 0.701749480 (gap 2.766e-04)
- solve/j015-1bmdoublewinged_1tier: VBS=cma-only20 obj -0.289767788 beats SBS obj -0.291412263 (gap 1.644e-03)
- solve/j016-X2jmmp2p: VBS=cma-only20 obj -4.855671218 beats SBS obj -4.857991791 (gap 2.321e-03)
- solve/j017-postwalllightningneo: VBS=cma-only20 obj -1.216615224 beats SBS obj -1.217846059 (gap 1.231e-03)
- solve/j018-tds2tdsbf: VBS=cma-only20 obj -4.294423762 beats SBS obj -4.294532469 (gap 1.087e-04)
- solve/j019-3jmmtruenix: VBS=cma-only20 obj -13.295059825 beats SBS obj -13.303542122 (gap 8.482e-03)
- solve/j020-panewallsingleblockbf: VBS=cma-only20 obj -4.299788229 beats SBS obj -4.302029815 (gap 2.242e-03)
- solve/j021-rinav1-01: VBS=custom-exh30 obj 1067.862796095 beats SBS obj 1066.894753207 (gap 9.680e-01)
- solve/j022-1bmhbfly: VBS=cma-only20 obj -531.700137494 beats SBS obj -531.700017422 (gap 1.201e-04)
- solve/j022-1bmhbfly-noland: VBS=cma-only20 obj -531.700138384 beats SBS obj -531.636401949 (gap 6.374e-02)
- solve/j023-1bmhbfly-eqlanding: VBS=cma-only20 obj -531.650100000 beats SBS obj -531.650015912 (gap 8.409e-05)
- solve/nix-t25-setup-tick: VBS=custom-exh30 obj 8.698293274 beats SBS obj 8.697963572 (gap 3.297e-04)
- solve/trp-optimize-feasible-swap: VBS=bnb-heavy60 obj 4.237144048 beats SBS obj 4.236883000 (gap 2.610e-04)

### Ranking by problem class

| class | preset | feasible | mean regret (feas) |
| --- | --- | --- | --- |
| single (35) | bnb-heavy60 | 0 | - |
| single (35) | cma-only20 | 35 | 1.429e-02 |
| single (35) | custom-exh30 | 0 | - |
| single (35) | fast | 35 | 3.295e-03 |
| single (35) | optimize60 | 0 | - |
| single (35) | seed-only15 | 35 | 1.758e-02 |
| multi (5) | bnb-heavy60 | 1 | 0.000e+00 |
| multi (5) | cma-only20 | 1 | 0.000e+00 |
| multi (5) | custom-exh30 | 2 | 0.000e+00 |
| multi (5) | fast | 3 | 3.228e-01 |
| multi (5) | optimize60 | 2 | 0.000e+00 |
| multi (5) | seed-only15 | 2 | 6.149e-01 |
| long (4) | bnb-heavy60 | 3 | 2.866e-03 |
| long (4) | cma-only20 | 0 | - |
| long (4) | custom-exh30 | 4 | 5.168e-08 |
| long (4) | fast | 4 | 6.531e-05 |
| long (4) | optimize60 | 4 | 5.168e-08 |
| long (4) | seed-only15 | 3 | 8.600e-01 |
| frontier (8) | bnb-heavy60 | 6 | 5.163e-07 |
| frontier (8) | cma-only20 | 1 | 2.357e-02 |
| frontier (8) | custom-exh30 | 6 | 1.142e-05 |
| frontier (8) | fast | 6 | 2.303e-03 |
| frontier (8) | optimize60 | 6 | 2.042e-05 |
| frontier (8) | seed-only15 | 6 | 2.303e-03 |
| gen (187) | bnb-heavy60 | 11 | 2.629e-04 |
| gen (187) | cma-only20 | 40 | 4.704e-02 |
| gen (187) | custom-exh30 | 11 | 3.178e-04 |
| gen (187) | fast | 51 | 2.997e-02 |
| gen (187) | optimize60 | 11 | 5.061e-03 |
| gen (187) | seed-only15 | 51 | 1.216e-01 |
