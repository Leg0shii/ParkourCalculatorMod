"""C4: are disk-relaxation verdicts sensitive to constraint-row order?

Usage: PYTHONPATH=<feature>/src row_order.py <new-output-dir> <capture.jsonl.gz>...
Every distinct request's strict spec is solved by production disk.solve (zero-cost retry included) with its
rows in the canonical sorted order, reversed, and in 3 seeded shuffles. Reports verdict flips and the
largest objective spread between orders among requests feasible in every order.
"""
import collections
import gzip
import json
from pathlib import Path
import random
import sys

ORDERS = ('sorted', 'reversed', 'shuffle0', 'shuffle1', 'shuffle2')


def main():
    from parkour_solver.numerics import disk
    out, captures = Path(sys.argv[1]), sys.argv[2:]
    out.mkdir(parents=True, exist_ok=False)
    seen, flips, spread, counts = set(), [], [], collections.Counter()
    log = (out/'results.jsonl').open('x')
    for capture in captures:
        for line in gzip.open(capture, 'rt'):
            r = json.loads(line)
            p = r['request']
            if p is None or r['id'] in seen:
                continue
            seen.add(r['id'])
            content = disk.spec(p, strict=True)
            if content is None:
                counts['unsupported'] += 1
                continue
            rng = random.Random(r['id'])
            orders = dict(sorted=content['rows'], reversed=content['rows'][::-1])
            for i in range(3):
                shuffled = list(content['rows'])
                rng.shuffle(shuffled)
                orders[f'shuffle{i}'] = shuffled
            results = {name: disk.solve(dict(content, rows=rows), seconds=disk.CAP) for name, rows in orders.items()}
            verdicts = {name: (res['outcome'], res.get('status'), res.get('retried', False)) for name, res in results.items()}
            objectives = [res['objective'] for res in results.values() if res['outcome'] == 'feasible']
            row = dict(capture=Path(capture).name, id=r['id'], rows=len(content['rows']), verdicts=verdicts,
                       spread=max(objectives)-min(objectives) if len(objectives) == len(ORDERS) else None)
            log.write(json.dumps(row)+'\n')
            counts[results['sorted']['outcome']] += 1
            if len({v[0] for v in verdicts.values()}) > 1:
                flips.append(row)
            if row['spread'] is not None:
                spread.append((row['spread'], r['id'][:8]))
    log.close()
    summary = dict(requests=len(seen), sortedOutcomes=dict(counts), verdictFlips=[(f['id'][:8], f['verdicts']) for f in flips],
                   maxObjectiveSpread=sorted(spread, reverse=True)[:10],
                   spreadOver1e6=sum(s > 1e-6 for s, _ in spread), spreadOver1e4=sum(s > 1e-4 for s, _ in spread))
    (out/'summary.json').write_text(json.dumps(summary, indent=1))
    print(json.dumps(summary, indent=1))


if __name__ == '__main__':
    main()
