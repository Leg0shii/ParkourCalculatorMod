# Step 6 finale: ALM tuning, 80/100t rungs, and the router-cap probes (2026-07-23)

Single consolidated record of the six matrix legs that closed checklist step 6 (grill brief
2026-07-19 section 10) and retired the 64-tick router cap. Raw runs.jsonl files were
intentionally not kept; every number cited below is the record. Companion in-tree archives:
matrix-bakeoff60-1 (the 60t shakedown this builds on) and matrix-taser-pin1 (band + lambda pin).
All legs: build bcad1f6 plus (final two legs) the shipped cap change, cold starts, TASer band
problems, lambda 1e-3 unless noted.

## Leg 1: ALM tuning sweep (tag almtune1)

A18 grid at alm60 l=1e-3: seeds={4,8,16}, gate={1.5,2.0}, topk=64, cooking=0, one axis at a
time, 7 configs x 6 band rungs. Verdict: NO exposed knob flips any bakeoff60-1 failure; all
three are structural.

| config | feas | j001 353t | j002 189t | j003 176t | j155 66t | nix-full-t1 | trp |
| --- | --- | --- | --- | --- | --- | --- | --- |
| seeds16 (anchor) | 3/6 | no snap | yes -33.589203 | yes -31.299772 | yes | 6.34e-4 | 1.71e-2 |
| seeds4 | 2/6 | viol 46 | 4.65e-7 | yes | yes | 5.62e-4 | 4.68e-2 |
| seeds8 | 2/6 | viol 46 | 1.48e-6 | yes | yes | 6.58e-4 | 1.71e-2 |
| gate1.5 | 2/6 | no snap | 1.71e-6 | yes | yes | 6.89e-4 | 1.71e-2 |
| gate2.0 | 1/6 | no snap | 1.57e-6 | 9.03e-7 | yes | 6.47e-4 | 1.71e-2 |
| topk64 | 3/6 | no snap | yes -33.589203 | yes | yes | 5.91e-4 | 1.71e-2 |
| cooking0 | 2/6 | no snap | yes -33.589307 | 1.77e-7 | yes 10s rev9 | 6.08e-4 | 1.71e-2 |

- j001: per-seed AlmBfgsCore cost at 353t consumes the whole 60 s before the snap phase; fewer
  seeds reach snapping but the candidates are hopeless (viol 46, travel 28366 deg). Fix needs
  per-seed time slices, exposed maxOuter/maxInner, or warm seeds, not this grid.
- nix-full-t1 pinned at 5.6e-4..6.9e-4 and trp at 1.71e-2 across every config: capability
  misses (the incumbent's lambda-free feasibility machinery class).
- Gate widening is harmful (flips j002/j003 feasible into 1e-6..1e-7 borderline misses);
  topk64 is a no-op; cooking0 is a speed-for-jitter trade (4x faster j155 at 9 reversals).
- The feasible/infeasible line on 176-189t rows sits at viol ~1e-6 and is razor-thin against
  seed-schedule changes. Anchor replication vs bakeoff60-1 is clean to 1e-6.

## Leg 2: 80/100t rungs bake-off (tag bakeoff80100-1)

New user-recorded rungs taser-80t and taser-100t (1.8.9, X/MAX, in captures/ + problems/solve/
sidecars + band.txt), closing the 66-176t corpus gap. Incumbent taser60-l1e3 vs ALM (defaults
and cooking0), pre-cap-ship.

| problem | incumbent (pre-ship) | alm60-l1e-3 | alm60 cooking0 |
| --- | --- | --- | --- |
| taser-80t | 0.141384, 270 deg, rev 6, 0.4 s horizon-only | 0.142181, 212 deg, rev 4, 41 s | 0.142181, rev 8, 10 s |
| taser-100t | -21.262273, 1043 deg, rev 15, 2 s horizon-only | -20.020222, 833 deg, rev 13, 60 s | -20.020188, rev 21, 60 s |

Pre-ship the incumbent quick-exited above the 64t router cap leaving ~58 s unspent; ALM won
both rungs (+7.97e-4, +1.242) with lower travel and fewer reversals.

## Legs 3+4: the 64t cap probes on the Optimize shape (tags capprobe1, capprobe2)

The user challenged the cap as arbitrary. History agrees: MULTI_JUMP_RACE_MAX_TICKS=64 was born
in be18592 with no comment, benchmark, or commit body, sized to a corpus that topped out at
54-62t, and survived M1 as the TICKS_LE_CAP router default gating the whole post-horizon
improvement stack plus all lambda scoring. Probe: optimize(60) graph with all caps raised
(captaser sweep base), CUSTOM harness.

| problem | horizon-only | raced ~76 s (capprobe1) | raced 60 s hard (capprobe2) | ALM 60 s |
| --- | --- | --- | --- | --- |
| taser-80t | 0.141384 | 0.151329 | 0.141438 | 0.142181 |
| taser-100t | -21.262273 | -20.023950 | -20.247508 | -20.020222 |
| j002 189t | -33.672892 raw | -33.669504 raw, travel -15 deg | -33.669480 raw | (worse raw) |
| j003 176t | -31.299998 | -31.300000 CERTIFIED | -31.300000 CERTIFIED | -31.299772 |

- Raising the cap improves or ties the incumbent at every measured length; j003 comes back
  certified optimal ("optimal at constraint cap") within 60 s; j002 trades 3.4 milliblocks raw
  for 15 deg travel, the lambda objective working as designed (at lambda=0 the trade would not
  fire). The step-5 "lambda inert above 64t" limit was the cap default, not capability.
- FAIRNESS TRAP: capprobe1 rows ran ~76 s (CUSTOM has no overall deadline) and beat ALM at 80t;
  at a hard 60 s the raced incumbent reaches only 0.141438 and ALM keeps both rungs. Always
  equalize wall budget before comparing contenders; cap128 on j002/j003 is the routing control
  (128 < 176) and reproduces horizon-only exactly.

## Leg 5: the Fast shape under cap 256 (tag fastprobe1)

custom-fastgraph (cap 64 control) vs fastcap-cap256 on the six >64t problems, lambda 0. All 12
rows feasible via horizon. Objectives byte-identical (j003 1e-6 nudge); wall cost of the raise
is 0.1-1.3 s (seedMulti + smoothWarm before the first-feasible exit). stopOnFeasible exits
before the expensive stages, so the earlier latency fear was wrong. Value on >64t problems
where horizon MISSES (rescue B&B, warm race) remains unmeasured; no such capture exists yet.

## Leg 6: the 353t bound (tag capj001)

cap=512 puts j001 (353t) through the warm path for the first time. Both shapes fail:

- fastcap-cap512: CANCELLED at 120 s, INFEASIBLE, no objective. Control solves feasible in
  0.3 s. seedMulti at 353t never finishes and no report node has run, so cancellation loses the
  feasible horizon answer.
- captaser60-cap512: 120 s to land 12.225675 / travel 3460, worse than the 0.5 s horizon-only
  row (12.225830 / 3441) on both axes; the race ran and lost to horizon.

WIRING TRAP: in BuiltinGraphs.build() the horizon candidate reaches a report node only after
seedMulti completes (horizon FOUND -> rChainTicks TRUE -> seedMulti, no report between); a slow
seedMulti holds the incumbent hostage. Add a report node there before any future cap raise or
>256t corpus growth.

## Ship + verify (tag capship1)

SHIPPED: cap 64 -> 256 uniformly, no tier split. BuiltinGraphs.IMPROVE_TICK_CAP=256, set by the
router() helper on every TICKS_LE_CAP router; NodeCatalog ParamSpec default references the same
constant (retro-applies to saved presets that never stored the param, intended). 256 is the
measured envelope: 66-189t improves or ties under it, 353t stays protected, 512+ is measured
harmful. Full :core:test green post-ship.

Real-THOROUGH-path verify (taser60-l1e3, post-ship): taser-80t 0.149831 in 74.7 s (chain
horizon -> CMA -> seam sweep -> B&B -> ILS, was 0.141384 in 0.4 s), taser-100t -20.023950 in
76.8 s (was -21.262273 in 2 s). OBSERVED WRINKLE: THOROUGH now overruns optimizeSeconds=60 by
~25% on >64t problems (node budgets sum past the overall figure once the stack actually runs);
belongs to the open A22 budget-semantics decision. Note the fairness nuance for the A6 arm
range: at real-path ~75 s the incumbent beats ALM's 60 s result at 80t (0.149831 vs 0.142181)
while ALM keeps 100t (-20.020222 vs -20.023950).

## Standing verdicts and open items

- A6 DECLARED (user): split verdict. Incumbent stays TASer core; ALM is the mid-length arm,
  advertised ~65-120t provisionally. Warm-seed ruling: persona-only (races may warm ALM from
  incumbents; matrix legs stay cold).
- Open: ALM-arm engine wiring; 100-176t crossover rungs; report-node hardening between horizon
  and seedMulti; THOROUGH budget overrun above 64t (A22); ALM per-seed budget slicing for the
  353t class; a >64t capture where horizon misses, to measure Fast's rescue value.
