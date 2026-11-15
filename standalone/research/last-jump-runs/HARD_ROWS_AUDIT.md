# Why the request has278 rows

This is an offline comparison of the saved numerical requests; no new solve or production change. [Structured counts](evidence/hard-row-breakdown.json) preserve the measured breakdown.

## The earlier100-row example was the other setup family

The current combined retry's physical-finish call9 has278 raw wall rows. The earlier combined run's physical-finish call9 **also had278**, with the identical problem hash `f05d5e3a7884…`. Its free-air call8 had272. There is no row-count growth between those matching requests.

The~100 figures match the earlier **initial0** run:104/106 for free-air,110/112 for physical-contact variants. For a like-contact comparison, the table uses initial0 call8 (physical B7 collision at64) versus the current call9 (physical B7 collision at67). Initial0 has zero run inputs and six jumps; initial8 has15 total run inputs and five jumps.

| Row source | Earlier initial0 physical finish | Current initial8 physical finish | Difference |
| --- | --- | --- | --- |
| Ordinary support/landing bounds (`normal:landing`) | 24 | 80 | +56 |
| Finite-contact footprint bounds (`contact:finite_*`) | 44 | 156 | +112 |
| Initial support bounds | 8 | 8 | 0 |
| Intermediate spatial landing-goal bounds | 16 | 16 | 0 |
| Obstacle avoidance | 16 | 16 | 0 |
| Corridor repair | 2 | 0 | −2 |
| Virtual finish | 2 | 2 | 0 |
| **Raw total** | **112** | **278** | **+166** |

The increase is in ground/support/contact bookkeeping. The obstacle-avoidance count stays16 in both requests.

## There is also actual duplication in assembly

These are raw entries, not278 independent requirements. Comparing the actual variable/expression, tick, comparison and exact numeric bound, while ignoring the diagnostic name:

| Count | Initial0 | Initial8 |
| --- | --- | --- |
| Raw entries | 112 | 278 |
| Distinct exact constraints | 60 | 114 |
| Exact duplicate entries | 52 | **164** |
| Bounds after keeping only the strongest lower/upper bound per axis/tick | 41 | **91** |

For these requests every row is a simple X/Z bound on one state, so keeping the maximum lower bound and minimum upper bound per variable is a direct equivalence-preserving simplification.91 is not a count after a new relaxation or decision pruning.

The source of the copies is concrete:

1. `open_movement.normal_choices` already adds four player-center footprint bounds for each ordinary landing/ground-hold event, via `normal:landing`.
2. `contact_combinations.compile_contact_events` generates four bounds for that same finite Y-contact footprint.
3. `checkpoints.retain_prefix` copies compiled plan walls into the next program. Later compilation regenerates finite-contact bounds that were already retained.

The initial8 final program has20 Y-contact events:15 ordinary ground-hold inputs and five actual landing collisions (B2/B3/B4/B5/B7). Its80 ordinary bounds duplicate the80 newly compiled finite bounds. Another76 finite bounds were inherited from the preceding19 contacts. Thus the finite-contact category has156=80+76 entries. Initial0 has six contact events, hence24 ordinary bounds and44=24+20 finite bounds. The difference of14 contact events accounts for56+112 extra raw rows.

Example from the current request: `X[17] >= 740.7000009880791` appears **three times**—once as `normal:landing`, twice as `contact:finite_lo`. The name changes do not change the constraint. Four duplicated initial rows and four duplicated avoidance rows account for the remaining exact-copy overhead.

The existing search deduplication avoids repeated branch models; it does **not** normalize all rows in the final assembled request. `ipopt.model.prepare` validates/defaults the row records, and `worker.fixed_phase` currently adds each record to the numerical model. Therefore this is a real assembly inefficiency, not merely a reporting count. Its share of total runtime has not been measured; the overall run also spends time generating candidates, validating and persisting state.

## Potential fix, not implemented by this explanation

Canonicalize final solver rows after assembly: remove exact duplicate mathematical constraints and, for simple positional bounds, keep the strongest lower/upper bound. Preserve source names/owners as provenance for diagnostics and future projection rather than treating those labels as different numerical requirements. Do not discard distinct interaction choices, rederive physics or change corridor semantics. Mixed expressions and facing rows need their own exact treatment; the91-row count above is specifically established for this all-simple-bound request.

This cleanup is separate from the deferred completion-time pruning and from corridor/cell redesign. No runtime code, candidate count or solve budget was changed in this audit.

## Measured performance follow-up

[Fixed-request A/B and callback benchmark](row-benchmark/README.md): duplicate removal improves a solve phase from46ms to34ms (33ms with strongest bounds), while fresh-worker medians are about199ms versus177/183ms. Ordinary per-request preparation/hash/validation overhead is milliseconds. A larger measured issue is synchronous progress encoding/compression: a late109MB JSON snapshot takes about4s and can consume a numerical-call deadline by itself. The benchmark rechecks all24 A/B outcomes against the original rows and does not change production behavior. The full317.53s has not been exclusively profiled; do not attribute all remaining time to row duplication or reporting.

## Bound extraction and snapshots explained

The [follow-up probe/explanation](row-benchmark/README.md#can-we-simply-enable-bound-extraction) confirms extraction finds a valid solution faster in this sample, but the current monitor misinterprets the returned constraint vector and rejects it. Recomputing the original constraint expressions in the monitor fixes that in the prototype; the one-line option change is therefore insufficient. Production remains unchanged. The same page explains snapshot contents, synchronous full-report rewrites and why saving can consume a numerical-call deadline even while the worker subprocess continues. Bound extraction does not shrink those stored Python records.
