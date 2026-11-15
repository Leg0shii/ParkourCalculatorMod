# Initial-delay selection without a fixture whitelist

Question: can the orchestrator choose first-jump delays through its existing
reachability, hard-transfer comparison and finish-bound logic, rather than
being restricted to the Real11 fixture's `[0,8]` list?

The fresh config removes `initialJumpInputs` from the curated finite physical
anchor case. Geometry, required anchors, movement families and every search
setting are retained. Provenance describes this domain change. Omission already
maps to `InitialJumpDomain(None)`, which imposes no first-jump whitelist within
other existing limits. No production algorithm or policy version changes.

From `standalone/`, using the [runbook](../../docs/RUNBOOK.md) native environment:

```sh
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input research/initial-domain-selection/configs/real11-unrestricted.json \
  --output /new/path/unrestricted --seconds 300
"$PKC_IPOPT_PYTHON" -B research/initial-domain-selection/audit.py \
  --reports /new/path/unrestricted/report.json.gz \
  --output /new/path/first-anchor-audit.json
```

Use fresh paths. `audit.py` reads saved root-arrival attempts, meaningful-node
observations and recoverable deferrals. It does not inject seeds, change queues
or claim to list every screened descriptor. Profile output retains the complete
manifest/SQLite checkpoint pair and independent witness replays. Compact evidence
and the configuration/policy audit are linked below after the run completes.

## Result: existing selection works without the whitelist

Source `bbb375cf`,300s cold run, no saved decisions/facings seeded. The run found
T64 at Z−1093.2381026602416 and all55 retained-witness physical replays passed.
Runtime hashes stayed unchanged. The budget ended with `deadline`; optimality
and exhaustive coverage are not claimed.

| First jump | First-span numerical preparation | B2 arrival / outcome |
| --- | --- | --- |
|8 | One anchor feasibility invocation, accepted from a valid seed without launching a worker. | B2@17; learned the hard B1→B2 latch. |
|0 | Source-local setup-hop feasibility at B1@12 (seed return), then one worker for the two-jump arrival. | B2@21; protected by more J and fewer R. |
|9 | No numerical invocation for this root arrival. | B2@18, J1/R9/A8; saved as recoverably deferred against8. |
|10 | No numerical invocation for this root arrival. | B2@19, J1/R10/A8; recoverably deferred. |
|11 | No numerical invocation for this root arrival. | B2@20, J1/R11/A8; recoverably deferred. |
|12 | No numerical invocation for this root arrival. | B2@21, J1/R12/A8; recoverably deferred. |

These are saved query/deferral records, not every generated descriptor. Later
work is also subject to the incumbent finish bound. No recovery of these four
root alternatives was needed. Retaining them distinguishes heuristic deferral
from a physical-impossibility claim or a permanent whitelist.

| Recorded arm | Finish | Invocations / workers | Expansions | Root queried first jumps | Total deferrals / root deferrals |
| --- | ---: | ---: | ---: | --- | ---: |
| Earlier restricted `[0,8]`,300s |64 |61 /55 |35 |0,8 |8 /0 |
| Unrestricted,300s |64 |61 /55 |39 |0,8 |12 /4 |

The unrestricted arm adds four expansions and four saved root deferrals, without
additional numerical invocations in these runs. The restricted comparison is
archived evidence from the earlier implementation source; this is a domain/
selection control, not an isolated timing or speedup experiment. Both sources
implement the same search policy; current code has since been repackaged.

Evidence:

- [Unrestricted first-anchor audit](evidence/2026-11-15/unrestricted-first-anchor-audit.json)
- [Earlier restricted first-anchor audit](evidence/2026-11-15/restricted-first-anchor-audit.json)
- [Summary](evidence/2026-11-15/summary.json), [source/configuration provenance](evidence/2026-11-15/provenance.json), [invocation fingerprints](evidence/2026-11-15/requests.json)
- [Configuration and CI audit](../../docs/INITIAL_DOMAIN_AND_CONFIG_AUDIT.md)

The full local report remains under ignored
`standalone/runs/initial-domain-selection-unrestricted-2026-11-15`, paired with
its SQLite companion. The maintained case now removes the initial-delay field;
archived inputs/results remain unchanged. Old restricted checkpoints keep their
old domain identity and require the original input for exact resume.

Validation after updating the maintained case:37 focused semantic-search,
shared-comparison and anchor-DFS tests pass with native dependencies and no skips.
The explicit-domain regression also checks that omitting the restriction admits
an otherwise filtered first jump. The maintained case matches the cold experiment
in every non-provenance field. Layout validation retains all902 protected original
file hashes; current maintained documentation links pass. No production Python
source changed, so the existing610-test runtime acceptance hashes still match.
