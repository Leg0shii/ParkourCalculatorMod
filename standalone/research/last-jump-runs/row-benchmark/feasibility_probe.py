"""Compare objective-free feasibility with directional early stop on fixed NLPs."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import statistics
import subprocess
import sys
import time

from standalone.ipopt import worker
from standalone.ipopt.continuation import Incumbent
from standalone.ipopt.model import prepare, validate
from standalone.ipopt.solver import solve


PREFIXES = ('b1a196f2215e','b6c9cb2b87a0','0ba4282df584','cb6d4aa98146')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    helpers = runpy.run_path(str(Path(__file__).with_name('benchmark.py')))
    factory, send = worker.ca.nlpsol, worker.send
    x = worker.ca.SX.sym('warm')
    factory('warm','ipopt',dict(x=x,f=x*x),{'print_time':False,'ipopt.print_level':0,'ipopt.sb':'yes'})
    modes = ['directional_early_stop','constant_feasibility','optimization']
    report = dict(baseRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        fullRouteSearch=False,casadiVersion=worker.ca.__version__,runs=[],
        codeHashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in
            [Path('standalone/ipopt/worker.py'),Path('standalone/autonomy/cell_routes/checkpoint_search.py')]})
    for prefix in PREFIXES:
        source,raw,_ = helpers['find_request'](args.input,prefix)
        problem = prepare(raw)
        report['inputSource'] = source
        for repeat in range(3):
            for mode in modes[repeat:]+modes[:repeat]:
                phases = []
                incumbent = Incumbent(problem,lambda *_:None)
                def configured(name,plugin,nlp,options):
                    if mode=='directional_early_stop':
                        options['iteration_callback'].stop = True
                    return factory(name,plugin,nlp,options)
                worker.ca.nlpsol, worker.send = configured, phases.append
                began = time.perf_counter()
                try:
                    yaw,status,_ = worker.fixed_phase(problem,problem['yaw'],[[2,2]]*problem['ticks'],3.,
                        mode=='constant_feasibility',incumbent.consider,'carry','initial',[[0,0]]*problem['ticks'],0)
                finally:
                    worker.ca.nlpsol, worker.send = factory, send
                elapsed = time.perf_counter()-began
                candidate = validate(problem,incumbent.best['yaw'] if incumbent.best else yaw)
                report['runs'].append(dict(problemHash=prefix,ticks=problem['ticks'],mode=mode,repetition=repeat,
                    elapsedSeconds=elapsed,status=status,iterations=phases[-1]['iterations'],
                    feasible=candidate['feasible'],measuredObjective=candidate['objective']))
        for stop in (False,True):
            outcome = solve(problem,seconds=3.,stop_on_feasible=stop,python_executable=sys.executable)
            candidate = outcome['candidate']
            checked = validate(problem,candidate['yaw']) if candidate else None
            report.setdefault('freshWorkers',[]).append(dict(problemHash=prefix,ticks=problem['ticks'],
                mode=outcome['objectiveMode'],status=outcome['status'],termination=outcome['termination'],
                elapsedSeconds=outcome['elapsedSeconds'],originalRowsFeasible=bool(checked and checked['feasible']),
                phases=outcome['phases']))
    report['medians'] = {prefix:{mode:{field:statistics.median(r[field] for r in report['runs']
        if r['problemHash']==prefix and r['mode']==mode) for field in ('elapsedSeconds','iterations')}
        for mode in modes} for prefix in PREFIXES}
    report['allOriginalRowsFeasible'] = all(r['feasible'] for r in report['runs'])
    report['noInvalidAccepted'] = all(r['status']!='feasible' or r['originalRowsFeasible'] for r in report['freshWorkers'])
    assert report['noInvalidAccepted']
    args.output.write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    print(json.dumps(report['medians']),flush=True)


if __name__=='__main__':
    main()
