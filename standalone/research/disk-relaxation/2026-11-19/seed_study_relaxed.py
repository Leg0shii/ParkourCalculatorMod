"""Seed arms on requests captured from the relaxed search (capture_relaxed.py).

Usage: PYTHONPATH=<snapshot>/src seed_study_relaxed.py <capture-dir> <new-output-dir> [workers]
Population: distinct optimization requests (stop_on_feasible=False) whose relaxation is feasible.
Arms as in seed_study.py (full, partial, none = the request's own seed, cold). The request's own
seed now comes from relaxed parents, as in production. Reference: when every arm finds a point and
all agree within 1e-4, the best arm; otherwise 8 random-yaw IPOPT restarts (10 s each) are added.
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
    capture, out = Path(sys.argv[1]), Path(sys.argv[2])
    workers = int(sys.argv[3]) if len(sys.argv) > 3 else 4
    out.mkdir(parents=True, exist_ok=False)
    cases, seen = [], set()
    for line in gzip.open(capture/'requests.jsonl.gz', 'rt'):
        r = json.loads(line)
        p = r['request']
        if p is None or r['stopOnFeasible'] or r['id'] in seen or (r['disk'] or {}).get('outcome') != 'feasible':
            continue
        seen.add(r['id'])
        cases.append((r, p))
    print(len(cases), 'relaxed-feasible optimization requests', flush=True)
    rng = random.Random(20261119)
    log = (out/'results.jsonl').open('x')
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        for index, (r, p) in enumerate(cases):
            arm_seeds, disk_objective, shortfall = seeds(p)
            outcomes = list(pool.map(run, [(p, arm, arm_seeds[arm], p['seconds']) for arm in ARMS]))
            retry = [(p, o['arm'], arm_seeds[o['arm']], LONG) for o in outcomes if o['status'] != 'feasible']
            longer = {o['arm']: o for o in pool.map(run, retry)}
            sense = 1 if p['objective']['sense'] == 'MAX' else -1
            found = [o['objective'] for o in outcomes if o['objective'] is not None]
            restarts = []
            if len(found) < len(ARMS) or max(found)-min(found) > 1e-4:
                jobs = [(p, 'random', [rng.uniform(-180., 180.) for _ in range(p['ticks'])], LONG) for _ in range(RESTARTS)]
                restarts = [o['objective'] for o in pool.map(run, jobs)]
            row = dict(test=r['test'], id=r['id'], ticks=p['ticks'], sense=p['objective']['sense'],
                       requestSeconds=p['seconds'], alarm=max(shortfall) > 0.01, peak=max(shortfall),
                       shortTicks=[t for t, s in enumerate(shortfall) if s > 0.01], diskObjective=disk_objective,
                       auditBestFull=None, randomScores=restarts, arms={})
            known = found + [o['objective'] for o in longer.values() if o['objective'] is not None]
            known += [s for s in restarts if s is not None]
            row['bestKnown'] = max(known, key=lambda v: sense*v) if known else None
            row['auditBestFull'] = row['bestKnown']  # seed_summary.py reads this as the external reference
            for o in outcomes:
                o['gapToDisk'] = None if o['objective'] is None else sense*(disk_objective-o['objective'])
                row['arms'][o['arm']] = dict(o, long=longer.get(o['arm']))
            log.write(json.dumps(row)+'\n')
            log.flush()
            print(index+1, r['id'][:8], {a: row['arms'][a]['status'] for a in ARMS}, 'restarts' if restarts else '',
                  flush=True)
    log.close()


if __name__ == '__main__':
    main()
