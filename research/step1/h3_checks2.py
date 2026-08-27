import json
import math
import numpy as np

BASE = r'C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/research/copt/data/'


def load(name):
    return json.load(open(BASE + name))


def grid_argmax_detail(chain, trials=120, seed=11):
    rng = np.random.default_rng(seed)
    idx = np.arange(65536)
    if chain == 'legacy':
        table = np.sin(idx * math.pi * 2.0 / 65536).astype(np.float32)
        degs = np.arange(0.0, 360.0, 0.0005)
        rads = (degs.astype(np.float32) * np.float32(math.pi) / np.float32(180.0)).astype(np.float32)
        x = rads * np.float32(10430.378)
        si = x.astype(np.int64) & 65535
        ci = (x + np.float32(16384.0)).astype(np.int64) & 65535
    else:
        table = np.sin(idx / 10430.378350470453).astype(np.float32)
        degs = np.arange(0.0, 360.0, 0.0005)
        rads = (degs.astype(np.float32) * np.float32(math.pi) / np.float32(180.0)).astype(np.float32)
        x = rads.astype(np.float64) * 10430.378350470453
        si = x.astype(np.int64) & 65535
        ci = (x + 16384.0).astype(np.int64) & 65535
    sinv = table[si].astype(np.float64)
    cosv = table[ci].astype(np.float64)
    norm = np.sqrt(sinv * sinv + cosv * cosv)
    worst_dist = 0.0
    worst_gain = 0.0
    worst_norm = 0.0
    for _ in range(trials):
        g = rng.normal(size=2)
        g /= np.hypot(g[0], g[1])
        val = g[0] * (-sinv) + g[1] * cosv
        k = int(np.argmax(val))
        theta_star = math.degrees(math.atan2(g[0], g[1]))
        move_ang = math.degrees(math.atan2(-sinv[k], cosv[k]))
        diff = abs((move_ang - theta_star + 180.0) % 360.0 - 180.0)
        near = np.abs((degs - (theta_star % 360.0) + 180.0) % 360.0 - 180.0) < 0.0055
        gain = float(val[k] - np.max(val[near])) if near.any() else 0.0
        worst_dist = max(worst_dist, diff / (360.0 / 65536.0))
        worst_gain = max(worst_gain, gain)
        worst_norm = max(worst_norm, float(abs(norm[k] - 1.0)))
    print('%s chain: worst argmax offset %.2f buckets, worst H gain over nearest-anchor %.3e (rel), worst |pairNorm-1| at argmax %.3e, global |pairNorm-1| max %.3e' % (
        chain, worst_dist, worst_gain, worst_norm, float(np.max(np.abs(norm - 1.0)))))


def warm_S(struct_name, yaws_name):
    d = load(struct_name)
    y = load(yaws_name)
    yaws = y['yawsAbsDeg'] if isinstance(y, dict) and 'yawsAbsDeg' in y else y
    n = d['numTicks']
    S = 0.0
    for t in range(n):
        tk = d['ticks'][t]
        phi = tk['baseArg'] + math.radians(yaws[t])
        S += tk['cx'] * tk['mMag'] * math.cos(phi) + tk['cz'] * tk['mMag'] * math.sin(phi)
    return S


def dual_long(name, iters, seed=3):
    d = load(name)
    n = d['numTicks']
    ticks = d['ticks']
    cx = np.array([t['cx'] for t in ticks])
    cz = np.array([t['cz'] for t in ticks])
    m = np.array([t['mMag'] for t in ticks])
    walls = d['walls']
    M = len(walls)
    axis = np.array([w['axis'] for w in walls])
    coef = np.array([w['coef'][:n] + [0.0] * max(0, n - len(w['coef'])) for w in walls])
    bP = np.array([w['bPrime'] for w in walls])
    eq = np.array([w['eq'] for w in walls])
    EPS2 = 1.0e-14
    selX = (axis == 0).astype(float)
    selZ = (axis == 1).astype(float)

    def eval_at(lam):
        gx = cx - (lam * selX) @ coef
        gz = cz - (lam * selZ) @ coef
        nrm = np.sqrt(gx * gx + gz * gz + EPS2)
        D = float(np.sum(m * nrm) + lam @ bP)
        ux = m * gx / nrm
        uz = m * gz / nrm
        dot = coef @ (selX[:, None].T * 0).T if False else None
        dots = np.empty(M)
        for j in range(M):
            u = ux if axis[j] == 0 else uz
            dots[j] = coef[j] @ u
        grad = bP - dots
        viol = float(np.max(np.where(eq, np.abs(-grad), np.maximum(0.0, -grad))))
        proj = np.where(eq, lam - grad, np.maximum(0.0, lam - grad))
        pgres = float(np.max(np.abs(proj - lam)))
        return D, grad, pgres, viol, nrm

    lam = np.zeros(M)
    D0, g0, _, _, _ = eval_at(lam)
    s0 = 1.0 / max(1e-12, float(np.max(np.abs(g0))))
    bestD = D0
    bestLam = lam.copy()
    minViol = math.inf
    minPg = math.inf
    marks = {}
    for k in range(iters):
        D, grad, pgres, viol, _ = eval_at(lam)
        if D < bestD:
            bestD = D
            bestLam = lam.copy()
        minViol = min(minViol, viol)
        minPg = min(minPg, pgres)
        if k + 1 in (1000, 10000, 60000, iters):
            marks[k + 1] = (bestD, minViol, minPg)
        step = s0 / math.sqrt(k + 1.0)
        lam = np.where(eq, lam - step * grad, np.maximum(0.0, lam - step * grad))
    D, grad, pgres, viol, nrm = eval_at(bestLam)
    frac = nrm / np.sqrt(nrm * nrm + 0)
    print('%s M=%d n=%d' % (name, M, n))
    for kk in sorted(marks):
        bD, mV, mP = marks[kk]
        print('  iters=%-7d bestD=%.6f minViol=%.4f minPg=%.4f' % (kk, bD, mV, mP))
    print('  at bestLam: pg=%.4f viol=%.4f minCostate=%.3e medCostate=%.3e maxLam=%.2f' % (
        pgres, viol, float(np.min(nrm)), float(np.median(nrm)), float(np.max(bestLam))))
    return bestD


if __name__ == '__main__':
    grid_argmax_detail('legacy')
    grid_argmax_detail('262')
    bD = dual_long('struct-loopmm-3jump-lands.json', 300000)
    try:
        S = warm_S('struct-loopmm-3jump-lands.json', 'yaws-loopmm-3jump-lands.json')
        print('loopmm warm S=%.6f dualBestD=%.6f gap=%.6f' % (S, bD, bD - S))
    except Exception as e:
        print('warm S failed:', e)
    dual_long('struct-f2f-dfchain-multijump.json', 300000)
    dual_long('struct-j005.json', 60000)
