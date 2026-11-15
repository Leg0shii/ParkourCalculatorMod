import argparse
import gzip
import json
import os
import pathlib
import sys
import tempfile
import threading

if __package__:
    from parkour_solver.numerics.solver import solve
    from parkour_solver.io.capture import attach_capture
else:
    from solver import solve
    from parkour_solver.io.capture import attach_capture


def read_json(path):
    path = pathlib.Path(path)
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'rt', encoding='utf-8') as stream:
        return json.load(stream)


def write_json(path, result):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as stream:
            temporary = stream.name
            json.dump(result, stream, allow_nan=False, indent=2)
            stream.write('\n')
        os.replace(temporary, path)
    finally:
        if temporary and os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description='Standalone continuous IPOPT trajectory solver')
    parser.add_argument('--input', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--seconds', type=float)
    parser.add_argument('--python', dest='python_executable', default=os.environ.get('PKC_IPOPT_PYTHON'))
    parser.add_argument('--feasibility-only', action='store_true')
    parser.add_argument('--threshold', type=float)
    parser.add_argument('--no-recorded-walls', action='store_true')
    parser.add_argument('--initial-solution', type=pathlib.Path)
    parser.add_argument('--ladder-capture', type=pathlib.Path)
    parser.add_argument('--capture-start', type=int)
    args = parser.parse_args()
    cancel = threading.Event()
    import signal
    signal.signal(signal.SIGINT, lambda *_: cancel.set())
    signal.signal(signal.SIGTERM, lambda *_: cancel.set())
    try:
        request = read_json(args.input)
        if args.threshold is not None:
            request['threshold'] = args.threshold
        if args.no_recorded_walls:
            request['contacts'] = []
        if args.initial_solution:
            request.setdefault('fallbackYaw', request['yaw'])
            request['yaw'] = read_json(args.initial_solution)['candidate']['yaw']
        if args.ladder_capture:
            request = attach_capture(request, read_json(args.ladder_capture), args.capture_start)
        elif args.capture_start is not None:
            raise ValueError('--capture-start requires --ladder-capture')
        result = solve(request, seconds=args.seconds, stop_on_feasible=True if args.feasibility_only else None,
                       python_executable=args.python_executable, cancel_event=cancel)
    except (ValueError, KeyError, TypeError, OSError) as error:
        result = {'version': 1, 'status': 'error', 'feasible': False, 'candidate': None, 'error': str(error)}
    try:
        write_json(args.output, result)
    except OSError as error:
        result = {'status': 'error', 'feasible': False, 'error': 'Could not write result: ' + str(error)}
    print(json.dumps({k: result[k] for k in ('status', 'feasible', 'error')}, allow_nan=False))
    return {'feasible': 0, 'unknown': 2, 'error': 3, 'cancelled': 130}[result['status']]


if __name__ == '__main__':
    raise SystemExit(main())
