"""Profile a cold route run without modifying its source or search decisions."""

import argparse
from contextlib import ExitStack, nullcontext
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

from tools.profiling.runtime import RuntimeProfile, instrument
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.io.checkpoint_store import CheckpointStore, read_report
from parkour_solver.io.search_checkpoint import SearchCheckpoint
from parkour_solver.search.application import run
from parkour_solver.io.provenance import source_hashes, source_revision
from parkour_solver.validation.witness import replay_witness
from parkour_solver.io.reports import save


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


class DurableProgress:
    """Keep the production callback protocol while timing each boundary."""
    def __init__(self, checkpoint, profile):
        self.checkpoint, self.profile = checkpoint, profile

    def __call__(self, *args, **kwargs):
        return self.profile.call('callback.durable.transition', self.checkpoint, *args, **kwargs)

    def begin_repair(self, *args, **kwargs):
        return self.profile.call('callback.durable.dispatch', self.checkpoint.begin_repair, *args, **kwargs)

    def repair_progress(self, *args, **kwargs):
        return self.profile.call('callback.durable.repair', self.checkpoint.repair_progress, *args, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--seconds',type=float,default=300.)
    parser.add_argument('--profile-mode',choices=('legacy','bookkeeping','none'),default='legacy')
    parser.add_argument('--sample-every',type=int,default=1024)
    parser.add_argument('--sample-limit',type=int,default=256)
    parser.add_argument('--allocation-samples',type=int,default=0)
    parser.add_argument('--durable',action='store_true')
    parser.add_argument('--resume',type=Path)
    parser.add_argument('--compiler',type=Path,default=Path(os.environ.get('PKC_RUN_TICKS_COMPILER','build/native/compiler.json')))
    parser.add_argument('--python',default=os.environ.get('PKC_IPOPT_PYTHON'))
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=False)
    (args.output/'instrumentation').mkdir()
    for script in Path(__file__).parent.glob('*.py'):
        shutil.copyfile(script,args.output/'instrumentation'/script.name)
    fixture = json.loads(args.input.read_text())
    fixture['settings']['seconds'] = args.seconds
    previous = None
    if args.resume is not None:
        prior = read_report(args.resume)
        previous = prior.get('result', {}).get('searchState') or prior.get('searchState')
        if previous is None:
            parser.error('Resume report has no saved automatic search state')
    from tools.paths import REPOSITORY_ROOT
    root = REPOSITORY_ROOT
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
    bookkeeping = None
    if args.profile_mode == 'bookkeeping':
        from tools.profiling.bookkeeping import BookkeepingProfile, instrument as instrument_bookkeeping
        bookkeeping = BookkeepingProfile(profile, sample_every=args.sample_every,
            sample_limit=args.sample_limit, allocation_samples=args.allocation_samples)
        hooks = instrument_bookkeeping(bookkeeping)
    else:
        hooks = instrument(profile) if args.profile_mode == 'legacy' else nullcontext()
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
        with ExitStack() as resources:
            compiler = resources.enter_context(JavaCompiler(json.loads(args.compiler.read_text())))
            checkpoint = None
            callback = profile.function('callback.progress',progress)
            if args.durable:
                durable_store = resources.enter_context(CheckpointStore(args.output/'report.json.gz'))
                checkpoint = SearchCheckpoint(durable_store, dict(provenance=provenance))
                callback = DurableProgress(checkpoint, profile)
            with hooks:
                result, validation = profile.call('run.search_and_final_replay',run,copy.deepcopy(fixture),compiler,
                    python_executable=args.python,on_progress=callback,
                    cancel_event=cancelled,resume_state=previous)
            scene = prepare_scene(result['effectiveRequest'])
            replays = profile.call('postrun.accepted_replays',lambda:[replay_witness(scene,best,compiler)
                                        for best in witnesses(result['searchState'])])
            if checkpoint is not None:
                checkpoint.metadata['independentReplays'] = replays
                profile.call('postrun.durable_finish',checkpoint.finish,result,validation)
        best = result['best'];dfs = result['searchState']['dfs']
        deferrals = dfs.get('minimumRunExclusions',[])
        summary = dict(ticks=best['plan']['ticks'] if best else None,
            objective=best['candidate']['objective'] if best else None,
            firstFullSolutionSeconds=dfs.get('firstFullSolutionSeconds'),
            searchSeconds=result['elapsedSeconds'],work=result['work'],expandedPrefixes=result['expandedPrefixes'],
            termination=result['termination'],runDeferrals=len(deferrals),
            runRestorations=sum(bool(v.get('restored')) for v in deferrals),
            deferredNodes=result.get('anchorSearch',{}).get('deferredNodes',0),
            comparisonWork=dfs.get('comparisonWork',{}),interactionMetrics=result.get('interactionGate',{}).get('metrics',{}),
            transferTimeBounds=dfs.get('transferTimeBounds',{}),
            wrappers=len(profile.requests) if args.profile_mode == 'legacy' else None,
            workers=sum(bool(r.get('workerLaunched')) for r in profile.requests) if args.profile_mode == 'legacy' else None,
            validatedWitnesses=len(replays),allReplaysPassed=all(v['passed'] for v in replays),
            sourceUnchanged=source==source_hashes(),sessions=profile.sessions,
            resume=result.get('resume'),profileMode=args.profile_mode,durable=args.durable)
        save(args.output/'summary.json',summary)
        if not args.durable:
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
        save(args.output/'requests.json',profile.requests)
        if bookkeeping is not None:
            save(args.output/'bookkeeping.json',bookkeeping.snapshot())
            save(args.output/'digest-examples.json',bookkeeping.examples)


if __name__ == '__main__':
    raise SystemExit(main())
