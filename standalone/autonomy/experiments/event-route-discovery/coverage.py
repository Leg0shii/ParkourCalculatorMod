"""Coverage oracle run after unseeded graph/guide discovery, never a solve seed."""
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.event_coverage_audit import audit
from standalone.autonomy.cell_routes.automatic_experiment import source_hashes
from standalone.autonomy.cell_routes.window_experiment import save

here=Path(__file__).resolve().parent
fixture=json.loads((here/'real11-auto.json').read_text())
archive=here.parent/'virtual-finish/evidence/initial0-cold.json.gz'
witness=json.loads(gzip.decompress(archive.read_bytes()))['result']['best']
before=source_hashes()
with JavaCompiler(json.loads(Path('/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json').read_text())) as compiler:
    report=audit(fixture,compiler,witness)
report.update(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),sourceHashes=before,
    sourceUnchanged=before==source_hashes(),archiveHash=hashlib.sha256(archive.read_bytes()).hexdigest())
save(here/'coverage-final-2026-11-03.json.gz',report)
print(json.dumps({k:report[k] for k in ('rawGeometryAdmitted','semanticProjectionRepresented','usefulGuidesMaterialized','sourceUnchanged')}),flush=True)
