# Generated contacts across movement units

2026-10-29. This extends [MOVEMENT_UNITS.md](MOVEMENT_UNITS.md) and closes the earlier first-episode-only contact-generation limitation. No route-specific rarity or directional pruning was added. Shared movement arithmetic and the IPOPT worker are unchanged.

## Generation and composition

`movement_contacts.contact_variants` supplies the same finite-contact proposal stream for an open initial episode, an appended approach/ladder interval, and the final normal-air interval. It includes both signs of horizontal contacts, ceiling contacts, same-input and consecutive-input combinations, independent contacts separated by free movement, and repeated ceiling clips under sustained climbing. A single predicted ceiling hit is also retained, because a trajectory can leave the finite underside before another clip.

The existing `contact_opportunities`, `contact_combinations`, `ceiling_sequence` and scalar Y functions supply these candidates. Explicit `independent_events=True` permits independent normal events to coexist in a global fixed schedule without pretending they are one coupled contact. The previous default same/consecutive-input enumeration remains unchanged for its existing callers.

`mixed_schedule_pool` feeds contact alternatives into the existing ladder schedule generator before it chooses the entry, climb/fall/stop/Sneak pattern and exit. This matters when a contact makes an otherwise impossible entry possible, or changes the number of climbing inputs needed to leave at the top. A post-hoc check against the old exit time would miss both cases. The existing initial scalar state, prior horizontal-collision flag, prefix contacts and complete input history remain carried.

`append_ladder_programs` now applies that expansion every time it appends a region. The contact opportunities cover the connecting air interval as well as the new ladder. Each result is retimed and recomposed through all previous episodes from the original state; it is still an unprobed X/Z proposal until the joint numerical solve succeeds. `minClimbInputs`, when explicitly requested, applies to every appended episode too; its default remains zero, preserving optional climbing.

`finish_programs` applies the same contact stream after the last region exit. It recomputes landing time with the original episodes and initial state. This works even when an extra contact is needed to bring a landing inside the input horizon. Actual body-height intervals, finite faces, collision order, caps, sprint cancellation and the carried air factor are checked by the existing full-route compiler and scene acceptance.

The older `mixed_ladder_schedules` compatibility helper uses the same proposal stream. Its fixed-timing `with_contacts` helper now accepts an airborne terminal as well as a landing. New timing choices use `mixed_schedule_pool`, not a separate Y implementation.

## Work budgets

`maxContactVariants` and `maxMixedContacts` retain their existing roles. Predictive repeated-ceiling sequences can contain more clips than the normal combined-event count; this preserves sustained climbing against an underside. Contact alternatives are capped to the schedule-request schema's 256 variants per expansion, with actual generation metadata retained.

Contact families and entry states are interleaved before output/ranking caps. Initial transfer windows retain separate contact histories at the same exit state, while the window cap counts distinct exit states. Otherwise a newly generated wall/ceiling alternative could be dropped merely because a plain schedule used that exit first.

Mask-work and output budgets are shared across contact alternatives. Each alternative receives a separate bounded scheduler call so a large entry/contact Cartesian product cannot monopolize skeleton construction. Its skeleton allowance stays below its mask allowance so the round-robin scheduler can resume a valid trace and actually emit a schedule. Reported mask work stays within the shared budget. These are work-allocation rules, not geometric pruning or infeasibility proofs.

The stream remains bounded and is not exhaustive. Unknown numerical solves, unvisited alternatives, dropped queue entries and limited entry horizons retain their existing meanings. Stepping/water and rare route-specific pruning remain follow-ups.

## Acceptance and regressions

The new snapshots in [experiments/contact-combinations](experiments/contact-combinations/README.md) contain compiled problems, accepted candidates and shared-scene acceptance:

| Case | Inputs | Generated change |
| --- | --- | --- |
| `appended-air-ceiling` | 29 | Ceiling clip on input 6 between regions; the second climb is regenerated |
| `appended-ladder-ceiling` | 29 | Ceiling clip on input 20 during the second climb; its top exit moves from state 25 to 26 |
| `final-air-ceiling` | 27 | Ceiling clip on input 25 after the last exit; landing moves from state 28 to 27 |

These are generated continuations from a prescribed first-episode prefix, not cold discovery of an entire multi-ladder route. The final-air case begins with a prescribed two-episode prefix. The tests regenerate contacts/timings and run fresh IPOPT solves with the full supplied scene. Every accepted case has one launch, two regions and no intermediate grounded state. Saved problems also pass independent scalar/scene revalidation.

`test_movement_contacts.py` additionally covers contact-assisted entry with no baseline schedule, retimed top exits, repeated ceiling/climb clips, all axis subsets, signed faces, independent normal contacts, inherited-contact conflicts, unchanged prefix Y/VY, deadline/cancellation, and a small shared budget that must still emit a valid schedule. Nine pure tests pass; the generated-contact integration gate covers all three accepted continuations. Existing cold discovery, five-region falling-only and bottom-exit integration regressions also pass.
