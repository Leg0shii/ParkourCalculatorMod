# Angle-solver apply resimulates from the solve start tick, not from tick 0

Status (2026-11-10): the decision stands. Since Block Overrides (#277) the simulator does see per-tick block changes in singleplayer (the rows of the Block Overrides table), which is the deferred option below in a table-driven form; a block the TAS clicks without a row is still read in its current world state.

Applying an angle-solver result rewrites the input rows from `startTick` onward and then resimulates with `simulateFrom(startTick)` (a partial resim resuming from the cached checkpoint), instead of a full resim from tick 0. A full resim reads every interactable block (a trapdoor the TAS clicks open) in its single current world state, which has no per-tick history, so it corrupts the hand-curated path upstream of the solve and forces one manual trapdoor fix per trapdoor on every apply. Because the solver only ever operates within a contiguous collision-free segment downstream of all interactable blocks, the cached path up to `startTick` is exactly the curated state worth keeping, and the existing post-apply deviation check (1e-9 per tick) still flags any divergence rather than letting it pass silently.

## Considered options

- **Full resim from tick 0** (the prior behavior). Stateless and always recomputes from world plus inputs, but destroys interactable-block curation, the problem this reverses. Its rationale, a comment warning that a partial path "can pick up stale entity state," predates the `restoreCheckpoint` reset-baseline fix and the deviation check, so it no longer holds.
- **Model block-state changes in the simulator** so a full resim stays correct on its own. The proper long-term fix, but much larger (a per-tick block-state timeline replayed into the sim); deferred.

## Consequences

- Apply now depends on the currently cached path up to `startTick` rather than being a pure function of world plus inputs. Applying while the upstream path is still un-curated yields a wrong tail; in practice the run is always curated up to the solve point first.
- Scope is angle-solver apply only. Velocity-finder apply clears inputs and sets a new start state, and file-reopen starts with no cached path, so both must still full-resim and are unchanged.
