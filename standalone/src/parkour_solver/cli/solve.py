"""Run/resume untimed routing from unordered geometry and the task predicate."""

from parkour_solver.search.application import run
from parkour_solver.io.provenance import source_hashes, source_revision

import argparse
import os
import gzip
import hashlib
import json
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.validation.witness import replay_witness
from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.search.domain import InitialJumpDomain
from parkour_solver.io.reports import save
from parkour_solver.io.checkpoint_store import CheckpointStore, read_report
from parkour_solver.io.search_checkpoint import SearchCheckpoint


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=Path(os.environ.get('PKC_RUN_TICKS_COMPILER','build/native/compiler.json')))
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
    root=Path.cwd()
    report=dict(sourceRevision=source_revision(),
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
