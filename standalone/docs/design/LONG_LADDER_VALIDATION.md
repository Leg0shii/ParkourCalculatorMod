# Longer climbing-chain validation

2026-10-29. Follow-up to the automatic mixed-contact work in [MIXED_MOVEMENT_CONTACTS.md](MIXED_MOVEMENT_CONTACTS.md). No new route pruning was added.

## Accepted results

| Case | Inputs | Discovery and acceptance |
| --- | --- | --- |
| Three climbing regions | 50 | Cold geometry-only route/schedule discovery, then IPOPT and full shared-scene acceptance |
| Five climbing regions | 99 | Supplied region order and six-input transfer policy; climb durations derived with the existing Y simulation, then IPOPT and full shared-scene acceptance |

Both cases have one launch, free facings, actual wall clips in every region and no intermediate ground contact. The fixtures start inside the first rectangular region, on a real support. The search setting `minClimbInputs: 2` deliberately exercises climbing in every selected region; its default remains zero. These are valid witnesses under the configured family, not minimum-time or objective certificates.

The three-region geometry-only fixture supplies collision boxes, climbable cells, initial state, start/goal supports and search limits. It supplies no entry times, climb windows, air times or facings. The discovered episodes enter/exit at `(0,3)`, `(9,29)`, `(34,47)`, then land at state 50. The measured run took 164.974 seconds and retained its incumbent at the context limit.

The five-region schedule uses `(0,3)`, `(9,29)`, `(35,51)`, `(57,74)`, `(80,96)`, then lands at state 99. Its region order and transfer delay are prescribed. Its valid continuous solve establishes that five climbing units, intervening normal air, carried velocity, wall collision and finite-scene constraints compose correctly. It does not establish cold five-region discovery.

## Seed correction

For an episode that starts at the original route start already inside a ladder, the seed previously aimed at the cell centre before moving toward the departure. With initial Z=.68, that could point the launch backward toward Z=.5. The resulting ladder-cap regime was incompatible with an early forward departure; numerical attempts returned unknown even for the valid three-region structure.

The seed now starts its tangential reference at the actual initial position in this case. Future facings remain unconstrained and the complete route is reoptimized. There is no new movement equation, state reset, frozen endpoint or facing constraint.

Ground friction on the initial in-ladder launch remains significant: the launch uses the grounded drag before the next input. An initially constructed five-input transfer was interval-infeasible after that drag. The accepted prescribed schedule uses six-input transfers; the cold three-region search independently chooses its own final transfer timing.

`test_long_ladder_chains.py` reconstructs fresh numerical problems from both episode contracts without stored facing seeds. It verifies one launch, actual climbing in all regions, no interior ground, valid scalar states and shared full-scene acceptance. Both tests passed in 2.491 seconds. They fail to establish cold route discovery on their own; that evidence is retained separately in the three-region snapshot and fixture.

## Bounded five-region discovery

A cold run on the five-region geometry returned `unknown`, with `context_limit`, after 131.180 seconds under a 180-second outer budget and 120-second route budget. The 99-input prescribed structure is feasible, so this outcome must not be described as geometric infeasibility. The bounded unpruned planner has many early-entry and route-history alternatives. Their existence is not an acceptance bug; only complete independently validated numerical candidates can become an incumbent.

That run expanded 20 scalar prefixes, left 381 queued, and generated 12,800 local schedules before the generation deadline. It reached no complete target proposal and performed zero full numerical attempts. This is a search-coverage/work-allocation limit, distinct from an IPOPT rejection of the accepted five-region structure.

[five-climbing-search-limit.json](experiments/contact-combinations/five-climbing-search-limit.json) retains the exact fixture hash, search settings, timing-window/attempt counts and work-limit outcome. [The accepted evidence folder](experiments/contact-combinations/README.md) holds both numerical witnesses. Keep cold five-region discovery as an open search-coverage item. Per the user's instruction, improve movement-type coverage before introducing route-specific pruning. Stepping/water, same-region re-entry and changing climb-wall orientation remain separately tracked follow-ups.

Reproduce the cold three-region search from the worktree root:

```sh
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m standalone.autonomy.solve_blocks \
  --input standalone/autonomy/fixtures/three-climbing-ladders.json \
  --output /tmp/three-climbing-search.json \
  --seconds 240 --route-seconds 180 --candidate-seconds 2 --retries 0
```

Final regression gate: all 228 orchestration tests passed in 525.911 seconds, followed by the two new long-chain regression tests passing in 2.491 seconds. This work changes orchestration seeds and tests only; it does not change the numerical worker or shared movement arithmetic.
