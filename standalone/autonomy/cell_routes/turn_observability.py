"""Compact resume-safe observations; these records never influence decisions."""

import copy

from ..solve_spec import digest
from .anchor_guides import classify
from ..candidate_comparison import comparison_label, priority_key


def record(state, guides, program, *, span, start, model, status, elapsed=0., kind='node', metrics=None, service=None):
    if not state.get('enabled',True):
        return
    events=state.setdefault('decisions',{})
    ident=program.get('decisionNode') or digest([model,span,kind])
    if ident not in events:
        point=program.get('decisionPoint') or guides['points'][0][::2]
        label=classify(guides,span,point)
        mode=program.get('priorityTurnaround',label['isTurnaround'])
        counts=comparison_label(program,start=start,mode=mode,identity=ident)
        events[ident]=dict(id=ident,parent=program.get('decisionParent'),
            depth=program.get('decisionDepth',0),lastSolvedDepth=program.get('lastSolvedDepth',0),
            pointTick=program.get('decisionPointTick',0),
            pointSource=program.get('decisionPointSource','original_initial'),
            **label,counts={k:counts[k] for k in ('T','J','R','A','start')},mode='turnaround' if mode else 'ordinary',
            key=list(priority_key(counts)),kind=kind,queries=[],replays=[])
    event=events[ident]
    query=dict(model=model,status=status,elapsedSeconds=elapsed,metrics=metrics or {},service=service or {})
    if query not in event['queries']:
        event['queries'].append(query)


def replay(state, guides, ancestor, span, point, model):
    if not state.get('enabled',True):return
    event=state.get('decisions',{}).get(ancestor)
    if event is None:return
    label=classify(guides,span,point)
    observation=dict(model=model,point=point,distances=label['distances'],
                     wouldFlip=label['isTurnaround']!=event['isTurnaround'])
    if observation not in event['replays']:event['replays'].append(observation)


def summary(state):
    result={}
    for e in state.get('decisions',{}).values():
        key=str(e['span'])
        group=result.setdefault(key,dict(ordinary=0,turnaround=0,outcomes={},solverSeconds=0.,
                                        rawDistances=[],shiftedDistances=[],replayFlips=0,
                                        selected=0,wrapperCalls=0,workerLaunches=0,feasibleSeedReturns=0))
        group[e['mode']]+=1
        group['selected']+=e.get('selected',0)
        for d in e['distances']:
            group['rawDistances'].append(d['rawDistance']);group['shiftedDistances'].append(d['shiftedDistance'])
        for q in e['queries']:
            group['outcomes'][q['status']]=group['outcomes'].get(q['status'],0)+1
            group['solverSeconds']+=q['elapsedSeconds']
            group['wrapperCalls']+=q.get('metrics',{}).get('solverCalls',0)
            group['workerLaunches']+=q.get('metrics',{}).get('workerLaunches',0)
            group['feasibleSeedReturns']+=q.get('metrics',{}).get('feasibleSeedReturns',0)
        group['replayFlips']+=sum(r['wouldFlip'] for r in e['replays'])
    return copy.deepcopy(result)


def draw(guides, observations, destination, *, trajectory=None):
    """Export inspectable edge/margin/point geometry without changing the run."""
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.patches import Polygon, Rectangle
    fig,ax=plt.subplots(figsize=(10,8))
    points=guides['points']
    ax.plot([p[0] for p in points],[p[2] for p in points],'--',color='gray',label='backward guides')
    for a,b in zip(points,points[1:]):
        ax.annotate('',xy=(b[0],b[2]),xytext=(a[0],a[2]),arrowprops=dict(arrowstyle='->',color='gray'))
    for boxes in guides.get('physicalFootprints',{}).values():
        for b in boxes:
            ax.add_patch(Rectangle((b[0],b[2]),b[3]-b[0],b[5]-b[2],color='gray',alpha=.2))
    for name,cover in guides['covers'].items():
        for b in cover:ax.add_patch(Rectangle((b[0],b[2]),b[3]-b[0],b[5]-b[2],fill=False,linestyle=':',alpha=.4))
    if trajectory:
        ax.plot([p[0] for p in trajectory],[p[1] for p in trajectory],color='green',linewidth=1.,label='validated trajectory')
    for turn in guides['turns']:
        n=turn['normal']; tangent=(-n[1],n[0]);c=turn['offset'];m=turn['margin']
        # Draw on a local tangent interval covering E, rather than an infinite
        # line that visually assigns unrelated route neighborhoods a mode.
        tangents=[sum(a*b for a,b in zip(tangent,p)) for p in turn['footprint']]
        lo,hi=min(tangents)-.5,max(tangents)+.5
        def at(offset,t):return [n[a]*offset+tangent[a]*t for a in (0,1)]
        for offset,style in ((c,'-'),(c-m,':')):
            a,b=at(offset,lo),at(offset,hi)
            ax.plot([a[0],b[0]],[a[1],b[1]],style,color='purple')
        poly=[at(c-m,lo),at(c-m,hi),at(c+1,hi),at(c+1,lo)]
        ax.add_patch(Polygon(poly,color='purple',alpha=.08))
    for e in observations.get('decisions',{}).values():
        x,z=e['point'];ax.scatter(x,z,c='orange' if e['isTurnaround'] else 'blue',s=18)
        for r in e['replays']:
            if r['wouldFlip']:ax.scatter(*r['point'],marker='x',c='red')
    for name,p in zip(guides['anchors'],points):ax.annotate(name,(p[0],p[2]))
    ax.set(xlabel='X',ylabel='Z',title='Purple: approach edge / dotted margin; blue: ordinary; orange: turnaround; red ×: replay flip')
    ax.legend();ax.set_aspect('equal',adjustable='datalim');fig.tight_layout();fig.savefig(destination);plt.close(fig)
