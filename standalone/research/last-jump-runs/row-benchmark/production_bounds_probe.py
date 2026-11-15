"""Controlled row reduction/extraction A/B on one archived fixed NLP."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import statistics
import subprocess
import sys

from standalone.ipopt import worker
from standalone.ipopt.model import prepare, validate, fingerprint
from standalone.ipopt.solver import solve


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--repeats',type=int,default=5)
    args = parser.parse_args()
    helpers = runpy.run_path(str(Path(__file__).with_name('benchmark.py')))
    source,request,_ = helpers['find_request'](args.input,'f05d5e3a7884')
    original = prepare(request)
    factory, reducer = worker.ca.nlpsol, worker.reduce_walls
    x = worker.ca.SX.sym('warm')
    factory('warm','ipopt',dict(x=x,f=x*x),{'print_time':False,'ipopt.print_level':0,'ipopt.sb':'yes'})
    labels = [('baseline',False,False),('reduction',True,False),('extraction',False,True),('production',True,True)]
    result = dict(inputSource=source,benchmarkBaseRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        codeHashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in
            [Path('standalone/ipopt/worker.py'),Path('standalone/ipopt/constraints.py')]},
        casadiVersion=worker.ca.__version__,problemHash=fingerprint(original),fullRouteSearch=False,
        maxIterations=200,runs=[],wallReduction=reducer(original['walls'])['audit'])
    for repeat in range(args.repeats):
        for label,reduced,extracted in labels[repeat%4:]+labels[:repeat%4]:
            def configured(name,plugin,nlp,options):
                return factory(name,plugin,nlp,dict(options,detect_simple_bounds=extracted))
            worker.ca.nlpsol = configured
            if not reduced:
                worker.reduce_walls = lambda walls:dict(walls=walls,audit={})
            try:
                row = helpers['one_solve'](request,original,200)
            finally:
                worker.ca.nlpsol, worker.reduce_walls = factory,reducer
            row.update(variant=label,repetition=repeat)
            result['runs'].append(row)
            print(json.dumps(row),flush=True)
    result['medians'] = {label:{field:statistics.median(r[field] for r in result['runs'] if r['variant']==label)
        for field in ('constraints','iterations','solveSeconds','elapsedSeconds')} for label,_,_ in labels}
    result['freshProduction'] = []
    for _ in range(3):
        solved = solve(request,seconds=2.,stop_on_feasible=False,python_executable=sys.executable)
        checked = validate(original,solved['candidate']['yaw']) if solved['candidate'] else None
        result['freshProduction'].append(dict(status=solved['status'],termination=solved['termination'],
            elapsedSeconds=solved['elapsedSeconds'],originalRowsFeasible=bool(checked and checked['feasible']),
            objective=checked['objective'] if checked else None,phases=solved['phases']))
    result['allOriginalRowsFeasible'] = all(r['originalRowsFeasible'] for r in result['runs']+result['freshProduction'])
    result['maximumObjectiveDifference'] = max(r['objective'] for r in result['runs'])-min(r['objective'] for r in result['runs'])
    assert result['allOriginalRowsFeasible']
    assert result['maximumObjectiveDifference']<1e-6
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(medians=result['medians'],allOriginalRowsFeasible=True,
        maximumObjectiveDifference=result['maximumObjectiveDifference'])),flush=True)


if __name__=='__main__':
    main()
