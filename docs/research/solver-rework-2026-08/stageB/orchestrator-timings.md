# Orchestrator authoritative timings (clean serial runs; Stage B reference)

Produced by the orchestrator via `EngineFileScreen` direct `java -cp` (JDK 25), one clean serial run
per capture (no concurrent load). Stage B agents: use these as the authoritative wall-clock; add your
own DETERMINISTIC counts (iterations, LP calls, forward evals, cache hits) which are contention-free.
Do NOT run 10-way concurrent timing benchmarks (they pollute each other); if you need a fresh wall-clock
number run it isolated and say so.

## FAST effort (first-feasible), single clean run

| capture | n / jumps | ms | met | FAST obj | COPT true | FAST gap | solver chain |
| --- | --- | --- | --- | --- | --- | --- | --- |
| j005 | 9/1 | 115 | 10/10 | -41.298292 | -41.294900 | first-feasible | closed form (first feasible) |
| j016-X2jmmp2p | 11/2 | 110 | 4/4 | -4.857992 | -4.857772 | ~2e-4 | closed form (first feasible) |
| j019-3jmmtruenix | 11/3 | 117 | 4/4 | -13.303542 | -13.302783 | ~8e-4 | closed form (first feasible) |
| j022-1bmhbfly | 11/1 | 137 | 5/5 | -531.700124 | -531.700200 | ~8e-5 | closed form -> SLP -> level set |
| j008b-2jump | 25/2 | 202 | 10/10 | -0.215326 | -0.196938 | 1.8e-2 SHORT | receding horizon (first feasible) |
| j021-rinav1-01 | 39/4 | 268 | 13/13 | 1067.684771 | 1067.863880 | 0.179 SHORT | receding horizon (first feasible) |
| loopmm-3jump-lands | 33/3 | 308 | 5/5 | -279.354398 | (gate) | (gate) | receding horizon (first feasible) |
| taser-80t | 80 | 260 | 16/16 | 0.141384 | - | - | receding horizon (first feasible) |
| nix-full-t1 | -/- | 39975 | 8/15 FAIL | 8.699440 | - | ACCEPTED-FAIL, blows envelope | receding horizon (no feasible) |

## THOROUGH effort (12 s budget)

| capture | ms | THOROUGH obj | COPT true | gap | solver chain |
| --- | --- | --- | --- | --- | --- |
| j005 | 9087 | -41.291516 | -41.294900 | prod over-reaches +3.4e-3 (half-angle) | closed form -> seam sweep -> B&B -> ILS |
| j016 | 9118 | -4.855680 | -4.857772 | prod over +2.1e-3 | closed form -> seam sweep -> B&B -> ILS |
| j019 | 9108 | -13.292335 | -13.302783 | prod over +1.0e-2 | closed form -> seam sweep -> B&B -> ILS |
| j022 | 9120 | -531.700150 | -531.700200 | prod short 5e-5 | closed form -> SLP -> level set -> seam sweep -> ILS |
| j008b | 9102 | -0.215314 | -0.196938 | prod SHORT 1.8e-2 | receding horizon -> B&B -> ILS |
| j021 | 9126 | 1067.862397 | 1067.863880 | prod SHORT 1.5e-3 | receding horizon -> closed form -> SLP -> level set -> seam sweep -> B&B -> ILS |

## Key reads for Stage B

- FAST envelope: 110-310 ms for normal captures; single jumps ~110-140 ms, multi-jump ~200-310 ms.
  This confirms the "0.1 ms to 800 ms" envelope for typical captures (the 0.1 ms figure is the internal
  closed-form fast path timed in a tight loop, not the engine wall-clock which includes worker spawn).
- FAST leaves LARGE objective gaps on coupled multi-jump (j021 FAST 0.179 b short, j008b 1.8e-2 short);
  FAST is first-feasible by definition. THOROUGH closes most of j021 (down to 1.5e-3 short) but takes
  12 s and still misses the COPT optimum by 1.5e-3.
- nix-full-t1 blows the envelope (40 s at FAST, still fails 8/15). This is an accepted-fail pin; it is
  the tail of the distribution and a target for the "hard multi-jump" question.
- Byte-exact production over-reaches the continuous COPT optimum on single/easy jumps by up to 1.0e-2 b
  (half-angle norm>1); real headroom exists only on the coupled cases (j008b, j021).
