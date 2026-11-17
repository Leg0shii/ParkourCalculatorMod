import argparse
import json
import os
from pathlib import Path
import signal
import sys
import threading

from parkour_solver.search.support_profiles import find_blocks
from parkour_solver.bridge.compiler import JavaCompiler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--compiler', default=os.environ.get('PKC_RUN_TICKS_COMPILER','build/native/compiler.json'))
    parser.add_argument('--python', default=os.environ.get('PKC_IPOPT_PYTHON'))
    parser.add_argument('--seconds', type=float, default=60)
    parser.add_argument('--route-seconds', type=float, default=8)
    parser.add_argument('--candidate-seconds', type=float, default=2)
    parser.add_argument('--retries', type=int, default=1)
    parser.add_argument('--support-search', action='store_true', help='Use the legacy support-profile search')
    parser.add_argument('--improve', action='store_true',
                        help='Accepted for compatibility; total-tick/objective search is always enabled')
    args = parser.parse_args()
    cancel = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, lambda *_: cancel.set())
    try:
        request = json.loads(Path(args.input).read_text())
        if args.support_search:
            request.pop('movementSearch', None)
        elif request.get('sceneModel') == 'collision_boxes':
            request.setdefault('movementSearch', {})
        config = json.loads(Path(args.compiler).read_text())
        with JavaCompiler(config) as compiler:
            result = find_blocks(request, compiler, seconds=args.seconds, route_seconds=args.route_seconds,
                                 candidate_seconds=args.candidate_seconds, retries=args.retries, improve=args.improve,
                                 python_executable=args.python, cancel_event=cancel)
    except (ValueError, OSError, RuntimeError) as error:
        result = {'status': 'error', 'error': str(error)}
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + '.tmp')
    temporary.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')
    temporary.replace(output)
    print(json.dumps({'status': result['status'], 'termination': result.get('termination'),
                      'blockIds': result.get('best', {}).get('blockIds') if result.get('best') else None,
                      'elapsedSeconds': result.get('elapsedSeconds')}))
    return {'feasible': 0, 'unknown': 2, 'error': 3, 'cancelled': 130}[result['status']]


if __name__ == '__main__':
    sys.exit(main())
