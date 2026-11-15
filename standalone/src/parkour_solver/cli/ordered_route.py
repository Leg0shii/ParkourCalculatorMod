import argparse
import json
import os
from pathlib import Path
import threading
import signal
import sys

from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.search.ordered_route import find_route


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--compiler', default=__import__('os').environ.get('PKC_RUN_TICKS_COMPILER','build/native/compiler.json'))
    parser.add_argument('--python', default=os.environ.get('PKC_IPOPT_PYTHON'))
    parser.add_argument('--seconds', type=float, default=60)
    parser.add_argument('--candidate-seconds', type=float, default=2)
    parser.add_argument('--retries', type=int, default=1)
    parser.add_argument('--improve', action='store_true')
    args = parser.parse_args()
    cancel = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, lambda *_: cancel.set())
    try:
        request = json.loads(Path(args.input).read_text())
        config = json.loads(Path(args.compiler).read_text())
        with JavaCompiler(config) as compiler:
            result = find_route(request, compiler, seconds=args.seconds, candidate_seconds=args.candidate_seconds,
                                retries=args.retries, improve=args.improve, python_executable=args.python,
                                cancel_event=cancel)
    except (ValueError, OSError, RuntimeError) as error:
        result = {'status': 'error', 'error': str(error)}
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + '.tmp')
    temporary.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')
    temporary.replace(output)
    print(json.dumps({key: result.get(key) for key in ('status', 'termination', 'minimumProven', 'elapsedSeconds')}))
    return {'feasible': 0, 'unknown': 2, 'error': 3, 'cancelled': 130}[result['status']]


if __name__ == '__main__':
    sys.exit(main())
