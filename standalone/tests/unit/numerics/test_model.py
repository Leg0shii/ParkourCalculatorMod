import copy
import math
import unittest

from parkour_solver.numerics.model import fingerprint, prepare, validate, wrap


def problem(ticks=2):
    return {'version': 1, 'ticks': ticks, 'position': [0.0, 0.0], 'velocity': [0.1, 0.2],
            'magnitude': [0.0] * ticks, 'friction': [0.5] * ticks, 'base': [math.pi / 2] * ticks,
            'yaw': [0.0] * ticks, 'threshold': 0.0, 'contacts': [], 'walls': [],
            'objective': {'axis': 'X', 'sense': 'MAX', 'tick': ticks}, 'seconds': 2.0}


class ModelTests(unittest.TestCase):
    def test_continuous_carry_and_inertia_are_distinct(self):
        request = problem()
        request['velocity'][0] = 0.004
        free = validate(prepare(request), [0, 0])
        request['threshold'] = 0.005
        zeroed = validate(prepare(request), [0, 0])
        self.assertAlmostEqual(free['positions'][2][0], 0.006)
        self.assertEqual(zeroed['positions'][2][0], 0)
        self.assertEqual(free['positions'][2][1], zeroed['positions'][2][1])

    def test_contact_resets_only_normal_velocity(self):
        request = problem()
        request['contacts'] = [{'state': 1, 'axis': 0, 'plane': 0.05, 'direction': 1}]
        result = validate(prepare(request), [0, 0])
        self.assertTrue(result['feasible'])
        self.assertEqual(result['positions'][1][0], 0.05)
        self.assertEqual(result['positions'][2][0], 0.05)
        self.assertEqual(result['velocity'][1][0], 0)
        self.assertAlmostEqual(result['positions'][2][1], 0.3)

    def test_subtolerance_missed_contact_never_resets_velocity(self):
        request = problem()
        request['contacts'] = [{'state': 1, 'axis': 0, 'plane': 0.100000001, 'direction': 1}]
        result = validate(prepare(request), [0, 0])
        self.assertFalse(result['feasible'])
        self.assertEqual(len(result['missedContacts']), 1)
        self.assertAlmostEqual(result['velocity'][1][0], 0.05)
        self.assertAlmostEqual(result['positions'][2][0], 0.15)

    def test_contact_from_wrong_side_is_rejected(self):
        request = problem()
        request['position'][0] = 0.2
        request['contacts'] = [{'state': 1, 'axis': 0, 'plane': 0.1, 'direction': 1}]
        result = validate(prepare(request), [0, 0])
        self.assertFalse(result['feasible'])
        self.assertEqual(len(result['wrongSideContacts']), 1)

    def test_fixed_pattern_is_checked_against_automatic_threshold(self):
        request = problem()
        request.update(threshold=0.005, velocity=[0.004, 0.2], inertiaPattern=[[0, 1], [0, 1]])
        self.assertTrue(validate(prepare(request), [0, 0])['feasible'])
        request['inertiaPattern'][0][0] = 1
        result = validate(prepare(request), [0, 0])
        self.assertFalse(result['feasible'])
        self.assertEqual(result['patternMismatches'][0]['tick'], 0)

    def test_facing_relative_and_cross_axis_rows(self):
        request = problem()
        request['walls'] = [
            {'mode': 'F', 't1': 0, 'cmp': 'EQ', 'rhs': 179},
            {'mode': 'F', 't1': 1, 't2': 0, 'op': 'MINUS', 'cmp': 'EQ', 'rhs': 2},
            {'mode': 'Z', 't1': 2, 't2': 0, 'op': 'MINUS', 'cmp': 'EQ', 'rhs': 0.3},
            {'mode': 'DXZ', 't1': 2, 't2': 0, 'cmp': 'LE', 'rhs': -0.1},
            {'mode': 'DZX', 't1': 2, 't2': 0, 'cmp': 'GE', 'rhs': 0.1}]
        self.assertTrue(validate(prepare(request), [179, -179])['feasible'])
        self.assertFalse(validate(prepare(request), [179, -178])['feasible'])
        self.assertEqual(wrap(-180), 180)

    def test_sum_of_world_positions_keeps_both_offsets(self):
        request = problem()
        request['position'][0] = 10
        request['walls'] = [{'mode': 'X', 't1': 2, 't2': 0, 'op': 'PLUS', 'cmp': 'EQ', 'rhs': 20.15}]
        self.assertTrue(validate(prepare(request), [0, 0])['feasible'])

    def test_custom_motion_objective_uses_displacement(self):
        request = problem()
        request['objective'].update(customYaw=-90, type='MOTION')
        result = validate(prepare(request), [0, 0])
        self.assertAlmostEqual(result['objective'], 0.05)

    def test_nonfinite_and_wrong_size_candidates_are_rejected(self):
        request = prepare(problem())
        for yaw in ([0], [0, float('nan')], [0, float('inf')], [0, True]):
            with self.subTest(yaw=yaw), self.assertRaises(ValueError):
                validate(request, yaw)

    def test_malformed_requests_fail_before_starting_worker(self):
        mutations = [('ticks', True), ('magnitude', [-1, 0]), ('friction', [1.1, 0.5]),
                     ('threshold', -0.1), ('walls', 'wrong'), ('contacts', [None]), ('seconds', float('inf')),
                     ('fallbackYaw', [0]), ('fallbackYaw', [0, float('nan')])]
        for key, value in mutations:
            request = problem()
            request[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                prepare(request)

    def test_prepare_copies_request_and_hash_excludes_execution_settings(self):
        original = problem()
        snapshot = copy.deepcopy(original)
        ready = prepare(original)
        self.assertEqual(original, snapshot)
        other = copy.deepcopy(ready)
        other.update(seconds=8, yaw=[20, 30], fallbackYaw=[10, 15], stopOnFeasible=True)
        self.assertEqual(fingerprint(ready), fingerprint(other))
        other['threshold'] = 0.005
        self.assertNotEqual(fingerprint(ready), fingerprint(other))


if __name__ == '__main__':
    unittest.main()
