"""Profile model preparation from archived programs; never dispatch an NLP.

Run as a module from the repository root. Saved repair cursors are deliberately
discarded: this is a fresh construction diagnostic, not a cold search/resume.
"""

import argparse
import cProfile
import copy
import gzip
import hashlib
import json
from pathlib import Path
import pstats
import subprocess
import time
from unittest.mock import patch

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program, _motion_bound
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import SearchWork
from standalone.autonomy.cell_routes.routes import Corridor
from standalone.autonomy.cell_routes.semantic_adapter import SemanticCorridorAdapter


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--compiler', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    archive = Path(__file__).resolve().parents[1] / 'automatic-corridors/matched-allocation-context-2026-11-03/automatic.json.gz'
    data = json.loads(gzip.decompress(archive.read_bytes()))
    scene = prepare_scene(data['result']['effectiveRequest'])
    state = data['result']['searchState']
    report = dict(sourceRevision=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
                  archive=str(archive.relative_to(root)), archiveHash=hashlib.sha256(archive.read_bytes()).hexdigest(),
                  purpose='Fresh archived-context preparation; no numerical dispatch; not cold discovery', contexts=[])
    def no_solve(*args, **kwargs):
        variants = kwargs['initial_variants']
        started = time.monotonic()
        emitted = 0
        first = None
        try:
            while True:
                next(variants)
                emitted += 1
                if first is None:
                    first = time.monotonic()-started
        except (StopIteration, TimeoutError) as error:
            outcome = type(error).__name__
        return dict(best=None, status='unknown', termination='profile_preparation_only', attempts=[],
                    profile=dict(itemCount=len(variants.items), exclusions=len(variants.exclusions),
                                 outcome=outcome, emitted=emitted, firstVariantSeconds=first,
                                 enumerationSeconds=time.monotonic()-started))
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        motion = _motion_bound(scene, compiler, 3)
        decomposition = decompose(scene)
        for route_id, tick in ((0, 41), (4, 17)):
            child = state['children'][str(route_id)]
            frame, record = next((f, r) for f in child['stack'] for r in f['records'] if r['ticks'] == tick)
            program = copy.deepcopy(record['program'])
            program.pop('repairState', None)
            corridor = Corridor(**dict(state['routes'][route_id], region_graph=state['graph']))
            adapter = SemanticCorridorAdapter(scene, corridor, decomposition=decomposition).for_goal(frame['index'])
            target_scene = dict(scene, goalBlock=program['terminalSupport'])
            profiler = cProfile.Profile()
            started = time.monotonic()
            with patch('standalone.autonomy.cell_routes.checkpoint_search.solve_contact_branches', no_solve):
                result = profiler.runcall(evaluate_program, target_scene, program, compiler, adapter,
                    SearchWork(1, deadline=time.monotonic()+8), motion, seconds=8, candidate_seconds=3,
                    required_landing=program['terminalSupport'], partial_corridor=True)
            stats = pstats.Stats(profiler)
            functions = sorted((dict(file=k[0], line=k[1], function=k[2], calls=v[1], selfSeconds=v[2], cumulativeSeconds=v[3])
                                for k, v in stats.stats.items()), key=lambda v: -v['cumulativeSeconds'])[:35]
            report['contexts'].append(dict(route=route_id, tick=tick, elapsedSeconds=time.monotonic()-started,
                result={k: result[k] for k in ('status', 'termination', 'profile')}, functions=functions))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps([dict(route=c['route'], tick=c['tick'], elapsedSeconds=c['elapsedSeconds']) for c in report['contexts']]))


if __name__ == '__main__':
    main()
