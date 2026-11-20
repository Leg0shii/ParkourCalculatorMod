"""B1 sweep: does the IPOPT finalization ever need collision repair on a real course?

Usage: PYTHONPATH=<feature>/src final_repairs.py <fixture.json> <new-output.json> [goal-block anchors...]
Runs search.application.run on the fixture (optionally with a different goal block and anchor list) and records,
for every solve_contact_branches call, its numerical backend and each contact attempt's failed geometry check
(reason, tick, solid) so finalization repairs (backend 'ipopt') can be told from traversal ones.
"""
import json
import os
from pathlib import Path
import sys
import time
from unittest.mock import patch


def main():
    fixture, out = json.loads(Path(sys.argv[1]).read_text()), Path(sys.argv[2])
    if len(sys.argv) > 3:
        fixture['request']['goalBlock'] = sys.argv[3]
        fixture['anchorSearch']['anchors'] = sys.argv[4:]
    from parkour_solver.bridge.python_compiler import PythonCompiler
    from parkour_solver.search import engine
    from parkour_solver.search.application import run
    calls = []
    original = engine.solve_contact_branches

    def recorded(route, *args, **kwargs):
        result = original(route, *args, **kwargs)
        log = (result.get('repairState') or {}).get('contactLog', result.get('contactAttempts', []))
        failures = [dict(reason=a['geometryCheck'].get('reason'), tick=a['geometryCheck'].get('tick'),
                         solid=a['geometryCheck'].get('solidIndex'), status=a.get('status'), termination=a.get('termination'))
                    for a in log if isinstance(a.get('geometryCheck'), dict) and a['geometryCheck'].get('feasible') is False]
        calls.append(dict(backend=kwargs['repair_scope']['solvePolicy'].get('numerical'), status=result['status'],
                          attempts=len(log), geometryFailures=failures,
                          accepted=bool(result.get('best')) and (result['best'].get('geometryCheck') or {}).get('feasible')))
        return result
    started = time.monotonic()
    with PythonCompiler() as compiler, patch.object(engine, 'solve_contact_branches', side_effect=recorded):
        result, validation = run(fixture, compiler, python_executable=os.environ['PKC_IPOPT_PYTHON'])
    final = [c for c in calls if c['backend'] == 'ipopt']
    summary = dict(fixture=sys.argv[1], goal=fixture['request']['goalBlock'], anchors=fixture['anchorSearch']['anchors'],
                   seconds=time.monotonic()-started, status=result['status'], termination=result['termination'],
                   ticks=result['best']['plan']['ticks'] if result['best'] else None, work=result['work'],
                   finalization=result.get('finalization'), replay=[v['passed'] for v in validation],
                   traversalCalls=len(calls)-len(final),
                   traversalGeometryFailures=sum(len(c['geometryFailures']) for c in calls if c['backend'] != 'ipopt'),
                   finalizationCalls=final)
    out.write_text(json.dumps(summary, indent=1, default=str))
    print(json.dumps({k: v for k, v in summary.items() if k != 'finalizationCalls'}, default=str))
    print('finalization geometry failures', [c['geometryFailures'] for c in final])


if __name__ == '__main__':
    main()
