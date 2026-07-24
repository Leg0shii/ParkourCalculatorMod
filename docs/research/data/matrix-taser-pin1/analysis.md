# TASer band + lambda pin (2026-07-20)

Step 5 of the split-solver sequencing (grill brief section 10). The scoring config is
{base objective + lambda * yawTravelDeg}, wrap-normalized deltas, applied in inner-loop search
scoring (CMA fitness, compass polish, bucket ascent, ILS score) and in every acceptance
comparison (SolveCore selection, node acceptance, SolveProgress incumbent, race winner).
Feasibility machinery runs lambda-free by construction: momentum assembly, homotopy closer,
receding-horizon window and setup-peel suffix specs, and the feasibility-only CMA rescue.
Certificates (capCertify, dual bounds, B&B bounds) and the legal-mode wrap searcher stay on the
raw objective. `SolveRunRecord.config.metric.smoothLambda` records the lambda of every run.

## The band

`band.txt` here is the standing TASer band: every corpus problem at 50+ ticks.

| problem | ticks | jumps |
| --- | --- | --- |
| solve/nix-full-t1 | 54 | 4 |
| solve/trp-optimize-feasible-swap | 62 | 1 |
| frontier/j155-4jmm_3bcmm_4.9375b | 66 | 5 |
| solve/j003 | 176 | 15 |
| solve/j002 | 189 | 16 |
| solve/j001 | 353 | 30 |

There are NO corpus problems between 66 and 176 ticks; the bake-off spec's 80/100-tick rungs do
not exist yet and need to be produced for step 6.

## Runs

runs.jsonl: band x {optimize60, taser60-l1e5, taser60-l1e4, taser60-l1e3} (24 records) plus a
pin-evidence supplement of the three sub-64-tick wiggly problems from the a8smooth1 band
(solve/j007, solve/nix-t25-setup-tick, frontier/j335) x {optimize60, l1e4, l1e3} (9 records).
taser60-l* = optimize60 shape (THOROUGH, 60 s) with the given smoothLambda. 120 s external cap.

## Findings

1. Lambda smooths exactly where the lambda-aware chain runs, and trades only value margin.
   On the four problems whose chains pass through CMA/ILS/bucket stages:

   | problem | lambda=0 travel/rev | l1e-4 | l1e-3 | objective cost at l1e-3 |
   | --- | --- | --- | --- | --- |
   | j007 (28t) | 1404 / 18 | 192 / 4 | 186 / 5 | 3e-7 (cap-bound: smoothing free) |
   | j335 (21t) | 299 / 10 | 220 / 5 | 207 / 2 | ~4.3e-3 |
   | nix-t25 (37t) | 431 / 9 | 426 / 9 | 364 / 5 | 2.3e-3 |
   | trp (62t) | 629 / 5 | 584 / 5 | 556 / 4 | 6.5e-3 |

   Feasibility 15/15 across all lambda runs; every objective cost is at the milliblock scale.

2. l1e-5 is inert (bucket-scale objective jitter is 1e-5..1e-4 per accepted move, so a
   1e-5-per-degree penalty never flips an acceptance). l1e-4 captures the free wins (j007's
   wiggle was pure jitter around a cap-bound solution). l1e-3 buys strictly more on three of
   the four problems (j335 down to 2 reversals) and never loses feasibility.

3. PIN: lambda = 1e-3. Persona ruling: the TASer smooths as much as possible within a fixed T,
   trading positional value margin only; milliblocks are inside that margin. The verifier
   persona runs lambda=0, so reach-margin work is unaffected. Standing preset: taser60-l1e3.

4. nix-full-t1 is identical at every lambda (obj 8.700000055416115, travel 403): momentum
   assembly wins it and feasibility machinery is lambda-free. This is the no-regression proof
   for the redirect/momentum class. First plumbing attempt propagated lambda into the assembly's
   window specs and BROKE nix feasibility (momentum NONE after its full budget); that trap is
   fixed and recorded.

5. The TAS-length trio (j001/j002/j003) and j155 are lambda-INERT: over the 64-tick router cap
   the chain is receding horizon -> closed form / SLP windows, which never consults lambda
   (windows are feasibility surrogates; SLP has no lambda by design), and their final smoothing
   is the post-hoc pass the A8 study already proved a dead end. Their travel is byte-identical
   between fast, optimize60, and every lambda (j001: 3441 deg, 59 reversals, in a8base1 and
   here). CONSEQUENCE for step 6: the incumbent-with-lambda contender is only live at <=64
   ticks; at 80/100 ticks it is inert as currently plumbed, so the bake-off either needs the
   ALM contender to win there, an SLP-with-lambda extension, or a router-cap rethink. This is
   the measured shape of the "smooth-by-construction core may be required at length" hypothesis.
