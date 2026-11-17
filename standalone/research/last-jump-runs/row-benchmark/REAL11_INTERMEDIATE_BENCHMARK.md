# Repeated Real11 intermediate solves

2026-11-04. Production code `090aa1ac`, archived retry source `61355976`. Measurement only; no production solver/search changes. [All nine cases and timing table](real11-intermediate-results.md), [complete data](real11-intermediate-results.json.gz), [benchmark script](real11_intermediate_benchmark.py).

## Setup

The benchmark extracts **all nine distinct intermediate numerical requests** from the archived retry, excluding its three terminal-goal requests. It checks each original physical fingerprint. Requests retain their original positions, velocities, controls/coefficients, hard rows, contacts, original/fallback yaw seeds,10000-iteration limit and zero inertia thresholds. Bound extraction and numerical-row reduction stay enabled in all modes.

Each request runs in each mode five times, rotating mode order:135 fresh API/worker runs. Every run has the same3s budget; none reaches the deadline. Both early-stop modes share the same seed bypass, validation and first-feasible stopping logic. The directional early-stop harness retains the original objective and sets the monitor's stopping flag; pure feasibility uses the production constant-zero objective. Optimization uses the production configured objective. All modes use the same benchmark subprocess entry point, avoiding different Python startup/import paths.

Separate warm first-phase probes run each request/mode three times, rotating order:81 additional probes. They isolate graph/setup/first-solve behavior after plugin load, without fallback exploration or the parent's feasible-seed bypass. A fresh early-stopping parent can terminate before phase statistics arrive; missing phase statistics are **not** counted as zero iterations.

Environment: Python3.12.2, CasADi3.8.1, macOS15.7.1 arm64; OPENBLAS/OMP threads1. Full requests, code/script/input provenance, per-run timings/status, accepted facings, exit positions/velocities, measured objective and available phase statistics are in the compressed data. The script produces ordinary JSON; the archived JSON is gzip-compressed without changing its contents.

## Results

Fresh API/worker medians for the three successful cases that need numerical solving:

| Intermediate goal | Inputs | Directional early stop | Pure feasibility | Optimization | Success per mode |
| --- | --- | --- | --- | --- | --- |
| B4 |40 |132.6ms |138.5ms |179.7ms |5/5 |
| B5,239-row request |58 |143.3ms |142.6ms |199.7ms |5/5 |
| B5, alternate schedule |67 |148.1ms |151.0ms |207.4ms |5/5 |

The two B2 candidates (17/21 inputs), B3 at28 inputs, and the separate240-row B5/58 request already have valid initial seeds. Both early-stop modes return them in **0.2–0.7ms**, avoiding worker startup entirely. Optimization takes153–203ms on those same requests. This is the effect of accepting an existing feasible seed, not IPOPT solving in a fraction of a millisecond.

The27-input B3 and57-input B5 requests remain **unknown in every repetition and mode**, matching the archive. Fresh median pure-feasibility times are174/252ms versus directional early-stop242/275ms. Faster unsuccessful local searches are not infeasibility certificates and do not justify additional pruning.

Across the135 fresh runs:105 feasible,30 unknown, **zero errors, timeouts or invalid accepted candidates**. All three modes agree on numerical feasibility for all nine requests. An additional offline pass independently reconstructed all105 accepted trajectories and checked every original request row, physical fingerprint, reported objective and saved exit position/velocity.

Summed fresh-run durations are9.299s optimization,4.802s directional early stop and4.360s pure feasibility. Thus pure feasibility cuts this batch's summed request time about53% versus optimization, but only about9% versus directional early stop. These totals include the four feasible-seed shortcuts and two unknown cases; they are not an overall route-search speedup.

Warm first-phase iteration counts illustrate the smaller effect on successful numerical work:

| Inputs/goal | Directional early stop | Pure feasibility | Optimization |
| --- | --- | --- | --- |
|40/B4 |16 |16 |18 |
|58/B5,239 rows |11 |8 |13 |
|67/B5 |19 |13 |21 |

Their warm construction/setup/first-solve times remain approximately25ms,30ms and38–40ms in either early-stop mode. Fewer iterations do not create a large wall-time saving here because setup and other fixed work matter.

## Tradeoff and interpretation

**The main speed benefit is early stopping, not removing the directional objective.** Pure feasibility is appropriate when the only requirement is an accepted witness. On these successful numerical requests, directional early stopping is about equally fast and returns nearly optimized Z progress. Constant-objective witnesses are about **0.211,0.211 and0.470 blocks shorter in Z** than the optimized witnesses for B4/40, B5/58 and B5/67 respectively. Their hard constraints are still satisfied. These are witness-quality differences, not proof that their discrete schedules lead to worse completed routes: later decisions re-solve facings from the original state.

Keep the requested pure-feasibility policy unchanged by this benchmark. If intermediate p/v measurements or warm-start quality later prove consequential, directional early stopping is a measured alternative worth comparing: it supplies more useful progress here with little extra successful-case cost. No evidence here justifies adding an artificial weighted objective, changing pruning, or interpreting arbitrary feasible progress as an optimized score.

This is a **fixed continuous-request** benchmark. It excludes new movement/constraint enumeration, Java coefficient compilation, scene/corridor replay, orchestration callbacks and checkpoint saves. The67-input B5 request is numerically feasible while its archived whole-program status was unknown; this benchmark does not resolve that later acceptance/search limitation. No full new Real11 route search or end-to-end decision-quality comparison was run.

## Reproduce

From the physical research root:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/real11_intermediate_benchmark.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/real11-intermediate-results-new.json \
  --repeats 5 --warm-repeats 3 --seconds 3
```

The command writes JSON and a companion Markdown timing table. Read the committed compressed data with `standalone.autonomy.checkpoint_store.read_report`. No compiler, Minecraft, new search state or captured optimized yaw injection is needed.
