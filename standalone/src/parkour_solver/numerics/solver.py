import collections
import json
import pathlib
import queue
import subprocess
import sys
import threading
import time

if __package__:
    from parkour_solver.numerics.model import TOLERANCE, fingerprint, prepare, validate
else:
    from parkour_solver.numerics.model import TOLERANCE, fingerprint, prepare, validate


def _command(python_executable):
    return [str(python_executable or sys.executable), '-u', '-m', 'parkour_solver.numerics.worker']


def solve(request, *, seconds=None, stop_on_feasible=None, python_executable=None,
          cancel_event=None, on_incumbent=None):
    overrides = dict(request)
    if seconds is not None:
        overrides['seconds'] = seconds
    if stop_on_feasible is not None:
        overrides['stopOnFeasible'] = stop_on_feasible
    problem = prepare(overrides)
    started = time.monotonic()
    deadline = started + problem['seconds']
    messages = queue.Queue(maxsize=64)
    stderr = collections.deque(maxlen=20)
    phases = []
    best = None
    first_feasible = None
    rejected = 0
    last_rejection = None
    termination = 'worker_finished'
    worker_status = None
    error = None
    cancelled = False
    process = None

    def accept(candidate):
        nonlocal best, first_feasible
        if not candidate['feasible']:
            return
        direction = 1 if problem['objective']['sense'] == 'MAX' else -1
        if best is None or direction * candidate['objective'] > direction * best['objective']:
            if first_feasible is None:
                first_feasible = time.monotonic() - started
            best = candidate
            if on_incumbent is not None:
                on_incumbent(json.loads(json.dumps(best)))

    def publish(kind, value):
        try:
            messages.put_nowait((kind, value))
        except queue.Full:
            try:
                messages.get_nowait()
            except queue.Empty:
                pass
            try:
                messages.put_nowait((kind, value))
            except queue.Full:
                pass

    def output_reader(stream):
        try:
            while True:
                line = stream.readline(2000001)
                if not line:
                    break
                if len(line) > 2000000:
                    publish('io_error', 'Oversized worker response')
                    break
                publish('message', line)
        except (OSError, ValueError) as failure:
            publish('io_error', str(failure))

    def error_reader(stream):
        try:
            for line in stream:
                stderr.append(line[-400:].rstrip())
        except (OSError, ValueError):
            pass

    def write_request():
        try:
            process.stdin.write(json.dumps(problem, allow_nan=False, separators=(',', ':')) + '\n')
            process.stdin.close()
        except (OSError, ValueError) as failure:
            publish('io_error', 'Could not write worker request: ' + str(failure))

    if cancel_event is None or not cancel_event.is_set():
        try:
            seed = validate(problem, problem['yaw'])
        except (ValueError, TypeError, OverflowError):
            seed = None
        if seed is not None:
            accept(seed)

    try:
        if cancel_event is not None and cancel_event.is_set():
            cancelled, termination = True, 'cancelled'
        elif best is not None and problem['stopOnFeasible']:
            termination = 'first_feasible'
        else:
            import os
            environment = os.environ.copy()
            environment['OPENBLAS_NUM_THREADS'] = '1'
            environment['OMP_NUM_THREADS'] = '1'
            process = subprocess.Popen(_command(python_executable), stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                       text=True, encoding='utf-8', errors='replace', env=environment)
            output = threading.Thread(target=output_reader, args=(process.stdout,), daemon=True)
            errors = threading.Thread(target=error_reader, args=(process.stderr,), daemon=True)
            writer = threading.Thread(target=write_request, daemon=True)
            output.start()
            errors.start()
            writer.start()
            while process.poll() is None or output.is_alive() or not messages.empty():
                if cancel_event is not None and cancel_event.is_set():
                    cancelled, termination = True, 'cancelled'
                    break
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    termination = 'time_limit'
                    break
                try:
                    kind, value = messages.get(timeout=min(0.025, remaining))
                except queue.Empty:
                    continue
                if kind == 'io_error':
                    error, termination = value, 'worker_error'
                    break
                try:
                    message = json.loads(value)
                    message_type = message['type']
                    if message_type == 'candidate':
                        try:
                            candidate = validate(problem, message['yaw'])
                        except (ValueError, TypeError, OverflowError) as invalid:
                            rejected += 1
                            last_rejection = str(invalid)
                            continue
                        if not candidate['feasible']:
                            rejected += 1
                            last_rejection = {k: v for k, v in candidate.items()
                                              if k not in ('yaw', 'positions', 'velocity', 'y', 'vy', 'onGround',
                                                           'horizontalCollision', 'ladderPattern')}
                            continue
                        accept(candidate)
                        if problem['stopOnFeasible']:
                            termination = 'first_feasible'
                            break
                    elif message_type == 'phase':
                        phases.append(message)
                    elif message_type == 'done':
                        worker_status = str(message['status'])
                    elif message_type == 'error':
                        error, termination = str(message['message']), 'worker_error'
                        break
                    else:
                        raise ValueError('Unknown worker response type')
                except (ValueError, KeyError, TypeError) as invalid:
                    error, termination = 'Invalid worker response: ' + str(invalid), 'protocol_error'
                    break
            errors.join(timeout=0.1)
            if process.poll() not in (None, 0) and termination == 'worker_finished':
                error, termination = 'Worker exited with code ' + str(process.returncode), 'worker_error'
    except OSError as failure:
        error, termination = str(failure), 'runtime_error'
    finally:
        if process is not None:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=0.2)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=1)
            for stream in (process.stdin, process.stdout, process.stderr):
                try:
                    stream.close()
                except (OSError, ValueError):
                    pass
    if cancelled:
        best = None
    status = 'cancelled' if cancelled else 'feasible' if best is not None else 'error' if error else 'unknown'
    return {'version': 1, 'status': status, 'feasible': best is not None, 'candidate': best,
            'objectiveMode': problem['objectiveMode'],
            'problemHash': fingerprint(problem), 'elapsedSeconds': time.monotonic() - started,
            'firstFeasibleSeconds': first_feasible,
            'problem': {'ticks': problem['ticks'], 'constraints': len(problem['walls']),
                        'contacts': len(problem['contacts']), 'threshold': problem['threshold'],
                        'objective': problem['objective'], 'source': problem.get('source')},
            'termination': termination, 'workerStatus': worker_status, 'workerLaunched': process is not None, 'error': error,
            'tolerance': TOLERANCE, 'phases': phases,
            'diagnostics': {'rejectedCandidates': rejected, 'lastRejection': last_rejection,
                            'stderr': list(stderr)}}
