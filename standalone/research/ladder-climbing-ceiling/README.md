# Climbing with ceiling contacts and endpoint stops

2026-10-30. Four complete original-state IPOPT witnesses using [the same finite scene](../../fixtures/ladder-climbing-ceiling.json), shared movement compilation, scalar validation and shared scene acceptance. The climbing controls are prescribed; the normal continuation to the goal is generated through the common open interface. These are feasible schedules, not cold discovery or global optimality certificates.

| Witness | Omitted primary wall hits | Ceiling inputs | Goal inputs |
| --- | --- | --- | --- |
| [Continuous](continuous.json) | None | 0, 1 | 12 |
| [Beginning stop](beginning-stop.json) | 0 | 0 | 12 |
| [End stop](end-stop.json) | 2 | 0, 1 | 10 |
| [Both stops](both-stops.json) | 0, 2 | 0 | 10 |

All four schedules are climbing, including the omitted endpoint hits. No input uses Sneak. Input 3 produces the side exit and has no climbing wall hit. The ceiling plane for the player's feet is `4 - float(1.8) = 2.200000047684`. Ceiling overlap is required at the pre-input X/Z state, since Y resolves first. The player must leave its finite footprint before the unselected upward inputs. A selected ceiling input is always followed by another input owned by climbing, including the legal end stop.

| State | Continuous Y | Beginning stop Y | End stop Y | Both stops Y |
| --- | --- | --- | --- | --- |
| 0 | 2.000000000000 | 2.000000000000 | 2.000000000000 | 2.000000000000 |
| 1 | 2.200000047684 | 2.200000047684 | 2.200000047684 | 2.200000047684 |
| 2 | 2.200000047684 | 2.121600046158 | 2.200000047684 | 2.121600046158 |
| 3 | 2.317600049973 | 2.239200048447 | 2.317600049973 | 2.239200048447 |
| 4 | 2.435200052261 | 2.356800050735 | 2.354448052933 | 2.276048051407 |

[y-heights.tsv](y-heights.tsv) includes Y and stored VY at every state through the final landing for all four witnesses. State 0 is the original start. Input t produces state t+1.

The beginning stop really clips the jump against the ceiling but omits the horizontal hit, so stored VY becomes `-0.078400001526`; the following input descends before refreshing climbing carry. The end stop is outside the ceiling footprint and retains ordinary velocity evolution: `+0.117600002289 -> +0.036848002960 -> -0.042288957922`. Neither the event label nor the stop flag resets velocity. This is why the end stop lowers the exit and reaches the final floor sooner in this scene.

Each JSON contains the source fixture hash, full numerical problem, route, typed plan, candidate, finite geometry and accepted scene result. Every X/Z position and velocity is solved jointly from the original start; the intermediate ladder exit is not frozen.

`test_ladder_ceiling.py` runs fresh solves for all four combinations and independently revalidates these saved examples. Negative controls remove, mistime, add or substitute the finite box of a ceiling contact and must fail shared scene acceptance. Pure tests cover ownership, endpoint heights/carry, successor rejection, repeated sequences/prefixes, generation with changed exit timing, entry-contact transfer and second-ladder retiming.

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m unittest standalone.autonomy.tests.test_ladder_ceiling -v
```
