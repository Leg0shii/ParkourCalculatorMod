"""Source-stable native regression scope inherited from the recorded baseline."""
import hashlib
import json
from pathlib import Path
import subprocess
import time
import unittest
from standalone.autonomy.cell_routes.automatic_experiment import source_hashes

here=Path(__file__).resolve().parent
baseline=json.loads((here.parent/'automatic-corridors/allocation-context-final-native-2026-11-03.json').read_text())
modules=baseline['modules']+['standalone.autonomy.tests.'+m for m in (
    'test_event_domain','test_event_routes','test_interaction_normalization','test_guide_progress','test_event_search')]
source=source_hashes();started=time.monotonic()
path=here/'native-forward-service-2026-11-03.log'
with path.open('w') as stream:
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromNames(modules))
root=here.parents[3]
report=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
    sourceHashes=source,sourceUnchanged=source==source_hashes(),modules=modules,tests=result.testsRun,
    failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),elapsedSeconds=time.monotonic()-started,
    testSourceHashes={m:hashlib.sha256((root/(m.replace('.','/')+'.py')).read_bytes()).hexdigest() for m in modules},
    log=path.name,excluded='Dedicated ladder/climbable/vine/movement-entry modules, as in baseline; generic mixed ladder controls remain included')
(here/'native-forward-service-2026-11-03.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:report[k] for k in ('tests','failures','errors','skipped','elapsedSeconds','sourceUnchanged')}),flush=True)
raise SystemExit(not result.wasSuccessful())
