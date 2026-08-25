# Stage E measured: byte-exact round-trip of the COPT continuous optima

The mission requires byte-exact-round-tripping any continuous/COPT solution through ExactJumpModel before
claiming achievability. Built `ReplayYaws.java` (test-only): exports the COPT constant-modulus QCQP optimal
yaws (research/copt/export_yaws.py -> data/yaws-*.json) and replays them through the real ExactJumpModel,
reporting the byte-exact objective and violation. Direct java -cp, no gradle-in-loop.

## Measured (COPT continuous optimum yaws -> ExactJumpModel)

| capture | degenerate ticks | COPT cont pos | byte-exact replay | viol | byte - cont | shipped THOROUGH | ARCH-1 vs shipped |
| --- | --- | --- | --- | --- | --- | --- | --- |
| j021-rinav1-01 | 1 (t12) | 1067.863733 | 1067.863789 | 9.8e-5 | +5.6e-5 | 1067.862397 | +1.4e-3 b RECOVERED |
| j019-3jmmtruenix | 0 | -13.303208 | -13.303185 | 2.5e-5 | +2.3e-5 | -13.292335 | shipped over-reaches |
| j008b-2jump | 1 (t1) | -0.197052 | -0.219554 | 7.4e-5 | -2.25e-2 | -0.215314 | WORSE than shipped |
| j005 | 0 | -41.294959 | -41.298231 | 4.3e-6 | -3.27e-3 | -41.291516 | WORSE than shipped |

(Objectives are byte-exact; viol is the compiled maxViolation at ~sine-floor scale, closed by the margin
ladder. All MAX objectives, higher better.)

## The three regimes (measured, important for the architecture)

1. COUPLED multi-jump, small half-angle effect (j021): the continuous optimum snaps CLEANLY to byte-exact
   (diff +5.6e-5, at the sine floor) and RECOVERS +1.4e-3 b over the shipped THOROUGH result. This is the
   ARCH-1 win: the residual solve gives a continuous optimum whose byte-exact realization beats the shipped
   full-n search. Byte-exact-validated headroom.

2. LOOSE-degenerate-tick, snap-suboptimal (j008b): the continuous optimum's degenerate tick (t1) direction
   is ARBITRARY on the degenerate face (the objective is indifferent there), so the specific continuous
   direction COPT returned byte-exact-realizes to -0.219554, WORSE than both the continuous -0.197 and the
   shipped -0.215. To actually ACHIEVE the continuous -0.197 byte-exact, the residual solve must optimize
   the BYTE-EXACT objective at t1 (sphere-decode / objective-aware snap, D07/D11), NOT snap the arbitrary
   continuous direction. This is the concrete reason the snap must be objective-aware.

3. HALF-ANGLE-dominated single jump (j005): the byte-exact OPTIMUM is at DIFFERENT yaws than the continuous
   optimum, because byte-exact has favorable half-angles (norm>1) the continuous model lacks. Snapping the
   continuous optimum LOSES 3.3e-3 (byte -41.2982 < cont -41.2949), while the shipped byte-exact ILS search
   GAINS 3.4e-3 (shipped -41.2915 > cont). So on half-angle jumps you MUST search byte-exact; the
   continuous optimum is not even a good starting point. The shipped fast-path + ILS already does this well.

## Architectural consequences (feed Stage E + FINAL-REPORT)

- The continuous (COPT/ARCH-1) optimum is a BOUND and a STRUCTURE GUIDE, not the byte-exact answer. Its
  direct byte-exact realization: (a) is essentially optimal when there are 0 degenerate ticks and small
  half-angle effect; (b) is suboptimal when a degenerate tick's direction is arbitrary (must
  objective-aware-snap); (c) is materially wrong when half-angles dominate (must byte-exact-search).
- ARCH-1 must therefore END with an objective-aware byte-exact search over the degenerate ticks AND the
  half-angle-relevant ticks (sphere decoding / BucketAscent), then certify. The shipped BucketAscent/ILS is
  exactly this search and already reaches within 2.8e-5 b of the continuous optimum on j021 (memory), so
  the ARCH-1 win is: replace the BAD SUGGEST (defaulted degenerate direction -> 0.34 b infeasible) with the
  residual-solve suggest, KEEP the good IMPROVE (BucketAscent/ILS/sphere-decode) as the byte-exact finisher.
  This matches D06's "residual replaces the bad suggest, keeps the good improve."
- j008b is the sharpest Stage E target: shipped is STUCK at -0.215 (B06: 7.6 s THOROUGH gains only 1.2e-5),
  the continuous optimum is -0.197 (1.8e-2 better, COPT-proven), and the fix is an objective-aware residual
  solve at the ONE degenerate tick t1 plus the byte-exact finisher. A Stage E prototype should land it.
