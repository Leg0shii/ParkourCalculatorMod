import json
import os
from pathlib import Path
import unittest

from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.scene.route import HALF_WIDTH


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER'), 'Set PKC_RUN_TICKS_COMPILER')
class SharedContactTests(unittest.TestCase):
    def setUp(self):
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_hit_cancels_sprint_with_carried_air_factor(self):
        route = {'sharedMovement': True, 'inputs': {'forward': .98, 'strafe': 0, 'sprint': True,
                                                  'incomingSprint': True, 'force45': True}}
        plan = {'grounded': [False] * 5, 'jump': [False] * 5,
                'sceneContacts': [{'state': 1, 'axis': 0}]}
        compiled = self.compiler.compile(route, plan, 5)
        self.assertEqual(compiled['movement']['sprinting'], [True, False, True, True, True])
        self.assertEqual(compiled['movement']['airSprinting'], [True, True, False, True, True])
        self.assertLess(compiled['magnitude'][2], compiled['magnitude'][1])
        plan['sceneContacts'] = []
        avoided = self.compiler.compile(route, plan, 5)
        self.assertTrue(all(avoided['movement']['sprinting']))

    def test_raw_wall_clip_and_outward_following_movement(self):
        box = [1, 0, -1, 2, 5, 1]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-5, -5, -5, 5, 10, 5],
                 'solids': [box]}
        plane = 1 - HALF_WIDTH
        positions = [[.5, 2, 0], [plane, 2, 0], [plane - .1, 2, 0]]
        movement = [[.4, 0, 0], [-.1, 0, 0]]
        contacts = [{'state': 1, 'axis': 0, 'plane': plane, 'direction': 1, 'box': box}]
        checked = self.compiler.check_scene(scene, positions, movement, [False] * 3, 5, contacts=contacts)
        self.assertTrue(checked['feasible'], checked)
        self.assertFalse(self.compiler.check_scene(scene, positions, movement, [False] * 3, 5)['feasible'])
        touch = [[plane - .5, 0, 0], [-.1, 0, 0]]
        self.assertFalse(self.compiler.check_scene(scene, positions, touch, [False] * 3, 5, contacts=contacts)['feasible'])
        late = [dict(contacts[0], state=2)]
        self.assertFalse(self.compiler.check_scene(scene, positions, movement, [False] * 3, 5, contacts=late)['feasible'])

    def test_finite_face_and_nearer_wall_reject_selected_hit(self):
        box = [1, 0, -1, 2, 5, 1]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-5, -5, -5, 5, 10, 5],
                 'solids': [box]}
        plane = 1 - HALF_WIDTH
        contact = [{'state': 1, 'axis': 0, 'plane': plane, 'direction': 1, 'box': box}]
        checked = self.compiler.check_scene(scene, [[.5, 2, 3], [plane, 2, 3]], [[.4, 0, 0]],
                                             [False] * 2, 5, contacts=contact)
        self.assertFalse(checked['feasible'])
        scene['solids'].insert(0, [.9, 0, -1, 1, 5, 1])
        checked = self.compiler.check_scene(scene, [[.5, 2, 0], [plane, 2, 0]], [[.4, 0, 0]],
                                             [False] * 2, 5, contacts=contact)
        self.assertFalse(checked['feasible'])

    def test_paired_contacts_use_new_x_for_z_and_repeated_hit_is_rejected(self):
        xwall, zwall = [1, 0, -1, 2, 5, 2], [.9, 0, 1, 2, 5, 2]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-5, -5, -5, 5, 10, 5],
                 'solids': [xwall, zwall]}
        plane = 1 - HALF_WIDTH
        contacts = [{'state': 1, 'axis': a, 'direction': 1, 'plane': plane, 'box': box}
                    for a, box in [(0, xwall), (2, zwall)]]
        positions = [[.5, 2, .5], [plane, 2, plane], [plane - .1, 2, plane - .1]]
        checked = self.compiler.check_scene(scene, positions, [[.4, 0, .4], [-.1, 0, -.1]],
                                             [False] * 3, 5, contacts=contacts)
        self.assertTrue(checked['feasible'], checked)
        positions[2] = positions[1][:]
        checked = self.compiler.check_scene(scene, positions, [[.4, 0, .4], [.1, 0, .1]],
                                             [False] * 3, 5, contacts=contacts)
        self.assertFalse(checked['feasible'])
