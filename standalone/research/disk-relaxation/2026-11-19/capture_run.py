"""Run tools.profiling.run with every numerical request recorded (payload included).

Usage (from the checkout's standalone/ directory):
  PYTHONPATH=$PWD/src capture_run.py <new-capture-file.jsonl.gz> -- <tools.profiling.run arguments>
Rows: kind='ipopt' for each numerics.solver.solve call, kind='dispatch' for each
RelaxedDispatch.solve call (when the source has one), with the prepared request, intent,
seconds, outcome and elapsed time. Results are unchanged.
"""
import gzip
import json
import runpy
import sys
import time

split = sys.argv.index('--')
out = sys.argv[1]
sys.argv = ['tools.profiling.run'] + sys.argv[split+1:]
sys.path.insert(0, '.')

import parkour_solver.numerics as numerics
from parkour_solver.numerics import solver as solver_module
from parkour_solver.numerics.model import fingerprint, prepare

log = gzip.open(out, 'xt')
original = solver_module.solve


def row(kind, request, seconds, stop_on_feasible, result, started, **extra):
    try:
        prepared = prepare(dict(request, **({'seconds': seconds} if seconds else {})))
    except ValueError:
        prepared = None
    candidate = result.get('candidate')
    log.write(json.dumps(dict(kind=kind, request=prepared, id=fingerprint(prepared) if prepared else None,
                              stopOnFeasible=stop_on_feasible, seconds=seconds, status=result['status'],
                              termination=result.get('termination'), workerStatus=result.get('workerStatus'),
                              workerLaunched=result.get('workerLaunched'),
                              candidateObjective=candidate.get('objective') if candidate else None,
                              elapsed=time.monotonic()-started, **extra), allow_nan=False)+'\n')
    log.flush()


def ipopt(request, **options):
    started = time.monotonic()
    result = original(request, **options)
    row('ipopt', request, options.get('seconds'), options.get('stop_on_feasible'), result, started)
    return result


solver_module.solve = ipopt
numerics.solve = ipopt
try:
    from parkour_solver.numerics import disk
except ImportError:
    disk = None
if disk is not None and hasattr(disk, 'RelaxedDispatch'):
    dispatch_solve = disk.RelaxedDispatch.solve

    def dispatch(self, problem, *, seconds, stop_on_feasible, **options):
        started = time.monotonic()
        result = dispatch_solve(self, problem, seconds=seconds, stop_on_feasible=stop_on_feasible, **options)
        row('dispatch', problem, seconds, stop_on_feasible, result, started, backend=result.get('backend'),
            fallback=result.get('fallback'), disk=result.get('disk'), confirmation=result.get('confirmation'))
        return result
    disk.RelaxedDispatch.solve = dispatch
try:
    runpy.run_module('tools.profiling.run', run_name='__main__')
finally:
    log.close()
