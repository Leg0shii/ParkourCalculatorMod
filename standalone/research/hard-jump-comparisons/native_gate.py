"""Run the recorded 52-module scope, new anchor tests and all IPOPT tests."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import unittest

from standalone.autonomy.cell_routes.automatic_experiment import source_hashes


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    args.output=args.output.resolve()
    args.output.mkdir(parents=True,exist_ok=False)
    here=Path(__file__).resolve().parent.parent/'anchor-feasibility'
    root=here.parents[3]
    os.chdir(root)  # Some archived controls intentionally use repository-relative paths.
    baseline=json.loads((here.parent/'event-route-discovery/native-forward-service-2026-11-03.json').read_text())
    modules=baseline['modules']+['standalone.autonomy.tests.'+m for m in
        ('test_anchor_policy','test_anchor_search','test_anchor_dfs','test_anchor_time_bound',
         'test_checkpoint_store','test_feasibility_mode','test_candidate_comparison',
         'test_proposal_analysis','test_transfer_time_bounds')]
    modules=list(dict.fromkeys(modules))
    modules += ['standalone.ipopt.tests.'+p.stem for p in sorted((root/'standalone/ipopt/tests').glob('test_*.py'))]
    source=source_hashes();started=time.monotonic()
    with (args.output/'tests.log').open('w') as stream:
        result=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromNames(modules))
    historical=here.parent/'event-route-discovery'
    prior=json.loads((historical/'acceptance-manifest-2026-11-03.json').read_text())
    unchanged=all(hashlib.sha256((historical/name).read_bytes()).hexdigest()==v['sha256']
                  for name,v in prior['artifactHashes'].items())
    report=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        sourceHashes=source,sourceUnchanged=source==source_hashes(),modules=modules,tests=result.testsRun,
        failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),
        elapsedSeconds=time.monotonic()-started,historicalArtifactsUnchanged=unchanged,
        testSourceHashes={m:hashlib.sha256((root/(m.replace('.','/')+'.py')).read_bytes()).hexdigest() for m in modules},
        excluded=baseline['excluded'])
    (args.output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ('tests','failures','errors','skipped','elapsedSeconds','sourceUnchanged','historicalArtifactsUnchanged')}),flush=True)
    return int(not result.wasSuccessful() or not unchanged or not report['sourceUnchanged'])


if __name__=='__main__':raise SystemExit(main())
