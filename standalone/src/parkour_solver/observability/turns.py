"""Compact resume-safe observations; these records never influence decisions."""

import copy

from parkour_solver.search.identity import digest
from parkour_solver.spatial.anchor_guides import classify
from parkour_solver.search.comparison import comparison_label, priority_key


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
