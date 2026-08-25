"""Proof-of-concept for the SPEC ARCH-1 reduction: convex dual/SOCP determines the non-degenerate ticks;
only a low-dimensional residual over the vanishing-costate (throttled) ticks needs nonconvex solving.

Test: solve the SOCP disk; identify the throttled ticks D (|u_t| < m_t); FIX every non-degenerate tick at
its disk direction projected to the circle; then solve ONLY the residual (the ticks in D free on their
circles) with the nonconvex constant-modulus QCQP. If the residual solve reaches the FULL COPT global
optimum, the decomposition is validated: the non-degenerate ticks are correctly determined by the convex
relaxation, and the effective nonconvex dimension is |D| (measured 0-4), not n.

RESEARCH ORACLE ONLY.
"""
import math
import sys
import time

import coptlib as C
from coptpy import COPT


def solve_residual(d, throttle_tol=1e-3, log=0):
    n = d['numTicks']
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]

    disk = C.solve_socp_disk(d)
    if 'modulus' not in disk:
        return {'status': 'disk-failed', 'diskStatus': disk['status']}
    modulus = disk['modulus']
    slack = disk['slack']
    D = [t for t in range(n) if slack[t] > throttle_tol]

    m = C.env().createModel('residual')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.TimeLimit, 60)
    try:
        m.setParam(COPT.Param.NonConvex, 2)
    except Exception:
        pass

    ax = [None] * n
    az = [None] * n
    free = bool(d.get('startBox') and d['startBox'].get('startFree'))
    px = pz = None
    if free:
        sb = d['startBox']
        px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'], name='px')
        pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'], name='pz')

    for t in range(n):
        if t in D:
            ax[t] = m.addVar(lb=-mMag[t], ub=mMag[t], name=f'ax{t}')
            az[t] = m.addVar(lb=-mMag[t], ub=mMag[t], name=f'az{t}')
            m.addQConstr(ax[t] * ax[t] + az[t] * az[t] == mMag[t] * mMag[t], name=f'sph{t}')
        else:
            mag = math.hypot(disk_x(disk, t), disk_z(disk, t))
            if mag < 1e-15:
                fx, fz = mMag[t], 0.0
            else:
                fx = mMag[t] * disk_x(disk, t) / mag
                fz = mMag[t] * disk_z(disk, t) / mag
            ax[t] = m.addVar(lb=fx, ub=fx, name=f'ax{t}')
            az[t] = m.addVar(lb=fz, ub=fz, name=f'az{t}')

    from coptpy import quicksum
    for w in d['walls']:
        var = ax if w['axis'] == 0 else az
        expr = quicksum(w['coef'][s] * var[s] for s in range(n) if w['coef'][s] != 0.0)
        if free and w['p0coef'] != 0.0:
            sv = px if w['axis'] == 0 else pz
            ref = d['startBox']['px'] if w['axis'] == 0 else d['startBox']['pz']
            expr = expr - w['p0coef'] * (sv - ref)
        if w['eq']:
            m.addConstr(expr == w['bPrime'], name=w['name'])
        else:
            m.addConstr(expr <= w['bPrime'], name=w['name'])
    S = quicksum(d['ticks'][t]['cx'] * ax[t] + d['ticks'][t]['cz'] * az[t] for t in range(n))
    if free:
        sv = px if d['objAxis'] == 0 else pz
        ref = d['startBox']['px'] if d['objAxis'] == 0 else d['startBox']['pz']
        S = S + (1.0 if d['objMaximize'] else -1.0) * (sv - ref)
    m.setObjective(S, sense=COPT.MAXIMIZE)

    t0 = time.time()
    m.solve()
    dt = time.time() - t0
    res = {'model': 'residual', 'status': m.status, 'time': dt, 'nDegenerate': len(D), 'degenerate': D,
           'diskPos': disk.get('pos'), 'diskThrottled': disk.get('nThrottled')}
    if m.status == COPT.OPTIMAL:
        S = m.objval
        res['pos'] = d['objConst'] + (S if d['objMaximize'] else -S)
    return res


def disk_x(disk, t):
    return disk['modulus'][t] * math.cos(disk_angle(disk, t))


def disk_z(disk, t):
    return disk['modulus'][t] * math.sin(disk_angle(disk, t))


_ANG = {}


def disk_angle(disk, t):
    return _ANG[(id(disk), t)]


def solve_residual2(d, throttle_tol=1e-3, log=0):
    """Same as solve_residual but keeps the disk (ax,az) directly rather than reconstructing angle."""
    n = d['numTicks']
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]
    disk = C.solve_socp_disk_xy(d)
    if disk is None:
        return {'status': 'disk-failed'}
    dax, daz, slack = disk['ax'], disk['az'], disk['slack']
    D = [t for t in range(n) if slack[t] > throttle_tol]

    m = C.env().createModel('residual')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.TimeLimit, 60)
    try:
        m.setParam(COPT.Param.NonConvex, 2)
    except Exception:
        pass
    from coptpy import quicksum
    ax = [None] * n
    az = [None] * n
    free = bool(d.get('startBox') and d['startBox'].get('startFree'))
    px = pz = None
    if free:
        sb = d['startBox']
        px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'], name='px')
        pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'], name='pz')
    for t in range(n):
        if t in D:
            ax[t] = m.addVar(lb=-mMag[t], ub=mMag[t], name=f'ax{t}')
            az[t] = m.addVar(lb=-mMag[t], ub=mMag[t], name=f'az{t}')
            m.addQConstr(ax[t] * ax[t] + az[t] * az[t] == mMag[t] * mMag[t])
        else:
            mag = math.hypot(dax[t], daz[t])
            fx = mMag[t] * (dax[t] / mag) if mag > 1e-15 else mMag[t]
            fz = mMag[t] * (daz[t] / mag) if mag > 1e-15 else 0.0
            ax[t] = m.addVar(lb=fx, ub=fx)
            az[t] = m.addVar(lb=fz, ub=fz)
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
    t0 = time.time()
    m.solve()
    dt = time.time() - t0
    res = {'status': m.status, 'time': dt, 'nDegenerate': len(D), 'degenerate': D}
    if m.status == COPT.OPTIMAL:
        Sv = m.objval
        res['pos'] = d['objConst'] + (Sv if d['objMaximize'] else -Sv)
    return res


if __name__ == '__main__':
    caps = sys.argv[1:] or ['j021-rinav1-01', 'j008b-2jump', 'loopmm-3jump-lands']
    for cap in caps:
        d = C.load(f'data/struct-{cap}.json')
        full = C.solve_qcqp_sphere(d, timelimit=120)
        res = solve_residual2(d)
        fp = full.get('pos')
        rp = res.get('pos')
        gap = abs(fp - rp) if (fp is not None and rp is not None) else None
        print(f'{cap:22s} n={d["numTicks"]:3d} degenerate={res.get("nDegenerate")}/{d["numTicks"]} '
              f'{res.get("degenerate")}  fullQCQP={fp}  residualSolve={rp}  '
              f'gap={gap}  residualTime={res.get("time"):.3f}s')
