"""Isolate synchronous snapshot cost inside the existing solver callback."""
import argparse
import gzip
import json
from pathlib import Path
import runpy
import subprocess
import sys
import time

from standalone.ipopt.solver import solve


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    helpers=runpy.run_path(str(Path(__file__).with_name('benchmark.py')))
    source,request,_=helpers['find_request'](args.input,'f05d5e3a7884')
    saved=json.loads(gzip.decompress(args.input.read_bytes()))
    payload=dict(source=saved['source'],fixture=saved['fixture'],windows=saved['windows'],searchState=saved['result']['searchState'])
    rows=[]
    for label,options in [('none',None),('pretty',dict(indent=2)),('compact',dict(separators=(',',':')))]:
        costs=[]
        def incumbent(_):
            begin=time.perf_counter();data=json.dumps(payload,allow_nan=False,**options).encode()
            blob=gzip.compress(data,mtime=0);costs.append(time.perf_counter()-begin)
            del data,blob
        result=solve(request,seconds=2.,stop_on_feasible=False,python_executable=sys.executable,
            on_incumbent=incumbent if options is not None else None)
        row=dict(format=label,solveBudgetSeconds=2.,elapsedSeconds=result['elapsedSeconds'],termination=result['termination'],
            firstFeasibleSeconds=result['firstFeasibleSeconds'],status=result['status'],phases=result['phases'],
            callbackCount=len(costs),callbackSeconds=costs,objective=result['candidate']['objective'] if result['candidate'] else None)
        rows.append(row);print(json.dumps(row),flush=True)
        args.output.write_text(json.dumps(dict(inputSource=source,benchmarkRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),fullRouteSearch=False,
            probe='Fixed recorded request, final snapshot payload; serialization/compression only, no disk write.',runs=rows),indent=2)+'\n')


if __name__=='__main__':main()
