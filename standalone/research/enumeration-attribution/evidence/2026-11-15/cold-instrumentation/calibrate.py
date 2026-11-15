"""Measure hash-hook overhead on identical captured canonical payloads.

This is a diagnostic microbenchmark, not search/solver speedup evidence.
"""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import time
import tracemalloc

from parkour_solver.search.identity import digest
from tools.profiling.bookkeeping import BookkeepingProfile
from tools.profiling.runtime import RuntimeProfile


def timed(function, value, count, expected):
    started = time.perf_counter()
    for _ in range(count):
        result = function(value)
    elapsed = time.perf_counter()-started
    if result != expected:
        raise AssertionError('Hook changed canonical identity')
    return elapsed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--examples', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--repeats', type=int, default=5)
    parser.add_argument('--max-iterations', type=int, default=20000)
    args = parser.parse_args()
    if args.repeats < 1 or args.max_iterations < 1:
        parser.error('Counts must be positive')
    if args.output.exists():
        parser.error('Use a new output file')
    examples = json.loads(args.examples.read_text())
    rows = []
    for site, encoded in examples.items():
        value = json.loads(encoded)
        expected = hashlib.sha256(encoded.encode()).hexdigest()
        first = timed(digest, value, 10, expected)/10
        count = min(args.max_iterations, max(10, int(.05/max(first, 1e-9))))
        results = {name:[] for name in ('unwrapped', 'sampled', 'legacy')}
        # Rotate order to reduce a fixed warmup/thermal ordering bias.
        for repeat in range(args.repeats):
            names = list(results)
            names = names[repeat % 3:]+names[:repeat % 3]
            for name in names:
                runtime = RuntimeProfile()
                function = (digest if name == 'unwrapped' else
                    BookkeepingProfile(runtime).digest(digest) if name == 'sampled' else
                    runtime.function('identity.digest', digest))
                results[name].append(timed(function, value, count, expected))
        tracemalloc.start()
        try:
            result = digest(value)
            current, peak = tracemalloc.get_traced_memory()
        finally:
            tracemalloc.stop()
        if result != expected:
            raise AssertionError('Allocation probe changed identity')
        medians = {name:statistics.median(values)/count for name,values in results.items()}
        rows.append(dict(site=site, canonicalBytes=len(encoded.encode()), digest=expected,
            iterations=count, repeats=args.repeats, rawSeconds=results,
            medianSecondsPerCall=medians,
            overheadSecondsPerCall={name:medians[name]-medians['unwrapped'] for name in ('sampled','legacy')},
            tracedDigestCurrentBytes=current, tracedDigestPeakBytes=peak))
    report = dict(examplesHash=hashlib.sha256(args.examples.read_bytes()).hexdigest(), rows=rows,
        accounting='Same payload/iteration count in all modes. New hook state per repetition. Medians include hook overhead; no search speedup claim. One representative payload per caller, not its whole distribution.')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False)+'\n')
    print(json.dumps(dict(payloads=len(rows), output=str(args.output))))


if __name__ == '__main__':
    main()
