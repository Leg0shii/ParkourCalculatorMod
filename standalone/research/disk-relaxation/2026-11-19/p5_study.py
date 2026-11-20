"""Relaxation verdicts and IPOPT seed accuracy on every distinct request of a capture_run.py capture.

Usage: PYTHONPATH=<feature>/src p5_study.py <new-output-dir> <workers> <capture.jsonl.gz>

Per distinct request: the production relaxation verdict (disk.solve with its zero-cost retry), then IPOPT
optimizations (stop_on_feasible=False, the request's own seconds and fallbackYaw) from
  own         the request's own seed (what the capturing run's IPOPT saw)
  production  disk.ipopt_seed(relaxed facings) (relaxation-feasible requests only)
  cold        every tick at startYaw
When the arms disagree on feasibility or objective, 8 random-yaw restarts at 10 s widen the truth.
Truth: 'feasible' when any IPOPT run found a point, else 'noPointFound' (presumed infeasible).
"""
import concurrent.futures
import gzip
import json
from pathlib import Path
import random
import sys
import time

sys.path.insert(0, str(Path(__file__).parent))
from seed_study import LONG, run

RESTARTS = 8


def relax(p):
    from parkour_solver.numerics import disk
    from parkour_solver.validation.numerical import validate_controls
    content = disk.spec(p, strict=True)
    if content is None:
        return dict(outcome='unsupported'), None
    started = time.process_time()
    solved = disk.solve(content, seconds=disk.CAP)
    verdict = dict(outcome=solved['outcome'], status=solved.get('status'), retried=solved.get('retried', False),
                   cpu=time.process_time()-started)
    if solved['outcome'] != 'feasible':
        return verdict, None
    relaxed = validate_controls(p, solved['controls'])
    _, shortfall, _ = disk.deficits(p, solved['controls'])
    verdict.update(objective=relaxed['objective'], peak=max(shortfall), alarmTicks=disk.alarm_ticks(p, shortfall))
    return verdict, disk.ipopt_seed(relaxed['yaw'])


def main():
    out, workers, capture = Path(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
    out.mkdir(parents=True, exist_ok=False)
    rows, seen = [], set()
    for line in gzip.open(capture, 'rt'):
        r = json.loads(line)
        if r['request'] is not None and r['id'] not in seen:
            seen.add(r['id'])
            rows.append(r)
    print(len(rows), 'distinct requests', flush=True)
    rng = random.Random(20261119)
    log = (out/'results.jsonl').open('x')
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        for index, r in enumerate(rows):
            p = r['request']
            verdict, production = relax(p)
            jobs = [(p, 'own', p['yaw'], p['seconds']), (p, 'cold', [p['startYaw']]*p['ticks'], p['seconds'])]
            if production is not None:
                jobs.append((p, 'production', production, p['seconds']))
            arms = {o['arm']: o for o in pool.map(run, jobs)}
            found = [o['objective'] for o in arms.values() if o['objective'] is not None]
            restarts = []
            if len(found) < len(arms) or max(found)-min(found) > 1e-4:
                restarts = [o['objective'] for o in pool.map(run, [
                    (p, 'random', [rng.uniform(-180., 180.) for _ in range(p['ticks'])], LONG) for _ in range(RESTARTS)])]
            sense = 1 if p['objective']['sense'] == 'MAX' else -1
            known = found + [s for s in restarts if s is not None]
            row = dict(id=r['id'], capturedStop=r['stopOnFeasible'], capturedStatus=r['status'],
                       capturedWorkerStatus=r['workerStatus'], ticks=p['ticks'], sense=p['objective']['sense'],
                       requestSeconds=p['seconds'], relaxation=verdict, randomScores=restarts,
                       bestKnown=max(known, key=lambda v: sense*v) if known else None, arms=arms)
            log.write(json.dumps(row, allow_nan=False)+'\n')
            log.flush()
            print(index+1, r['id'][:8], verdict['outcome'], {a: o['status'] for a, o in arms.items()},
                  'restarts' if restarts else '', flush=True)
    log.close()


if __name__ == '__main__':
    main()
