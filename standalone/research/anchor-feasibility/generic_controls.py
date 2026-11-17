"""Native cold controls for same-block momentum chains and reflected turns."""

import argparse
import copy
import json
import os
from pathlib import Path
import time

from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.automatic_experiment import run, source_hashes
from standalone.autonomy.cell_routes.anchor_policy import local_counts
from standalone.autonomy.cell_routes.window_experiment import save


def fixtures():
    base=dict(version=1,sceneModel='collision_boxes',geometryBounds=[-15,-10,-10,15,12,10],
        initial=dict(position=[0,1,0],velocity=[0,-.0784000015258789,0],yaw=-90),
        startBlock='start',goalBlock='goal',objective=dict(axis='X',sense='MAX'),maxWallHits=0,
        maxContactCombinations=0,maxTotalRunTicks=0,maxRunTicksPerJump=0,maxJumps=3,
        blocks=[dict(id='start',boxes=[[-5,0,-2,6,1,2]]),dict(id='goal',boxes=[[9,0,-1,10,1,1]])],
        movementSearch=dict(types=['normal'],maxInputs=50,maxPrefixes=128,maxChoicesPerType=16))
    settings=dict(seconds=20,candidate_seconds=1,score_seconds=4,max_solver_calls=64,score_call_limit=8,final_call_limit=16)
    chain=dict(request=base,anchorSearch=dict(anchors=[]),settings=settings)
    yield 'same-block-chain',chain
    turn=copy.deepcopy(base)
    turn.update(initial=dict(position=[.5,1,.5],velocity=[.1,-.0784000015258789,0],yaw=-90),
        objective=dict(axis='Z',sense='MAX'),maxTotalRunTicks=12,maxRunTicksPerJump=12,maxJumps=4,
        blocks=[dict(id='start',boxes=[[0,0,0,1,1,1]]),dict(id='turn',boxes=[[2.5,0,0,3.5,2,1]]),
                dict(id='goal',boxes=[[0,0,1.2,1,3,2.2]])])
    yield 'turn-positive-x',dict(request=turn,anchorSearch=dict(anchors=['turn']),settings=settings)
    reflected=copy.deepcopy(turn)
    reflected['initial']['position'][0]*=-1;reflected['initial']['velocity'][0]*=-1
    reflected['initial']['yaw']=90
    for block in reflected['blocks']:
        for box in block['boxes']:box[0],box[3]=-box[3],-box[0]
    yield 'turn-negative-x',dict(request=reflected,anchorSearch=dict(anchors=['turn']),settings=settings)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    rows=[];source=source_hashes()
    for name,fixture in fixtures():
        started=time.monotonic()
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result,replays=run(fixture,compiler,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        best=result['best']
        row=dict(case=name,status=result['status'],termination=result['termination'],
            seconds=time.monotonic()-started,ticks=best['plan']['ticks'] if best else None,
            counts=local_counts(best['movementProgram']) if best else None,
            work=result['work'],replays=replays,sourceUnchanged=source==source_hashes(),
            turnLabels=result.get('anchorSearch',{}).get('observations'))
        save(args.output/(name+'.json.gz'),dict(fixture=fixture,result=result,validation=replays,sourceHashes=source,
            sourceUnchanged=row['sourceUnchanged']))
        rows.append(row);save(args.output/'summary.json',dict(sourceHashes=source,runs=rows))
        print(json.dumps(row),flush=True)
    return int(not all(r['sourceUnchanged'] and r['replays'] and all(v['passed'] for v in r['replays']) for r in rows))


if __name__=='__main__':raise SystemExit(main())
