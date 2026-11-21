"""usage: ab_calls.py <out.jsonl> <tools.profiling.run args>; run from the worktree's standalone/.

Phase 4 step 3b A/B: every fresh evaluate_program call (no saved repair state, not a finalization)
runs twice on the same inputs: conflict-driven avoidance (PKC_AVOIDANCE=conflict) on a copy with its
own work budget, then up-front enumeration, whose result drives the search. Before each run the disk
content cache and the gate's IPOPT confirmations are cleared, so neither run reuses the other's solves.
"""
import copy, json, os, runpy, sys, time
import parkour_solver.search.engine as engine
from parkour_solver.numerics import disk
from parkour_solver.search.ranking import candidate_rank
from parkour_solver.search.work import SearchWork
out = open(sys.argv[1], 'w')
real = engine.evaluate_program
mismatches = []

def clear(adapter):
    disk._DECIDED.clear()
    gate = getattr(adapter, 'interaction_service', None)
    for key in [k for k in getattr(gate, 'cache', {}) if isinstance(k, tuple) and k and k[0] == 'confirmation']:
        gate.cache.pop(key)

def measure(mode, args, kwargs):
    os.environ['PKC_AVOIDANCE'] = mode
    before = {k: dict(v) for k, v in disk.STATS.items()}
    clear(args[3])
    started = time.monotonic()
    result = real(*args, **kwargs)
    seconds = time.monotonic()-started
    solved = sum(disk.STATS[k].get('solved', 0)-before[k].get('solved', 0) for k in disk.STATS)
    repair = result.get('repairState') or {}
    accepted = repair.get('acceptedVariants', [])
    objective = (result.get('best') or {}).get('route', {}).get('objective') or dict(axis='X', sense='MAX')
    ranks = [candidate_rank(v['best'], objective) for v in accepted]
    gen = repair.get('constraintGeneration') or {}
    return result, dict(mode=mode, seconds=seconds, clarabelSolves=solved,
        ipopt=result['work'].get('workerLaunches', 0), requests=result['work'].get('solverCalls', 0),
        verdict=(result.get('nodeFeasibility') or {}).get('status', result['status']),
        bestRelaxed=list(min(ranks)) if ranks else None,
        best=(result.get('best') or {}).get('candidate', {}).get('objective'),
        accepted=len(accepted), branchKeys=len({v['best'].get('alternativeBranchKey') for v in accepted}),
        obstacles=gen.get('obstacles'), items=len(gen.get('items', [])),
        termination=result.get('termination'), timings=result.get('timings'),
        settled=args[1].get('settledThrough', 0), through=(result.get('solveScope') or {}).get('through'),
        attempts=[dict(status=x.get('status'), termination=x.get('termination'), conflicts=x.get('conflicts'),
                       geometry={k: (x.get('geometryCheck') or {}).get(k) for k in ('reason', 'tick', 'solidIndex', 'axis')},
                       candidateFilter={k: v for k, v in (x.get('candidateFilter') or {}).items() if k in ('accepted', 'reason')})
                  for x in result.get('contactAttempts', [])])

def evaluate_program(*args, **kwargs):
    fresh = kwargs.get('final_route') is None and not args[1].get('repairState')
    if not fresh:
        os.environ['PKC_AVOIDANCE'] = 'upfront'
        return real(*args, **kwargs)
    purpose = (kwargs.get('solve_policy') or {}).get('purpose')
    b_args = list(copy.deepcopy(args[:2]))+list(args[2:])
    b_args[4] = SearchWork(None, deadline=getattr(args[4], 'deadline', None))
    _, b = measure('conflict', b_args, dict(kwargs, on_progress=None))
    result, a = measure('upfront', list(args), kwargs)
    out.write(json.dumps(dict(purpose=purpose, ticks=args[1]['profile']['airtime'], upfront=a, conflict=b)) + '\n')
    out.flush()
    if a['verdict'] == 'feasible' and b['verdict'] != 'feasible':
        mismatches.append(1)
        if len(mismatches) >= int(os.environ.get('AB_STOP_AFTER', '1000000')):
            os._exit(0)
    return result
engine.evaluate_program = evaluate_program
sys.argv = ['tools.profiling.run'] + sys.argv[2:]
runpy.run_module('tools.profiling.run', run_name='__main__')
