import sys, math

TIE = 1e-4
YAWLOCK_TOL = 1e-9

def load(path):
    rows = {}
    with open(path, encoding='utf-8') as fh:
        header = fh.readline().rstrip('\n').split('\t')
        idx = {h: i for i, h in enumerate(header)}
        for line in fh:
            p = line.rstrip('\n').split('\t')
            if len(p) < len(header):
                continue
            name = p[0]
            def g(k, cast=str, d=None):
                v = p[idx[k]] if k in idx and idx[k] < len(p) else ''
                if v == '' or v == 'EXC':
                    return d
                try:
                    return cast(v)
                except Exception:
                    return d
            rows[name] = {
                'success': p[idx['success']] == 'true',
                'raw_success': p[idx['success']],
                'sense': g('sense'),
                'hasObj': g('hasObj') == 'true',
                'shippedObj': g('shippedObj', float, math.nan),
                'recertObj': g('recertObj', float, math.nan),
                'recertViol': g('recertViol', float, math.nan),
                'coldMs': g('coldMs', int, -1),
                'warmMedMs': g('warmMedMs', int, -1),
                'smoothLambda': g('smoothLambda', float, 0.0),
                'n': g('n', int, -1),
            }
    return rows

def better_delta(sense, old, new):
    if sense == 'MAX':
        return new - old
    return old - new  # MIN: lower is better

def main():
    tier = sys.argv[1]
    old = load(sys.argv[2])
    new = load(sys.argv[3])
    keys = sorted(set(old) & set(new))
    only_old = sorted(set(old) - set(new))
    only_new = sorted(set(new) - set(old))

    feas_reg = []      # OLD feasible, NEW infeasible (HARD GATE)
    feas_gain = []     # OLD infeasible, NEW feasible
    wins = []
    reg = []           # objective regressions (both feasible)
    ties = 0
    yawlock_new = []
    yawlock_old = []
    smooth_rows = []
    speed = []         # (name, oldCold, newCold, oldWarm, newWarm)

    for k in keys:
        o, n = old[k], new[k]
        # feasibility
        if o['success'] and not n['success']:
            feas_reg.append(k)
        if not o['success'] and n['success']:
            feas_gain.append(k)
        # yaw-lock divergence (per tree, only meaningful for feasible+obj)
        for tag, r, lst in (('new', n, yawlock_new), ('old', o, yawlock_old)):
            if r['success'] and r['hasObj'] and not math.isnan(r['shippedObj']) and not math.isnan(r['recertObj']):
                if abs(r['shippedObj'] - r['recertObj']) > YAWLOCK_TOL:
                    lst.append((k, r['shippedObj'], r['recertObj'], r['shippedObj'] - r['recertObj']))
        # objective classification (both feasible + both hasObj)
        if o['success'] and n['success'] and o['hasObj'] and n['hasObj'] \
                and not math.isnan(o['shippedObj']) and not math.isnan(n['shippedObj']):
            sense = n['sense'] or o['sense'] or 'MAX'
            d = better_delta(sense, o['shippedObj'], n['shippedObj'])
            if d > TIE:
                wins.append((k, o['shippedObj'], n['shippedObj'], d))
            elif d < -TIE:
                reg.append((k, o['shippedObj'], n['shippedObj'], d))
            else:
                ties += 1
            if (o['smoothLambda'] or 0) > 0 or (n['smoothLambda'] or 0) > 0:
                smooth_rows.append((k, o['shippedObj'], n['shippedObj'], d))
        # speed
        speed.append((k, o['coldMs'], n['coldMs'], o['warmMedMs'], n['warmMedMs']))

    print(f"===== TIER {tier} =====")
    print(f"captures compared: {len(keys)}  only-OLD: {len(only_old)}  only-NEW: {len(only_new)}")
    if only_old: print("  only-OLD:", ", ".join(only_old[:20]) + (" ..." if len(only_old) > 20 else ""))
    if only_new: print("  only-NEW:", ", ".join(only_new[:20]) + (" ..." if len(only_new) > 20 else ""))
    print()
    print(f"FEASIBILITY REGRESSIONS (HARD GATE, must be 0): {len(feas_reg)}")
    for k in feas_reg: print("   !! ", k)
    print(f"feasibility gains (OLD infeasible -> NEW feasible): {len(feas_gain)}")
    for k in feas_gain: print("   ++ ", k)
    print()
    print(f"OBJECTIVE  wins={len(wins)}  regressions={len(reg)}  ties(<= {TIE})={ties}")
    if reg:
        print("  --- OBJECTIVE REGRESSIONS (NEW worse) ---")
        for k, oo, nn, d in sorted(reg, key=lambda x: x[3]):
            print(f"    {k:52s} OLD={oo:.6f} NEW={nn:.6f} deltaBetter={d:+.2e}")
    if wins:
        print("  --- OBJECTIVE WINS (NEW better), top 15 ---")
        for k, oo, nn, d in sorted(wins, key=lambda x: -x[3])[:15]:
            print(f"    {k:52s} OLD={oo:.6f} NEW={nn:.6f} deltaBetter={d:+.2e}")
    print()
    print(f"YAW-LOCK-DEPENDENT (shipped != recompute) NEW: {len(yawlock_new)}  OLD: {len(yawlock_old)}")
    for k, s, r, d in yawlock_new: print(f"    NEW {k:48s} shipped={s:.6f} recompute={r:.6f} div={d:+.2e}")
    print()
    if smooth_rows:
        print(f"smoothLambda>0 captures: {len(smooth_rows)}")
        for k, oo, nn, d in smooth_rows:
            print(f"    {k:52s} OLD={oo:.6f} NEW={nn:.6f} deltaBetter={d:+.2e}")
        print()
    # speed summary
    valid = [(k, oc, nc, ow, nw) for k, oc, nc, ow, nw in speed if oc >= 0 and nc >= 0]
    if valid:
        old_cold = sorted(x[1] for x in valid)
        new_cold = sorted(x[2] for x in valid)
        def pct(a, p): return a[min(len(a)-1, int(len(a)*p))]
        print("SPEED cold FAST (ms): OLD median={} p90={} max={}  NEW median={} p90={} max={}".format(
            pct(old_cold,0.5), pct(old_cold,0.9), old_cold[-1],
            pct(new_cold,0.5), pct(new_cold,0.9), new_cold[-1]))
        worse = sorted([(k, oc, nc, nc-oc) for k,oc,nc,ow,nw in valid if nc-oc > 200], key=lambda x:-x[3])
        if worse:
            print("  cold slower on NEW by >200ms:")
            for k,oc,nc,d in worse[:15]: print(f"    {k:52s} OLD={oc}ms NEW={nc}ms (+{d}ms)")
        has_warm = [(k,ow,nw) for k,oc,nc,ow,nw in valid if ow>=0 and nw>=0]
        if has_warm and any(ow>0 for _,ow,_ in has_warm):
            ow_s = sorted(x[1] for x in has_warm); nw_s = sorted(x[2] for x in has_warm)
            print("SPEED warm-med (ms): OLD median={} p90={} max={}  NEW median={} p90={} max={}".format(
                pct(ow_s,0.5),pct(ow_s,0.9),ow_s[-1], pct(nw_s,0.5),pct(nw_s,0.9),nw_s[-1]))

if __name__ == '__main__':
    main()
