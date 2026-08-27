import json, glob, os
from collections import Counter

HUMAN = r'C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/.claude/worktrees/stratlib/core/src/test/resources/captures/hpk_human'
HPK = r'C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/hpk'

MOVE = ['W', 'A', 'S', 'D']

def combo(keys):
    ks = set(k.upper() for k in keys)
    return ''.join(k for k in MOVE if k in ks) or 'NONE'

def analyze(path):
    d = json.load(open(path, encoding='utf-8', errors='replace'))
    rows = d.get('rows') or []
    n = len(rows)
    if n == 0:
        return None
    combos = [combo(r.get('keys', [])) for r in rows]
    yaws = [round(float(r.get('yaw', 0.0)), 4) for r in rows]
    sprint = ['SPRINT' in [k.upper() for k in r.get('keys', [])] for r in rows]
    jump = ['JUMP' in [k.upper() for k in r.get('keys', [])] or 'SPACE' in [k.upper() for k in r.get('keys', [])] for r in rows]
    sneak = ['SNEAK' in [k.upper() for k in r.get('keys', [])] or 'SHIFT' in [k.upper() for k in r.get('keys', [])] for r in rows]

    # combo edges
    edges = sum(1 for i in range(1, n) if combos[i] != combos[i - 1])
    # run lengths (dwell) of combos, ignoring leading/trailing
    runs = []
    i = 0
    while i < n:
        j = i
        while j < n and combos[j] == combos[i]:
            j += 1
        runs.append(j - i)
        i = j
    min_dwell = min(runs) if runs else 0
    interior_runs = runs[1:-1] if len(runs) > 2 else []
    min_interior_dwell = min(interior_runs) if interior_runs else (min(runs) if runs else 0)

    # sprint pattern
    sprint_engages = sum(1 for i in range(1, n) if sprint[i] and not sprint[i - 1]) + (1 if sprint and sprint[0] else 0)
    sprint_drops = sum(1 for i in range(1, n) if not sprint[i] and sprint[i - 1])
    sprint_monotone = sprint_drops == 0

    # yaw / turn structure: find last jump tick, count distinct yaws BEFORE it (setup)
    jump_ticks = [i for i in range(n) if jump[i]]
    last_jump = jump_ticks[-1] if jump_ticks else n - 1
    setup_yaws = yaws[:last_jump + 1]
    # a no-turn setup: yaw constant from tick1..last jump (tick0 free)
    setup_body = setup_yaws[1:] if len(setup_yaws) > 1 else setup_yaws
    distinct_setup = len(set(setup_body))
    yaw_changes_setup = sum(1 for i in range(1, len(setup_yaws)) if setup_yaws[i] != setup_yaws[i - 1])
    total_yaw_changes = sum(1 for i in range(1, n) if yaws[i] != yaws[i - 1])

    return {
        'n': n, 'edges': edges, 'min_dwell': min_dwell, 'min_interior_dwell': min_interior_dwell,
        'sprint_engages': sprint_engages, 'sprint_monotone': sprint_monotone, 'sprint_drops': sprint_drops,
        'distinct_setup_yaw': distinct_setup, 'yaw_changes_setup': yaw_changes_setup,
        'total_yaw_changes': total_yaw_changes, 'n_jumps': len(jump_ticks),
        'any_sneak': any(sneak), 'combos': combos,
    }

def run(label, files):
    print('===', label, len(files), 'saves ===')
    rows = []
    for f in files:
        try:
            r = analyze(f)
        except Exception as e:
            continue
        if r:
            r['name'] = os.path.basename(f)
            rows.append(r)
    if not rows:
        print('  none'); return rows
    def dist(key):
        c = Counter(r[key] for r in rows)
        return dict(sorted(c.items()))
    print(' edges dist:', dist('edges'))
    print(' n_jumps dist:', dist('n_jumps'))
    print(' min_interior_dwell dist:', dist('min_interior_dwell'))
    print(' sprint_engages dist:', dist('sprint_engages'))
    print(' sprint_monotone:', sum(r['sprint_monotone'] for r in rows), '/', len(rows))
    print(' distinct_setup_yaw dist:', dist('distinct_setup_yaw'))
    print(' yaw_changes_setup dist:', dist('yaw_changes_setup'))
    print(' any_sneak:', sum(r['any_sneak'] for r in rows))
    # easy-class membership: edges<=6, min_interior_dwell>=1 (no 0), sprint monotone or single-drop,
    # setup near-constant yaw (distinct_setup_yaw<=2 => no-turn-ish)
    def easy(r):
        return (r['edges'] <= 6 and r['sprint_engages'] <= 1 and r['distinct_setup_yaw'] <= 2)
    def noturn(r):
        return r['distinct_setup_yaw'] <= 1
    ne = sum(1 for r in rows if easy(r))
    nn = sum(1 for r in rows if noturn(r))
    print(' EASY-CLASS (edges<=6 & 1 sprint-engage & setup-yaw<=2 distinct):', ne, '/', len(rows), '=', round(100.0 * ne / len(rows), 1), '%')
    print(' NO-TURN setup (setup-yaw exactly constant):', nn, '/', len(rows), '=', round(100.0 * nn / len(rows), 1), '%')
    # sorted edges for the tail
    worst = sorted(rows, key=lambda r: -r['edges'])[:8]
    print(' highest-edge saves:', [(r['name'][:30], r['edges'], 'setupYaw', r['distinct_setup_yaw']) for r in worst])
    return rows

if __name__ == '__main__':
    hu = glob.glob(os.path.join(HUMAN, '**', '*.json'), recursive=True)
    hk = glob.glob(os.path.join(HPK, '**', '*.json'), recursive=True)
    run('hpk_human (byte-exact witnesses)', hu)
    print()
    run('hpk captures', hk)
