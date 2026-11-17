import json
import pathlib
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest import mock

import parkour_solver.numerics.solver as solver
from tests.support.numerical_model import problem


class ProcessTests(unittest.TestCase):
    def run_worker(self, code, request=None, **options):
        with mock.patch.object(solver, '_command', return_value=[sys.executable, '-u', '-c', code]):
            return solver.solve(request or problem(), **options)

    def test_timeout_stops_worker_that_never_reads_large_request(self):
        request = problem(4000)
        request['walls'] = [{'mode': 'F', 't1': 0, 'cmp': 'EQ', 'rhs': 90}]
        start = time.monotonic()
        result = self.run_worker('import time; time.sleep(30)', request, seconds=0.1)
        self.assertLess(time.monotonic() - start, 2)
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(result['termination'], 'time_limit')
        self.assertIsNone(result['candidate'])

    def test_active_cancellation_discards_candidate(self):
        event = threading.Event()
        timer = threading.Timer(0.15, event.set)
        timer.start()
        try:
            result = self.run_worker('import time; print(\'{"type":"candidate","yaw":[0,0]}\',flush=True); time.sleep(30)',
                                     seconds=5, cancel_event=event)
        finally:
            timer.cancel()
        self.assertEqual(result['status'], 'cancelled')
        self.assertIsNone(result['candidate'])

    def test_timeout_retains_validated_incumbent(self):
        result = self.run_worker('import time; print(\'{"type":"candidate","yaw":[0,0]}\',flush=True); time.sleep(30)',
                                 seconds=0.2)
        self.assertEqual(result['status'], 'feasible')
        self.assertEqual(result['termination'], 'time_limit')
        self.assertTrue(result['candidate']['feasible'])
        self.assertIsNotNone(result['firstFeasibleSeconds'])

    def test_worker_success_does_not_override_failed_facing_constraint(self):
        request = problem()
        request['walls'] = [{'mode': 'F', 't1': 0, 'cmp': 'EQ', 'rhs': 90}]
        result = self.run_worker('print(\'{"type":"candidate","yaw":[0,0]}\'); print(\'{"type":"done","status":"Solve_Succeeded"}\')', request)
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(result['workerStatus'], 'Solve_Succeeded')
        self.assertEqual(result['diagnostics']['rejectedCandidates'], 1)

    def test_malformed_output_and_missing_runtime_are_errors(self):
        request = problem()
        request['walls'] = [{'mode': 'F', 't1': 0, 'cmp': 'EQ', 'rhs': 90}]
        result = self.run_worker('print("broken")', request)
        self.assertEqual(result['status'], 'error')
        self.assertEqual(result['termination'], 'protocol_error')
        with tempfile.TemporaryDirectory() as directory:
            result = solver.solve(request, python_executable=str(pathlib.Path(directory) / 'missing'))
        self.assertEqual(result['status'], 'error')
        self.assertEqual(result['termination'], 'runtime_error')

    def test_first_feasible_stop_and_callback(self):
        seen = []
        request = problem()
        request.update(velocity=[0, 0], magnitude=[0.1, 0.1], base=[0, 0], yaw=[180, 180],
                       walls=[{'mode': 'X', 't1': 2, 'cmp': 'GE', 'rhs': 0.1}])
        result = self.run_worker('import time; print(\'{"type":"candidate","yaw":[0,0]}\',flush=True); time.sleep(30)',
                                 request, stop_on_feasible=True, on_incumbent=seen.append)
        self.assertEqual(result['termination'], 'first_feasible')
        self.assertEqual(len(seen), 1)
        seen[0]['yaw'][0] = 90
        self.assertEqual(result['candidate']['yaw'][0], 0)

    def test_valid_seed_survives_timeout_without_worker_candidates(self):
        seen = []
        result = self.run_worker('import time; time.sleep(30)', seconds=0.2, on_incumbent=seen.append)
        self.assertEqual(result['status'], 'feasible')
        self.assertEqual(result['termination'], 'time_limit')
        self.assertEqual(result['candidate']['yaw'], [0, 0])
        self.assertEqual(len(seen), 1)

    def test_valid_seed_satisfies_feasibility_without_starting_worker(self):
        with mock.patch.object(solver.subprocess, 'Popen') as start:
            result = solver.solve(problem(), stop_on_feasible=True)
        start.assert_not_called()
        self.assertEqual(result['termination'], 'first_feasible')
        self.assertTrue(result['feasible'])

    def test_valid_seed_survives_runtime_error_and_weaker_worker_candidate(self):
        request = problem()
        request.update(velocity=[0, 0], magnitude=[0.1, 0.1], base=[0, 0])
        result = self.run_worker('print(\'{"type":"candidate","yaw":[180,180]}\')', request)
        self.assertAlmostEqual(result['candidate']['objective'], 0.25)
        with tempfile.TemporaryDirectory() as directory:
            result = solver.solve(request, python_executable=str(pathlib.Path(directory) / 'missing'))
        self.assertTrue(result['feasible'])
        self.assertEqual(result['termination'], 'runtime_error')
        self.assertIsNotNone(result['error'])

    def test_cli_writes_structured_error_atomically(self):
        with tempfile.TemporaryDirectory() as directory:
            source = pathlib.Path(directory) / 'bad.json'
            target = pathlib.Path(directory) / 'result.json'
            source.write_text('{}')
            command = [sys.executable, '-m', 'parkour_solver.cli.numerical', '--input', str(source), '--output', str(target)]
            completed = subprocess.run(command, capture_output=True, text=True, timeout=5)
            self.assertEqual(completed.returncode, 3)
            self.assertEqual(json.loads(target.read_text())['status'], 'error')
            self.assertEqual(sorted(p.name for p in pathlib.Path(directory).iterdir()), ['bad.json', 'result.json'])
            command[-1] = str(source / 'unwritable.json')
            completed = subprocess.run(command, capture_output=True, text=True, timeout=5)
            self.assertEqual(completed.returncode, 3)
            self.assertIn('Could not write result', json.loads(completed.stdout)['error'])


if __name__ == '__main__':
    unittest.main()
