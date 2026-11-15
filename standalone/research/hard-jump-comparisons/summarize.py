"""Summarize matched arms and the new policy's saved decisions, without solves."""
import argparse
import collections
import json
from pathlib import Path

from standalone.autonomy.checkpoint_store import read_report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.output.exists():raise ValueError('Use a new evidence path')
    rows={}
    for arm in ('baseline-combined','baseline-initial0','updated-combined','updated-initial0'):
        directory=args.base/arm;summary=json.loads((directory/'summary.json').read_text())
        profile=json.loads((directory/'profile.json').read_text())
        row={k:v for k,v in summary.items() if k not in ('sessions','transferTimeBounds')}
        row['largestSelfTimeBuckets']=dict(sorted(profile['buckets'].items(),key=lambda x:-x[1]['selfSeconds'])[:12])
        if arm.startswith('updated'):
            result=read_report(directory/'report.json.gz')['result'];state=result['searchState'];dfs=state['dfs']
            frames={f['id']:f for k in ('frames','stack','suspended','refinements','goalQueue') for f in dfs.get(k,[])}
            spans={}
            for f in frames.values():
                i=f['semanticCursor']['anchorIndex'];name='→'.join(state['guidePlan']['anchors'][i:i+2])
                c=f['generation'].get('continuation',{})
                x=spans.setdefault(name,dict(frames=0,generated=0,expanded=0,screened=0,admitted=0))
                x['frames']+=1
                for k in ('generated','expanded','screened'):x[k]+=c.get(k,0)
                x['admitted']+=len(c.get('seen',[]))
            requests=json.loads((directory/'requests.json').read_text())
            fingerprints=collections.Counter(r['fingerprint'] for r in requests)
            row.update(spans=spans,timeBoundExclusions=len(dfs['timeBoundExclusions']),
                positiveFlightExclusions=sum(e['proof'].get('verticalTicks',0)>0 for e in dfs['timeBoundExclusions']),
                comparisonActions=dict(collections.Counter(d['action'] for d in dfs.get('comparisonDecisions',{}).values())),
                requestFingerprints=dict(invocations=len(requests),unique=len(fingerprints),
                    repeats=sum(n-1 for n in fingerprints.values()),
                    note='Repeated fingerprints can be explicitly retried suspended/unknown models; not a claim of redundant successful solves.'),
                hardLatches=list(dfs['hardLatches']),interactionMetrics=result['interactionGate']['metrics'])
        rows[arm]=row
    args.output.write_text(json.dumps(rows,indent=2,allow_nan=False)+'\n')
    print(json.dumps({k:{f:v.get(f) for f in ('ticks','firstFullSolutionSeconds','expandedPrefixes','work','runDeferrals','validatedWitnesses','allReplaysPassed')} for k,v in rows.items()},indent=2))


if __name__=='__main__':main()
