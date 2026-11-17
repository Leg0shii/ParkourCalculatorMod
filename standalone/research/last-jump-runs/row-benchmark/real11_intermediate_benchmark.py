"""Repeated, fixed-request Real11 intermediate solves; no route discovery."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import sys
import time


MODES = ('directional_early_stop','constant_feasibility','optimization')


def directional_phase(function, factory, module):
    def phase(problem,yaw,pattern,seconds,stop,on_candidate,kind,seed,caps,attempt):
        def configured(name,plugin,nlp,options):
            options['iteration_callback'].stop = stop
            return factory(name,plugin,nlp,options)
        module.ca.nlpsol = configured
        try:
            return function(problem,yaw,pattern,seconds,False,on_candidate,kind,seed,caps,attempt)
        finally:
            module.ca.nlpsol = factory
    return phase


def worker_main(mode):
    from standalone.ipopt import worker
    if mode=='directional_early_stop':
        worker.fixed_phase = directional_phase(worker.fixed_phase,worker.ca.nlpsol,worker)
    worker.main()


def cases_from_report(report):
    from standalone.ipopt.model import prepare, fingerprint, validate
    from standalone.ipopt.constraints import reduce_walls
    state = report['result']['searchState']
    cases = {}
    for group in ('frames','stack','refinements','suspended'):
        for frame in state[group]:
            for record in frame['records']:
                if record.get('complete') or record.get('variantOf'):
                    continue
                for attempt in record.get('numericalAttempts',[]):
                    for diagnostic in attempt.get('diagnostics',[]):
                        if 'request' not in diagnostic:
                            continue
                        problem = prepare(diagnostic['request'])
                        identity = fingerprint(problem)
                        assert identity==diagnostic['problemHash']
                        if identity in cases:
                            continue
                        cases[identity] = dict(problemHash=identity,request=problem,frame=frame['id'],candidate=record['id'],
                            goal=record.get('goal',frame['goal']),ticks=problem['ticks'],
                            jumpInputs=[t for t,j in enumerate(record['program']['profile']['jump']) if j],
                            originalRows=len(problem['walls']),numericalRows=reduce_walls(problem['walls'])['audit']['retained'],
                            archivedWorkerStatus=diagnostic['status'],archivedProgramStatus=record['status'],
                            initialSeedFeasible=validate(problem,problem['yaw'])['feasible'],
                            fallbackSeedFeasible=validate(problem,problem.get('fallbackYaw',problem['yaw']))['feasible'])
    return sorted(cases.values(),key=lambda c:(c['goal'].get('block',''),c['ticks'],c['problemHash']))


def fresh_run(case,mode,seconds):
    from standalone.ipopt import solver
    from standalone.ipopt.model import validate
    original_command = solver._command
    solver._command = lambda python:[python or sys.executable,'-u',str(Path(__file__).resolve()),'--worker-mode',mode]
    try:
        result = solver.solve(case['request'],seconds=seconds,stop_on_feasible=mode!='optimization',python_executable=sys.executable)
    finally:
        solver._command = original_command
    checked = validate(case['request'],result['candidate']['yaw']) if result['candidate'] else None
    assert result['status']!='feasible' or checked and checked['feasible']
    return dict(problemHash=case['problemHash'],mode=mode,status=result['status'],termination=result['termination'],
        elapsedSeconds=result['elapsedSeconds'],firstFeasibleSeconds=result['firstFeasibleSeconds'],
        initialSeedAccepted=result['termination']=='first_feasible' and not result['phases'] and case['initialSeedFeasible'],
        originalRowsFeasible=bool(checked and checked['feasible']),
        measuredObjective=checked['objective'] if checked else None,
        exitPosition=checked['positions'][-1] if checked else None,
        exitVelocity=checked['velocity'][-1] if checked else None,
        yaw=checked['yaw'] if checked else None,phases=result['phases'],error=result['error'])


def warm_run(case,mode,seconds):
    from standalone.ipopt import worker
    from standalone.ipopt.continuation import Incumbent
    from standalone.ipopt.model import validate
    original_phase, original_send = worker.fixed_phase, worker.send
    phases = []
    if mode=='directional_early_stop':
        worker.fixed_phase = directional_phase(original_phase,worker.ca.nlpsol,worker)
    worker.send = phases.append
    incumbent = Incumbent(case['request'],lambda *_:None)
    began = time.perf_counter()
    try:
        problem = case['request']
        yaw,status,_ = worker.fixed_phase(problem,problem['yaw'],[[2,2]]*problem['ticks'],seconds,
            mode!='optimization',incumbent.consider,'carry','initial',[[0,0]]*problem['ticks'],0)
    finally:
        worker.fixed_phase, worker.send = original_phase, original_send
    elapsed = time.perf_counter()-began
    checked = validate(problem,incumbent.best['yaw'] if incumbent.best else yaw)
    return dict(problemHash=case['problemHash'],mode=mode,status=status,feasible=checked['feasible'],
        elapsedSeconds=elapsed,iterations=phases[-1]['iterations'],measuredObjective=checked['objective'])


def summarize(report):
    summaries = []
    for case in report['cases']:
        modes = {}
        for mode in MODES:
            fresh = [r for r in report['freshRuns'] if r['problemHash']==case['problemHash'] and r['mode']==mode]
            warm = [r for r in report['warmRuns'] if r['problemHash']==case['problemHash'] and r['mode']==mode]
            objectives = [r['measuredObjective'] for r in fresh if r['originalRowsFeasible']]
            modes[mode] = dict(feasible=sum(r['status']=='feasible' for r in fresh),runs=len(fresh),
                medianSeconds=statistics.median(r['elapsedSeconds'] for r in fresh),
                minSeconds=min(r['elapsedSeconds'] for r in fresh),maxSeconds=max(r['elapsedSeconds'] for r in fresh),
                medianWarmSeconds=statistics.median(r['elapsedSeconds'] for r in warm),
                medianWarmIterations=statistics.median(r['iterations'] for r in warm),
                measuredObjectiveRange=[min(objectives),max(objectives)] if objectives else None)
        summaries.append(dict(problemHash=case['problemHash'],goal=case['goal'],ticks=case['ticks'],
            initialSeedFeasible=case['initialSeedFeasible'],modes=modes))
    return summaries


def markdown(report):
    lines = ['# Real11 intermediate feasibility benchmark','',
        'All distinct intermediate requests in the saved retry; terminal-goal requests are excluded. '+
        f"Source code: `{report['revision']}`. Fresh workers: {report['repeats']} rotated repetitions per mode, "+
        f"{report['seconds']}s per request. Warm first-phase probes: {report['warmRepeats']} repetitions per mode.",'',
        '| Goal | Inputs | Seed already feasible | Directional early stop | Pure feasibility | Optimization | Feasible runs per mode |',
        '| --- | --- | --- | --- | --- | --- | --- |']
    for row in report['summaries']:
        modes = row['modes']
        times = [f"{1000*modes[m]['medianSeconds']:.1f}ms" for m in MODES]
        counts = ', '.join(f"{modes[m]['feasible']}/{modes[m]['runs']}" for m in MODES)
        lines.append('| '+str(row['goal'].get('block',row['goal']['kind']))+' | '+str(row['ticks'])+' | '+
            ('yes' if row['initialSeedFeasible'] else 'no')+' | '+' | '.join(times)+' | '+counts+' |')
    lines += ['',
        'Times include parent validation, worker startup, construction, solving and shutdown. '+
        'All modes use the same benchmark worker entry point, original/fallback seeds, hard constraints, '+
        'bound extraction and numerical-row reduction. Already feasible seeds return before worker startup in the two early-stop modes.',
        '',
        'Warm iteration counts come from a separate initial-seed first-phase probe. A fresh feasibility worker can stop '+
        'before emitting a completed phase message, so missing phase data is not reported as zero iterations. '+
        'Warm probes do not include fallback exploration or parent seed bypass.',
        '',
        'Every accepted candidate is independently scalar-replayed against every original request row; '+
        'facings, exit position/velocity and measured objective are in the JSON. No scene/corridor/orchestrator callbacks '+
        'are timed or rerun, so these are fixed continuous-request results, not complete-route or actual-world validation. '+
        'The 67-input B5 request was numerically feasible but its archived whole-program result was unknown.',
        '',
        'Unknown remains unknown, not proof of infeasibility. Pure feasibility does not optimize the reported progress. '+
        'No new full Real11 route search was run.','']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--worker-mode',choices=MODES)
    parser.add_argument('--input',type=Path)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--repeats',type=int,default=5)
    parser.add_argument('--warm-repeats',type=int,default=3)
    parser.add_argument('--seconds',type=float,default=3.)
    args = parser.parse_args()
    if args.worker_mode:
        worker_main(args.worker_mode)
        return
    if args.input is None or args.output is None or min(args.repeats,args.warm_repeats,args.seconds)<=0:
        parser.error('Supply --input, --output and positive repetitions/budget')
    from standalone.autonomy.checkpoint_store import read_report
    from standalone.ipopt import worker
    source = read_report(args.input)
    cases = cases_from_report(source)
    report = dict(revision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        source=str(args.input),sourceRevision=source['source'],sourceSha256=hashlib.sha256(args.input.read_bytes()).hexdigest(),
        scriptSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),casadiVersion=worker.ca.__version__,
        repeats=args.repeats,warmRepeats=args.warm_repeats,seconds=args.seconds,fullRouteSearch=False,
        cases=cases,freshRuns=[],warmRuns=[])
    args.output.parent.mkdir(parents=True,exist_ok=True)
    for repeat in range(args.repeats):
        for index,case in enumerate(cases):
            offset = (repeat+index)%len(MODES)
            for mode in MODES[offset:]+MODES[:offset]:
                row = fresh_run(case,mode,args.seconds)
                report['freshRuns'].append(dict(row,repetition=repeat))
            print(json.dumps(dict(repetition=repeat,goal=case['goal'],ticks=case['ticks'],
                outcomes=[(r['mode'],r['status'],round(r['elapsedSeconds'],4)) for r in report['freshRuns'][-3:]])),flush=True)
        args.output.write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    x = worker.ca.SX.sym('warm')
    worker.ca.nlpsol('warm','ipopt',dict(x=x,f=x*x),{'print_time':False,'ipopt.print_level':0,'ipopt.sb':'yes'})
    for repeat in range(args.warm_repeats):
        for index,case in enumerate(cases):
            offset = (repeat+index)%len(MODES)
            for mode in MODES[offset:]+MODES[:offset]:
                report['warmRuns'].append(dict(warm_run(case,mode,args.seconds),repetition=repeat))
    report['summaries'] = summarize(report)
    report['allAcceptedRevalidated'] = all(r['status']!='feasible' or r['originalRowsFeasible'] for r in report['freshRuns'])
    args.output.write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    args.output.with_suffix('.md').write_text(markdown(report))
    assert report['allAcceptedRevalidated']


if __name__=='__main__':
    main()
