"""M0 falsification pre-check driver (issue #422): fixed-schedule inner solves.

Runs the fold + margins loop on a fixed-input capture with free per-tick facings:
solve the disk SOCP on the (folded) struct, decode per-tick yaws, replay byte-exact
through NoTurnReplay, extract the gate pattern from the replay velocities, re-export
the folded struct via StructureDump (PKC_STRUCT_ZERO), translate walls with per-wall
signed byte-minus-linear margins at the replayed anchor, iterate.

Research-only. Never shipped.
"""

import argparse
import json
import math
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import coptlib
from coptlib import COPT, quicksum

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
DATA = os.path.join(REPO, 'research', 'copt', 'data')


def wrap(deg):
    x = math.fmod(deg + 180.0, 360.0)
    if x < 0:
        x += 360.0
    return x - 180.0


def run_java(main, envvars):
    cp = open(os.path.join(REPO, 'core', 'build', 'test-classpath.txt')).read().strip()
    env = dict(os.environ)
    env.update(envvars)
    r = subprocess.run(['java', '-cp', cp, 'org.junit.runner.JUnitCore',
                        'de.legoshi.parkourcalc.anglesolver.' + main],
                       cwd=REPO, env=env, capture_output=True, text=True, timeout=300)
    if 'OK (1 test)' not in r.stdout:
        raise RuntimeError(f'{main} failed:\n{r.stdout}\n{r.stderr}')
    return r.stdout


def replay(capture, decode_path, out_path):
    run_java('NoTurnReplay', {
        'PKC_NOTURN_CAPTURE': capture,
        'PKC_NOTURN_FILE': decode_path,
        'PKC_NOTURN_OUT': out_path,
    })
    return json.load(open(out_path))


def export_struct(capture, pattern_path, out_path):
    ev = {'PKC_STRUCT_FILE': capture, 'PKC_STRUCT_OUT': out_path}
    if pattern_path:
        ev['PKC_STRUCT_ZERO'] = pattern_path
    run_java('StructureDump', ev)
    return json.load(open(out_path))


def solve_disk(d, margins, log=0, extra_tighten=None, chords=None):
    n = d['numTicks']
    m = coptlib.env().createModel('inner_fixed')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.FeasTol, 1e-9)
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]
    ax = [m.addVar(lb=-mMag[t], ub=mMag[t], name=f'ax{t}') for t in range(n)]
    az = [m.addVar(lb=-mMag[t], ub=mMag[t], name=f'az{t}') for t in range(n)]
    sb = d.get('startBox')
    free = bool(sb and sb.get('startFree'))
    px = pz = None
    if free:
        px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'], name='px')
        pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'], name='pz')
    for w in d['walls']:
        var = ax if w['axis'] == 0 else az
        expr = quicksum(w['coef'][s] * var[s] for s in range(n) if w['coef'][s] != 0.0)
        if free and w['p0coef'] != 0.0:
            p = px if w['axis'] == 0 else pz
            ref = sb['px'] if w['axis'] == 0 else sb['pz']
            expr = expr - w['p0coef'] * (p - ref)
        b = w['bPrime'] - margins.get(w['name'], 0.0)
        if extra_tighten:
            b -= extra_tighten.get(w['name'], 0.0)
        if w['eq']:
            m.addConstr(expr == b, name=w['name'])
        else:
            m.addConstr(expr <= b, name=w['name'])
    for t in range(n):
        m.addQConstr(ax[t] * ax[t] + az[t] * az[t] <= mMag[t] * mMag[t], name=f'disk{t}')
    if chords:
        for t, (mu, delta) in chords.items():
            rhs = mMag[t] * math.cos(delta)
            m.addConstr(math.cos(mu) * ax[t] + math.sin(mu) * az[t] >= rhs, name=f'chord{t}')
    S = quicksum(d['ticks'][t]['cx'] * ax[t] + d['ticks'][t]['cz'] * az[t] for t in range(n))
    if free:
        p = px if d['objAxis'] == 0 else pz
        ref = sb['px'] if d['objAxis'] == 0 else sb['pz']
        sgn = 1.0 if d['objMaximize'] else -1.0
        S = S + sgn * (p - ref)
    m.setObjective(S, sense=COPT.MAXIMIZE)
    m.solve()
    if m.status != COPT.OPTIMAL:
        return {'status': m.status}
    res = {
        'status': m.status,
        'S': m.objval,
        'pos': d['objConst'] + (m.objval if d['objMaximize'] else -m.objval),
        'ax': [ax[t].x for t in range(n)],
        'az': [az[t].x for t in range(n)],
        'px': px.x if free else (sb['px'] if sb else d['startPos'][0]),
        'pz': pz.x if free else (sb['pz'] if sb else d['startPos'][1]),
    }
    res['slack'] = [mMag[t] - math.hypot(res['ax'][t], res['az'][t]) for t in range(n)]
    res['interior'] = [t for t in range(n) if res['slack'][t] > 1e-6]
    return res


def solve_full_modulus(d, margins, tol=1e-4, max_iter=30, delta0=1.0472, shrink=0.7,
                       chord_min_mmag=0.0):
    chords = {}
    sol = solve_disk(d, margins)
    if sol['status'] != COPT.OPTIMAL:
        return sol, 0
    it = 0
    while it < max_iter:
        it += 1
        n = d['numTicks']
        worst = None
        for t in range(n):
            mm = d['ticks'][t]['mMag']
            if mm <= 0.0 or mm < chord_min_mmag:
                continue
            if sol['slack'][t] > tol * max(mm, 0.026):
                if worst is None or sol['slack'][t] > sol['slack'][worst]:
                    worst = t
        if worst is None:
            break
        new_chords = dict(chords)
        for t in range(d['numTicks']):
            mm = d['ticks'][t]['mMag']
            if mm <= 0.0 or mm < chord_min_mmag:
                continue
            frac_slack = sol['slack'][t] / max(mm, 1e-12)
            ux, uz = sol['ax'][t], sol['az'][t]
            if math.hypot(ux, uz) < 1e-12:
                mu = d['ticks'][t]['baseArg']
            else:
                mu = math.atan2(uz, ux)
            if t in chords:
                old_mu, old_delta = chords[t]
                new_delta = old_delta * shrink if frac_slack > tol else old_delta
                new_chords[t] = (mu, max(new_delta, 2e-5))
            elif frac_slack > tol:
                new_chords[t] = (mu, delta0)
        cand = solve_disk(d, margins, chords=new_chords)
        if cand['status'] != COPT.OPTIMAL:
            loosened = {t: (mu, min(dl / shrink, delta0)) for t, (mu, dl) in new_chords.items()}
            cand = solve_disk(d, margins, chords=loosened)
            if cand['status'] != COPT.OPTIMAL:
                break
            new_chords = loosened
        chords = new_chords
        sol = cand
    return sol, it


def solve_sphere(d, margins, warm=None, timelimit=120.0, log=0):
    n = d['numTicks']
    m = coptlib.env().createModel('inner_sphere')
    m.setParam(COPT.Param.Logging, log)
    m.setParam(COPT.Param.FeasTol, 1e-9)
    m.setParam(COPT.Param.TimeLimit, timelimit)
    try:
        m.setParam(COPT.Param.NonConvex, 2)
    except Exception:
        pass
    mMag = [d['ticks'][t]['mMag'] for t in range(n)]
    ax = [m.addVar(lb=-mMag[t], ub=mMag[t], name=f'ax{t}') for t in range(n)]
    az = [m.addVar(lb=-mMag[t], ub=mMag[t], name=f'az{t}') for t in range(n)]
    sb = d.get('startBox')
    free = bool(sb and sb.get('startFree'))
    px = pz = None
    if free:
        px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'], name='px')
        pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'], name='pz')
    for w in d['walls']:
        var = ax if w['axis'] == 0 else az
        expr = quicksum(w['coef'][s] * var[s] for s in range(n) if w['coef'][s] != 0.0)
        if free and w['p0coef'] != 0.0:
            p = px if w['axis'] == 0 else pz
            ref = sb['px'] if w['axis'] == 0 else sb['pz']
            expr = expr - w['p0coef'] * (p - ref)
        b = w['bPrime'] - margins.get(w['name'], 0.0)
        if w['eq']:
            m.addConstr(expr == b, name=w['name'])
        else:
            m.addConstr(expr <= b, name=w['name'])
    for t in range(n):
        m.addQConstr(ax[t] * ax[t] + az[t] * az[t] == mMag[t] * mMag[t], name=f'sph{t}')
    S = quicksum(d['ticks'][t]['cx'] * ax[t] + d['ticks'][t]['cz'] * az[t] for t in range(n))
    if free:
        p = px if d['objAxis'] == 0 else pz
        ref = sb['px'] if d['objAxis'] == 0 else sb['pz']
        sgn = 1.0 if d['objMaximize'] else -1.0
        S = S + sgn * (p - ref)
    m.setObjective(S, sense=COPT.MAXIMIZE)
    if warm is not None:
        try:
            ms = {f'ax{t}': warm['ax'][t] for t in range(n)}
            ms.update({f'az{t}': warm['az'][t] for t in range(n)})
            if free:
                ms['px'] = warm['px']
                ms['pz'] = warm['pz']
            m.setMipStart([m.getVarByName(k) for k in ms], list(ms.values()))
            m.loadMipStart()
        except Exception:
            pass
    m.solve()
    has = m.status == COPT.OPTIMAL
    if not has:
        try:
            has = m.objval is not None
        except Exception:
            has = False
    if not has:
        return {'status': m.status}
    res = {
        'status': COPT.OPTIMAL,
        'S': m.objval,
        'pos': d['objConst'] + (m.objval if d['objMaximize'] else -m.objval),
        'ax': [ax[t].x for t in range(n)],
        'az': [az[t].x for t in range(n)],
        'px': px.x if free else (sb['px'] if sb else d['startPos'][0]),
        'pz': pz.x if free else (sb['pz'] if sb else d['startPos'][1]),
    }
    res['slack'] = [mMag[t] - math.hypot(res['ax'][t], res['az'][t]) for t in range(n)]
    res['interior'] = [t for t in range(n) if abs(res['slack'][t]) > 1e-6]
    res['modCheat'] = max(abs(s) for s in res['slack'])
    return res


def decode_yaws(d, sol, prev_yaws=None):
    n = d['numTicks']
    yaws = [0.0] * n
    last = 0.0
    for t in range(n):
        mm = d['ticks'][t]['mMag']
        ux, uz = sol['ax'][t], sol['az'][t]
        if mm <= 0.0 or math.hypot(ux, uz) < 1e-12:
            yaws[t] = prev_yaws[t] if prev_yaws else last
        else:
            yaws[t] = wrap(math.degrees(math.atan2(uz, ux) - d['ticks'][t]['baseArg']))
        last = yaws[t]
    return yaws


def gate_zero(vx, vz, d):
    if d['perAxisInertia']:
        thr = d['inertiaThreshold']
        if abs(vx) < thr:
            vx = 0.0
        if abs(vz) < thr:
            vz = 0.0
    else:
        if vx * vx + vz * vz < 9.0e-6:
            vx = 0.0
            vz = 0.0
    return vx, vz


def track_decode(d, sol, pattern, kp=0.3, kv=1.0, prev_yaws=None):
    n = d['numTicks']
    zx = pattern['zeroX'] if pattern else [False] * n
    zz = pattern['zeroZ'] if pattern else [False] * n
    vix, viz = d['startBox']['vx'], d['startBox']['vz']
    pix, piz = sol['px'], sol['pz']
    vrx, vrz = vix, viz
    prx, prz = pix, piz
    yaws = [0.0] * n
    last = prev_yaws[0] if prev_yaws else 0.0
    for t in range(n):
        f4 = d['ticks'][t]['f4']
        mm = d['ticks'][t]['mMag']
        if zx[t]:
            vix = 0.0
        if zz[t]:
            viz = 0.0
        vrx, vrz = gate_zero(vrx, vrz, d)
        tx = sol['ax'][t] + kv * (vix - vrx) + kp * (pix - prx)
        tz = sol['az'][t] + kv * (viz - vrz) + kp * (piz - prz)
        if mm <= 0.0:
            yaws[t] = prev_yaws[t] if prev_yaws else last
            urx = urz = 0.0
        else:
            norm = math.hypot(tx, tz)
            if norm < 1e-12:
                yaws[t] = prev_yaws[t] if prev_yaws else last
                phi = d['ticks'][t]['baseArg'] + math.radians(yaws[t])
            else:
                phi = math.atan2(tz, tx)
                yaws[t] = wrap(math.degrees(phi - d['ticks'][t]['baseArg']))
            urx = mm * math.cos(phi)
            urz = mm * math.sin(phi)
        last = yaws[t]
        vix += sol['ax'][t]
        viz += sol['az'][t]
        pix += vix
        piz += viz
        vix *= f4
        viz *= f4
        vrx += urx
        vrz += urz
        prx += vrx
        prz += vrz
        vrx *= f4
        vrz *= f4
    dev = math.hypot(pix - prx, piz - prz)
    return yaws, dev


def extract_pattern(d, rep):
    n = d['numTicks']
    thr = d['inertiaThreshold']
    per_axis = d['perAxisInertia']
    vx = rep['velX']
    vz = rep['velZ']
    zx = [False] * n
    zz = [False] * n
    for t in range(n):
        if per_axis:
            zx[t] = abs(vx[t]) < thr
            zz[t] = abs(vz[t]) < thr
        else:
            both = vx[t] * vx[t] + vz[t] * vz[t] < 9.0e-6
            zx[t] = both
            zz[t] = both
    return {'zeroX': zx, 'zeroZ': zz}


def realized_u(d, yaws):
    n = d['numTicks']
    ux = [0.0] * n
    uz = [0.0] * n
    for t in range(n):
        mm = d['ticks'][t]['mMag']
        phi = d['ticks'][t]['baseArg'] + math.radians(yaws[t])
        ux[t] = mm * math.cos(phi)
        uz[t] = mm * math.sin(phi)
    return ux, uz


def compute_margins(d, yaws, px, pz, rep):
    n = d['numTicks']
    ux, uz = realized_u(d, yaws)
    cons = {c['name']: c for c in d['constraints']}
    margins = {}
    for w in d['walls']:
        c = cons.get(w['name'])
        if c is None or w['eq']:
            continue
        var = ux if w['axis'] == 0 else uz
        lin = sum(w['coef'][t] * var[t] for t in range(n))
        if w['p0coef'] != 0.0:
            p = px if w['axis'] == 0 else pz
            ref = d['startBox']['px'] if w['axis'] == 0 else d['startBox']['pz']
            lin -= w['p0coef'] * (p - ref)
        vlin = lin - w['bPrime']
        pos = rep['posX'] if c['mode'] == 'X' else rep['posZ']
        val = pos[c['t1']]
        if c.get('t2') is not None:
            val += (1.0 if c['op'] == 'PLUS' else -1.0) * pos[c['t2']]
        if c['cmp'] == 'GE':
            vbyte = c['rhs'] - val
        else:
            vbyte = val - c['rhs']
        margins[w['name']] = vbyte - vlin
    return margins


def byte_viol(d, rep, c):
    pos = rep['posX'] if c['mode'] == 'X' else rep['posZ']
    val = pos[c['t1']]
    if c.get('t2') is not None:
        val += (1.0 if c['op'] == 'PLUS' else -1.0) * pos[c['t2']]
    if c['cmp'] == 'GE':
        return c['rhs'] - val
    if c['cmp'] == 'LE':
        return val - c['rhs']
    return abs(val - c['rhs'])


def translate_window(d, rep, clearance=0.0):
    sb = d.get('startBox') or {}
    if not sb.get('startFree'):
        return None
    lo = {0: sb['pxLo'] - rep['px'], 1: sb['pzLo'] - rep['pz']}
    hi = {0: sb['pxHi'] - rep['px'], 1: sb['pzHi'] - rep['pz']}
    for c in d['constraints']:
        if c['mode'] not in ('X', 'Z'):
            if byte_viol(d, rep, c) > 0.0:
                return None
            continue
        axis = 0 if c['mode'] == 'X' else 1
        if c.get('t2') is not None:
            if byte_viol(d, rep, c) > 0.0:
                return None
            continue
        v = byte_viol(d, rep, c)
        if c['cmp'] == 'GE':
            lo[axis] = max(lo[axis], v + clearance)
        elif c['cmp'] == 'LE':
            hi[axis] = min(hi[axis], -v - clearance)
        else:
            return None
    if lo[0] > hi[0] or lo[1] > hi[1]:
        return {'empty': True, 'lo': lo, 'hi': hi}
    return {'empty': False,
            'dpx': 0.5 * (lo[0] + hi[0]), 'dpz': 0.5 * (lo[1] + hi[1]),
            'lo': lo, 'hi': hi}


def polish_translate(d, dec, rep, capture, tag):
    win = translate_window(d, rep)
    if win is None:
        print('    polish: not translatable (pair/F wall violated or pinned start)')
        return None
    if win['empty']:
        print(f'    polish: empty translation window lo={win["lo"]} hi={win["hi"]}')
        return None
    dec2 = dict(dec)
    dec2['px'] = rep['px'] + win['dpx']
    dec2['pz'] = rep['pz'] + win['dpz']
    dec_path = os.path.join(DATA, f'{tag}-polish-decode.json')
    json.dump(dec2, open(dec_path, 'w'))
    rep_path = os.path.join(DATA, f'{tag}-polish-replay.json')
    rep2 = replay(capture, dec_path, rep_path)
    print(f'    polish: dpx={win["dpx"]:.3e} dpz={win["dpz"]:.3e} '
          f'obj={rep2["objective"]:.9f} maxViol={rep2["maxViol"]:.6g}')
    if rep2['maxViol'] == 0.0:
        return {'objective': rep2['objective'], 'decode': dec_path, 'replay': rep_path}
    return None


BUCKET_DEG = 360.0 / 65536.0


def vel_couplings(d, pattern):
    n = d['numTicks']
    fPre = [1.0] * (n + 1)
    for t in range(n):
        fPre[t + 1] = fPre[t] * d['ticks'][t]['f4']
    zNext = []
    for a in range(2):
        za = (pattern['zeroX'] if a == 0 else pattern['zeroZ']) if pattern else [False] * n
        nx = [n + 1] * n
        nxt = n + 1
        for t in range(n - 1, -1, -1):
            nx[t] = nxt
            if za[t]:
                nxt = t
        zNext.append(nx)
    return fPre, zNext


def walk_step(d, yaws, rep, pattern, W=2, budget=6, gate_band=5e-4):
    n = d['numTicks']
    ux, uz = realized_u(d, yaws)
    cons = {c['name']: c for c in d['constraints']}
    thr = d['inertiaThreshold']
    fPre, zNext = vel_couplings(d, pattern)
    m = coptlib.env().createModel('walk')
    m.setParam(COPT.Param.Logging, 0)
    b = [m.addVar(lb=-W, ub=W, vtype=COPT.INTEGER, name=f'b{t}') for t in range(n)]
    babs = [m.addVar(lb=0, ub=W, name=f'ba{t}') for t in range(n)]
    for t in range(n):
        m.addConstr(babs[t] >= b[t])
        m.addConstr(babs[t] >= -b[t])
    m.addConstr(quicksum(babs) <= budget)
    sb = d.get('startBox') or {}
    free = bool(sb.get('startFree'))
    if free:
        dpx = m.addVar(lb=sb['pxLo'] - rep['px'], ub=sb['pxHi'] - rep['px'], name='dpx')
        dpz = m.addVar(lb=sb['pzLo'] - rep['pz'], ub=sb['pzHi'] - rep['pz'], name='dpz')
    smin = m.addVar(lb=-COPT.INFINITY, name='smin')
    step = math.radians(BUCKET_DEG)
    dux = [-uz[t] * step for t in range(n)]
    duz = [ux[t] * step for t in range(n)]
    for w in d['walls']:
        c = cons.get(w['name'])
        if c is None or w['eq']:
            continue
        vb = byte_viol(d, rep, c)
        du = dux if w['axis'] == 0 else duz
        expr = quicksum(w['coef'][t] * du[t] * b[t] for t in range(n) if w['coef'][t] != 0.0)
        if free and w['p0coef'] != 0.0:
            dp = dpx if w['axis'] == 0 else dpz
            expr = expr - w['p0coef'] * dp
        m.addConstr(-vb - expr >= smin, name=w['name'])
    per_axis = d['perAxisInertia']
    for a in range(2):
        vrep = rep['velX'] if a == 0 else rep['velZ']
        dus = dux if a == 0 else duz
        for t in range(1, n):
            v = vrep[t]
            dv = quicksum((fPre[t] / fPre[s]) * dus[s] * b[s]
                          for s in range(t) if zNext[a][s] >= t and dus[s] != 0.0)
            eff_thr = thr if per_axis else 3e-3
            if abs(v) < eff_thr:
                m.addConstr(v + dv <= eff_thr - 1e-5)
                m.addConstr(v + dv >= -eff_thr + 1e-5)
            elif abs(abs(v) - eff_thr) < gate_band:
                if v > 0:
                    m.addConstr(v + dv >= eff_thr + 1e-5)
                else:
                    m.addConstr(v + dv <= -eff_thr - 1e-5)
    m.setObjective(smin - 1e-6 * quicksum(babs), sense=COPT.MAXIMIZE)
    m.solve()
    if m.status != COPT.OPTIMAL:
        return None
    return {
        'smin': m.objval,
        'b': [int(round(b[t].x)) for t in range(n)],
        'dpx': dpx.x if free else 0.0,
        'dpz': dpz.x if free else 0.0,
    }


def slp_step(d, yaws, rep, pattern, trust_deg=1.5, gate_band=5e-4):
    n = d['numTicks']
    ux, uz = realized_u(d, yaws)
    cons = {c['name']: c for c in d['constraints']}
    thr = d['inertiaThreshold']
    fPre, zNext = vel_couplings(d, pattern)
    m = coptlib.env().createModel('slp')
    m.setParam(COPT.Param.Logging, 0)
    tr = math.radians(trust_deg)
    dy = [m.addVar(lb=-tr, ub=tr, name=f'dy{t}') for t in range(n)]
    sb = d.get('startBox') or {}
    free = bool(sb.get('startFree'))
    if free:
        dpx = m.addVar(lb=sb['pxLo'] - rep['px'], ub=sb['pxHi'] - rep['px'], name='dpx')
        dpz = m.addVar(lb=sb['pzLo'] - rep['pz'], ub=sb['pzHi'] - rep['pz'], name='dpz')
    smin = m.addVar(lb=-COPT.INFINITY, name='smin')
    for w in d['walls']:
        c = cons.get(w['name'])
        if c is None or w['eq']:
            continue
        vb = byte_viol(d, rep, c)
        if w['axis'] == 0:
            expr = quicksum(w['coef'][t] * (-uz[t]) * dy[t] for t in range(n)
                            if w['coef'][t] != 0.0)
        else:
            expr = quicksum(w['coef'][t] * ux[t] * dy[t] for t in range(n)
                            if w['coef'][t] != 0.0)
        if free and w['p0coef'] != 0.0:
            dp = dpx if w['axis'] == 0 else dpz
            expr = expr - w['p0coef'] * dp
        m.addConstr(-vb - expr >= smin, name=w['name'])
    per_axis = d['perAxisInertia']
    for a in range(2):
        vrep = rep['velX'] if a == 0 else rep['velZ']
        dus = [(-uz[t]) for t in range(n)] if a == 0 else [ux[t] for t in range(n)]
        for t in range(1, n):
            v = vrep[t]
            dv = quicksum((fPre[t] / fPre[s]) * dus[s] * dy[s]
                          for s in range(t) if zNext[a][s] >= t and dus[s] != 0.0)
            eff_thr = thr if per_axis else 3e-3
            if abs(v) < eff_thr:
                m.addConstr(v + dv <= eff_thr - 1e-5)
                m.addConstr(v + dv >= -eff_thr + 1e-5)
            elif abs(abs(v) - eff_thr) < gate_band:
                if v > 0:
                    m.addConstr(v + dv >= eff_thr + 1e-5)
                else:
                    m.addConstr(v + dv <= -eff_thr - 1e-5)
    m.setObjective(smin, sense=COPT.MAXIMIZE)
    m.solve()
    if m.status != COPT.OPTIMAL:
        return None
    smin_val = m.objval
    cap = 1e-4
    if smin_val > cap:
        m.addConstr(smin >= cap)
        dS = quicksum(d['ticks'][t]['cx'] * (-uz[t]) * dy[t]
                      + d['ticks'][t]['cz'] * ux[t] * dy[t] for t in range(n))
        if free:
            dp = dpx if d['objAxis'] == 0 else dpz
            sgn = 1.0 if d['objMaximize'] else -1.0
            dS = dS + sgn * dp
        m.setObjective(dS, sense=COPT.MAXIMIZE)
        m.solve()
        if m.status != COPT.OPTIMAL:
            return None
    return {
        'smin': smin_val,
        'dy': [dy[t].x for t in range(n)],
        'dpx': dpx.x if free else 0.0,
        'dpz': dpz.x if free else 0.0,
    }


def slp_polish(d, dec, rep, capture, tag, pattern, rounds=8, trust_deg=1.5):
    yaws = list(dec['yawsDeg'])
    cur_rep = rep
    best = (rep['maxViol'], list(yaws), rep['px'], rep['pz'], rep)
    for r in range(rounds):
        st = slp_step(d, yaws, cur_rep, pattern, trust_deg=trust_deg)
        if st is None:
            print('    slp: step LP failed')
            break
        new_yaws = [yaws[t] + math.degrees(st['dy'][t]) for t in range(len(yaws))]
        dec2 = dict(dec)
        dec2['yawsDeg'] = new_yaws
        dec2['px'] = cur_rep['px'] + st['dpx']
        dec2['pz'] = cur_rep['pz'] + st['dpz']
        dec_path = os.path.join(DATA, f'{tag}-slp{r}-decode.json')
        json.dump(dec2, open(dec_path, 'w'))
        rep_path = os.path.join(DATA, f'{tag}-slp{r}-replay.json')
        rep2 = replay(capture, dec_path, rep_path)
        print(f'    slp[{r}]: predSmin={st["smin"]:.3e} trust={trust_deg:.3f} '
              f'obj={rep2["objective"]:.9f} maxViol={rep2["maxViol"]:.6g}')
        if rep2['maxViol'] == 0.0:
            return {'objective': rep2['objective'], 'decode': dec_path, 'replay': rep_path,
                    'dec': dec2, 'rep': rep2}
        if rep2['maxViol'] < best[0]:
            best = (rep2['maxViol'], list(new_yaws), dec2['px'], dec2['pz'], rep2)
            yaws = new_yaws
            cur_rep = rep2
            dec = dec2
        else:
            trust_deg = max(trust_deg * 0.4, 0.01)
            yaws = best[1]
            cur_rep = best[4]
    dec_best = dict(dec)
    dec_best['yawsDeg'] = best[1]
    dec_best['px'] = best[2]
    dec_best['pz'] = best[3]
    return {'objective': best[4]['objective'], 'decode': None, 'replay': None,
            'dec': dec_best, 'rep': best[4], 'maxViol': best[0]}


def walk_polish(d, dec, rep, capture, tag, pattern, W=2, rounds=6):
    yaws = list(dec['yawsDeg'])
    cur_rep = rep
    cur_yaws = yaws
    budget = 6
    best_state = (rep['maxViol'], list(yaws), rep['px'], rep['pz'])
    for r in range(rounds):
        st = walk_step(d, cur_yaws, cur_rep, pattern, W=W, budget=budget)
        if st is None:
            print('    walk: step MIP failed')
            return None
        moved = sum(1 for x in st['b'] if x != 0)
        new_yaws = [cur_yaws[t] + st['b'][t] * BUCKET_DEG for t in range(len(cur_yaws))]
        dec2 = dict(dec)
        dec2['yawsDeg'] = new_yaws
        dec2['px'] = cur_rep['px'] + st['dpx']
        dec2['pz'] = cur_rep['pz'] + st['dpz']
        dec_path = os.path.join(DATA, f'{tag}-walk{r}-decode.json')
        json.dump(dec2, open(dec_path, 'w'))
        rep_path = os.path.join(DATA, f'{tag}-walk{r}-replay.json')
        rep2 = replay(capture, dec_path, rep_path)
        print(f'    walk[{r}]: predSmin={st["smin"]:.3e} moved={moved} budget={budget} '
              f'obj={rep2["objective"]:.9f} maxViol={rep2["maxViol"]:.6g}')
        if rep2['maxViol'] == 0.0:
            return {'objective': rep2['objective'], 'decode': dec_path, 'replay': rep_path}
        if rep2['maxViol'] < best_state[0]:
            best_state = (rep2['maxViol'], list(new_yaws), dec2['px'], dec2['pz'])
            cur_yaws = new_yaws
            cur_rep = rep2
        else:
            budget = max(1, budget // 2)
            cur_yaws = best_state[1]
    return None


def obj_step(d, yaws, rep, pattern, W=2, budget=8, min_slack=2e-6, gate_band=5e-4):
    n = d['numTicks']
    ux, uz = realized_u(d, yaws)
    cons = {c['name']: c for c in d['constraints']}
    thr = d['inertiaThreshold']
    fPre, zNext = vel_couplings(d, pattern)
    m = coptlib.env().createModel('objwalk')
    m.setParam(COPT.Param.Logging, 0)
    b = [m.addVar(lb=-W, ub=W, vtype=COPT.INTEGER, name=f'b{t}') for t in range(n)]
    babs = [m.addVar(lb=0, ub=W, name=f'ba{t}') for t in range(n)]
    for t in range(n):
        m.addConstr(babs[t] >= b[t])
        m.addConstr(babs[t] >= -b[t])
    m.addConstr(quicksum(babs) <= budget)
    sb = d.get('startBox') or {}
    free = bool(sb.get('startFree'))
    if free:
        dpx = m.addVar(lb=sb['pxLo'] - rep['px'], ub=sb['pxHi'] - rep['px'], name='dpx')
        dpz = m.addVar(lb=sb['pzLo'] - rep['pz'], ub=sb['pzHi'] - rep['pz'], name='dpz')
    step = math.radians(BUCKET_DEG)
    dux = [-uz[t] * step for t in range(n)]
    duz = [ux[t] * step for t in range(n)]
    for w in d['walls']:
        c = cons.get(w['name'])
        if c is None or w['eq']:
            continue
        vb = byte_viol(d, rep, c)
        du = dux if w['axis'] == 0 else duz
        expr = quicksum(w['coef'][t] * du[t] * b[t] for t in range(n) if w['coef'][t] != 0.0)
        if free and w['p0coef'] != 0.0:
            dp = dpx if w['axis'] == 0 else dpz
            expr = expr - w['p0coef'] * dp
        m.addConstr(vb + expr <= -min_slack, name=w['name'])
    per_axis = d['perAxisInertia']
    for a in range(2):
        vrep = rep['velX'] if a == 0 else rep['velZ']
        dus = dux if a == 0 else duz
        for t in range(1, n):
            v = vrep[t]
            dv = quicksum((fPre[t] / fPre[s]) * dus[s] * b[s]
                          for s in range(t) if zNext[a][s] >= t and dus[s] != 0.0)
            eff_thr = thr if per_axis else 3e-3
            if abs(v) < eff_thr:
                m.addConstr(v + dv <= eff_thr - 1e-5)
                m.addConstr(v + dv >= -eff_thr + 1e-5)
            elif abs(abs(v) - eff_thr) < gate_band:
                if v > 0:
                    m.addConstr(v + dv >= eff_thr + 1e-5)
                else:
                    m.addConstr(v + dv <= -eff_thr - 1e-5)
    dS = quicksum(d['ticks'][t]['cx'] * dux[t] * b[t] + d['ticks'][t]['cz'] * duz[t] * b[t]
                  for t in range(n))
    if free:
        dp = dpx if d['objAxis'] == 0 else dpz
        sgn = 1.0 if d['objMaximize'] else -1.0
        dS = dS + sgn * dp
    m.setObjective(dS, sense=COPT.MAXIMIZE)
    m.solve()
    if m.status != COPT.OPTIMAL:
        return None
    return {
        'dS': m.objval,
        'b': [int(round(b[t].x)) for t in range(n)],
        'dpx': dpx.x if free else 0.0,
        'dpz': dpz.x if free else 0.0,
    }


def obj_polish(d, dec, rep, capture, tag, pattern, rounds=8, W=2, budget=8):
    yaws = list(dec['yawsDeg'])
    cur_rep = rep
    best = {'objective': rep['objective'], 'decode': None, 'replay': None}
    sense = 1.0 if d['objSense'] == 'MAX' else -1.0
    for r in range(rounds):
        st = obj_step(d, yaws, cur_rep, pattern, W=W, budget=budget)
        if st is None:
            print('    obj: step MIP infeasible')
            break
        moved = sum(1 for x in st['b'] if x != 0)
        if moved == 0 and abs(st['dpx']) < 1e-12 and abs(st['dpz']) < 1e-12:
            print('    obj: no move proposed, stopping')
            break
        new_yaws = [yaws[t] + st['b'][t] * BUCKET_DEG for t in range(len(yaws))]
        dec2 = dict(dec)
        dec2['yawsDeg'] = new_yaws
        dec2['px'] = cur_rep['px'] + st['dpx']
        dec2['pz'] = cur_rep['pz'] + st['dpz']
        dec_path = os.path.join(DATA, f'{tag}-obj{r}-decode.json')
        json.dump(dec2, open(dec_path, 'w'))
        rep_path = os.path.join(DATA, f'{tag}-obj{r}-replay.json')
        rep2 = replay(capture, dec_path, rep_path)
        gain = sense * (rep2['objective'] - best['objective'])
        print(f'    obj[{r}]: predDS={st["dS"]:.3e} moved={moved} budget={budget} '
              f'obj={rep2["objective"]:.9f} maxViol={rep2["maxViol"]:.6g} gain={gain:.3e}')
        if rep2['maxViol'] == 0.0 and gain > 0:
            best = {'objective': rep2['objective'], 'decode': dec_path, 'replay': rep_path}
            yaws = new_yaws
            cur_rep = rep2
            dec = dec2
            if st['dS'] > 0 and abs(gain - st['dS']) < 0.3 * st['dS']:
                budget = min(budget * 2, 1024)
            if gain < 1e-9:
                break
        else:
            budget = max(2, budget // 2)
            if budget == 2 and rep2['maxViol'] != 0.0 and r > 3:
                break
    return best


def build_inputs(d, args):
    n = d['numTicks']
    if args.inputs_from:
        src = json.load(open(args.inputs_from))
        return src['forward'], src['strafe'], src['sprint']
    if args.inputs_from_capture:
        p = args.inputs_from_capture
        if not os.path.isabs(p):
            p = os.path.join(REPO, p)
        cap = json.load(open(p))
        st = d['startTick']
        dbg = cap['debug']
        derive = cap.get('angleSolver', {}).get('defaultSprint') == 'DERIVE'
        fwd = [dbg[st + k + 1]['moveForward'] for k in range(n)]
        strafe = [dbg[st + k + 1]['moveStrafe'] for k in range(n)]
        sprint = [(dbg[st + k + 1]['sprinting'] if derive else True) for k in range(n)]
        return fwd, strafe, sprint
    fwd = [args.forward] * n
    strafe = [args.strafe] * n
    sprint = [not args.no_sprint] * n
    return fwd, strafe, sprint


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--struct', required=True)
    ap.add_argument('--capture', required=True)
    ap.add_argument('--tag', required=True)
    ap.add_argument('--rounds', type=int, default=8)
    ap.add_argument('--forward', type=float, default=0.98)
    ap.add_argument('--strafe', type=float, default=0.0)
    ap.add_argument('--no-sprint', action='store_true')
    ap.add_argument('--inputs-from', default=None)
    ap.add_argument('--inputs-from-capture', default=None)
    ap.add_argument('--warm-check', action='store_true')
    ap.add_argument('--seed-pattern', default=None)
    ap.add_argument('--decode-mode', choices=['direct', 'track', 'chord', 'hybrid', 'sphere'],
                    default='chord')
    ap.add_argument('--chord-min-mmag', type=float, default=0.05)
    ap.add_argument('--kp', type=float, default=0.3)
    ap.add_argument('--kv', type=float, default=1.0)
    ap.add_argument('--optimize-rounds', type=int, default=0)
    args = ap.parse_args()

    d = json.load(open(args.struct))
    fwd, strafe, sprint = build_inputs(d, args)
    if args.warm_check:
        dec = {'yawsDeg': d['warmYawsDeg'], 'forward': fwd, 'strafe': strafe, 'sprint': sprint}
        dec_path = os.path.join(DATA, f'{args.tag}-warmcheck-decode.json')
        json.dump(dec, open(dec_path, 'w'))
        rep = replay(args.capture, dec_path, os.path.join(DATA, f'{args.tag}-warmcheck-replay.json'))
        print(f'warm check: obj={rep["objective"]!r} (want {d.get("warmObj")!r}) '
              f'maxViol={rep["maxViol"]!r} (want {d.get("warmViol")!r})')
        return
    cur_struct_path = args.struct
    cur_pattern = None
    if args.seed_pattern:
        cur_pattern = json.load(open(args.seed_pattern))
    margins = {}
    best = None
    prev_yaws = None
    prev_sol = None

    for rnd in range(args.rounds):
        if args.decode_mode == 'chord':
            sol, chord_iters = solve_full_modulus(d, margins)
        elif args.decode_mode == 'hybrid':
            sol, chord_iters = solve_full_modulus(d, margins,
                                                  chord_min_mmag=args.chord_min_mmag)
        elif args.decode_mode == 'sphere':
            sol = solve_sphere(d, margins, warm=prev_sol)
            chord_iters = 0
            if sol.get('modCheat', 0.0) > 1e-6:
                print(f'    sphere modulus cheat {sol["modCheat"]:.2e}')
        else:
            sol = solve_disk(d, margins)
            chord_iters = 0
        prev_sol = sol if sol['status'] == COPT.OPTIMAL else prev_sol
        if sol['status'] != COPT.OPTIMAL:
            print(f'[{rnd}] solve status {sol["status"]}, stopping')
            break
        if args.decode_mode in ('track', 'hybrid'):
            yaws, dev = track_decode(d, sol, cur_pattern, kp=args.kp, kv=args.kv,
                                     prev_yaws=prev_yaws)
        else:
            yaws = decode_yaws(d, sol, prev_yaws)
            dev = float(chord_iters)
        prev_yaws = yaws
        dec = {'yawsDeg': yaws, 'forward': fwd, 'strafe': strafe, 'sprint': sprint,
               'px': sol['px'], 'pz': sol['pz']}
        dec_path = os.path.join(DATA, f'{args.tag}-r{rnd}-decode.json')
        json.dump(dec, open(dec_path, 'w'))
        rep_path = os.path.join(DATA, f'{args.tag}-r{rnd}-replay.json')
        rep = replay(args.capture, dec_path, rep_path)
        pat = extract_pattern(d, rep)
        n_zero = sum(pat['zeroX']) + sum(pat['zeroZ'])
        feasible = rep['maxViol'] == 0.0
        worst = max(rep['violations'], key=lambda v: v['slack'])['name'] if rep['violations'] else '-'
        print(f'[{rnd}] linPos={sol["pos"]:.6f} interior={len(sol["interior"])} trackDev={dev:.2e} '
              f'byteObj={rep["objective"]:.9f} maxViol={rep["maxViol"]:.6g} worst={worst} zeros={n_zero}')
        if feasible and (best is None or
                         (rep['objective'] > best['objective']) == (d['objSense'] == 'MAX')):
            best = {'round': rnd, 'objective': rep['objective'], 'decode': dec_path,
                    'replay': rep_path}
        if not feasible and pat == cur_pattern:
            pol = None
            dec_p, rep_p = dec, rep
            if rep['maxViol'] >= 1e-3:
                s = slp_polish(d, dec, rep, args.capture, f'{args.tag}-r{rnd}', cur_pattern)
                if s is not None and s['rep']['maxViol'] == 0.0:
                    pol = s
                elif s is not None:
                    dec_p, rep_p = s['dec'], s['rep']
            if pol is None and rep_p['maxViol'] < 1e-3:
                pol = polish_translate(d, dec_p, rep_p, args.capture, f'{args.tag}-r{rnd}')
                if pol is None:
                    pol = walk_polish(d, dec_p, rep_p, args.capture, f'{args.tag}-r{rnd}',
                                      cur_pattern)
            if pol is not None:
                if best is None or (pol['objective'] > best['objective']) == (d['objSense'] == 'MAX'):
                    pol['round'] = rnd
                    best = {k: pol[k] for k in ('objective', 'decode', 'replay', 'round')}
                print('    polish landed byte-exact, stopping')
                break
        if pat != cur_pattern:
            pat_path = os.path.join(DATA, f'{args.tag}-r{rnd}-pattern.json')
            json.dump(pat, open(pat_path, 'w'))
            new_struct = os.path.join(DATA, f'{args.tag}-r{rnd}-struct.json')
            d = export_struct(args.capture, pat_path, new_struct)
            cur_struct_path = new_struct
            cur_pattern = pat
            print(f'    pattern changed -> refolded ({new_struct})')
            margins = compute_margins(d, yaws, sol['px'], sol['pz'], rep)
            continue
        margins = compute_margins(d, yaws, sol['px'], sol['pz'], rep)
        mx = max(abs(v) for v in margins.values()) if margins else 0.0
        print(f'    margins recomputed, max|m|={mx:.3e}')
        if feasible and rnd > 0:
            prev = json.load(open(os.path.join(DATA, f'{args.tag}-r{rnd-1}-replay.json')))
            if prev['maxViol'] == 0.0 and abs(prev['objective'] - rep['objective']) < 1e-9:
                print('    converged (feasible, objective stable)')
                break

    if best is not None and args.optimize_rounds > 0 and best.get('decode'):
        print(f'OPTIMIZE phase ({args.optimize_rounds} rounds) from {best["objective"]:.9f}')
        bdec = json.load(open(best['decode']))
        brep = json.load(open(best['replay']))
        pat = extract_pattern(d, brep)
        if pat != cur_pattern:
            pat_path = os.path.join(DATA, f'{args.tag}-opt-pattern.json')
            json.dump(pat, open(pat_path, 'w'))
            d = export_struct(args.capture, pat_path,
                              os.path.join(DATA, f'{args.tag}-opt-struct.json'))
            cur_pattern = pat
        ob = obj_polish(d, bdec, brep, args.capture, f'{args.tag}-opt', cur_pattern,
                        rounds=args.optimize_rounds)
        if ob['decode'] is not None:
            best = {'objective': ob['objective'], 'decode': ob['decode'],
                    'replay': ob['replay'], 'round': 'opt'}
    print('BEST:', json.dumps(best, indent=1) if best else 'none (no feasible replay)')


if __name__ == '__main__':
    main()
