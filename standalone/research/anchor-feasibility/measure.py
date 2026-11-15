"""Fresh matched physical-task runs; never write into an existing run directory."""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from unittest.mock import patch

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes import checkpoint_search
from standalone.autonomy.cell_routes.automatic_experiment import run, source_hashes
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.turn_observability import draw
from standalone.autonomy.cell_routes.window_experiment import save


def witnesses(state):
    dfs=state['dfs'];seen=set()
    for group in ('frames','stack','suspended','refinements','goalQueue'):
        for frame in dfs.get(group,[]):
            for record in frame['records']:
                best=record.get('witness')
                if best is None:continue
                key=(record['id'],tuple(best['candidate']['yaw']))
                if key in seen:continue
                seen.add(key)
                yield record['id'],best


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--fixture',type=Path,default=Path(__file__).with_name('real11-anchors.json'))
    parser.add_argument('--seconds',type=float)
    parser.add_argument('--compiler',type=Path,default=Path(os.environ['PKC_RUN_TICKS_COMPILER']))
    parser.add_argument('--python',default=os.environ.get('PKC_IPOPT_PYTHON',sys.executable))
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    fixture=json.loads(args.fixture.read_text())
    if args.seconds is not None:fixture['settings']['seconds']=args.seconds
    root=Path(__file__).resolve().parents[4]
    source=source_hashes()
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    dirty=subprocess.check_output(['git','diff','--binary','--','standalone'],cwd=root,text=True)
    (args.output/'source.patch').write_text(dirty)
    # Include untracked new runtime modules through exact hashes and copied
    # source, since git diff alone cannot describe them before the feature commit.
    runtime=args.output/'runtime'
    for name in source:
        target=runtime/name;target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((root/name).read_bytes())
    result_rows=[]
    original=checkpoint_search.evaluate_program
    for arm in ('guided_early_stop','guided_polish_control'):
        arm_fixture=copy.deepcopy(fixture)
        def evaluate(*a,**kw):
            if arm=='guided_polish_control' and kw.get('solve_policy',{}).get('purpose') in ('node','anchor'):
                kw['solve_policy']=dict(kw['solve_policy'],stopOnFeasible=False)
            return original(*a,**kw)
        report=dict(sourceRevision=revision,sourceHashes=source,fixture=arm_fixture,arm=arm,
                    compilerConfigHash=hashlib.sha256(args.compiler.read_bytes()).hexdigest(),python=args.python)
        last_save=0.
        def progress(state):
            nonlocal last_save
            if time.monotonic()-last_save>=2.:
                save(args.output/(arm+'.progress.json.gz'),dict(report,searchState=state))
                last_save=time.monotonic()
        wall=time.monotonic()
        with JavaCompiler(json.loads(args.compiler.read_text())) as compiler, \
             patch.object(checkpoint_search,'evaluate_program',side_effect=evaluate):
            result,validation=run(arm_fixture,compiler,python_executable=args.python,on_progress=progress)
            replay=[dict(id=ident,**replay_witness(prepare_scene(result['effectiveRequest']),best,compiler))
                    for ident,best in witnesses(result['searchState'])]
        elapsed=time.monotonic()-wall
        report.update(result=result,validation=validation,independentReplays=replay,
                      totalWallSeconds=elapsed,sourceUnchanged=source==source_hashes())
        save(args.output/(arm+'.json.gz'),report)
        dfs=result['searchState']['dfs'];best=result['best']
        obs=dfs['observations'];guides=result['searchState']['guidePlan']
        for suffix in ('svg','pdf'):
            draw(guides,obs,args.output/(arm+'.'+suffix),trajectory=best['candidate']['positions'] if best else None)
        row=dict(arm=arm,status=result['status'],termination=result['termination'],
            ticks=best['plan']['ticks'] if best else None,objective=best['candidate']['objective'] if best else None,
            position=best['candidate']['positions'][-1] if best else None,
            y=best['candidate']['y'][-1] if best else None,onGround=best['candidate']['onGround'][-1] if best else None,
            firstFullSolutionSeconds=dfs.get('firstFullSolutionSeconds'),
            searchSeconds=result['elapsedSeconds'],totalWallSeconds=elapsed,declaredSeconds=arm_fixture['settings']['seconds'],
            work=result['work'],expandedPrefixes=result['expandedPrefixes'],
            validatedWitnesses=len(replay),allReplaysPassed=all(r['passed'] for r in replay),
            sourceUnchanged=report['sourceUnchanged'],latches=dfs['hardLatches'],
            runDeferrals=result['anchorSearch']['runDeferrals'],runRestorations=result['anchorSearch']['runRestorations'])
        result_rows.append(row)
        save(args.output/'summary.json',dict(sourceRevision=revision,sourceHashes=source,runs=result_rows,
            control='Same finite physical task/domain/guides/allocation; only intermediate stopOnFeasible differs',
            validationScope='Continuous equations and complete supplied collision scene; no game/executable replay or optimality certificate'))
        print(json.dumps(row),flush=True)
    return int(not all(r['sourceUnchanged'] and r['allReplaysPassed'] for r in result_rows))


if __name__=='__main__':raise SystemExit(main())
