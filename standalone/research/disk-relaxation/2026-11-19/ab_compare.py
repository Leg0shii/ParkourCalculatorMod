"""Compare ab_capture.py runs. Usage: ab_compare.py <run-dir>... (label = directory name).

Per run: dispatch backend/fallback counts, confirmations, searches with a best, replay failures, and summed
work counters. Then, per automatic search (test + call index), the published objective and replay verdict
across runs, listing every search whose outcome is not identical in all runs.
"""
import collections
import gzip
import json
from pathlib import Path
import sys

runs = [Path(a) for a in sys.argv[1:]]
outcomes = {}
for run in runs:
    rows = [json.loads(l) for l in gzip.open(run/'requests.jsonl.gz', 'rt')]
    searches = [json.loads(l) for l in (run/'searches.jsonl').open()]
    calls = collections.Counter((r.get('backend'), r.get('fallback')) for r in rows)
    work = collections.Counter()
    for s in searches:
        work.update({k: v for k, v in s['work'].items() if k in (
            'solverCalls', 'workerLaunches', 'confirmations', 'confirmationIpoptCalls', 'materialGaps', 'promotions',
            'relaxedFallbacks', 'relaxedFallback:promoted', 'finalizationIpoptCalls', 'conicCandidates')})
    summary = json.loads((run/'summary.json').read_text())
    print(run.name, 'tests', summary['tests'], 'failures', summary['failures'], 'errors', summary['errors'])
    print('  dispatch calls', len(rows), dict(calls), 'confirmations', sum(1 for r in rows if r.get('confirmation')))
    print('  searches', len(searches), 'with best', sum(s['objective'] is not None for s in searches),
          'replay not passed', [s['test'] for s in searches if s['objective'] is not None and s['replay'] is not True],
          'relaxed published', sum(1 for s in searches if s['model'] is not None))
    print('  work', dict(sorted(work.items())), 'search seconds %.1f' % sum(s['seconds'] for s in searches))
    seen = collections.Counter()
    for s in searches:
        key = (s['test'], seen[s['test']])
        seen[s['test']] += 1
        outcomes.setdefault(key, {})[run.name] = (s['status'], None if s['objective'] is None else round(s['objective'], 6),
                                                  s['replay'], s['finalization'], s['termination'])
print('searches compared', len(outcomes))
for key, by_run in sorted(outcomes.items()):
    if len(set(by_run.values())) > 1 or len(by_run) < len(runs):
        print(key[0].split('.')[-1], key[1])
        for name in [r.name for r in runs]:
            print('   ', name, by_run.get(name))
