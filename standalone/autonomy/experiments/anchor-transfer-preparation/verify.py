"""Check corrected-source evidence without rewriting earlier manifests."""

import hashlib
import json
from pathlib import Path
import subprocess

from standalone.autonomy.cell_routes.automatic_experiment import source_hashes


def main():
    here=Path(__file__).resolve().parent;root=here.parents[3]
    source=source_hashes()
    native=json.loads((here/'native-2026-11-04/report.json').read_text())
    assert native['sourceHashes']==source and native['sourceUnchanged'] and native['historicalArtifactsUnchanged']
    assert native['tests']==535 and native['failures']==native['errors']==native['skipped']==0
    assert all(hashlib.sha256((root/(name.replace('.','/')+'.py')).read_bytes()).hexdigest()==sha
               for name,sha in native['testSourceHashes'].items())
    controls=[]
    for directory in ('initial0-90s-2026-11-04','combined-300s-2026-11-04'):
        summary=json.loads((here/directory/'summary.json').read_text())
        assert summary['sourceHashes']==source
        for row in summary['runs']:
            assert row['sourceUnchanged'] and row['allReplaysPassed'] and row['validatedWitnesses']>0
            assert row['Y']==7 and row['onGround']
            if row['arm']=='initial0':
                assert row['ticks']==64
                transfer=row['latches']['b1→b2']['transferStructure']
                assert transfer['preparationJumps']==[0] and transfer['transferJumps']==[12]
            controls.append(row)
    old=here.parent/'anchor-feasibility'
    prior=json.loads((old/'acceptance-manifest.json').read_text())
    assert all(hashlib.sha256((old/name).read_bytes()).hexdigest()==entry['sha256']
               for name,entry in prior['artifactHashes'].items())
    event=here.parent/'event-route-discovery'
    event_manifest=json.loads((event/'acceptance-manifest-2026-11-03.json').read_text())
    assert all(hashlib.sha256((event/name).read_bytes()).hexdigest()==entry['sha256']
               for name,entry in event_manifest['artifactHashes'].items())
    files=[p for p in here.rglob('*') if p.is_file() and p.name!='manifest.json' and not p.name.endswith('.tmp')
           and '.progress.' not in p.name]
    manifest=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        sourceHashes=source,testSourceHashes=native['testSourceHashes'],nativeTests=535,
        oldAnchorArtifactsUnchanged=len(prior['artifactHashes']),oldEventArtifactsUnchanged=len(event_manifest['artifactHashes']),runs=controls,
        artifactHashes={str(p.relative_to(here)):dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),bytes=p.stat().st_size)
                        for p in sorted(files)},
        validationScope='Corrected hard-transfer semantics; cold supplied-scene/native replay; no global optimality proof')
    (here/'manifest.json').write_text(json.dumps(manifest,indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(nativeTests=535,oldAnchorArtifactsUnchanged=len(prior['artifactHashes']),
                         artifacts=len(files),runs=[dict(arm=r['arm'],T=r['ticks'],replays=r['validatedWitnesses']) for r in controls])),flush=True)


if __name__=='__main__':main()
