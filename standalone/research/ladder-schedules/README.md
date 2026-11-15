# Ladder schedule height checks

This is generator/Y-model evidence, not a horizontal climbing solve. [The request](../../fixtures/ladder-schedule-generator.json) supplies a validated front-entry window at state 1, a contiguous vertical region, side exit at state 7, initial Y=2 and VY equal to the legacy rest carry, and target top Y=0. `height-evidence.json` retains selected schedules, full traces, fall/Sneak comparisons and the bounded pool counters.

States are pre-input. The selected climb window is [0,6): hit inputs 0 through 5. Input 0 enters the region and can establish carry because state 1 is inside. State 6 is the last inside state. Input 5 is the final climb-refresh-eligible hit; input 6 moves to outside state 7 and is no-hit. The end stop omits input 5, retaining input 4. It never substitutes an omission of the penultimate planned hit.

| Stop choice | Last inside Y, state 6 | Exit Y, state 7 | Landing state |
| --- | --- | --- | --- |
| Neither | 2.5096000099182127 | 2.627200012207031 | 17 |
| Beginning | 2.2420000076293944 | 2.359600009918213 | 16 |
| End | 2.5096000099182127 | 2.5464480128784177 | 16 |
| Both | 2.2420000076293944 | 2.2788480105895994 | 15 |

Without the end stop, the final inside stored VY is .11760000228881837. Omitting the final hit lets that carry decay to .03684800296020513. Its effect occurs in the following exit movement: exit height drops by .08075199932861324, even though the last inside Y is unchanged. The generated descending target contact then occurs one input earlier. These numbers are simulated consequences, not fields edited to manufacture a difference.

Beginning-stop and fall tests separately verify capped descent, Sneak arrest, continued climbing while Sneak is held, and residual positive velocity after wall hits cease. Four interspersed Sneak inputs and four ordinary inputs produce a different height from four contiguous Sneak inputs; count alone is insufficient.

The unit suite also checks exceptions. A hit ending outside does not refresh climb carry, so removing it alone cannot affect Y. With incoming upward movement greater than .2, removing a first hit can preserve more upward motion; at exactly .2, hit/no-hit carries coincide. A top-exit schedule is rejected if the changed trace fails to cross the actual integer-height boundary.

Run the pure generator gate:

```sh
python3 -m unittest standalone.autonomy.tests.test_ladder_schedules -v
```

The 37 tests include exhaustive bounded hit/Sneak families, all 256 eight-input Sneak masks, 500 independent-equation traces and 100 comparisons with the existing scalar validator. The full orchestration gate also passed 155 tests before two additional focused edge cases, which passed in the final focused run. No numerical entry probes, horizontal benchmark, finite wall geometry or final ladder route solve is claimed here. See [LADDER_STRATEGY_PLAN.md](../../LADDER_STRATEGY_PLAN.md) for the interface and remaining work.
