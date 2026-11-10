"""Verify final source, tests, native replays and immutable historical evidence."""

import hashlib
import gzip
import json
from pathlib import Path
import subprocess

from standalone.autonomy.cell_routes.automatic_experiment import source_hashes


def main():
    here=Path(__file__).resolve().parent;root=here.parents[3]
    native=json.loads((here/'native-acceptance-2026-11-04/report.json').read_text())
    matched=json.loads((here/'matched-2026-11-04/summary.json').read_text())
    generic=json.loads((here/'generic-2026-11-04/summary.json').read_text())
    hashes=source_hashes()
    assert native['sourceHashes']==matched['sourceHashes']==generic['sourceHashes']==hashes
    assert native['sourceUnchanged'] and native['historicalArtifactsUnchanged']
    assert native['tests']==527 and native['failures']==native['errors']==native['skipped']==0
    assert all(hashlib.sha256((root/(name.replace('.','/')+'.py')).read_bytes()).hexdigest()==sha
               for name,sha in native['testSourceHashes'].items())
    assert len(matched['runs'])==2 and all(r['sourceUnchanged'] and r['allReplaysPassed'] and
        r['validatedWitnesses'] and r['ticks']==67 and r['y']==7 and r['onGround'] for r in matched['runs'])
    assert len(generic['runs'])==3 and all(r['sourceUnchanged'] and r['replays'] and
        all(v['passed'] for v in r['replays']) for r in generic['runs'])
    historical=here.parent/'event-route-discovery'
    prior=json.loads((historical/'acceptance-manifest-2026-11-03.json').read_text())
    assert all(hashlib.sha256((historical/name).read_bytes()).hexdigest()==v['sha256']
               for name,v in prior['artifactHashes'].items())
    files=[p for p in here.rglob('*') if p.is_file() and 'runtime' not in p.relative_to(here).parts and
           '.progress.' not in p.name and not p.name.endswith('.tmp') and p.name!='acceptance-manifest.json']
    report=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        sourceHashes=hashes,testSourceHashes=native['testSourceHashes'],
        native={k:native[k] for k in ('tests','failures','errors','skipped','elapsedSeconds','sourceUnchanged')},
        matched=matched['runs'],generic=generic['runs'],historicalArtifactsUnchanged=len(prior['artifactHashes']),
        compilerConfigHash=json.loads(gzip.decompress(
            (here/'matched-2026-11-04/guided_early_stop.json.gz').read_bytes()))['compilerConfigHash'],
        artifactHashes={str(p.relative_to(here)):dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),bytes=p.stat().st_size)
                        for p in sorted(files)},
        validationScope='Continuous physics and full supplied collision scene; no executable/game/actual-world replay or global optimality certificate')
    (here/'acceptance-manifest.json').write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(native=527,runtimeFiles=len(hashes),historicalArtifactsUnchanged=report['historicalArtifactsUnchanged'],
                         matchedReplays=sum(r['validatedWitnesses'] for r in matched['runs']),genericReplays=len(generic['runs']),
                         artifacts=len(files))),flush=True)


if __name__=='__main__':main()
