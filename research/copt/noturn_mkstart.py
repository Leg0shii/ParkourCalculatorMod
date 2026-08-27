"""Build a (partial) MIP-start vars json for noturn_miqcp from a decode json: the discrete
schedule (combo deltas + sprint) plus theta/start. COPT completes the continuous rest at load."""

import argparse
import json
import math

COMBOS = ['NONE', 'W', 'WA', 'A', 'SA', 'S', 'SD', 'D', 'WD']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--decode', required=True)
    ap.add_argument('--last-setup', type=int, default=38)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()
    dec = json.load(open(args.decode))
    vals = {}
    vals['a'] = math.cos(math.radians(dec['thetaDeg']))
    vals['b'] = math.sin(math.radians(dec['thetaDeg']))
    if dec.get('theta2Deg') is not None:
        vals['a2'] = math.cos(math.radians(dec['theta2Deg']))
        vals['b2'] = math.sin(math.radians(dec['theta2Deg']))
    vals['px'] = dec['px']
    vals['pz'] = dec['pz']
    for t, on in enumerate(dec['sprint']):
        vals[f's{t}'] = 1.0 if on else 0.0
    for t in range(args.last_setup + 1):
        want = dec['setupCombos'][t]
        for c in COMBOS:
            vals[f'd{t}_{c}'] = 1.0 if c == want else 0.0
    with open(args.out, 'w') as f:
        json.dump(vals, f)
    print('->', args.out, len(vals), 'vars')


if __name__ == '__main__':
    main()
