"""Single-attempt IPOPT seed benchmark on captured Real11 requests (capture_run.py output).

Usage: PYTHONPATH=<snapshot>/src seed_bench.py <new-output-dir> <workers> <capture.jsonl.gz>...
Population: distinct requests (any kind/intent) whose disk relaxation is feasible, i.e. the requests a
confirmation or finalization could see. Each arm is one IPOPT optimization (stop_on_feasible=False, the
request's own seconds and fallbackYaw): full, partial, own (the request's seed), cold (all startYaw).
Truth: feasible when any arm or one of 8 random-yaw restarts (10 s) finds a point; otherwise
'noPointFound' (presumed infeasible; the relaxation cannot certify it).
"""
import concurrent.futures
import gzip
import json
from pathlib import Path
import random
import sys

sys.path.insert(0, str(Path(__file__).parent))
from seed_study import ARMS, LONG, run, seeds

RESTARTS = 8


def main():
    out, workers, captures = Path(sys.argv[1]), int(sys.argv[2]), sys.argv[3:]
    out.mkdir(parents=True, exist_ok=False)
    from parkour_solver.numerics import disk
    cases, seen = [], set()
    for capture in captures:
        for line in gzip.open(capture, 'rt'):
            r = json.loads(line)
            p = r['request']
            if p is None or r['id'] in seen:
                continue
            seen.add(r['id'])
            content = disk.spec(p, strict=True)
            if content is None or disk.solve(content, seconds=disk.CAP)['outcome'] != 'feasible':
                continue
            cases.append((capture, r, p))
    print(len(cases), 'relaxation-feasible distinct requests of', len(seen), flush=True)
    rng = random.Random(20261119)
    log = (out/'results.jsonl').open('x')
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        for index, (capture, r, p) in enumerate(cases):
            arm_seeds, disk_objective, shortfall = seeds(p)
            outcomes = list(pool.map(run, [(p, arm, arm_seeds[arm], p['seconds']) for arm in ARMS]))
            found = [o['objective'] for o in outcomes if o['objective'] is not None]
            restarts = []
            if len(found) < len(ARMS) or max(found)-min(found) > 1e-4:
                jobs = [(p, 'random', [rng.uniform(-180., 180.) for _ in range(p['ticks'])], LONG) for _ in range(RESTARTS)]
                restarts = [o['objective'] for o in pool.map(run, jobs)]
            sense = 1 if p['objective']['sense'] == 'MAX' else -1
            known = found + [s for s in restarts if s is not None]
            row = dict(capture=Path(capture).name, kind=r['kind'], capturedIntentStop=r['stopOnFeasible'],
                       capturedStatus=r['status'], capturedWorkerStatus=r['workerStatus'], id=r['id'], ticks=p['ticks'],
                       sense=p['objective']['sense'], requestSeconds=p['seconds'], peak=max(shortfall),
                       alarmTicks=disk.alarm_ticks(p, shortfall), diskObjective=disk_objective,
                       randomScores=restarts, bestKnown=max(known, key=lambda v: sense*v) if known else None,
                       arms={o['arm']: o for o in outcomes})
            log.write(json.dumps(row)+'\n')
            log.flush()
            print(index+1, r['id'][:8], {a: row['arms'][a]['status'] for a in ARMS}, 'restarts' if restarts else '',
                  flush=True)
    log.close()


if __name__ == '__main__':
    main()
