# COPT research-oracle harness (angle-solver re-founding, 2026-08)

RESEARCH ORACLE / BENCHMARK GROUND TRUTH ONLY. This directory is OUTSIDE every Gradle module and is
NEVER on any shipped classpath or in any jar. COPT (Cardinal Optimizer) is a commercial trial and is
NOT redistributable; it is used here only to answer research questions and to produce per-capture
ground-truth optima for benchmarking pure-Java prototypes. Nothing here ships.

## Prerequisites

- Python 3.12, `pip install coptpy numpy` (coptpy 8.x; a valid COPT license).
- License files live at `C:\Users\benja\Desktop\Coding\98 Anderes\copt` (license.dat, license.key).
  Do NOT copy, move, print, or commit them. Set the env var before any run:
  - Bash: `export COPT_LICENSE_DIR='/c/Users/benja/Desktop/Coding/98 Anderes/copt'`
  - PowerShell: `$env:COPT_LICENSE_DIR = 'C:\Users\benja\Desktop\Coding\98 Anderes\copt'`

## Step 1: export the compiled program from the shipped model (Java, test-only)

`core/src/test/java/de/legoshi/parkourcalc/anglesolver/StructureDump.java` dumps any capture's fully
compiled continuous program (per-tick constant moduli, phases, frictions; objective vectors; every
position wall as `sum coef*(a.u) <= bPrime`; free-start box; recorded warm path). It reads
`JumpLinearModel` directly, so no physics is reimplemented in Python.

Compile once, then run headless via direct `java -cp` (Gradle swallows env into the test JVM):

```bash
cd <repo-root>
./gradlew :core:testClasses
# classpath was written to core/build/test-classpath.txt by the printTestCp init task
CP="$(cat core/build/test-classpath.txt)"
PKC_STRUCT_FILE="core/src/test/resources/captures/j021-rinav1-01.json" \
  PKC_STRUCT_OUT="research/copt/data/struct-j021-rinav1-01.json" \
  java -cp "$CP" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.StructureDump
```

`PKC_STRUCT_FILE` accepts a direct file path or a capture pool name. Output JSON lands in
`research/copt/data/`.

## Step 2: solve the relaxations / global QCQP in COPT (Python)

- `coptlib.py`: the model builder and three solvers:
  - `solve_socp_disk(d)`: SOCP disk relaxation `|u_t| <= m_t` (convex upper bound; reports per-tick
    modulus slack -> H1 signal). Also auto-adds free-start variables from `p0coef` when the box is free.
  - `solve_qcqp_sphere(d)`: nonconvex constant-modulus QCQP `|u_t| == m_t` (COPT spatial B&B, NonConvex
    =2; the TRUE global optimum and its gap).
  - `solve_shor_sdp(d)`: Shor/SDP lifting (dim `2n+1`); reports the SDP bound and the eigen-spectrum /
    rank of the optimal moment matrix (rank-1 => tight).
  - `reconstruct_from_warm(d)`: the FAITHFULNESS GATE; rebuilds the recorded path from the dump and
    confirms it reproduces the recorded objective before any COPT result is trusted.
- `run_h1h2.py`: runs all three on a list of captures and writes `data/h1h2-<capture>.json`.

```bash
cd research/copt
export COPT_LICENSE_DIR='/c/Users/benja/Desktop/Coding/98 Anderes/copt'
python run_h1h2.py j021-rinav1-01 j008b-2jump loopmm-3jump-lands
```

## Caveats (see stage0-copt/FINDINGS.md for the full measured record)

- The exported model is CLAMP-FREE (no inertia gate). For gate-dependent captures (loopmm) the result
  is not the true feasible basin; the gate must be added as big-M indicators (Stage E).
- The model DROPS dF (facing) constraints (position walls only), so on dF-chain captures it is a
  relaxation / loose upper bound. dF is a per-tick phase constraint (Stage D/E).
- COPT's continuous optimum is a near-exact reference (within a few e-3 b) but NOT a strict byte-exact
  upper bound: byte-exact can out-reach it via half-angle norm>1. Always byte-exact-round-trip a COPT
  solution through `ExactJumpModel` before claiming achievability.
