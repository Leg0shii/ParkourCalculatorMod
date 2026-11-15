"""Cold Real11 controls for one transfer with source-local setup jumps."""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.anchor_policy import local_counts
from standalone.autonomy.cell_routes.automatic_experiment import run, source_hashes
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save


def witnesses(state):
    seen=set()
    for group in ('frames','stack','suspended','refinements','goalQueue'):
        for frame in state['dfs'].get(group,[]):
            for record in frame['records']:
                best=record.get('witness')
                if best is None:continue
                key=(record['id'],tuple(best['candidate']['yaw']))
                if key not in seen:
                    seen.add(key);yield best


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--seconds',type=float,default=300)
    parser.add_argument('--arms',nargs='+',choices=('initial0','combined'),default=['initial0','combined'])
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    here=Path(__file__).resolve().parent;root=here.parents[3]
    fixture=json.loads((here.parent/'anchor-feasibility/real11-anchors.json').read_text())
    source=source_hashes();revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    (args.output/'source.patch').write_text(subprocess.check_output(['git','diff','--binary','--','standalone'],cwd=root,text=True))
    rows=[]
    for arm in args.arms:
        f=copy.deepcopy(fixture);f['settings']['seconds']=args.seconds
        if arm=='initial0':f['initialJumpInputs']=[0]
        last=None
        def progress(state):
            nonlocal last
            best=state.get('dfs',{}).get('best') if state.get('dfs') else None
            tick=best['plan']['ticks'] if best else None
            if tick is not None and tick!=last:
                last=tick
                print(json.dumps(dict(arm=arm,discoveredT=tick,firstFullSeconds=state['dfs'].get('firstFullSolutionSeconds'))),flush=True)
        started=time.monotonic()
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result,validation=run(f,compiler,python_executable=os.environ['PKC_IPOPT_PYTHON'],on_progress=progress)
            replay=[replay_witness(prepare_scene(result['effectiveRequest']),best,compiler)
                    for best in witnesses(result['searchState'])]
        best=result['best'];dfs=result['searchState']['dfs']
        frames={v['id']:v for group in ('frames','stack','suspended','refinements','goalQueue') for v in dfs.get(group,[])}
        root_records=[dict(T=r['arrival'],status=r['status'],transfer=r.get('transferStructure'))
                      for frame in frames.values() if frame['index']==0 for r in frame['records']]
        row=dict(arm=arm,initialJumpInputs=f['initialJumpInputs'],declaredSeconds=args.seconds,
            ticks=best['plan']['ticks'] if best else None,objective=best['candidate']['objective'] if best else None,
            Y=best['candidate']['y'][-1] if best else None,onGround=best['candidate']['onGround'][-1] if best else None,
            counts=local_counts(best['movementProgram']) if best else None,
            firstFullSolutionSeconds=dfs.get('firstFullSolutionSeconds'),searchSeconds=result['elapsedSeconds'],
            totalSeconds=time.monotonic()-started,work=result['work'],expandedPrefixes=result['expandedPrefixes'],
            validatedWitnesses=len(replay),allReplaysPassed=all(r['passed'] for r in replay),
            rootRecords=root_records,latches=dfs['hardLatches'],sourceUnchanged=source==source_hashes(),termination=result['termination'])
        save(args.output/(arm+'.json.gz'),dict(sourceRevision=revision,sourceHashes=source,sourceUnchanged=row['sourceUnchanged'],
            fixture=f,result=result,validation=validation,independentReplays=replay,
            compilerConfigHash=hashlib.sha256(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_bytes()).hexdigest()))
        rows.append(row);save(args.output/'summary.json',dict(sourceRevision=revision,sourceHashes=source,runs=rows,
            validationScope='Cold discovery and full supplied-scene replay; no archived seeds or global optimality proof'))
        print(json.dumps(row),flush=True)
    return int(not all(r['sourceUnchanged'] and r['allReplaysPassed'] for r in rows))


if __name__=='__main__':raise SystemExit(main())
