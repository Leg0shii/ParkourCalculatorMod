from tests.support.paths import AUTONOMY_DATA
import copy
import json
from pathlib import Path
import unittest

from parkour_solver.movement.profiles import compile_open_air_route, ordinary_jump_profile, runoff_profile, stair_jump_profile
from parkour_solver.physics.vertical import REST_CARRY
from parkour_solver.numerics.model import prepare, validate


FIXTURE = AUTONOMY_DATA / 'fixtures' / 'blocks-flat-two-jumps.json'


class BlockProfileTests(unittest.TestCase):
    def test_runoff_profiles_use_the_same_vertical_model_without_a_jump_impulse(self):
        for drop, expected in [(.0625, 1), (.125, 2), (.25, 3), (.5, 4), (1, 5)]:
            with self.subTest(drop=drop):
                profile = runoff_profile(2, 2 - drop)
                self.assertEqual(profile['airtime'], expected)
                self.assertEqual(profile['jump'], [False] * expected)
                self.assertEqual(profile['grounded'], [True] + [False] * (expected - 1) + [True])
                request = prepare({'version': 1, 'ticks': expected, 'position': [0, 0], 'velocity': [0, 0],
                                   'magnitude': [0] * expected, 'friction': [.91] * expected,
                                   'base': [0] * expected, 'yaw': [0] * expected,
                                   'objective': {'axis': 'X', 'sense': 'MAX', 'tick': expected},
                                   'vertical': {'profile': 'minecraft-1.8.9', 'position': 2, 'velocity': REST_CARRY,
                                                'onGround': True, 'previousHorizontalCollision': False,
                                                'sneak': [False] * expected, 'jumpVelocity': [None] * expected,
                                                'contacts': profile['contacts'],
                                                'referencePosition': profile['position'],
                                                'referenceVelocity': profile['velocity']}})
                result = validate(request, [0] * expected)
                self.assertTrue(result['feasible'], result)
                self.assertEqual(result['onGround'], profile['grounded'])
                self.assertLess(profile['airtime'], ordinary_jump_profile(2, 2 - drop)['airtime'])

    def test_runoff_cannot_rise_and_respects_its_simulation_horizon(self):
        self.assertIsNone(runoff_profile(1, 1))
        self.assertIsNone(runoff_profile(1, 1.5))
        self.assertIsNone(runoff_profile(1, 0, max_inputs=2))

    def test_half_block_stair_has_a_takeoff_and_one_airborne_input(self):
        profile = stair_jump_profile(1, 1.5)
        self.assertEqual(profile['airtime'], 2)
        self.assertEqual(profile['position'], [1, 1.5, 1.5])
        self.assertEqual(profile['grounded'], [True, False, True])
        self.assertTrue(profile['contacts'][0]['step'])
        request = prepare({'version': 1, 'ticks': 2, 'position': [0, 0], 'velocity': [0, 0],
                           'magnitude': [0, 0], 'friction': [.546, .91], 'base': [0, 0], 'yaw': [0, 0],
                           'objective': {'axis': 'X', 'sense': 'MAX', 'tick': 2},
                           'vertical': {'profile': 'minecraft-1.8.9', 'position': 1, 'velocity': REST_CARRY,
                                        'onGround': True, 'previousHorizontalCollision': False,
                                        'sneak': [False, False], 'jumpVelocity': [0.41999998688697815, None],
                                        'contacts': profile['contacts'], 'referencePosition': profile['position'],
                                        'referenceVelocity': profile['velocity']}})
        result = validate(request, [0, 0])
        self.assertTrue(result['feasible'], result)
        self.assertEqual(result['onGround'], profile['grounded'])

    def test_stair_profile_is_only_generated_when_the_existing_step_model_accepts_it(self):
        for height in [0, .25, .41999998688697815, .7, 1]:
            self.assertIsNone(stair_jump_profile(0, height))
        self.assertIsNotNone(stair_jump_profile(0, .6000000238418579))

    def test_ordinary_height_tiers_and_existing_vertical_acceptance(self):
        for delta, expected in [(0, 12), (.5, 10), (1, 9), (-1, 14)]:
            with self.subTest(delta=delta):
                profile = ordinary_jump_profile(5, 5 + delta)
                self.assertEqual(profile['airtime'], expected)
                self.assertEqual(profile['grounded'], [True] + [False] * (expected - 1) + [True])
                self.assertEqual(profile['position'][-1], 5 + delta)
                self.assertEqual(profile['velocity'][-1], REST_CARRY)
                problem = prepare({'version': 1, 'ticks': expected, 'position': [0, 0], 'velocity': [0, 0],
                                   'magnitude': [0] * expected, 'friction': [.91] * expected,
                                   'base': [0] * expected, 'yaw': [0] * expected,
                                   'objective': {'axis': 'X', 'sense': 'MAX', 'tick': expected},
                                   'vertical': {'profile': 'minecraft-1.8.9', 'position': 5,
                                                'velocity': REST_CARRY, 'onGround': True,
                                                'previousHorizontalCollision': False,
                                                'sneak': [False] * expected,
                                                'jumpVelocity': [0.41999998688697815] + [None] * (expected - 1),
                                                'contacts': profile['contacts'],
                                                'referencePosition': profile['position'],
                                                'referenceVelocity': profile['velocity']}})
                accepted = validate(problem, [0] * expected)
                self.assertTrue(accepted['feasible'], accepted)
                self.assertEqual(accepted['onGround'], profile['grounded'])

    def test_high_target_is_not_given_a_fabricated_profile(self):
        self.assertIsNone(ordinary_jump_profile(1, 3))
        request = json.loads(FIXTURE.read_text())
        request['blocks'][1]['top'] = 3
        result = compile_open_air_route(request)
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(result['leg'], 0)
        self.assertNotIn('route', result)

    def test_short_budget_is_unknown_and_can_be_extended(self):
        self.assertIsNone(ordinary_jump_profile(1, -9, max_inputs=12))
        self.assertIsNotNone(ordinary_jump_profile(1, -9))

    def test_geometry_and_heights_generate_schedule_without_capture(self):
        request = json.loads(FIXTURE.read_text())
        original = copy.deepcopy(request)
        request['blocks'][1]['top'] = 1.5
        result = compile_open_air_route(request)
        self.assertEqual(result['status'], 'derived')
        self.assertEqual(result['route']['airtimes'], [10, 13])
        self.assertEqual([p['sourceBlock'] for p in result['profiles']], ['start', 'middle'])
        self.assertEqual(request['initial'], original['initial'])
        self.assertNotIn('airtimes', request)
        result['route']['blocks'][0]['bounds'][0] = -100
        self.assertEqual(request['blocks'], [original['blocks'][0],
                                           dict(original['blocks'][1], top=1.5), original['blocks'][2]])

    def test_surroundings_and_contact_profiles_are_not_silently_ignored(self):
        for field, value in [('ceilings', []), ('obstacles', []), ('airtimes', [12, 12]),
                             ('kinds', ['jump', 'step']), ('contactEntries', [None, None])]:
            with self.subTest(field=field):
                request = json.loads(FIXTURE.read_text())
                request[field] = value
                with self.assertRaises(ValueError):
                    compile_open_air_route(request)
        request = json.loads(FIXTURE.read_text())
        del request['sceneModel']
        with self.assertRaises(ValueError):
            compile_open_air_route(request)

    def test_disconnected_support_union_is_not_accepted_as_one_support(self):
        request = json.loads(FIXTURE.read_text())
        request['blocks'][1]['boxes'] = [[3, 4, 0, 1], [5, 6, 0, 1]]
        with self.assertRaises(ValueError):
            compile_open_air_route(request)


if __name__ == '__main__':
    unittest.main()
