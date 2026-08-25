# Agent B05 shard: hot leaf functions and their inner loops

Territory: the hot leaf functions and inner loops of a solve, and where an incremental / cached / bounded
form helps. Measured, not estimated, wherever a number appears.

Files inspected:
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/CostateDualSolver.java`
  (`buildHessian`, `choleskySolve`, `newtonStep`, `costate`, `grad`, `solve`).
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpLinearModel.java`
  (`compileWall`, `coefAxis`, the prefix-friction coupling that shapes the wall `coef[]` support).
- `core/src/main/java/.../solver/BucketAscentPolish.java`, `IlsPolish.java` (score kernel, block1/block2 scans),
  `TrustRegionLp.java` (revised simplex).
- `docs/research/dual-newton-iteration-audit.md` section 6, `docs/research/angle-solver.md` section 5.1.

Commands run (direct `java -cp`, JDK 25, NO gradle; classpath from `core/build/test-classpath.txt`):
- `LeafBench` (scratch, compiled against test classpath): drives the REAL objects from a capture save
  (`AngleSolverEngine.debugBuildSpec` -> `JumpSpec` -> `JumpLinearModel.compileWalls` -> `CostateDualSolver`,
  plus `ExactJumpModel.forward`). Modes: `opcount` (deterministic, contention-free op-counts on the real
  compiled walls over all 55 captures) and full timing (self-calibrating: ~0.8 s warm, 25 timed rounds,
  median/p10/p90 ns per call). buildHessian and choleskySolve are timed via faithful arithmetic replicas
  (identical loop bodies) on the real `coef[]` arrays because both are private.
- Trace re-count over 137 `build/reports/solver-trace-*.txt` for the SLP/TrustRegionLp reject rate.
- Op-count cross-check against the COPT `research/copt/data/struct-*.json` compiled-wall dumps.

---

## B05-1: buildHessian's inner MAC loop is uncapped and is the hottest solver leaf; capping it at each wall pair's last coupled tick is a BIT-IDENTICAL 65% cut of buildHessian

LOCATION: `CostateDualSolver.buildHessian` (the triple loop: outer over the free set `nf`, inner
`for (int t = 0; t < n; t++)` MAC `sum += wOverNrm[t]*cc*(...)`). Wall support set by
`JumpLinearModel.coefAxis` / `compileWall`.

CLAIM: The inner loop runs `t = 0..n` for every free-wall pair even though each wall's `coef[]` is nonzero
only on a prefix `[0, lastCoupled]` (causal friction coupling: `coefAxis(axis,s,k)=0` for `s>=k`), so capping
the bound at `min(lastCoupled_i, lastCoupled_j)+1` drops only trailing zeros and is numerically bit-identical
while removing ~65% of the multiply-accumulates on the CPU-dominant captures.

EVIDENCE (measured):
- Loop bound is uncapped: source read confirms `for (int t = 0; t < n; t++)` with an inner `if (cc==0.0)
  continue;` that still iterates and loads `ci[t]*cj[t]` across the full `n`.
- Structural op-count over all 55 captures (`LeafBench` opcount mode, real compiled walls,
  full = `n * m(m+1)/2`, capped = `sum_pairs min(lastCoupled_i,lastCoupled_j)+1`):
  per-capture saving ranges 11.8% (j005, n=51 m=10, walls cluster at ticks 41-50) to 71.0%
  (gh398-optimize-2jump / bfsetup2). Unweighted median 43.0%. Op-weighted by `m^2*n` (where buildHessian
  CPU actually goes) = **64.8%** (total full 4,403,214 -> capped 1,549,015 MACs).
- Timed (self-calibrated, median of 25 rounds) on the CPU-dominant grinding captures, faithful replica on real
  `coef[]`: razor-proof (n=49,m=20) full 10.6-19.1 us -> capped 5.5-9.7 us, saving **48-49%**;
  taser-80t (n=79,m=32) full 40-72 us -> capped 16-29 us, saving **59-60%**; j001 (n=354,m=81) full 0.92 ms
  -> capped 0.44 ms, saving **52%**. Measured saving tracks the op-count to within a few points.
- Bit-identical: full and capped accumulate the same H (LeafBench sink equal to printed precision, e.g. j005
  8.84e+09 == 8.84e+09); the cap removes only the zero suffix, so iterates and convergence are unchanged.
- Hottest-leaf confirmation: on razor-proof the whole dual `solve` is 1191-1608 us over 100 iters
  (~12-16 us/iter) and buildHessian at `nf=m` alone is ~the entire iteration cost; corroborates the audit's
  JFR 43% leaf (`dual-newton-iteration-audit.md` s6). The grinding population that pays this: razor-proof 100
  iters/solve, taser-80t 99, j021 100, vs converged loopmm 38 and j005 2.

IMPACT: speed. buildHessian is ~43% of solver leaf CPU (audit JFR); a bit-identical 64.8% cut of it is
~**28% of solver leaf CPU** removed with ZERO iterate change. Unlike every iteration-cap / exit-heuristic in
the audit (all measured dead via the non-monotone lottery), this touches no iterate, so it cannot break the
corpus: it is exactly the audit s7 "bit-compatible on converged solves, strictly better on capped ones" shape,
which the audit believed did not exist for buildHessian. It does.

PROPOSAL: precompute `lastCoupled[j]` once per wall set (last nonzero index of `coef[j]`, or `t1`/`max(t1,t2)`
from `compileWall`); change the inner bound to `t < min(lastCoupled_i, lastCoupled_j)+1`; drop the now-dead
`if (cc==0.0) continue;`. One-time O(m*n) precompute amortized over ~100 buildHessian calls per grinding solve.

CONFIDENCE: 0.9. DEPENDS-ON: none.

CAVEATS:
- Production buildHessian loops over the free set `nf <= m`; my timing uses `nf=m` (worst case), so absolute
  ns is an upper bound, but the SAVING FRACTION is a property of `coef[]` support and is independent of `nf`.
- The saving is capture-shape dependent: run-up-padded single-jump specs cluster walls late (12-17% saving);
  spread multi-wall runs (j001/deserthard/taser/trp/nix/razor) save 52-71%. Because buildHessian cost scales
  as `m^2*n`, the high-m spread runs dominate total CPU, hence the 64.8% op-weighted figure. The
  COPT `struct-*.json` dumps are jump-window-trimmed (e.g. j005 n=9) and show higher per-capture savings than
  the engine's padded spec (j005 n=51); the engine spec is the production one.

---

## B05-2: choleskySolve is a fresh full factorization every call, refactored up to 8x per Newton iteration, never updated across the damping retry where only the diagonal changes

LOCATION: `CostateDualSolver.choleskySolve` (fresh LDL^T from `H` each call), called inside
`newtonStep`'s `for (int retry = 0; retry < 8; retry++)` damping loop; `buildHessian` is called once per
`newtonStep`, `choleskySolve` up to 8 times.

CLAIM: The factorization is recomputed from scratch on every damping retry within a single Newton step, even
though consecutive retries change only `H[a][a] += damp` (a scalar on the diagonal), and it is never reused
across iterations; it is the second solver leaf after buildHessian.

EVIDENCE (measured):
- Source: `choleskySolve` builds `Lwork` from `H[a][b] + (a==b?damp:0)` with no incoming factor; `newtonStep`
  calls it with a growing `damp` (`rhoRel *= 4`) up to 8 times, each a full O(nf^3/3) refactor.
- Single-call cost ratio chol/hessian at `nf=m` (LeafBench, damped-PD H): razor-proof 0.168, taser-80t 0.160,
  j021 0.052, loopmm 0.101/0.083. So ~16% of one buildHessian on the mid captures.
- Reconciles with the audit's 18%/43% = 0.42 leaf ratio: the single-call 0.16 is amplified by the retry
  multiplicity (up to 8 refactors per Newton step vs 1 buildHessian) plus real `nf<m`; 0.16 x ~2-3 avg retries
  ~= 0.3-0.5.

IMPACT: speed, ~18% of solver leaf CPU. Smaller and riskier lever than B05-1. A retry that only bumps the
diagonal admits a bit-identical rebuild that reuses the columns unaffected by the larger `damp` (or a running
LDL update), but the audit flags this method as the bit-identical-only zone, so only a provably bit-identical
reuse is admissible.

PROPOSAL: Lower priority than B05-1. If pursued: within the `newtonStep` retry loop, factor once and, on
retry, update for the pure-diagonal `+delta*I` change rather than refactoring from scratch; keep it bit-exact
or leave it. Also: `H`/`Lwork` are already preallocated `[m][m]` scratch (good, no per-call alloc).

CONFIDENCE: 0.8. DEPENDS-ON: B05-1 (same function family, same bit-identical constraint).

---

## B05-3: BucketAscentPolish / IlsPolish rescore every block1/block2 candidate with a full tick-0 forward; stepRange would halve the stepper but toGameFacings and maxViolation must also go incremental

LOCATION: `BucketAscentPolish.score` and `IlsPolish.score` (`gf = toGameFacings(wrapAll(abs)); pr =
model.forward(sc, gf); c.maxViolation(gf, pr)`), scanned by `block1` (single-tick) and `block2` (tick-pair).
`ExactJumpModel.stepRange(from,...)` is present but has no caller (F12).

CLAIM: This is the anytime hot loop; each candidate perturbs one (block1) or two (block2) ticks yet pays a
full O(n) forward from tick 0 plus a full toGameFacings and a full wall scan; routing the forward through
`stepRange(from = first changed tick)` recomputes only the tail `[from, n)`, ~2x on the stepper, but only if
the game-facing map and the violation check are made incremental too.

EVIDENCE (measured):
- One forward eval (LeafBench, `model.forward` incl `ForwardPath` alloc + `getPos`), median of 25 rounds:
  razor-proof (n=49) 1174 ns, taser-80t (n=79) 1882 ns, loopmm (n=71) 1888 ns, j021 (n=175) 5977 ns,
  j001 (n=354) 7762 ns. (A02 quoted 504 ns at n=49; mine is ~2.3x at the same n, machine + scope difference:
  A02 timed a bare stepper, this includes alloc/getPos. Direction and n-scaling agree.)
- Evals per full pass at n=49 (LeafBench, derived from the `Config` schedules): FAST block1 = 21,364 per pass
  (x up to 60 iters/window), block2 = 595,725 per pass; THOROUGH block1 = 57,918, block2 = 3,810,807 per pass,
  x up to 12 rounds + 8 restarts. THOROUGH's block2 dominates and reproduces A02's ~36M-forward order for a
  full climb; at ~1.2 us/forward that is ~40 s of stepper work if uncapped (it is deadline/cancel bounded).
- stepRange saving is structural: block1 scans tick `t` uniformly over `0..n-1`, only ticks `>= t` change, so
  the average recompute fraction is `(n+1)/(2n) ~= 0.5` -> ~2x on the forward. block2 from `min(i,j)` is the
  same on average.

IMPACT: speed, ~2x on the dominant polisher cost, but ONLY on the forward stepper. `toGameFacings` (O(n)) and
`JumpConstraintCompiler.Compiled.maxViolation` (O(walls), each wall an O(n) dot in general) also full-recompute
in `score`; the full 2x is realized only if those become tail-incremental too, else the win is partial.

PROPOSAL: wire `stepRange` into a `scoreFrom(model, ..., changedTickLo)` used by block1/block2, caching the
pre-`from` forward state and the game-facings prefix; make `maxViolation` skip walls whose `t1 < from`. Prove
bit-identical against the full forward on the corpus (the polishers gate on FEAS_TOL=0, so any drift clips a
feasible result). Prototype before claiming the 2x end-to-end.

CONFIDENCE: 0.75 (2x on the stepper is structural; end-to-end 2x is UNMEASURED pending the toGameFacings /
maxViolation incrementalization). DEPENDS-ON: none.

---

## B05-4: TrustRegionLp rejects 86.2% of its LP factorizations to trust-region probing; ~26 LPs per SLP solve, phase-2 the worst

LOCATION: `TrustRegionLp` (revised simplex, `run` -> `iterate` -> `pickEntering` / `pivotOrFlip` / `refactor`),
driven by `SlpSolve` with a binary halve-on-reject / conditional-double-on-accept trust region.

CLAIM: The LP kernel itself is cheap; the waste is trust-region management, which throws away ~86% of the LPs
it solves near a hugged wall, with phase-2 the worst rejecter.

EVIDENCE (measured, re-counted over 137 `solver-trace-*.txt`):
- 144,450 LP solves across 5,591 SLP invocations = **25.8 LP per SLP solve**; **86.22% reject**
  (124,552 reject / 19,898 accept); **7.26 LP per accepted step** (1 accept + ~6.26 halving rejects).
- Phase split: phase-1 84.6% reject (58,294/68,910), phase-2 **87.7% reject** (66,258/75,540). Phase-2 (the
  objective phase hugging a tight level set) is the worst, matching A04/F2.
- Per-LP structural cost (revised simplex, m constraints, total = 2n+1+m vars): per pivot `computeDuals`
  O(m^2) + `pickEntering` O(total*m) = O((2n+m)*m) + `pivotOrFlip` O(m^2); `refactor` O(m^3) every 100 pivots.
  `pickEntering`'s full scan over `total` variables dominates a pivot. (Op-count from source; not separately
  timed because TrustRegionLp is package-private.)

IMPACT: speed. Audit JFR: TrustRegionLp ~11% of solver CPU; 86.2% of its LP factorizations are rejected TR
probes. F2 bounds the recoverable at ~5% of solver CPU (not all rejects are removable; some are the necessary
binary search for the TR boundary on the float lattice). Measure-gated, knife-edge risk per the audit record.

PROPOSAL: replace the curvature-free halve/double TR with a predicted-vs-actual-reduction ratio model (classic
trust-region radius update) so the radius lands near the accepted step in 1-2 tries instead of ~7; keep Bland's
anti-cycling. A02/A04 already flag this; do not re-add an external LP library (measured net-negative packaging).

CONFIDENCE: 0.85 (counts re-verified; the 5% recoverable is the audit's bound, not independently re-timed).
DEPENDS-ON: none.

---

## B05-5: leaf ranking of a THOROUGH solve, and the top 3 measured levers

CLAIM: A THOROUGH solve's wall-clock splits between (a) dual-bound work (closed-form fast path + B&B node
bounds + degenerate grinds) where buildHessian and cholesky dominate, and (b) the anytime byte-exact polish
(BucketAscent/IlsPolish inside seam-sweep/B&B/ILS) where the full-forward score kernel dominates.

EVIDENCE:
- Leaf shares (audit JFR over `:core:test -PslowTests`, dominated by ProblemsTest B&B = 935k dual solves):
  buildHessian 43%, cholesky 18%, TrustRegionLp ~11%. Independently corroborated here: buildHessian is ~the
  full dual iteration on the grinding captures (razor-proof ~12-16 us/iter, buildHessian at nf=m ~that), and
  the grind population runs 99-100 iters/solve.
- The forward-eval polisher is deadline/cancel-bounded, so its share is capped by the budget, not by
  convergence; per the eval counts (B05-3) it would otherwise be tens of seconds per THOROUGH climb.

RANKED LEVERS (measured achievable saving):
1. **Cap buildHessian's inner loop** (B05-1): op-weighted **64.8%** of buildHessian, bit-identical, ~**28% of
   solver leaf CPU**. Biggest measured lever, zero corpus risk (no iterate change). Measured 48-60% buildHessian
   saving on razor-proof/taser/j001.
2. **stepRange-backed incremental rescoring** in BucketAscent/IlsPolish (B05-3): ~**2x** on the dominant
   polisher's forward stepper (structural; end-to-end pending toGameFacings/maxViolation incrementalization).
3. **Curvature-aware TrustRegionLp** (B05-4): cut the measured **86.2% LP reject rate**; ~**5% of solver CPU**
   recoverable per F2 (measure-gated).

IMPACT: speed. Combined, levers 1+3 are bit-safe/measure-safe and remove ~30%+ of solver leaf CPU on the
grinding population; lever 2 targets the anytime tail. None regress objective or feasibility.

CONFIDENCE: 0.85. DEPENDS-ON: B05-1, B05-3, B05-4.

---

## Method notes / re-verification against the handoffs

- angle-solver.md 5.1 "capping buildHessian's inner loop at each wall's last coupled tick roughly halves the
  dominant per-iteration cost": CONFIRMED and slightly conservative for the CPU-dominant captures (op-weighted
  64.8%, measured 48-60%). The claim's "each wall's last coupled tick" is exactly `lastCoupled` = last nonzero
  `coef[]` index; the pair bound is `min` of the two.
- dual-newton-iteration-audit.md s6 "buildHessian 43% leaf (hottest single line: the O(walls^2 x ticks) multiply
  ... at 460)": the referenced line is the inner MAC `sum += wOverNrm[t]*cc*(...)`; CONFIRMED as the hottest.
  (Line numbers in CostateDualSolver.java shifted between my reads during this session, likely a concurrent
  campaign edit; I cite method names, not line numbers.)
- dual-newton-iteration-audit.md s7 "buildHessian/choleskySolve are already in the bit-identical-only zone" is
  the constraint, not a dead end: the buildHessian cap SATISFIES it (drops only zeros), so it is a legal
  speedup where every iterate-perturbing change in that record was dead.
- A02-6 forward-eval "504 ns at n=49": my razor-proof n=49 forward is 1174 ns (2.3x); attribute to machine +
  the ForwardPath alloc/getPos in the real `score` path. The ~36M-forward THOROUGH climb order is reproduced.
- SYNTHESIS F2 "87.5% reject, 8.01 LP/accept" (109 traces): re-count over 137 traces gives 86.2% / 7.26,
  consistent to the corpus difference.
