# Stage D shard: agent D12

- Agent: D12
- Territory (research topic): the CONVEX SOCP/QP solver KERNEL for pure-Java. What converging convex
  solver can the project ship (pure-Java) or justify as a redistributable dependency, to get the tight
  dual/SOCP bound and the disk-relaxation solution reliably at n up to ~353 (ARCH-1's bound step)?
- Files inspected (for the incumbent kernel and the measured failures):
  `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/CostateDualSolver.java` (the shipped
  Lagrangian dual, read in full), `.../solver/RelaxationRecovery.java` (the shipped AL-FISTA disk-SOCP, read
  in full); shards `stage0-copt/FINDINGS.md` (H1/H2, bound tightness, all COPT-measured), `stageA/agentA03.md`
  (A03-14 n=353 AL-FISTA failure), `stageA/agentA04.md` (A04-7 dependency net-negative), `SPEC.md` sections
  4-5 and ARCH-1.
- Web research (WebSearch/WebFetch, real citations only): OSQP, SCS, ECOS, Clarabel, Nesterov-Todd IPM
  iteration complexity, Nesterov smoothing, and a license/maturity survey of every Java-reachable candidate
  (ojAlgo, JOSQP, ALGLIB, Apache Commons Math, Clarabel4j).
- Did NOT run gradle or write code; all incumbent numbers are cited from Stage 0 / Stage A measurements and
  re-anchored to current code, all convergence-class claims are cited to the literature and labeled.

Convention: each finding is tagged ESTABLISHED (literature or a cited prior measurement) or SPECULATION
(a belief to route to a Stage E prototype). Applicability claims are tagged measured-against-our-model or
theoretical.

---

## D12-1: The convex core is a SMALL-CONE SOCP, and the shipped dual already IS its Lagrangian dual solved in the low-dimensional wall space
- LOCATION: research topic; anchored to `CostateDualSolver.java:5-36` (class doc), `costate()` 408-427,
  `buildHessian()` 451-490; `RelaxationRecovery.relaxedPrimal` 143-262.
- CLAIM: The disk relaxation `max c.u s.t. Au<=b, |u_t|<=m_t` is a second-order cone program whose ONLY
  cones are `n` tiny 2-D disks (one per tick) plus the `m` linear walls; its Lagrangian dual, dualizing only
  the walls, is exactly the function `CostateDualSolver` minimizes, `D(lambda)=Sum_t m_t||g_t|| + lambda.b`,
  which lives in the wall-count space `m` (m <= ~30 even when n is large). So the convex "kernel" already
  operates in a Schur-reduced low-dimensional space and already ships a dense Cholesky.
- EVIDENCE: `CostateDualSolver.java:5-15` states the dual verbatim; `buildHessian` (451-490) forms an
  `m x m` free-set Hessian and `choleskySolve` (494-519) factors it, i.e. the solve is O(m^3) in the wall
  count, not O(n^3). Measured wall counts (Stage 0 FINDINGS.md section 1): j021 n=39 has 12-22 walls; j001
  n=353 (A03-14) still has a small wall set. ESTABLISHED (code + cited counts). Applicability:
  measured-against-our-model.
- IMPACT: simplicity/speed (frames the whole kernel question): the latency bottleneck is NOT n; it is the
  wall count m, which is tiny. Any kernel that stays in wall/Schur space inherits the incumbent's microsecond
  cost class. The open problem is CONVERGENCE and PRIMAL RECOVERY, not speed.
- PROPOSAL: Treat the kernel search as "a conditioning-robust convex solve in the m-dimensional Schur space
  that ALSO returns the disk primal u," not "a faster solver."
- CONFIDENCE: 0.9
- DEPENDS-ON: -

## D12-2: FOUNDATIONAL, ESTABLISHED: the two solver families and their published convergence contrast
- LOCATION: research topic (literature).
- CLAIM: Two families solve this SOCP. (a) Primal-dual INTERIOR-POINT with Nesterov-Todd scaling: iteration
  count is nearly conditioning-INDEPENDENT, `O(sqrt(numCones) log(1/eps))` worst case, ~10-30 in practice,
  each iteration a KKT linear solve; returns tight primal AND dual. (b) First-order OPERATOR-SPLITTING
  (ADMM / Douglas-Rachford): cheap iterations (one cached factorization + cone projections), warm-startable,
  infeasibility certificates, but `O(1/k)` ergodic rate that degrades sharply with problem CONDITIONING.
- EVIDENCE (real citations):
  - IPM iteration complexity `O(sqrt(n) log 1/eps)` with the Nesterov-Todd direction: Alizadeh & Goldfarb,
    "Second-order cone programming," Mathematical Programming 95:3-51 (2003); foundation Nesterov & Todd,
    "Self-scaled barriers and interior-point methods for convex programming," Math. of OR 22(1):1-42 (1997)
    and "Primal-dual interior-point methods for self-scaled cones," SIAM J. Optim. 8(2):324-364 (1998).
    Practical embedded realization: Domahidi, Chu, Boyd, "ECOS: An SOCP Solver for Embedded Systems," ECC
    2013, pp. 3071-3076 (Mehrotra predictor-corrector, NT scaling, self-dual embedding, sparse LDL,
    library-free ANSI-C).
  - Operator-splitting `O(1/k)` and conditioning sensitivity: Stellato, Banjac, Goulart, Bemporad, Boyd,
    "OSQP: An Operator Splitting Solver for Quadratic Programs," Math. Prog. Computation 12(4):637-672
    (2020) (ADMM QP, factorization caching, warm start, primal/dual infeasibility detection); O'Donoghue,
    Chu, Parikh, Boyd, "Conic Optimization via Operator Splitting and Homogeneous Self-Dual Embedding,"
    JOTA 169:1042-1068 (2016) (SCS: ADMM over cones incl. SOC, returns primal+dual or an infeasibility
    certificate). The `O(1/t)` ergodic rate and its strong dependence on conditioning are standard (e.g.
    Nishihara et al., "A General Analysis of the Convergence of ADMM," ICML 2015).
  ESTABLISHED (literature). Applicability: theoretical.
- IMPACT: correctness/robustness (decisive for ARCH-1): the family choice is dictated by the RELIABILITY
  requirement at n=353. IPM's iteration count is robust to the exact ill-conditioning that makes the
  incumbent first-order kernel fail (D12-3).
- PROPOSAL: Prefer the interior-point family for the RELIABLE-at-large-n bound, unless a first-order kernel
  with proper preconditioning is measured to match it (Stage E A/B).
- CONFIDENCE: 0.85
- DEPENDS-ON: D12-1

## D12-3: MEASURED ROOT CAUSE of the incumbent kernel failure: RelaxationRecovery is a first-order augmented-Lagrangian penalty method, so it degrades exactly where conditioning is worst (n=353)
- LOCATION: `RelaxationRecovery.relaxedPrimal` 143-262 (AL-FISTA: FISTA inner loop 183-215, disk projection
  205-209, multiplier update 253-255, rho growth 256, `powerLambdaMax` Lipschitz estimate 264-297).
- CLAIM: The shipped disk-SOCP kernel is an augmented-Lagrangian method with an accelerated projected-gradient
  (FISTA) inner solve and a heuristic `rho` growth schedule. This is a first-order penalty method: its inner
  Lipschitz constant is `rho * lambda_max(A^T A)` (line 176), so as `rho` grows and the friction convolution
  conditioning worsens, the step `1/lip` collapses and the outer loop cannot drive the violation to zero.
- EVIDENCE (cited prior measurement, re-anchored): A03-14 measured, on j001 (n=353), the dual trips its
  divergence bail (`iters=23 pgres=1.452e+1 stalled=true`) and `relaxedPrimal` does NOT converge
  (`bestViol 15.5 after 731 ms`); on the coupled j021/j828 the disk method works but "the in-house disk SOCP
  is unreliable at large n." The rho-penalty conditioning dependence is the ESTABLISHED first-order behavior
  of D12-2 (ADMM/AL rate degrades with conditioning). measured-against-our-model (the failing capture) +
  theoretical (the mechanism).
- IMPACT: correctness/robustness (this is the ARCH-1 blocker the task names): "the AL-FISTA disk fails at
  n=353." The failure is INHERENT to the first-order-penalty choice, not a tuning bug; a better rho schedule
  buys a constant, not conditioning-robustness.
- PROPOSAL: Do not try to rescue AL-FISTA with rho tuning at n=353; replace the kernel with a
  conditioning-robust method (D12-7) or a properly-preconditioned operator-splitting solve (D12-8).
- CONFIDENCE: 0.85
- DEPENDS-ON: D12-2

## D12-4: "Fix the existing dual step" (the option-3 lever) closes the BOUND but is provably insufficient for the disk PRIMAL that ARCH-1 needs
- LOCATION: `CostateDualSolver.solve` 190-273 (MAX_ITER=100 cap 40, DIVERGE bail 51-53/237-239, u-space early
  exit 244-249), `newtonStep` 287-322, `gradientStep` 327-354; SPEC.md section 4.3.
- CLAIM: The dual `D(lambda)` is nonsmooth (the `||g_t||` terms are non-differentiable where `g_t=0`,
  smoothed only by `EPS2=1e-14`), and its truncated-Newton + projected-gradient hybrid stalls on the
  DEGENERATE FLAT FACE where the curvature `1 - ghat.ghat` vanishes (the class doc names this at
  CostateDualSolver.java:17-25). Converging the dual (a Nesterov-smoothed accelerated dual gradient, or a
  bundle/subgradient step with a proper line search) would tighten the BOUND, but on that same flat face the
  closed-form primal recovery `u*_t = m_t g_t/||g_t||` is ILL-DEFINED as `g_t -> 0`, so a perfectly converged
  dual still does not hand you the disk primal at exactly the degenerate ticks ARCH-1's residual consumes.
- EVIDENCE: bound looseness is measured to be pure non-convergence: Stage 0 FINDINGS.md section 2, shipped
  `dualBound` 1067.889761 vs COPT-converged SOCP disk 1067.865480 on j021 (loose by 0.0243 b), COPT solving
  the identical SOCP in <20 ms. The degenerate-face recovery failure is measured: A03-6 finds on j828 that
  13/39 ticks are off-sphere at the disk optimum and every one has `|g_t| <= 7.3e-10` (the `g=0` null-space);
  A03-14 finds the dual's u-space early exit (CostateDualSolver.java:244-249) lands off the true optimal face
  at `pgres=0.117`. For the accelerated-dual option: Nesterov, "Smooth minimization of non-smooth functions,"
  Math. Prog. 103:127-152 (2005) gives the `O(1/eps)` smoothing route for exactly this max-structured
  nonsmooth dual. ESTABLISHED (cited measurements + literature).
- IMPACT: correctness (scopes the option): fixing the dual step is WORTH ~0.024 b of bound (real, above the
  1e-4 certify floor) and is the cheapest change, but it is NOT a complete kernel for ARCH-1 because it does
  not produce the throttled disk primal. This matches SPEC 4.3: "Make the dual converge is the WRONG lever"
  for the recovery, right lever for the bound.
- PROPOSAL: Keep an accelerated/smoothed dual as the BOUND primitive (cheap, closes 0.024 b), but source the
  disk PRIMAL from a primal-dual method (D12-7), not from `u=m g/|g|` on the degenerate face.
- CONFIDENCE: 0.83
- DEPENDS-ON: D12-2, D12-3

## D12-5: LIBRARY SURVEY (verified licenses): NO maintained pure-Java SOCP solver exists; every real SOCP is native-C/Rust or GPL/commercial
- LOCATION: research topic (web-verified licenses and capabilities).
- CLAIM: Across every Java-reachable candidate, exactly two are permissive pure-Java, and NEITHER gives a
  reliable SOCP: ojAlgo's native SOCP is only partial, and JOSQP is QP-only. Full SOCP is available only from
  native (JNI/FFM) or copyleft/commercial libraries.
- EVIDENCE (web-verified): table below. ESTABLISHED (vendor pages / repos).

  | Library | License | Pure Java? | SOCP? | Maturity | Verdict for shipping |
  | --- | --- | --- | --- | --- | --- |
  | ojAlgo (optimatika) | MIT | YES, zero deps | QP mature; SOCP only PARTIAL natively | QP solver ~15 yrs, active; 94% QP success vs CPLEX | Candidate for QP subproblems; native SOCP not reliable |
  | ojAlgo-clarabel4j | MIT wrapper / Clarabel Apache-2.0 | NO (Java FFM to Rust native) | YES via Clarabel | new, "SOCP partial, planned" | REJECT: FFM needs Java 22+, native binaries; Forge is Java 8 |
  | JOSQP (quantego) | MIT | YES, Java 8+, zero deps | NO (QP only, linear constraints) | v0.6.5 on Maven Central, "at par with OSQP C"; 13 stars, low activity | Candidate as a QP kernel only, not SOCP |
  | Apache Commons Math3 | Apache-2.0 | YES | NO (SimplexSolver = LP only) | mature but was DROPPED (A04-7) | REJECT: LP-only, already removed |
  | ECOS (embotech) | GPLv3 | NO (ANSI-C, JNI) | YES (NT-scaling IPM) | mature, embedded | REJECT: GPL + native |
  | SCS (Boyd group) | MIT | NO (C, JNI) | YES (ADMM cones) | mature | REJECT: native (JNI + 3-platform binaries) |
  | OSQP (Boyd group) | Apache-2.0 | NO (C, JNI) | NO (QP; SOC via reformulation only) | mature | REJECT: native; not SOCP anyway |
  | Clarabel (oxfordcontrol) | Apache-2.0 | NO (Rust, C/FFM) | YES (IPM, LP/QP/SOCP/SDP) | modern, active | REJECT: native + FFM/Java 22+ |
  | ALGLIB | GPL (free) / commercial | YES (Java edition) | YES (dense+sparse SOCP) | mature | REJECT: GPL copyleft; commercial is paid |

- IMPACT: simplicity/robustness (decides the dependency question): the redistributable-dependency route is
  effectively closed. The only permissive pure-Java options are ojAlgo (QP, partial SOCP) and JOSQP (QP).
  Neither delivers a reliable SOCP; a dependency that DID (ECOS/SCS/Clarabel/ALGLIB) is native or copyleft.
- PROPOSAL: Rule out a shipped SOCP LIBRARY dependency on license+packaging grounds. If a dependency is ever
  taken, it can only be ojAlgo (MIT, pure Java) for its mature QP, never for SOCP.
- CONFIDENCE: 0.88
- DEPENDS-ON: -

## D12-6: The native-binding route (ECOS/SCS/Clarabel/ojAlgo-clarabel4j) is dead on the loader packaging matrix, independent of license
- LOCATION: research topic; AGENTS.md packaging rules (Forge 1.8.9/1.12.2 shade+relocate, Fabric include),
  A04-7 (`core/build.gradle:34`).
- CLAIM: Any C/Rust SOCP reached by JNI or the Foreign Function and Memory (FFM) API forces per-platform
  native binaries (win/mac-x64/mac-arm64/linux) shipped and loaded inside THREE loaders, two of which are
  Java 8 (Forge 1.8.9, 1.12.2) where FFM (finalized Java 22) does not exist and JNI would need a hand-written
  shim per MC/loader. This is strictly heavier than the imgui-java native burden the project already fights,
  and it violates the "core stays Minecraft-free / dependency-free preferred" invariant.
- EVIDENCE: AGENTS.md module table (Forge loaders are Java 8; Fabric is Java 25); ojAlgo's own page states
  the Clarabel path uses "Java's Foreign Function and Memory API" (WebFetch of ojalgo.org/2026/01/qp-news).
  A04-7 measured that re-adding even a PURE-JAVA LP library (commons-math3) is net-negative for packaging, so
  a native SOCP with per-platform binaries is a fortiori worse. ESTABLISHED (cited + web).
- IMPACT: robustness/simplicity (negative if adopted): a native SOCP dependency re-incurs and multiplies the
  exact cross-loader native-packaging cost the project paid to shed, for a kernel invoked on a minority of
  captures.
- PROPOSAL: Do not ship a native SOCP. If SOCP is needed on the shipped path, it must be pure-Java from
  scratch (D12-7).
- CONFIDENCE: 0.9
- DEPENDS-ON: D12-5

## D12-7: RECOMMENDED KERNEL: a from-scratch pure-Java primal-dual interior-point SOCP in wall/Schur space, reusing the incumbent's dense Cholesky
- LOCATION: research topic; build on `CostateDualSolver.buildHessian` 451-490 + `choleskySolve` 494-519
  (the dense factorization already exists in-repo).
- CLAIM: The theoretically-indicated conditioning-robust kernel is a bespoke primal-dual IPM (Mehrotra
  predictor-corrector, Nesterov-Todd scaling, self-dual embedding for infeasibility), pure-Java, exploiting
  that (a) all cones are 2-D disks (NT scaling is a trivial 2x2 per tick), (b) the diagonal 2x2 cone blocks
  Schur-eliminate into an `m x m` dense system in the tiny wall count. It returns the TIGHT disk bound, the
  disk PRIMAL `u` (including throttled `|u_t| < m_t` at the degenerate ticks), and the dual, in ~10-30
  iterations regardless of conditioning. The dense linear algebra is already in the repo (Cholesky in
  CostateDualSolver); the IPM is a moderate addition (~a few hundred lines), not a new dependency.
- EVIDENCE: iteration-count robustness and NT scaling are ESTABLISHED (Alizadeh-Goldfarb 2003; Nesterov-Todd
  1997/1998; ECOS/Domahidi-Chu-Boyd 2013 realize exactly this for embedded C). The Schur reduction to
  `m x m` follows from D12-1's measured tiny wall counts (Stage 0 FINDINGS.md section 1). That it PORTS to
  pure-Java within the perf envelope is SPECULATION (UNMEASURED-HYPOTHESIS) until a Stage E prototype.
  Applicability: theoretical (convergence) + measured-against-our-model (structure).
- IMPACT: correctness/robustness/simplicity (the ARCH-1 bound step): one primitive gives the tight bound AND
  the disk primal that seeds the low-dim residual, replacing BOTH the stalling dual bound and the failing
  AL-FISTA, with no shipped dependency. It converges at n=353 where AL-FISTA does not (D12-3).
- PROPOSAL: Stage E: prototype the 2-D-cone primal-dual IPM (NT scaling + Mehrotra) in the research harness
  first, validate the bound and primal against the COPT SOCP references (Stage 0 FINDINGS.md section 1a/2:
  j021 disk 1067.865480, j008b -0.195409, loopmm/j005/j016/j019/j022), then port. Benchmark iteration count
  and wall-clock at n=9,39,353.
- CONFIDENCE: 0.72
- DEPENDS-ON: D12-1, D12-2, D12-3

## D12-8: LATENCY ESTIMATE, pure-Java SOCP vs the 0.1-800 ms envelope: dominated by wall count m, not n, so both n<=49 and n=353 fit
- LOCATION: research topic; anchored to the `m x m` Schur structure (D12-1) and COPT timings (Stage 0).
- CLAIM: With the diagonal 2x2 cone blocks Schur-eliminated, each IPM iteration costs `O(n*m + m^3)`; at
  m <= ~30 the `m^3` term is negligible and the `n*m` assembly dominates. Estimate: n<=49 solves in low
  single-digit ms (COPT does the same SOCP in <20 ms in C; a pure-Java dense IPM in m-space should be
  comparable-to-faster because m is tiny); n=353 solves in tens of ms (assembly O(353*30) per iter x ~25
  iters x 2 axes, plus a 30x30 Cholesky), well inside 800 ms. A first-order operator-splitting kernel would
  be even cheaper per iteration but needs more iterations and preconditioning at n=353 (D12-9).
- EVIDENCE: COPT SOCP <20 ms at n=39 (Stage 0 FINDINGS.md section 2) and 0.13 s for the free-start SOCP at
  n=39 (section 5); the incumbent dual already solves the m-space Cholesky in microseconds
  (CostateDualSolver, class doc "a few microseconds"), proving the m-space solve is not the bottleneck. The
  pure-Java IPM wall-clock itself is UNMEASURED-HYPOTHESIS. Applicability: measured-against-our-model
  (structure + COPT reference) + theoretical (the per-iteration flop count).
- IMPACT: speed (removes the latency objection): the kernel choice is NOT constrained by the envelope at any
  n the tool hits; it is constrained only by convergence RELIABILITY, which is the whole D12-2 argument for
  IPM over the incumbent first-order method.
- PROPOSAL: Stage E benchmark: warmup then repeated timed runs of the IPM prototype at n in {9,39,49,353},
  report medians+spreads, compare to the incumbent dual+AL-FISTA wall-clock on the same captures.
- CONFIDENCE: 0.7
- DEPENDS-ON: D12-1, D12-7

## D12-9: FALLBACK KERNEL if the IPM proves too much: a proper operator-splitting SOCP (SCS-style cone-projection ADMM), which is strictly better-founded than the incumbent AL-FISTA
- LOCATION: research topic; contrast with `RelaxationRecovery.relaxedPrimal` 143-262.
- CLAIM: If a from-scratch IPM is judged too risky, the next-best pure-Java kernel is a real operator-splitting
  SOCP: factor the KKT/quasi-definite system ONCE and reuse it every iteration (OSQP's design), project onto
  the product of 2-D disks and the nonnegative orthant each iteration (SCS's cone handling), with a
  homogeneous self-dual embedding for primal/dual infeasibility certificates. This is a principled upgrade of
  the incumbent AL-FISTA (which re-derives a Lipschitz step and grows a penalty `rho` every outer iter,
  D12-3): the cached factorization plus Ruiz/diagonal preconditioning is what makes ADMM survive the
  conditioning that breaks the penalty method. Warm-starting across the margin ladder is native to this
  family and matches the incumbent's warm-lambda pattern.
- EVIDENCE: OSQP factorization-caching + warm-start + infeasibility detection (Stellato et al. 2020); SCS
  cone-projection ADMM returning primal+dual or an infeasibility certificate (O'Donoghue et al. 2016). A
  pure-Java OSQP port EXISTS and is MIT-licensed and "at par with OSQP C" (JOSQP, quantego, WebFetch of the
  repo), proving the operator-splitting inner loop ports cleanly to pure Java; it is QP-only, so the SOC
  projection would have to be added, but the ADMM skeleton and the KKT factorization are demonstrated. That
  a preconditioned ADMM reaches the required tolerance at n=353 is SPECULATION (UNMEASURED-HYPOTHESIS).
  Applicability: theoretical.
- IMPACT: robustness/simplicity (a lower-effort pure-Java path than the IPM): cheaper to implement than an
  IPM, keeps the warm-start ladder, but carries residual conditioning risk at n=353 that must be measured.
- PROPOSAL: Stage E: if the IPM (D12-7) slips, prototype an SCS-style ADMM (KKT factor once + disk/orthant
  projections + Ruiz preconditioning + self-dual embedding) and measure whether it certifies j001 (n=353) to
  tolerance, which AL-FISTA cannot. JOSQP's KKT-solve code is an MIT-licensed reference to study (not ship).
- CONFIDENCE: 0.7
- DEPENDS-ON: D12-3, D12-5

## D12-10: VERDICT: dependency-free pure-Java IPM SOCP as the rescue kernel; NO redistributable SOCP library justified; "fix the dual" closes the bound but not the primal
- LOCATION: research topic (synthesis).
- CLAIM: Ranked recommendation for ARCH-1's converging-convex-solve step:
  1. KEEP the incumbent dual on the small-n majority. Stage 0 FINDINGS.md section 2 measured it TIGHT to
     ~1e-6 on single/easy captures (j005/j016/j019/j022) in microseconds; a new kernel is a RESCUE for the
     coupled/large minority, not a hot-path replacement. Do not regress the fast path.
  2. BUILD a from-scratch pure-Java primal-dual IPM SOCP (D12-7) as the rescue kernel: it is the only route
     that is simultaneously redistributable (no license issue), pure-Java (no native binaries across three
     loaders, D12-6), conditioning-robust (converges at n=353 where AL-FISTA fails, D12-3), and complete
     (returns the tight bound AND the disk primal the low-dim residual needs, unlike a converged dual, D12-4).
     Latency is a non-issue because the solve lives in the tiny wall/Schur space (D12-1, D12-8).
  3. OPTIONALLY add an accelerated/smoothed dual (Nesterov 2005) purely for the BOUND if the IPM is deferred;
     it closes the measured 0.024 b gap but does not solve the primal recovery (D12-4).
  4. REJECT a shipped SOCP LIBRARY: no permissive pure-Java SOCP exists (ojAlgo SOCP partial, JOSQP QP-only,
     commons-math LP-only), and every real SOCP (ECOS GPL, SCS/OSQP/Clarabel native, ALGLIB GPL/commercial)
     fails the license or the packaging test (D12-5, D12-6). ojAlgo (MIT, pure Java) is the ONLY dependency
     that could ever be justified, and only for its mature QP, never SOCP.
  5. FALLBACK: if the IPM slips, a preconditioned SCS-style ADMM (D12-9) is the pure-Java second choice,
     strictly better-founded than the incumbent AL-FISTA.
- EVIDENCE: aggregates the cited measurements (Stage 0 section 2 tight-on-easy / loose-by-0.024-on-coupled;
  A03-14 n=353 AL-FISTA viol 15.5; A04-7 dependency net-negative) and the ESTABLISHED convergence-class
  literature (D12-2). The pure-Java IPM's realized robustness and wall-clock are the Stage E measurement that
  closes this verdict (UNMEASURED-HYPOTHESIS until then).
- IMPACT: correctness/robustness/simplicity (the make-or-break for ARCH-1's bound step): a single pure-Java
  primal-dual primitive replaces the stalling dual bound and the failing disk relaxation, with no dependency
  and no envelope regression.
- PROPOSAL: Route D12-7 (IPM) as the primary Stage E kernel prototype, D12-9 (ADMM) as the measured fallback,
  benchmarked against the COPT SOCP references and byte-exact round-tripped through ExactJumpModel per SPEC
  section 7.
- CONFIDENCE: 0.75
- DEPENDS-ON: D12-1, D12-2, D12-3, D12-4, D12-5, D12-6, D12-7, D12-8, D12-9

---

## Citations (all real, checkable)

- B. Stellato, G. Banjac, P. Goulart, A. Bemporad, S. Boyd. "OSQP: An Operator Splitting Solver for
  Quadratic Programs." Mathematical Programming Computation 12(4):637-672, 2020.
- B. O'Donoghue, E. Chu, N. Parikh, S. Boyd. "Conic Optimization via Operator Splitting and Homogeneous
  Self-Dual Embedding." Journal of Optimization Theory and Applications 169:1042-1068, 2016. (SCS)
- A. Domahidi, E. Chu, S. Boyd. "ECOS: An SOCP Solver for Embedded Systems." European Control Conference
  (ECC) 2013, pp. 3071-3076.
- F. Alizadeh, D. Goldfarb. "Second-order cone programming." Mathematical Programming 95:3-51, 2003.
  (survey; O(sqrt(n) log 1/eps) IPM iteration complexity, NT direction)
- Yu. Nesterov, M. J. Todd. "Self-scaled barriers and interior-point methods for convex programming."
  Mathematics of Operations Research 22(1):1-42, 1997; and "Primal-dual interior-point methods for
  self-scaled cones." SIAM J. Optimization 8(2):324-364, 1998. (NT scaling foundation)
- Yu. Nesterov. "Smooth minimization of non-smooth functions." Mathematical Programming 103:127-152, 2005.
  (smoothing, O(1/eps) accelerated first-order for max-structured nonsmooth objectives)
- R. Nishihara, L. Lessard, B. Recht, A. Packard, M. Jordan. "A General Analysis of the Convergence of
  ADMM." ICML 2015. (ADMM rate and conditioning dependence)
- P. J. Goulart, Y. Chen. "Clarabel" interior-point conic solver (Rust/Julia), Apache-2.0, oxfordcontrol.

## Library license/maturity references (web-verified this session)

- ojAlgo (optimatika/ojAlgo): MIT, 100% pure Java, zero deps; mature QP (~15 yr), PARTIAL native SOCP; full
  SOCP only via ojAlgo-clarabel4j (Java FFM to Clarabel/Rust). https://www.ojalgo.org , qp-news 2026-01.
- JOSQP (quantego/josqp): MIT, pure Java 8+, zero deps, QP-only (no SOCP), v0.6.5 Maven Central, "at par with
  OSQP C." https://github.com/quantego/josqp
- ECOS (embotech/ecos): GPLv3, ANSI-C (native). https://github.com/embotech/ecos
- SCS: MIT, C (native). OSQP: Apache-2.0, C (native, QP-only).
- Clarabel (oxfordcontrol/Clarabel.rs): Apache-2.0, Rust (native; C/Python/R/FFM interfaces).
- ALGLIB (alglib.net): dual-licensed GPL (free) / commercial; Java edition with dense+sparse SOCP.
- Apache Commons Math3: Apache-2.0, SimplexSolver = LP only (already dropped from the shipped path, A04-7).
