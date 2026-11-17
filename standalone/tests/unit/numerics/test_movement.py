import copy
import unittest

from parkour_solver.numerics.model import prepare
from parkour_solver.validation.numerical import validate
from tests.support.numerical_model import problem


def movement(ticks):
    return {'profile': 'minecraft-1.8.9', 'compiler': 'mod-input-state-v1',
            'horizontalCollision': [False] * (ticks + 1), 'ground': [False] * ticks,
            'sprinting': [False] * ticks, 'airSprinting': [False] * ticks,
            'moveForward': [0.0] * ticks, 'moveStrafe': [0.0] * ticks}


class MovementTests(unittest.TestCase):
    def test_boundary_touch_cannot_claim_the_compiled_wall_hit(self):
        request = problem(1)
        request.update(velocity=[0.1, 0], movement=movement(1),
                       contacts=[{'state': 1, 'axis': 0, 'plane': 0.1, 'direction': 1}])
        request['movement']['horizontalCollision'][1] = True
        result = validate(prepare(request), [0])
        self.assertFalse(result['feasible'])
        self.assertEqual(result['missedContacts'], [])
        self.assertEqual(result['movementMismatches'], [{'state': 1, 'reason': 'horizontal_collision_schedule'}])
        request['contacts'][0]['plane'] = 0.05
        result = validate(prepare(request), [0])
        self.assertTrue(result['feasible'])
        self.assertEqual(result['movementMismatches'], [])

    def test_ground_changes_cannot_silently_keep_old_acceleration(self):
        request = problem(2)
        request['movement'] = movement(2)
        request['movement']['ground'] = [True, True]
        request['vertical'] = {'profile': 'minecraft-1.8.9', 'position': 1.0, 'velocity': 0,
                               'onGround': True, 'previousHorizontalCollision': False,
                               'sneak': [False, False], 'jumpVelocity': [None, None]}
        result = validate(prepare(request), [0, 0])
        self.assertFalse(result['feasible'])
        self.assertIn({'state': 1, 'reason': 'ground_schedule'}, result['movementMismatches'])

    def test_collision_assumptions_must_match_declared_schedule(self):
        request = problem(2)
        request['movement'] = movement(2)
        request['movement']['horizontalCollision'][1] = True
        with self.assertRaisesRegex(ValueError, 'collision schedule'):
            prepare(request)

    def test_malformed_movement_metadata_is_rejected(self):
        original = problem(2)
        original['movement'] = movement(2)
        for key, value in [('profile', 'minecraft-1.12.2'), ('compiler', 'invented'),
                           ('ground', [False]), ('sprinting', [False, 0]),
                           ('moveForward', [0, float('nan')]), ('horizontalCollision', [])]:
            request = copy.deepcopy(original)
            request['movement'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                prepare(request)


if __name__ == '__main__':
    unittest.main()
