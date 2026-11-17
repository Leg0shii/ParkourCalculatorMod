"""Cross-check final source/evidence identities; keep original evidence immutable."""
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from standalone.autonomy.cell_routes.automatic_experiment import source_hashes

here=Path(__file__).resolve().parent
root=here.parents[3]
def read(path):
    data=path.read_bytes()
    return json.loads(gzip.decompress(data) if path.suffix=='.gz' else data)
native=read(here/'native-forward-service-2026-11-03.json')
coverage=read(here/'coverage-forward-service-2026-11-03.json.gz')
generic=read(here/'generic-forward-service-2026-11-03/summary.json')
matched=read(here/'matched-forward-service-2026-11-03/summary.json')
hashes=source_hashes()
assert native['sourceHashes']==coverage['sourceHashes']==generic['sourceHashes']==matched['sourceHashes']==hashes
assert native['sourceUnchanged'] and coverage['sourceUnchanged'] and matched['sourceUnchanged']
assert native['failures']==native['errors']==native['skipped']==0
assert all(r['allReplaysPassed'] and r['sourceUnchanged'] for r in generic['runs'])
assert all(r['allReplaysPassed'] for r in matched['runs'])
legacy=here.parent/'automatic-corridors'
prior=read(legacy/'acceptance-manifest-2026-11-03.json')
assert all(hashlib.sha256((legacy/name).read_bytes()).hexdigest()==v['sha256'] for name,v in prior['artifactHashes'].items())
files=[p for p in here.rglob('*') if p.is_file() and p.name!='acceptance-manifest-2026-11-03.json' and p.suffix!='.tmp']
report=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),sourceHashes=hashes,
    python=sys.version,worker='/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/.venv-guidance/bin/python',
    compilerConfigHash=hashlib.sha256(Path('/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json').read_bytes()).hexdigest(),
    native=dict(tests=native['tests'],failures=0,errors=0,skipped=0,sourceUnchanged=True),
    coverage={k:coverage[k] for k in ('rawGeometryAdmitted','semanticProjectionRepresented','usefulGuidesMaterialized')},
    generic=generic['runs'],matched=matched['runs'],legacyEvidenceUnchanged=len(prior['artifactHashes']),
    artifactHashes={str(p.relative_to(here)):dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),bytes=p.stat().st_size) for p in sorted(files)},
    validationScope='Standalone continuous physics, complete supplied collision scene and task replay; no game/export/actual-world or global optimality claim')
(here/'acceptance-manifest-2026-11-03.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(dict(native=native['tests'],legacyUnchanged=report['legacyEvidenceUnchanged'],artifacts=len(files))),flush=True)
