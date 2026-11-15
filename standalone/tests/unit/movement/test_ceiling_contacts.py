from tests.support.paths import AUTONOMY_DATA
import copy
import json
import os
from pathlib import Path
import unittest

from parkour_solver.search.repair import validate_vertical_schedule
from parkour_solver.movement.profiles import ordinary_jump_profile
from parkour_solver.search.support_profiles import build_transition_graph, compile_block_path, find_blocks, prepare_scene
from parkour_solver.movement.ceiling_contacts import ceiling_profile
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.scene.route import schedule
from parkour_solver.physics.vertical import REST_CARRY, f32


FIXTURE = AUTONOMY_DATA / 'fixtures/ceiling-bonk.json'


class CeilingProfileTests(unittest.TestCase):
    def test_existing_y_model_regenerates_single_hit_and_shorter_landing(self):
        for bottom in [3, 3.4, 3.9]:
            box = [-2, bottom, -2, 5, 5, 3]
            profile = ceiling_profile(1, 1, box, 2, 'ceiling')
            hit = profile['ceilingEvent']['offset']
            self.assertLess(profile['airtime'], ordinary_jump_profile(1, 1)['airtime'])
            self.assertEqual(profile['velocity'][hit + 1], REST_CARRY)
            self.assertLess(profile['position'][hit + 2], profile['position'][hit + 1])
            self.assertEqual(sum(c['direction'] > 0 for c in profile['contacts']), 1)
            plane = box[1] - f32(1.8)
            self.assertTrue(all(y <= plane for y in profile['position'][:hit + 1]))
            self.assertTrue(all(y < plane for y in profile['position'][hit + 2:]))
        self.assertIsNone(ceiling_profile(1, 1, [-2, 5, -2, 5, 6, 3], 2, 'high'))
        short = ceiling_profile(1, 1, [-2, 1 + f32(1.8), -2, 5, 5, 3], 2, 'low', max_inputs=2)
        self.assertEqual(short['airtime'], 2)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class CeilingContactIntegrationTests(unittest.TestCase):
    def test_same_input_ceiling_and_wall_hit_use_one_coupled_movement_schedule(self):
        request = json.loads(FIXTURE.with_name('wall-ceiling-reversal.json').read_text())
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=12, candidate_seconds=1,
                                 retries=0, python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'], 'feasible', result)
        best = result['best']
        self.assertEqual(best['plan']['ticks'], 3)
        self.assertEqual({c['axis'] for c in best['plan']['sceneContacts']}, {0, 1})
        self.assertEqual({c['state'] for c in best['plan']['sceneContacts']}, {1})
        self.assertTrue(best['geometryCheck']['feasible'])
        self.assertEqual(best['candidate']['velocity'][1][0], 0)
        self.assertEqual(best['candidate']['vy'][1], REST_CARRY)
        self.assertFalse(best['candidate']['movement']['sprinting'][1])

    def test_geometry_discovers_bonk_and_graph_bound_includes_shorter_profile(self):
        request = json.loads(FIXTURE.read_text())
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            graph = build_transition_graph(request, compiler, seconds=5)
            edges = [graph['transitions'][e] for e in graph['edgeOptions']['start']['goal']]
            self.assertTrue(any(e['profileKind'] == 'ordinary' for e in edges))
            self.assertTrue(any(e['profileKind'] == 'ceiling' for e in edges))
            self.assertEqual(graph['flightInputsToGoal']['start'], 3)
            result = find_blocks(request, compiler, seconds=15, route_seconds=6, candidate_seconds=1,
                                 retries=0, python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'], 'feasible', result)
            best = result['best']
            self.assertEqual(best['plan']['ticks'], 3)
            self.assertEqual(len(best['ceilingEvents']), 1)
            self.assertEqual(best['hitEvents'], [])
            self.assertTrue(best['geometryCheck']['feasible'])
            self.assertTrue(all(best['candidate']['movement']['sprinting']))
            self.assertFalse(any(best['candidate']['movement']['horizontalCollision']))
            self.assertEqual(result['ticksLowerBound'], 3)
            scene = prepare_scene(request)
            reordered = copy.deepcopy(scene)
            reordered['blocks'].reverse()
            regenerated = build_transition_graph(reordered, compiler, seconds=5)
            self.assertEqual({e['profile']['ceilingEvent']['blockId'] for e in regenerated['transitions'].values()
                              if e['profileKind'] == 'ceiling'}, {'ceiling'})
            scene['maxRunTicksPerJump'] = scene['maxTotalRunTicks'] = 2
            compiled = compile_block_path(scene, ['start', 'goal'],
                                          [next(e for e in edges if e['profileKind'] == 'ceiling')])
            plan = schedule(compiled['route'], [2])
            self.assertEqual(plan['sceneContacts'][0]['state'], 3)
            self.assertTrue(validate_vertical_schedule(compiled['route'], compiled['profiles'], plan)['feasible'])
            missing = copy.deepcopy(best['plan'])
            missing['sceneContacts'] = []
            from parkour_solver.scene.geometry import check_geometry
            vertical = {'vertical': best['vertical'], **{k: best['candidate'][k] for k in ('y', 'vy', 'onGround')}}
            missed = check_geometry(scene['collisionScene'], missing, best['candidate'], vertical, compiler, 5)
            self.assertFalse(missed['feasible'])
