# Modular per-cycle brute-forcer (product direction, user-authored 2026-08-10)

Captured from the user during the j154 cold session. This is the intended shape of the solver, not just a j154 hack. Document-for-later; supersedes "one auto-magic cold solve" as the north star for exotic-tech jumps.

## The idea

A jump's momentum decomposes into **press-cycles** (the ground→air hops; j154 has 3: ticks [0-2] [3-15] [16-28]). Instead of one monolithic search, expose **each cycle as an independently configurable search unit**. The user, who usually knows the rough strat, tells the solver how to spend effort per cycle; the solver's job is to search each cycle's declared space as efficiently as possible.

Per-cycle config knobs:
- **Fixed vs brute-forced.** e.g. "cycle 1 (the momentum-build) is fixed, only cycle 0 and cycle 2 are searched." The user's example: `j2 fixed, j1 + j3 brute-forced`.
- **Alphabet** the cycle may draw from (coasts / glides / presses), defaulting to the general butterfly set (diagonals for glides, forward for the launch press).
- **Value ranges** — e.g. glide length band, which strat families (pessi / fmm / mark / …), press-combo set.
- **Ordering / strength** — search families in order of "strength" so the likely one comes first (see levers).

Rationale from the user: "You usually don't want to slow down in the long glide, so the middle part can be fixed." And: "maybe we just have to offer THAT as the solver — a very modular brute-forcer where you can configure it as you wish, so that if someone is in the right ballpark of a strat, it can find it."

## UI vision

A **3D view of the jump with each press-cycle highlighted as a selectable region**. Click a cycle → choose how the brute-forcer treats it (fixed / swept, alphabet, ranges). Run. This turns the solver from a black box into a **tunable instrument** the player drives with their strat knowledge.

## Efficiency levers (the solver's half of the bargain)

The solver must make each declared space cheap to sweep. Concretely, from this session:

1. **Template dedup.** In the `coast → glide×j → press` grammar: when `coast==glide` the j-sweep collapses to one pattern; when `j==L-1` the coast vanishes so coasts collapse. ~15-20% per cycle, free, no coverage loss.
2. **Fix the momentum-build cycle.** Only one cycle carries the long glide (j large); the others are j=1. Sweeping j everywhere is the ~10× bloat. Letting the user pin the build cycle (or auto-detecting it as "the longest glide") removes it.
3. **Two-sided tail reachability (replaces the crude Filter 2).** From the tick pinned against the binding constraint (e.g. X@34), compute the **minimum velocity required to still reach the landing box**, and forward from the momentum exit the **max achievable velocity/position**; reject any candidate that can't hit BOTH the pinned constraint and the landing. Much tighter than the current omnidirectional max-distance test (which passes ~1.0M of j154's candidates because "can reach" ≠ "can thread all").
4. **Binary-search glide length by strength.** Feasibility vs glide length is monotone for a min-constraint (more glide = more momentum = reaches further), so the feasible band is found in ~log(range) probes, not linear. Rank strat families by strength (rough ordering: `fmm > pessi`, longer > shorter; `fmm@1t` vs `pessi@max` etc.) to set the search order. User flagged this may not prune alone but sets a good order.
5. **Probe gate at the certify level.** Real certifies sit at probe ~1e-4; screen/gate around 1e-3 (not 1e-2) so the expensive byte-exact certify only fires on genuine basins.
6. **Symmetry.** Butterfly solves come in mirror pairs (`{SA,WD}` ↔ `{SD,WA}`); search one handedness, mirror the other for free.

## Status / relationship to current work

The running j154 experiment (`ColdCycleBeamScreen`, env-configurable alphabets `PKC_COLD_BEAM_COASTS/GLIDES/PRESSES/ENGAGES`, tail-reachability filter, streaming probe→certify) is the **first, CLI-flavored instance** of this modular brute-forcer: alphabets are the per-cycle config, glideMax is the length range. The next steps toward the vision: per-cycle (not global) alphabet + length + fixed-flag, the two-sided tail reachability, the strength ordering, and eventually the 3D per-cycle UI.

The key mindset shift the user endorsed: the cold solver for exotic-tech jumps is **not** a fully-autonomous oracle; it's a **player-tunable brute-forcer** that becomes tractable once the human puts it in the right ballpark. Autonomy (auto-detecting the alphabet/families) is a later refinement on top.
