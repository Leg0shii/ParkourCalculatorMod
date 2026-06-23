# Minecraft Parkour Wiki, in-repo reference

A faithful mirror of the physics-and-mechanics pages of the Minecraft Parkour Wiki (`https://www.mcpk.wiki`), captured here because the wiki sits behind a Cloudflare challenge that an agent cannot fetch. This is reference material (constants, formulas, source snippets, tables), not implementation. The plain-language glossary lives in the repo-root `CONTEXT.md`.

This tool is a byte-exact replica of the 1.8 movement model documented here, so these constants are ground truth: if the code disagrees with a number on these pages, the code is almost certainly the bug. Versions noted as 1.9+ / 1.13 / 1.14 / 1.15 are differences the Fabric 1.21.10 loader must account for.

## Files

- [01-movement-formulas.md](./01-movement-formulas.md) — the core horizontal and vertical movement formulas, the multipliers (move, effects, slipperiness), 45 strafe, and the non-recursive closed forms.
- [02-angles-and-mouse.md](./02-angles-and-mouse.md) — ticks and the turn tick, mouse sensitivity, yaw vs facing, the sine table, significant angles, half angles.
- [03-collisions-and-stepping.md](./03-collisions-and-stepping.md) — collision order (Y-X-Z), X-facing vs Z-facing, stepping, blips, jump-cancel, the 1.14+ change.
- [04-blocks-and-friction.md](./04-blocks-and-friction.md) — block collision boxes, slipperiness, soulsand, cobweb, ladders and vines.
- [05-status-effects-tiers-jumps.md](./05-status-effects-tiers-jumps.md) — speed/slowness/jump boost tables, tiers, jump durations, longest jump per tier.
- [06-timings-momentum-glitches.md](./06-timings-momentum-glitches.md) — timings, tapping, backward momentum, anvil/chest manipulation, ceiling hover.

Source pages were last edited between 2021 and 2026; see each section's attribution line.
