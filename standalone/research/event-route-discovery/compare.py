"""Matched, unseeded automatic/supplied runs with identical snapshot cadence."""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.automatic_experiment import run as automatic_run, source_hashes
from standalone.autonomy.cell_routes.fixed_clock_experiment import run as supplied_run
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save
from standalone.autonomy.solve_spec import digest, full_witness


def verify_automatic(result,compiler):
    scene=prepare_scene(result['effectiveRequest'])
    seen=set();checks=[]
    states=[result['searchState']['dfs']]
    for state in states:
        for frame in sum((state.get(k,[]) for k in ('frames','stack','suspended','refinements','goalQueue')),[]):
            for record in frame['records']:
                witness=record.get('witness')
                if witness is None or not full_witness(witness):
                    continue
                key=digest([witness['route'],witness['profiles'],witness['candidate']['yaw']])
                if key in seen:
                    continue
                seen.add(key)
                checked=replay_witness(scene,witness,compiler)
                checks.append(dict(key=key,**checked))
                if not checked['passed']:
                    raise RuntimeError('Automatic stage witness failed independent replay')
    return checks


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,required=True)
    parser.add_argument('--seconds',type=float)
    parser.add_argument('--arms',nargs='+',choices=['automatic','supplied'],default=['automatic','supplied'])
    args=parser.parse_args()
    args.output_dir.mkdir(parents=True,exist_ok=False)
    here=Path(__file__).resolve().parent
    fixtures=dict(automatic=json.loads((here/'real11-auto.json').read_text()),
                  supplied=json.loads((here.parent/'virtual-finish/fixture.json').read_text()))
    if args.seconds is not None:
        for fixture in fixtures.values():fixture['settings']['seconds']=args.seconds
    assert fixtures['automatic']['request']==fixtures['supplied']['request']
    assert fixtures['automatic']['terminalGoal']==fixtures['supplied']['terminalGoal']
    assert fixtures['automatic']['initialJumpInputs']==fixtures['supplied']['initialJumpInputs']
    summary=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        sourceHashes=source_hashes(),driverHash=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        compilerConfigHash=hashlib.sha256(args.compiler.read_bytes()).hexdigest(),runs=[])
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for arm in args.arms:
            fixture=copy.deepcopy(fixtures[arm])
            compiler.compile(dict(prepare_scene(fixture['request']),sharedMovement=True),
                             dict(grounded=[True,True,False],jump=[False,True,False]),5)
            report=dict(arm=arm,fixture=fixture,sourceRevision=summary['sourceRevision'],sourceHashes=summary['sourceHashes'])
            path=args.output_dir/(arm+'.json.gz')
            started=time.monotonic();last_save=0.;last_note=0.
            def progress(state):
                nonlocal last_save,last_note
                report['searchState']=state
                now=time.monotonic()
                if now-last_save>=1.:
                    save(path,report);last_save=time.monotonic()
                if now-last_note>=15.:
                    calls=(state.get('dfs') or {}).get('cumulativeCalls',0) if arm=='automatic' else state.get('cumulativeCalls',0)
                    print(json.dumps(dict(arm=arm,elapsed=round(now-started,2),calls=calls)),flush=True)
                    last_note=now
            runner=automatic_run if arm=='automatic' else supplied_run
            result,validation=runner(fixture,compiler,python_executable=sys.executable,on_progress=progress)
            search_wall=time.monotonic()-started
            if arm=='automatic':validation=verify_automatic(result,compiler)
            report.pop('searchState',None)
            report.update(result=result,validation=validation,externalSearchWall=search_wall)
            save(path,report)
            best=result['best']
            item=dict(arm=arm,status=result['status'],termination=result['termination'],
                secondsCap=fixture['settings']['seconds'],searchWall=search_wall,
                reportedSearchSeconds=result['elapsedSeconds'],calls=result['work'].get('solverCalls',0),
                ticks=best['plan']['ticks'] if best else None,objective=best['candidate']['objective'] if best else None,
                independentReplays=len(validation),allReplaysPassed=all(v['passed'] for v in validation),
                expandedPrefixes=result['expandedPrefixes'],minimumTicksProven=result['minimumTicksProven'])
            summary['runs'].append(item)
            summary['sourceUnchanged']=source_hashes()==summary['sourceHashes']
            save(args.output_dir/'summary.json',summary)
            print(json.dumps(item),flush=True)


if __name__=='__main__':main()
