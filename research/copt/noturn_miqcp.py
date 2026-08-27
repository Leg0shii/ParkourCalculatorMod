"""No-turn offset-selection MIQCP (ARCH-2 outer layer proof-of-concept).

Given a StructureDump geometry export (walls, objective vectors, free-start box, ground/jump
pattern) plus two W-probe exports (sprint on / off) for the per-tick accel magnitudes, build the
single-quadratic MIQCP over (a, b) = (cos theta, sin theta) for one constant setup facing:

  setup tick t: combo k in 9-way alphabet (8 offsets + NONE), sprint s_t monotone;
                u_t = accel_t(e_t) * kappa_k * R(45k)(a,b) + 0.2 * s_t * i*(a,b) on jump ticks
  turn tick t:  u_t free direction, |u_t|^2 == accel_air(e_t)^2 (diagonal keys, or <= with --disk)
  e_t = s_t on ground ticks, s_{t-1} on air ticks (legacy air-factor lag)
  walls / objective: linear in u_t and the free start (px, pz)

The only nonconvexities are the circle a^2+b^2=1 and the optional turn spheres. Decodes the
solution to keys + yaws and writes a decode json for the NoTurnReplay byte-exact verifier.
"""

import argparse
import json
import math
import time

import coptlib
from coptpy import COPT, quicksum

COMBOS = [
    ('NONE', None, 0.0, False, 0.0, 0.0),
    ('W', 90.0, 0.98, True, 0.98, 0.0),
    ('WA', 45.0, 1.0, True, 0.98, 0.98),
    ('A', 0.0, 0.98, False, 0.0, 0.98),
    ('SA', -45.0, 1.0, False, -0.98, 0.98),
    ('S', -90.0, 0.98, False, -0.98, 0.0),
    ('SD', -135.0, 1.0, False, -0.98, -0.98),
    ('D', 180.0, 0.98, False, 0.0, -0.98),
    ('WD', 135.0, 1.0, True, 0.98, -0.98),
]

TURN_COMBOS = {'WA': 45.0, 'WD': 135.0}


def magnitudes(struct_on, struct_off):
    n = struct_on['numTicks']
    base = [0.0] * n
    delta = [0.0] * n
    for t in range(n):
        off = struct_off['ticks'][t]['forwardMag'] / 0.98
        on = struct_on['ticks'][t]['forwardMag'] / 0.98
        base[t] = off
        delta[t] = on - off
    return base, delta


def build(d, base, delta, last_setup, disk=False, sprint_fix=None, relax_circle=False, fix=None,
          min_slack=False, margins=None, trust=None, trust_k=4, trust_theta=1.0, free_sprint=False,
          ja_tick=None, fix_theta=None, fix_theta2=None, trust_turn=None, min_edges=False,
          max_margin=False, max_edges=None):
    n = d['numTicks']
    setup = list(range(0, last_setup + 1))
    turn = list(range(last_setup + 1, n))
    contact = [d['ticks'][t]['contact'] for t in range(n)]
    jump = [d['ticks'][t]['jump'] for t in range(n)]
    sb = d['startBox']

    m = coptlib.env().createModel('noturn')
    m.setParam(COPT.Param.Logging, 1)

    a = m.addVar(lb=-1.0, ub=1.0, name='a')
    b = m.addVar(lb=-1.0, ub=1.0, name='b')
    if fix_theta is not None:
        r0 = math.radians(fix_theta)
        m.addConstr(a == math.cos(r0), name='fta')
        m.addConstr(b == math.sin(r0), name='ftb')
    elif relax_circle:
        m.addQConstr(a * a + b * b <= 1.0, name='circle')
    else:
        m.addQConstr(a * a + b * b == 1.0, name='circle')

    px = m.addVar(lb=sb['pxLo'], ub=sb['pxHi'], name='px')
    pz = m.addVar(lb=sb['pzLo'], ub=sb['pzHi'], name='pz')

    a2 = b2 = None
    if ja_tick is not None:
        a2 = m.addVar(lb=-1.0, ub=1.0, name='a2')
        b2 = m.addVar(lb=-1.0, ub=1.0, name='b2')
        if fix_theta2 is not None:
            r2 = math.radians(fix_theta2)
            m.addConstr(a2 == math.cos(r2), name='fta2')
            m.addConstr(b2 == math.sin(r2), name='ftb2')
        elif relax_circle:
            m.addQConstr(a2 * a2 + b2 * b2 <= 1.0, name='circle2')
        else:
            m.addQConstr(a2 * a2 + b2 * b2 == 1.0, name='circle2')

    s = [m.addVar(vtype=COPT.BINARY, name=f's{t}') for t in range(n)]
    if not free_sprint:
        for t in range(1, n):
            m.addConstr(s[t] >= s[t - 1], name=f'mono{t}')
    if sprint_fix is not None:
        for t in range(n):
            m.addConstr(s[t] == (1 if t >= sprint_fix else 0), name=f'sfix{t}')
    if fix is not None:
        for t in range(n):
            m.addConstr(s[t] == (1 if fix['sprint'][t] else 0), name=f'ffs{t}')
    if trust is not None:
        t0 = math.radians(trust['thetaDeg'])
        band = math.radians(trust_theta)
        a0, b0 = math.cos(t0), math.sin(t0)
        r = 2.0 * math.sin(band / 2.0)
        m.addConstr(a - a0 <= r, name='tra_u')
        m.addConstr(a - a0 >= -r, name='tra_l')
        m.addConstr(b - b0 <= r, name='trb_u')
        m.addConstr(b - b0 >= -r, name='trb_l')
        if ja_tick is not None and trust.get('theta2Deg') is not None:
            t2 = math.radians(trust['theta2Deg'])
            a20, b20 = math.cos(t2), math.sin(t2)
            m.addConstr(a2 - a20 <= r, name='tra2_u')
            m.addConstr(a2 - a20 >= -r, name='tra2_l')
            m.addConstr(b2 - b20 <= r, name='trb2_u')
            m.addConstr(b2 - b20 >= -r, name='trb2_l')
        ham = quicksum((1 - s[t]) if trust['sprint'][t] else s[t] for t in range(n))
        m.addConstr(ham <= trust_k, name='trs')

    def eff(t):
        return s[t] if (t == 0 or contact[t]) else s[t - 1]

    delt = {}
    ux = {}
    uz = {}
    for t in setup:
        row = [m.addVar(vtype=COPT.BINARY, name=f'd{t}_{c[0]}') for c in COMBOS]
        delt[t] = row
        m.addConstr(quicksum(row) == 1, name=f'one{t}')
        if fix is not None:
            want = fix['setupCombos'][t]
            for i, c in enumerate(COMBOS):
                m.addConstr(row[i] == (1 if c[0] == want else 0), name=f'ffc{t}_{c[0]}')
        if trust is not None:
            anchor = trust['setupCombos'][t]
            ai = next(i for i, c in enumerate(COMBOS) if c[0] == anchor)
            trust.setdefault('_hamTerms', []).append(1 - row[ai])
        fwd_sum = quicksum(row[i] for i, c in enumerate(COMBOS) if c[3])
        m.addConstr(s[t] <= fwd_sum, name=f'sfwd{t}')
        if free_sprint and t > 0:
            m.addConstr(s[t] >= s[t - 1] + fwd_sum - 1, name=f'slatch{t}')
        aa = a2 if (ja_tick is not None and t == ja_tick) else a
        bb = b2 if (ja_tick is not None and t == ja_tick) else b
        wx = m.addVar(lb=-1.0, ub=1.0, name=f'wx{t}')
        wz = m.addVar(lb=-1.0, ub=1.0, name=f'wz{t}')
        for i, (name, phi, kappa, _, _, _) in enumerate(COMBOS):
            if phi is None:
                tx = 0.0 * aa
                tz = 0.0 * aa
            else:
                r = math.radians(phi)
                tx = kappa * (math.cos(r) * aa - math.sin(r) * bb)
                tz = kappa * (math.sin(r) * aa + math.cos(r) * bb)
            m.addConstr(wx - tx <= 2.0 * (1 - row[i]), name=f'wxu{t}_{name}')
            m.addConstr(wx - tx >= -2.0 * (1 - row[i]), name=f'wxl{t}_{name}')
            m.addConstr(wz - tz <= 2.0 * (1 - row[i]), name=f'wzu{t}_{name}')
            m.addConstr(wz - tz >= -2.0 * (1 - row[i]), name=f'wzl{t}_{name}')
        e = eff(t)
        yx = m.addVar(lb=-1.0, ub=1.0, name=f'yx{t}')
        yz = m.addVar(lb=-1.0, ub=1.0, name=f'yz{t}')
        m.addConstr(yx <= e, name=f'yxa{t}')
        m.addConstr(yx >= -1.0 * e, name=f'yxb{t}')
        m.addConstr(yx <= wx + (1 - e), name=f'yxc{t}')
        m.addConstr(yx >= wx - (1 - e), name=f'yxd{t}')
        m.addConstr(yz <= e, name=f'yza{t}')
        m.addConstr(yz >= -1.0 * e, name=f'yzb{t}')
        m.addConstr(yz <= wz + (1 - e), name=f'yzc{t}')
        m.addConstr(yz >= wz - (1 - e), name=f'yzd{t}')
        ex = base[t] * wx + delta[t] * yx
        ez = base[t] * wz + delta[t] * yz
        if jump[t]:
            sa = m.addVar(lb=-1.0, ub=1.0, name=f'sa{t}')
            sbv = m.addVar(lb=-1.0, ub=1.0, name=f'sb{t}')
            for v, tgt, nm in ((sa, aa, 'sa'), (sbv, bb, 'sb')):
                m.addConstr(v <= s[t], name=f'{nm}a{t}')
                m.addConstr(v >= -1.0 * s[t], name=f'{nm}b{t}')
                m.addConstr(v <= tgt + (1 - s[t]), name=f'{nm}c{t}')
                m.addConstr(v >= tgt - (1 - s[t]), name=f'{nm}d{t}')
            ex = ex - 0.2 * sbv
            ez = ez + 0.2 * sa
        ux[t] = ex
        uz[t] = ez

    if trust is not None and trust.get('_hamTerms'):
        m.addConstr(quicksum(trust['_hamTerms']) <= trust_k, name='trc')

    turn_mag = {}
    for t in turn:
        cap = base[t] + delta[t]
        vx = m.addVar(lb=-cap, ub=cap, name=f'tux{t}')
        vz = m.addVar(lb=-cap, ub=cap, name=f'tuz{t}')
        e = eff(t)
        lo2 = base[t] * base[t]
        hi2 = cap * cap
        if disk:
            m.addQConstr(vx * vx + vz * vz - (hi2 - lo2) * e <= lo2, name=f'tdisk{t}')
        else:
            m.addQConstr(vx * vx + vz * vz - (hi2 - lo2) * e == lo2, name=f'tsph{t}')
        ux[t] = vx
        uz[t] = vz
        turn_mag[t] = (base[t], cap)
        if free_sprint:
            m.addConstr(s[t] >= s[t - 1], name=f'slatch{t}')
        if trust_turn is not None and trust is not None:
            note = next((nn for nn in trust.get('turn', []) if nn['t'] == t), None)
            if note is not None:
                phi = 45.0 if note['combo'] == 'WA' else 135.0
                arg = math.radians(note['yaw'] + phi)
                ux0 = note['mag'] * math.cos(arg)
                uz0 = note['mag'] * math.sin(arg)
                m.addConstr(vx - ux0 <= trust_turn, name=f'ttxu{t}')
                m.addConstr(vx - ux0 >= -trust_turn, name=f'ttxl{t}')
                m.addConstr(vz - uz0 <= trust_turn, name=f'ttzu{t}')
                m.addConstr(vz - uz0 >= -trust_turn, name=f'ttzl{t}')

    slacks = []
    mv = None
    spec_names = {c['name'] for c in d.get('constraints', [])}
    if max_margin:
        mv = m.addVar(lb=0.0, ub=10.0, name='mv')
    for w in d['walls']:
        uu = ux if w['axis'] == 0 else uz
        expr = quicksum(w['coef'][t] * uu[t] for t in range(n) if w['coef'][t] != 0.0)
        if w['p0coef'] != 0.0:
            pv = px if w['axis'] == 0 else pz
            ref = sb['px'] if w['axis'] == 0 else sb['pz']
            expr = expr - w['p0coef'] * (pv - ref)
        if margins and w['name'] in margins:
            w = dict(w, bPrime=w['bPrime'] - margins[w['name']])
        if max_margin and not w['eq'] and w['name'] in spec_names:
            expr = expr + mv
        if min_slack:
            sg = m.addVar(lb=0.0, ub=10.0, name=f'slk_{w["name"]}')
            slacks.append(sg)
            if w['eq']:
                m.addConstr(expr - w['bPrime'] <= sg, name=w['name'] + '_u')
                m.addConstr(w['bPrime'] - expr <= sg, name=w['name'] + '_l')
            else:
                m.addConstr(expr <= w['bPrime'] + sg, name=w['name'])
        elif w['eq']:
            m.addConstr(expr == w['bPrime'], name=w['name'])
        else:
            m.addConstr(expr <= w['bPrime'], name=w['name'])

    if min_edges or max_edges is not None:
        eterms = []
        for t in setup[1:]:
            ev = m.addVar(vtype=COPT.BINARY, name=f'edge{t}')
            for i in range(len(COMBOS)):
                m.addConstr(ev >= delt[t][i] - delt[t - 1][i], name=f'ed{t}_{i}')
            eterms.append(ev)
        if max_edges is not None:
            m.addConstr(quicksum(eterms) <= float(max_edges), name='edgecap')

    if min_slack:
        m.setObjective(quicksum(slacks), sense=COPT.MINIMIZE)
    elif max_margin:
        m.setObjective(mv, sense=COPT.MAXIMIZE)
    elif min_edges:
        m.setObjective(quicksum(eterms), sense=COPT.MINIMIZE)
    else:
        S = quicksum(d['ticks'][t]['cx'] * ux[t] + d['ticks'][t]['cz'] * uz[t] for t in range(n))
        pobj = px if d['objAxis'] == 0 else pz
        pref = sb['px'] if d['objAxis'] == 0 else sb['pz']
        sgn = 1.0 if d['objMaximize'] else -1.0
        S = S + sgn * (pobj - pref)
        m.setObjective(S, sense=COPT.MAXIMIZE)

    return m, {'a': a, 'b': b, 'a2': a2, 'b2': b2, 'ja_tick': ja_tick,
               'px': px, 'pz': pz, 's': s, 'delt': delt,
               'ux': ux, 'uz': uz, 'setup': setup, 'turn': turn, 'turn_mag': turn_mag,
               'contact': contact, 'jump': jump, 'slacks': slacks, 'min_slack': min_slack}


def decode(d, vars_, base, delta):
    n = d['numTicks']
    a = vars_['a'].x
    b = vars_['b'].x
    theta = math.degrees(math.atan2(b, a))
    s = [round(v.x) > 0 for v in vars_['s']]
    combos = []
    for t in vars_['setup']:
        row = vars_['delt'][t]
        best = max(range(len(COMBOS)), key=lambda i: row[i].x)
        combos.append(COMBOS[best])
    yaws = [theta] * n
    theta2 = None
    ja = vars_.get('ja_tick')
    if ja is not None:
        theta2 = math.degrees(math.atan2(vars_['b2'].x, vars_['a2'].x))
        yaws[ja] = theta2
    forward = [0.0] * n
    strafe = [0.0] * n
    for t, c in zip(vars_['setup'], combos):
        forward[t] = c[4]
        strafe[t] = c[5]
    turn_notes = []
    prev = yaws[vars_['turn'][0] - 1] if vars_['turn'] else theta
    for t in vars_['turn']:
        vx = vars_['ux'][t].x
        vz = vars_['uz'][t].x
        mag = math.hypot(vx, vz)
        lo, hi = vars_['turn_mag'][t]
        want = hi if s[t - 1] else lo
        argdeg = math.degrees(math.atan2(vz, vx))
        cands = []
        for name, phi in TURN_COMBOS.items():
            y = argdeg - phi
            dy = (y - prev + 180.0) % 360.0 - 180.0
            cands.append((abs(dy), y, name))
        cands.sort()
        _, y, name = cands[0]
        yaws[t] = y
        forward[t] = 0.98
        strafe[t] = 0.98 if name == 'WA' else -0.98
        prev = y
        turn_notes.append({'t': t, 'combo': name, 'mag': mag, 'wantMag': want, 'yaw': y})
    return {
        'thetaDeg': theta,
        'theta2Deg': theta2,
        'a': a, 'b': b,
        'px': vars_['px'].x, 'pz': vars_['pz'].x,
        'yawsDeg': yaws,
        'forward': forward,
        'strafe': strafe,
        'sprint': s,
        'setupCombos': [c[0] for c in combos],
        'turn': turn_notes,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--struct', default='data/struct-j1150-inputs-gone.json')
    ap.add_argument('--probe-on', default='data/struct-j1150-probe-w-sprint.json')
    ap.add_argument('--probe-off', default='data/struct-j1150-probe-w-nosprint.json')
    ap.add_argument('--last-setup', type=int, default=38)
    ap.add_argument('--time', type=float, default=600.0)
    ap.add_argument('--gap', type=float, default=None)
    ap.add_argument('--feastol', type=float, default=1e-9)
    ap.add_argument('--disk', action='store_true')
    ap.add_argument('--relax-circle', action='store_true')
    ap.add_argument('--min-slack', action='store_true')
    ap.add_argument('--mip-start', default=None)
    ap.add_argument('--dump-vars', default=None)
    ap.add_argument('--fix-combos', default=None)
    ap.add_argument('--margins', default=None)
    ap.add_argument('--trust-decode', default=None)
    ap.add_argument('--trust-k', type=int, default=4)
    ap.add_argument('--trust-theta', type=float, default=1.0)
    ap.add_argument('--sprint-fix', type=int, default=None)
    ap.add_argument('--free-sprint', action='store_true')
    ap.add_argument('--ja-tick', type=int, default=None)
    ap.add_argument('--fix-theta', type=float, default=None)
    ap.add_argument('--fix-theta2', type=float, default=None)
    ap.add_argument('--trust-turn', type=float, default=None)
    ap.add_argument('--min-edges', action='store_true')
    ap.add_argument('--max-margin', action='store_true')
    ap.add_argument('--max-edges', type=int, default=None)
    ap.add_argument('--out', default='data/noturn-j1150-decode.json')
    ap.add_argument('--report', default='data/noturn-j1150-result.json')
    args = ap.parse_args()

    d = coptlib.load(args.struct)
    on = coptlib.load(args.probe_on)
    off = coptlib.load(args.probe_off)
    base, delta = magnitudes(on, off)

    fix = None
    if args.fix_combos:
        with open(args.fix_combos) as f:
            fix = json.load(f)
    margins = None
    if args.margins:
        with open(args.margins) as f:
            margins = json.load(f)
    trust = None
    if args.trust_decode:
        with open(args.trust_decode) as f:
            trust = json.load(f)
    m, vars_ = build(d, base, delta, args.last_setup, disk=args.disk, sprint_fix=args.sprint_fix,
                     relax_circle=args.relax_circle, fix=fix, min_slack=args.min_slack, margins=margins,
                     trust=trust, trust_k=args.trust_k, trust_theta=args.trust_theta,
                     free_sprint=args.free_sprint, ja_tick=args.ja_tick,
                     fix_theta=args.fix_theta, fix_theta2=args.fix_theta2,
                     trust_turn=args.trust_turn, min_edges=args.min_edges,
                     max_margin=args.max_margin, max_edges=args.max_edges)
    if args.mip_start:
        with open(args.mip_start) as f:
            start_vals = json.load(f)
        loaded = 0
        for v in m.getVars():
            if v.name in start_vals:
                m.setMipStart(v, start_vals[v.name])
                loaded += 1
        m.loadMipStart()
        print(f'mip start: {loaded} vars')
    m.setParam(COPT.Param.TimeLimit, args.time)
    m.setParam(COPT.Param.FeasTol, args.feastol)
    if args.gap is not None:
        m.setParam(COPT.Param.RelGap, args.gap)
    try:
        m.setParam(COPT.Param.NonConvex, 2)
    except Exception:
        pass
    for p in ('HeurLevel', 'DivingHeurLevel', 'RoundingHeurLevel', 'SubMipHeurLevel', 'PreRootHeurLevel'):
        try:
            m.setParam(getattr(COPT.Param, p), 3)
        except Exception:
            pass
    t0 = time.time()
    m.solve()
    dt = time.time() - t0

    res = {'status': m.status, 'time': dt}
    have = coptlib._try(lambda: m.getAttr(COPT.Attr.HasMipSol))
    have = bool(have) if have is not None else (m.status == COPT.OPTIMAL)
    if have and args.dump_vars:
        vals = {}
        for v in m.getVars():
            vals[v.name] = v.x
        with open(args.dump_vars, 'w') as f:
            json.dump(vals, f)
        print('vars ->', args.dump_vars)
    if have and args.min_slack:
        res['slackTotal'] = m.objval
        bad = []
        for sg in vars_['slacks']:
            if sg.x > 1e-9:
                bad.append({'name': sg.name, 'slack': sg.x})
        res['slackWalls'] = bad
        bb = coptlib._try(lambda: m.getAttr(COPT.Attr.BestBnd))
        res['slackBound'] = bb
        dec = decode(d, vars_, base, delta)
        res['thetaDeg'] = dec['thetaDeg']
        res['setupCombos'] = dec['setupCombos']
        with open(args.out, 'w') as f:
            json.dump(dec, f, indent=1)
        with open(args.report, 'w') as f:
            json.dump(res, f, indent=1)
        print(json.dumps(res, indent=1))
        return
    if have:
        S = m.objval
        pos = d['objConst'] + (S if d['objMaximize'] else -S)
        res['S'] = S
        res['pos'] = pos
        bb = coptlib._try(lambda: m.getAttr(COPT.Attr.BestBnd))
        if bb is not None:
            res['S_bound'] = bb
            res['pos_bound'] = d['objConst'] + (bb if d['objMaximize'] else -bb)
        dec = decode(d, vars_, base, delta)
        a, b = dec['a'], dec['b']
        res['circleResid'] = abs(a * a + b * b - 1.0)
        res['abNorm'] = math.hypot(a, b)
        sphres = 0.0
        for t in vars_['turn']:
            vx = vars_['ux'][t].x
            vz = vars_['uz'][t].x
            lo, hi = vars_['turn_mag'][t]
            want = hi if dec['sprint'][t - 1] else lo
            sphres = max(sphres, abs(math.hypot(vx, vz) - want))
        res['maxTurnMagResid'] = sphres
        res['thetaDeg'] = dec['thetaDeg']
        res['px'] = dec['px']
        res['pz'] = dec['pz']
        res['setupCombos'] = dec['setupCombos']
        res['sprintEngage'] = next((i for i, v in enumerate(dec['sprint']) if v), None)
        with open(args.out, 'w') as f:
            json.dump(dec, f, indent=1)
        print('decode ->', args.out)
    with open(args.report, 'w') as f:
        json.dump(res, f, indent=1)
    print(json.dumps({k: v for k, v in res.items() if k not in ('setupCombos',)}, indent=1))
    if have:
        print('combos:', ' '.join(res['setupCombos']))


if __name__ == '__main__':
    main()
