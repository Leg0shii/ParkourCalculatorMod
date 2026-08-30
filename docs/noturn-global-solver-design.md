# No-turn global solver design record

## Ruling: the one biggest hammer

**Combinatorial-Benders / one-tree no-good-cut structure-pool enumeration over the discrete schedule (combos, sprint latch, gate pattern, min-edges as the master objective), with each proposed structure certified to gap=0 by the existing single-quadratic spatial branch-and-bound (`DiskSocpKernel` disk + arc chord/tangent cuts + exact 3-way gate branch in `CertifiedBnb`) replayed through `ExactJumpModel`, and seeded cold by a Scholtes wall-homotopy continuation front end.**

That is one hammer with three welded parts: an outer combinatorial master, an inner certified continuous oracle, and a cold incumbent front end. No single named textbook solver is the whole thing, and no off-the-shelf global solver (COPT/SCIP/Gurobi) is even a drop-in, because the two failures that actually kill this problem live outside what they provide.

### Why it dominates for THIS problem

The problem is a mixed-integer program with exactly one nonconvexity (the circle a^2+b^2=1), a min-edges objective, global landing walls, a state-triggered inertia gate, a free start, and a finite-lattice byte-exact truth. Match each structural fact to the survey verdicts:

- **Single quadratic => the continuous subproblem is EXACT, not merely relaxed** (single-quadratic survey, S-lemma / TRS; Yakubovich, Adachi-Nakatsukasa, Jeyakumar-Li). For a fixed schedule the node is an extended TRS: one disk plus linear walls plus free-start. Its optimum saturates the circle (measured abNorm 1.0000000003) and is recovered in closed form / one SOCP solve. So none of the exponential hardness lives in the geometry. This is precisely what `DiskSocpKernel` + `CertifiedBnb` already are. That kills the wide-arc enumeration that ended the beam/MITM cold solver: a bound replaces enumeration over the facing.

- **The residual hardness is combinatorial (schedule) and dead-zone (gate), and min-edges is the master objective.** The gcs-switched survey is decisive here: GCS is the wrong hammer (the shared circle G1 and the global walls G2 are not edge-local, so GCS's perspective tightness does not survive, and min-transitions is degenerate Viterbi on our instance). Its own verdict names **logic-based / combinatorial Benders (Hooker-Ottosson; Codato-Fischetti)** as THE hammer for min-edges specifically: a pure combinatorial master (no continuous vars) whose objective is the number of input changes = constrained total variation (Zeile-Sager), coupled to a certified continuous slave that returns IIS-strengthened no-good cuts. Codato-Fischetti targets exactly our gate/combo/latch big-M logic.

- **DP/reachability is dominated** (dp-reachability survey, own verdict DEAD): friction amplification 6.85..10.05 against 1e-4..1e-6 corridors forces ~1e-5 velocity quantization (~5e9 cells/tick) and dedup is unsound because the objective is not a function of the interface state. The R(theta)*S factorization removes theta from the state but converts the DP into injective schedule enumeration, not a compressed value function. DP contributes two components only: the friction-funnel admissible bound and free-start-as-seed-box.

- **The certificate must be byte-exact against a finite 65536-lattice oracle, not a continuous relaxation tolerance** (certified-incumbents survey). Continuous gap=0 is the wrong certificate: the byte-exact outcome is piecewise-constant per 0.00549 degree bucket, and the continuous optimum sits up to ~12560 buckets from the byte optimum cross-family. `ExactJumpModel` replay at FEAS_TOL=0 is the sole certificate; the relaxation only produces the bound and a candidate lattice point. This is the VIPR "verify the answer, not the solver" split, realizable in pure Java because the bound side is our SOCP kernel and truth is the exact replica.

- **Cold incumbent finding is the measured killer, and wall homotopy is the only thing that beat it cold** (certified-incumbents survey B; NOTURN-HANDOFF 4c). COPT found 0 incumbents in 500k+ nodes on the exact circle. Feasibility pump does not apply: the infeasibility is a corridor gap, not an integrality gap. Scholtes relax-then-tighten continuation (`WallHomotopyLadder`) is the measured cold rediscovery mechanism.

So: the geometry is polynomial and already solved (S-lemma exact node), the objective is native to a combinatorial Benders master (min-edges = TV shortest path), the gate is an MPVC that must be branched not rounded (already built in `CertifiedBnb`), and the certificate is exact replay. The composite is the only architecture that fits all five facts at once, and every piece but the outer driver already exists in pure Java.

## Architecture

### Layering

```
OUTER   structure-pool master        min-edges TV shortest path over the 9-combo x sprint trellis
        (combinatorial Benders)       + assignment + sprint-latch + edge-cap/dwell rows
                                       + accumulated IIS no-good cuts
          | proposes schedule sigma          ^ returns feasibility + objective, or active-wall IIS
          v                                   |
INNER   certified continuous slave     one extended-TRS node per sigma:
        (single-quadratic spatial B&B)  DiskSocpKernel disk + arc chord+tangent cuts + linear walls
                                        + SOC-RLT wall x disk rows, IPM weak-duality bound
                                        + CertifiedBnb arc bisection + 3-way gate branch (ZERO/POS/NEG)
                                        + free start px/pz
          | candidate lattice facing         ^ gate pattern fold fixed point (2 rounds)
          v                                   |
TRUTH   ExactJumpModel replay, FEAS_TOL=0    gap=0 iff disk/arc bound over the residual lattice
        on the 65536 sine grid               window meets a replay-verified incumbent
FRONT   WallHomotopyLadder continuation      cold first incumbent per structure (relax walls by delta,
END     + switching-cost-aware rounding      tighten to 0, fold gate each rung); SCARP -> low-edge start
```

### Node relaxation (inner slave), exploiting the single quadratic

For a fixed schedule the node is the extended TRS

```
min c . (a, b, p0)   s.t.   a^2 + b^2 <= 1,   W . (a, b, p0) <= d
```

- **Disk, not McCormick.** Enforce a^2+b^2<=1 as the SOCP disk (the S-lemma convex hull for one quadratic), never a bounding-box RLT on a^2+b^2. `DiskSocpKernel` already does this and emits the weak-duality certificate as the node lower bound.
- **Arc chord + tangent cuts.** `SineTableGeometry` already carries the Coffrin-Hijazi chord cos(mu)a + sin(mu)b >= cos(Delta). Add the two endpoint tangent cuts a cos(theta_e) + b sin(theta_e) <= 1 so the arc envelope is the convex hull (chord = inner, tangents = outer). Bound closes geometrically, ~8-12 bisections to snap to a grid bucket.
- **SOC-RLT wall x disk rows.** The walls generically cross inside the disk (a landing X-wall and a landing Z-wall, free-start corners), so the raw eTRS relaxation reopens a tiny gap (measured 2.1e-7..1.6e-3). Append the second-order-cone RLT product of each active wall with the disk (Anstreicher; Burer-Yang non-intersecting condition) so the node bound is certified-exact rather than empirically-tight. Cheap: one extra SOC row per wall in `DiskSocpKernel`. The fixed-m arc-face argument (Bienstock) makes this polynomial since only 1-2 walls are active at the optimum.

### Min-edges objective handling

Master objective = number of schedule transitions, encoded as a constrained total variation over the trellis:

```
assignment    sum_k x[t,k] = 1
edge var      c[t] >= x[t,k] - x[t+1,k],   c[t] >= x[t+1,k] - x[t,k]
minimize      sum_t c[t]          ties broken by the physics objective (MAX/MIN X at landing)
sprint latch  s[t] >= s[t-1] + fwd[t] - 1,   s[t] <= fwd[t]      (single-engage; H8)
edge cap      sum_t c[t] <= E_cap;   optional min-dwell L
```

With no cuts this is a min-TV shortest path solvable exactly by Viterbi DP in O(n * 9^2), pure Java. GCS is rejected for this (gcs-switched 2.2): the shared circle and global walls have no GCS home, and min-transitions is degenerate. The edge-cap MILP / TV encoding is the right home. Each Benders no-good cut is a small forbidden sub-assignment (a DFA); the product with the trellis stays a DP, degrading to a light custom B&B only when cuts accumulate.

### Gate-pattern handling

The per-axis 0.005 dead-zone is a state-triggered vanishing constraint (MPVC). Two-tier:

1. **Fold fixed point (incumbent-driven).** Relax-solve, replay, extract the realized gate pattern, refold the affine model with pattern-aware velocity walls, repeat. Converges in 2 rounds. This is `GateFoldFinder` + `FoldReplayDriver`. It must enforce both the zero side AND the keep-alive side; the COPT fold enforced only the zero side and the replay caught the rest.
2. **Exact 3-way branch (certificate closer).** On the residual low-velocity tail, branch each gate event ZERO / POS / NEG with `keepAliveWall`. This is already implemented in `CertifiedBnb` (byte states 1/2/3, interval propagation fixing POS when lo>=thr, NEG when hi<=-thr, ZERO inside (-thr,thr)). Rounding provably cannot fix a dead-zone (Sager-Bock-Diehl CIA is affine-in-control only; a single mis-fired gate is amplified ~11x by friction), so this branch is mandatory, not optional. A replay that violates a folded gate reopens it into the 3-way branch (premature-commitment guard).

### Incumbent engine (cold front end)

- **Wall homotopy continuation** (`WallHomotopyLadder`): relax landing/neo walls by delta (~0.05), solve clamp-free, tighten delta -> 0 in staged rungs, folding the gate pattern each rung. Scholtes relax-then-tighten; small rungs near active-set flips, restart on path loss at bifurcations. This is the measured cold incumbent source; it seeds the master's first incumbent per structure.
- **Switching-cost-aware rounding (SCARP)** turns a relaxed/fractional homotopy trajectory into a LOW-edge integer schedule (not just any feasible one), respecting the simplicity metric. It is a MIP-start factory, not a certifier.
- **Costate dual and feasibility pump are NOT incumbent engines here** (corridor-not-integrality; costate is warm-start only, violates walls 0.08..12.29 blocks). The costate/friction-funnel value serves only as an anytime prune bound inside the inner B&B.

### gap=0 certification

Declared when the disk/arc/SOC-RLT bound over the remaining lattice window meets a replay-verified incumbent. Acceptance test for any candidate is `JumpConstraintCompiler.maxViolation <= 0` on `ExactJumpModel` at FEAS_TOL=0. The master's LP/DP bound is a valid lower bound on edges throughout (a fractional 2.12 proves >= 3 edges). Termination: the sine grid is finite and every IIS no-good removes at least one lattice schedule, so the outer loop terminates at a certified global min-edges optimum; the RENS bucket-window leaf enumeration inside the inner solve is small (<= 3 buckets per ambiguous tick on modern chains) and each candidate is replayed.

## Component map onto existing Java

Package root: `core/.../anglesolver/solver/` and `core/.../anglesolver/noturn/`.

| Component | File | Reuse or build |
| --- | --- | --- |
| Single-quadratic disk node + weak-duality cert | `DiskSocpKernel.java` | Reuse; ADD endpoint tangent rows and SOC-RLT wall x disk rows |
| Arc chord cut geometry | `SineTableGeometry.java` | Reuse; ADD the two endpoint tangent cuts |
| Spatial arc B&B + exact 3-way gate branch + free start | `CertifiedBnb.java` | Reuse as-is (ZERO/POS/NEG + keepAliveWall already present) |
| Linear structure extractor (mMag, baseArg, coef, bPrime, p0coef, velocityWalls) | `JumpLinearModel.java` | Reuse; expose active-wall -> responsible-tick map for IIS cuts |
| Byte-exact oracle + FEAS_TOL=0 compiler | `ExactJumpModel.java`, `JumpConstraintCompiler.java` | Reuse; the terminal certificate |
| Gate fold fixed point | `GateFoldFinder.java`, `FoldReplayDriver.java` | Reuse |
| Cold incumbent continuation | `WallHomotopyLadder.java` | Reuse as the front end |
| Free start px/pz | `FreeStartSolve.java` | Reuse (also folded into the slave translation term) |
| RENS leaf bucket-window scan | `LeafSnap.java`, `BucketWalk.java` | Reuse; formalize as RENS optimal rounding over active-wall neighborhoods |
| Costate / funnel prune bound | `CostateDualSolver.java` | Reuse as anytime bound only |
| No-turn problem model, 9-combo alphabet, sprint latch, edge count | `noturn/NoTurnProblem.java`, `noturn/NoTurnKeys.java`, `noturn/NoTurnModel.java` | Reuse |
| Current beam finder (the heuristic being replaced) | `noturn/NoTurnFinder.java` | Superseded by the new driver; keep as fallback |
| **Structure-pool master driver (Benders): min-TV DP + IIS no-good cuts + edge-cap/latch/dwell rows** | new, e.g. `noturn/StructurePoolDriver.java` | **BUILD** |
| **IIS extractor (active-wall subsystem -> minimal tick-combo set)** | new, e.g. `solver/WallIis.java` | **BUILD** |
| **SCARP switching-cost rounding of relaxed trajectories** | new, e.g. `solver/SwitchingCostRound.java` | **BUILD** |

The existing `anglesolver/graph/` DAG (`BuiltinGraphs`, `GraphRunner`) is a solver-pipeline of stages, NOT a graph of convex sets. The Benders driver is a new node in that pipeline, not a reuse of GCS.

## Staged build plan

Target first global cold solve: **j1150 pure** (no-turn, no ja) and **j154 ja** (knife-edge, four walls at 1e-6).

1. **Inner node certification.** Add endpoint tangent cuts to `SineTableGeometry` and SOC-RLT wall x disk rows to `DiskSocpKernel`. Gate: the disk/arc bound is certified-exact (not just empirically tight) on the disk-vs-sphere corpus. Validates the S-lemma claim end to end before any outer work.
2. **IIS extractor.** From an infeasible slave, map the active walls back through `JumpLinearModel.coef` to the minimal responsible tick-combo set. Gate: on a hand-fixed infeasible j154 schedule, the extracted set reproduces the known responsible ticks.
3. **Structure-pool master.** Min-TV Viterbi over the trellis with assignment + sprint-latch + edge-cap rows, plus lazy IIS no-good cuts (product-automaton DP). Wire the slave = existing fold + homotopy + `CertifiedBnb` + replay. Gate: j1150 pure returns a certified min-edges no-turn with gap=0 replay, terminating (no external solver).
4. **Cold front end integration.** Drive the first incumbent per structure from `WallHomotopyLadder`; add SCARP to convert relaxed trajectories to low-edge starts. Gate: j154 ja solved cold (zero warm seed), min-edges certified.
5. **Free start + post-space turn folded into the slave.** px/pz as the linear translation term, the few free-facing turn ticks as a small separate byte-exact circle. Gate: both captures solve with the free-start box active.
6. **Harden and tag.** Wire into `anglesolver/graph/` as a pipeline node; add captures to `ProblemsTest`; tag the engine gate `@Category(SlowSolverTests.class)` (or VerySlow if multi-minute).

## Runner-up hammer

**A monolithic single-quadratic spatial branch-and-bound over the whole (a,b, free-start, combos, sprint, gate) MIQCP** with the eTRS-exact node relaxation and the friction-funnel admissible bound driving best-first search (the single-quadratic survey's node oracle, run monolithically instead of split by Benders).

When it would win: on EASY structures where a first incumbent is cheap to find (small n, wide corridors, few edges), the monolith avoids the master/slave round-trip overhead and the IIS bookkeeping, and its single tree shares bound information globally. It is dominated on the hard jumps precisely because cold incumbent finding is the bottleneck there (0 incumbents in 500k nodes), which is what forces the Benders split and the homotopy front end. If a future cold-incumbent primal heuristic made incumbents cheap on hard jumps, the monolith would become competitive again and simpler to maintain.

## Honest caveats

- **Worst-case complexity is exponential in the schedule.** The combinatorial rows (combos, sprint, min-edges) are MILP-hard; H8 legality quotients (sprint latch 2^n -> n+1, edge-cap 9^n -> ~1e8) shrink the reachable space to ~1e8..1e15, and IIS cuts each kill large slabs, but a jump with many near-tied low-edge families can still force many master iterations.
- **eTRS exactness needs the non-intersecting condition or SOC-RLT.** Where two walls cross inside the arc, the raw node bound has a tiny gap; the SOC-RLT rows and arc bisection close it, but this is the one place the "single quadratic => exact" slogan carries an asterisk (single-quadratic survey 2). Verify the node bound is certified on the disk-vs-sphere corpus before trusting the outer loop.
- **Wall homotopy is a path follower with no coverage guarantee.** It can lose the path at a bifurcation (active-set flip) and miss a basin. Mitigate with small rungs near flips and restart on path loss; the Benders certificate still holds for whatever incumbent it produces, but a missed basin can raise the first incumbent's edge count and widen the search.
- **Gate fold can commit prematurely.** A replay that violates a folded gate must reopen it into the 3-way branch, else the affine model is unfaithful and the certificate is void. The premature-commitment guard is mandatory.
- **Global certification is expensive on the hardest ja jumps.** j154 rides four walls at 1e-6 with tightest proven-safe clearance 4e-7; the arc must bisect deep and the SOC-RLT rows must be active for the node bound to certify, so per-node cost and iteration count are both highest exactly where the answer matters most.
- **The certificate is only as exact as `ExactJumpModel`.** gap=0 is against the finite 65536 lattice and the float replica; any divergence of the replica from real MC is a correctness bug that no amount of solver rigor detects. The replay is the truth, and it must stay byte-identical to `SimulatorEntity`.
