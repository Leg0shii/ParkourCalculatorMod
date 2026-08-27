import json, os, glob, math, sys

DATA = os.path.join(os.path.dirname(__file__), '..', 'copt', 'data')

def load(p):
    return json.load(open(p))

def replay_warm(d):
    n = d['numTicks']
    ticks = d['ticks']
    ivx, ivz = d['initialVelocity']
    px, pz = d['startPos']
    gf = d['warmGameFacings']
    vx, vz = ivx, ivz
    X, Z = px, pz
    per = d['perAxisInertia']
    eps = d['inertiaThreshold']
    for t in range(n):
        tk = ticks[t]
        m = tk['mMag']; ba = tk['baseArg']; f4 = tk['f4']
        if per:
            if abs(vx) < eps: vx = 0.0
            if abs(vz) < eps: vz = 0.0
        else:
            if vx*vx+vz*vz < eps*eps: vx = 0.0; vz = 0.0
        phi = ba + math.radians(gf[t])
        vx += m*math.cos(phi)
        vz += m*math.sin(phi)
        X += vx; Z += vz
        vx *= f4; vz *= f4
    axis = d['objAxis']
    return X if axis==0 else Z

def fbbt_forward(d):
    n = d['numTicks']
    ticks = d['ticks']
    ivx, ivz = d['initialVelocity']
    per = d['perAxisInertia']
    eps = d['inertiaThreshold']
    m = [ticks[t]['mMag'] for t in range(n)]
    f4 = [ticks[t]['f4'] for t in range(n)]
    ambig = {'x':[], 'z':[]}
    fired = {'x':0,'z':0}
    notfired = {'x':0,'z':0}
    if per:
        for axis, iv, key in ((0, ivx, 'x'), (1, ivz, 'z')):
            lo = iv; hi = iv
            for t in range(n):
                can_fire = (lo < eps) and (hi > -eps)
                can_not = (hi >= eps) or (lo <= -eps)
                if can_fire and can_not:
                    ambig[key].append(t)
                    glo = min(lo, 0.0); ghi = max(hi, 0.0)
                elif can_fire:
                    fired[key]+=1
                    glo = 0.0; ghi = 0.0
                else:
                    notfired[key]+=1
                    glo = lo; ghi = hi
                wlo = glo - m[t]; whi = ghi + m[t]
                lo = f4[t]*wlo; hi = f4[t]*whi
        return ambig, fired, notfired
    else:
        # combined-XZ gate: box [xlo,xhi]x[zlo,zhi], gate fires if x^2+z^2 < eps^2
        xlo=xhi=ivx; zlo=zhi=ivz
        amb=[]; fir=0; nfr=0
        for t in range(n):
            # min dist^2 from box to origin
            dx = 0.0 if (xlo<=0<=xhi) else min(abs(xlo),abs(xhi))
            dz = 0.0 if (zlo<=0<=zhi) else min(abs(zlo),abs(zhi))
            dmin2 = dx*dx+dz*dz
            dxm = max(abs(xlo),abs(xhi)); dzm=max(abs(zlo),abs(zhi))
            dmax2 = dxm*dxm+dzm*dzm
            can_fire = dmin2 < eps*eps
            can_not = dmax2 >= eps*eps
            if can_fire and can_not:
                amb.append(t)
                gxlo=min(xlo,0.0);gxhi=max(xhi,0.0);gzlo=min(zlo,0.0);gzhi=max(zhi,0.0)
            elif can_fire:
                fir+=1; gxlo=gxhi=gzlo=gzhi=0.0
            else:
                nfr+=1; gxlo,gxhi,gzlo,gzhi=xlo,xhi,zlo,zhi
            xlo=f4[t]*(gxlo-m[t]); xhi=f4[t]*(gxhi+m[t])
            zlo=f4[t]*(gzlo-m[t]); zhi=f4[t]*(gzhi+m[t])
        return {'x':amb,'z':[]}, {'x':fir,'z':0}, {'x':nfr,'z':0}

def warm_gate_events(d):
    # actual gate firings on the recorded warm solution (ground truth pattern)
    n=d['numTicks']; ticks=d['ticks']
    ivx,ivz=d['initialVelocity']; per=d['perAxisInertia']; eps=d['inertiaThreshold']
    gf=d['warmGameFacings']
    vx,vz=ivx,ivz; ex=[]; ez=[]
    for t in range(n):
        m=ticks[t]['mMag']; ba=ticks[t]['baseArg']; f4=ticks[t]['f4']
        if per:
            if abs(vx)<eps: vx=0.0; ex.append(t)
            if abs(vz)<eps: vz=0.0; ez.append(t)
        else:
            if vx*vx+vz*vz<eps*eps: vx=0.0;vz=0.0; ex.append(t)
        phi=ba+math.radians(gf[t]); vx+=m*math.cos(phi); vz+=m*math.sin(phi)
        vx*=f4; vz*=f4
    return ex,ez

files = sorted(glob.glob(os.path.join(DATA,'struct-*.json')))
skip = ('probe','-pat','pattern')
rows=[]
for f in files:
    base=os.path.basename(f)
    d=load(f)
    if 'warmGameFacings' not in d:
        continue
    n=d['numTicks']
    try:
        wo=replay_warm(d)
    except Exception as e:
        continue
    err=abs(wo-d['warmObj'])
    ambig,fired,notfired=fbbt_forward(d)
    ex,ez=warm_gate_events(d)
    kx=len(ambig['x']); kz=len(ambig['z'])
    rows.append((base,n,d['perAxisInertia'],d['inertiaThreshold'],kx,kz,kx+kz,
                 fired['x']+fired['z'],notfired['x']+notfired['z'],
                 len(ex)+len(ez),err))

print("%-52s %4s %3s %7s %3s %3s %4s %5s %6s %6s %9s"%('file','n','per','eps','kx','kz','ktot','fire','nofire','warmG','replayErr'))
for r in rows:
    print("%-52s %4d %3s %7.4g %3d %3d %4d %5d %6d %6d %9.2e"%(
        r[0],r[1],'Y' if r[2] else 'N',r[3],r[4],r[5],r[6],r[7],r[8],r[9],r[10]))

print("\n# ambiguous tick lists (ktot>0):")
for f in files:
    base=os.path.basename(f); d=load(f)
    if 'warmGameFacings' not in d: continue
    ambig,fired,notfired=fbbt_forward(d)
    if len(ambig['x'])+len(ambig['z'])>0:
        ex,ez=warm_gate_events(d)
        print("%-52s ambigX=%s ambigZ=%s | warmFireX=%s warmFireZ=%s"%(base,ambig['x'],ambig['z'],ex,ez))
