from tools.paths import RESEARCH_ROOT, DEFAULT_COMPILER
import argparse
import copy
import gzip
import json
from pathlib import Path
import subprocess
import sys
import time

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.program import truncate_program
from research.legacy_cell_routes.checkpoint_experiment import brief, replay_witness
from parkour_solver.search.engine import _key, evaluate_program, find_checkpoint_route
from parkour_solver.reachability.intervals import motion_bound
from parkour_solver.spatial.checkpoints import checkpoint, retain_prefix
from parkour_solver.search.compat.decision_windows import DecisionPolicy, plan_windows, score_features
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks
from research.legacy_cell_routes.window_experiment import save


def load(path):
    return json.loads(gzip.decompress(path.read_bytes()))


def controls(root):
    evidence = root/'checkpoint-guidance/evidence'
    main = load(evidence/'checkpoint-guidance-final.json.gz')
    greedy = load(evidence/'checkpoint-guidance-greedy-final.json.gz')
    for name, horizons, variants in [
        ('ceiling',[7,9,11],[('early_jump',greedy,'generation'),('late_jump',greedy,'guided')]),
        ('step-or-jump',[4,7,10],[('normal_step',main,'guided'),('slab_boost',greedy,'guided')]),
        ('grind-or-run',[3,7,10],[('chain_13',main,'guided'),('chain_11',greedy,'guided')])]:
        fixture = json.loads((root/'decision-windows/fixtures'/f'{name}.json').read_text())
        for label, report, arm in variants:
            best = next(r['result']['best'] for r in report['runs'] if r['name']==name and r['repeat']==0
                        and r['result']['arm']==arm)
            yield name,horizons,label,fixture,best


def compare(records):
    groups = []
    for name,h in sorted({(r['name'],r['h']) for r in records}):
        siblings = [r for r in records if r['name']==name and r['h']==h and r['score'] is not None]
        if not siblings:
            continue
        peak = max(r['score'] for r in siblings)
        finished = [r for r in siblings if r['bestCompletion'] is not None]
        reference = min((r['bestCompletion']['ticks'] for r in finished),default=None)
        winners = [r for r in siblings if abs(r['score']-peak)<1e-8]
        groups.append(dict(name=name,h=h,bestKnownTicks=reference,
            gaps=[dict(label=r['label'],gap=peak-r['score'],ticks=r['bestCompletion']['ticks']
                       if r['bestCompletion'] else None,prefixKey=r['prefixKey']) for r in siblings],
            greedyTicks=min((r['bestCompletion']['ticks'] for r in winners if r['bestCompletion']),default=None),
            bands=[dict(epsilon=epsilon,retained=[r['label'] for r in siblings if peak-r['score']<=epsilon+1e-9],
                        keepsBestKnown=any(r['bestCompletion'] and r['bestCompletion']['ticks']==reference
                            and peak-r['score']<=epsilon+1e-9 for r in siblings)) for epsilon in (.05,.25,.5,1.)]))
    return groups


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=DEFAULT_COMPILER)
    parser.add_argument('--python',default=sys.executable)
    parser.add_argument('--repeats',type=int,default=1)
    parser.add_argument('--continuation-seconds',type=float,default=6)
    args = parser.parse_args()
    root = RESEARCH_ROOT
    report = dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
                  sourceControls='checkpoint-guidance source 5e36f490; accepted fixed and greedy full programs',
                  runs=[],validation=[],comparisons=[])
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for repeat in range(args.repeats):
            records = []
            for name,horizons,label,fixture,known in controls(root):
                scene = prepare_scene(dict(fixture['request'],maxWallHits=0))
                d,gates = decompose(scene),landmarks(scene)
                corridor = corridor_for_points(d,fixture['spatialPoints'],gates)
                adapter = CorridorAdapter(scene,corridor,gates,decomposition=d)
                motion = motion_bound(scene,compiler,2)
                full = copy.deepcopy(known['movementProgram'])
                policy = DecisionPolicy(plan_windows(fixture['opportunities'],gap=fixture['settings']['cluster_gap'],
                                                     max_inputs=scene['movementSearch']['maxInputs']),scene)
                audit = policy.audit(full)
                if not audit['accepted']:
                    raise ValueError('Diagnostic control lies outside the tested opportunity domains')
                full['guideSeedYaw'] = known['candidate']['yaw'][:]
                for h in horizons:
                    program = truncate_program(full,h)
                    cp = checkpoint(corridor,gates,dict(tick=h))
                    work = SearchWork(16,deadline=time.monotonic()+4)
                    score = evaluate_program(scene,program,compiler,adapter,work,motion,checkpoint=cp,
                        seconds=4,candidate_seconds=.8,solver_calls=16,python_executable=args.python)
                    record = dict(name=name,label=label,repeat=repeat,h=h,prefixKey=_key(program),
                        knownTicks=known['plan']['ticks'],score=None,scoreResult=score,
                        reference=None,bestCompletion=None,continuation=None,fixture=fixture,opportunityAudit=audit)
                    completed, reference_best = [], None
                    if score['best'] is not None:
                        record['score'] = score['best']['candidate']['objective']
                        record['features'] = score_features(score['best'],cp)
                        retained = retain_prefix(program,score['best'])
                        reference_program = retain_prefix(full,score['best'])
                        reference_program['guideSeedYaw'] += known['candidate']['yaw'][h:]
                        reference = evaluate_program(scene,reference_program,compiler,adapter,
                            SearchWork(24,deadline=time.monotonic()+4),motion,seconds=4,
                            candidate_seconds=.8,solver_calls=24,python_executable=args.python)
                        record['reference'] = reference
                        reference_best = reference['best']
                        if reference_best:
                            completed.append(reference_best)
                        record['retainedPrefixKey'] = _key(retained)
                        continuation = find_checkpoint_route(scene,compiler,checkpoints=[],initial_programs=[retained],
                            spatial_points=fixture['spatialPoints'],seconds=args.continuation_seconds,
                            max_candidates=12,max_solver_calls=40,final_call_limit=24,score_seconds=3,
                            candidate_seconds=.8,fair_parents=True,python_executable=args.python)
                        record['continuation'] = continuation
                        if continuation['best']:
                            completed.append(continuation['best'])
                    if completed:
                        best = min(completed,key=lambda b:(b['plan']['ticks'],-b['candidate']['objective']))
                        record['bestCompletion'] = dict(ticks=best['plan']['ticks'],objective=best['candidate']['objective'])
                    witnesses = [b for b in [reference_best,score['best'],
                        record['continuation']['best'] if record['continuation'] else None] if b is not None]
                    for best in witnesses:
                        checked = replay_witness(scene,best,compiler)
                        report['validation'].append(dict(name=name,label=label,repeat=repeat,h=h,**checked))
                        if not checked['passed']:
                            raise RuntimeError('Diagnostic witness failed independent replay')
                    records.append(record)
                    report['runs'].append(record)
                    print(json.dumps(dict(name=name,label=label,h=h,repeat=repeat,score=record['score'],
                        completion=record['bestCompletion'],continuation=brief(record['continuation'])
                        if record['continuation'] else None)),flush=True)
                    report['comparisons'] = [dict(repeat=i,groups=compare([r for r in report['runs'] if r['repeat']==i]))
                                             for i in range(repeat+1)]
                    save(args.output,report)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
