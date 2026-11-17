# Real11 retry after the handoff push

The user reauthorized a fresh combined initial0/8 solve after pushing the branch and writing the [corridor-agent handoff](../../../docs/CORRIDOR_AGENT_HANDOFF.md). The fork branch was verified at `61355976` before launch. This run uses that source, the unchanged [virtual-finish fixture](../virtual-finish/fixture.json), the same candidate limits and300-second budget, and local-run policy1. No recorded prefix/yaw is supplied. Corridor/cell behavior and the continuous model are unchanged.

[Raw report](evidence/real11-retry-after-handoff.json.gz), [run log](evidence/real11-retry-after-handoff.log), [verified summary](evidence/real11-retry-summary.json) and [all12 numerical requests](REAL11_RETRY_CALLS.md) preserve exact requests, scope/branch diagnostics, excluded programs and continuation state. `summarize_retry.py` reuses the existing DFS auditor to verify request hashes, original state, zero thresholds, variable facings, unique active frame ownership and independent replay.

## Result

| Measurement | Fresh combined retry |
| --- | --- |
| Best discovered finish | 67 |
| Z67 | −1093.8231291279699 |
| Final X | 728.850632960287 |
| Actual Y / on ground | 7 / true, physical B7 collision |
| Jump inputs | 8,19,30,43,58 |
| Required support arrivals | B2@17, B3@28, B4@40, B5@58 |
| First complete solution | 101.85s /8 worker calls |
| Worker calls in whole session | 12 |
| Independent witness replay checks | 13/13 pass |
| Local-run exclusions | 9 |
| Repeated identical problem hashes | 0 |
| Actual search elapsed | 317.53s |
| Termination | deadline, unfinished work saved |

First completion is the free-air variant after call8; call9 supplies the better-Z physical-contact incumbent. Reported elapsed includes progress persistence and post-deadline handling. This run did not overlap another numerical test suite. Neither minimum time nor maximum Z is certified. The initial0 root sibling remains saved and uncontinued; the older independent initial0 diagnostic at64 is not rediscovered by this combined run.

## What pruning changed

After call7 validated B4 arrival40→jump43→B5 arrival58, the rule learned **three local runs** for that interaction pattern. It excluded jumps44–52 that simply continue standing on B4 (local4–12 runs), before numerical dispatch. In particular, the pre-policy run4/8/9 prefix requests corresponding to old cold10–12 were not dispatched. Source and fixture contain no hardcoded3/4 threshold.

Calls8 and9 persist, as requested, with no change to corridor handling. They have the same inputs but different final-contact constraints (free overshoot and physical B7 collision). The archived corridor-induced distinctions and repairs remain visible for the other agent; no special-case suppression or deduplication was introduced here.

The total still reaches12 worker calls because saved work is redirected elsewhere:

- New call10 proposes an extra B4 self-jump, with jumps40 and52 before B5 arrival67. Its latest jump has **zero** runs after landing back on B4 at52. That is a different prior interaction history from standing on B4 from40 to52, so the local-run rule correctly does not merge it. Worker feasibility was returned but the overall evaluation remains unknown at its deadline.
- New call11 revisits B5@58 with the same3-run preparation and one additional avoidance row, `X[56] >= 731.3000010119209`, labelled `blockage:5` (B6). Equal-run constraint variants remain eligible. The stored prefix model has240 rows versus239 for call7.
- New call12 continues that variant to the free-air finish at67, with273 rows versus272 for call8. The added row is the same B6 avoidance row, not a `cell_corridor` row. Do not attribute every remaining extra request to corridor repair without inspecting its rows.

All12 request hashes are distinct. This is an exact-model accounting statement, not a claim that every remaining request is globally necessary. More time reasoning and discrete decision reuse remain deferred; corridor/cell improvement belongs to the other agent. The saved state and ledger allow those costs to be inspected without rerunning this experiment.

## Publication and reproduction

The first fork publication used SSH after HTTPS transport failures/stalls; `origin` remains configured to the user's HTTPS fork and tracks `origin/research/ipopt-cell-routes`. No force push or pull request was used. The handoff was pushed and its remote hash verified before this solve.

Use the native environment in the [runbook](../../../docs/RUNBOOK.md), choose a new output filename, and run the existing `cell_routes.fixed_clock_experiment` CLI against `virtual-finish/fixture.json`. For an offline summary only:

```sh
PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/summarize_retry.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/real11-retry-summary.json --calls /tmp/real11-retry-calls.md
```

No production code or test expectations were changed for this retry. The52 focused regression acceptance remains the latest implementation gate.

## Budget and “hard rows” clarification

Yes, this session stopped at its300-second wall-time search deadline. The recorded317.53s includes progress persistence and post-deadline handling. First completion arrived after101.85s; the remaining budget explored other histories/constraint variants. It did not exhaust the solver-call allowance:12 of1024 calls were used. Unfinished work is saved, including the initial0 root; timeout is not an infeasibility verdict.

“Hard rows” in the call ledger is exactly `len(worker_request['walls'])`: the number of explicit scalar constraint records sent with that numerical problem. For example, `X[56] >= 731.3000010119209` is one row. A rectangular player-center landing region generally contributes four bounds (lower/upper X and Z); constraints on multiple ticks contribute additional rows. Rows can include support/landing, avoidance, corridor-repair and inherited constraints. The count is neither the number of decisions nor the number of ticks, and it can include repeated/redundant entries. It does not count the model's automatically constructed motion equations, control restrictions or other separately represented solver conditions. “Hard” means the solver is asked to satisfy the condition within numerical tolerance, rather than merely reward it in the objective.

## Detailed constraint-count audit

[Hard-row audit](HARD_ROWS_AUDIT.md) compares the earlier~100-row initial0 request with the278-row initial8 request. The latter already had278 in the prior combined run. It contains164 exact duplicate entries (114 distinct constraints); merging weaker simple bounds would leave91. Counts and assembly provenance are documented without a new solve or runtime change.
