"""Read-only inventory of saved Real11 movement and numerical candidates.

The saved seen keys contain complete physical profiles. Reconstruct admitted
programs from those keys without running a solver or altering the search.
Raw rejected proposals retain reasons/IDs, not complete profiles.
"""
import argparse
import collections
import csv
import hashlib
import json
import os
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.checkpoint_store import read_report
from standalone.autonomy.cell_routes.anchor_policy import local_counts, transfer_structure
from standalone.autonomy.cell_routes.window_experiment import save
from standalone.autonomy.movement_composition import program_search_key


def decode_key(key, initial):
    decoder=json.JSONDecoder()
    values,end=decoder.raw_decode(key)
    owner_start=end+len('|owners=')
    owners,owner_end=decoder.raw_decode(key,owner_start)
    profile,support,threshold,ladder=values
    profile['constraintOwners']=owners[0]
    return dict(initial=initial,profile=profile,terminalSupport=support,threshold=threshold,
                ladderEvents=ladder,committedThrough=owners[1],interactions=owners[2],solveScope=owners[3]),key[:owner_end]


def write_csv(path, rows):
    if not rows:return
    with path.open('w',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=False)
    report=read_report(args.input);result=report['result'];outer=result['searchState'];dfs=outer['dfs']
    scene=prepare_scene(result['effectiveRequest']);guides=outer['guidePlan'];names=guides['anchors']
    frames={f['id']:f for group in ('frames','stack','suspended','refinements','goalQueue') for f in dfs.get(group,[])}
    programs=[];frame_rows=[];arrivals=[];nodes=[];exclusions=[];spans={};contexts={}
    for fid,f in sorted(frames.items()):
        span=f['semanticCursor']['anchorIndex'];start=f['semanticCursor']['spanStart'];a,b=names[span:span+2]
        label=a+'->'+b;c=f['generation'].get('continuation',{})
        contexts.setdefault(label,dict(frame=fid,span=span,parent=f['parents'][0],
                                      request=f['goalRequest'],cursor=f['semanticCursor']))
        group=spans.setdefault(label,dict(frames=0,expanded=0,generated=0,admitted=0,emitted=0,screened=0,
            pending=0,deferredNodes=0,progressKinds=collections.Counter(),caseKinds=collections.Counter(),
            localJumps=collections.Counter(),exclusionReasons=collections.Counter(),repairOnlyDuplicates=0,
            nodeStatuses=collections.Counter(),arrivalRecords=0,arrivalEvaluationHistoryEntries=0))
        group['frames']+=1
        for key,field in [('expanded','expanded'),('generated','generated'),('emitted','emitted'),('screened','screened')]:
            group[field]+=c.get(key,0)
        group['pending']+=len(c.get('pending',[]));group['deferredNodes']+=len(c.get('deferredNodes',[]))
        physical=set()
        for key in c.get('seen',[]):
            p,without_repair=decode_key(key,scene['initial']);physical.add(without_repair)
            assert program_search_key(p)==without_repair,'Seen-key reconstruction changed the physical/search model'
            counts=local_counts(p,start);support=p['terminalSupport'];profile=p['profile']
            cases=[e for e in profile.get('caseEvents',[]) if e['input']>=start]
            kinds='+'.join(e['case'] for e in cases) or 'ordinary'
            structure=transfer_structure(scene,p,start,a,b,complete=support==b)
            kind=('arrival_with_setup' if structure['preparationJumps'] else 'direct_arrival') if support==b else (
                'source_setup' if counts['J'] else 'source_run_hold') if support==a else 'other_or_air'
            group['admitted']+=1;group['progressKinds'][kind]+=1;group['caseKinds'][kinds]+=1
            group['localJumps'][str(counts['J'])]+=1
            programs.append(dict(frame=fid,span=label,start=start,T=counts['T'],J=counts['J'],R=counts['R'],A=counts['A'],
                terminalSupport=support,kind=kind,cases=kinds,jumpInputs=json.dumps([t for t in range(start,profile['airtime']) if profile['jump'][t] and profile['grounded'][t]]),
                preparationJumps=json.dumps(structure['preparationJumps']),transferJumps=json.dumps(structure['transferJumps']),
                constraints=len(profile['positionConstraints']),grounded=profile['grounded'][-1],hardStructureValid=structure['valid'],
                searchId=hashlib.sha256(key.encode()).hexdigest()))
        group['repairOnlyDuplicates']+=len(c.get('seen',[]))-len(physical)
        for e in c.get('exclusions',[]):
            reason=e.get('proof',{}).get('reason','unknown');group['exclusionReasons'][reason]+=1
            exclusions.append(dict(frame=fid,span=label,T=e.get('ticks'),reason=reason,id=e.get('id'),scope=e.get('scope')))
        for identity,node in c.get('nodes',{}).items():
            group['nodeStatuses'][node['status']]+=1
            p=node.get('program',{});counts=local_counts(p,start) if p else {}
            nodes.append(dict(frame=fid,span=label,id=identity,status=node['status'],T=counts.get('T'),J=counts.get('J'),R=counts.get('R'),A=counts.get('A'),
                support=p.get('terminalSupport'),cases='+'.join(e['case'] for e in p.get('profile',{}).get('caseEvents',[]) if e['input']>=start)))
        for r in f['records']:
            group['arrivalRecords']+=1;group['arrivalEvaluationHistoryEntries']+=len(r['evaluations'])
            counts=local_counts(r['program'],start)
            arrivals.append(dict(frame=fid,span=label,T=r['arrival'],status=r['status'],tried=r['tried'],evaluationHistoryLength=len(r['evaluations']),
                J=counts['J'],R=counts['R'],A=counts['A'],cases='+'.join(e['case'] for e in r['program']['profile'].get('caseEvents',[]) if e['input']>=start),
                id=r['id'],candidateKey=r.get('candidateKey'),constraints=len(r['program']['profile']['positionConstraints'])))
        frame_rows.append(dict(frame=fid,span=label,start=start,phase=f['phase'],sourceExhausted=f['sourceExhausted'],
            expanded=c.get('expanded',0),generated=c.get('generated',0),admitted=len(c.get('seen',[])),
            emitted=c.get('emitted',0),pending=len(c.get('pending',[])),records=len(f['records']),nodes=len(c.get('nodes',{}))))
    queries=[]
    for event in dfs['observations']['decisions'].values():
        label=names[event['span']]+'->'+names[event['span']+1]
        for q in event['queries']:
            m=q['metrics'];counts=event['counts']
            queries.append(dict(span=label,kind=event['kind'],id=event['id'],model=q['model'],status=q['status'],
                T=counts['T'],J=counts['J'],R=counts['R'],A=counts['A'],wrappers=m.get('solverCalls',0),
                workers=m.get('workerLaunches',0),seedReturns=m.get('feasibleSeedReturns',0)))
    for label,group in spans.items():
        own=[q for q in queries if q['span']==label]
        group['observedQueries']=len(own);group['wrappers']=sum(q['wrappers'] for q in own);group['workers']=sum(q['workers'] for q in own)
        group['internalNodeWorkers']=sum(q['workers'] for q in own if q['kind']=='node')
        group['anchorWorkers']=sum(q['workers'] for q in own if q['kind']=='anchor')
    assert sum(q['wrappers'] for q in queries)==result['work']['solverCalls']
    assert sum(q['workers'] for q in queries)==result['work']['workerLaunches']
    summary=dict(source=str(args.input),baseRevision=report['provenance']['sourceRevision'],
        runtimeHashes=report['provenance']['sourceHashes'],
        snapshotNote='The run was recorded before the integration merge commit. Base revision plus saved source.patch/runtime hashes identify the tested source; it is the 91381e5a integration, before 5d54f02b lazy queue checks.',
        currentCodeNote='Saved run predates the lazy-bound follow-up; movement families are unchanged. No new solve.',
        work=result['work'],expanded=result['expandedPrefixes'],spans=spans,
        totals={field:sum(g[field] for g in spans.values()) for field in ('expanded','generated','admitted','emitted','screened','pending','deferredNodes','arrivalRecords','arrivalEvaluationHistoryEntries','repairOnlyDuplicates','wrappers','workers','internalNodeWorkers','anchorWorkers')},
        queryEvaluations=len(queries),uniqueBaseSolveModels=len({q['model'] for q in queries}),
        bounds='The seen-key ledger enumerates admitted physical programs, not every rejected raw proposal or every reconstruction of a lazy generator. Observations retain query metrics, not all successful-node numerical request bodies.')
    for name,rows in [('programs',programs),('frames',frame_rows),('arrivals',arrivals),('nodes',nodes),('queries',queries),('exclusions',exclusions)]:
        write_csv(args.output/(name+'.csv'),rows)
    (args.output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    save(args.output/'probe-contexts.json.gz',dict(request=result['effectiveRequest'],domain=outer['domain'],graph=outer['graph'],
        policy=outer['anchorPolicy'],motion=outer['motion'],contexts=contexts))
    print(json.dumps(summary),flush=True)
    os._exit(0)  # Release the large decoded graph without timing its destruction.


if __name__=='__main__':main()
