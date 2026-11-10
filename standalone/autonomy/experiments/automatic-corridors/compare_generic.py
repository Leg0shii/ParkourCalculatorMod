"""Small matched controls: automatic task entry versus authored spatial goals."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time

from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.automatic_experiment import source_hashes
from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,required=True)
    args=parser.parse_args()
    args.output_dir.mkdir(parents=True,exist_ok=False)
    cases=json.loads(Path(__file__).with_name('generic-fixtures.json').read_text())
    hashes=source_hashes()
    summary=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        sourceHashes=hashes,driverHash=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        compilerConfigHash=hashlib.sha256(args.compiler.read_bytes()).hexdigest(),runs=[])
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for case in cases:
            scene=case['request']
            for arm in ('automatic','supplied'):
                compiler.compile(dict(scene,sharedMovement=True),dict(grounded=[True,True,False],jump=[False,True,False]),5)
                settings=dict(case['settings'])
                routing=dict(spatial_settings=case['spatialSettings']) if arm=='automatic' else dict(
                    spatial_points=case['spatialPoints'],spatial_goals=case['spatialGoals'])
                start=time.monotonic()
                result=find_checkpoint_route(scene,compiler,python_executable=sys.executable,**settings,**routing)
                elapsed=time.monotonic()-start
                best=result['best']
                validation=replay_witness(scene,best,compiler) if best else None
                if validation and not validation['passed']:
                    raise RuntimeError('Generic incumbent failed independent replay')
                save(args.output_dir/(case['name']+'-'+arm+'.json.gz'),dict(
                    case=case,arm=arm,result=result,validation=validation,sourceHashes=hashes))
                item=dict(case=case['name'],arm=arm,status=result['status'],termination=result['termination'],
                    wallSeconds=elapsed,calls=result['work']['solverCalls'],
                    ticks=best['plan']['ticks'] if best else None,
                    objective=best['candidate']['objective'] if best else None,
                    replayPassed=validation['passed'] if validation else None)
                summary['runs'].append(item)
                summary['sourceUnchanged']=hashes==source_hashes()
                save(args.output_dir/'summary.json',summary)
                print(json.dumps(item),flush=True)
    summary['sourceUnchanged']=hashes==source_hashes()
    save(args.output_dir/'summary.json',summary)


if __name__=='__main__':main()
