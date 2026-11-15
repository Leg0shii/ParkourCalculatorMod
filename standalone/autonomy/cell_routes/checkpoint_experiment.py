from ..witness import replay_witness
from ..reports import brief
import argparse
import gzip
import json
from pathlib import Path
import platform
import signal
import subprocess
import sys
import threading

from standalone.autonomy.block_contacts import validate_vertical_schedule
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.obstacle_geometry import check_geometry
from standalone.autonomy.route import prepare_route, schedule, supports
from standalone.ipopt import prepare, validate
from .checkpoint_search import find_checkpoint_route






def run_fixture(fixture, compiler, *, arm='guided', settings=None, python_executable=None, cancel_event=None):
    if arm not in ('baseline', 'generation', 'guided'):
        raise ValueError('Unknown checkpoint experiment arm')
    request = fixture['request']
    kwargs = dict(fixture.get('settings', {}), **(settings or {}))
    result = find_checkpoint_route(request, compiler,
        checkpoints=[] if arm == 'baseline' else fixture['checkpoints'], spatial_points=fixture.get('spatialPoints'),
        order='guided' if arm == 'guided' else 'generation', python_executable=python_executable,
        cancel_event=cancel_event, **kwargs)
    result['arm'] = arm
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--compiler', type=Path, default=Path('standalone/autonomy/build/compiler.json'))
    parser.add_argument('--python', default=sys.executable)
    parser.add_argument('--arms', nargs='+', choices=['baseline', 'generation', 'guided'], default=['baseline', 'generation', 'guided'])
    parser.add_argument('--repeats', type=int, default=1)
    parser.add_argument('--seconds', type=float)
    parser.add_argument('--calls', type=int)
    parser.add_argument('--beam-width', type=int)
    parser.add_argument('--unknown-slots', type=int)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error('repeats must be positive')
    cancel = threading.Event()
    for sign in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sign, lambda *_: cancel.set())
    paths = sorted(args.input.glob('*.json')) if args.input.is_dir() else [args.input]
    source = subprocess.run(['git', 'rev-parse', 'HEAD'], check=True, capture_output=True, text=True).stdout.strip()
    report = dict(source=source, python=sys.version, platform=platform.platform(), runs=[], summaries=[], validation=[])
    overrides = {}
    if args.seconds is not None:
        overrides['seconds'] = args.seconds
    if args.calls is not None:
        overrides['max_solver_calls'] = args.calls
    if args.beam_width is not None:
        overrides['beam_width'] = args.beam_width
    if args.unknown_slots is not None:
        overrides['unknown_slots'] = args.unknown_slots
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for repeat in range(args.repeats):
            for path in paths:
                fixture = json.loads(path.read_text())
                scene = prepare_scene(dict(fixture['request'], maxWallHits=0))
                for arm in args.arms:
                    result = run_fixture(fixture, compiler, arm=arm, settings=overrides,
                                         python_executable=args.python, cancel_event=cancel)
                    report['runs'].append(dict(name=path.stem, repeat=repeat, fixture=fixture, result=result))
                    summary = dict(name=path.stem, repeat=repeat, arm=arm, **brief(result))
                    report['summaries'].append(summary)
                    witnesses = [c['witness'] for w in result['windows'] for c in w['candidates'] if 'witness' in c]
                    if result['best']:
                        witnesses.append(result['best'])
                    for best in witnesses:
                        audit = replay_witness(scene, best, compiler)
                        report['validation'].append(dict(name=path.stem, arm=arm, repeat=repeat, **audit))
                        if not audit['passed']:
                            raise RuntimeError('Independent checkpoint witness replay failed: '+str(audit))
                    raw = json.dumps(report, indent=2, allow_nan=False).encode()
                    temporary = args.output.with_suffix(args.output.suffix+'.tmp')
                    temporary.write_bytes(gzip.compress(raw, mtime=0) if args.output.suffix == '.gz' else raw)
                    temporary.replace(args.output)
                    print(json.dumps(summary), flush=True)
                    if cancel.is_set():
                        return 130
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
