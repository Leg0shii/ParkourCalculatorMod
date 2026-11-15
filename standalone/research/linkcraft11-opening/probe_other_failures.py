import argparse
import copy
import gzip
import json
import sys
import time
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import append_choice, boundary_state, program_search_key
from standalone.autonomy.movement_search import movement_settings, screen_program
from standalone.autonomy.open_movement import event_choice, normal_choices
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.checkpoint_search import _key, _motion_bound, evaluate_program
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.decision_windows import fair_programs
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter, SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points, landmarks
from standalone.autonomy.cell_routes.support_order import SupportOrder
from standalone.autonomy.cell_routes.window_experiment import save


ROOT = Path(__file__).resolve().parent


def read(name):
    with gzip.open(ROOT / 'evidence' / (name + '.json.gz'), 'rt') as handle:
        return json.load(handle)


def summary(program):
    p = program['profile']
    return dict(ticks=p['airtime'], jumps=[i for i, j in enumerate(p['jump']) if j],
                runInputs=[i for i in range(p['airtime']) if p['grounded'][i] and not p['jump'][i]],
                contacts=[dict(state=e['offset']+1, box=e['box']) for e in p['contactEvents']],
                y=p['position'][-1], onGround=p['grounded'][-1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['generation', 'numeric', 'fresh', 'priority'])
    parser.add_argument('--compiler', type=Path, default=Path('standalone/autonomy/build/compiler.json'))
    args = parser.parse_args()
    source = read('real11-clock64-event-priority')
    f, result = source['fixture'], source['result']
    scene = prepare_scene(f['request'])
    d, gates = decompose(scene), landmarks(scene)
    corridor = corridor_for_points(d, f['spatialPoints'], gates)
    adapter = CorridorAdapter(scene, corridor, gates, decomposition=d)
    policy = SupportOrder(scene, f['requiredSupports'], 64)
    path = ROOT / 'evidence' / ('real11-failure-' + args.mode + '.json.gz')
    report = dict(mode=args.mode, source='real11-clock64-event-priority.json.gz', runs=[])
    h28 = result['windows'][1]
    candidates = {c['id']: c for c in h28['candidates']}
    kept = [candidates[k]['program'] for k in h28['retained']]

    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        if args.mode == 'generation':
            cfg = movement_settings(scene)
            cfg['maxPrefixes'] -= sum(w['generation']['expandedPrefixes'] for w in result['windows'][:2])
            generation = {}
            deadline = time.monotonic()+55
            def screen(p):
                if p['profile']['position'][1] < scene['initial']['position'][1]-1e-7:
                    return dict(reason='unselected_initial_floor')
                return screen_program(scene, p, compiler, 2)
            iterator = fair_programs(scene, kept, cfg=cfg, fair=True, report=generation, deadline=deadline,
                context_adapter=adapter.context, screen=screen, checkpoint_tick=44, threshold=0.,
                program_filter=policy, stop_on_goal=False, program_priority=policy.priority)
            original = [c['id'] for c in result['windows'][2]['candidates']]
            try:
                for index, p in enumerate(iterator, 1):
                    row = dict(index=index, id=_key(p), parent=p.get('searchParent'), **summary(p))
                    report['runs'].append(row)
                    contacts = p['profile']['contactEvents']
                    hit40 = any(e['offset']==39 and e['box']==[734,0,-1100,735,7.5,-1099] for e in contacts)
                    if row['jumps']==[9,18,30,41] and hit40:
                        report['target'] = dict(index=index, program=p)
                        break
                    if index >= 256:
                        break
                    if index % 20 == 0:
                        print(json.dumps(dict(proposals=index, expanded=generation['expandedPrefixes'])), flush=True)
            finally:
                iterator.close()
            report.update(generation=generation, first20MatchOriginal=[r['id'] for r in report['runs'][:20]]==original,
                          originalCandidateCap=20, noNumericalSolves=True)
            save(path, report)
            print(json.dumps(dict(emitted=len(report['runs']), first20MatchOriginal=report['first20MatchOriginal'],
                targetIndex=report.get('target', {}).get('index'), termination=generation['termination'])), flush=True)
            return

        motion = _motion_bound(scene, compiler, 5)
        def cp(t, signature=None, direction=None):
            return Checkpoint(t, -1, tuple(direction or [0,1]), tuple(scene['initial']['position']),
                              tuple(corridor.points[-1]), tuple(signature or ()))
        def evaluate(label, p, *, chosen_adapter=adapter, checkpoint=None, seconds=15, calls=24):
            start = time.monotonic()
            out = evaluate_program(scene, p, compiler, chosen_adapter,
                SearchWork(calls, deadline=start+seconds), motion, checkpoint=checkpoint or cp(64, corridor.signature),
                seconds=seconds, candidate_seconds=2, solver_calls=calls, python_executable=sys.executable)
            checked = replay_witness(scene, out['best'], compiler) if out['best'] else None
            report['runs'].append(dict(label=label, program=p, result=out, replay=checked))
            save(path, report)
            print(json.dumps(dict(label=label, status=out['status'], termination=out['termination'],
                calls=out['work'].get('solverCalls'), elapsed=time.monotonic()-start,
                objective=out['best']['candidate']['objective'] if out['best'] else None,
                replay=checked['passed'] if checked else None)), flush=True)
            return out

        sample = result['finalAttempts'][1]
        if args.mode == 'priority':
            previous = read('real11-failure-numeric')
            p = copy.deepcopy(sample['decision_program'])
            state = copy.deepcopy(p['repairState'])
            state['context'] = copy.deepcopy(previous['runs'][0]['result']['repairState']['context'])
            state['seen'] = []
            state['pending'].insert(0, dict(score=-1000, rows=[], passages=[], hits=[], warmYaw=p.get('guideSeedYaw')))
            p['repairState'] = state
            report['intervention'] = 'Same incoming55 repair pool promoted to current64 context with extension-style cleared seen; its existing cold base branch moved before inherited branches. Same8-second/24-call budget.'
            evaluate('cold_base_first_same_original_budget', p, seconds=8)
            return
        if args.mode == 'fresh':
            p = copy.deepcopy(sample['decision_program'])
            p.pop('repairState', None)
            evaluate('same_final_fresh_original_corridor', p, seconds=15)
            previous = read('real11-failure-numeric')
            accepted = next(r for r in previous['runs'] if r['label']=='same_final_scene_only')
            p['guideSeedYaw'] = accepted['result']['best']['candidate']['yaw'][:]
            evaluate('same_final_valid_seed_original_corridor', p, seconds=8)
            p = copy.deepcopy(sample['decision_program'])
            state = copy.deepcopy(previous['runs'][0]['result']['repairState'])
            state['pending'].insert(0, dict(score=-1000, rows=[], passages=[], hits=[], warmYaw=p.get('guideSeedYaw')))
            p['repairState'] = state
            evaluate('same_final_fresh_branch_before_inherited_queue', p, seconds=15)
            return
        p = copy.deepcopy(sample['decision_program'])
        out = evaluate('retained_slow_exact_final', p, seconds=8)
        for turn in range(2):
            if out['best'] is not None:
                break
            p = copy.deepcopy(p)
            p['repairState'] = out['repairState']
            out = evaluate('true_inner_resume_' + str(turn+1), p, seconds=15)

        class SceneOnly:
            gates = []
            def candidate(self, *args, **kwargs):
                return dict(accepted=True, validation='shared_scalar_and_scene_only')
        p = copy.deepcopy(sample['decision_program'])
        p.pop('repairState', None)
        relaxed = evaluate('same_final_scene_only', p, chosen_adapter=SceneOnly(), checkpoint=cp(64), seconds=15)
        if relaxed['best'] is not None:
            b = relaxed['best']
            report['sceneOnlyOriginalCorridorVerdict'] = adapter.candidate(b['plan'], b['candidate'],
                {k:b['candidate'][k] for k in ('y','vy','onGround')}, partial=True)
            report['sceneOnlyOriginalTerminalVerdict'] = cp(64, corridor.signature).stage(b['candidate'], gates)

        def append_normal(p, support, jumping):
            context = dict(scene=scene, state=boundary_state(p), maxInputs=64-p['profile']['airtime'], threshold=0.)
            c = next(c for c in normal_choices(context) if c['terminalSupport']==support and c['profile']['jump'][0]==jumping)
            return append_choice(p, c, max_inputs=64)

        parent = next(c for c in h28['candidates'] if c['id'].startswith('28223d4f73'))
        missing = copy.deepcopy(parent['program'])
        for _ in range(2):
            missing = append_normal(missing, 'b3', False)
        missing = append_normal(missing, 'b4', True)
        missing = append_normal(missing, 'b4', False)
        missing = append_choice(missing, event_choice(dict(threshold=0.), 'normal', boundary_state(missing),
            44-missing['profile']['airtime'], jumping=True), max_inputs=64)
        known = next(r for r in read('real11-terminal-variants')['runs'] if r['initialTakeoff']==[9] and r['jumpAfterB5'])
        missing['guideSeedYaw'] = known['result']['best']['candidate']['yaw'][:44]
        missing.pop('repairState', None)
        evaluate('missing_B3_wait3_from_actual_retained_parent', missing,
                 checkpoint=cp(44, direction=result['windows'][2]['checkpoint']['direction']), seconds=15)
        save(path, report)


if __name__ == '__main__':
    main()
