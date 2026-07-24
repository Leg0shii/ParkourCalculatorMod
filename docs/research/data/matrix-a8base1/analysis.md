# A8 smoothness baseline (2026-07-19)

Step 3 of the split-solver sequencing (grill brief section 10). Stats definition: yawTravelDeg = sum of |delta yaw| with each delta wrap-normalized into (-180, 180], yawDirChanges = sign alternations between consecutive nonzero deltas, yawMaxStepDeg = max normalized |delta|. Computed on the final solution yaws at record finalize (SolveRunRecord.smoothnessOf). The wrap normalization is mandatory: recorded yaws are wrapped, so raw deltas fake ~333 degree steps at the seam.

## Runs

- matrix-a8base1: standing corpus (solve + closedform + frontier, 51 problems) x {fast, optimize60}, 120 s cap, 102 records, 40 min.
- matrix-a8smooth1: the 7 wiggliest problems x {fast, custom-fastgraph (CUSTOM + builtin fast graph, control), smooth-heavy (same graph, smoothing nodes at maxRounds 200 / maxEvals 400000 / pairSpan 8 vs shipped 24 / 24000 / 3)}, 21 records, 4.5 min. Band in matrix-a8smooth1/band.txt.

## Findings

1. Wiggle is structural, not universal. Closed-form-territory problems come out perfectly smooth (six problems with zero direction reversals). Everything that goes through receding-horizon / CMA-ES / recovery chains wiggles: worst j335 reverses direction on 62% of ticks, nix-full-t1 30%, the TAS-length trio j001/j002/j003 carry 1786-3441 deg of travel with a reversal every ~6 ticks, all after the shipped smoothing pass.
2. More optimization makes it worse. optimize60 mean reversal rate 20% of ticks vs fast 9%; on 48 paired feasible problems optimize60 is wigglier on 28, smoother on 2, tied 18. Budget at the last sine bucket is earned by exploiting jitter.
3. Heavier polish does not fix it (VERDICT: search-intrinsic, not a polish-configuration gap). The 16x smoothing budget changed reversal counts by 0-2 and travel by under 1 percent across the 7 worst problems, and where it moved travel it cost objective at the 1e-4..1e-5 scale (j001: -16.5 deg travel for -9.2e-5 objective). j335 came out byte-identical.
4. Consequence for the plan: smooth solutions require smoothness inside search scoring (the lambda term, step 5) and/or a smooth-by-construction core (ALM bake-off, step 6). Post-hoc polish is a dead end for this.
5. Lambda scale hints for step 5 pinning: feasible-run travel/tick mean 7.9 (fast) / 9.4 (optimize60) deg, reversal rate 9% / 20% of ticks, maxStep mean ~45 deg.
6. Side observation consistent with the A22 verdict: j335 solves in 0.6 s under FAST effort but the identical graph under CUSTOM grinds 20.4 s to the same objective (stopOnFeasible=false, no overall deadline).
