import argparse
import gzip
import hashlib
import json
from pathlib import Path


def read(path):
    with gzip.open(path,'rt') as stream:
        return json.load(stream)


def records(state):
    unique = {}
    for key in ('frames','stack','suspended','refinements'):
        for frame in state[key]:
            for record in frame['records']:
                if record.get('variantOf'):
                    continue
                ident = frame['id'],record['id']
                old = unique.get(ident)
                if old is None or len(record.get('evaluations',[]))>=len(old.get('evaluations',[])):
                    unique[ident] = record
    return unique


def calls(state):
    result = {}
    for (frame, candidate), record in records(state).items():
        for a, attempt in enumerate(record.get('numericalAttempts',[])):
            for d, diagnostic in enumerate(attempt.get('diagnostics',[])):
                if diagnostic.get('request') is not None:
                    from standalone.ipopt.model import prepare, fingerprint
                    if fingerprint(prepare(diagnostic['request']))!=diagnostic['problemHash']:
                        raise ValueError('Stored request fingerprint mismatch')
                result[(frame,candidate,a,d)] = dict(diagnostic,problemHash=diagnostic.get('problemHash',attempt.get('problemHash')))
    return result


def first_full_calls(state):
    owners=records(state);indices={};count=0
    for event in state['transitions']:
        if event['kind']=='incumbent':
            return count
        if event['kind']!='dispatch':
            continue
        key=event['frame'],event['candidate']
        index=indices.get(key,0);indices[key]=index+1
        evaluations=owners[key]['evaluations']
        if index>=len(evaluations):
            return None
        count+=evaluations[index]['work'].get('solverCalls',0)
    return None


def run_summary(report):
    result=report['result'];state=result['searchState'];worker=calls(state)
    if len(worker)!=state['cumulativeCalls']:
        raise ValueError('Worker diagnostics do not reconcile with cumulative dispatch count')
    active=[f['id'] for key in ('stack','suspended','refinements') for f in state[key]]
    if len(active)!=len(set(active)):
        raise ValueError('A frame has more than one execution owner')
    initial=report['fixture']['request']['initial']
    for call in worker.values():
        request=call.get('request')
        if request is not None and (request['position']!=initial['position'][::2] or
                request['velocity']!=initial['velocity'][::2] or request['threshold']!=0. or
                any(row['mode']=='F' for row in request['walls'])):
            raise ValueError('A request changed the original state, threshold policy or free facings')
    validations=report.get('validation',[])
    if not validations or not all(v['passed'] for v in validations):
        raise ValueError('Independent witness replay failed or is absent')
    roots=[f for f in state['frames']+state['stack'] if f['index']==0]
    root=max(roots,key=lambda f:len(f['records'])) if roots else None
    return dict(source=report['source'],status=result['status'],termination=result['termination'],
        schema=state['version'],session=state['session'],elapsedSeconds=result['elapsedSeconds'],
        firstFullSolutionSeconds=state.get('firstFullSolutionSeconds'),workerCallsToFirstFull=first_full_calls(state),
        workerCallsThisSession=result['work'].get('solverCalls',0),cumulativeWorkerCalls=state['cumulativeCalls'],
        storedRequestCount=sum('request' in c for c in worker.values()),
        objective=result['best']['candidate']['objective'] if result['best'] else None,
        independentReplays=len(validations),allReplaysPassed=True,
        rootArrivals=[r['arrival'] for r in root['records'] if r.get('witness')] if root else [],
        rootChosenArrivals=[t['arrival'] for t in state['transitions'] if t['kind']=='choose' and t['depth']==0],
        backtracks=sum(t['kind']=='backtrack' for t in state['transitions']),
        activeDepth=len(state['stack']),deferredRefinements=len(state['refinements']),suspendedFrames=len(state['suspended']),
        optimumProven=result['objectiveOptimalityProven'],context=state['context'])


def main():
    parser=argparse.ArgumentParser(description='Reconcile DFS cold/resume calls, scope, ownership and witness replays.')
    parser.add_argument('--cold',type=Path,required=True)
    parser.add_argument('--resume',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--calls-output',type=Path)
    args=parser.parse_args()
    cold,resumed=read(args.cold),read(args.resume)
    a,b=run_summary(cold),run_summary(resumed)
    if a['context']!=b['context'] or not resumed['result']['resume']['exact']:
        raise ValueError('Continuation is not exact in the same model context')
    old,new=calls(cold['result']['searchState']),calls(resumed['result']['searchState'])
    if not old.keys()<=new.keys() or any(old[k]['problemHash']!=new[k]['problemHash'] for k in old):
        raise ValueError('Completed request history changed during continuation')
    delta=[new[k] for k in new.keys()-old.keys()]
    old_models={((k[0],k[1]),v['problemHash']) for k,v in old.items()}
    repeats=sum(((k[0],k[1]),new[k]['problemHash']) in old_models for k in new.keys()-old.keys())
    if repeats or len(delta)!=b['workerCallsThisSession']:
        raise ValueError('Completed exact-context requests were redispatched or counts do not reconcile')
    output=dict(cold=a,resume=b,exactResume=True,repeatedCompletedRequests=repeats,
        additionalWorkerCalls=len(delta),newCallsWithExactRequest=sum('request' in v for v in delta),
        sourceTransition='same semantic context; additive request/validation logging added after cold run',
        artifacts={p.name:dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
                   for p in (args.cold,args.resume)})
    args.output.write_text(json.dumps(output,indent=2,allow_nan=False)+'\n')
    if args.calls_output:
        owners=records(resumed['result']['searchState'])
        lines=['# DFS cold/resume worker calls','',
            'Grouped by frame/candidate context, not wall-clock order. Each row is one worker call. '+
            'Old cold records retain hashes and fixed program context; resumed calls additionally retain exact '+
            'normalized requests, verified against their fingerprints. Accepted terminal/prefix replays are '+
            'summarized separately; unknown or missing per-call validation is not physical infeasibility.','',
            '| # | Session | Frame | Goal | Inputs | Worker result | Validation log | Exact request stored | Model SHA-256 |',
            '| --- | --- | --- | --- | --- | --- | --- | --- | --- |']
        for number,(key,value) in enumerate(sorted(new.items()),1):
            frame,candidate,attempt,diagnostic=key
            record=owners[(frame,candidate)]
            goal=record.get('goal')
            label=(goal.get('block',goal['kind']) if goal else 'terminal clock')
            completion=[c for c in record.get('program',{}).get('repairState',{}).get('completed',[])
                        if value['problemHash'] in c.get('problemHashes',[])]
            validation=completion[0].get('validationStatus','not logged in older source') if len(completion)==1 else 'not uniquely logged'
            lines.append('| '+ ' | '.join(map(str,[number,1 if key in old else 2,frame,label,
                record['program']['profile']['airtime'],value['status'],validation,
                'yes' if 'request' in value else 'no',value['problemHash']]))+' |')
        lines+=['','Use `records(state)[(frame, candidateId)]` and its `numericalAttempts[attempt].diagnostics[diagnostic]` '+
                'for the exact record; the full IDs/indices are recoverable from the script `calls(state)`. '+
                'The raw reports preserve all constraints, generator state, witnesses and backtracking transitions.']
        args.calls_output.write_text('\n'.join(lines)+'\n')

    print(json.dumps(output,indent=2))


if __name__=='__main__':
    main()
