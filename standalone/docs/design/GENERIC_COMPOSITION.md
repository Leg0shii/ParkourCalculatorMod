# Generic movement programs

2026-10-30. Normal, stepping, ceiling and ladder climb/fall choices share one open-program contract. This replaces the one-stepping-event-per-support-leg restriction in the new orchestration path. Existing support-profile generation remains a compatibility adapter.

## Boundary and composition

A choice consumes the actual scalar Y/VY, ground state and incoming collision state. It supplies relative input units, jump/Sneak controls, ladder membership, finite contacts, named vertical events and positional constraints. Its result is the event's own state: a step, ceiling hit, ladder exit or floor contact. A following landing is optional.

`movement_composition.empty_program`, `boundary_state` and `append_choice` own the common boundary and append operation. Appending retimes controls, contacts, cases and anchors once, then replays the whole accumulated scalar history from the original start. Earlier positions and velocities remain unchanged, except for the terminal carry resolved by the next unit's ladder membership. Every horizontal position, velocity and facing is optimized jointly from the original numerical state.

The scalar replay supports continuing through explicit intermediate landings. Planned intermediate floor contacts are retained instead of being mistaken for unplanned early floors. Blip-up's grounded-but-unsupported result can feed any eligible generator; a jump/run-off tail is no longer baked into its case.

When an input both enters a ladder and hits a wall, post-input membership refreshes climbing carry immediately. The composer resolves that boundary and derives phase ownership from the existing ladder grammar. It keeps coincident stepping/ceiling effects in the typed program. Caps still depend on actual pre-input membership, so the entry-producing input is not clamped early. The existing climb/fall, Sneak and omitted endpoint-hit rules remain authoritative.

Ladder interaction constraints have a local context range. A later interaction with the same region does not force the earlier visit to have been outside it. Complete programs retain each selected episode's finite walls and exit constraints.

## Generators and queue

The registered generators accept `mode=open`:

| Type | Open result |
| --- | --- |
| normal | A selected floor contact, or a requested air interval |
| stepping | Normal step, slab boost, blip, blip-up or either corner event, ending on its named input |
| ceiling | Ordinary ceiling impact or grind, ending at the impact/cancel input |
| ladder_climb | A generated region interaction containing climbing and its allowed local phases |
| ladder_fall | A generated falling-only region interaction |

Normal air leading to an event can be included in a choice. The same existing Y simulator computes its duration. Ladder generators reuse the current schedule generator and mixed-contact helpers. `ladder_ceiling` is an effect within `ladder_climb`, including its optional endpoint stops, rather than a separate route type. Its next input must remain within `ladder_climb`. Shared finite `role=ceiling` contacts still enforce the actual clip; the effect label never causes a climbing refresh or velocity reset. `ladder_ceiling.py` owns prediction/ownership and the generic composer normalizes entry-producing contacts and retimes the effects. See [LADDER_CEILING_PLAN.md](LADDER_CEILING_PLAN.md) and the [saved examples](experiments/ladder-climbing-ceiling/README.md).

`movement_search.iter_movement_programs` extends open prefixes using the enabled registry entries. New registered types participate without adding type-pair dispatch. The queue orders elapsed inputs first. Completed numerical solutions are compared by ticks and then objective. Candidate evaluation uses the existing shared movement compiler, IPOPT, scalar validation and shared finite-scene checker.

`compile_program` requires a grounded goal result for complete numerical acceptance. Prefix screening explicitly requests an open contract. The existing interval screen is used when contacts are fixed; it is skipped when extra wall-hit branches could reset velocity and invalidate a fixed-contact rejection. No optimizer endpoint is used as a new fixed start.

## Standalone entry points

The collision-box CLI now selects generic movement composition by default:

```sh
python3 -m standalone.autonomy.solve_blocks \
  --input standalone/autonomy/fixtures/generic-grind-blip.json \
  --output standalone/autonomy/build/generic-result.json \
  --python "$PWD/.venv-ipopt/bin/python" \
  --seconds 15 --candidate-seconds 1 --retries 0
```

`--support-search` selects the legacy support-profile search for comparisons and existing benchmarks. Support-plane scenes retain their legacy path.

Python callers can use `find_movement_blocks` directly. The existing `find_blocks` API selects the generic path when the request contains `movementSearch: {}`. This preserves the older library call's output contract for existing consumers. The CLI supplies that field automatically for collision-box scenes.

`movementSearch` accepts `maxInputs` (default 200), `maxPrefixes` (256), `maxQueuedPrefixes` (512), `maxChoicesPerType` (64), `maxInteractions` (200), `maxEntryInputs` (14) and `types` (all registered types by default). These are reported work bounds. They impose no one-event or fixed type-order restriction. Incomplete numerical attempts, queue drops and generation caps do not establish global time or objective optimality.

## Evidence and current limits

The focused standalone gate passed 53 tests in 14.680 seconds. It includes 13 generic-composition tests, existing movement generation/units, stepping cases, floor checks and combined step/wall/ladder contracts. All 51 numerical tests passed in 24.099 seconds. No mod Java test suite or game replay suite was run for this work.

New complete numerical/shared-scene witnesses include:

- Cold geometry discovery of grind, normal air and blip in five inputs, with no intermediate landing. The CLI smoke completed in about 3.9 seconds.
- Grind, blip, three ground inputs, another ceiling hit and landing in 12 inputs. The test prescribes the event choices; it does not claim cold optimal discovery.
- Two airborne ladder interactions followed by a blip, with no intermediate ground contact, in 28 inputs.
- Five airborne ladder interactions followed by the real final landing in 58 inputs. This adapts the existing episode contract through the generic composer and solves X/Z again from the original state; it is not a new cold five-region discovery.

Pure tests cover five repeated stepping units, generic registration without queue changes, elapsed-time ordering, exact state continuity, immediate entry-hit climbing carry, cap/deadline/cancellation reporting and preserved prior state.

Search efficiency remains unfinished. A separate cold one-ladder smoke with reduced schedule limits returned unknown after its 18-second budget before producing a complete numerical proposal. The existing type/schedule mechanisms and prescribed mixed chains have numerical acceptance; exhaustive cold discovery is not claimed. Route pruning, schedule dominance, caching/resumable enumeration and water remain later work.
