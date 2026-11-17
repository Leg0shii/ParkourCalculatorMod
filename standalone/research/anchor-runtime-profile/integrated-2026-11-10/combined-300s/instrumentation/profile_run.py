"""Profile a cold route run without modifying its source or search decisions."""

import argparse
import copy
import gc
import hashlib
import json
import os
from pathlib import Path
import resource
import shutil
import signal
import subprocess
import sys
import threading
import time

from runtime_profile import RuntimeProfile, instrument
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.checkpoint_store import CheckpointStore
from standalone.autonomy.cell_routes.automatic_experiment import run, source_hashes
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save


def witnesses(state):
    seen = set()
    for group in ('frames','stack','suspended','refinements','goalQueue'):
        for frame in state['dfs'].get(group,[]):
            for record in frame['records']:
                best = record.get('witness')
                if best is not None:
                    key = (record['id'],tuple(best['candidate']['yaw']))
                    if key not in seen:
                        seen.add(key)
                        yield best


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--seconds',type=float,default=300.)
    parser.add_argument('--compiler',type=Path,default=Path(os.environ.get('PKC_RUN_TICKS_COMPILER','compiler.json')))
    parser.add_argument('--python',default=os.environ.get('PKC_IPOPT_PYTHON'))
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=False)
    (args.output/'instrumentation').mkdir()
    for script in Path(__file__).parent.glob('*.py'):
        shutil.copyfile(script,args.output/'instrumentation'/script.name)
    fixture = json.loads(args.input.read_text())
    fixture['settings']['seconds'] = args.seconds
    import standalone.autonomy.cell_routes.depth_first as source_module
    root = Path(source_module.__file__).resolve().parents[3]
    source = source_hashes()
    provenance = dict(sourceRoot=str(root),sourceRevision=subprocess.check_output(
        ['git','rev-parse','HEAD'],cwd=root,text=True).strip(),sourceHashes=source,
        fixture=fixture,command=__import__('sys').argv,
        instrumentationHashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in Path(__file__).parent.glob('*.py')},
        compilerConfigHash=hashlib.sha256(args.compiler.read_bytes()).hexdigest(),
        accounting='Python selfSeconds exclude instrumented children; inclusive categories overlap. GC is an overlapping diagnostic. Profiler overhead is included; no speedup or optimality claim.')
    (args.output/'source.patch').write_text(subprocess.check_output(
        ['git','diff','HEAD','--binary','--',':(glob)standalone/**/*.py'],cwd=root,text=True))
    save(args.output/'provenance.json',provenance)
    profile = RuntimeProfile()
    cancelled = threading.Event()
    for sign in (signal.SIGINT,signal.SIGTERM):
        signal.signal(sign,lambda *_:cancelled.set())
    last_write = 0.
    gc_start = {};gc_times = dict(calls=0,seconds=0.)
    def gc_event(phase, info):
        generation = info['generation']
        if phase == 'start':
            gc_start[generation] = time.perf_counter()
        elif generation in gc_start:
            gc_times['calls'] += 1
            gc_times['seconds'] += time.perf_counter()-gc_start.pop(generation)
    def progress(state):
        nonlocal last_write
        now = time.monotonic()
        if now-last_write < 10.:
            return
        last_write = now
        dfs = state.get('dfs') or {}
        best = dfs.get('best')
        small = dict(elapsedSeconds=now-started,ticks=best['plan']['ticks'] if best else None,
                     expanded=dfs.get('expanded',0),sessions=len(profile.sessions),profile=profile.snapshot())
        profile.call('callback.profile_write',save,args.output/'progress.json',small)
        print(json.dumps({k:v for k,v in small.items() if k!='profile'}),flush=True)
    started = time.monotonic();cpu_start = time.process_time()
    gc.callbacks.append(gc_event)
    try:
        with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
            with instrument(profile):
                result, validation = profile.call('run.search_and_final_replay',run,copy.deepcopy(fixture),compiler,
                    python_executable=args.python,on_progress=profile.function('callback.progress',progress),
                    cancel_event=cancelled)
            scene = prepare_scene(result['effectiveRequest'])
            replays = profile.call('postrun.accepted_replays',lambda:[replay_witness(scene,best,compiler)
                                        for best in witnesses(result['searchState'])])
        best = result['best'];dfs = result['searchState']['dfs']
        deferrals = dfs.get('minimumRunExclusions',[])
        summary = dict(ticks=best['plan']['ticks'] if best else None,
            objective=best['candidate']['objective'] if best else None,
            firstFullSolutionSeconds=dfs.get('firstFullSolutionSeconds'),
            searchSeconds=result['elapsedSeconds'],work=result['work'],expandedPrefixes=result['expandedPrefixes'],
            termination=result['termination'],runDeferrals=len(deferrals),
            runRestorations=sum(bool(v.get('restored')) for v in deferrals),
            validatedWitnesses=len(replays),allReplaysPassed=all(v['passed'] for v in replays),
            sourceUnchanged=source==source_hashes(),sessions=profile.sessions)
        save(args.output/'summary.json',summary)
        with CheckpointStore(args.output/'report.json.gz') as store:
            profile.call('postrun.shared_report_write',store.save,
                         dict(provenance=provenance,result=result,validation=validation,independentReplays=replays))
            profile.call('postrun.shared_report_compact',store.compact)
        print(json.dumps({k:v for k,v in summary.items() if k!='sessions'}),flush=True)
        return int(not summary['sourceUnchanged'] or not summary['allReplaysPassed'])
    finally:
        gc.callbacks.remove(gc_event)
        snapshot = profile.snapshot()
        error = sys.exc_info()[1]
        snapshot.update(wallSeconds=time.monotonic()-started,cpuSeconds=time.process_time()-cpu_start,
                        maxRssBytes=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss*(1 if sys.platform=='darwin' else 1024),
                        error=None if error is None else dict(type=type(error).__name__,message=str(error)),
                        gc=gc_times,cancelled=cancelled.is_set())
        save(args.output/'profile.json',snapshot)


if __name__ == '__main__':
    raise SystemExit(main())
