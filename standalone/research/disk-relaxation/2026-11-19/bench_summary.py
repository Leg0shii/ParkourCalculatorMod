"""Summarize seed_bench.py output. Usage: bench_summary.py <seed-bench-dir> (writes summary.json there)."""
import collections
import json
from pathlib import Path
import statistics
import sys

ARMS = ('full', 'partial', 'none', 'cold')
d = Path(sys.argv[1])
rows = [json.loads(l) for l in (d/'results.jsonl').open()]
out = {}
for truth in ('feasible', 'noPointFound', 'all'):
    sel = [r for r in rows if truth == 'all' or (r['bestKnown'] is not None) == (truth == 'feasible')]
    if not sel:
        continue
    s = out[truth] = dict(requests=len(sel))
    for arm in ARMS:
        a, cpu = collections.Counter(), []
        for r in sel:
            o = r['arms'][arm]
            cpu.append(o['cpu'])
            a[o['status']+':'+str(o['workerStatus'])] += 1
            if r['bestKnown'] is None:
                continue
            if o['objective'] is None:
                a['falseNoPoint'] += 1
                a['falseInfeasibleDetected'] += o['workerStatus'] == 'Infeasible_Problem_Detected'
            else:
                sense = 1 if r['sense'] == 'MAX' else -1
                a['shortOfBest>1e-4'] += sense*(r['bestKnown']-o['objective']) > 1e-4
                a['withinBest1e-6'] += sense*(r['bestKnown']-o['objective']) <= 1e-6
        s[arm] = dict(sorted(a.items()), cpuTotal=round(sum(cpu), 3), cpuMedian=round(statistics.median(cpu), 4),
                      cpuP90=round(sorted(cpu)[int(.9*len(cpu))], 4), cpuMax=round(max(cpu), 3))
(d/'summary.json').write_text(json.dumps(out, indent=1))
for truth, s in out.items():
    print(truth, s['requests'])
    for arm in ARMS:
        print(' ', arm, s[arm])
