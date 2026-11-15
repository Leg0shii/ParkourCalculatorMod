import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CASES = [
    ('open', 'controls/open.json', True),
    ('remote-supports', 'controls/remote-supports.json', True),
    ('same-corridor-supports', 'controls/same-corridor-supports.json', True),
    ('wide-neo', 'controls/wide-neo.json', True),
    ('normal-step', '../../fixtures/stepping/normal_step.json', True),
    ('blip', '../../fixtures/stepping/blip.json', False),
    ('grind', '../../fixtures/generic-grind-blip.json', True),
    ('run-up', 'controls/run-up.json', True),
    ('ceiling', 'controls/ceiling.json', True),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='standalone/autonomy/build/cell-route-benchmark')
    parser.add_argument('--python', default=os.environ.get('PKC_IPOPT_PYTHON'))
    parser.add_argument('--compiler', default='standalone/autonomy/build/compiler.json')
    parser.add_argument('--cases', nargs='+', choices=[c[0] for c in CASES])
    parser.add_argument('--modes', nargs='+', choices=['generation', 'solve', 'generation-no-forward', 'solve-no-forward'],
                        default=['generation', 'solve', 'generation-no-forward', 'solve-no-forward'])
    parser.add_argument('--repeat', type=int, default=3)
    parser.add_argument('--route-caps', nargs='+', type=int, default=[10, 50, 100])
    args = parser.parse_args()
    if args.repeat < 1 or any(cap < 1 for cap in args.route_caps):
        parser.error('Repeat and route caps must be positive')
    for mode in args.modes:
        for name, relative, solve in CASES:
            if args.cases and name not in args.cases:
                continue
            if mode == 'solve' and not solve:
                continue
            if mode == 'generation-no-forward' and name not in ('remote-supports', 'same-corridor-supports', 'grind'):
                continue
            if mode == 'solve-no-forward' and name != 'grind':
                continue
            scene = (HERE/relative).resolve()
            request = json.loads(scene.read_text())
            request = request.get('request', request)
            if request.get('climbableCells') or request.get('maxWallHits', 2):
                raise ValueError('Benchmark scope excludes ladders and explicit wall-hit search')
            generation = mode.startswith('generation')
            caps = args.route_caps[:1] if mode == 'solve-no-forward' else args.route_caps
            command = [sys.executable, '-m', 'standalone.autonomy.cell_routes.experiment',
                '--input', str(scene), '--spatial-route', str(HERE/'controls'/f'{name}-route.json'),
                '--output', str(Path(args.output)/mode/name), '--mode', 'generation' if generation else 'solve',
                '--route-caps', *map(str, caps), '--seconds', '3' if generation else '6',
                '--max-expansions', '300', '--max-prefixes', '32' if generation else '64',
                '--repeat', str(args.repeat), '--route-seconds', '2', '--max-solver-calls', '12',
                '--compiler', args.compiler]
            if args.python:
                command.extend(['--python', args.python])
            if mode.endswith('no-forward'):
                command.append('--no-forward')
            print('RUN', mode, name, flush=True)
            subprocess.run(command, cwd=ROOT, check=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
