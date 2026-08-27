import copy
import json

SRC = r'C:\Users\benja\Desktop\Games\MultiMC\instances\1.8.9\.minecraft\parkourcalculator\hpk_human\d12\j154_1bm_Head_Butterfly_Neo.json'
OUT = 'data/j154-2jump.json'
PROBE_ON = 'data/j154-2jump-probe-w-sprint.json'
PROBE_OFF = 'data/j154-2jump-probe-w-nosprint.json'

CUT_LO = 16
CUT_HI = 28
SHIFT = 13


def main():
    with open(SRC) as f:
        d = json.load(f)

    d['rows'] = [r for i, r in enumerate(d['rows']) if not (CUT_LO <= i <= CUT_HI)]
    d['debug'] = [e for i, e in enumerate(d['debug']) if not (CUT_LO + 1 <= i <= CUT_HI + 1)]

    sol = d['angleSolver']
    kept = []
    for entry in sol['ticks']:
        t = entry['tick']
        if CUT_LO <= t <= CUT_HI:
            continue
        if t > CUT_HI:
            if not entry.get('constraints') and not entry.get('override'):
                continue
            entry['tick'] = t - SHIFT
        if entry['tick'] == 15:
            entry['constraints'] = [c for c in entry.get('constraints', []) if c.get('field') != 'DF']
        kept.append(entry)
    sol['ticks'] = kept
    sol['landingTick'] = 26
    sol.pop('result', None)

    with open(OUT, 'w') as f:
        json.dump(d, f)
    print('wrote', OUT, 'rows', len(d['rows']), 'debug', len(d['debug']),
          'specTicks', sorted(e['tick'] for e in kept))

    for path, sprint in ((PROBE_ON, True), (PROBE_OFF, False)):
        p = copy.deepcopy(d)
        for e in p['debug']:
            e['moveForward'] = 0.98
            e['moveStrafe'] = 0.0
            e['sprinting'] = sprint
        with open(path, 'w') as f:
            json.dump(p, f)
        print('wrote', path)


if __name__ == '__main__':
    main()
