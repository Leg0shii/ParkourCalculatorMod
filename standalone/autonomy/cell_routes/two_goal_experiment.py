import argparse
import copy
import json
from pathlib import Path
import subprocess
import sys

from standalone.autonomy.candidate_comparison import compare_candidates

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from .checkpoint_experiment import brief,replay_witness
from .comparison_audit import run_full_audit,source_entries
from .failure_diagnostic import read
from .generation_diagnostic import save
from .window_experiment import run_fixture
from standalone.autonomy.solve_spec import full_witness


def witnesses(result):
    if result.get('best') is not None and full_witness(result['best']):yield result['best']
    for window in result.get('windows',[]):
        for candidate in window['candidates']:
            if 'witness' in candidate and full_witness(candidate['witness']):yield candidate['witness']
            spatial=candidate.get('spatial_probe',{})
            if spatial.get('best') is not None and full_witness(spatial['best']):yield spatial['best']
    for rescue in result.get('rescues',[]):yield from witnesses(rescue['result'])


def competitive_followup(run,prior):
    best=run['result'].get('best')
    if best is None:raise ValueError('Competitive reference requires an accepted completion')
    unfinished={r['candidate'] for r in prior['candidates'] if not r['result']['complete']
        and r['selection'] not in ('retained','final_evaluated')}
    ticks=best['plan']['ticks']
    entries=[e for e in source_entries(run['result']) if e['id'] in unfinished
        and compare_candidates(dict(T=e['program']['profile']['airtime']),incumbent=dict(T=ticks))['action']!='exclude_bound']
    request=copy.deepcopy(run['fixture']['request'])
    request.setdefault('movementSearch',{}).update(maxInputs=ticks,maxPrefixes=4096,
        maxQueuedPrefixes=4096,maxChoicesPerType=200000)
    return request,entries


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--part',choices=['search','audit','competitive-audit'],required=True)
    parser.add_argument('--input',type=Path,default=Path('standalone/autonomy/experiments/decision-windows/fixtures'))
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=Path('standalone/autonomy/build/compiler.json'))
    parser.add_argument('--reference-seconds',type=float,default=4)
    parser.add_argument('--reference-calls',type=int,default=48)
    parser.add_argument('--prior-audit',type=Path)
    args=parser.parse_args()
    if args.part=='competitive-audit' and args.prior_audit is None:
        parser.error('Competitive audit requires --prior-audit')
    report=dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),runs=[],validation=[])
    with JavaCompiler(read(args.compiler)) as compiler:
        if args.part=='search':
            files=sorted(args.input.glob('*.json')) if args.input.is_dir() else [args.input]
            for path in files:
                fixture=read(path)
                scene=prepare_scene(fixture['request'])
                result=run_fixture(fixture,compiler,gap=.25,python_executable=sys.executable)
                record=dict(name=path.stem,fixture=fixture,result=result,summary=brief(result))
                report['runs'].append(record)
                for best in witnesses(result):
                    checked=replay_witness(scene,best,compiler)
                    report['validation'].append(dict(name=path.stem,**checked))
                    if not checked['passed']:raise RuntimeError('Two-goal candidate failed independent replay')
                save(args.output,report)
                print(json.dumps(dict(name=path.stem,**record['summary'])),flush=True)
        else:
            source=read(args.input)
            report['sourceSearch']=str(args.input)
            prior=read(args.prior_audit) if args.prior_audit is not None else None
            report['part']=args.part
            if prior is not None:report['priorAudit']=str(args.prior_audit)
            for run in source['runs']:
                request=run['fixture']['request']
                entries=None
                if args.part=='competitive-audit':
                    earlier=next(r['audit'] for r in prior['runs'] if r['name']==run['name'])
                    request,entries=competitive_followup(run,earlier)
                def progress(record):
                    r=record['result']
                    print(json.dumps(dict(name=run['name'],candidate=record['candidate'],selection=record['selection'],
                        ticks=r['best']['plan']['ticks'] if r['best'] else None,programs=len(r['trials']),
                        calls=r['work'].get('solverCalls',0),complete=r['complete'])),flush=True)
                audit=run_full_audit(request,run['result'],compiler,
                    seconds_per_candidate=args.reference_seconds,solver_calls=args.reference_calls,
                    python_executable=sys.executable,on_candidate=progress,candidate_entries=entries)
                report['runs'].append(dict(name=run['name'],audit=audit,referenceRequest=request,
                    selectedEntries=[e['id'] for e in entries] if entries is not None else None))
                save(args.output,report)
                print(json.dumps(dict(name=run['name'],auditCandidates=len(audit['candidates']),
                    complete=audit['continuationAuditComplete'],qualityLoss=audit['heuristicQualityLoss'],
                    discardedWinners=audit['discardedBeatsHeuristic'])),flush=True)


if __name__=='__main__':main()
