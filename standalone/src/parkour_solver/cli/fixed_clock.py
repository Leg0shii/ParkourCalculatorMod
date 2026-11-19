from parkour_solver.io.provenance import source_revision
import argparse
import copy
import json
import math
from pathlib import Path
import subprocess
import sys
import itertools

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import open_compiler
from parkour_solver.spatial.checkpoints import Checkpoint
from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.validation.witness import replay_witness
from parkour_solver.spatial.cells import decompose
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks
from parkour_solver.search.compat.support_order import SupportOrder
from parkour_solver.validation.witness import witnesses
from parkour_solver.io.checkpoint_store import CheckpointStore, read_report
from parkour_solver.io.search_checkpoint import SearchCheckpoint
from parkour_solver.search.identity import full_witness


def run(fixture,compiler,*,on_window=None,python_executable=None,initial_programs=None,resume_state=None,on_progress=None,retry_unknown=False):
    request=copy.deepcopy(fixture['request'])
    virtual=fixture.get('terminalGoal')
    if virtual is not None:
        from parkour_solver.search.goals import goal_spec
        virtual=goal_spec(prepare_scene(request),virtual)
        from parkour_solver.search.compat.corridor_queue import collision_request
        request=collision_request(request,compiler,3)
    scene=prepare_scene(request)
    d,gates=decompose(scene),landmarks(scene)
    corridor=corridor_for_points(d,fixture['spatialPoints'],gates)
    initial=tuple(scene['initial']['position'])
    target=next(b for b in scene['blocks'] if b['id']==(fixture['requiredSupports'][-1] if fixture['requiredSupports'] else scene['goalBlock']))
    end=((target['bounds'][0]+target['bounds'][1])/2,target['top'],(target['bounds'][2]+target['bounds'][3])/2)
    dx,dz=end[0]-initial[0],end[2]-initial[2]
    length=math.hypot(dx,dz)
    intermediate=(dx/length,dz/length)
    checkpoints=[Checkpoint(t,-1,(0.,1.) if t==fixture['comparisonTicks'][-1] else intermediate,initial,end,())
                 for t in fixture['comparisonTicks']]
    direction=tuple(fixture.get('terminalDirection',[0.,1.]))
    terminal=None if virtual is not None else Checkpoint(fixture['terminalTick'],-1,direction,initial,tuple(corridor.points[-1]),corridor.signature)
    policy=SupportOrder(scene,fixture['requiredSupports'],None if virtual is not None else fixture['terminalTick'],
        fixture.get('initialJumpInputs'),max_inputs=request['movementSearch']['maxInputs'])
    settings=copy.deepcopy(fixture['settings'])
    if virtual is not None:
        goals=settings.pop('spatial_goals',[dict(kind='landing',block=b) for b in fixture['requiredSupports']])
        if not goals or goals[-1]!=virtual:goals.append(virtual)
        settings['spatial_goals']=goals
    result=find_checkpoint_route(request,compiler,corridor=corridor,checkpoints=checkpoints,
        terminal_checkpoint=terminal,program_filter=policy,python_executable=python_executable,on_window=on_window,
        initial_programs=initial_programs,
        resume_state=resume_state,on_progress=on_progress,retry_unknown=retry_unknown,
        **settings)
    result['supportOrder']=fixture['requiredSupports']
    result['effectiveRequest']=request
    if virtual is not None:
        result['terminalGoal']=virtual
        result['objectiveOrder']=['earliest_landing_height_tick','max_objective_at_that_tick']
    if result['best'] is not None:
        result['bestSupportAudit']=policy.audit(result['best']['movementProgram'])
    if virtual is not None and result['best'] is not None:
        from parkour_solver.search.virtual_goals import satisfied
        best=result['best'];program=best['movementProgram']
        if not satisfied(program,best['candidate'],virtual,scene.get('supportClearance',1e-6)) or policy.remaining(program):
            raise RuntimeError('Best result does not satisfy the virtual finish and required supports')
        result['finish']=dict(tick=best['plan']['ticks'],height=virtual['height'],
            position=best['candidate']['positions'][-1],actualY=best['candidate']['y'][-1],
            physicalOnGround=best['candidate']['onGround'][-1],
            interpretation='nominal landing-height clock with terminal horizontal bounds, not an artificial physical floor')
    validation=[]
    final_witnesses=(r['witness'] for r in result.get('finalAttempts',[]) if 'witness' in r and full_witness(r['witness']))
    for witness in itertools.chain(witnesses(result),final_witnesses):
        checked=replay_witness(scene,witness,compiler)
        validation.append(checked)
        if not checked['passed']:raise RuntimeError('Route witness failed independent replay')
    return result,validation


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,help='Java bridge descriptor (default: PKC_RUN_TICKS_COMPILER, else Python)')
    parser.add_argument('--resume',type=Path)
    parser.add_argument('--retry-unknown',action='store_true')
    args=parser.parse_args()
    fixture=json.loads(args.input.read_text())
    report=dict(source=source_revision(),fixture=fixture)
    seeds, resumed = None, None
    if args.resume is not None:
        previous=read_report(args.resume)
        prior=previous.get('result',previous)
        resumed=prior.get('searchState',previous.get('searchState'))
        if resumed is not None:
            report['resume']=dict(source=str(args.resume),kind='exact_search_continuation')
        else:
            window=next((w for w in reversed(prior.get('windows',[])) if w['retained']),None)
            if window is None:raise ValueError('Legacy report has no retained histories; exact continuation is unavailable')
            by_id={c['id']:c['program'] for c in window['candidates'] if 'program' in c}
            seeds=[by_id[i] for i in window['retained']]
            report['resume']=dict(source=str(args.resume),kind='legacy_parent_reconstruction',
                                  tick=window['checkpoint']['tick'],histories=len(seeds))
    def progress(window):
        print(json.dumps(dict(tick=window['checkpoint']['tick'],scored=window['scored'],unknown=window['unknown'],
            retained=len(window['retained']),generation=window['generation']['termination'],
            progress=[c['value'] for c in window['candidates'] if c['id'] in window['retained']])),flush=True)
    with CheckpointStore(args.output) as store, open_compiler(args.compiler) as compiler:
        report['bridge']=compiler.provenance()
        checkpoint_progress=SearchCheckpoint(store,report)
        result,validation=run(fixture,compiler,on_window=progress,python_executable=sys.executable,initial_programs=seeds,
            resume_state=resumed,on_progress=checkpoint_progress,retry_unknown=args.retry_unknown)
        checkpoint_progress.finish(result,validation)
    best=result['best']
    print(json.dumps(dict(status=result['status'],termination=result['termination'],elapsed=result['elapsedSeconds'],
        work=result['work'],position=[best['candidate']['positions'][-1][0],best['candidate']['y'][-1],
        best['candidate']['positions'][-1][1]] if best else None,replays=len(validation))),flush=True)


if __name__=='__main__':main()
