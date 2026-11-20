"""Full disk seed with short ticks rotated off the symmetry line, on the three seed populations.

Usage: PYTHONPATH=<src> seed_tilt.py <new-output-dir> <workers> [all] <degrees>...
A short tick (shortfall > ALARM_FRACTION) gets its disk direction plus <degrees>; other ticks keep
their disk direction. With `all`, every tick gets its disk direction plus <degrees>. Populations and references come from the earlier studies' results.jsonl.
"""
import concurrent.futures
import gzip
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent))
from seed_study import run

HERE = Path(__file__).parent
AUDIT = HERE.parent/'audit-20261119'/'native-capture'/'requests.jsonl.gz'
RUNS = HERE.parent/'runs'


def load(path):
    out = {}
    for line in gzip.open(path, 'rt'):
        r = json.loads(line)
        if r['request'] is not None:
            out.setdefault(r['id'], r['request'])
    return out


def best(r, arms):
    sense = 1 if r['sense'] == 'MAX' else -1
    found = [o['objective'] for o in arms.values() if o['objective'] is not None]
    found += [o['long']['objective'] for o in arms.values() if o.get('long') and o['long']['objective'] is not None]
    found += [s for s in r.get('randomScores', []) if s is not None]
    for key in ('auditBestFull', 'bestKnown'):
        if r.get(key) is not None:
            found.append(r[key])
    return max(found, key=lambda v: sense*v) if found else None


def populations():
    audit = load(AUDIT)
    for line in (HERE/'seed-study-d6f52446'/'results.jsonl').open():
        r = json.loads(line)
        p = audit[r['id']] if r['variant'] == 'captured' else dict(audit[r['id']], threshold=0.)
        yield 'heldout', r, p
    native = load(HERE/'relaxed-capture-5edfc66a-nopromote'/'requests.jsonl.gz')
    for line in (HERE/'seed-study-relaxed-5edfc66a'/'results.jsonl').open():
        r = json.loads(line)
        yield 'nativeRelaxed', r, native[r['id']]
    real11 = load(RUNS/'extended-stock-c9deef42-capture.jsonl.gz')
    real11.update({k: v for k, v in load(RUNS/'extended-relaxed-5edfc66a-capture.jsonl.gz').items() if k not in real11})
    for line in (HERE/'seed-bench-extended'/'results.jsonl').open():
        r = json.loads(line)
        yield 'real11', r, real11[r['id']]


def main():
    global ALL
    ALL = sys.argv[3] == 'all'
    out, workers, tilts = Path(sys.argv[1]), int(sys.argv[2]), [float(a) for a in sys.argv[3+ALL:]]
    out.mkdir(parents=True, exist_ok=False)
    from parkour_solver.numerics import disk
    from parkour_solver.validation.numerical import validate_controls
    log = (out/'results.jsonl').open('x')
    with concurrent.futures.ProcessPoolExecutor(workers) as pool:
        for index, (population, r, p) in enumerate(populations()):
            solved = disk.solve(disk.spec(p, strict=True), seconds=disk.CAP)
            yaw = validate_controls(p, solved['controls'])['yaw']
            _, shortfall, _ = disk.deficits(p, solved['controls'])
            if ALL:
                jobs = [(p, f'all{d:g}', [y+d for y in yaw], p['seconds']) for d in tilts]
            else:
                jobs = [(p, f'tilt{d:g}', [y+d if s > disk.ALARM_FRACTION else y for y, s in zip(yaw, shortfall)],
                         p['seconds']) for d in tilts]
            arms = {o['arm']: o for o in pool.map(run, jobs)}
            log.write(json.dumps(dict(population=population, id=r['id'], variant=r.get('variant'), sense=r['sense'],
                                      alarm=r.get('alarm', bool(r.get('alarmTicks'))), best=best(r, r['arms']),
                                      full=r['arms']['full'], arms=arms))+'\n')
            log.flush()
            print(index+1, population, r['id'][:8], {a: o['status'] for a, o in arms.items()}, flush=True)
    log.close()


if __name__ == '__main__':
    main()
