"""COPT research-oracle harness for the ParkourCalculatorMod angle solver.

RESEARCH ORACLE / BENCHMARK GROUND TRUTH ONLY. Never shipped, never in any Gradle module.
Consumes the StructureDump JSON (research/copt/data/struct-*.json), which carries the fully
compiled linear program: per-tick constant moduli mMag_t and baseArg_t, the objective vectors
cx_t/cz_t (already oriented so we always MAXIMIZE), and each position wall compiled to
sum_s coef_s * (a . u_s) <= bPrime. The only nonconvexity is the per-tick modulus |u_t| = mMag_t.

Model: per tick t decision u_t = (ax_t, az_t). An X wall reads ax across ticks, a Z wall reads az.
pos(objTick, objAxis) = objConst + S, where S = sum_t (cx_t ax_t + cz_t az_t). We MAXIMIZE S; the
true position value is objConst + S for a MAX objective and objConst - S for a MIN objective (the
objectiveVectors sign already folds the sense, so maximizing S optimizes the user's direction).

Set COPT_LICENSE_DIR to C:\\Users\\benja\\Desktop\\Coding\\98 Anderes\\copt before importing coptpy.
"""

import json
import math
import os
import time

os.environ.setdefault('COPT_LICENSE_DIR', r'C:\Users\benja\Desktop\Coding\98 Anderes\copt')

import coptpy
from coptpy import Envr, COPT, quicksum

_ENV = None


def env():
    global _ENV
    if _ENV is None:
        _ENV = Envr()
    return _ENV


def load(path):
    with open(path) as f:
        return json.load(f)


def reconstruct_from_warm(d):
    """Rebuild (ax, az) from the recorded warm yaws and verify the dump reproduces warmObj and viol."""
    n = d['numTicks']
    warm = d.get('warmYawsDeg')
    if warm is None:
        return None
    ax = [0.0] * n
    az = [0.0] * n
    for t in range(n):
        tk = d['ticks'][t]
        phi = tk['baseArg'] + math.radians(warm[t])
        ax[t] = tk['mMag'] * math.cos(phi)
        az[t] = tk['mMag'] * math.sin(phi)
    S = sum(d['ticks'][t]['cx'] * ax[t] + d['ticks'][t]['cz'] * az[t] for t in range(n))
    pos = d['objConst'] + (S if d['objMaximize'] else -S)
    worst = -1e18
    for w in d['walls']:
        lhs = sum(w['coef'][s] * (ax[s] if w['axis'] == 0 else az[s]) for s in range(n))
        viol = lhs - w['bPrime']
        if viol > worst:
            worst = viol
    return {'posLinear': pos, 'worstWallViol': worst, 'warmObj': d.get('warmObj'),
            'warmViol': d.get('warmViol'), 'modulusOk': True}


def _build_common(m, d, free=None):
    n = d['numTicks']
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]
    ax = [m.addVar(lb=-mMag[t], ub=mMag[t], name=f'ax{t}') for t in range(n)]
    az = [m.addVar(lb=-mMag[t], ub=mMag[t], name=f'az{t}') for t in range(n)]
    if free is None:
        sb = d.get('startBox')
        free = bool(sb and sb.get('startFree'))
    px = pz = None
    if free:
        sb = d['startBox']
        px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'], name='px')
        pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'], name='pz')
    for w in d['walls']:
        var = ax if w['axis'] == 0 else az
        terms = [w['coef'][s] * var[s] for s in range(n) if w['coef'][s] != 0.0]
        expr = quicksum(terms)
        if free and w['p0coef'] != 0.0:
            start_of_wall_axis = px if w['axis'] == 0 else pz
            start_ref = d['startBox']['px'] if w['axis'] == 0 else d['startBox']['pz']
            expr = expr - w['p0coef'] * (start_of_wall_axis - start_ref)
        if w['eq']:
            m.addConstr(expr == w['bPrime'], name=w['name'])
        else:
            m.addConstr(expr <= w['bPrime'], name=w['name'])
    S = quicksum(d['ticks'][t]['cx'] * ax[t] + d['ticks'][t]['cz'] * az[t] for t in range(n))
    if free:
        objaxis_start = px if d['objAxis'] == 0 else pz
        objaxis_ref = d['startBox']['px'] if d['objAxis'] == 0 else d['startBox']['pz']
        sgn = 1.0 if d['objMaximize'] else -1.0
        S = S + sgn * (objaxis_start - objaxis_ref)
    m.setObjective(S, sense=COPT.MAXIMIZE)
    return ax, az, mMag


def _pos_from_S(d, Sval):
    return d['objConst'] + (Sval if d['objMaximize'] else -Sval)


def solve_socp_disk(d, timelimit=120.0, log=0):
    """SOCP: |u_t| <= mMag_t. Convex upper bound on the constant-modulus max. Reports per-tick modulus
    slack; slack > 0 at some tick means the disk relaxation is loose there (H1 evidence)."""
    n = d['numTicks']
    m = env().createModel('socp_disk')
    m.setParam(COPT.Param.Logging, log)
    ax, az, mMag = _build_common(m, d)
    for t in range(n):
        m.addQConstr(ax[t] * ax[t] + az[t] * az[t] <= mMag[t] * mMag[t], name=f'disk{t}')
    t0 = time.time()
    m.solve()
    dt = time.time() - t0
    res = {'model': 'socp_disk', 'status': m.status, 'time': dt, 'n': n}
    if m.status in (COPT.OPTIMAL,):
        Sval = m.objval
        res['S'] = Sval
        res['pos'] = _pos_from_S(d, Sval)
        modulus = [math.hypot(ax[t].x, az[t].x) for t in range(n)]
        slack = [mMag[t] - modulus[t] for t in range(n)]
        res['modulus'] = modulus
        res['mMag'] = mMag
        res['slack'] = slack
        res['maxSlack'] = max(slack)
        res['sumSlack'] = sum(s for s in slack if s > 1e-9)
        res['throttledTicks'] = [t for t in range(n) if slack[t] > 1e-6]
        res['nThrottled'] = len(res['throttledTicks'])
    return res


def solve_socp_disk_xy(d, log=0):
    n = d['numTicks']
    m = env().createModel('socp_disk_xy')
    m.setParam(COPT.Param.Logging, log)
    ax, az, mMag = _build_common(m, d)
    for t in range(n):
        m.addQConstr(ax[t] * ax[t] + az[t] * az[t] <= mMag[t] * mMag[t], name=f'disk{t}')
    m.solve()
    if m.status != COPT.OPTIMAL:
        return None
    axv = [ax[t].x for t in range(n)]
    azv = [az[t].x for t in range(n)]
    slack = [mMag[t] - math.hypot(axv[t], azv[t]) for t in range(n)]
    return {'ax': axv, 'az': azv, 'slack': slack, 'mMag': mMag, 'S': m.objval}


def solve_qcqp_sphere(d, timelimit=300.0, log=0, nonconvex=2):
    """Nonconvex QCQP: |u_t| == mMag_t exactly (the true constant-modulus problem). COPT spatial B&B.
    Global optimum (if it converges) is the reference constant-modulus optimum."""
    n = d['numTicks']
    m = env().createModel('qcqp_sphere')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.TimeLimit, timelimit)
    try:
        m.setParam(COPT.Param.NonConvex, nonconvex)
    except Exception:
        pass
    ax, az, mMag = _build_common(m, d)
    for t in range(n):
        m.addQConstr(ax[t] * ax[t] + az[t] * az[t] == mMag[t] * mMag[t], name=f'sph{t}')
    t0 = time.time()
    m.solve()
    dt = time.time() - t0
    res = {'model': 'qcqp_sphere', 'status': m.status, 'time': dt, 'n': n}
    has_sol = False
    try:
        _ = m.objval
        has_sol = m.status in (COPT.OPTIMAL,) or (hasattr(m, 'haslinsol') and m.haslinsol)
    except Exception:
        has_sol = False
    if m.status == COPT.OPTIMAL or (m.status == COPT.TIMEOUT and _try(lambda: m.objval) is not None):
        try:
            Sval = m.objval
            res['S'] = Sval
            res['pos'] = _pos_from_S(d, Sval)
            res['bestbound'] = _try(lambda: m.getAttr(COPT.Attr.BestBnd))
            res['gap'] = _try(lambda: m.getAttr(COPT.Attr.BestGap))
            res['modulus'] = [math.hypot(ax[t].x, az[t].x) for t in range(n)]
        except Exception as e:
            res['note'] = f'objval read failed: {e!r}'
    res['posBoundFromS'] = None
    bb = _try(lambda: m.getAttr(COPT.Attr.BestBnd))
    if bb is not None:
        res['S_bound'] = bb
        res['pos_bound'] = _pos_from_S(d, bb)
    return res


def solve_shor_sdp(d, timelimit=300.0, log=0):
    """Shor/SDP relaxation of the constant-modulus QCQP. Lift M = [[1, u^T],[u, X]] PSD, dim 2n+1,
    with X_tt(x)+X_tt(z) == mMag_t^2 (the modulus equality). Objective and walls linear in u = first
    row/col of M. Reports the SDP objective bound and the eigen-spectrum / rank of M (rank 1 => tight,
    the constant-modulus optimum is recoverable; rank > 1 => genuine relaxation gap)."""
    n = d['numTicks']
    dim = 2 * n + 1
    m = env().createModel('shor_sdp')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.TimeLimit, timelimit)
    X = m.addPsdVar(dim, name='M')

    IX = lambda t: 1 + 2 * t
    IZ = lambda t: 2 + 2 * t

    def offdiag_mat(entries):
        e = []
        for (idx, c) in entries:
            e.append((0, idx, c / 2.0))
            e.append((idx, 0, c / 2.0))
        return m.addSparseMat(dim, e)

    m.addConstr((X * m.addSparseMat(dim, [(0, 0, 1.0)])) == 1.0, name='homog')
    for t in range(n):
        mm = d['ticks'][t]['mMag'] ** 2
        Amod = m.addSparseMat(dim, [(IX(t), IX(t), 1.0), (IZ(t), IZ(t), 1.0)])
        m.addConstr((X * Amod) == mm, name=f'mod{t}')
    for w in d['walls']:
        entries = []
        for s in range(n):
            c = w['coef'][s]
            if c == 0.0:
                continue
            idx = IX(s) if w['axis'] == 0 else IZ(s)
            entries.append((idx, c))
        Aw = offdiag_mat(entries)
        if w['eq']:
            m.addConstr((X * Aw) == w['bPrime'], name=w['name'])
        else:
            m.addConstr((X * Aw) <= w['bPrime'], name=w['name'])
    objentries = []
    for t in range(n):
        cx = d['ticks'][t]['cx']
        cz = d['ticks'][t]['cz']
        if cx != 0.0:
            objentries.append((IX(t), cx))
        if cz != 0.0:
            objentries.append((IZ(t), cz))
    Aobj = offdiag_mat(objentries)
    m.setObjective(X * Aobj, sense=COPT.MAXIMIZE)
    t0 = time.time()
    m.solve()
    dt = time.time() - t0
    res = {'model': 'shor_sdp', 'status': m.status, 'time': dt, 'n': n, 'dim': dim}
    if m.status == COPT.OPTIMAL or _try(lambda: m.objval) is not None:
        Sval = _try(lambda: m.objval)
        res['S'] = Sval
        res['pos'] = _pos_from_S(d, Sval) if Sval is not None else None
        vals = _try(lambda: m.getPsdValues())
        if vals is not None:
            import numpy as np
            Mfull = _to_matrix(vals, dim)
            eig = sorted(np.linalg.eigvalsh(Mfull), reverse=True)
            res['topEig'] = [float(x) for x in eig[:5]]
            res['eigSum'] = float(sum(abs(e) for e in eig))
            res['eig1_over_sum'] = float(eig[0] / sum(abs(e) for e in eig)) if eig else None
            res['rankRatio_eig2_eig1'] = float(eig[1] / eig[0]) if len(eig) > 1 and eig[0] != 0 else None
            # recover u from first row (M[0, 1:])
            u = [Mfull[0][k] for k in range(1, dim)]
            modulus = [math.hypot(u[2 * t], u[2 * t + 1]) for t in range(n)]
            res['recoveredModulus'] = modulus
            res['recoveredSlack'] = [d['ticks'][t]['mMag'] - modulus[t] for t in range(n)]
    return res


def _to_matrix(vals, dim):
    import numpy as np
    if isinstance(vals, np.ndarray) and vals.ndim == 2:
        return vals
    flat = list(vals)
    M = [[0.0] * dim for _ in range(dim)]
    if len(flat) == dim * dim:
        for i in range(dim):
            for j in range(dim):
                M[i][j] = flat[i * dim + j]
    else:
        k = 0
        for i in range(dim):
            for j in range(i, dim):
                M[i][j] = M[j][i] = flat[k]
                k += 1
    return M


def _try(fn):
    try:
        return fn()
    except Exception:
        return None
