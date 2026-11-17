"""Reconcile a fresh local-run-pruning retry using the existing DFS auditor."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import runpy


AUDIT=runpy.run_path(str(Path(__file__).parents[1]/'dfs-interactions/summarize.py'))


def summarize(path):
    report=AUDIT['read'](path);result=report['result'];state=result['searchState']
    summary=AUDIT['run_summary'](report)
    workers=AUDIT['calls'](state);owners=AUDIT['records'](state)
    summary.update(finish=result.get('finish'),minimumRunPruning=result['minimumRunPruning'],
        minimumTicksProven=result['minimumTicksProven'],
        repeatedProblemHashes={k:v for k,v in collections.Counter(c['problemHash'] for c in workers.values()).items() if v>1},
        localRunExclusions=[dict(frame=r['frame'],candidate=r['candidate'],**r['proof']) for r in state['minimumRunExclusions']])
    by_candidate=collections.defaultdict(list)
    for (f,c,a,d),worker in sorted(workers.items()):by_candidate[(f,c)].append(worker)
    cursors=collections.Counter();evaluations=collections.Counter();rows=[]
    for event in state['transitions']:
        if event['kind']!='dispatch':continue
        key=event['frame'],event['candidate'];record=owners.get(key)
        if record is None:continue # A dispatch stopped before producing a worker evaluation.
        index=evaluations[key];evaluations[key]+=1
        if index>=len(record['evaluations']):continue
        count=record['evaluations'][index]['work'].get('solverCalls',0)
        chunk=by_candidate[key][cursors[key]:cursors[key]+count];cursors[key]+=count
        p=record['program']['profile'];jumps=[t for t,j in enumerate(p['jump']) if j and p['grounded'][t]]
        ground=jump=jumps[-1] if jumps else 0
        while ground and p['grounded'][ground-1] and not p['jump'][ground-1]:ground-=1
        goal=record.get('goal') or {};name=goal.get('block',goal.get('kind','clock'))
        for worker in chunk:
            rows.append(dict(call=len(rows)+1,frame=event['frame'],candidate=event['candidate'],goal=name,
                ticks=p['airtime'],jumps=jumps,localRuns=jump-ground if jumps else None,
                finalContact='physical support' if p['grounded'][-1] else 'airborne',
                hardRows=len(worker['request']['walls']),workerStatus=worker['status'],
                finalProgramResult=record['status']+': '+record['termination'],problemHash=worker['problemHash']))
    if len(rows)!=summary['workerCallsThisSession']:raise ValueError('Chronological ledger did not reconcile')
    summary['calls']=rows
    summary['rawReportSha256']=hashlib.sha256(path.read_bytes()).hexdigest()
    return summary


def render(summary):
    lines=['# Fresh Real11 retry: numerical requests','',
        'Source: `'+summary['source']+'`. Requests are ordered by dispatch/evaluation, checked against their recorded fingerprints, and reconciled with the worker-call budget. Accepted-variant aliases are not new calls. Final program result includes any later repair; it is not the acceptance verdict of each individual worker return.','',
        '| Call | Goal | Inputs | Jump inputs | Local runs before last jump | Final contact | Hard rows | Worker | Final program result | Hash |',
        '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |']
    for r in summary['calls']:
        values=[r['call'],r['goal'],r['ticks'],','.join(map(str,r['jumps'])),r['localRuns'],r['finalContact'],r['hardRows'],
            r['workerStatus'],r['finalProgramResult'],r['problemHash'][:12]]
        lines.append('| '+' | '.join(map(str,values))+' |')
    return '\n'.join(lines)+'\n'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--calls',type=Path,required=True)
    args=parser.parse_args();summary=summarize(args.input)
    args.output.write_text(json.dumps(summary,indent=2,allow_nan=False)+'\n');args.calls.write_text(render(summary))
    print(json.dumps({k:summary[k] for k in ('status','termination','workerCallsThisSession','independentReplays','finish','minimumRunPruning')}))


if __name__=='__main__':main()
