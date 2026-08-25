"""ARCH-1 residual solve, corrected: BRANCH on the few degenerate (throttled) ticks, and at each branch
node RE-OPTIMIZE the rest with the convex disk SOCP (the degenerate ticks fixed at full modulus at the
branched angle). This is the spatial-B&B-on-the-low-dim-residual the SPEC section 4.2 describes: the
non-degenerate ticks are NOT held rigid (that is infeasible, measured), they re-solve convexly per node.

For |D| degenerate ticks and a grid of G angles each, this is G^|D| convex SOCP solves. |D| is measured
0-4, so with a modest grid + local refine this is small. Validates that the effective nonconvex dimension
is |D|, not n, and that a branch-with-convex-reopt reaches the COPT global optimum.

RESEARCH ORACLE ONLY.
"""
import math
import sys
import time
import itertools

import coptlib as C
from coptpy import COPT, quicksum


def disk_with_fixed(d, fixed, log=0):
    """Solve the disk SOCP with the ticks in `fixed` (dict t->(ax,az)) pinned; return objective pos and
    whether the free ticks come out essentially on-sphere (tight)."""
    n = d['numTicks']
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]
    m = C.env().createModel('disk_fixed')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.FeasTol, 1e-9)
    ax = [None] * n
    az = [None] * n
    free = bool(d.get('startBox') and d['startBox'].get('startFree'))
    px = pz = None
    if free:
        sb = d['startBox']
        px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'])
        pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'])
    for t in range(n):
        if t in fixed:
            fx, fz = fixed[t]
            ax[t] = m.addVar(lb=fx, ub=fx)
            az[t] = m.addVar(lb=fz, ub=fz)
        else:
            ax[t] = m.addVar(lb=-mMag[t], ub=mMag[t])
            az[t] = m.addVar(lb=-mMag[t], ub=mMag[t])
            m.addQConstr(ax[t] * ax[t] + az[t] * az[t] <= mMag[t] * mMag[t])
    for w in d['walls']:
        var = ax if w['axis'] == 0 else az
        expr = quicksum(w['coef'][s] * var[s] for s in range(n) if w['coef'][s] != 0.0)
        if free and w['p0coef'] != 0.0:
            sv = px if w['axis'] == 0 else pz
            ref = d['startBox']['px'] if w['axis'] == 0 else d['startBox']['pz']
            expr = expr - w['p0coef'] * (sv - ref)
        if w['eq']:
            m.addConstr(expr == w['bPrime'])
        else:
            m.addConstr(expr <= w['bPrime'])
    S = quicksum(d['ticks'][t]['cx'] * ax[t] + d['ticks'][t]['cz'] * az[t] for t in range(n))
    if free:
        sv = px if d['objAxis'] == 0 else pz
        ref = d['startBox']['px'] if d['objAxis'] == 0 else d['startBox']['pz']
        S = S + (1.0 if d['objMaximize'] else -1.0) * (sv - ref)
    m.setObjective(S, sense=COPT.MAXIMIZE)
    m.solve()
    if m.status != COPT.OPTIMAL:
        return None
    maxslack = 0.0
    for t in range(n):
        if t in fixed:
            continue
        sl = mMag[t] - math.hypot(ax[t].x, az[t].x)
        maxslack = max(maxslack, sl)
    pos = d['objConst'] + (m.objval if d['objMaximize'] else -m.objval)
    return {'pos': pos, 'freeMaxSlack': maxslack}


def residual_branch(d, grid=720, refine=True, throttle_tol=1e-3):
    n = d['numTicks']
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]
    disk = C.solve_socp_disk_xy(d)
    D = [t for t in range(n) if disk['slack'][t] > throttle_tol]
    if not D:
        pos = d['objConst'] + (sum(d['ticks'][t]['cx'] * disk['ax'][t] + d['ticks'][t]['cz'] * disk['az'][t]
                                   for t in range(n)) if d['objMaximize'] else 0)
        base = disk_with_fixed(d, {})
        return {'nDegenerate': 0, 'degenerate': [], 'pos': base['pos'] if base else None, 'time': 0.0}
    t0 = time.time()
    best = None
    besta = None
    angles = [2 * math.pi * k / grid for k in range(grid)]
    for combo in itertools.product(angles, repeat=len(D)):
        fixed = {D[i]: (mMag[D[i]] * math.cos(combo[i]), mMag[D[i]] * math.sin(combo[i])) for i in range(len(D))}
        r = disk_with_fixed(d, fixed)
        if r is None:
            continue
        if r['freeMaxSlack'] > 5e-3:
            continue
        if best is None or r['pos'] > best:
            best = r['pos']
            besta = combo
    if refine and besta is not None:
        for _ in range(40):
            improved = False
            for i in range(len(D)):
                for da in (0.02, -0.02, 0.005, -0.005, 0.001, -0.001):
                    combo = list(besta)
                    combo[i] += da
                    fixed = {D[j]: (mMag[D[j]] * math.cos(combo[j]), mMag[D[j]] * math.sin(combo[j])) for j in range(len(D))}
                    r = disk_with_fixed(d, fixed)
                    if r and r['freeMaxSlack'] <= 5e-3 and r['pos'] > best + 1e-9:
                        best = r['pos']
                        besta = combo
                        improved = True
            if not improved:
                break
    return {'nDegenerate': len(D), 'degenerate': D, 'pos': best, 'time': time.time() - t0,
            'bestAngles': list(besta) if besta else None}


if __name__ == '__main__':
    caps = sys.argv[1:] or ['j021-rinav1-01', 'j008b-2jump', 'loopmm-3jump-lands']
    for cap in caps:
        d = C.load(f'data/struct-{cap}.json')
        full = C.solve_qcqp_sphere(d, timelimit=120)
        fp = full.get('pos')
        r = residual_branch(d, grid=720)
        rp = r.get('pos')
        gap = abs(fp - rp) if (fp is not None and rp is not None) else None
        print(f'{cap:22s} n={d["numTicks"]:3d} |D|={r["nDegenerate"]} {r["degenerate"]}  '
              f'fullQCQP={fp}  branchResidual={rp}  gap={gap}  time={r["time"]:.2f}s')
