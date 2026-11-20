"""Acceptance step 2: production numerics.disk on the audit's held-out native population.

Usage: probe_production.py <audit-dir> <new-output-dir>
Solves every supported request of audit-20261119/native-capture (and its
threshold-0 variants) with parkour_solver.numerics.disk, and joins the audit's
IPOPT evidence (probe results and follow-ups), which this source does not change.
"""
import collections
import gzip
import json
from pathlib import Path
import sys

from parkour_solver.numerics import disk
from parkour_solver.validation.numerical import validate_controls

audit, out = Path(sys.argv[1]), Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=False)
requests = {}
for line in gzip.open(audit/'native-capture'/'requests.jsonl.gz', 'rt'):
    row = json.loads(line)
    if row['request'] is not None:
        requests[row['id']] = row['request']
old = {(r['variant'], r['id']): r for r in map(json.loads, (audit/'native-probe'/'results.jsonl').open())}
followup = {(r['variant'], r['id']): r for r in map(json.loads, (audit/'followup.jsonl').open())}
rows = []
for (variant, id_), before in old.items():
    p = requests[id_] if variant == 'captured' else dict(requests[id_], threshold=0.)
    content = disk.spec(p, strict=True)
    row = dict(variant=variant, id=id_, supported=content is not None, auditSupported=before['supported'])
    if content is not None:
        solved = disk.solve(content, seconds=disk.CAP)
        row.update(status=solved['status'], outcome=solved['outcome'], auditStatus=before['disk']['status'])
        if solved['controls'] is not None:
            checked = validate_controls(p, solved['controls'])
            peak, _, tick = disk.deficits(p, solved['controls'])
            row.update(accepted=checked['feasible'], objective=checked['objective'], peak=peak,
                       alarm=peak > disk.ALARM_FRACTION)
            best = before.get('bestFullObjective')
            gap = followup.get((variant, id_), {}).get('gapAfterRandom')
            if best is not None:
                sense = 1 if p['objective']['sense'] == 'MAX' else -1
                # Material gaps use the audit's best full-strength score after its random restarts.
                fb = followup.get((variant, id_), {}).get('bestFull', best)
                row.update(bestFullObjective=fb, gap=sense*(checked['objective']-fb))
        exclusion = followup.get((variant, id_))
        row['ipoptWitness'] = before['ipoptWitness']
        if exclusion is not None and exclusion['kind'] == 'exclusion':
            row['followupIpopt'] = exclusion['ipopt']
    rows.append(row)
with (out/'results.jsonl').open('x') as log:
    for row in rows:
        log.write(json.dumps(row)+'\n')
supported = [r for r in rows if r['supported']]
summary = dict(
    distinct=len(rows), supported=len(supported),
    supportChanged=[r['id'] for r in rows if r['supported'] != r['auditSupported']],
    statuses=dict(collections.Counter(r['status'] for r in supported)),
    verdictChanged=[dict(id=r['id'], variant=r['variant'], now=r['status'], audit=r['auditStatus']) for r in supported
                    if (r['status'] in disk.INFEASIBLE) != (r['auditStatus'] in disk.INFEASIBLE)],
    wrongExclusions=[r['id'] for r in supported if r['outcome'] == 'infeasible'
                     and (r['ipoptWitness'] or r.get('followupIpopt') == 'feasible')],
    rejectedRelaxedPoints=[r['id'] for r in supported if r.get('accepted') is False],
    feasibleWithoutFullPoint=[r['id'] for r in supported if r['outcome'] == 'feasible' and r.get('bestFullObjective') is None],
    materialGaps=[dict(id=r['id'], variant=r['variant'], gap=r['gap'], peak=r['peak'], alarm=r['alarm'])
                  for r in supported if r.get('gap') is not None and r['gap'] > disk.MATERIAL_GAP],
    alarms=sum(bool(r.get('alarm')) for r in supported))
summary['missedMaterialGaps'] = [g for g in summary['materialGaps'] if not g['alarm']]
(out/'summary.json').write_text(json.dumps(summary, indent=1))
print(json.dumps({k: (v if not isinstance(v, list) else len(v)) for k, v in summary.items()}))
