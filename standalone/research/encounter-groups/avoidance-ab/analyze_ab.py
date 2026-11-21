"""usage: analyze_ab.py <ab.jsonl>: per-call acceptance criteria of plan step 3b, totals and worst cases."""
import collections, json, sys
rows = [json.loads(l) for l in open(sys.argv[1])]
tot = collections.Counter()
fails = collections.defaultdict(list)
for i, r in enumerate(rows):
    a, b = r['upfront'], r['conflict']
    tot['calls'] += 1; tot['calls:'+str(r['purpose'])] += 1
    for k in ('seconds', 'clarabelSolves', 'ipopt', 'requests'):
        tot['upfront.'+k] += a[k]; tot['conflict.'+k] += b[k]
    feasible = lambda x: x['verdict'] == 'feasible'
    if feasible(a) != feasible(b) or (a['verdict'] != b['verdict']):
        fails['verdict'].append((i, r['purpose'], r['ticks'], a['verdict'], b['verdict']))
    if a['bestRelaxed'] and b['bestRelaxed']:
        ra, rb = a['bestRelaxed'], b['bestRelaxed']
        if ra[0] != rb[0] or abs(ra[1]-rb[1]) > 1e-6:
            fails['bestRelaxed'].append((i, r['purpose'], r['ticks'], ra, rb, 'conflict better' if rb < ra else 'upfront better'))
    elif bool(a['bestRelaxed']) != bool(b['bestRelaxed']):
        fails['bestRelaxed'].append((i, r['purpose'], r['ticks'], a['bestRelaxed'], b['bestRelaxed'], 'one side has none'))
    if b['clarabelSolves'] >= a['clarabelSolves'] and a['clarabelSolves']:
        fails['clarabel'].append((i, r['purpose'], r['ticks'], a['clarabelSolves'], b['clarabelSolves']))
    if b['seconds'] >= a['seconds']:
        fails['seconds'].append((i, r['purpose'], r['ticks'], round(a['seconds'], 3), round(b['seconds'], 3)))
    tot['conflict.obstaclesHit'] += len(b['obstacles'] or [])
    if b['branchKeys'] < a['branchKeys']:
        fails['alternatives'].append((i, r['purpose'], r['ticks'], a['branchKeys'], b['branchKeys'], len(b['obstacles'] or [])))
for k in sorted(tot):
    print(f'{k:28s} {tot[k]:.3f}' if isinstance(tot[k], float) else f'{k:28s} {tot[k]}')
for k, v in fails.items():
    print(f'\n{k}: {len(v)} calls fail')
    if k == 'seconds':
        v = sorted(v, key=lambda e: e[3]-e[4])
    for e in v[:12]:
        print('  ', e)
