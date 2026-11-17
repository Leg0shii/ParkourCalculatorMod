"""Unseeded generic automatic cases; unknown is reported, never infeasible."""
import copy
import argparse
import json
from pathlib import Path
import sys
import time
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.automatic_experiment import run,source_hashes
from standalone.autonomy.cell_routes.window_experiment import save

here=Path(__file__).resolve().parent
base=json.loads((here.parent/'automatic-corridors/generic-fixtures.json').read_text())[0]['request']
# Return to a raw fixture representation before changing physical geometry.
def make(name,boxes,*,initial=None,types=('normal',),goal=None):
    request=copy.deepcopy(base)
    request.pop('collisionScene');request['blocks']=[dict(id=label,boxes=[box]) for label,box in boxes]
    request['startBlock']='start';request['goalBlock']='goal'
    request['movementSearch']['types']=list(types)
    if initial:request['initial'].update(initial)
    return dict(name=name,request=request,terminalGoal=goal,
        discoverySettings=dict(version=1,maxGraphExpansions=5000,maxGuideMaterializations=50),
        settings=dict(seconds=10,max_solver_calls=8,candidate_seconds=.7,score_seconds=2))
start=[-2,0,-2,3,1,2]
cases=[
 make('flat-gap',[('start',start),('goal',[5,0,-1,6,1,1])]),
 make('turn',[('start',start),('goal',[4,0,3,5,1,4])]),
 make('near-higher',[('start',start),('goal',[2,0,0,3,2,1])]),
 make('far-higher',[('start',start),('goal',[5,0,0,6,2,1])]),
 make('corner-detour',[('start',[-2,0,-2,2,1,2]),('avoid',[2.5,0,.2,3,5,1.5]),('goal',[4.5,0,-1,5.5,1,1])]),
 make('underpass',[('start',start),('ceiling',[1.5,3,-2,4,4,2]),('goal',[5,-2,-1,6,-1,1])],
      initial=dict(position=[2.8,1,.5],velocity=[.3,-.0784000015258789,0]),types=('normal','ceiling')),
 dict(make('airborne-prefix-control',[('start',start),('goal',[5,0,-1,6,1,1])],
      goal=dict(kind='landing_height_region',height=1.5,rows=[dict(mode='X',cmp='GE',rhs=1.)])),airbornePrefixControl=True),
]
parser=argparse.ArgumentParser();parser.add_argument('--only');parser.add_argument('--output-dir',type=Path,required=True);args=parser.parse_args()
if args.only:cases=[c for c in cases if c['name']==args.only]
path=args.output_dir;path.mkdir(exist_ok=False)
hashes=source_hashes();summary=dict(sourceHashes=hashes,runs=[])
with JavaCompiler(json.loads(Path('/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json').read_text())) as compiler:
    for fixture in cases:
        if fixture.get('airbornePrefixControl'):
            from standalone.autonomy.block_search import prepare_scene
            from standalone.autonomy.movement_composition import empty_program,boundary_state,append_choice
            from standalone.autonomy.open_movement import event_choice
            scene=prepare_scene(fixture['request']);root=empty_program(scene['initial'],threshold=0.)
            root['terminalSupport']=scene['startBlock']
            ctx=dict(state=boundary_state(root),threshold=0.)
            child=append_choice(root,event_choice(ctx,'normal',ctx['state'],1,jumping=True))
            fixture['settings']['initial_programs']=[child]
            fixture['provenance']='Explicit one-input jump prefix; airborne continuation control, not cold discovery'
        before=time.monotonic()
        result,validation=run(fixture,compiler,python_executable=sys.executable)
        best=result['best']
        item=dict(case=fixture['name'],status=result['status'],termination=result['termination'],
            wallSeconds=time.monotonic()-before,calls=result['work'].get('solverCalls',0),
            ticks=best['plan']['ticks'] if best else None,independentReplays=len(validation),
            allReplaysPassed=all(v['passed'] for v in validation),sourceUnchanged=hashes==source_hashes())
        save(path/(fixture['name']+'.json.gz'),dict(fixture=fixture,result=result,validation=validation,sourceHashes=hashes))
        summary['runs'].append(item);save(path/'summary.json',summary)
        print(json.dumps(item),flush=True)
