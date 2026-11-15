"""Fixed-request row-count A/B; no route discovery or production modification."""
import argparse
import copy
import gzip
import json
from pathlib import Path
import statistics
import subprocess
import sys
import time

from standalone.ipopt import worker
from standalone.ipopt.continuation import Incumbent
from standalone.ipopt.model import prepare, validate, fingerprint
from standalone.ipopt.solver import solve
from standalone.autonomy.reachability import reachable_envelopes
from standalone.autonomy.linear_reachability import linear_exclusion


def canonical(row):
    return tuple(row.get(k,'PLUS' if k=='op' else None) for k in ('mode','t1','t2','op','cmp','rhs'))


def variants(request):
    exact={};tight={}
    for row in request['walls']:
        assert row['mode'] in ('X','Z') and row.get('t2') is None and row['cmp'] in ('GE','LE')
        exact.setdefault(canonical(row),row)
        key=canonical(row)[:-1];old=tight.get(key)
        if old is None or (row['rhs']>old['rhs'] if row['cmp']=='GE' else row['rhs']<old['rhs']):tight[key]=row
    return {name:dict(copy.deepcopy(request),walls=copy.deepcopy(rows)) for name,rows in
        [('raw',request['walls']),('exact',list(exact.values())),('tight',list(tight.values()))]}


def find_request(path, prefix):
    report=json.loads(gzip.decompress(path.read_bytes()));state=report['result']['searchState']
    for group in ('frames','stack','refinements','suspended'):
        for frame in state[group]:
            for record in frame['records']:
                for attempt in record['numericalAttempts']:
                    for d in attempt.get('diagnostics',[]):
                        if d['problemHash'].startswith(prefix):
                            return report['source'],d['request'],report['result']['best']['candidate']['yaw']
    raise ValueError('Missing fixed request')


def one_solve(request,original,iterations):
    problem=prepare(dict(request,maxIterations=iterations));phase_info=[];record={}
    incumbent=Incumbent(problem,lambda *_:None)
    original_factory=worker.ca.nlpsol;original_send=worker.send;start=time.perf_counter()
    def factory(name,plugin,nlp,options):
        record['graphSeconds']=time.perf_counter()-start
        record.update(variables=int(nlp['x'].numel()),constraints=int(nlp['g'].numel()))
        before=time.perf_counter();solver=original_factory(name,plugin,nlp,options)
        record['solverSetupSeconds']=time.perf_counter()-before
        class Timed:
            def __call__(self,*a,**kw):
                before=time.perf_counter();result=solver(*a,**kw)
                record['solveSeconds']=time.perf_counter()-before
                return result
            def stats(self):return solver.stats()
        return Timed()
    worker.ca.nlpsol=factory;worker.send=lambda data:phase_info.append(data)
    try:
        yaw,status,_=worker.fixed_phase(problem,problem['yaw'],[[2,2]]*problem['ticks'],5.,False,
            incumbent.consider,'carry','initial',[[0,0]]*problem['ticks'],0)
    finally:worker.ca.nlpsol=original_factory;worker.send=original_send
    record.update(elapsedSeconds=time.perf_counter()-start,status=status,
        iterations=phase_info[-1]['iterations'],rows=len(problem['walls']))
    candidate=validate(original,incumbent.best['yaw'] if incumbent.best is not None else yaw)
    record.update(originalRowsFeasible=candidate['feasible'],objective=candidate['objective'])
    return record


def time_operation(fn,count):
    start=time.perf_counter()
    for _ in range(count):fn()
    return (time.perf_counter()-start)/count


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--hash',default='f05d5e3a7884');parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--repeats',type=int,default=5);parser.add_argument('--iterations',type=int,default=200)
    parser.add_argument('--external-repeats',type=int,default=3)
    args=parser.parse_args();source,request,common_yaw=find_request(args.input,args.hash);models=variants(request)
    original=prepare(request);ca=worker.ca;x=ca.SX.sym('warm')
    ca.nlpsol('warm','ipopt',{'x':x,'f':x*x},{'ipopt.print_level':0,'ipopt.sb':'yes','print_time':False})
    result=dict(inputSource=source,benchmarkRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        casadiVersion=ca.__version__,problemHash=fingerprint(original),maxIterations=args.iterations,
        fixedPhaseTimeLimit=5.,repeats=args.repeats,fullRouteSearch=False,runs=[],microbench={})
    # Rotate execution order to reduce warm-cache/order bias.
    names=list(models)
    for repetition in range(args.repeats):
        for name in names[repetition%3:]+names[:repetition%3]:
            row=one_solve(models[name],original,args.iterations);row.update(variant=name,repetition=repetition)
            result['runs'].append(row);print(json.dumps(row),flush=True)
            args.output.write_text(json.dumps(result,indent=2)+'\n')
    result['externalRuns']=[]
    for repetition in range(args.external_repeats):
        for name in names[repetition%3:]+names[:repetition%3]:
            outcome=solve(dict(models[name],maxIterations=args.iterations),seconds=2.,stop_on_feasible=False,
                python_executable=sys.executable)
            candidate=outcome['candidate']
            row=dict(variant=name,repetition=repetition,elapsedSeconds=outcome['elapsedSeconds'],status=outcome['status'],
                termination=outcome['termination'],firstFeasibleSeconds=outcome['firstFeasibleSeconds'],
                objective=candidate['objective'] if candidate else None,
                originalRowsFeasible=bool(candidate and validate(original,candidate['yaw'])['feasible']),phases=outcome['phases'])
            result['externalRuns'].append(row);print(json.dumps(dict(external=row)),flush=True)
            args.output.write_text(json.dumps(result,indent=2)+'\n')
    for name,raw in models.items():
        problem=prepare(raw)
        linear_exclusion(problem) # Warm the LP cache before timing preprocessing.
        result['microbench'][name]=dict(rows=len(problem['walls']),jsonBytes=len(json.dumps(raw).encode()),
            prepareSeconds=time_operation(lambda:prepare(raw),100),
            fingerprintSeconds=time_operation(lambda:fingerprint(problem),100),
            jsonSeconds=time_operation(lambda:json.dumps(raw),100),
            validateSeconds=time_operation(lambda:validate(problem,common_yaw),100),
            envelopeSeconds=time_operation(lambda:reachable_envelopes(problem),100),
            cachedLinearScreenSeconds=time_operation(lambda:linear_exclusion(problem),100))
    result['medians']={name:{k:statistics.median(r[k] for r in result['runs'] if r['variant']==name)
        for k in ('graphSeconds','solverSetupSeconds','solveSeconds','elapsedSeconds','iterations')} for name in names}
    result['externalMedians']={name:statistics.median(r['elapsedSeconds'] for r in result['externalRuns']
        if r['variant']==name) for name in names}
    result['allRecheckedAgainstOriginal']=all(r['originalRowsFeasible'] for r in result['runs']+result['externalRuns'])
    # Reconstruct the actual late progress-publication payload; no route search.
    saved=json.loads(gzip.decompress(args.input.read_bytes()))
    payload=dict(source=saved['source'],fixture=saved['fixture'],windows=saved['windows'],searchState=saved['result']['searchState'])
    result['lateSnapshot']={}
    for name,options in [('pretty',dict(indent=2)),('compact',dict(separators=(',',':')))]:
        begin=time.perf_counter();data=json.dumps(payload,allow_nan=False,**options).encode()
        encoded=time.perf_counter()-begin;begin=time.perf_counter();zipped=gzip.compress(data,mtime=0)
        compressed=time.perf_counter()-begin
        result['lateSnapshot'][name]=dict(jsonBytes=len(data),gzipBytes=len(zipped),encodeSeconds=encoded,
            gzipSeconds=compressed,totalSeconds=encoded+compressed)
        del data,zipped
    result['snapshotTransitionCount']=len(saved['result']['searchState']['transitions'])
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(dict(medians=result['medians'],externalMedians=result['externalMedians'],microbench=result['microbench'],
        snapshot=result['lateSnapshot'],allFeasible=result['allRecheckedAgainstOriginal'])),flush=True)


if __name__=='__main__':main()
