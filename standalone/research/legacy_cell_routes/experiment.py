from tools.paths import DEFAULT_COMPILER
import argparse
import collections
import cProfile
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import pstats
import signal
import statistics
import subprocess
import sys
import threading
import time

from parkour_solver.numerics import prepare, validate
from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.search.movement import find_movement_blocks, iter_movement_programs
from parkour_solver.movement.settings import movement_settings
from parkour_solver.validation.scene import check_geometry
from parkour_solver.search.ranking import candidate_rank
from parkour_solver.scene.route import prepare_route, supports
from parkour_solver.spatial.cells import decompose, segment_intervals
from tools.plotting.cells import diagnostic_svg
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.bridge.counting import CountingCompiler; from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import Corridor, corridor_for_points, enumerate_routes, horizontal_trace, landmarks, prescribed_projection, signature


def content_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def verify_witness(scene, witness, compiler, seconds):
    best = witness.get('best', witness)
    if best is None:
        raise ValueError('Saved witness has no accepted candidate')
    route = prepare_route(best['route'])
    if route['initial'] != scene['initial'] or route['inputs'] != scene['inputs']:
        raise ValueError('Witness and requested original state/input policy differ')
    goal = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
    if route['blocks'][-1]['id'] != goal['id'] + '@' + str(len(route['blocks'])-1) or any(route['blocks'][-1][k] != goal[k] for k in ('bounds', 'top')):
        raise ValueError('Witness goal differs from requested scene')
    plan = dict(best['plan'])
    vertical = validate_vertical_schedule(route, best['profiles'], plan)
    if not vertical['feasible']:
        raise ValueError('Saved witness fails current vertical replay')
    plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'], referenceVelocity=vertical['vy'])
    coefficient = compiler.compile(route, plan, seconds)
    problem = prepare(dict(coefficient, version=1, ticks=plan['ticks'],
        position=scene['initial']['position'][::2], velocity=scene['initial']['velocity'][::2],
        yaw=best['candidate']['yaw'], threshold=route.get('physicsThreshold', .005), walls=plan['walls'], contacts=plan.get('contacts', []),
        ladderTicks=plan.get('ladderTicks', [False]*(plan['ticks']+1)),
        objective=dict(scene['objective'], tick=plan['ticks'])))
    candidate = validate(problem, problem['yaw'])
    if not candidate['feasible'] or any(not supports(route['blocks'][index], *candidate['positions'][tick], kind=kind)
                                      for tick, index, kind in plan['supportChecks']):
        raise ValueError('Saved witness fails current scalar/support replay')
    if any(supports(route['blocks'][index], *candidate['positions'][tick], kind='run') for tick, index in plan['exitChecks']):
        raise ValueError('Saved witness fails current run-off replay')
    candidate['rawMovement'] = [[(value if abs(value) >= problem['threshold'] else 0) + problem['magnitude'][t] *
        (math.cos(angle) if axis == 0 else math.sin(angle)) for axis, value in enumerate(candidate['velocity'][t])]
        for t, yaw in enumerate(candidate['yaw']) for angle in [problem['base'][t] + math.radians(yaw)]]
    candidate['friction'] = problem['friction']
    checked = check_geometry(scene['collisionScene'], plan, candidate, vertical, compiler, seconds)
    if not checked['feasible']:
        raise ValueError('Saved witness fails current shared-scene replay: ' + str(checked))
    candidate.update({k: vertical[k] for k in ('y', 'vy', 'onGround')})
    return candidate


def witness_corridor(decomposition, candidate, gates):
    points, _ = horizontal_trace(candidate)
    try:
        corridor = corridor_for_points(decomposition, points, gates, source='verified_witness_spatial_only')
        return corridor, dict(mapping='3d_yxz_phases', signature=corridor.signature, represented=True)
    except ValueError:
        cells = set()
        for a, b in zip(points, points[1:]):
            for c in decomposition.cells:
                box = c.bounds
                if min(a[0], b[0]) <= box[3] and max(a[0], b[0]) >= box[0] and min(a[2], b[2]) <= box[5] and max(a[2], b[2]) >= box[2]:
                    cells.add(c.id)
        if not cells:
            raise ValueError('Witness has no representable projected corridor')
        cell_ids = sorted(cells)
        corridor = Corridor(0, cell_ids, [], points, signature(points, gates), prescribed_projection(decomposition, cell_ids, points),
                            sum(math.dist(a, b) for a, b in zip(points, points[1:])), source='verified_witness_projection_only')
        return corridor, dict(mapping='horizontal_projection_only;exact_step_phases_unavailable', signature=corridor.signature,
                              represented=True, exact3dMapping=False)


def generation_totals(report):
    total = collections.Counter()
    for record in report.get('generation', []):
        for key, value in record.get('stats', {}).items():
            if type(value) in (int, float):
                total[key] += value
    return dict(total)


def run_arm(request, arm, *, mode='generation', seconds=10, route_cap=10, max_expansions=1000,
            compiler=None, python_executable=None, spatial_points=None, witness_candidate=None,
            max_solver_calls=None, candidate_seconds=1, route_seconds=3, retries=0, cancel_event=None, forward=True):
    if arm not in ('generic', 'prescribed', 'automatic') or mode not in ('generation', 'solve'):
        raise ValueError('Unsupported experiment arm/mode')
    if max_solver_calls is not None and (type(max_solver_calls) is not int or max_solver_calls < 1):
        raise ValueError('max_solver_calls must be positive')
    if not math.isfinite(seconds) or seconds <= 0:
        raise ValueError('seconds must be positive and finite')
    if mode == 'solve' and compiler is None:
        raise ValueError('Solving requires a worktree-local compiler')
    started = time.monotonic()
    deadline = started + seconds
    scene = prepare_scene(request)
    routes, geometry, route_report, coverage = [None], None, None, None
    gates = []
    if arm != 'generic':
        d = decompose(scene, deadline=deadline)
        geometry = d.to_dict()
        gates = landmarks(scene)
        if arm == 'prescribed':
            if witness_candidate is not None:
                prescribed, coverage = witness_corridor(d, witness_candidate, gates)
                routes = [prescribed]
            elif spatial_points is not None:
                if list(spatial_points[0]) != list(scene['initial']['position']):
                    raise ValueError('Prescribed route must start at the original initial position')
                prescribed = corridor_for_points(d, spatial_points, gates)
                goal = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
                last = spatial_points[-1]
                if abs(last[1] - goal['top']) > 1e-7 or not supports(goal, last[0], last[2]):
                    raise ValueError('Prescribed route must finish at the requested goal support')
                routes = [prescribed]
            else:
                raise ValueError('Prescribed arm requires spatial points or a verified witness')
        else:
            routes, route_report = enumerate_routes(scene, d, max_routes=route_cap, max_expansions=max_expansions,
                deadline=min(deadline, time.monotonic()+min(5, seconds*.25)), cancel_event=cancel_event)
            if witness_candidate is not None:
                known, audit = witness_corridor(d, witness_candidate, gates)
                coverage = dict(audit, automaticSignatureRetained=any(r.signature == known.signature for r in routes))
        route_data = [r.to_dict() for r in routes]
    else:
        route_data = []
    remaining_calls = max_solver_calls
    results, best = [], None
    for index, corridor in enumerate(routes):
        remaining = deadline - time.monotonic()
        if remaining <= 0 or remaining_calls is not None and remaining_calls <= 0 or cancel_event is not None and cancel_event.is_set():
            break
        allocation = remaining / (len(routes) - index)
        call_allocation = max(1, remaining_calls // (len(routes) - index)) if remaining_calls is not None else None
        adapter = CorridorAdapter(scene, corridor, gates, decomposition=d, forward=forward) if corridor is not None else None
        route_started = time.monotonic()
        work = SearchWork(call_allocation, deadline=route_started+allocation)
        counted = CountingCompiler(compiler) if compiler is not None else None
        if mode == 'generation':
            report = {}
            for program in iter_movement_programs(scene, settings=movement_settings(scene), deadline=route_started+allocation,
                    report=report, context_adapter=adapter.context if adapter else None, cancel_event=cancel_event, work=work):
                work.program(program)
            result = dict(status='generated', best=None, movementGeneration=report, termination=report.get('termination'))
        else:
            result = find_movement_blocks(request, counted, seconds=max(.001, allocation),
                candidate_seconds=candidate_seconds, route_seconds=route_seconds, retries=retries,
                python_executable=python_executable, cancel_event=cancel_event,
                context_adapter=adapter.context if adapter else None,
                candidate_filter=adapter.candidate if adapter else None, work=work)
        summary = work.summary()
        for key in ('firstProposalSeconds', 'firstAcceptedSeconds'):
            if summary[key] is not None:
                summary[key] += route_started-started
        if remaining_calls is not None:
            remaining_calls -= summary.get('solverCalls', 0)
        result.update(corridorId=corridor.id if corridor else None, work=summary,
            adapter=adapter.summary() if adapter else None, compiler=counted.summary() if counted else None,
            generationTotals=generation_totals(result['movementGeneration']), allocatedSeconds=allocation,
            observedSeconds=time.monotonic()-route_started)
        results.append(result)
        if result.get('best') is not None and (best is None or candidate_rank(result['best'], scene['objective']) < candidate_rank(best, scene['objective'])):
            best = result['best']
        if result['status'] == 'error':
            break
    return dict(arm=arm, mode=mode, status='error' if any(r['status']=='error' for r in results) else
        'feasible' if best else 'generated' if mode=='generation' else 'unknown', best=best,
        geometry=geometry, routes=route_data, routeGeneration=route_report, witnessCoverage=coverage,
        results=results, unattemptedRoutes=len(routes)-len(results), elapsedSeconds=time.monotonic()-started,
        budget=dict(seconds=seconds, maxSolverCalls=max_solver_calls, routeCap=route_cap),
        numericalCalls=sum(r['work'].get('solverCalls', 0) for r in results),
        minimumTicksProven=False, objectiveOptimalityProven=False)


def compact(run):
    results = run['results']
    return dict(arm=run['arm'], mode=run['mode'], status=run['status'], elapsedSeconds=run['elapsedSeconds'],
        routeCap=run['budget']['routeCap'], routes=len(run['routes']), unattemptedRoutes=run['unattemptedRoutes'],
        expandedPrefixes=sum(r['movementGeneration'].get('expandedPrefixes', 0) for r in results),
        generatedChoices=sum(r['movementGeneration'].get('generated', 0) for r in results),
        completePrograms=sum(r['work']['uniqueCompletePrograms'] for r in results),
        numericalCalls=run['numericalCalls'],
        firstAcceptedSeconds=min((r['work']['firstAcceptedSeconds'] for r in results if r['work']['firstAcceptedSeconds'] is not None), default=None),
        bestTicks=run['best']['plan']['ticks'] if run['best'] else None,
        bestObjective=run['best']['candidate'].get('objective') if run['best'] else None,
        forwardChecks=sum((r.get('adapter') or {}).get('forwardPrefixChecks',0) for r in results),
        forwardRejections=sum((r.get('adapter') or {}).get('forwardPrefixRejections',0) for r in results),
        forwardPhaseUpdates=sum((r.get('adapter') or {}).get('forwardPhaseUpdates',0) for r in results),
        skeletons=sum(r['generationTotals'].get('skeletons',0) for r in results),
        maskWork=sum(r['generationTotals'].get('maskWork',0) for r in results))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--mode', choices=['generation', 'solve'], default='generation')
    parser.add_argument('--arms', nargs='+', choices=['generic', 'prescribed', 'automatic'], default=['generic', 'prescribed', 'automatic'])
    parser.add_argument('--spatial-route')
    parser.add_argument('--witness')
    parser.add_argument('--compiler', default=str(DEFAULT_COMPILER))
    parser.add_argument('--python', default=os.environ.get('PKC_IPOPT_PYTHON'))
    parser.add_argument('--seconds', type=float, default=10)
    parser.add_argument('--candidate-seconds', type=float, default=1)
    parser.add_argument('--route-seconds', type=float, default=3)
    parser.add_argument('--retries', type=int, default=0)
    parser.add_argument('--max-solver-calls', type=int)
    parser.add_argument('--route-caps', nargs='+', type=int, default=[10, 50, 100])
    parser.add_argument('--max-expansions', type=int, default=1000)
    parser.add_argument('--max-prefixes', type=int)
    parser.add_argument('--repeat', type=int, default=1)
    parser.add_argument('--profile', action='store_true')
    parser.add_argument('--no-forward', action='store_true')
    args = parser.parse_args()
    if args.repeat < 1:
        parser.error('--repeat must be positive')
    cancel = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, lambda *_: cancel.set())
    request = json.loads(Path(args.input).read_text())
    if 'request' in request:
        request = request['request']
    if args.max_prefixes is not None:
        request.setdefault('movementSearch', {})['maxPrefixes'] = args.max_prefixes
    points = json.loads(Path(args.spatial_route).read_text()) if args.spatial_route else None
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    compiler = JavaCompiler(json.loads(Path(args.compiler).read_text())) if args.mode == 'solve' or args.witness else None
    candidate = None
    warmup_seconds = None
    if compiler is not None:
        warmup_started = time.monotonic()
        compiler.compile(dict(prepare_scene(request), sharedMovement=True),
                         dict(grounded=[True, True, False], jump=[False, True, False]), 10)
        warmup_seconds = time.monotonic()-warmup_started
    profile = cProfile.Profile() if args.profile else None
    runs = []
    try:
        if args.witness:
            candidate = verify_witness(prepare_scene(request), json.loads(Path(args.witness).read_text()), compiler, max(5,args.seconds))
        if profile:
            profile.enable()
        for repetition in range(args.repeat):
            for arm in args.arms:
                caps = args.route_caps if arm == 'automatic' else args.route_caps[:1]
                for cap in caps:
                    if cancel.is_set():
                        break
                    try:
                        run = run_arm(request, arm, mode=args.mode, seconds=args.seconds, route_cap=cap,
                            max_expansions=args.max_expansions, compiler=compiler, python_executable=args.python,
                            spatial_points=points, witness_candidate=candidate, max_solver_calls=args.max_solver_calls,
                            candidate_seconds=args.candidate_seconds, route_seconds=args.route_seconds,
                            retries=args.retries, cancel_event=cancel, forward=not args.no_forward)
                    except (ValueError, RuntimeError, OSError, TimeoutError) as error:
                        run = dict(arm=arm, mode=args.mode, status='unknown' if isinstance(error, TimeoutError) else 'error',
                            error=str(error), best=None, routes=[], geometry=None, results=[], unattemptedRoutes=0,
                            numericalCalls=0, elapsedSeconds=None, budget=dict(seconds=args.seconds, maxSolverCalls=args.max_solver_calls, routeCap=cap))
                    run['repetition'] = repetition
                    filename = f'{arm}-{cap}-{repetition}.json'
                    (output/filename).write_text(json.dumps(run, allow_nan=False, indent=2)+'\n')
                    if run['geometry'] is not None:
                        d = decompose(prepare_scene(request))
                        (output/f'{arm}-slice.svg').write_text(diagnostic_svg(d, y=request['initial']['position'][1]))
                    summary = dict(compact(run), file=filename, repetition=repetition)
                    runs.append(summary)
                    print(json.dumps(summary), flush=True)
        if profile:
            profile.disable()
            profile.dump_stats(str(output/'profile.pstats'))
            with (output/'profile.txt').open('w') as stream:
                pstats.Stats(profile, stream=stream).sort_stats('cumulative').print_stats(45)
    finally:
        if compiler:
            compiler.close()
    commit = subprocess.run(['git', 'rev-parse', 'HEAD'], capture_output=True, text=True, check=True).stdout.strip()
    dirty = subprocess.run(['git', 'status', '--porcelain'], capture_output=True, text=True, check=True).stdout
    aggregates = []
    for key in sorted({(r['arm'],r['routeCap']) for r in runs}):
        group = [r for r in runs if (r['arm'],r['routeCap']) == key]
        elapsed = [r['elapsedSeconds'] for r in group if r['elapsedSeconds'] is not None]
        aggregates.append(dict(arm=key[0], routeCap=key[1], repetitions=len(group),
            accepted=sum(r['status']=='feasible' for r in group), medianSeconds=statistics.median(elapsed) if elapsed else None,
            rangeSeconds=[min(elapsed),max(elapsed)] if elapsed else None))
    source_files = list(Path('standalone/autonomy/cell_routes').glob('*.py')) + [Path('standalone/autonomy')/name for name in
        ['movement_search.py','block_contacts.py','search.py','open_ladder.py','movement_contacts.py']]
    manifest = dict(baseline='99ecfe9', commit=commit, dirtyStatus=dirty, input=args.input, inputHash=content_hash(request),
        request=request, spatialPoints=points, witnessSource=args.witness, python=sys.version, platform=platform.platform(),
        workerPython=args.python, compilerConfig=json.loads(Path(args.compiler).read_text()) if args.mode=='solve' or args.witness else None,
        arguments=vars(args), profiled=args.profile, runs=runs, aggregates=aggregates, boundsDeferred=True,
        helperWarmupSeconds=warmup_seconds, sourceHashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files})
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2, allow_nan=False)+'\n')
    return 1 if any(r['status']=='error' for r in runs) else 0


if __name__ == '__main__':
    sys.exit(main())
