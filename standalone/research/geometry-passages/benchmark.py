"""Fixed A-D inputs: compare retained path coverage with the old B6 ledger."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import time

from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.constraints.variants import ConstraintVariants
from parkour_solver.reachability.screen import condition_envelopes
from parkour_solver.reachability.service import InteractionGate
from parkour_solver.io.provenance import source_hashes, source_revision
from tools.profiling.bookkeeping import BookkeepingProfile, instrument
from tools.profiling.runtime import RuntimeProfile


def read(path):
    return json.loads(gzip.decompress(path.read_bytes()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parents[1]/'early-passage-screen/b6-options-evidence'
    before = source_hashes()
    result = dict(sourceRevision=source_revision(), sourceHashes=before, inputs={}, histories=[],
        accounting='Fixed complete B6 catalogues under the same recorded movement/parent requirements. Old ledger descriptions are source-era evidence; the new union is compared by entailed requirements, not cursor/order equality. Retained means pre-checks did not exclude; no NLP feasibility/optimality claim.')
    profiler = BookkeepingProfile(RuntimeProfile(), sample_every=1, sample_limit=512)
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler, instrument(profiler):
        for label in 'ABCD':
            path = root/f'history-{label}-input.json.gz'
            result['inputs'][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
            data = read(path)
            q = data['prepared']
            original = read(root/f'history-{label}-options.json.gz')
            gate = InteractionGate(data['scene'], compiler)
            analysis = gate.analyze_model(q['route'], q['plan'], q['vertical'])
            base = condition_envelopes(analysis['envelopes'], data['parent']['rows'])
            assert not base['proof']['ruledOut']
            owner = ConstraintVariants(q['route'], q['profiles'][0], q['plan'], dict(solids=[data['item']['box']]),
                base['envelopes'], dict(through=data['program']['profile']['airtime'], solidIndices=[0]),
                owners=['b6'], deadline=time.monotonic()+120)
            started = time.perf_counter()
            variants = list(owner)
            seconds = time.perf_counter()-started
            old_retained = [o for o in original if o['result'] == 'not_ruled_out']
            covered = [o['id'] for o in old_retained if any(owner.entailed(o['rows'], v['rows']) for v in variants)]
            assert len(covered) == len(old_retained), (label, set(o['id'] for o in old_retained)-set(covered))
            row = dict(history=label, oldDescriptions=len(original), oldRetainedDescriptions=len(old_retained),
                newRetainedAlternatives=len(variants), oldRetainedCovered=covered, seconds=seconds,
                metrics=owner.state['metrics'], timings=owner.state['timings'],
                variants=variants, screenExclusions=owner.state['screenExclusions'])
            result['histories'].append(row)
            print(json.dumps({k: v for k, v in row.items() if k not in ('variants', 'screenExclusions', 'oldRetainedCovered')}), flush=True)
    result['sourceUnchanged'] = before == source_hashes()
    assert result['sourceUnchanged']
    (args.output/'report.json').write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    (args.output/'bookkeeping.json').write_text(json.dumps(profiler.snapshot(), indent=2, allow_nan=False)+'\n')
    (args.output/'profile.json').write_text(json.dumps(profiler.runtime.snapshot(), indent=2, allow_nan=False)+'\n')


if __name__ == '__main__':
    main()
