"""Probe bound extraction and original-row monitoring without changing production."""
import argparse
import json
from pathlib import Path
import runpy
import subprocess
import numpy as np

from standalone.ipopt import worker
from standalone.ipopt.model import prepare, validate


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    helper=runpy.run_path(str(Path(__file__).with_name('benchmark.py')))
    source,request,_=helper['find_request'](args.input,'f05d5e3a7884');original=prepare(request)
    real_factory=worker.ca.nlpsol;records=[]
    for enabled,recompute in [(False,False),(True,False),(True,True)]:
        captured={}
        def factory(name,plugin,nlp,options):
            monitor=options['iteration_callback']
            if recompute:
                old_record=monitor.record
                original_g=worker.ca.Function('original_constraints',[nlp['x']],[nlp['g']])
                monitor.record=lambda x,g,f:old_record(x,original_g(x),f)
            solver=real_factory(name,plugin,nlp,dict(options,detect_simple_bounds=enabled))
            class Wrapped:
                def __call__(self,*a,**kw):
                    out=solver(*a,**kw);yaw=np.array(monitor.decode(out['x'])).ravel().tolist()
                    candidate=validate(original,yaw);g=np.array(out['g']).ravel()
                    bad=np.maximum(monitor.lower-g,g-monitor.upper);index=int(np.argmax(bad))
                    captured.update(decodedFinalFeasible=candidate['feasible'],decodedObjective=candidate['objective'],
                        returnedVectorVsOriginalBoundsMax=float(bad[index]),returnedVectorMismatchIndex=index,
                        monitorHadFeasible=monitor.best_yaw is not None)
                    return out
                def stats(self):return solver.stats()
            return Wrapped()
        worker.ca.nlpsol=factory
        try:result=helper['one_solve'](request,original,200)
        finally:worker.ca.nlpsol=real_factory
        records.append(dict(enabled=enabled,recomputedOriginalConstraints=recompute,result=result,finalSolverOutput=captured))
    assert records[0]['result']['originalRowsFeasible']
    assert records[1]['finalSolverOutput']['decodedFinalFeasible'] and not records[1]['finalSolverOutput']['monitorHadFeasible']
    assert records[2]['result']['originalRowsFeasible'] and records[2]['finalSolverOutput']['monitorHadFeasible']
    result=dict(inputSource=source,benchmarkRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        casadiVersion=worker.ca.__version__,productionCodeChanged=False,fullRouteSearch=False,records=records)
    args.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':main()
