# Strat families: where `strats/schedules.txt` comes from

`core/src/main/resources/strats/schedules.txt` is the library behind the stratfinder's family stage (#671, `StratFamilies`). This folder holds the tool that built it and the record of what was measured.

## The library file

One key schedule per line:

```
<weight>;<jump numbers the row belongs to>;<jump ticks>;<keys>
```

- `weight`: how many worded Strats translate into this schedule (mirror pairs counted together), 1 for a recorded human Strat.
- `jump numbers`: the hpk onejump numbers whose own Strat this row is (matched by checkpoint position, or the recording's own jump). `StratFamilies.shippedWithout(number)` leaves those rows out, which is how a cold test never sees its own jump. Empty for most rows.
- `jump ticks`: the ticks of the schedule on which the player jumps.
- `keys`: the key combination per tick, run-length coded like `NoTurnKeys.describe` (`-x10 Sx3 W WAx12 W WAx10`).

1,381 rows come from worded Strats, 59 from the recorded human Strats under `core/src/test/resources/captures/hpk`. The file holds no Strat text.

## The translator

`translate_strats.py <corpus folder>` turns worded Strats into those rows. It expects `<corpus folder>/_export/all_strats.tsv` (tab separated: source, x, y, z, text; one line per checkpoint Strat) and `<corpus folder>/hpk/onejump/<number>.yml` (for the positions of the recorded jumps), and writes `rendered.tsv` and `render_fails.tsv` next to the export. The corpus itself (4,913 hpk checkpoint texts and 1,745 Cosmic Network texts) is not in this repository.

What a text means, as the translator reads it (validated against the recordings: 20 of the 59 recordings are reproduced tick for tick from their own texts, side insensitive):

- air time is 12 ticks from the jump tick to the landing tick;
- "Nt pessi X": jump with no key, X pressed N ticks after the jump; "max pessi X": X one tick after the jump;
- "a7 X": jump with no key, X one tick before the landing;
- "HH X": X for one tick, then the jump; "jam X": jump with X held;
- "Nt mark W+X": jump with X only, W added N ticks later;
- "max c4.5 X", "fmm", "burst", "rex": jump with X;
- "walk / run / wait Nt [X]": ground ticks from the landing tick;
- "Nt later" counts from the last event, "Nt before landing" from the landing;
- "wobble W": W released one tick after the jump;
- "WAD", "45", "strafe": final jump with W, W plus side one tick later; "WDWA": the side flips on the jump tick; "noturn": final jump with the keys held;
- with no ground ticks named, the next jump is one tick after the landing.

4,391 of 6,658 texts translate. The largest groups it does not read yet: texts that name no jump, "3bc bwmm", "hold", bare key lists, tick ranges ("run 4-5t"), "1t strafe", "rex bwmm", "max pfmm" and flicks.

## What was measured (2026-11-11)

Segment families (see `docs/noturn-global-solver-design.md`, section "The family stage"): 103 run-up, 498 middle and 55 final families.

Leave one out over the 59 recordings (the jump's own rows removed):

- 55 human Strats are a product of known families. Not covered: j347 (two middle segments), j155, j129, j135 (run-ups).
- Schedules that come before the human Strat in each order, exact counts:

| reach within | change count (the levels) | segment families |
| --- | --- | --- |
| 1,000 schedules | | 22 of 59 |
| 10,000 | | 37 |
| 100,000 | 21 | 43 |
| 1 million | 25 | 48 |
| 10 million | 38 | 53 |
| 100 million | 40 | 54 |

- The jumps the levels miss: j716 970 thousand (levels: 3,200 billion), j424 9.8 million (22,000 billion), j154 38 million (2,300 billion), j1099 100 million (1e15).
- A per tick model (chance of a key change by distance to the nearest jump) trained on the same data ranks worse by a factor of 100 to 1000 on those jumps, and shifting one event of a family by a tick covers none of the four uncovered recordings. The families are exact patterns; a missing one is missing, not mistimed.
