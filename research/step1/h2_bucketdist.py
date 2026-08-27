import json
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'copt'))
import coptlib as C
from coptpy import COPT

DATA = os.path.join(os.path.dirname(__file__), '..', 'copt', 'data')
BW = 360.0 / 65536.0

CAPS = [
    'struct-j005.json',
    'struct-j019-3jmmtruenix.json',
    'struct-j021-rinav1-01.json',
    'struct-j144_1bm_5-1_Triple_Neo.json',
    'struct-j155-4jmm_3bcmm_4.9375b.json',
    'struct-hpk-j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo.json',
    'struct-hpk-j718-2bm__Z__Pane_to_Pane_Neo.json',
    'struct-j757_1bmhh_4.0625_1.json',
    'struct-j345_3jmm_True_Nix_Neo.json',
    'struct-loopmm-3jump-lands.json',
]


def pct(vals, p):
    if not vals:
        return None
    s = sorted(vals)
    i = min(len(s) - 1, int(round(p / 100.0 * (len(s) - 1))))
    return s[i]


def run(name):
    d = C.load(os.path.join(DATA, name))
    if 'warmGameFacings' not in d:
        return {'capture': name, 'skip': 'no warm'}
    n = d['numTicks']
    m = C.env().createModel('sph_theta')
    m.setParam(COPT.Param.Logging, 0)
    m.setParam(COPT.Param.TimeLimit, 180.0)
    m.setParam(COPT.Param.FeasTol, 1e-9)
    try:
        m.setParam(COPT.Param.NonConvex, 2)
    except Exception:
        pass
    ax, az, mMag = C._build_common(m, d)
    for t in range(n):
        m.addQConstr(ax[t] * ax[t] + az[t] * az[t] == mMag[t] * mMag[t], name='sph%d' % t)
    m.solve()
    out = {'capture': name, 'n': n, 'status': m.status}
    try:
        S = m.objval
    except Exception:
        out['skip'] = 'no incumbent'
        return out
    out['S'] = S
    out['pos'] = C._pos_from_S(d, S)
    try:
        out['gap'] = m.getAttr(COPT.Attr.BestGap)
    except Exception:
        pass
    out['warmObj'] = d.get('warmObj')
    gf = d['warmGameFacings']
    rows = []
    for t in range(n):
        tk = d['ticks'][t]
        if tk['mMag'] <= 0:
            continue
        ux, uz = ax[t].x, az[t].x
        if math.hypot(ux, uz) < 1e-9:
            continue
        theta = math.atan2(uz, ux)
        yaw = math.degrees(theta - tk['baseArg'])
        diff = (yaw - gf[t] + 180.0) % 360.0 - 180.0
        rows.append({'t': t, 'buckets': abs(diff) / BW, 'contYaw': yaw, 'warmYaw': gf[t]})
    w = [r['buckets'] for r in rows]
    out['ticksMeasured'] = len(w)
    out['W_max'] = max(w) if w else None
    out['W_p90'] = pct(w, 90)
    out['W_median'] = pct(w, 50)
    out['worst5'] = sorted(rows, key=lambda r: -r['buckets'])[:5]
    return out


if __name__ == '__main__':
    results = []
    for c in CAPS:
        try:
            r = run(c)
        except Exception as e:
            r = {'capture': c, 'error': repr(e)}
        results.append(r)
        print(json.dumps({k: v for k, v in r.items() if k != 'worst5'}, default=str))
        sys.stdout.flush()
    with open(os.path.join(os.path.dirname(__file__), 'h2-bucketdist.json'), 'w') as f:
        json.dump(results, f, indent=1, default=str)
    print('WROTE h2-bucketdist.json')
