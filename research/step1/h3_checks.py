import json
import math
import numpy as np

BASE = r'C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/research/copt/data/'


def load(name):
    return json.load(open(BASE + name))


def check1_recursion(name):
    d = load(name)
    n = d['numTicks']
    ticks = d['ticks']
    f4 = [t['f4'] for t in ticks]
    ax = 0 if d['objAxisName'] == 'X' else 1
    sgn = 1.0 if d['objMaximize'] else -1.0
    c = [t['cx'] if ax == 0 else t['cz'] for t in ticks]
    objTick = d['objTick']
    W = [0.0] * (n + 1)
    for s in range(min(objTick, n) - 1, -1, -1):
        W[s] = 1.0 + f4[s] * W[s + 1]
    worst = 0.0
    for t in range(n):
        want = sgn * (W[t] if t < objTick else 0.0)
        worst = max(worst, abs(want - c[t]))
    print('check1 %-40s n=%d objTick=%d max|recursion-cx|=%.3e' % (name, n, objTick, worst))


def check2_grid_argmax(trials=200, seed=7):
    rng = np.random.default_rng(seed)
    scale = np.float32(10430.378)
    idx = np.arange(65536)
    table = np.sin(idx * math.pi * 2.0 / 65536).astype(np.float32)
    degs = np.arange(0.0, 360.0, 0.0005)
    rads = (degs * np.float32(math.pi) / np.float32(180.0)).astype(np.float32)
    si = (rads * scale).astype(np.int64) & 65535
    ci = (rads * scale + np.float32(16384.0)).astype(np.int64) & 65535
    sinv = table[si].astype(np.float64)
    cosv = table[ci].astype(np.float64)
    worst_bucket_dist = 0.0
    for _ in range(trials):
        g = rng.normal(size=2)
        g /= np.hypot(g[0], g[1])
        val = g[0] * (-sinv) + g[1] * cosv
        k = int(np.argmax(val))
        theta_star = math.degrees(math.atan2(g[0], g[1]))
        move_ang = math.degrees(math.atan2(-sinv[k], cosv[k]))
        diff = abs((move_ang - theta_star + 180.0) % 360.0 - 180.0)
        worst_bucket_dist = max(worst_bucket_dist, diff / (360.0 / 65536.0))
    print('check2 grid argmax: worst |argmax_dir - costate_dir| = %.3f buckets (bucket=0.00549 deg)' % worst_bucket_dist)


def dual_run(name, iters=60000, seed=3):
    d = load(name)
    n = d['numTicks']
    ticks = d['ticks']
    cx = np.array([t['cx'] for t in ticks])
    cz = np.array([t['cz'] for t in ticks])
    m = np.array([t['mMag'] for t in ticks])
    walls = d['walls']
    M = len(walls)
    axis = np.array([w['axis'] for w in walls])
    coef = np.array([w['coef'][:n] + [0.0] * (n - len(w['coef'][:n])) for w in walls])
    bP = np.array([w['bPrime'] for w in walls])
    eq = np.array([w['eq'] for w in walls])
    EPS2 = 1.0e-14

    def eval_at(lam):
        gx = cx - (lam * (axis == 0)) @ coef
        gz = cz - (lam * (axis == 1)) @ coef
        nrm = np.sqrt(gx * gx + gz * gz + EPS2)
        D = float(np.sum(m * nrm) + lam @ bP)
        ux = m * gx / nrm
        uz = m * gz / nrm
        dot = coef @ np.where(axis[:, None] == 0, ux, uz).T
        dot = np.array([coef[j] @ (ux if axis[j] == 0 else uz) for j in range(M)])
        grad = bP - dot
        viol = float(np.max(np.where(eq, np.abs(-grad), np.maximum(0.0, -grad))))
        proj = np.where(eq, lam - grad, np.maximum(0.0, lam - grad))
        pgres = float(np.max(np.abs(proj - lam)))
        return D, grad, pgres, viol, nrm

    lam = np.zeros(M)
    D0, g0, _, _, _ = eval_at(lam)
    s0 = 1.0 / max(1e-12, float(np.max(np.abs(g0))))
    bestD = D0
    bestLam = lam.copy()
    minPg = math.inf
    minViol = math.inf
    for k in range(iters):
        D, grad, pgres, viol, _ = eval_at(lam)
        if D < bestD:
            bestD = D
            bestLam = lam.copy()
        minPg = min(minPg, pgres)
        minViol = min(minViol, viol)
        step = s0 / math.sqrt(k + 1.0)
        lam = np.where(eq, lam - step * grad, np.maximum(0.0, lam - step * grad))
    D, grad, pgres, viol, nrm = eval_at(bestLam)
    tiny = int(np.sum(nrm < 1e-3 * float(np.median(nrm))))
    print('dual   %-40s n=%d M=%d bestD=%.6f pg@best=%.3e minPg=%.3e minViol=%.3e tinyCostates=%d' % (
        name, n, M, bestD, pgres, minPg, minViol, tiny))


if __name__ == '__main__':
    for f in ['struct-j005.json', 'struct-j019-3jmmtruenix.json', 'struct-loopmm-3jump-lands.json',
              'struct-f2f-dfchain-multijump.json', 'struct-j155-4jmm_3bcmm_4.9375b.json']:
        check1_recursion(f)
    check2_grid_argmax()
    for f in ['struct-j005.json', 'struct-j019-3jmmtruenix.json', 'struct-loopmm-3jump-lands.json',
              'struct-f2f-dfchain-multijump.json', 'struct-j155-4jmm_3bcmm_4.9375b.json']:
        dual_run(f)
