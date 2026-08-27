"""SLP-0 margin computation for the no-turn MIQCP.

Reconstructs the linear-model wall values of a decoded no-turn (combos + theta + sprint + turn
inputs) and compares them against the byte-exact replay positions. Emits per-wall SIGNED margins
(byte-exact minus linear, in the <=-normalized lhs frame) so the next solve's walls are
translated onto the byte-exact anchor instead of shrunk by guessed safety margins:

  lhs_linear <= bPrime - margin   with   margin = viol_byte - viol_linear
"""

import argparse
import json
import math

COMBO_VEC = {
    'NONE': None,
    'W': (90.0, 0.98), 'WA': (45.0, 1.0), 'A': (0.0, 0.98), 'SA': (-45.0, 1.0),
    'S': (-90.0, 0.98), 'SD': (-135.0, 1.0), 'D': (180.0, 0.98), 'WD': (135.0, 1.0),
}


def rebuild_inputs(d, dec, base, delta, last_setup, ja_tick=None):
    n = d['numTicks']
    contact = [d['ticks'][t]['contact'] for t in range(n)]
    jump = [d['ticks'][t]['jump'] for t in range(n)]
    s = dec['sprint']
    a = math.cos(math.radians(dec['thetaDeg']))
    b = math.sin(math.radians(dec['thetaDeg']))
    a2, b2 = a, b
    if ja_tick is not None and dec.get('theta2Deg') is not None:
        a2 = math.cos(math.radians(dec['theta2Deg']))
        b2 = math.sin(math.radians(dec['theta2Deg']))
    ux = [0.0] * n
    uz = [0.0] * n
    for t in range(last_setup + 1):
        aa, bb = (a2, b2) if t == ja_tick else (a, b)
        combo = dec['setupCombos'][t]
        e = s[t] if (t == 0 or contact[t]) else s[t - 1]
        acc = base[t] + (delta[t] if e else 0.0)
        cv = COMBO_VEC[combo]
        if cv is not None:
            phi, kappa = cv
            r = math.radians(phi)
            ux[t] += kappa * acc * (math.cos(r) * aa - math.sin(r) * bb)
            uz[t] += kappa * acc * (math.sin(r) * aa + math.cos(r) * bb)
        if jump[t] and s[t]:
            ux[t] += -0.2 * bb
            uz[t] += 0.2 * aa
    for note in dec.get('turn', []):
        t = note['t']
        phi = 45.0 if note['combo'] == 'WA' else 135.0
        arg = math.radians(note['yaw'] + phi)
        ux[t] = note['mag'] * math.cos(arg)
        uz[t] = note['mag'] * math.sin(arg)
    return ux, uz


def wall_lhs_linear(d, w, ux, uz, px, pz):
    n = d['numTicks']
    var = ux if w['axis'] == 0 else uz
    lhs = sum(w['coef'][t] * var[t] for t in range(n))
    if w['p0coef'] != 0.0:
        p = px if w['axis'] == 0 else pz
        ref = d['startBox']['px'] if w['axis'] == 0 else d['startBox']['pz']
        lhs -= w['p0coef'] * (p - ref)
    return lhs


def wall_viol_byte(d, w, replay):
    cons = {c['name']: c for c in d['constraints']}
    c = cons[w['name']]
    pos = replay['posX'] if c['mode'] == 'X' else replay['posZ']
    val = pos[c['t1']]
    if c.get('t2') is not None:
        val += (1.0 if c['op'] == 'PLUS' else -1.0) * pos[c['t2']]
    if c['cmp'] == 'GE':
        return c['rhs'] - val
    return val - c['rhs']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--struct', default='data/struct-j1150-inputs-gone.json')
    ap.add_argument('--probe-on', default='data/struct-j1150-probe-w-sprint.json')
    ap.add_argument('--probe-off', default='data/struct-j1150-probe-w-nosprint.json')
    ap.add_argument('--decode', required=True)
    ap.add_argument('--replay', required=True)
    ap.add_argument('--last-setup', type=int, default=38)
    ap.add_argument('--ja-tick', type=int, default=None)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()

    d = json.load(open(args.struct))
    on = json.load(open(args.probe_on))
    off = json.load(open(args.probe_off))
    dec = json.load(open(args.decode))
    replay = json.load(open(args.replay))
    n = d['numTicks']
    base = [off['ticks'][t]['forwardMag'] / 0.98 for t in range(n)]
    delta = [on['ticks'][t]['forwardMag'] / 0.98 - base[t] for t in range(n)]

    ux, uz = rebuild_inputs(d, dec, base, delta, args.last_setup, ja_tick=args.ja_tick)
    cons_names = {c['name'] for c in d['constraints']}
    margins = {}
    rows = []
    for w in d['walls']:
        if w['name'] not in cons_names:
            continue
        lin = wall_lhs_linear(d, w, ux, uz, dec['px'], dec['pz'])
        vlin = lin - w['bPrime']
        vbyte = wall_viol_byte(d, w, replay)
        margins[w['name']] = vbyte - vlin
        rows.append((w['name'], vlin, vbyte, vbyte - vlin))
    with open(args.out, 'w') as f:
        json.dump(margins, f, indent=1)
    print(f"{'wall':10s} {'violLinear':>12s} {'violByte':>12s} {'margin':>12s}")
    for name, vlin, vbyte, mg in rows:
        print(f'{name:10s} {vlin:12.6f} {vbyte:12.6f} {mg:12.6f}')
    print('->', args.out)


if __name__ == '__main__':
    main()
