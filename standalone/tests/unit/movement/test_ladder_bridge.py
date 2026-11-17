from tests.support.paths import MOVEMENT_FIXTURES
import copy
import json
import os
from pathlib import Path
import unittest

from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.scene.ladders import climb_walls, entry_channels, ladder_columns
from parkour_solver.search.ladder_ranking import rank_ladder_schedules, straight_line_score
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.scene.route import HALF_WIDTH


class LadderGeometryTests(unittest.TestCase):
    def test_columns_preserve_holes_and_signed_front_side_channels(self):
        columns = ladder_columns([[0, 0, 0], [0, 1, 0], [0, 3, 0], [1, 1, 0]])
        self.assertEqual([(c['minY'], c['maxY']) for c in columns], [(0, 2), (3, 4), (1, 2)])
        walls = climb_walls(columns[0], [[1, 0, 0, 2, 4, 1]])
        self.assertEqual(len(walls), 1)
        self.assertEqual([(c['family'], c['axis'], c['direction']) for c in entry_channels(columns[0], walls[0])],
                         [('front', 'X', -1), ('side', 'Z', -1), ('side', 'Z', 1)])

    def test_straight_line_ranking_evaluates_progress_and_caps(self):
        early = {'magnitude': [.1, .01], 'friction': [.9, .9], 'base': [0, 0]}
        late = {'magnitude': [.01, .1], 'friction': [.9, .9], 'base': [0, 0]}
        a, b = (straight_line_score(c, [False] * 3, 1) for c in (early, late))
        self.assertGreater(a['distance'], b['distance'])
        self.assertLess(a['finalSpeed'], b['finalSpeed'])
        capped = straight_line_score({'magnitude': [.4], 'friction': [.9], 'base': [0]}, [True, True], 1)
        self.assertAlmostEqual(capped['distance'], .15000000596046448)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER'), 'Set PKC_RUN_TICKS_COMPILER')
class LadderBridgeTests(unittest.TestCase):
    def setUp(self):
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_shared_compiler_realizes_sneak_and_repeated_wall_sprint_loss(self):
        route = {'sharedMovement': True, 'inputs': {'forward': .98, 'strafe': 0, 'sprint': True,
                                                   'incomingSprint': True, 'force45': True}}
        plan = {'grounded': [False] * 4, 'jump': [False] * 4, 'sneak': [False, True, False, False],
                'ladderTicks': [True] * 5, 'sceneContacts': [{'state': s, 'axis': 0} for s in (1, 2, 3)]}
        result = self.compiler.compile(route, plan, 5)
        self.assertAlmostEqual(result['movement']['moveForward'][1], .3 * .98, places=7)
        self.assertEqual(result['movement']['sprinting'], [True, False, False, False])
        self.assertEqual(result['movement']['airSprinting'], [True, True, False, False])

    def test_scene_checks_occupancy_sneak_and_actual_climb_velocity(self):
        plane = 1 - HALF_WIDTH
        box = [1, 0, -1, 2, 5, 2]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-5, -5, -5, 5, 10, 5],
                 'solids': [box], 'ladders': [[0, 1, 0], [0, 2, 0]]}
        positions = [[.6, 2, .5], [plane, 2, .5]]
        contacts = [{'state': 1, 'axis': 0, 'plane': plane, 'direction': 1, 'box': box}]
        contract = {'ladderTicks': [True, True], 'sneak': [True],
                    'velocities': [[0, 0, 0], [0, (.2 - .08) * .9800000190734863, 0]], 'friction': [.91]}
        checked = self.compiler.check_scene(scene, positions, [[.15, 0, 0]], [False, False], 5,
                                             contacts=contacts, **contract)
        self.assertTrue(checked['feasible'], checked)
        wrong = copy.deepcopy(contract)
        wrong['velocities'][1][1] = 0
        self.assertFalse(self.compiler.check_scene(scene, positions, [[.15, 0, 0]], [False, False], 5,
                                                 contacts=contacts, **wrong)['feasible'])
        wrong = copy.deepcopy(contract)
        wrong['ladderTicks'][1] = False
        self.assertFalse(self.compiler.check_scene(scene, positions, [[.15, 0, 0]], [False, False], 5,
                                                 contacts=contacts, **wrong)['feasible'])
        self.assertFalse(self.compiler.check_scene(scene, positions, [[.3, 0, 0]], [False, False], 5,
                                                 contacts=contacts, **contract)['feasible'])

    def test_first_clamped_sneak_can_preserve_progress_better_than_late_sneak(self):
        route = {'sharedMovement': True, 'inputs': {'forward': .98, 'strafe': 0, 'sprint': True,
                                                   'incomingSprint': False, 'force45': True}}
        plan = {'grounded': [True, False, False, False], 'jump': [True, False, False, False],
                'ladderTicks': [False, True, True, True, True],
                'sceneContacts': [{'state': s, 'axis': 0} for s in (2, 3, 4)]}
        scores = []
        for tick in (1, 3):
            p = dict(plan, sneak=[t == tick for t in range(4)])
            coefficients = self.compiler.compile(route, p, 5)
            scores.append(straight_line_score(coefficients, p['ladderTicks'], 3))
        self.assertGreater(scores[0]['distance'], scores[1]['distance'])

    def test_coplanar_climb_parts_require_the_declared_face_plane_and_overlap(self):
        lower, upper = [1, 0, -1, 2, 2, 2], [1, 2, -1, 2, 4, 2]
        scene = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-5, -5, -5, 5, 10, 5],
                 'solids': [lower, upper]}
        plane = 1 - HALF_WIDTH
        positions, movement = [[.6, 1.8, .5], [plane, 1.8, .5]], [[.15, 0, 0]]
        contact = {'state': 1, 'axis': 0, 'plane': plane, 'direction': 1, 'box': upper}
        checked = self.compiler.check_scene(scene, positions, movement, [False, False], 5, contacts=[contact])
        self.assertTrue(checked['feasible'], checked)
        for wrong in ([2, 2, -1, 3, 4, 2], [1, 4, -1, 2, 5, 2], [1, 2, 3, 2, 4, 4]):
            with self.subTest(box=wrong):
                checked = self.compiler.check_scene(scene, positions, movement, [False, False], 5,
                                                     contacts=[dict(contact, box=wrong)])
                self.assertFalse(checked['feasible'])

    def test_ranked_pool_preserves_backups_and_does_not_change_schedules(self):
        request = json.loads(MOVEMENT_FIXTURES / 'ladder-schedule-generator.json'.read_text())
        request['maxSchedules'] = 20
        schedules = generate_ladder_schedules(request)['schedules']
        original = copy.deepcopy(schedules)
        result = rank_ladder_schedules(schedules, self.compiler, seconds=5, representatives=1)
        self.assertEqual(result['rankedCount'], len(schedules))
        self.assertTrue(result['backups'])
        self.assertEqual(schedules, original)
        for row in result['ranked'] + result['backups']:
            self.assertEqual(row['score']['initialVelocity'], [0, 0])
