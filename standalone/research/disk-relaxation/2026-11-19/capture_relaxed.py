"""Capture the requests a production relaxed search sees, on every native-suite scene.

Runs the native suite with (1) every route's physicsThreshold forced to 0 (the production
regime) and (2) every find_route numerical call that would go straight to IPOPT routed
through a per-test RelaxedDispatch, so each request's prefix seed comes from relaxed parent
solves as in production. Automatic search's own dispatch is recorded too. Test verdicts are
not meaningful (thresholds and backends differ from the tests' expectations).

Usage: PYTHONPATH=<source>/src capture_relaxed.py <standalone-root> <new-output-dir>
Writes requests.jsonl.gz: one row per dispatch call with the prepared request (own yaw and
fallbackYaw), stop_on_feasible, seconds, the relaxed outcome and the dispatch result summary.
"""
from contextlib import ExitStack
import gzip
import json
from pathlib import Path
import sys
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

root, out = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
sys.path.insert(0, str(root))
out.mkdir(parents=True, exist_ok=False)

from parkour_solver.numerics import disk
from parkour_solver.numerics import solver as solver_module
from parkour_solver.numerics.model import fingerprint, prepare
from parkour_solver.scene import route as route_module

log = gzip.open(out/'requests.jsonl.gz', 'wt')
current = dict(test=None, dispatch=None)
count = 0
original_dispatch = disk.RelaxedDispatch.solve
original_prepare = route_module.prepare_route
original_solve = solver_module.solve


def recorded(self, problem, *, seconds, stop_on_feasible, **options):
    global count
    started = time.monotonic()
    result = original_dispatch(self, problem, seconds=seconds, stop_on_feasible=stop_on_feasible, **options)
    try:
        prepared = prepare(dict(problem, seconds=seconds))
    except ValueError:
        prepared = None
    candidate = result.get('candidate')
    row = dict(test=current['test'], request=prepared, id=fingerprint(prepared) if prepared else None,
               stopOnFeasible=stop_on_feasible, seconds=seconds, backend=result.get('backend'),
               fallback=result.get('fallback'), disk=result.get('disk'), confirmation=result.get('confirmation'),
               status=result['status'], termination=result.get('termination'), workerStatus=result.get('workerStatus'),
               candidateModel=candidate.get('model') if candidate else None,
               candidateObjective=candidate.get('objective') if candidate else None,
               elapsed=time.monotonic()-started)
    log.write(json.dumps(row, allow_nan=False)+'\n')
    count += 1
    return result


def zero_threshold(request):
    route = original_prepare(request)
    route['physicsThreshold'] = 0.
    return route


def relaxed(request, **options):
    if current['dispatch'] is None:
        current['dispatch'] = disk.RelaxedDispatch(promoted=False, gate=SimpleNamespace(cache={}))
    # Production promotes one lineage; these routed calls have no lineage, so none is promoted.
    current['dispatch'].promoted = False
    return current['dispatch'].solve(dict(request, threshold=0.), **options)


class Tracking(unittest.TextTestResult):
    def startTest(self, test):
        current.update(test=test.id(), dispatch=None)
        super().startTest(test)


scope = json.loads((root/'tools/native-suite.json').read_text())
modules = sys.argv[3:] or scope['modules']
suite = unittest.defaultTestLoader.loadTestsFromNames(modules)
started = time.monotonic()
with ExitStack() as stack:
    stack.enter_context(patch.object(disk.RelaxedDispatch, 'solve', recorded))
    for name, module in list(sys.modules.items()):
        if not name.startswith(('parkour_solver', 'tests', 'tools')):
            continue
        if getattr(module, 'prepare_route', None) is original_prepare:
            stack.enter_context(patch.object(module, 'prepare_route', zero_threshold))
        # IPOPT bound by name in search modules becomes the relaxed dispatch; the
        # dispatch's own IPOPT (imported lazily from numerics.solver) stays stock.
        if name.startswith('parkour_solver.search') and getattr(module, 'solve', None) is original_solve:
            stack.enter_context(patch.object(module, 'solve', relaxed))
    with (out/'tests.log').open('w') as stream:
        result = unittest.TextTestRunner(stream=stream, verbosity=2, resultclass=Tracking).run(suite)
log.close()
summary = dict(tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
               skipped=len(result.skipped), captured=count, elapsedSeconds=time.monotonic()-started)
(out/'summary.json').write_text(json.dumps(summary, indent=2)+'\n')
print(json.dumps(summary))
