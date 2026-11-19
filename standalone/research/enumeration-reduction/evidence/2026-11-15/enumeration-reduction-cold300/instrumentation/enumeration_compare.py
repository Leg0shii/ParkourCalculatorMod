"""Time complete identical option catalogues against a separately recorded baseline.

Run this script from each checkout's standalone directory using that checkout's
installed interpreter. No production baseline code is imported into another
checkout, and no numerical workers are needed for prepared option cases.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import statistics
import sys


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--baseline',type=Path)
    parser.add_argument('--repeats',type=int,default=3)
    args=parser.parse_args()
    if args.output.exists() or args.repeats<1:parser.error('Use a new output and positive repeat count')
    # Developer tools are deliberately checkout-local, unlike the installed API.
    sys.path.insert(0,str(Path.cwd()))
    from tools.profiling.enumeration_calibrate import measure
    from parkour_solver.io.provenance import source_hashes,source_revision
    import parkour_solver
    data=args.cases.read_bytes()
    cases=json.loads(gzip.decompress(data) if args.cases.suffix=='.gz' else data)
    baseline=json.loads(args.baseline.read_text()) if args.baseline else None
    if baseline and baseline['casesSHA256']!=hashlib.sha256(data).hexdigest():
        parser.error('Baseline cases differ')
    before=source_hashes();rows=[]
    for index,case in enumerate(cases):
        runs=[measure(case,2000000,False) for _ in range(args.repeats)]
        if any(r['termination']!='exhausted' for r in runs):
            raise AssertionError('Complete-catalogue comparison exceeded its guard')
        hashes={r['exactOutputAndCursorSHA256'] for r in runs}
        if len(hashes)!=1:raise AssertionError('Option catalogue changed between repetitions')
        row=dict(metadata=case['metadata'],runs=runs,medianSeconds=statistics.median(r['seconds'] for r in runs))
        if baseline:
            reference=baseline['rows'][index]
            if hashes!={reference['runs'][0]['exactOutputAndCursorSHA256']}:
                raise AssertionError('Ordered options, representatives, visited states or counts changed')
            row.update(speedup=reference['medianSeconds']/row['medianSeconds'],
                checkReduction=1-runs[0]['checks']/reference['runs'][0]['checks'])
        rows.append(row)
        print(json.dumps(dict(block=case['metadata']['block'],through=case['metadata']['through'],
            medianSeconds=row['medianSeconds'],speedup=row.get('speedup'),checks=runs[0]['checks'])),flush=True)
    if before!=source_hashes():raise AssertionError('Production source changed during benchmark')
    result=dict(sourceRevision=source_revision(),sourceHashes=before,sourceUnchanged=True,
        pythonExecutable=sys.executable,pythonVersion=sys.version,package=parkour_solver.__file__,
        casesSHA256=hashlib.sha256(data).hexdigest(),rows=rows,
        baselineSHA256=hashlib.sha256(args.baseline.read_bytes()).hexdigest() if baseline else None,
        accounting='Complete identical prepared catalogues, not equal wall budgets. Exact ordered option/final-cursor/item/duplicate-count hashes must match. Native startup and preparation excluded. Iteration checks may decrease because already-seen entries are omitted.')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')


if __name__=='__main__':main()
