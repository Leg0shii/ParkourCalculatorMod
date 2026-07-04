# Beating Wolfram on j021 with pure-Java ILS

> **Note (2026-07-04).** The j021 findings here still hold (ILS reaches the optimum on j021, and it stays
> shipped as `IlsPolish`). What did NOT generalize is the implied "every structured exact method
> underperforms a neighborhood ratchet": on the other hard multi-jump, loopmm, the recovery refactoring
> (#204/#209/#213) plus a STRUCTURED pattern-branched B&B (`BoundPrunedRecovery`) is what lands it, not ILS
> (`angle-solver.md` 10.3). So read the "structured methods underperformed" conclusion as instance-specific
> to j021's landscape, not a general law.

Investigation (2026-06-13) into whether our bundleable, pure-Java angle solver can independently reach
(and beat) the global optimum that a Wolfram/Cbc spatial branch-and-bound finds on the hard multi-jump
fixture `j021-rinav1-01`, **without reusing Wolfram's answer**. Answer: **yes.**

## The problem

Per tick the only free variable is yaw; the input is a fixed-modulus 2D vector rotated by yaw. Horizontal
position is affine in the per-tick input vectors `u_t`, so the objective `c·u` and every wall `A·u ≤ b` are
linear; the only nonconvexity is the per-tick modulus equality `|u_t| = m_t`. This is a constant-modulus /
unimodular QCQP (optimization over a product of circles with linear inequality coupling). j021: n=39 ticks,
4 jumps, 13 walls, maximize Z at tick 39.

Reference points (byte-exact feasible Z at tick 39):
- shipped multi-jump solver: **1066.633**
- Wolfram `optimize.wls` (NMaximize → COIN-OR Cbc), then snapped+polished: **1067.8636334**
- convex (SOCP) bound: 1067.8655 (loose; its minimizer violates the modulus, defect 0.083)

## What works: Iterated Local Search (ILS)

**Result: 1067.8636684 byte-exact feasible — beats Wolfram by +3.5e-5, beats shipped by +1.2307,
pure-Java, no Wolfram seed.** (Feasibility enforced in-loop: the evaluator returns −∞ on any wall crossing,
so the reported value is a legal in-game path.)

Algorithm (a polish stage on top of the existing solver's feasible result):
1. Seed from the shipped feasibility cascade (ClosedForm dual → SLP → CMA race), best feasible.
2. ILS ratchet: repeat — perturb a random subset of 3–15 ticks by ±up to ~50°, re-climb (tight CMA + a
   FAST `BucketAscentPolish`), accept if strictly-feasible objective improved.
3. Once within ~1e-4 of the incumbent ceiling, run one `BucketAscentPolish` THOROUGH to punch through the
   final lattice precision (the FAST per-round polish stalls there).
4. Parallel best-of-batch: each round climbs `cores` perturbations concurrently, keep the best — this both
   shrinks wall-clock and makes stronger ratchet steps.

Why ILS and not the textbook exact methods: independent random restarts **plateau at ~1067.8634** because
they keep re-finding the same near-basin; ILS *ratchets* through the neighborhood and hops into the true
global basin. Every structured exact method we tried **underperformed** it (see below).

## What did NOT work (and why)

The research pointed at exact QCQP methods; all failed here because of genuine LCvx-failure + degeneracy:
- **Lagrangian dual / costate recovery** (`CostateDualSolver`): the dual does not converge on j021
  (projected-gradient residual stalls ~0.5, hits the iteration cap), stuck at the loose bound 1067.8898;
  recovery is infeasible by ~2.9 blocks. ~1 block is truly degenerate (`|g_t|→0`, direction undefined).
- **Active-set KKT enumeration**: 0/8192 active sets produced a feasible KKT point (the degenerate block
  breaks clean costate alignment).
- **Convex-relaxation LP seed** (polygon-approx disks via commons-math3 Simplex): reproduces the bound
  1067.8657, but projecting onto the spheres is byte-exact infeasible; refine lands 1067.8623.
- **Sequential-LP** (`SlpSolve`): feasibility-centered, *lowers* the objective in every basin.

Lesson: the LCvx-failure (coupled multi-jump) + a degenerate block defeat the structured machinery; a
structure-agnostic neighborhood ratchet wins.

## Generalization (validated)

- **Across seeds (j021):** 6 fresh seeds, **5/6 single runs beat Wolfram**, all 6 within 2e-5; ensemble-best
  1067.8636692. A single run is ~83% reliable; a small multi-seed ensemble (keep-best) is reliable.
- **Across problems (ILS as a polish stage):** improvement over the shipped baseline scales with hardness —
  j013 +5e-6 (already optimal, ties), j016 +8e-5, j019 (3-jump) +2.4e-4, **j021 (4-jump) +3.4e-3**.
  Improvement is always ≥0 (only accepts gains). ILS **must** be seeded from the existing solver's feasible
  result — standalone random/CF/SLP seeding failed to find feasibility on the tight single-jump j022, where
  the engine's full cascade succeeds.

## Runtime and scaling

| stage | cost |
|---|---|
| single climb (the ILS per-candidate cost) | n=11: 30–190 ms; n=39: 1.2 s; n=176: 19 s; n=353: 131 s |
| ILS to beat Wolfram on j021 (n=39), 12-core parallel | **73–197 s (median ~100 s)** single run; ~1.5–2 min ensemble |

Per-climb cost is **~O(n²)** (BucketAscent pair-scans dominate; CMA@50k under-budgets at large n).
Implications for longer routes:
- **~100 ticks:** feasible but slow — extrapolating between n=39 (1.2 s) and n=176 (19 s), a climb is
  ~5–8 s, so a full ILS run is roughly **~15–25 min** (offline only).
- **~180+ ticks** (j002/j003): climb ~20 s → ILS is ~hour-scale.
- **353 ticks** (j001): climb ~131 s → ILS is **impractical** (hours).

So whole-span ILS is the right tool for the **~10–40 tick multi-jump range**. Long routes should use the
existing segment decomposition (`FreeSpaceDecomposition` / `LongRunSolver`) and apply ILS per segment, not
monolithically. The per-climb O(n²) (chiefly BucketAscent) is the first thing to optimize for longer n
(restrict block-2 pairs to wall-relevant ticks).

## Productization recommendation

Wire ILS (parallel best-of-batch + small ensemble) as an **offline "Exhaustive" effort tier**, running
AFTER the existing solve in `AngleSolverEngine`'s multi-jump path (seeded from its feasible result). It is
firmly minutes-scale (never interactive); Fast/Balanced/Thorough keep the current solver. It never regresses
(accepts only improvements) and closes the multi-jump gap to the true global optimum, matching/beating what
an external global solver (Wolfram/Cbc) achieves — in bundleable pure Java.
