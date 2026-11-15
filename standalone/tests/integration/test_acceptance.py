import unittest
from unittest.mock import patch

from parkour_solver.scene.route import HALF_WIDTH
from parkour_solver.search.ordered_route import find_route


class NoMovementCompiler:
    def compile(self, route, plan, seconds):
        return {'magnitude': [0.0] * plan['ticks'], 'friction': [.91] * plan['ticks'],
                'base': [0.0] * plan['ticks']}


class AcceptanceTests(unittest.TestCase):
    def test_numerical_row_tolerance_cannot_make_a_nonoverlapping_landing_valid(self):
        route = {'version': 1,
                 'initial': {'position': [.5, 1, .5], 'velocity': [0, 0, 0], 'yaw': 0},
                 'blocks': [{'bounds': [0, 1, 0, 1], 'top': 1},
                            {'bounds': [.5 + HALF_WIDTH + 5e-8, 2, 0, 1], 'top': 1}],
                 'airtimes': [12], 'runBounds': [[0, 0]], 'maxTotalRunTicks': 0,
                 'supportClearance': 0}
        with patch('parkour_solver.search.ordered_route.solve', return_value={
                'status': 'feasible', 'feasible': True, 'candidate': {'yaw': [0.0] * 12}}):
            result = find_route(route, NoMovementCompiler(), retries=0)
        self.assertEqual(result['status'], 'unknown')
        self.assertIsNone(result['best'])
        self.assertTrue(result['attempts'][0]['diagnostics'][0]['supportFailures'])

    def test_compiler_failure_is_reported_without_searching_more_ticks(self):
        class BadCompiler:
            def compile(self, *args):
                raise RuntimeError('test compiler failed')

        route = {'version': 1,
                 'initial': {'position': [.5, 1, .5], 'velocity': [0, 0, 0]},
                 'blocks': [{'bounds': [0, 1, 0, 1], 'top': 1}, {'bounds': [2, 3, 0, 1], 'top': 1}],
                 'airtimes': [12]}
        result = find_route(route, BadCompiler())
        self.assertEqual(result['status'], 'error')
        self.assertEqual(len(result['attempts']), 1)
        self.assertIn('test compiler failed', result['attempts'][0]['error'])


if __name__ == '__main__':
    unittest.main()
