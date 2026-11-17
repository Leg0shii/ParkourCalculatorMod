"""Audit first-anchor choices in saved Real11 runs; never inject search seeds."""
import argparse
import collections
import hashlib
import json
from pathlib import Path

from parkour_solver.io.checkpoint_store import read_report
from parkour_solver.search.comparison import comparison_label


def describe(program):
    p=program['profile']
    jumps=[i for i,j in enumerate(p['jump']) if j and p['grounded'][i]]
    label=comparison_label(program)
    return dict(firstJump=jumps[0] if jumps else None,jumps=jumps,
                terminalSupport=program.get('terminalSupport'),
                **{k:label[k] for k in ('T','J','R','A')})


def audit(path):
    report=read_report(path);result=report['result'];outer=result['searchState'];state=outer['dfs']
    frames={f['id']:f for group in ('frames','stack','suspended','refinements','goalQueue')
            for f in state.get(group,[])}
    root=[f for f in frames.values() if f['index']==0]
    arrivals=[];nodes=[];deferred=[]
    known={}
    for frame in root:
        for row in frame.get('records',[]):
            work=collections.Counter()
            for evaluation in row.get('evaluations',[]):
                work.update({k:evaluation.get('work',{}).get(k,0) for k in
                             ('solverCalls','workerLaunches','feasibleSeedReturns')})
            arrivals.append(dict(frame=frame['id'],candidate=row['id'],status=row['status'],
                arrival=row.get('arrival'),tried=row.get('tried',False),work=dict(work),
                **describe(row['program'])))
        continuation=frame.get('generation',{}).get('continuation') or {}
        for ident,node in continuation.get('nodes',{}).items():
            if node.get('program') is not None:known[ident]=describe(node['program'])
    for ident,row in state.get('observations',{}).get('decisions',{}).items():
        if row['span']!=0 or not row.get('queries'):continue
        nodes.append(dict(decision=ident,kind=row['kind'],counts=row['counts'],
            program=known.get(ident),queries=[{k:q[k] for k in ('status','metrics','service')} for q in row['queries']]))
    ids={f['id'] for f in root}
    for row in state.get('minimumRunExclusions',[]):
        if row['frame'] in ids:
            deferred.append(dict(frame=row['frame'],restored=row.get('restored',False),
                reason=row['proof']['reason'],comparison=row['proof'].get('comparison'),**describe(row['program'])))
    fixture=report.get('fixture') or report.get('provenance',{}).get('fixture')
    return dict(report=str(path),manifestSHA256=hashlib.sha256(path.read_bytes()).hexdigest(),
        initialJumpInputs=fixture.get('initialJumpInputs') if fixture else None,
        finish=result['best']['plan']['ticks'] if result.get('best') else None,
        work=result['work'],expandedPrefixes=result['expandedPrefixes'],
        hardLatches=state.get('hardLatches'),rootArrivalAttempts=arrivals,
        firstJumpsAtRootArrivals=sorted({r['firstJump'] for r in arrivals if r['firstJump'] is not None}),
        firstSpanQueries=nodes,rootDeferrals=deferred,
        scope='Saved root arrival records, meaningful-node observations and recoverable deferrals; not every generated/screened descriptor.')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reports',type=Path,nargs='+',required=True)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    if args.output.exists():raise ValueError('Use a new output path')
    records=[audit(path) for path in args.reports]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(records,indent=2,allow_nan=False)+'\n')
    print(json.dumps([dict(report=r['report'],finish=r['finish'],firstJumps=r['firstJumpsAtRootArrivals'],
                          deferrals=len(r['rootDeferrals']),queries=len(r['firstSpanQueries'])) for r in records]))


if __name__=='__main__':main()
