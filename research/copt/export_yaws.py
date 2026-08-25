import json, math, sys
import coptlib as C
from coptpy import COPT, quicksum

def qcqp_yaws(cap):
    d = C.load(f'data/struct-{cap}.json')
    n = d['numTicks']; mMag=[d['ticks'][t]['mMag'] for t in range(n)]
    m = C.env().createModel('q'); m.setParam(COPT.Param.Logging,0); m.setParam(COPT.Param.TimeLimit,120)
    m.setParam(COPT.Param.FeasTol,1e-9)
    try: m.setParam(COPT.Param.NonConvex,2)
    except: pass
    ax=[m.addVar(lb=-mMag[t],ub=mMag[t]) for t in range(n)]
    az=[m.addVar(lb=-mMag[t],ub=mMag[t]) for t in range(n)]
    free=bool(d.get('startBox') and d['startBox'].get('startFree')); px=pz=None
    if free:
        sb=d['startBox']; px=m.addVar(lb=sb['pxLo'],ub=sb['pxHi']); pz=m.addVar(lb=sb['pzLo'],ub=sb['pzHi'])
    for t in range(n): m.addQConstr(ax[t]*ax[t]+az[t]*az[t]==mMag[t]*mMag[t])
    for w in d['walls']:
        var=ax if w['axis']==0 else az
        e=quicksum(w['coef'][s]*var[s] for s in range(n) if w['coef'][s]!=0.0)
        if free and w['p0coef']!=0.0:
            sv=px if w['axis']==0 else pz; ref=d['startBox']['px'] if w['axis']==0 else d['startBox']['pz']
            e=e-w['p0coef']*(sv-ref)
        m.addConstr(e==w['bPrime'] if w['eq'] else e<=w['bPrime'])
    S=quicksum(d['ticks'][t]['cx']*ax[t]+d['ticks'][t]['cz']*az[t] for t in range(n))
    if free:
        sv=px if d['objAxis']==0 else pz; ref=d['startBox']['px'] if d['objAxis']==0 else d['startBox']['pz']
        S=S+(1.0 if d['objMaximize'] else -1.0)*(sv-ref)
    m.setObjective(S,sense=COPT.MAXIMIZE); m.solve()
    if m.status!=COPT.OPTIMAL: return None
    yaws=[]
    for t in range(n):
        phi=math.atan2(az[t].x, ax[t].x)
        yaw=math.degrees(phi - d['ticks'][t]['baseArg'])
        yaw=((yaw+180)%360)-180
        yaws.append(yaw)
    out={'capture':cap,'contPos':d['objConst']+(m.objval if d['objMaximize'] else -m.objval),'yawsDeg':yaws}
    if free: out['startX']=px.x; out['startZ']=pz.x
    json.dump(out, open(f'data/yaws-{cap}.json','w'), indent=1)
    print(f"{cap}: contPos={out['contPos']:.9g} wrote data/yaws-{cap}.json")

for cap in sys.argv[1:] or ['j021-rinav1-01','j008b-2jump','j005','j019-3jmmtruenix']:
    qcqp_yaws(cap)
