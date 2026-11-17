import argparse
import copy
import json
import math
from pathlib import Path
import subprocess
import sys

from parkour_solver.numerics import prepare, validate
from parkour_solver.numerics import solve
from research.legacy_cell_routes.failure_diagnostic import read
from research.legacy_cell_routes.generation_diagnostic import save


def synthetic(ticks,axis):
    start = 0. if axis=='X' else 90.
    template = dict(version=1,ticks=ticks,position=[0,0],velocity=[0,0],magnitude=[1.]*ticks,
                    friction=[.91]*ticks,base=[0.]*ticks,yaw=[start]*ticks,threshold=0.,
                    objective=dict(axis=axis,sense='MAX',tick=ticks),walls=[])
    free = prepare(template)
    extent = validate(free,free['yaw'])['objective']
    template['walls'] = [dict(mode=axis,t1=ticks,cmp=cmp,rhs=fraction*extent,name='bounded_progress')
                         for cmp,fraction in [('GE',.2),('LE',.5)]]
    witness = [60. if axis=='X' else 30.]*ticks
    return prepare(template),witness


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--capture',type=Path,required=True)
    args = parser.parse_args()
    cases = [(f'synthetic-{axis}-{n}',*synthetic(n,axis)) for axis in ('X','Z') for n in (1,4,12)]
    captured = read(args.capture)[0]['problem']
    cases.append(('captured-turn-16',captured,None))
    report = dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),runs=[])
    for name,problem,witness in cases:
        if witness is not None and not validate(problem,witness)['feasible']:
            raise ValueError('Synthetic analytic witness is invalid')
        for mode in ('initial_only','declared_fallback','fallback_as_initial'):
            trial = copy.deepcopy(problem)
            start = problem['yaw']
            fallback = problem.get('fallbackYaw',[v+5 for v in start])
            if mode=='initial_only':
                trial.pop('fallbackYaw',None)
            elif mode=='declared_fallback':
                trial['fallbackYaw'] = fallback
            else:
                trial['yaw'] = fallback
                trial.pop('fallbackYaw',None)
            result = solve(trial,seconds=.7,stop_on_feasible=False,python_executable=sys.executable)
            record = dict(name=name,mode=mode,problem=trial,initial=validate(trial,trial['yaw']),result=result,
                analyticWitness=validate(problem,witness) if witness is not None else None)
            report['runs'].append(record)
            print(json.dumps(dict(name=name,mode=mode,status=result['status'],workerStatus=result['workerStatus'],
                                  phaseSeeds=[p.get('seed') for p in result['phases']])),flush=True)
            save(args.output,report)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
