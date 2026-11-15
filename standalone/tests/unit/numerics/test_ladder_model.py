import copy
import unittest

from parkour_solver.physics.vertical import CAP, CLIMB_CARRY, REST_CARRY, Y_DRAG
from parkour_solver.numerics.model import prepare
from parkour_solver.validation.numerical import validate
from tests.support.numerical_model import problem


def vertical(ticks):
    return {'profile': 'minecraft-1.8.9', 'position': 0.0, 'velocity': 0.1,
            'onGround': False, 'previousHorizontalCollision': False, 'threshold': 0.005,
            'jumpVelocity': [None] * ticks, 'sneak': [False] * ticks, 'contacts': []}


class LadderModelTests(unittest.TestCase):
    def test_entry_is_uncapped_and_climb_is_carried_to_next_movement(self):
        request = problem()
        request.update(position=[0, 2.01], velocity=[0.3, -0.8], friction=[0.91, 0.91],
                       ladderTicks=[False, True, True], vertical=vertical(2),
                       contacts=[{'state': 1, 'axis': 1, 'plane': 1.425, 'direction': -1}])
        result = validate(prepare(request), [0, 0])
        self.assertTrue(result['feasible'])
        self.assertAlmostEqual(result['positions'][1][0], 0.3)
        self.assertAlmostEqual(result['positions'][2][0] - result['positions'][1][0], CAP)
        self.assertAlmostEqual(result['y'][1], 0.1)
        self.assertEqual(result['vy'][1], CLIMB_CARRY)
        self.assertAlmostEqual(result['y'][2], 0.1 + CLIMB_CARRY)
        self.assertAlmostEqual(result['vy'][2], (CLIMB_CARRY - 0.08) * Y_DRAG)
        self.assertEqual(result['horizontalCollision'], [False, True, False])

    def test_missing_wall_does_not_grant_climb_carry(self):
        request = problem(1)
        request.update(velocity=[0.1, 0], ladderTicks=[False, True], vertical=vertical(1),
                       contacts=[{'state': 1, 'axis': 0, 'plane': 0.2, 'direction': 1}])
        result = validate(prepare(request), [0])
        self.assertFalse(result['feasible'])
        self.assertEqual(result['horizontalCollision'], [False, False])
        self.assertAlmostEqual(result['vy'][1], (0.1 - 0.08) * Y_DRAG)

    def test_cap_precedes_wall_reach_and_cuts_stored_velocity(self):
        request = problem(1)
        request.update(velocity=[0.4, -0.4], ladderTicks=[True, True], friction=[0.5])
        result = validate(prepare(request), [0])
        self.assertEqual(result['positions'][1], [CAP, -CAP])
        self.assertEqual(result['velocity'][1], [CAP * 0.5, -CAP * 0.5])
        request['contacts'] = [{'state': 1, 'axis': 0, 'plane': 0.2, 'direction': 1}]
        result = validate(prepare(request), [0])
        self.assertFalse(result['feasible'])
        self.assertEqual(len(result['missedContacts']), 1)

    def test_cap_is_after_inertia_and_acceleration(self):
        request = problem(1)
        request.update(velocity=[0.004, 0], threshold=0.005, magnitude=[0.3], base=[0],
                       ladderTicks=[True, False])
        result = validate(prepare(request), [0])
        self.assertEqual(result['positions'][1][0], CAP)
        self.assertEqual(result['ladderPattern'][0][0], 1)
        result = validate(prepare(request), [180])
        self.assertEqual(result['positions'][1][0], -CAP)

    def test_explicit_cap_branches_and_continuous_boundary(self):
        request = problem(1)
        request.update(velocity=[CAP, 0], ladderTicks=[True, True])
        for pattern in ([[0, 0]], [[1, 0]]):
            request['ladderPattern'] = pattern
            self.assertTrue(validate(prepare(request), [0])['feasible'])
        request['ladderPattern'] = [[-1, 0]]
        self.assertFalse(validate(prepare(request), [0])['feasible'])

    def test_sneak_floor_jump_and_ceiling_order(self):
        request = problem(1)
        request.update(ladderTicks=[True, True], vertical=vertical(1))
        request['vertical'].update(velocity=-0.5, sneak=[True])
        result = validate(prepare(request), [0])
        self.assertEqual(result['y'], [0, 0])
        self.assertEqual(result['vy'][1], REST_CARRY)
        request['vertical']['sneak'] = [False]
        self.assertAlmostEqual(validate(prepare(request), [0])['y'][1], -0.15)
        request['vertical'].update(velocity=-0.1, jumpVelocity=[0.42], onGround=True,
                                   contacts=[{'state': 1, 'plane': 0.2, 'direction': 1}])
        result = validate(prepare(request), [0])
        self.assertTrue(result['feasible'])
        self.assertEqual(result['y'][1], 0.2)
        self.assertEqual(result['vy'][1], REST_CARRY)

    def test_prescribed_step_and_reference_are_checked(self):
        request = problem(1)
        request['vertical'] = vertical(1)
        request['vertical'].update(velocity=REST_CARRY, onGround=True,
                                   contacts=[{'state': 1, 'plane': 0.5, 'direction': -1, 'step': True}],
                                   referencePosition=[0, 0.5], referenceVelocity=[REST_CARRY, REST_CARRY])
        self.assertTrue(validate(prepare(request), [0])['feasible'])
        request['vertical']['contacts'][0]['plane'] = 0.7
        self.assertFalse(validate(prepare(request), [0])['feasible'])
        request['vertical'].update(jumpVelocity=[0.42], referencePosition=[0, 0.5])
        request['vertical']['contacts'][0]['plane'] = 0.5
        result = validate(prepare(request), [0])
        self.assertTrue(result['feasible'])
        self.assertFalse(result['onGround'][1])

    def test_resume_preserves_climb_carry_and_previous_collision(self):
        request = problem(3)
        request.update(velocity=[0.3, 0], ladderTicks=[False, True, True, False], vertical=vertical(3),
                       contacts=[{'state': 1, 'axis': 0, 'plane': 0.2, 'direction': 1}])
        full = validate(prepare(request), [0, 0, 0])
        sliced = problem(2)
        sliced.update(position=full['positions'][1], velocity=full['velocity'][1],
                      ladderTicks=request['ladderTicks'][1:], vertical=vertical(2))
        sliced['vertical'].update(position=full['y'][1], velocity=full['vy'][1],
                                  previousHorizontalCollision=full['horizontalCollision'][1])
        tail = validate(prepare(sliced), [0, 0])
        self.assertEqual(tail['y'], full['y'][1:])
        self.assertEqual(tail['vy'], full['vy'][1:])
        self.assertEqual(tail['horizontalCollision'], full['horizontalCollision'][1:])

    def test_malformed_ladder_and_vertical_fields_are_rejected(self):
        original = problem()
        for field, value in [('ladderTicks', [False, True]), ('ladderTicks', [False, 1, False]),
                             ('ladderPattern', [[0, 0], [0, 0]]), ('vertical', {})]:
            request = copy.deepcopy(original)
            request[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                prepare(request)


if __name__ == '__main__':
    unittest.main()
