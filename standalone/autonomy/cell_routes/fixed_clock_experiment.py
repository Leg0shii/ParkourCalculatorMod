import argparse
import copy
import json
import math
from pathlib import Path
import subprocess
import sys
import itertools

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from .checkpoints import Checkpoint
from .checkpoint_search import find_checkpoint_route
from .checkpoint_experiment import replay_witness
from .geometry import decompose
from .routes import corridor_for_points,landmarks
from .support_order import SupportOrder
from .two_goal_experiment import witnesses
from .window_experiment import save
from standalone.autonomy.solve_spec import full_witness


def run(fixture,compiler,*,on_window=None,python_executable=None,initial_programs=None,resume_state=None,on_progress=None,retry_unknown=False):
    request=copy.deepcopy(fixture['request'])
    virtual=fixture.get('terminalGoal')
    if virtual is not None:
        from .comparison_goals import goal_spec
        virtual=goal_spec(prepare_scene(request),virtual)
        # A finite collision-query envelope contains every center reachable in
        # the declared horizon. It is not an upper/lower bound on the goal.
        sample=compiler.compile(dict(prepare_scene(request),sharedMovement=True),
            dict(grounded=[True,True,False],jump=[False,True,False]),3)
        speed=math.hypot(*request['initial']['velocity'][::2]);radius=0.
        for _ in range(request['movementSearch']['maxInputs']):
            movement=speed+max(sample['magnitude'])
            radius+=movement
            speed=movement*max(sample['friction'])
        bounds=request['geometryBounds'][:]
        for axis in (0,2):
            bounds[axis]=min(bounds[axis],request['initial']['position'][axis]-radius-1.)
            bounds[axis+3]=max(bounds[axis+3],request['initial']['position'][axis]+radius+1.)
        request['geometryBounds']=bounds
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
        from standalone.autonomy.terminal_goals import satisfied
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
    parser.add_argument('--compiler',type=Path,default=Path('standalone/autonomy/build/compiler.json'))
    parser.add_argument('--resume',type=Path)
    parser.add_argument('--retry-unknown',action='store_true')
    args=parser.parse_args()
    fixture=json.loads(args.input.read_text())
    report=dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),fixture=fixture,windows=[])
    seeds, resumed = None, None
    if args.resume is not None:
        from .failure_diagnostic import read
        previous=read(args.resume)
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
        report['windows'].append(window)
        save(args.output,report)
        print(json.dumps(dict(tick=window['checkpoint']['tick'],scored=window['scored'],unknown=window['unknown'],
            retained=len(window['retained']),generation=window['generation']['termination'],
            progress=[c['value'] for c in window['candidates'] if c['id'] in window['retained']])),flush=True)
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        def checkpoint_progress(state):
            report['searchState']=state
            save(args.output,report)
        result,validation=run(fixture,compiler,on_window=progress,python_executable=sys.executable,initial_programs=seeds,
            resume_state=resumed,on_progress=checkpoint_progress,retry_unknown=args.retry_unknown)
    report.pop('searchState',None)
    report.update(result=result,validation=validation)
    save(args.output,report)
    best=result['best']
    print(json.dumps(dict(status=result['status'],termination=result['termination'],elapsed=result['elapsedSeconds'],
        work=result['work'],position=[best['candidate']['positions'][-1][0],best['candidate']['y'][-1],
        best['candidate']['positions'][-1][1]] if best else None,replays=len(validation))),flush=True)


if __name__=='__main__':main()
