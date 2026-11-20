"""Summarize seed_study output. Usage: seed_summary.py <seed-study-dir> (writes summary.json there)."""
import collections, json, statistics, sys
from pathlib import Path

ARMS = ('full', 'partial', 'none', 'cold')
d = Path(sys.argv[1])
rows = [json.loads(l) for l in (d/'results.jsonl').open()]
out = {}
for scope, sel in (('all', rows), ('alarms', [r for r in rows if r['alarm']]), ('quiet', [r for r in rows if not r['alarm']])):
    s = out[scope] = dict(requests=len(sel))
    for arm in ARMS:
        a = collections.Counter()
        cpus, cpus_final = [], []
        for r in sel:
            sense = 1 if r['sense'] == 'MAX' else -1
            found = [o['objective'] for o in r['arms'].values() if o['objective'] is not None]
            found += [o['long']['objective'] for o in r['arms'].values() if o['long'] and o['long']['objective'] is not None]
            if r['auditBestFull'] is not None:
                found.append(r['auditBestFull'])
            best = max(found, key=lambda v: sense*v) if found else None
            o = r['arms'][arm]
            cpus.append(o['cpu'])
            a['firstPass:'+o['status']+':'+str(o['workerStatus'])] += 1
            final = o['long'] or o
            cpus_final.append(o['cpu'] + (o['long']['cpu'] if o['long'] else 0))
            a['final:'+final['status']+':'+str(final['workerStatus'])] += 1
            for tag, res in (('first', o), ('final', final)):
                if res['objective'] is not None and best is not None:
                    short = sense*(best-res['objective'])
                    a[tag+'WithinBest1e-6'] += short <= 1e-6
                    a[tag+'ShortOfBest>1e-4'] += short > 1e-4
                    gap = sense*(r['diskObjective']-res['objective'])
                    truth = sense*(r['diskObjective']-best)
                    a[tag+'MaterialCallWrong'] += (gap > 1e-4) != (truth > 1e-4)
                else:
                    a[tag+'NoPoint'] += 1
                    a[tag+'NoPointInfeasibleDetected'] += res['workerStatus'] == 'Infeasible_Problem_Detected'
        s[arm] = dict(sorted(a.items()), cpuTotal=round(sum(cpus), 3), cpuMedian=round(statistics.median(cpus), 4),
                      cpuP90=round(sorted(cpus)[int(.9*len(cpus))], 4), cpuMax=round(max(cpus), 3),
                      cpuTotalWithRetry=round(sum(cpus_final), 3))
(d/'summary.json').write_text(json.dumps(out, indent=1))
print(json.dumps(out, indent=1))
