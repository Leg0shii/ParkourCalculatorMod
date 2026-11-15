import unittest

from parkour_solver.numerics.continuation import Incumbent, run
from parkour_solver.numerics.model import prepare
from tests.unit.numerics.test_model import problem


class ContinuationTests(unittest.TestCase):
    def request(self):
        request = problem()
        request.update(velocity=[0, 0], magnitude=[0.1, 0.1], base=[0, 0],
                       threshold=0.005, yaw=[180, 180])
        return prepare(request)

    def test_failed_phase_cannot_displace_valid_path_for_polishing(self):
        request = self.request()
        request['walls'] = [{'mode': 'X', 't1': 2, 'cmp': 'GE', 'rhs': 0.1}]
        calls, events = [], []

        def phase(problem, yaw, pattern, seconds, stop, consider, kind, seed):
            calls.append((kind, yaw, pattern))
            if kind == 'zero_events':
                self.assertTrue(consider([0, 0]))
                return [180, 180], 'Maximum_WallTime_Exceeded'
            if kind == 'incumbent_polish':
                self.assertEqual(yaw, [0, 0])
                self.assertEqual(pattern, [[0, 0], [1, 0]])
                return yaw, 'Solve_Succeeded'
            return yaw, 'Solve_Succeeded'

        status = run(request, phase, lambda yaw: [[0, 0], [-1, 0]], events.append)
        self.assertEqual(status, 'Solve_Succeeded')
        self.assertEqual([c[0] for c in calls], ['carry', 'zero_events', 'incumbent_polish'])
        self.assertEqual(events[-1]['yaw'], [0, 0])

    def test_fallback_is_explored_after_successful_incumbent_polish(self):
        request = self.request()
        request.update(yaw=[0, 0], fallbackYaw=[90, 90])
        calls, events = [], []

        def phase(problem, yaw, pattern, seconds, stop, consider, kind, seed):
            calls.append((kind, seed, seconds))
            return yaw, 'Solve_Succeeded'

        run(request, phase, lambda yaw: [[0, 0], [0, 1]], events.append)
        self.assertEqual([(c[0], c[1]) for c in calls],
                         [('incumbent_polish', 'initial'), ('carry', 'fallback'),
                          ('zero_events', 'fallback')])
        self.assertLessEqual(calls[0][2], request['seconds'] * 0.4)
        self.assertEqual(events[-1]['yaw'], [0, 0])

    def test_incumbent_tracks_minimization_and_rejects_invalid_paths(self):
        request = self.request()
        request['objective']['sense'] = 'MIN'
        request['walls'] = [{'mode': 'F', 't1': 0, 'cmp': 'LE', 'rhs': 170}]
        events = []
        incumbent = Incumbent(request, events.append)
        self.assertTrue(incumbent.consider([0, 0]))
        self.assertFalse(incumbent.consider([180, 180]))
        self.assertTrue(incumbent.consider([90, 90]))
        self.assertTrue(incumbent.consider([0, 0]))
        self.assertEqual(incumbent.best['yaw'], [90, 90])
        self.assertEqual(len(events), 2)

    def test_zero_threshold_uses_fallback_without_inertia_inference(self):
        request = self.request()
        request.update(threshold=0, fallbackYaw=[90, 90], seconds=1, stopOnFeasible=False)
        calls, events = [], []
        now = [0.]

        def phase(problem, yaw, pattern, seconds, stop, consider, kind, seed):
            calls.append((seed, seconds, pattern))
            now[0] += seconds
            if seed == 'fallback':
                consider([0, 0])
            return yaw, 'Solve_Succeeded'

        run(request, phase, lambda yaw: self.fail('Threshold inference in carry-only mode'),
            events.append, clock=lambda: now[0])
        self.assertEqual([c[0] for c in calls], ['initial', 'fallback'])
        self.assertAlmostEqual(sum(c[1] for c in calls), 1.)
        self.assertTrue(all(c[2] == [[2, 2], [2, 2]] for c in calls))
        self.assertEqual(events[-1]['yaw'], [0, 0])

    def test_zero_threshold_failed_fallback_preserves_valid_incumbent(self):
        request = self.request()
        request.update(threshold=0, yaw=[0, 0], fallbackYaw=[90, 90])
        request['walls'] = [{'mode': 'X', 't1': 2, 'cmp': 'GE', 'rhs': .1}]
        events = []

        def phase(*args):
            return [180, 180], 'Maximum_WallTime_Exceeded'

        run(request, phase, lambda yaw: self.fail('Threshold inference in carry-only mode'), events.append)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]['yaw'], [0, 0])

    def test_explicit_pattern_is_preserved(self):
        request = self.request()
        request['inertiaPattern'] = [[0, 0], [-1, 0]]
        calls = []

        def phase(problem, yaw, pattern, seconds, stop, consider, kind, seed):
            calls.append(pattern)
            return yaw, 'Solve_Succeeded'

        run(request, phase, lambda yaw: self.fail('Explicit pattern was reinferred'), lambda event: None)
        self.assertEqual(calls, [request['inertiaPattern']])


if __name__ == '__main__':
    unittest.main()
