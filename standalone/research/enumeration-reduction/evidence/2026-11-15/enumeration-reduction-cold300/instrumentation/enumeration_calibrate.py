"""Compare option observations with unchanged fixed-work option enumeration."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import statistics
import time

from parkour_solver.constraints.variants import ConstraintVariants
from parkour_solver.search.identity import digest
from tools.profiling.enumeration import EnumerationProfile, instrumented_option


def measure(case, checks, observed):
    owner = ConstraintVariants.__new__(ConstraintVariants)
    owner.__dict__.update(copy.deepcopy(case['attributes']))
    owner.envelopes = copy.deepcopy(case['envelopes'])
    owner.state = dict(context=case['metadata']['context'],duplicateVariants=0,
        timings=dict(preparationSeconds=0.,variantGenerationSeconds=0.))
    owner.deadline, owner.route_guards = None, None
    item, cursor, outputs = copy.deepcopy(case['item']), {}, []
    calls = 0
    class FixedWorkEnd(Exception):
        pass
    def check():
        nonlocal calls
        calls += 1
        if calls > checks:
            raise FixedWorkEnd()
    owner.check = check
    profile = EnumerationProfile(max_cases=0)
    function = instrumented_option(ConstraintVariants.option,profile) if observed else ConstraintVariants.option
    started = time.perf_counter()
    termination = 'exhausted'
    try:
        while True:
            value = function(owner,item,cursor)
            if value is None:break
            outputs.append(value)
    except FixedWorkEnd:
        termination = 'fixed_work'
    elapsed = time.perf_counter()-started
    fingerprint = digest(dict(cursor=cursor,outputs=outputs,item=item,
        duplicateVariants=owner.state['duplicateVariants']))
    report = dict(seconds=elapsed,checks=calls,emitted=len(outputs),
        termination=termination,exactOutputAndCursorSHA256=fingerprint)
    if observed:
        stats=profile.snapshot()
        if not stats['exitsPartitionVisits']:
            raise AssertionError('Unaccounted enumeration visit')
        report['counts']=stats['totals']
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--checks',type=int,default=20000)
    parser.add_argument('--repeats',type=int,default=3)
    args=parser.parse_args()
    if args.checks<1 or args.repeats<1 or args.output.exists():
        parser.error('Use positive counts and a new output file')
    data=args.cases.read_bytes()
    cases=json.loads(gzip.decompress(data) if args.cases.suffix=='.gz' else data)
    if not cases:parser.error('No captured prepared option cases')
    rows=[]
    for case in cases:
        runs={'unwrapped':[],'observed':[]}
        expected=None
        for repeat in range(args.repeats):
            for name in (('unwrapped','observed') if repeat%2==0 else ('observed','unwrapped')):
                result=measure(case,args.checks,name=='observed')
                actual=(result['exactOutputAndCursorSHA256'],result['checks'],result['termination'])
                if expected is None:expected=actual
                if actual!=expected:raise AssertionError('Observation changed fixed-work output/cursor')
                runs[name].append(result)
        medians={name:statistics.median(r['seconds'] for r in values) for name,values in runs.items()}
        rows.append(dict(metadata=case['metadata'],runs=runs,medianSeconds=medians,
            observationOverheadRatio=medians['observed']/medians['unwrapped']))
    result=dict(casesSHA256=hashlib.sha256(args.cases.read_bytes()).hexdigest(),
        rows=rows,allOutputAndCursorHashesEqual=True,
        accounting='Same prepared inputs/check budget, fresh cursor each repetition; source preparation/native startup excluded. Observation overhead, not production speedup. Cursor/output equality includes order and exact rows.')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(cases=len(rows),allOutputAndCursorHashesEqual=True,
        overheadRatios=[r['observationOverheadRatio'] for r in rows])))


if __name__=='__main__':main()
