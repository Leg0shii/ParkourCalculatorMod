# Every generated B6 option: logged replay

This is a fresh replay on production revision `732624f575b90c5bfb8345e6bdb8515350e3242a` of **all four B6 catalogue contexts** captured in the latest continuous-search run.
Histories A/B are the final 67-tick journeys (jumps **8, 19, 30, 43, 58**). C/D are the earlier partial journeys ending at 57/58. B6 box: `[730, 0, -1095, 731, 20, -1094]`.

**Scope:** every option returned by the B6 option generator, after its internal masks and duplicate suppression. All four catalogues were run to exhaustion. Internal discarded DFS states are not called options here.
**Results are freshly logged B6-prefix pre-check decisions.** The B7 alternatives and angle solver were not run in this replay. “Retained” means not ruled out, not a proven feasible path.
The recorded-run column distinguishes original logged decisions from options reached only in this complete replay. Every recorded decision was checked against the fresh result. Order and visited-node prefixes were also checked for A/B/D; C’s completed cursor was already removed, so its original order cannot be compared.

## Reading the entries

Face labels: `X−` is the winning side (`X ≤ 729.6999989880791`); `X+` is the opposite side (`X ≥ 731.3000010119209`); `Z−` is behind B6 (`Z ≤ −1095.300001011921`); `Z+` is past its far end (`Z ≥ −1093.699998988079`). `Y−` / `Y+` are below / above labels.
Switch tuples are the generator’s recorded gates, in order. The next face starts at the same state or the next state according to collision-axis order. The requirements column is authoritative about which states actually receive constraints.
Early labelled phases can have no active constraints. Equal constraints can remain separate options because their nominal blockage counts differ. “Same rows” identifies that relationship within a history.
Requirement symbols below use the face labels above; all numeric row values and LP backend statuses are preserved in the JSON evidence. A missing historical decision is labelled unavailable rather than inferred.

## Summary

| History | Complete options | Emitted in old run | Rejected now | Retained now |
| --- | ---: | ---: | ---: | ---: |
| A | 277 | 255 | 237 | 40 |
| B | 153 | 39 | 117 | 36 |
| C | 18 | 18 | 10 | 8 |
| D | 34 | 4 | 21 | 13 |

**Total: 482 logged options; 316 were emitted in the original run.**
All 316 available historical decisions match the fresh replay.

Across the complete rerun, **385 were rejected and 97 were retained**. Retained
means only that B6's pre-check did not reject the option. An angle-solver result
belongs to a coupled B6+B7 combination, and is not assigned to a B6 option here.

**Inconclusive checks:** A228 and A233 were retained because the LP returned status 4 (unknown/numerical issue). They are not labelled feasible. The exact backend messages are in the evidence.

The [evidence index](b6-options-evidence/index.json) links each data filename to
its SHA256. The [run summary](b6-options-evidence/summary.json) records source
hashes, completed catalogues, historical verification and the replay scope.
The [event log](b6-options-evidence/events.jsonl.gz) contains one generated and
one checked event per option, **964 events total**. Full rows, rejection
certificates and LP statuses are in the compressed option records for
[A](b6-options-evidence/history-A-options.json.gz),
[B](b6-options-evidence/history-B-options.json.gz),
[C](b6-options-evidence/history-C-options.json.gz) and
[D](b6-options-evidence/history-D-options.json.gz).

The [logging driver](log_b6_options.py) generated the catalogue from the actual
saved run, using production generation and screening. The original manifest and
its SQLite companion remain together under the recorded run path. Reproduce
from `standalone/` with its installed interpreter and a fresh output directory:

```sh
env OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 \
  ../.venv-bookkeeping/bin/python -u research/early-passage-screen/log_b6_options.py \
  --report runs/early-passage-screen-cold300/report.json.gz \
  --compiler build/native/compiler.json --output /new/path/b6-ledger \
  --document /new/path/B6_OPTIONS.md
```

## Recorded contexts with no B6 passage choices

| Horizon | Jump inputs | Saved reason |
| --- | --- | --- |
| 17 | [8] | `all_contact_phases_unreachable` |
| 27 | [8, 18] | `all_contact_phases_unreachable` |
| 28 | [8, 19] | `all_contact_phases_unreachable` |
| 40 | [8, 19, 30] | `all_contact_phases_unreachable` |

## History A

Context: `ba9ac0a771d965da7715474a90ad9fddae78a11a323e460d4d18b1e205c4f1f4`.
Horizon: **67**. Jump inputs: **[8, 19, 30, 43, 58]**.

| ID | Recorded 300s run | Face sequence | Switches | Active added requirements | Fresh result | Reason | Same rows |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A001 | retained | Z− → X− | 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A002 | retained | Z− → X− | 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A003 | retained | Z− → X− | 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A004 | retained | Z− → X− | 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A005 | rejected | Z− → X− | 59 | X−@60–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A006 | retained | Z− → X− | 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A007 | rejected | Z− → X− | 0 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A008 | rejected | Z− → X− | 58 | X−@59–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A009 | retained | Z− → X− | 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A010 | retained | X− → Z− → X− | 0, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A001 |
| A011 | retained | X− → Z− → X− | 0, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A002 |
| A012 | retained | X− → Z− → X− | 0, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A003 |
| A013 | retained | X− → Z− → X− | 0, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A004 |
| A014 | rejected | X− → Z− → X− | 0, 59 | X−@60–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A005 |
| A015 | retained | X− → Z− → X− | 0, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A006 |
| A016 | rejected | X− → Z− → X− | 0, 0 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A017 | rejected | X− → Z− → X− | 0, 58 | X−@59–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A008 |
| A018 | retained | X− → Z− → X− | 0, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A009 |
| A019 | rejected | X− → Z− → X− | 59, 61 | X−@59,62–67; Z−@59–61 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A020 | rejected | X− → Z− → X− | 59, 62 | X−@59,63–67; Z−@59–62 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A021 | rejected | X− → Z− → X− | 59, 60 | X−@59,61–67; Z−@59–60 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A022 | rejected | X− → Z− → X− | 59, 63 | X−@59,64–67; Z−@59–63 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A023 | rejected | X− → Z− → X− | 59, 59 | X−@59–67; Z−@59 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A024 | rejected | X− → Z− → X− | 59, 64 | X−@59,65–67; Z−@59–64 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A025 | rejected | X− → Z− → X− | 59, 65 | X−@59,66–67; Z−@59–65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A026 | rejected | X− → Z− → X− | 60, 61 | X−@59–60,62–67; Z−@60–61 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A027 | rejected | X− → Z− → X− | 60, 62 | X−@59–60,63–67; Z−@60–62 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A028 | rejected | X− → Z− → X− | 60, 60 | X−@59–67; Z−@60 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A029 | rejected | X− → Z− → X− | 60, 63 | X−@59–60,64–67; Z−@60–63 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A030 | rejected | X− → Z− → X− | 60, 64 | X−@59–60,65–67; Z−@60–64 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A031 | rejected | X− → Z− → X− | 60, 65 | X−@59–60,66–67; Z−@60–65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A032 | rejected | X− → Z− → X− | 61, 61 | X−@59–67; Z−@61 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A033 | rejected | X− → Z− → X− | 61, 62 | X−@59–61,63–67; Z−@61–62 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A034 | rejected | X− → Z− → X− | 61, 63 | X−@59–61,64–67; Z−@61–63 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A035 | rejected | X− → Z− → X− | 61, 64 | X−@59–61,65–67; Z−@61–64 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A036 | rejected | X− → Z− → X− | 61, 65 | X−@59–61,66–67; Z−@61–65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A037 | rejected | X− → Z− → X− | 62, 62 | X−@59–67; Z−@62 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A038 | rejected | X− → Z− → X− | 62, 63 | X−@59–62,64–67; Z−@62–63 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A039 | rejected | X− → Z− → X− | 62, 64 | X−@59–62,65–67; Z−@62–64 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A040 | rejected | X− → Z− → X− | 62, 65 | X−@59–62,66–67; Z−@62–65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A041 | rejected | X− → Z− → X− | 63, 63 | X−@59–67; Z−@63 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A042 | rejected | X− → Z− → X− | 63, 64 | X−@59–63,65–67; Z−@63–64 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A043 | rejected | X− → Z− → X− | 63, 65 | X−@59–63,66–67; Z−@63–65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A044 | rejected | X− → Z− → X− | 64, 64 | X−@59–67; Z−@64 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A045 | rejected | X− → Z− → X− | 64, 65 | X−@59–64,66–67; Z−@64–65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A046 | rejected | X− → Z− → X− | 65, 65 | X−@59–67; Z−@65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A047 | rejected | X+ → Z− → X− | 59, 61 | X+@59; X−@62–67; Z−@59–61 | REJECTED | Propagated X position range is empty at state 62. | — |
| A048 | rejected | X+ → Z− → X− | 59, 62 | X+@59; X−@63–67; Z−@59–62 | REJECTED | Propagated X position range is empty at state 63. | — |
| A049 | rejected | X+ → Z− → X− | 59, 60 | X+@59; X−@61–67; Z−@59–60 | REJECTED | Propagated X position range is empty at state 61. | — |
| A050 | rejected | X+ → Z− → X− | 59, 63 | X+@59; X−@64–67; Z−@59–63 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A051 | rejected | X+ → Z− → X− | 59, 59 | X+@59; X−@60–67; Z−@59 | REJECTED | Propagated X position range is empty at state 60. | — |
| A052 | rejected | X+ → Z− → X− | 59, 64 | X+@59; X−@65–67; Z−@59–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A053 | rejected | X+ → Z− → X− | 59, 65 | X+@59; X−@66–67; Z−@59–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A054 | rejected | X+ → Z− → X− | 60, 61 | X+@59–60; X−@62–67; Z−@60–61 | REJECTED | Propagated X position range is empty at state 62. | — |
| A055 | rejected | X+ → Z− → X− | 60, 62 | X+@59–60; X−@63–67; Z−@60–62 | REJECTED | Propagated X position range is empty at state 63. | — |
| A056 | rejected | X+ → Z− → X− | 60, 60 | X+@59–60; X−@61–67; Z−@60 | REJECTED | Propagated X position range is empty at state 61. | — |
| A057 | rejected | X+ → Z− → X− | 60, 63 | X+@59–60; X−@64–67; Z−@60–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| A058 | rejected | X+ → Z− → X− | 60, 64 | X+@59–60; X−@65–67; Z−@60–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A059 | rejected | X+ → Z− → X− | 60, 65 | X+@59–60; X−@66–67; Z−@60–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A060 | rejected | X+ → Z− → X− | 61, 61 | X+@59–61; X−@62–67; Z−@61 | REJECTED | Propagated X position range is empty at state 62. | — |
| A061 | rejected | X+ → Z− → X− | 61, 62 | X+@59–61; X−@63–67; Z−@61–62 | REJECTED | Propagated X position range is empty at state 63. | — |
| A062 | rejected | X+ → Z− → X− | 61, 63 | X+@59–61; X−@64–67; Z−@61–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| A063 | rejected | X+ → Z− → X− | 61, 64 | X+@59–61; X−@65–67; Z−@61–64 | REJECTED | Propagated X position range is empty at state 65. | — |
| A064 | rejected | X+ → Z− → X− | 61, 65 | X+@59–61; X−@66–67; Z−@61–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A065 | rejected | X+ → Z− → X− | 62, 62 | X+@59–62; X−@63–67; Z−@62 | REJECTED | Propagated X position range is empty at state 63. | — |
| A066 | rejected | X+ → Z− → X− | 62, 63 | X+@59–62; X−@64–67; Z−@62–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| A067 | rejected | X+ → Z− → X− | 62, 64 | X+@59–62; X−@65–67; Z−@62–64 | REJECTED | Propagated X position range is empty at state 65. | — |
| A068 | rejected | X+ → Z− → X− | 62, 65 | X+@59–62; X−@66–67; Z−@62–65 | REJECTED | Propagated X position range is empty at state 66. | — |
| A069 | retained | Y− → Z− → X− | 0, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A001 |
| A070 | retained | Y− → Z− → X− | 0, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A002 |
| A071 | retained | Y− → Z− → X− | 0, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A003 |
| A072 | retained | Y− → Z− → X− | 0, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A004 |
| A073 | rejected | Y− → Z− → X− | 0, 59 | X−@60–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A005 |
| A074 | retained | Y− → Z− → X− | 0, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A006 |
| A075 | rejected | Y− → Z− → X− | 0, 0 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A076 | rejected | Y− → Z− → X− | 0, 58 | X−@59–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A008 |
| A077 | retained | Y− → Z− → X− | 0, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A009 |
| A078 | retained | X− → Y− → Z− → X− | 0, 1, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A001 |
| A079 | retained | X− → Y− → Z− → X− | 0, 1, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A002 |
| A080 | retained | X− → Y− → Z− → X− | 0, 1, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A003 |
| A081 | retained | X− → Y− → Z− → X− | 0, 1, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A004 |
| A082 | rejected | X− → Y− → Z− → X− | 0, 1, 59 | X−@60–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A005 |
| A083 | retained | X− → Y− → Z− → X− | 0, 1, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A006 |
| A084 | rejected | X− → Y− → Z− → X− | 0, 1, 1 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A085 | rejected | X− → Y− → Z− → X− | 0, 1, 58 | X−@59–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A008 |
| A086 | retained | X− → Y− → Z− → X− | 0, 1, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A009 |
| A087 | retained | Y− → X+ → Z− → X− | 0, 0, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A001 |
| A088 | retained | Y− → X+ → Z− → X− | 0, 0, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A002 |
| A089 | retained | Y− → X+ → Z− → X− | 0, 0, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A003 |
| A090 | retained | Y− → X+ → Z− → X− | 0, 0, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A004 |
| A091 | rejected | Y− → X+ → Z− → X− | 0, 0, 59 | X−@60–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A005 |
| A092 | retained | Y− → X+ → Z− → X− | 0, 0, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A006 |
| A093 | rejected | Y− → X+ → Z− → X− | 0, 0, 0 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A094 | rejected | Y− → X+ → Z− → X− | 0, 0, 58 | X−@59–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A008 |
| A095 | retained | Y− → X+ → Z− → X− | 0, 0, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A009 |
| A096 | rejected | Y− → X+ → Z− → X− | 0, 59, 61 | X+@59; X−@62–67; Z−@59–61 | REJECTED | Propagated X position range is empty at state 62. | A047 |
| A097 | rejected | Y− → X+ → Z− → X− | 0, 59, 62 | X+@59; X−@63–67; Z−@59–62 | REJECTED | Propagated X position range is empty at state 63. | A048 |
| A098 | rejected | Y− → X+ → Z− → X− | 0, 59, 60 | X+@59; X−@61–67; Z−@59–60 | REJECTED | Propagated X position range is empty at state 61. | A049 |
| A099 | rejected | Y− → X+ → Z− → X− | 0, 59, 63 | X+@59; X−@64–67; Z−@59–63 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A050 |
| A100 | rejected | Y− → X+ → Z− → X− | 0, 59, 59 | X+@59; X−@60–67; Z−@59 | REJECTED | Propagated X position range is empty at state 60. | A051 |
| A101 | rejected | Y− → X+ → Z− → X− | 0, 59, 64 | X+@59; X−@65–67; Z−@59–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A052 |
| A102 | rejected | Y− → X+ → Z− → X− | 0, 59, 65 | X+@59; X−@66–67; Z−@59–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A053 |
| A103 | rejected | Y− → X+ → Z− → X− | 0, 60, 61 | X+@59–60; X−@62–67; Z−@60–61 | REJECTED | Propagated X position range is empty at state 62. | A054 |
| A104 | rejected | Y− → X+ → Z− → X− | 0, 60, 62 | X+@59–60; X−@63–67; Z−@60–62 | REJECTED | Propagated X position range is empty at state 63. | A055 |
| A105 | rejected | Y− → X+ → Z− → X− | 0, 60, 60 | X+@59–60; X−@61–67; Z−@60 | REJECTED | Propagated X position range is empty at state 61. | A056 |
| A106 | rejected | Y− → X+ → Z− → X− | 0, 60, 63 | X+@59–60; X−@64–67; Z−@60–63 | REJECTED | Propagated X position range is empty at state 64. | A057 |
| A107 | rejected | Y− → X+ → Z− → X− | 0, 60, 64 | X+@59–60; X−@65–67; Z−@60–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A058 |
| A108 | rejected | Y− → X+ → Z− → X− | 0, 60, 65 | X+@59–60; X−@66–67; Z−@60–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A059 |
| A109 | rejected | Y− → X+ → Z− → X− | 0, 61, 61 | X+@59–61; X−@62–67; Z−@61 | REJECTED | Propagated X position range is empty at state 62. | A060 |
| A110 | rejected | Y− → X+ → Z− → X− | 0, 61, 62 | X+@59–61; X−@63–67; Z−@61–62 | REJECTED | Propagated X position range is empty at state 63. | A061 |
| A111 | rejected | Y− → X+ → Z− → X− | 0, 61, 63 | X+@59–61; X−@64–67; Z−@61–63 | REJECTED | Propagated X position range is empty at state 64. | A062 |
| A112 | rejected | Y− → X+ → Z− → X− | 0, 61, 64 | X+@59–61; X−@65–67; Z−@61–64 | REJECTED | Propagated X position range is empty at state 65. | A063 |
| A113 | rejected | Y− → X+ → Z− → X− | 0, 61, 65 | X+@59–61; X−@66–67; Z−@61–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A064 |
| A114 | rejected | Y− → X+ → Z− → X− | 0, 62, 62 | X+@59–62; X−@63–67; Z−@62 | REJECTED | Propagated X position range is empty at state 63. | A065 |
| A115 | rejected | Y− → X+ → Z− → X− | 0, 62, 63 | X+@59–62; X−@64–67; Z−@62–63 | REJECTED | Propagated X position range is empty at state 64. | A066 |
| A116 | rejected | Y− → X+ → Z− → X− | 0, 62, 64 | X+@59–62; X−@65–67; Z−@62–64 | REJECTED | Propagated X position range is empty at state 65. | A067 |
| A117 | rejected | Y− → X+ → Z− → X− | 0, 62, 65 | X+@59–62; X−@66–67; Z−@62–65 | REJECTED | Propagated X position range is empty at state 66. | A068 |
| A118 | rejected | Z− → X− → Z+ | 61, 67 | X−@62–67; Z+@67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A119 | rejected | Z− → X− → Z+ | 61, 66 | X−@62–66; Z+@66–67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A120 | rejected | Z− → X− → Z+ | 61, 65 | X−@62–65; Z+@65–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 65. | — |
| A121 | rejected | Z− → X− → Z+ | 61, 64 | X−@62–64; Z+@64–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 64. | — |
| A122 | rejected | Z− → X− → Z+ | 61, 63 | X−@62–63; Z+@63–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 63. | — |
| A123 | rejected | Z− → X− → Z+ | 61, 62 | X−@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | — |
| A124 | rejected | Z− → X− → Z+ | 62, 67 | X−@63–67; Z+@67; Z−@58–62 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A125 | rejected | Z− → X− → Z+ | 62, 66 | X−@63–66; Z+@66–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 66. | — |
| A126 | rejected | Z− → X− → Z+ | 62, 65 | X−@63–65; Z+@65–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 65. | — |
| A127 | rejected | Z− → X− → Z+ | 62, 64 | X−@63–64; Z+@64–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 64. | — |
| A128 | rejected | Z− → X− → Z+ | 62, 63 | X−@63; Z+@63–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 63. | — |
| A129 | retained | Z− → X− → Z+ | 60, 67 | X−@61–67; Z+@67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A130 | rejected | Z− → X− → Z+ | 60, 66 | X−@61–66; Z+@66–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A131 | rejected | Z− → X− → Z+ | 60, 65 | X−@61–65; Z+@65–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A132 | rejected | Z− → X− → Z+ | 60, 64 | X−@61–64; Z+@64–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 64. | — |
| A133 | rejected | Z− → X− → Z+ | 60, 63 | X−@61–63; Z+@63–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 63. | — |
| A134 | rejected | Z− → X− → Z+ | 60, 62 | X−@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | — |
| A135 | rejected | Z− → X− → Z+ | 63, 67 | X−@64–67; Z+@67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 67. | — |
| A136 | rejected | Z− → X− → Z+ | 63, 66 | X−@64–66; Z+@66–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 66. | — |
| A137 | rejected | Z− → X− → Z+ | 63, 65 | X−@64–65; Z+@65–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 65. | — |
| A138 | rejected | Z− → X− → Z+ | 63, 64 | X−@64; Z+@64–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 64. | — |
| A139 | rejected | Z− → X− → Z+ | 59, 67 | X−@60–67; Z+@67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A140 | rejected | Z− → X− → Z+ | 59, 66 | X−@60–66; Z+@66–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A141 | rejected | Z− → X− → Z+ | 59, 65 | X−@60–65; Z+@65–67; Z−@58–59 | REJECTED | States 0→60: minimum travel 19.117420194 exceeds upper bound 19.071255401 blocks. | — |
| A142 | rejected | Z− → X− → Z+ | 59, 64 | X−@60–64; Z+@64–67; Z−@58–59 | REJECTED | States 0→59: minimum travel 18.799488392 exceeds upper bound 18.721285914 blocks. | — |
| A143 | rejected | Z− → X− → Z+ | 59, 63 | X−@60–63; Z+@63–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 63. | — |
| A144 | rejected | Z− → X− → Z+ | 59, 62 | X−@60–62; Z+@62–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 62. | — |
| A145 | rejected | Z− → X− → Z+ | 64, 67 | X−@65–67; Z+@67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 67. | — |
| A146 | rejected | Z− → X− → Z+ | 64, 66 | X−@65–66; Z+@66–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 66. | — |
| A147 | rejected | Z− → X− → Z+ | 64, 65 | X−@65; Z+@65–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 65. | — |
| A148 | rejected | Z− → X− → Z+ | 0, 67 | X−@59–67; Z+@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A149 | rejected | Z− → X− → Z+ | 0, 66 | X−@59–66; Z+@66–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A150 | rejected | Z− → X− → Z+ | 0, 65 | X−@59–65; Z+@65–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A151 | rejected | Z− → X− → Z+ | 0, 64 | X−@59–64; Z+@64–67 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | — |
| A152 | rejected | Z− → X− → Z+ | 0, 63 | X−@59–63; Z+@63–67 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | — |
| A153 | rejected | Z− → X− → Z+ | 0, 62 | X−@59–62; Z+@62–67 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A154 | rejected | Z− → X− → Z+ | 58, 67 | X−@59–67; Z+@67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A155 | rejected | Z− → X− → Z+ | 58, 66 | X−@59–66; Z+@66–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A156 | rejected | Z− → X− → Z+ | 58, 65 | X−@59–65; Z+@65–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A157 | rejected | Z− → X− → Z+ | 58, 64 | X−@59–64; Z+@64–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | — |
| A158 | rejected | Z− → X− → Z+ | 58, 63 | X−@59–63; Z+@63–67; Z−@58 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | — |
| A159 | rejected | Z− → X− → Z+ | 58, 62 | X−@59–62; Z+@62–67; Z−@58 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A160 | rejected | Z− → X− → Z+ | 65, 67 | X−@66–67; Z+@67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 67. | — |
| A161 | rejected | Z− → X− → Z+ | 65, 66 | X−@66; Z+@66–67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 66. | — |
| A162 | rejected | Y− → Z− → X− → Z+ | 0, 61, 67 | X−@62–67; Z+@67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A118 |
| A163 | rejected | Y− → Z− → X− → Z+ | 0, 61, 66 | X−@62–66; Z+@66–67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A119 |
| A164 | rejected | Y− → Z− → X− → Z+ | 0, 61, 65 | X−@62–65; Z+@65–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 65. | A120 |
| A165 | rejected | Y− → Z− → X− → Z+ | 0, 61, 64 | X−@62–64; Z+@64–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 64. | A121 |
| A166 | rejected | Y− → Z− → X− → Z+ | 0, 61, 63 | X−@62–63; Z+@63–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 63. | A122 |
| A167 | rejected | Y− → Z− → X− → Z+ | 0, 61, 62 | X−@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | A123 |
| A168 | rejected | Y− → Z− → X− → Z+ | 0, 62, 67 | X−@63–67; Z+@67; Z−@58–62 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A124 |
| A169 | rejected | Y− → Z− → X− → Z+ | 0, 62, 66 | X−@63–66; Z+@66–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 66. | A125 |
| A170 | rejected | Y− → Z− → X− → Z+ | 0, 62, 65 | X−@63–65; Z+@65–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 65. | A126 |
| A171 | rejected | Y− → Z− → X− → Z+ | 0, 62, 64 | X−@63–64; Z+@64–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 64. | A127 |
| A172 | rejected | Y− → Z− → X− → Z+ | 0, 62, 63 | X−@63; Z+@63–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 63. | A128 |
| A173 | retained | Y− → Z− → X− → Z+ | 0, 60, 67 | X−@61–67; Z+@67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | A129 |
| A174 | rejected | Y− → Z− → X− → Z+ | 0, 60, 66 | X−@61–66; Z+@66–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A130 |
| A175 | rejected | Y− → Z− → X− → Z+ | 0, 60, 65 | X−@61–65; Z+@65–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A131 |
| A176 | rejected | Y− → Z− → X− → Z+ | 0, 60, 64 | X−@61–64; Z+@64–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 64. | A132 |
| A177 | rejected | Y− → Z− → X− → Z+ | 0, 60, 63 | X−@61–63; Z+@63–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 63. | A133 |
| A178 | rejected | Y− → Z− → X− → Z+ | 0, 60, 62 | X−@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | A134 |
| A179 | rejected | Y− → Z− → X− → Z+ | 0, 63, 67 | X−@64–67; Z+@67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 67. | A135 |
| A180 | rejected | Y− → Z− → X− → Z+ | 0, 63, 66 | X−@64–66; Z+@66–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 66. | A136 |
| A181 | rejected | Y− → Z− → X− → Z+ | 0, 63, 65 | X−@64–65; Z+@65–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 65. | A137 |
| A182 | rejected | Y− → Z− → X− → Z+ | 0, 63, 64 | X−@64; Z+@64–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 64. | A138 |
| A183 | rejected | Y− → Z− → X− → Z+ | 0, 59, 67 | X−@60–67; Z+@67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A139 |
| A184 | rejected | Y− → Z− → X− → Z+ | 0, 59, 66 | X−@60–66; Z+@66–67; Z−@58–59 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A140 |
| A185 | rejected | Y− → Z− → X− → Z+ | 0, 59, 65 | X−@60–65; Z+@65–67; Z−@58–59 | REJECTED | States 0→60: minimum travel 19.117420194 exceeds upper bound 19.071255401 blocks. | A141 |
| A186 | rejected | Y− → Z− → X− → Z+ | 0, 59, 64 | X−@60–64; Z+@64–67; Z−@58–59 | REJECTED | States 0→59: minimum travel 18.799488392 exceeds upper bound 18.721285914 blocks. | A142 |
| A187 | rejected | Y− → Z− → X− → Z+ | 0, 59, 63 | X−@60–63; Z+@63–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 63. | A143 |
| A188 | rejected | Y− → Z− → X− → Z+ | 0, 59, 62 | X−@60–62; Z+@62–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 62. | A144 |
| A189 | rejected | Y− → Z− → X− → Z+ | 0, 64, 67 | X−@65–67; Z+@67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 67. | A145 |
| A190 | rejected | Y− → Z− → X− → Z+ | 0, 64, 66 | X−@65–66; Z+@66–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 66. | A146 |
| A191 | rejected | Y− → Z− → X− → Z+ | 0, 64, 65 | X−@65; Z+@65–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 65. | A147 |
| A192 | rejected | Y− → Z− → X− → Z+ | 0, 0, 67 | X−@59–67; Z+@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A148 |
| A193 | rejected | Y− → Z− → X− → Z+ | 0, 0, 66 | X−@59–66; Z+@66–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A149 |
| A194 | rejected | Y− → Z− → X− → Z+ | 0, 0, 65 | X−@59–65; Z+@65–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A150 |
| A195 | rejected | Y− → Z− → X− → Z+ | 0, 0, 64 | X−@59–64; Z+@64–67 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | A151 |
| A196 | rejected | Y− → Z− → X− → Z+ | 0, 0, 63 | X−@59–63; Z+@63–67 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | A152 |
| A197 | rejected | Y− → Z− → X− → Z+ | 0, 0, 62 | X−@59–62; Z+@62–67 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | A153 |
| A198 | rejected | Y− → Z− → X− → Z+ | 0, 58, 67 | X−@59–67; Z+@67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A154 |
| A199 | rejected | Y− → Z− → X− → Z+ | 0, 58, 66 | X−@59–66; Z+@66–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A155 |
| A200 | rejected | Y− → Z− → X− → Z+ | 0, 58, 65 | X−@59–65; Z+@65–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A156 |
| A201 | rejected | Y− → Z− → X− → Z+ | 0, 58, 64 | X−@59–64; Z+@64–67; Z−@58 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | A157 |
| A202 | rejected | Y− → Z− → X− → Z+ | 0, 58, 63 | X−@59–63; Z+@63–67; Z−@58 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | A158 |
| A203 | rejected | Y− → Z− → X− → Z+ | 0, 58, 62 | X−@59–62; Z+@62–67; Z−@58 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | A159 |
| A204 | rejected | Y− → Z− → X− → Z+ | 0, 65, 67 | X−@66–67; Z+@67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 67. | A160 |
| A205 | rejected | Y− → Z− → X− → Z+ | 0, 65, 66 | X−@66; Z+@66–67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 66. | A161 |
| A206 | rejected | X− | — | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A207 | rejected | Y− → X− | 0 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A208 | rejected | X− → Y− → X− | 0, 1 | X−@59–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A007 |
| A209 | rejected | X− → Z+ → X− | 66, 66 | X−@59–67; Z+@66 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A210 | rejected | X− → Z+ → X− | 65, 65 | X−@59–67; Z+@65 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A211 | rejected | X− → Z+ → X− | 65, 66 | X−@59–65,67; Z+@65–66 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A212 | rejected | X− → Z+ → X− | 64, 64 | X−@59–67; Z+@64 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | — |
| A213 | rejected | X− → Z+ → X− | 64, 65 | X−@59–64,66–67; Z+@64–65 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | — |
| A214 | rejected | X− → Z+ → X− | 64, 66 | X−@59–64,67; Z+@64–66 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | — |
| A215 | rejected | X− → Z+ → X− | 63, 63 | X−@59–67; Z+@63 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | — |
| A216 | rejected | X− → Z+ → X− | 63, 64 | X−@59–63,65–67; Z+@63–64 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | — |
| A217 | rejected | X− → Z+ → X− | 63, 65 | X−@59–63,66–67; Z+@63–65 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | — |
| A218 | rejected | X− → Z+ → X− | 63, 66 | X−@59–63,67; Z+@63–66 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | — |
| A219 | rejected | X− → Z+ → X− | 62, 62 | X−@59–67; Z+@62 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A220 | rejected | X− → Z+ → X− | 62, 63 | X−@59–62,64–67; Z+@62–63 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A221 | rejected | X− → Z+ → X− | 62, 64 | X−@59–62,65–67; Z+@62–64 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A222 | rejected | X− → Z+ → X− | 62, 65 | X−@59–62,66–67; Z+@62–65 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A223 | rejected | X− → Z+ → X− | 62, 66 | X−@59–62,67; Z+@62–66 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | — |
| A224 | rejected | X+ → Z+ → X− | 62, 62 | X+@59–62; X−@63–67; Z+@62 | REJECTED | Propagated X position range is empty at state 63. | — |
| A225 | rejected | X+ → Z+ → X− | 62, 63 | X+@59–62; X−@64–67; Z+@62–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| A226 | rejected | X+ → Z+ → X− | 62, 64 | X+@59–62; X−@65–67; Z+@62–64 | REJECTED | Propagated X position range is empty at state 65. | — |
| A227 | rejected | X+ → Z+ → X− | 62, 65 | X+@59–62; X−@66–67; Z+@62–65 | REJECTED | Propagated X position range is empty at state 66. | — |
| A228 | retained | X+ → Z+ → X− | 62, 66 | X+@59–62; X−@67; Z+@62–66 | RETAINED | Linear check inconclusive (status [4]); retained, not proved feasible. | — |
| A229 | rejected | Y− → X+ → Z+ → X− | 0, 62, 62 | X+@59–62; X−@63–67; Z+@62 | REJECTED | Propagated X position range is empty at state 63. | A224 |
| A230 | rejected | Y− → X+ → Z+ → X− | 0, 62, 63 | X+@59–62; X−@64–67; Z+@62–63 | REJECTED | Propagated X position range is empty at state 64. | A225 |
| A231 | rejected | Y− → X+ → Z+ → X− | 0, 62, 64 | X+@59–62; X−@65–67; Z+@62–64 | REJECTED | Propagated X position range is empty at state 65. | A226 |
| A232 | rejected | Y− → X+ → Z+ → X− | 0, 62, 65 | X+@59–62; X−@66–67; Z+@62–65 | REJECTED | Propagated X position range is empty at state 66. | A227 |
| A233 | retained | Y− → X+ → Z+ → X− | 0, 62, 66 | X+@59–62; X−@67; Z+@62–66 | RETAINED | Linear check inconclusive (status [4]); retained, not proved feasible. | A228 |
| A234 | rejected | X− → Z+ | 67 | X−@59–67; Z+@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A148 |
| A235 | rejected | X− → Z+ | 66 | X−@59–66; Z+@66–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A149 |
| A236 | rejected | X− → Z+ | 65 | X−@59–65; Z+@65–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A150 |
| A237 | rejected | X− → Z+ | 64 | X−@59–64; Z+@64–67 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | A151 |
| A238 | rejected | X− → Z+ | 63 | X−@59–63; Z+@63–67 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | A152 |
| A239 | rejected | X− → Z+ | 62 | X−@59–62; Z+@62–67 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | A153 |
| A240 | rejected | Y− → X− → Z+ | 0, 67 | X−@59–67; Z+@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A148 |
| A241 | rejected | Y− → X− → Z+ | 0, 66 | X−@59–66; Z+@66–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A149 |
| A242 | rejected | Y− → X− → Z+ | 0, 65 | X−@59–65; Z+@65–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A150 |
| A243 | rejected | Y− → X− → Z+ | 0, 64 | X−@59–64; Z+@64–67 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | A151 |
| A244 | rejected | Y− → X− → Z+ | 0, 63 | X−@59–63; Z+@63–67 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | A152 |
| A245 | rejected | Y− → X− → Z+ | 0, 62 | X−@59–62; Z+@62–67 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | A153 |
| A246 | rejected | X+ → Y− → X− → Z+ | 0, 1, 67 | X−@59–67; Z+@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A148 |
| A247 | rejected | X+ → Y− → X− → Z+ | 0, 1, 66 | X−@59–66; Z+@66–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A149 |
| A248 | rejected | X+ → Y− → X− → Z+ | 0, 1, 65 | X−@59–65; Z+@65–67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A150 |
| A249 | rejected | X+ → Y− → X− → Z+ | 0, 1, 64 | X−@59–64; Z+@64–67 | REJECTED | States 0→57: minimum travel 17.909518674 exceeds upper bound 17.835681856 blocks. | A151 |
| A250 | rejected | X+ → Y− → X− → Z+ | 0, 1, 63 | X−@59–63; Z+@63–67 | REJECTED | States 0→55: minimum travel 17.272405830 exceeds upper bound 17.250143865 blocks. | A152 |
| A251 | rejected | X+ → Y− → X− → Z+ | 0, 1, 62 | X−@59–62; Z+@62–67 | REJECTED | States 0→54: minimum travel 17.032085466 exceeds upper bound 16.956790234 blocks. | A153 |
| A252 | retained | Z− → X− → Z− | 61, 67 | X−@62–67; Z−@58–61,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A253 | retained | Z− → X− → Z− | 62, 67 | X−@63–67; Z−@58–62,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A254 | retained | Z− → X− → Z− | 60, 67 | X−@61–67; Z−@58–60,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A255 | retained | Z− → X− → Z− | 63, 67 | X−@64–67; Z−@58–63,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A256 | not reached | Z− → X− → Z− | 59, 67 | X−@60–67; Z−@58–59,67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A257 | not reached | Z− → X− → Z− | 64, 67 | X−@65–67; Z−@58–64,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A258 | not reached | Z− → X− → Z− | 0, 67 | X−@59–67; Z−@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A259 | not reached | Z− → X− → Z− | 58, 67 | X−@59–67; Z−@58,67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | — |
| A260 | not reached | Z− → X− → Z− | 65, 67 | X−@66–67; Z−@58–65,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| A261 | not reached | X− → Z− | 67 | X−@59–67; Z−@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A258 |
| A262 | not reached | Y− → X− → Z− | 0, 67 | X−@59–67; Z−@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A258 |
| A263 | not reached | X+ → Y− → X− → Z− | 0, 1, 67 | X−@59–67; Z−@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A258 |
| A264 | not reached | Y− → Z+ → X− → Z− | 0, 0, 67 | X−@59–67; Z−@67 | REJECTED | States 0→57: minimum travel 17.902738503 exceeds upper bound 17.835681856 blocks. | A258 |
| A265 | not reached | Z− → X+ → Z+ | 61, 62 | X+@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | — |
| A266 | not reached | Z− → X+ → Z+ | 60, 62 | X+@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | — |
| A267 | not reached | Z− → X+ → Z+ | 59, 62 | X+@60–62; Z+@62–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 62. | — |
| A268 | not reached | Z− → X+ → Z+ | 0, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A269 | not reached | Z− → X+ → Z+ | 58, 62 | X+@59–62; Z+@62–67; Z−@58 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| A270 | not reached | Y− → Z− → X+ → Z+ | 0, 61, 62 | X+@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | A265 |
| A271 | not reached | Y− → Z− → X+ → Z+ | 0, 60, 62 | X+@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | A266 |
| A272 | not reached | Y− → Z− → X+ → Z+ | 0, 59, 62 | X+@60–62; Z+@62–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 62. | A267 |
| A273 | not reached | Y− → Z− → X+ → Z+ | 0, 0, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A268 |
| A274 | not reached | Y− → Z− → X+ → Z+ | 0, 58, 62 | X+@59–62; Z+@62–67; Z−@58 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A269 |
| A275 | not reached | X+ → Z+ | 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A268 |
| A276 | not reached | Y− → X+ → Z+ | 0, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A268 |
| A277 | not reached | X− → Y− → X+ → Z+ | 0, 1, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | A268 |

## History B

Context: `f99ad745d68b30414773d20fd43eb5656d55bcb6d220bfe0eacd87672297671e`.
Horizon: **67**. Jump inputs: **[8, 19, 30, 43, 58]**.
This history includes the additional earlier approach constraint `X ≥ 731.3000010119209` at state 56.

| ID | Recorded 300s run | Face sequence | Switches | Active added requirements | Fresh result | Reason | Same rows |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B001 | retained | Z− → X− | 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B002 | retained | Z− → X− | 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B003 | retained | Z− → X− | 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B004 | retained | Z− → X− | 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B005 | retained | Z− → X− | 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B006 | retained | Z− → X− | 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B007 | retained | X− → Z− → X− | 0, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B001 |
| B008 | retained | X− → Z− → X− | 0, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B002 |
| B009 | retained | X− → Z− → X− | 0, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B003 |
| B010 | retained | X− → Z− → X− | 0, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B004 |
| B011 | retained | X− → Z− → X− | 0, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B005 |
| B012 | retained | X− → Z− → X− | 0, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B006 |
| B013 | rejected | X+ → Z− → X− | 59, 61 | X+@59; X−@62–67; Z−@59–61 | REJECTED | Propagated X position range is empty at state 62. | — |
| B014 | rejected | X+ → Z− → X− | 59, 62 | X+@59; X−@63–67; Z−@59–62 | REJECTED | Propagated X position range is empty at state 63. | — |
| B015 | rejected | X+ → Z− → X− | 59, 60 | X+@59; X−@61–67; Z−@59–60 | REJECTED | Propagated X position range is empty at state 61. | — |
| B016 | rejected | X+ → Z− → X− | 59, 63 | X+@59; X−@64–67; Z−@59–63 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B017 | rejected | X+ → Z− → X− | 59, 64 | X+@59; X−@65–67; Z−@59–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B018 | rejected | X+ → Z− → X− | 59, 65 | X+@59; X−@66–67; Z−@59–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B019 | rejected | X+ → Z− → X− | 60, 61 | X+@59–60; X−@62–67; Z−@60–61 | REJECTED | Propagated X position range is empty at state 62. | — |
| B020 | rejected | X+ → Z− → X− | 60, 62 | X+@59–60; X−@63–67; Z−@60–62 | REJECTED | Propagated X position range is empty at state 63. | — |
| B021 | rejected | X+ → Z− → X− | 60, 60 | X+@59–60; X−@61–67; Z−@60 | REJECTED | Propagated X position range is empty at state 61. | — |
| B022 | rejected | X+ → Z− → X− | 60, 63 | X+@59–60; X−@64–67; Z−@60–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| B023 | rejected | X+ → Z− → X− | 60, 64 | X+@59–60; X−@65–67; Z−@60–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B024 | rejected | X+ → Z− → X− | 60, 65 | X+@59–60; X−@66–67; Z−@60–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B025 | rejected | X+ → Z− → X− | 61, 61 | X+@59–61; X−@62–67; Z−@61 | REJECTED | Propagated X position range is empty at state 62. | — |
| B026 | rejected | X+ → Z− → X− | 61, 62 | X+@59–61; X−@63–67; Z−@61–62 | REJECTED | Propagated X position range is empty at state 63. | — |
| B027 | rejected | X+ → Z− → X− | 61, 63 | X+@59–61; X−@64–67; Z−@61–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| B028 | rejected | X+ → Z− → X− | 61, 64 | X+@59–61; X−@65–67; Z−@61–64 | REJECTED | Propagated X position range is empty at state 65. | — |
| B029 | rejected | X+ → Z− → X− | 61, 65 | X+@59–61; X−@66–67; Z−@61–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B030 | rejected | X+ → Z− → X− | 62, 62 | X+@59–62; X−@63–67; Z−@62 | REJECTED | Propagated X position range is empty at state 63. | — |
| B031 | rejected | X+ → Z− → X− | 62, 63 | X+@59–62; X−@64–67; Z−@62–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| B032 | rejected | X+ → Z− → X− | 62, 64 | X+@59–62; X−@65–67; Z−@62–64 | REJECTED | Propagated X position range is empty at state 65. | — |
| B033 | rejected | X+ → Z− → X− | 62, 65 | X+@59–62; X−@66–67; Z−@62–65 | REJECTED | Propagated X position range is empty at state 66. | — |
| B034 | retained | Y− → Z− → X− | 0, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B001 |
| B035 | retained | Y− → Z− → X− | 0, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B002 |
| B036 | retained | Y− → Z− → X− | 0, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B003 |
| B037 | retained | Y− → Z− → X− | 0, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B004 |
| B038 | retained | Y− → Z− → X− | 0, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B005 |
| B039 | retained | Y− → Z− → X− | 0, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B006 |
| B040 | not reached | X− → Y− → Z− → X− | 0, 1, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B001 |
| B041 | not reached | X− → Y− → Z− → X− | 0, 1, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B002 |
| B042 | not reached | X− → Y− → Z− → X− | 0, 1, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B003 |
| B043 | not reached | X− → Y− → Z− → X− | 0, 1, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B004 |
| B044 | not reached | X− → Y− → Z− → X− | 0, 1, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B005 |
| B045 | not reached | X− → Y− → Z− → X− | 0, 1, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B006 |
| B046 | not reached | Y− → X+ → Z− → X− | 0, 0, 61 | X−@62–67; Z−@58–61 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B001 |
| B047 | not reached | Y− → X+ → Z− → X− | 0, 0, 62 | X−@63–67; Z−@58–62 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B002 |
| B048 | not reached | Y− → X+ → Z− → X− | 0, 0, 60 | X−@61–67; Z−@58–60 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B003 |
| B049 | not reached | Y− → X+ → Z− → X− | 0, 0, 63 | X−@64–67; Z−@58–63 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B004 |
| B050 | not reached | Y− → X+ → Z− → X− | 0, 0, 64 | X−@65–67; Z−@58–64 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B005 |
| B051 | not reached | Y− → X+ → Z− → X− | 0, 0, 65 | X−@66–67; Z−@58–65 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | B006 |
| B052 | not reached | Y− → X+ → Z− → X− | 0, 59, 61 | X+@59; X−@62–67; Z−@59–61 | REJECTED | Propagated X position range is empty at state 62. | B013 |
| B053 | not reached | Y− → X+ → Z− → X− | 0, 59, 62 | X+@59; X−@63–67; Z−@59–62 | REJECTED | Propagated X position range is empty at state 63. | B014 |
| B054 | not reached | Y− → X+ → Z− → X− | 0, 59, 60 | X+@59; X−@61–67; Z−@59–60 | REJECTED | Propagated X position range is empty at state 61. | B015 |
| B055 | not reached | Y− → X+ → Z− → X− | 0, 59, 63 | X+@59; X−@64–67; Z−@59–63 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B016 |
| B056 | not reached | Y− → X+ → Z− → X− | 0, 59, 64 | X+@59; X−@65–67; Z−@59–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B017 |
| B057 | not reached | Y− → X+ → Z− → X− | 0, 59, 65 | X+@59; X−@66–67; Z−@59–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B018 |
| B058 | not reached | Y− → X+ → Z− → X− | 0, 60, 61 | X+@59–60; X−@62–67; Z−@60–61 | REJECTED | Propagated X position range is empty at state 62. | B019 |
| B059 | not reached | Y− → X+ → Z− → X− | 0, 60, 62 | X+@59–60; X−@63–67; Z−@60–62 | REJECTED | Propagated X position range is empty at state 63. | B020 |
| B060 | not reached | Y− → X+ → Z− → X− | 0, 60, 60 | X+@59–60; X−@61–67; Z−@60 | REJECTED | Propagated X position range is empty at state 61. | B021 |
| B061 | not reached | Y− → X+ → Z− → X− | 0, 60, 63 | X+@59–60; X−@64–67; Z−@60–63 | REJECTED | Propagated X position range is empty at state 64. | B022 |
| B062 | not reached | Y− → X+ → Z− → X− | 0, 60, 64 | X+@59–60; X−@65–67; Z−@60–64 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B023 |
| B063 | not reached | Y− → X+ → Z− → X− | 0, 60, 65 | X+@59–60; X−@66–67; Z−@60–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B024 |
| B064 | not reached | Y− → X+ → Z− → X− | 0, 61, 61 | X+@59–61; X−@62–67; Z−@61 | REJECTED | Propagated X position range is empty at state 62. | B025 |
| B065 | not reached | Y− → X+ → Z− → X− | 0, 61, 62 | X+@59–61; X−@63–67; Z−@61–62 | REJECTED | Propagated X position range is empty at state 63. | B026 |
| B066 | not reached | Y− → X+ → Z− → X− | 0, 61, 63 | X+@59–61; X−@64–67; Z−@61–63 | REJECTED | Propagated X position range is empty at state 64. | B027 |
| B067 | not reached | Y− → X+ → Z− → X− | 0, 61, 64 | X+@59–61; X−@65–67; Z−@61–64 | REJECTED | Propagated X position range is empty at state 65. | B028 |
| B068 | not reached | Y− → X+ → Z− → X− | 0, 61, 65 | X+@59–61; X−@66–67; Z−@61–65 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B029 |
| B069 | not reached | Y− → X+ → Z− → X− | 0, 62, 62 | X+@59–62; X−@63–67; Z−@62 | REJECTED | Propagated X position range is empty at state 63. | B030 |
| B070 | not reached | Y− → X+ → Z− → X− | 0, 62, 63 | X+@59–62; X−@64–67; Z−@62–63 | REJECTED | Propagated X position range is empty at state 64. | B031 |
| B071 | not reached | Y− → X+ → Z− → X− | 0, 62, 64 | X+@59–62; X−@65–67; Z−@62–64 | REJECTED | Propagated X position range is empty at state 65. | B032 |
| B072 | not reached | Y− → X+ → Z− → X− | 0, 62, 65 | X+@59–62; X−@66–67; Z−@62–65 | REJECTED | Propagated X position range is empty at state 66. | B033 |
| B073 | not reached | Z− → X− → Z+ | 61, 67 | X−@62–67; Z+@67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B074 | not reached | Z− → X− → Z+ | 61, 66 | X−@62–66; Z+@66–67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B075 | not reached | Z− → X− → Z+ | 61, 65 | X−@62–65; Z+@65–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 65. | — |
| B076 | not reached | Z− → X− → Z+ | 61, 64 | X−@62–64; Z+@64–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 64. | — |
| B077 | not reached | Z− → X− → Z+ | 61, 63 | X−@62–63; Z+@63–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 63. | — |
| B078 | not reached | Z− → X− → Z+ | 61, 62 | X−@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | — |
| B079 | not reached | Z− → X− → Z+ | 62, 67 | X−@63–67; Z+@67; Z−@58–62 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B080 | not reached | Z− → X− → Z+ | 62, 66 | X−@63–66; Z+@66–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 66. | — |
| B081 | not reached | Z− → X− → Z+ | 62, 65 | X−@63–65; Z+@65–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 65. | — |
| B082 | not reached | Z− → X− → Z+ | 62, 64 | X−@63–64; Z+@64–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 64. | — |
| B083 | not reached | Z− → X− → Z+ | 62, 63 | X−@63; Z+@63–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 63. | — |
| B084 | not reached | Z− → X− → Z+ | 60, 67 | X−@61–67; Z+@67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B085 | not reached | Z− → X− → Z+ | 60, 66 | X−@61–66; Z+@66–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B086 | not reached | Z− → X− → Z+ | 60, 65 | X−@61–65; Z+@65–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B087 | not reached | Z− → X− → Z+ | 60, 64 | X−@61–64; Z+@64–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 64. | — |
| B088 | not reached | Z− → X− → Z+ | 60, 63 | X−@61–63; Z+@63–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 63. | — |
| B089 | not reached | Z− → X− → Z+ | 60, 62 | X−@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | — |
| B090 | not reached | Z− → X− → Z+ | 63, 67 | X−@64–67; Z+@67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 67. | — |
| B091 | not reached | Z− → X− → Z+ | 63, 66 | X−@64–66; Z+@66–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 66. | — |
| B092 | not reached | Z− → X− → Z+ | 63, 65 | X−@64–65; Z+@65–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 65. | — |
| B093 | not reached | Z− → X− → Z+ | 63, 64 | X−@64; Z+@64–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 64. | — |
| B094 | not reached | Z− → X− → Z+ | 64, 67 | X−@65–67; Z+@67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 67. | — |
| B095 | not reached | Z− → X− → Z+ | 64, 66 | X−@65–66; Z+@66–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 66. | — |
| B096 | not reached | Z− → X− → Z+ | 64, 65 | X−@65; Z+@65–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 65. | — |
| B097 | not reached | Z− → X− → Z+ | 65, 67 | X−@66–67; Z+@67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 67. | — |
| B098 | not reached | Z− → X− → Z+ | 65, 66 | X−@66; Z+@66–67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 66. | — |
| B099 | not reached | Y− → Z− → X− → Z+ | 0, 61, 67 | X−@62–67; Z+@67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B073 |
| B100 | not reached | Y− → Z− → X− → Z+ | 0, 61, 66 | X−@62–66; Z+@66–67; Z−@58–61 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B074 |
| B101 | not reached | Y− → Z− → X− → Z+ | 0, 61, 65 | X−@62–65; Z+@65–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 65. | B075 |
| B102 | not reached | Y− → Z− → X− → Z+ | 0, 61, 64 | X−@62–64; Z+@64–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 64. | B076 |
| B103 | not reached | Y− → Z− → X− → Z+ | 0, 61, 63 | X−@62–63; Z+@63–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 63. | B077 |
| B104 | not reached | Y− → Z− → X− → Z+ | 0, 61, 62 | X−@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | B078 |
| B105 | not reached | Y− → Z− → X− → Z+ | 0, 62, 67 | X−@63–67; Z+@67; Z−@58–62 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B079 |
| B106 | not reached | Y− → Z− → X− → Z+ | 0, 62, 66 | X−@63–66; Z+@66–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 66. | B080 |
| B107 | not reached | Y− → Z− → X− → Z+ | 0, 62, 65 | X−@63–65; Z+@65–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 65. | B081 |
| B108 | not reached | Y− → Z− → X− → Z+ | 0, 62, 64 | X−@63–64; Z+@64–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 64. | B082 |
| B109 | not reached | Y− → Z− → X− → Z+ | 0, 62, 63 | X−@63; Z+@63–67; Z−@58–62 | REJECTED | Propagated Z position range is empty at state 63. | B083 |
| B110 | not reached | Y− → Z− → X− → Z+ | 0, 60, 67 | X−@61–67; Z+@67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B084 |
| B111 | not reached | Y− → Z− → X− → Z+ | 0, 60, 66 | X−@61–66; Z+@66–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B085 |
| B112 | not reached | Y− → Z− → X− → Z+ | 0, 60, 65 | X−@61–65; Z+@65–67; Z−@58–60 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B086 |
| B113 | not reached | Y− → Z− → X− → Z+ | 0, 60, 64 | X−@61–64; Z+@64–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 64. | B087 |
| B114 | not reached | Y− → Z− → X− → Z+ | 0, 60, 63 | X−@61–63; Z+@63–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 63. | B088 |
| B115 | not reached | Y− → Z− → X− → Z+ | 0, 60, 62 | X−@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | B089 |
| B116 | not reached | Y− → Z− → X− → Z+ | 0, 63, 67 | X−@64–67; Z+@67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 67. | B090 |
| B117 | not reached | Y− → Z− → X− → Z+ | 0, 63, 66 | X−@64–66; Z+@66–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 66. | B091 |
| B118 | not reached | Y− → Z− → X− → Z+ | 0, 63, 65 | X−@64–65; Z+@65–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 65. | B092 |
| B119 | not reached | Y− → Z− → X− → Z+ | 0, 63, 64 | X−@64; Z+@64–67; Z−@58–63 | REJECTED | Propagated Z position range is empty at state 64. | B093 |
| B120 | not reached | Y− → Z− → X− → Z+ | 0, 64, 67 | X−@65–67; Z+@67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 67. | B094 |
| B121 | not reached | Y− → Z− → X− → Z+ | 0, 64, 66 | X−@65–66; Z+@66–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 66. | B095 |
| B122 | not reached | Y− → Z− → X− → Z+ | 0, 64, 65 | X−@65; Z+@65–67; Z−@58–64 | REJECTED | Propagated Z position range is empty at state 65. | B096 |
| B123 | not reached | Y− → Z− → X− → Z+ | 0, 65, 67 | X−@66–67; Z+@67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 67. | B097 |
| B124 | not reached | Y− → Z− → X− → Z+ | 0, 65, 66 | X−@66; Z+@66–67; Z−@58–65 | REJECTED | Propagated Z position range is empty at state 66. | B098 |
| B125 | not reached | Z− → X− → Z− | 61, 67 | X−@62–67; Z−@58–61,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B126 | not reached | Z− → X− → Z− | 62, 67 | X−@63–67; Z−@58–62,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B127 | not reached | Z− → X− → Z− | 60, 67 | X−@61–67; Z−@58–60,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B128 | not reached | Z− → X− → Z− | 63, 67 | X−@64–67; Z−@58–63,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B129 | not reached | Z− → X− → Z− | 64, 67 | X−@65–67; Z−@58–64,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B130 | not reached | Z− → X− → Z− | 65, 67 | X−@66–67; Z−@58–65,67 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| B131 | not reached | Z− → X+ → Z+ | 61, 62 | X+@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | — |
| B132 | not reached | Z− → X+ → Z+ | 60, 62 | X+@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | — |
| B133 | not reached | Z− → X+ → Z+ | 59, 62 | X+@60–62; Z+@62–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 62. | — |
| B134 | not reached | Z− → X+ → Z+ | 0, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B135 | not reached | Z− → X+ → Z+ | 58, 62 | X+@59–62; Z+@62–67; Z−@58 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B136 | not reached | Y− → Z− → X+ → Z+ | 0, 61, 62 | X+@62; Z+@62–67; Z−@58–61 | REJECTED | Propagated Z position range is empty at state 62. | B131 |
| B137 | not reached | Y− → Z− → X+ → Z+ | 0, 60, 62 | X+@61–62; Z+@62–67; Z−@58–60 | REJECTED | Propagated Z position range is empty at state 62. | B132 |
| B138 | not reached | Y− → Z− → X+ → Z+ | 0, 59, 62 | X+@60–62; Z+@62–67; Z−@58–59 | REJECTED | Propagated Z position range is empty at state 62. | B133 |
| B139 | not reached | Y− → Z− → X+ → Z+ | 0, 0, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B134 |
| B140 | not reached | Y− → Z− → X+ → Z+ | 0, 58, 62 | X+@59–62; Z+@62–67; Z−@58 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B135 |
| B141 | not reached | X+ → Z+ → X− | 62, 62 | X+@59–62; X−@63–67; Z+@62 | REJECTED | Propagated X position range is empty at state 63. | — |
| B142 | not reached | X+ → Z+ → X− | 62, 63 | X+@59–62; X−@64–67; Z+@62–63 | REJECTED | Propagated X position range is empty at state 64. | — |
| B143 | not reached | X+ → Z+ → X− | 62, 64 | X+@59–62; X−@65–67; Z+@62–64 | REJECTED | Propagated X position range is empty at state 65. | — |
| B144 | not reached | X+ → Z+ → X− | 62, 65 | X+@59–62; X−@66–67; Z+@62–65 | REJECTED | Propagated X position range is empty at state 66. | — |
| B145 | not reached | X+ → Z+ → X− | 62, 66 | X+@59–62; X−@67; Z+@62–66 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| B146 | not reached | Y− → X+ → Z+ → X− | 0, 62, 62 | X+@59–62; X−@63–67; Z+@62 | REJECTED | Propagated X position range is empty at state 63. | B141 |
| B147 | not reached | Y− → X+ → Z+ → X− | 0, 62, 63 | X+@59–62; X−@64–67; Z+@62–63 | REJECTED | Propagated X position range is empty at state 64. | B142 |
| B148 | not reached | Y− → X+ → Z+ → X− | 0, 62, 64 | X+@59–62; X−@65–67; Z+@62–64 | REJECTED | Propagated X position range is empty at state 65. | B143 |
| B149 | not reached | Y− → X+ → Z+ → X− | 0, 62, 65 | X+@59–62; X−@66–67; Z+@62–65 | REJECTED | Propagated X position range is empty at state 66. | B144 |
| B150 | not reached | Y− → X+ → Z+ → X− | 0, 62, 66 | X+@59–62; X−@67; Z+@62–66 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B145 |
| B151 | not reached | X+ → Z+ | 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B134 |
| B152 | not reached | Y− → X+ → Z+ | 0, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B134 |
| B153 | not reached | X− → Y− → X+ → Z+ | 0, 1, 62 | X+@59–62; Z+@62–67 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | B134 |

## History C

Context: `c2266d6f6c969622fddee2c4cf3dc3249ff5ddce92106ba0cf819b3e961e4717`.
Horizon: **57**. Jump inputs: **[8, 19, 30, 42]**.
The check also includes these previously selected required-contact rows: X GE 729.6999999880791 at state 56; X LE 731.3000000119209 at state 56; Z GE -1096.300000011921 at state 56; Z LE -1095.300000011921 at state 56.

| ID | Recorded 300s run | Face sequence | Switches | Active added requirements | Fresh result | Reason | Same rows |
| --- | --- | --- | --- | --- | --- | --- | --- |
| C001 | retained | Z− | — | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| C002 | retained | X− → Z− | 0 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C003 | retained | Y− → Z− | 0 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C004 | retained | X− → Y− → Z− | 0, 1 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C005 | retained | Y− → X− → Z− | 0, 0 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C006 | retained | Z− → X− → Z− | 0, 1 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C007 | retained | X− → Y− → X+ → Z− | 0, 1, 1 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C008 | retained | Y− → Z+ → X− → Z− | 0, 0, 1 | Z−@56–57 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | C001 |
| C009 | rejected | Z− → X− | 56 | X−@57; Z−@56 | REJECTED | States 0→48: minimum travel 15.271443390 exceeds upper bound 15.212912022 blocks. | — |
| C010 | rejected | X− → Z− → X− | 0, 56 | X−@57; Z−@56 | REJECTED | States 0→48: minimum travel 15.271443390 exceeds upper bound 15.212912022 blocks. | C009 |
| C011 | rejected | Y− → Z− → X− | 0, 56 | X−@57; Z−@56 | REJECTED | States 0→48: minimum travel 15.271443390 exceeds upper bound 15.212912022 blocks. | C009 |
| C012 | rejected | X− → Y− → Z− → X− | 0, 1, 56 | X−@57; Z−@56 | REJECTED | States 0→48: minimum travel 15.271443390 exceeds upper bound 15.212912022 blocks. | C009 |
| C013 | rejected | Y− → X+ → Z− → X− | 0, 0, 56 | X−@57; Z−@56 | REJECTED | States 0→48: minimum travel 15.271443390 exceeds upper bound 15.212912022 blocks. | C009 |
| C014 | rejected | Z− → X+ | 56 | X+@57; Z−@56 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| C015 | rejected | X− → Z− → X+ | 0, 56 | X+@57; Z−@56 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | C014 |
| C016 | rejected | Y− → Z− → X+ | 0, 56 | X+@57; Z−@56 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | C014 |
| C017 | rejected | X− → Y− → Z− → X+ | 0, 1, 56 | X+@57; Z−@56 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | C014 |
| C018 | rejected | Y− → X− → Z− → X+ | 0, 0, 56 | X+@57; Z−@56 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | C014 |

## History D

Context: `4173d6ecd570ee8d6cb1704dc26137cfc6c2628b8ff0c5b274657bcfba540661`.
Horizon: **58**. Jump inputs: **[8, 19, 30, 43]**.
The check also includes these previously selected required-contact rows: X GE 729.6999999880791 at state 57; X LE 731.3000000119209 at state 57; Z GE -1096.300000011921 at state 57; Z LE -1095.300000011921 at state 57.

| ID | Recorded 300s run | Face sequence | Switches | Active added requirements | Fresh result | Reason | Same rows |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D001 | retained | Z− | — | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| D002 | retained | X− → Z− | 0 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D003 | rejected | X− → Z− | 56 | X−@56; Z−@56–58 | REJECTED | States 0→39: minimum travel 12.125574097 exceeds upper bound 12.118046411 blocks. | — |
| D004 | retained | X+ → Z− | 56 | X+@56; Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | — |
| D005 | not reached | Y− → Z− | 0 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D006 | not reached | X− → Y− → Z− | 0, 1 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D007 | not reached | Y− → X− → Z− | 0, 0 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D008 | not reached | Y− → X− → Z− | 0, 56 | X−@56; Z−@56–58 | REJECTED | States 0→39: minimum travel 12.125574097 exceeds upper bound 12.118046411 blocks. | D003 |
| D009 | not reached | Y− → X+ → Z− | 0, 56 | X+@56; Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D004 |
| D010 | not reached | Z− → X− → Z− | 0, 1 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D011 | not reached | Z− → X− → Z− | 0, 56 | X−@56; Z−@56–58 | REJECTED | States 0→39: minimum travel 12.125574097 exceeds upper bound 12.118046411 blocks. | D003 |
| D012 | not reached | Z− → X+ → Z− | 0, 56 | X+@56; Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D004 |
| D013 | not reached | X− → Y− → X+ → Z− | 0, 1, 1 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D014 | not reached | X− → Y− → X+ → Z− | 0, 1, 56 | X+@56; Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D004 |
| D015 | not reached | X+ → Y− → X− → Z− | 0, 1, 56 | X−@56; Z−@56–58 | REJECTED | States 0→39: minimum travel 12.125574097 exceeds upper bound 12.118046411 blocks. | D003 |
| D016 | not reached | Y− → Z+ → X− → Z− | 0, 0, 1 | Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D001 |
| D017 | not reached | Y− → Z+ → X− → Z− | 0, 0, 56 | X−@56; Z−@56–58 | REJECTED | States 0→39: minimum travel 12.125574097 exceeds upper bound 12.118046411 blocks. | D003 |
| D018 | not reached | Y− → Z+ → X+ → Z− | 0, 0, 56 | X+@56; Z−@56–58 | RETAINED | Retained by B6 pre-check; no angle solve in this replay. | D004 |
| D019 | not reached | Z− → X− | 57 | X−@58; Z−@56–57 | REJECTED | States 0→51: minimum travel 16.090190370 exceeds upper bound 16.073901044 blocks. | — |
| D020 | not reached | X− → Z− → X− | 0, 57 | X−@58; Z−@56–57 | REJECTED | States 0→51: minimum travel 16.090190370 exceeds upper bound 16.073901044 blocks. | D019 |
| D021 | not reached | X− → Z− → X− | 56, 57 | X−@56,58; Z−@56–57 | REJECTED | States 0→39: minimum travel 12.125574097 exceeds upper bound 12.118046411 blocks. | — |
| D022 | not reached | X+ → Z− → X− | 56, 57 | X+@56; X−@58; Z−@56–57 | REJECTED | Propagated X position range is empty at state 58. | — |
| D023 | not reached | Y− → Z− → X− | 0, 57 | X−@58; Z−@56–57 | REJECTED | States 0→51: minimum travel 16.090190370 exceeds upper bound 16.073901044 blocks. | D019 |
| D024 | not reached | X− → Y− → Z− → X− | 0, 1, 57 | X−@58; Z−@56–57 | REJECTED | States 0→51: minimum travel 16.090190370 exceeds upper bound 16.073901044 blocks. | D019 |
| D025 | not reached | Y− → X+ → Z− → X− | 0, 0, 57 | X−@58; Z−@56–57 | REJECTED | States 0→51: minimum travel 16.090190370 exceeds upper bound 16.073901044 blocks. | D019 |
| D026 | not reached | Y− → X+ → Z− → X− | 0, 56, 57 | X+@56; X−@58; Z−@56–57 | REJECTED | Propagated X position range is empty at state 58. | D022 |
| D027 | not reached | Z− → X+ | 57 | X+@58; Z−@56–57 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| D028 | not reached | X− → Z− → X+ | 0, 57 | X+@58; Z−@56–57 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | D027 |
| D029 | not reached | X− → Z− → X+ | 56, 57 | X+@58; X−@56; Z−@56–57 | REJECTED | Propagated X position range is empty at state 58. | — |
| D030 | not reached | X+ → Z− → X+ | 56, 57 | X+@56,58; Z−@56–57 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | — |
| D031 | not reached | Y− → Z− → X+ | 0, 57 | X+@58; Z−@56–57 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | D027 |
| D032 | not reached | X− → Y− → Z− → X+ | 0, 1, 57 | X+@58; Z−@56–57 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | D027 |
| D033 | not reached | Y− → X− → Z− → X+ | 0, 0, 57 | X+@58; Z−@56–57 | REJECTED | Optimistic linear movement model is infeasible (recorded LP status 2). | D027 |
| D034 | not reached | Y− → X− → Z− → X+ | 0, 56, 57 | X+@58; X−@56; Z−@56–57 | REJECTED | Propagated X position range is empty at state 58. | D029 |
