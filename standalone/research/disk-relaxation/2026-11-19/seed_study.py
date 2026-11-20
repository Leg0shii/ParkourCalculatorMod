"""A2/A3: IPOPT confirmation outcome and CPU by seed, on every relaxed-feasible held-out request.

Usage: PYTHONPATH=<snapshot>/src seed_study.py <audit-dir> <probe-dir> <new-output-dir> [workers]

Arms (the request's fallbackYaw stays in every arm, as in production):
  full     every tick seeded with the disk direction
  partial  disk direction where the tick uses >= 99% of its acceleration, request yaw elsewhere (production)
  none     the request's own yaw (no confirmation seed)
  cold     every tick at the request's startYaw (constant facing)
Each (request, arm) runs at the request's own seconds; anything not feasible is rerun at 10 s.
CPU is the IPOPT worker subprocess's user+sys time (RUSAGE_CHILDREN delta, one solve per process at a time).
"""
import concurrent.futures
import gzip
import json
from pathlib import Path
import resource
import sys
import time

ARMS = ('full', 'partial', 'none', 'cold')
LONG = 10.


def seeds(p):
    from parkour_solver.numerics import disk
    solved = disk.solve(disk.spec(p, strict=True), seconds=disk.CAP)
    assert solved['outcome'] == 'feasible', solved['status']
    from parkour_solver.validation.numerical import validate_controls
    relaxed = validate_controls(p, solved['controls'])
    _, shortfall, _ = disk.deficits(p, solved['controls'])
    return dict(full=relaxed['yaw'],
                partial=[own if s > disk.ALARM_FRACTION else d for own, d, s in zip(p['yaw'], relaxed['yaw'], shortfall)],
                none=p['yaw'], cold=[p['startYaw']]*p['ticks']), relaxed['objective'], shortfall


def run(job):
    from parkour_solver.numerics.solver import solve
    p, arm, yaw, seconds = job
    before, started = resource.getrusage(resource.RUSAGE_CHILDREN), time.monotonic()
    result = solve(dict(p, yaw=yaw), seconds=seconds, stop_on_feasible=False)
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    return dict(arm=arm, seconds=seconds, status=result['status'], termination=result['termination'],
                workerStatus=result.get('workerStatus'), workerLaunched=result.get('workerLaunched'),
                objective=result['candidate']['objective'] if result['candidate'] else None,
                cpu=(after.ru_utime-before.ru_utime)+(after.ru_stime-before.ru_stime), wall=time.monotonic()-started)


def main():
    audit, probe, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    workers = int(sys.argv[4]) if len(sys.argv) > 4 else 4
    out.mkdir(parents=True, exist_ok=False)
    requests = {}
    for line in gzip.open(audit/'native-capture'/'requests.jsonl.gz', 'rt'):
        row = json.loads(line)
        if row['request'] is not None:
            requests[row['id']] = row['request']
    cases = []
    for line in (probe/'results.jsonl').open():
        r = json.loads(line)
        if r.get('outcome') != 'feasible':
            continue
        p = requests[r['id']] if r['variant'] == 'captured' else dict(requests[r['id']], threshold=0.)
        arm_seeds, disk_objective, shortfall = seeds(p)
        cases.append((r, p, arm_seeds, disk_objective, shortfall))
    print(len(cases), 'relaxed-feasible requests', flush=True)
    log = (out/'results.jsonl').open('x')
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        for index, (r, p, arm_seeds, disk_objective, shortfall) in enumerate(cases):
            jobs = [(p, arm, arm_seeds[arm], p['seconds']) for arm in ARMS]
            outcomes = list(pool.map(run, jobs))
            retry = [(p, o['arm'], arm_seeds[o['arm']], LONG) for o in outcomes if o['status'] != 'feasible']
            longer = {o['arm']: o for o in pool.map(run, retry)}
            sense = 1 if p['objective']['sense'] == 'MAX' else -1
            row = dict(variant=r['variant'], id=r['id'], ticks=p['ticks'], sense=p['objective']['sense'],
                       requestSeconds=p['seconds'], alarm=r['alarm'], peak=r['peak'],
                       shortTicks=[t for t, s in enumerate(shortfall) if s > 0.01], diskObjective=disk_objective,
                       auditBestFull=r.get('bestFullObjective'), arms={})
            for o in outcomes:
                o['gapToDisk'] = None if o['objective'] is None else sense*(disk_objective-o['objective'])
                row['arms'][o['arm']] = dict(o, long=longer.get(o['arm']))
            log.write(json.dumps(row)+'\n')
            log.flush()
            print(index+1, r['id'][:8], r['variant'], {a: row['arms'][a]['status'] for a in ARMS}, flush=True)
    log.close()


if __name__ == '__main__':
    main()
