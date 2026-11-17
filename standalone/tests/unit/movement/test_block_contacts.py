import copy
import unittest

from parkour_solver.constraints.contacts import avoid_contact_rows, contact_conflicts, floor_checks
from parkour_solver.movement.profiles import ordinary_jump_profile, runoff_profile, stair_jump_profile
from parkour_solver.search.support_profiles import compile_block_path
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.scene.route import schedule


def descending_scene():
    return {'version': 1, 'sceneModel': 'support_planes',
            'initial': {'position': [.5, 2, .5], 'velocity': [0, -.0784000015258789, 0]},
            'startBlock': 'start', 'goalBlock': 'goal',
            'blocks': [{'id': 'start', 'bounds': [0, 1, 0, 1], 'top': 2},
                       {'id': 'goal', 'bounds': [2, 3, 0, 1], 'top': 1}]}


class FloorContactTests(unittest.TestCase):
    def test_runoff_checks_the_departure_floor_on_its_first_input(self):
        scene = prepare_scene(descending_scene())
        profile = runoff_profile(2, 1)
        profile['profileKind'] = 'run_off'
        checks = floor_checks(scene, ['start', 'goal'], [profile])
        self.assertIn(('start', 0), [(c['block']['id'], c['offset']) for c in checks if c['reason'] == 'earlier_floor'])

    def test_no_jump_first_movement_uses_its_own_step_eligibility(self):
        scene = prepare_scene(descending_scene())
        scene['blocks'].append({'id': 'low_step', 'bounds': [3, 4, 0, 1], 'top': 2.25})
        fall = floor_checks(scene, ['start', 'goal'], [runoff_profile(2, 1)])
        jump = floor_checks(scene, ['start', 'goal'], [ordinary_jump_profile(2, 1)])
        self.assertTrue(any(c['block']['id'] == 'low_step' and c['reason'] == 'unplanned_stair' for c in fall))
        self.assertFalse(any(c['block']['id'] == 'low_step' and c['reason'] == 'unplanned_stair' for c in jump))

    def test_ordinary_profile_must_avoid_an_unplanned_first_movement_stair_cancel(self):
        scene = descending_scene()
        scene['blocks'][1]['top'] = 2.5
        scene = prepare_scene(scene)
        ordinary = ordinary_jump_profile(2, 2.5)
        checks = floor_checks(scene, ['start', 'goal'], [ordinary])
        self.assertEqual([(c['reason'], c['offset']) for c in checks], [('unplanned_stair', 1)])
        scene['stairBoost'] = False
        self.assertEqual(floor_checks(scene, ['start', 'goal'], [ordinary]), checks)
        stair = stair_jump_profile(2, 2.5)
        stair['profileKind'] = 'stair'
        self.assertEqual(floor_checks(scene, ['start', 'goal'], [stair]), [])

    def test_descending_profile_must_miss_the_departure_floor_before_the_lower_target(self):
        scene = prepare_scene(descending_scene())
        compiled = compile_block_path(scene, ['start', 'goal'])
        checks = floor_checks(scene, ['start', 'goal'], compiled['profiles'])
        self.assertEqual([(c['block']['id'], c['offset']) for c in checks], [('start', 11)])
        plan = schedule(compiled['route'], [2])
        positions = [[10, 10] for _ in range(plan['ticks'] + 1)]
        positions[13] = [.5, .5]
        conflicts = contact_conflicts(checks, plan, {'positions': positions})
        self.assertEqual(len(conflicts), 1)
        self.assertEqual(conflicts[0]['state'], 13)
        rows = avoid_contact_rows(conflicts[0], 1e-6)
        self.assertEqual({(r['mode'], r['cmp']) for r in rows}, {('X', 'LE'), ('X', 'GE'), ('Z', 'LE'), ('Z', 'GE')})
        self.assertTrue(all(r['leg'] == 0 and r['offset'] == 11 for r in rows))
        positions[13] = [2, .5]
        self.assertEqual(contact_conflicts(checks, plan, {'positions': positions}), [])

    def test_higher_intermediate_floor_is_checked_even_if_it_is_not_in_the_route(self):
        scene = prepare_scene(descending_scene())
        scene['blocks'].append({'id': 'unselected', 'bounds': [5, 6, 0, 1], 'top': 2.5})
        profile = ordinary_jump_profile(2, 1)
        checks = floor_checks(scene, ['start', 'goal'], [profile])
        self.assertIn('unselected', [c['block']['id'] for c in checks])
        self.assertNotIn('goal', [c['block']['id'] for c in checks])

    def test_equal_height_landing_does_not_forbid_the_selected_floor(self):
        scene = descending_scene()
        scene['blocks'][1]['top'] = 2
        scene = prepare_scene(scene)
        original = copy.deepcopy(scene)
        self.assertEqual(floor_checks(scene, ['start', 'goal'], [ordinary_jump_profile(2, 2)]), [])
        self.assertEqual(scene, original)


if __name__ == '__main__':
    unittest.main()
