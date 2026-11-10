"""Run/resume untimed routing from unordered geometry and the task predicate."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time

from ..block_search import prepare_scene
from ..compiler import JavaCompiler
from .checkpoint_experiment import replay_witness
from .checkpoint_search import find_checkpoint_route
from .support_order import InitialJumpDomain
from .window_experiment import save
from ..checkpoint_store import CheckpointStore, read_report
from .checkpoint_persistence import SearchCheckpoint


def source_hashes():
    root=Path(__file__).resolve().parents[3]
    sources=sorted((root/'standalone/autonomy/cell_routes').glob('*.py'))
    sources += sorted((root/'standalone/autonomy').glob('*.py'))
    sources += sorted((root/'standalone/ipopt').glob('*.py'))
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}


def run(fixture,compiler,*,python_executable=None,resume_state=None,on_progress=None,cancel_event=None):
    request=fixture['request']
    if 'spatialSettings' in fixture:
        raise ValueError('Legacy spatialSettings is not event discovery; use discoverySettings version1')
    from ..movement_search import movement_settings
    cfg=movement_settings(prepare_scene(request))
    policy=InitialJumpDomain(fixture.get('initialJumpInputs'),max_inputs=cfg['maxInputs'])
    result=find_checkpoint_route(request,compiler,task_goal=fixture.get('terminalGoal'),
        anchor_search=fixture.get('anchorSearch'),
        discovery_settings=fixture.get('discoverySettings'),program_filter=policy,
        resume_state=resume_state,on_progress=on_progress,cancel_event=cancel_event,
        python_executable=python_executable,**fixture.get('settings',{}))
    validation=[]
    if result['best'] is not None:
        checked=replay_witness(prepare_scene(result['effectiveRequest']),result['best'],compiler)
        validation.append(checked)
        if not checked['passed']:
            raise RuntimeError('Automatic route incumbent failed independent physical replay')
    return result,validation


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=Path('standalone/autonomy/build/compiler.json'))
    parser.add_argument('--python',default=sys.executable)
    parser.add_argument('--resume',type=Path)
    args=parser.parse_args()
    fixture=json.loads(args.input.read_text())
    previous=None
    if args.resume is not None:
        prior=read_report(args.resume)
        previous=prior.get('result',{}).get('searchState') or prior.get('searchState')
        if previous is None:
            parser.error('Resume report has no saved automatic search state')
    cancel=threading.Event()
    for sign in (signal.SIGINT,signal.SIGTERM):
        signal.signal(sign,lambda *_:cancel.set())
    root=Path(__file__).resolve().parents[3]
    report=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        sourceHashes=source_hashes(),fixture=fixture)
    with CheckpointStore(args.output) as store, JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        progress=SearchCheckpoint(store,report)
        result,validation=run(fixture,compiler,python_executable=args.python,resume_state=previous,
                              on_progress=progress,cancel_event=cancel)
        report.update(sourceHashesAfter=source_hashes(),
                  sourceUnchanged=report['sourceHashes']==source_hashes())
        progress.finish(result,validation)
    best=result['best']
    print(json.dumps(dict(status=result['status'],termination=result['termination'],work=result['work'],
        routes=(result.get('routeGeneration') or {}).get('routes',0),ticks=best['plan']['ticks'] if best else None,
        objective=best['candidate']['objective'] if best else None,validation=validation)),flush=True)
    return 130 if cancel.is_set() else 0


if __name__=='__main__':raise SystemExit(main())
