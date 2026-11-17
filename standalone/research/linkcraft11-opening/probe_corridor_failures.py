import gzip
import json
import sys
import time
from pathlib import Path

from standalone.autonomy import block_contacts
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoint_search import _motion_bound, evaluate_program
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter, SearchWork
from standalone.autonomy.cell_routes.routes import audit_candidate, corridor_for_points, horizontal_trace, landmarks
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import compile_program
from standalone.autonomy.obstacle_geometry import check_geometry
from standalone.autonomy.route import prepare_route, schedule


ROOT = Path(__file__).resolve().parent


def read(name):
    with gzip.open(ROOT / 'evidence' / (name + '.json.gz'), 'rt') as handle:
        return json.load(handle)


def main():
    source = read('real11-clock64-event-priority')
    archived = read('real11-failure-numeric')['runs'][0]['result']
    fixture = source['fixture']
    scene = prepare_scene(fixture['request'])
    decomposition = decompose(scene)
    gates = landmarks(scene)
    corridor = corridor_for_points(decomposition, fixture['spatialPoints'], gates)
    branches = []
    active = None
    original = block_contacts.find_route

    class TracingAdapter(CorridorAdapter):
        def candidate(self, plan, candidate, vertical, *, partial=False):
            terminal_pre = any(t == len(candidate['positions']) - 2 and kind == 'landing'
                               for t, _, kind in plan.get('supportChecks', []))
            trajectory = dict(candidate, y=vertical['y'])
            verdict = audit_candidate(corridor, trajectory, gates, terminal_pre=terminal_pre, partial=partial)
            record = dict(verdict=verdict, trajectory=trajectory)
            if 'segment' in verdict:
                points, _ = horizontal_trace(trajectory)
                index = verdict['segment']
                tick, phase = divmod(index, 3)
                record['firstFailure'] = dict(input=tick, phase=['Y', 'X', 'Z'][phase],
                    segmentStart=points[index], segmentEnd=points[index+1])
            active['audits'].append(record)
            return super().candidate(plan, candidate, vertical, partial=partial)

    def traced_find_route(route, *args, **kwargs):
        nonlocal active
        active = dict(index=len(branches)+1, constraints=route['constraints'],
                      passages=route['passages'], audits=[])
        branches.append(active)
        result = original(route, *args, **kwargs)
        active['attempts'] = result.get('attempts', [])
        active['status'] = result['status']
        return result

    block_contacts.find_route = traced_find_route
    program = source['result']['finalAttempts'][1]['decision_program']
    adapter = TracingAdapter(scene, corridor, gates, decomposition=decomposition)
    checkpoint = Checkpoint(64, -1, (0, 1), tuple(scene['initial']['position']),
                            tuple(corridor.points[-1]), tuple(corridor.signature))
    try:
        with JavaCompiler(json.loads(Path('standalone/autonomy/build/compiler.json').read_text())) as compiler:
            motion = _motion_bound(scene, compiler, 5)
            start = time.monotonic()
            result = evaluate_program(scene, program, compiler, adapter,
                SearchWork(24, deadline=start+8), motion, checkpoint=checkpoint,
                seconds=8, candidate_seconds=2, solver_calls=24, python_executable=sys.executable)
            compiled = compile_program(scene, program, require_complete=False)
            route = prepare_route(compiled['route'])
            plan = schedule(route, [0])
            vertical = block_contacts.validate_vertical_schedule(route, compiled['profiles'], plan)
            for branch in branches:
                if branch['audits']:
                    audit = branch['audits'][-1]
                    audit['sharedSceneCheck'] = check_geometry(scene['collisionScene'], plan,
                        audit['trajectory'], vertical, compiler, 3)
    finally:
        block_contacts.find_route = original
    old_hashes = [a.get('problemHash') for a in archived['attempts']]
    new_hashes = [a.get('problemHash') for a in result['attempts']]
    report = dict(source='real11-failure-numeric:retained_slow_exact_final',
        fixture=fixture, corridor=corridor.to_dict(), gates=gates, branches=branches,
        result=result, archivedProblemHashes=old_hashes, reproducedProblemHashes=new_hashes,
        exactHashSequenceMatch=old_hashes == new_hashes)
    path = ROOT / 'evidence' / 'real11-corridor-failures.json.gz'
    with gzip.open(path, 'wt') as handle:
        json.dump(report, handle, separators=(',', ':'))
    print(json.dumps(dict(calls=result['work']['solverCalls'], termination=result['termination'],
                          exactHashSequenceMatch=report['exactHashSequenceMatch'])))
    for branch in branches:
        if branch['audits']:
            audit = branch['audits'][-1]
            print(json.dumps(dict(branch=branch['index'], failure=audit.get('firstFailure'),
                sceneCheck=audit['sharedSceneCheck'])))


if __name__ == '__main__':
    main()
