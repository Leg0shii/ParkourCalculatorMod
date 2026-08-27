import json, os, glob, math

DATA = os.path.join(os.path.dirname(__file__), '..', 'copt', 'data')

REAL = [
 'struct-hpk-j1150-2x2bm_Nix_Neo.json',
 'struct-j154.json',
 'struct-hpk-j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo.json',
 'struct-j021-rinav1-01.json',
 'struct-j345_3jmm_True_Nix_Neo.json',
 'struct-j153_Flat_Momentum_Head_to_Head_Neo.json',
 'struct-hpk-j155-4jmm_3bcmm_4.9375b.json',
 'struct-j757_1bmhh_4.0625_1.json',
 'struct-hpk-j718-2bm__Z__Pane_to_Pane_Neo.json',
 'struct-loopmm-3jump-lands.json',
]

def cosrange(center, half):
    # min/max of cos over [center-half, center+half]
    lo = center-half; hi = center+half
    vals = [math.cos(lo), math.cos(hi)]
    # include extrema 0 (max cos=1) and pi (min cos=-1) if inside
    k0 = math.ceil(lo/ (2*math.pi))*2*math.pi
    m = k0
    while m <= hi:
        vals.append(math.cos(m)); m += math.pi  # every multiple of pi is an extremum
    return min(vals), max(vals)

def k_at_delta(d, deltadeg):
    n=d['numTicks']; ticks=d['ticks']; ivx,ivz=d['initialVelocity']
    per=d['perAxisInertia']; eps=d['inertiaThreshold']; gf=d['warmGameFacings']
    half=math.radians(deltadeg)
    kx=kz=0
    # per axis independent interval
    lox=hix=ivx; loz=hiz=ivz
    ambX=[]; ambZ=[]
    for t in range(n):
        m=ticks[t]['mMag']; ba=ticks[t]['baseArg']; f4=ticks[t]['f4']
        # gate X
        can_fire=(lox<eps) and (hix>-eps); can_not=(hix>=eps) or (lox<=-eps)
        if can_fire and can_not: ambX.append(t); gxlo=min(lox,0.0); gxhi=max(hix,0.0)
        elif can_fire: gxlo=gxhi=0.0
        else: gxlo,gxhi=lox,hix
        can_fire=(loz<eps) and (hiz>-eps); can_not=(hiz>=eps) or (loz<=-eps)
        if can_fire and can_not: ambZ.append(t); gzlo=min(loz,0.0); gzhi=max(hiz,0.0)
        elif can_fire: gzlo=gzhi=0.0
        else: gzlo,gzhi=loz,hiz
        # u_t X = m*cos(ba+theta); Z = m*sin(ba+theta) = m*cos(ba+theta-90)
        cen=ba+math.radians(gf[t])
        cxlo,cxhi=cosrange(cen,half)
        szlo,szhi=cosrange(cen-math.pi/2,half)
        uxlo,uxhi=m*cxlo,m*cxhi
        uzlo,uzhi=m*szlo,m*szhi
        lox=f4*(gxlo+uxlo); hix=f4*(gxhi+uxhi)
        loz=f4*(gzlo+uzlo); hiz=f4*(gzhi+uzhi)
    return len(ambX),len(ambZ)

deltas=[45,20,10,5,2,1,0.5,0.1,0.01]
print("%-52s %4s "%('file','n')+" ".join("d=%g"%x for x in deltas))
for f in REAL:
    p=os.path.join(DATA,f)
    if not os.path.exists(p):
        print(f,"MISSING"); continue
    d=json.load(open(p))
    if 'warmGameFacings' not in d:
        print(f,"nowarm"); continue
    n=d['numTicks']
    ks=[sum(k_at_delta(d,dd)) for dd in deltas]
    print("%-52s %4d "%(os.path.basename(f),n)+" ".join("%5d"%k for k in ks))
