import random
import unittest

from parkour_solver.reachability.screen import contradiction
from parkour_solver.numerics import prepare, validate


class ScreenTests(unittest.TestCase):
    def test_unreachable_wall_is_rejected(self):
        p = {'ticks': 1, 'position': [0, 0], 'velocity': [0, 0], 'magnitude': [.1],
             'friction': [.91], 'walls': [{'mode': 'X', 't1': 1, 'cmp': 'GE', 'rhs': .2}]}
        self.assertEqual(contradiction(p)['state'], 1)

    def test_per_axis_zeroing_and_turns_do_not_reject_a_known_witness(self):
        rng = random.Random(163)
        for _ in range(150):
            n = rng.randint(2, 25)
            p = prepare({'version': 1, 'ticks': n, 'position': [rng.uniform(-100, 100) for _ in range(2)],
                         'velocity': [rng.uniform(-.2, .2) for _ in range(2)],
                         'magnitude': [rng.uniform(.001, .33) for _ in range(n)],
                         'friction': [rng.uniform(.5, .95) for _ in range(n)],
                         'base': [rng.uniform(-3, 3) for _ in range(n)],
                         'yaw': [rng.uniform(-180, 180) for _ in range(n)], 'threshold': .005,
                         'walls': [], 'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}})
            witness = validate(p, p['yaw'])
            for t, point in enumerate(witness['positions']):
                for axis, coordinate in zip(('X', 'Z'), point):
                    p['walls'].extend([{'mode': axis, 't1': t, 'cmp': 'GE', 'rhs': coordinate - 1e-5},
                                       {'mode': axis, 't1': t, 'cmp': 'LE', 'rhs': coordinate + 1e-5}])
            self.assertIsNone(contradiction(p))

    def test_ladder_cap_cannot_claim_reaching_a_further_contact(self):
        p = {'ticks': 1, 'position': [0, 0], 'velocity': [.8, 0], 'magnitude': [.03],
             'friction': [.91], 'walls': [], 'ladderTicks': [True, True],
             'contacts': [{'state': 1, 'axis': 0, 'plane': .2, 'direction': 1}]}
        self.assertEqual(contradiction(p)['reason'], 'contact_reach_contradiction')
        p['ladderTicks'][0] = False
        self.assertIsNone(contradiction(p))

    def test_contact_cap_screen_keeps_random_valid_witnesses(self):
        rng = random.Random(75124)
        for _ in range(300):
            n = rng.randint(2, 20)
            p = prepare({'version': 1, 'ticks': n, 'position': [rng.uniform(-10, 10) for _ in range(2)],
                         'velocity': [rng.uniform(-.3, .3) for _ in range(2)], 'threshold': .005,
                         'magnitude': [rng.uniform(.005, .33) for _ in range(n)],
                         'friction': [rng.uniform(.5, .95) for _ in range(n)], 'base': [0] * n,
                         'yaw': [rng.uniform(-180, 180) for _ in range(n)],
                         'ladderTicks': [bool(rng.randrange(2)) for _ in range(n + 1)],
                         'walls': [], 'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}})
            for t in range(n):
                candidate = validate(p, p['yaw'])
                for axis in range(2):
                    delta = candidate['positions'][t + 1][axis] - candidate['positions'][t][axis]
                    if abs(delta) > 1e-5 and rng.random() < .3:
                        p['contacts'].append({'state': t + 1, 'axis': axis,
                            'plane': candidate['positions'][t][axis] + delta / 2, 'direction': 1 if delta > 0 else -1})
            witness = validate(p, p['yaw'])
            self.assertTrue(witness['feasible'])
            for t, point in enumerate(witness['positions']):
                for axis, value in zip(('X', 'Z'), point):
                    p['walls'].extend([{'mode': axis, 't1': t, 'cmp': 'GE', 'rhs': value - 1e-5},
                                       {'mode': axis, 't1': t, 'cmp': 'LE', 'rhs': value + 1e-5}])
            self.assertIsNone(contradiction(p))


if __name__ == '__main__':
    unittest.main()
