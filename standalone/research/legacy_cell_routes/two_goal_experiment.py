from tools.paths import RESEARCH_ROOT, DEFAULT_COMPILER
import argparse
import copy
import json
from pathlib import Path
import subprocess
import sys

from parkour_solver.search.comparison import compare_candidates

from parkour_solver.bridge.compiler import JavaCompiler
from research.legacy_cell_routes.comparison_audit import run_full_audit, source_entries
from research.legacy_cell_routes.failure_diagnostic import read
from research.legacy_cell_routes.generation_diagnostic import save
from parkour_solver.search.identity import full_witness


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
    parser.add_argument('--part',choices=['audit','competitive-audit'],required=True)
    parser.add_argument('--input',type=Path,default=RESEARCH_ROOT/'decision-windows/fixtures')
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--compiler',type=Path,default=DEFAULT_COMPILER)
    parser.add_argument('--reference-seconds',type=float,default=4)
    parser.add_argument('--reference-calls',type=int,default=48)
    parser.add_argument('--prior-audit',type=Path)
    args=parser.parse_args()
    if args.part=='competitive-audit' and args.prior_audit is None:
        parser.error('Competitive audit requires --prior-audit')
    report=dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),runs=[],validation=[])
    with JavaCompiler(read(args.compiler)) as compiler:
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
