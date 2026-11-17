# Bookkeeping baseline evidence

Raw scoped profiles preserved from two completed runs, before bookkeeping or
branch-scheduling changes. These support the
[separate-agent handoff](../../docs/PERFORMANCE.md).

- [Unrestricted300s profile](evidence/2026-11-15/unrestricted300-profile.json),
  [source/configuration provenance](../initial-domain-selection/evidence/2026-11-15/provenance.json),
  [summary](../initial-domain-selection/evidence/2026-11-15/summary.json).
- [Initial0-only90s profile](evidence/2026-11-15/initial0-90-profile.json),
  [provenance](../../docs/validation/layout-final/real11-provenance.json),
  [summary](../../docs/validation/layout-final/real11-summary.json).
- [Timing index with raw-file hashes](evidence/2026-11-15/timing-index.json).
- [First chosen anchor paths](evidence/2026-11-15/chosen-anchor-paths.json), extracted from the unrestricted report for the scheduling investigation; these are witness clocks, not arrival optima.

Profiles are byte-identical copies of local output. Inclusive categories overlap;
self times subtract instrumented children. Hook overhead is included. Numerical
evaluation self time includes unwrapped preparation/refinement/validation work.
The cold profiler uses a progress-summary callback and only saves a shared-store
report after search; durable CLI checkpoint IO needs a separate control. Do not
interpret every copy/hash second as avoidable IO or a promised speedup.

The full unrestricted local checkpoint pair remains under ignored
`standalone/runs/initial-domain-selection-unrestricted-2026-11-15`. The older
tracked full [combined report](../hard-jump-comparisons/2026-11-11/updated-combined/report.json.gz)
and its companion are also available for read-only controls at their recorded
source/domain. The compact refactor witness is not a complete resumable checkpoint.

[Branch scheduling investigation](../../docs/design/BRANCH_SCHEDULING_INVESTIGATION.md)
is separate: this agent must preserve current traversal/policies while measuring
and improving bookkeeping. No new run or timing claim is introduced by copying
these profiles into a durable handoff bundle.
