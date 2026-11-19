"""Run the explicitly recorded native acceptance scope from any cwd."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import unittest

STANDALONE_ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(STANDALONE_ROOT))
from parkour_solver.io.provenance import source_hashes, source_revision
from tools.validate_layout import verify


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args();args.output=args.output.resolve();args.output.mkdir(parents=True,exist_ok=False)
    scope=json.loads((STANDALONE_ROOT/'tools/native-suite.json').read_text())
    before=source_hashes();started=time.monotonic()
    suite=unittest.defaultTestLoader.loadTestsFromNames(scope['modules'])
    with (args.output/'tests.log').open('w') as stream:
        result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
    layout=verify()
    report=dict(sourceRevision=source_revision(),sourceHashes=before,sourceUnchanged=before==source_hashes(),
                modules=scope['modules'],excluded=scope['excluded'],tests=result.testsRun,
                failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),
                elapsedSeconds=time.monotonic()-started,layout=layout)
    (args.output/'report.json').write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k not in ('sourceHashes','modules')},indent=2),flush=True)
    return int(not result.wasSuccessful() or bool(result.skipped) or not report['sourceUnchanged'])


if __name__=='__main__':raise SystemExit(main())
