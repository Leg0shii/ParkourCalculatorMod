import copy
import json

SRC = r'C:\Users\benja\Desktop\Games\MultiMC\instances\1.8.9\.minecraft\parkourcalculator\hpk_human\d12\j154_1bm_Head_Butterfly_Neo.json'


def load():
    with open(SRC) as f:
        d = json.load(f)
    sol = d['angleSolver']
    t1 = entry_for(sol, 1)
    t0 = entry_for(sol, 0)
    t0['constraints'] = [copy.deepcopy(c) for c in t1['constraints'] if c['field'] in ('X', 'Z')]
    return d


def entry_for(sol, t):
    return next((e for e in sol['ticks'] if e['tick'] == t), None)


def write_variant(d, name):
    out = 'data/j154-%s.json' % name
    with open(out, 'w') as f:
        json.dump(d, f)
    for suffix, sprint in (('probe-w-sprint', True), ('probe-w-nosprint', False)):
        p = copy.deepcopy(d)
        for e in p['debug']:
            e['moveForward'] = 0.98
            e['moveStrafe'] = 0.0
            e['sprinting'] = sprint
        with open('data/j154-%s-%s.json' % (name, suffix), 'w') as f:
            json.dump(p, f)
    sol = d['angleSolver']
    print(name, 'rows', len(d['rows']), 'debug', len(d['debug']), 'landing', sol['landingTick'],
          'jumps', [i for i, r in enumerate(d['rows']) if 'JUMP' in r.get('keys', [])],
          'grounds', sorted(e['tick'] for e in sol['ticks'] if e.get('override')))


def runup(n):
    d = load()
    sol = d['angleSolver']
    blank = copy.deepcopy(d['rows'][0])
    blank['keys'] = []
    blank['yaw'] = 0.0
    d['rows'][0:0] = [copy.deepcopy(blank) for _ in range(n)]
    d['debug'][1:1] = [copy.deepcopy(d['debug'][0]) for _ in range(n)]
    t1 = entry_for(sol, 1)
    for e in sol['ticks']:
        if e['tick'] >= 1:
            e['tick'] += n
    for k in range(1, 1 + n):
        e = copy.deepcopy(t1)
        e['tick'] = k
        e['constraints'] = [c for c in e['constraints'] if c['field'] != 'DF']
        sol['ticks'].append(e)
    sol['landingTick'] += n
    write_variant(d, 'runup%d' % n)


def plusjump():
    d = load()
    sol = d['angleSolver']
    d['rows'][28:28] = [copy.deepcopy(r) for r in d['rows'][15:28]]
    d['debug'][29:29] = [copy.deepcopy(e) for e in d['debug'][16:29]]
    dups = [copy.deepcopy(e) for e in sol['ticks'] if 15 <= e['tick'] <= 27]
    for e in sol['ticks']:
        if e['tick'] >= 28:
            e['tick'] += 13
    for e in dups:
        e['tick'] += 13
    sol['ticks'].extend(dups)
    sol['landingTick'] += 13
    write_variant(d, 'plusjump')


def main():
    runup(2)
    runup(5)
    plusjump()


if __name__ == '__main__':
    main()
