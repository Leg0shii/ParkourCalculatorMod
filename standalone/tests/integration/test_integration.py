from tests.support.paths import AUTONOMY_DATA
import copy
import json
import os
import unittest

from parkour_solver.api import find_route, prepare_route, schedule
from parkour_solver.movement.profiles import compile_open_air_route
from parkour_solver.search.support_profiles import compile_block_path, find_blocks
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.python_compiler import PythonCompiler
from parkour_solver.scene.route import initial_guess, supports
from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.validation.scene import check_geometry
from parkour_solver.numerics import prepare, validate


RUNTIME = os.environ.get('PKC_IPOPT_PYTHON')
FIXTURES = AUTONOMY_DATA / 'fixtures'


@unittest.skipUnless(RUNTIME, 'Set PKC_IPOPT_PYTHON')
class IntegrationTests(unittest.TestCase):
    def test_wall_and_ceiling_the_player_cannot_pass_between_are_one_obstacle(self):
        # The wall and the overhanging ceiling are 0.14 apart, so one cluster's
        # walk passes both, with a corner side bounded by each of them. Check the
        # per-phase sides: the passage's face list merges nested sides, and where the
        # ceiling is out of reach the wall's plain side contains that corner.
        request = json.loads((FIXTURES / 'obstacle-interleaved.json').read_text())
        request.update(ceilingHits=False, maxWallHits=0)
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=55, route_seconds=45,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        best = result['best']
        self.assertEqual(best['plan']['ticks'], 12)
        self.assertTrue(best['geometryCheck']['feasible'])
        passage = next(p for p in best['passages'] if p['blockId'] == 'ceiling+wall')
        self.assertEqual(len(passage['members']), 2)
        boxes = {b['id']: b['boxes'][0] for b in request['blocks']}
        corner = sorted([('X', -1, boxes['ceiling'][0]-.3), ('Z', 1, boxes['wall'][5]+.3)])
        self.assertTrue(any(len(key) == 2 and all(a == b[0] and d == b[1] and abs(p-b[2]) < 1e-6
                                                  for (a, d, p), b in zip(sorted(map(tuple, key)), corner))
                            for _, _, key in passage['phaseSpans']), passage['phaseSpans'])
        timeline = best['blockageTimeline']
        self.assertTrue(timeline and all(g['blockId'] == 'ceiling+wall' for g in timeline))
        self.assertEqual(sum(u['total'] for u in best['blockageUsage']), len(timeline))

    def test_full_obstacle_geometry_finds_cross_and_wide_neos_and_checks_both_models(self):
        with PythonCompiler() as compiler:
            for fixture, kind in [('obstacle-cross-neo.json', 'cross_neo'), ('obstacle-wide-neo.json', 'wide_neo')]:
                with self.subTest(fixture=fixture):
                    request = json.loads((FIXTURES / fixture).read_text())
                    request.update(ceilingHits=False, maxWallHits=0)
                    if kind == 'wide_neo':
                        # The historical full-width goal also permits a cross
                        # landing at its edge. Require a real return from the side.
                        target = next(b for b in request['blocks'] if b['id'] == request['goalBlock'])
                        target['boxes'][0][2], target['boxes'][0][5] = .4, .6
                        request['initial']['velocity'][0] = .1
                    result = find_blocks(request, compiler, seconds=35, route_seconds=25,
                                         candidate_seconds=1, retries=0, python_executable=RUNTIME)
                    self.assertEqual(result['status'], 'feasible', result)
                    best = result['best']
                    self.assertEqual(best['plan']['ticks'], 12)
                    actual = {p['kind'] for p in best['passages']}
                    self.assertTrue(actual & ({'wide_neo'} if kind == 'wide_neo' else {'cross_neo', 'wide_neo'}))
                    self.assertTrue(best['geometryCheck']['feasible'])
                    coefficients = compiler.compile(best['route'], best['plan'], 2)
                    problem = prepare(dict(coefficients, version=1, ticks=best['plan']['ticks'],
                                           position=request['initial']['position'][::2],
                                           velocity=request['initial']['velocity'][::2], yaw=best['candidate']['yaw'],
                                           walls=best['plan']['walls'], vertical=best['vertical'],
                                           objective={'axis': 'X', 'sense': 'MAX', 'tick': best['plan']['ticks']}))
                    self.assertTrue(validate(problem, best['candidate']['yaw'])['feasible'])
                    vertical = validate_vertical_schedule(best['route'], best['profiles'], best['plan'])
                    self.assertTrue(check_geometry(result['scene']['collisionScene'], best['plan'], best['candidate'],
                                                   vertical, compiler, 3)['feasible'])
                    self.assertFalse(any(w['mode'] == 'F' for w in problem['walls']))
                    self.assertTrue(result['minimumTicksProven'])

    def test_source_runoff_can_leave_the_near_side_and_stays_clear_while_y_overlaps(self):
        request = json.loads((FIXTURES / 'obstacle-runoff-side.json').read_text())
        request.update(ceilingHits=False, maxWallHits=0)
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=5,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        best = result['best']
        self.assertEqual(best['route']['kinds'], ['walk_off'])
        self.assertEqual(best['plan']['ticks'], 5)
        self.assertEqual(best['allocation'], [1])
        self.assertEqual(best['route']['walkOffExits'], [{'axis': 'Z', 'direction': 1}])
        for tick in range(best['plan']['events'][0]['jump'], best['plan']['ticks'] + 1):
            if best['candidate']['y'][tick] < 1 and best['candidate']['y'][tick] + BODY_HEIGHT > 0:
                self.assertFalse(supports(best['route']['blocks'][0], *best['candidate']['positions'][tick]))
        self.assertTrue(best['geometryCheck']['feasible'])

    def test_ceiling_can_be_passed_under_and_a_sealed_wall_is_never_accepted(self):
        with PythonCompiler() as compiler:
            for name, status in [('obstacle-under-ceiling.json', 'feasible'), ('obstacle-blocked.json', 'unknown')]:
                request = json.loads((FIXTURES / name).read_text())
                request.update(ceilingHits=False, maxWallHits=0)
                result = find_blocks(request, compiler, seconds=15, route_seconds=5,
                                     candidate_seconds=1, retries=0, python_executable=RUNTIME)
                self.assertEqual(result['status'], status, result)
                if status == 'feasible':
                    self.assertTrue(result['best']['geometryCheck']['feasible'])
                    self.assertEqual(result['best']['plan']['ticks'], 12)
                else:
                    self.assertIsNone(result['best'])
                    self.assertTrue(any(a['obstacleSearch'].get('excludedFaces', 0) > 0 for a in result['attempts']))

    def test_shared_scene_bridge_detects_swept_walls_and_releases_below_the_bottom(self):
        geometry = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': [-5, -5, -5, 10, 10, 10],
                    'solids': [[1, 0, -3, 2, 5, 3]], 'stepHeight': .6}
        with PythonCompiler() as compiler:
            blocked = compiler.check_scene(geometry, [[.5, 1, .5], [3.5, 1, .5]],
                                           [[3, 0, 0]], [False, False], 2)
            clear = compiler.check_scene(geometry, [[.5, -2, .5], [3.5, -2, .5]],
                                         [[3, 0, 0]], [False, False], 2)
        self.assertFalse(blocked['feasible'])
        self.assertEqual(blocked['clips'][0]['axis'], 0)
        self.assertEqual(blocked['clips'][0]['box'], geometry['solids'][0])
        self.assertTrue(clear['feasible'], clear)

    def test_runoff_competes_as_a_shorter_profile_in_the_same_route_search(self):
        request = json.loads((FIXTURES / 'blocks-runoff.json').read_text())
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=4,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
            best = result['best']
            self.assertEqual(result['status'], 'feasible', result)
            coefficients = compiler.compile(best['route'], best['plan'], 3)
        self.assertEqual([p['profileKind'] for p in best['profiles']], ['run_off'])
        self.assertEqual(best['route']['airtimes'], [4])
        self.assertEqual(best['allocation'], [4])
        self.assertEqual(best['plan']['ticks'], 8)
        self.assertFalse(any(best['plan']['jump']))
        self.assertTrue(all(v is None for v in best['vertical']['jumpVelocity']))
        self.assertLess(coefficients['magnitude'][4], .2)
        self.assertEqual(best['candidate']['onGround'], best['plan']['grounded'] + [True])

    def test_a_jump_can_be_followed_by_a_zero_delay_runoff(self):
        request = json.loads((FIXTURES / 'blocks-jump-runoff.json').read_text())
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=4,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        best = result['best']
        self.assertEqual(best['blockIds'], ['start', 'middle', 'goal'])
        self.assertEqual([p['profileKind'] for p in best['profiles']], ['ordinary', 'run_off'])
        self.assertEqual(best['route']['airtimes'], [12, 4])
        self.assertEqual(best['allocation'], [0, 0])
        self.assertEqual(best['plan']['ticks'], 16)
        departure = best['plan']['events'][1]['jump']
        self.assertFalse(best['plan']['jump'][departure])
        self.assertFalse(supports(best['route']['blocks'][1], *best['candidate']['positions'][departure]))

    def test_small_drop_can_land_on_the_first_runoff_input(self):
        request = json.loads((FIXTURES / 'blocks-runoff.json').read_text())
        request['initial']['position'][0] = .9
        request['initial']['velocity'][0] = .4
        request['blocks'][1].update(bounds=[1.5, 2.5, 0, 1], top=.9375)
        request['maxRunTicksPerJump'] = request['maxTotalRunTicks'] = 1
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=15, route_seconds=3,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['route']['airtimes'], [1])
        self.assertEqual(result['best']['allocation'], [1])
        self.assertEqual(result['best']['plan']['ticks'], 2)
        self.assertEqual(result['best']['candidate']['y'], [1, 1, .9375])

    def test_grounded_jump_after_leaving_the_edge_uses_only_the_scheduled_inputs(self):
        request = json.loads((FIXTURES / 'blocks-required-run.json').read_text())
        request['initial']['position'][0] = .9
        request['initial']['velocity'][0] = .4
        request['blocks'][0]['bounds'] = [5, 6, 0, 1]
        scene = prepare_scene(request)
        compiled = compile_block_path(scene, ['start', 'goal'])
        route = compiled['route']
        plan = schedule(route, [1])
        with PythonCompiler() as compiler:
            coefficients = compiler.compile(route, plan, 3)
        problem = prepare(dict(coefficients, version=1, ticks=plan['ticks'], position=[.9, .5],
                               velocity=[.4, 0], yaw=initial_guess(route, plan), threshold=.005,
                               walls=plan['walls'], objective={'axis': 'X', 'sense': 'MAX', 'tick': plan['ticks']}))
        checked = validate(problem, problem['yaw'])
        self.assertTrue(checked['feasible'], checked)
        self.assertEqual(plan['ticks'], 13)
        self.assertEqual(len(checked['yaw']), 13)
        self.assertTrue(supports(route['blocks'][0], *checked['positions'][0]))
        self.assertFalse(supports(route['blocks'][0], *checked['positions'][1]))
        vertical = validate_vertical_schedule(route, compiled['profiles'], plan)
        self.assertTrue(vertical['feasible'])
        self.assertTrue(vertical['onGround'][1])
        self.assertTrue(plan['jump'][1])

    def test_configured_objective_is_optimized_after_fixing_the_tick_count(self):
        request = json.loads((FIXTURES / 'blocks-required-run.json').read_text())
        request['blocks'][0]['bounds'] = [2.5, 3.5, 0, 1]
        values = {}
        with PythonCompiler() as compiler:
            for sense in ['MAX', 'MIN']:
                request['objective'] = {'axis': 'Z', 'sense': sense}
                result = find_blocks(request, compiler, seconds=15, route_seconds=4,
                                     candidate_seconds=1, retries=0, python_executable=RUNTIME)
                self.assertEqual(result['status'], 'feasible', result)
                self.assertEqual(result['best']['plan']['ticks'], 12)
                candidate = result['best']['candidate']
                self.assertEqual(candidate['objective'], candidate['positions'][-1][1])
                values[sense] = candidate['objective']
                self.assertTrue(result['minimumTicksProven'])
                self.assertFalse(result['objectiveOptimalityProven'])
        self.assertGreater(values['MAX'], 1)
        self.assertLess(values['MIN'], 0)

    def test_staircase_uses_two_short_profiles_and_searches_the_run_between_them(self):
        request = json.loads((FIXTURES / 'blocks-staircase.json').read_text())
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=4,
                                 candidate_seconds=1, retries=0, improve=True, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['blockIds'], ['start', 'step', 'goal'])
        self.assertEqual([p['profileKind'] for p in result['best']['profiles']], ['stair', 'stair'])
        self.assertEqual(result['best']['route']['airtimes'], [2, 2])
        self.assertEqual(sum(result['best']['allocation']), 1)
        self.assertEqual(result['best']['plan']['ticks'], 5)
        self.assertEqual(result['best']['candidate']['y'][-1], 2)
        self.assertEqual(result['best']['candidate']['onGround'], result['best']['plan']['grounded'] + [True])
        self.assertEqual([c['state'] for c in result['best']['vertical']['contacts'] if c.get('step')],
                         [event['jump'] + 1 for event in result['best']['plan']['events']])
        for entry in result['best']['route']['contactEntries']:
            self.assertEqual(entry['enter'], 1)

    def test_ordinary_half_block_jump_survives_when_the_short_stair_profile_cannot_reach(self):
        request = json.loads((FIXTURES / 'blocks-staircase.json').read_text())
        request['maxJumps'] = 1
        request['blocks'] = [b for b in request['blocks'] if b['id'] != 'step']
        request['blocks'][0].update(bounds=[2.8, 3.8, 0, 1], top=1.5)
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=4,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['profiles'][0]['profileKind'], 'ordinary')
        self.assertEqual(result['best']['route']['airtimes'], [10])
        self.assertGreater(len(result['attempts']), 1)

    def test_different_heights_generate_edge_specific_airtimes(self):
        request = json.loads((FIXTURES / 'blocks-height-route.json').read_text())
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=20, route_seconds=4,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['blockIds'], ['start', 'raised', 'goal'])
        self.assertEqual(result['best']['route']['airtimes'], [9, 9])
        self.assertEqual(result['best']['plan']['ticks'], 18)
        self.assertNotIn('goal', result['graph']['adjacency']['start'])
        self.assertEqual(result['graph']['profiles']['raised']['start']['airtime'], 14)

    def test_lower_target_cannot_be_accepted_through_an_earlier_floor(self):
        request = {'version': 1, 'sceneModel': 'support_planes',
                   'initial': {'position': [.5, 2, .5], 'velocity': [0, -.0784000015258789, 0]},
                   'startBlock': 'start', 'goalBlock': 'goal', 'maxJumps': 1,
                   'maxRunTicksPerJump': 0, 'maxTotalRunTicks': 0,
                   'blocks': [{'id': 'start', 'bounds': [-5, 5, -5, 5], 'top': 2},
                              {'id': 'goal', 'bounds': [2, 3, 0, 1], 'top': 1}]}
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=15, route_seconds=5,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'unknown', result)
        self.assertIsNone(result['best'])
        self.assertTrue(any(a['contactBranches'] == 5 for a in result['attempts']))

    def test_selected_block_path_can_require_an_added_ground_input(self):
        request = json.loads((FIXTURES / 'blocks-required-run.json').read_text())
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=15, route_seconds=5,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['allocation'], [1])
        self.assertEqual(result['best']['plan']['ticks'], 13)
        self.assertFalse(result['minimumTicksProven'])

    def test_only_endpoints_are_fixed_and_remote_supports_are_pruned(self):
        request = json.loads((FIXTURES / 'blocks-route-choice.json').read_text())
        original = copy.deepcopy(request)
        with PythonCompiler() as compiler:
            result = find_blocks(request, compiler, seconds=25, route_seconds=4,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['blockIds'], ['start', 'middle', 'goal'])
        self.assertEqual(result['best']['cost'], 0)
        self.assertNotIn('remote', result['graph']['distanceToGoal'])
        self.assertGreater(result['pruning']['horizontalEdges'], 0)
        self.assertEqual(request, original)
        self.assertFalse(result['assumptions']['routeOrderSupplied'])
        self.assertFalse(result['assumptions']['airtimesSupplied'])
        best = result['best']
        self.assertEqual(best['route']['airtimes'], [12, 12])
        self.assertFalse(any(w['mode'] == 'F' for w in best['plan']['walls']))
        for state, block, kind in best['plan']['supportChecks']:
            self.assertTrue(supports(best['route']['blocks'][block], *best['candidate']['positions'][state], kind=kind))

    def test_route_pool_matches_exhaustive_small_family_with_coupled_momentum(self):
        request = json.loads((FIXTURES / 'blocks-momentum-choice.json').read_text())
        scene = prepare_scene(request)
        exhaustive = []
        with PythonCompiler() as compiler:
            for path in [['start', 'goal']] + [['start', b['id'], 'goal'] for b in request['blocks'] if b['id'] != 'goal']:
                route = compile_block_path(scene, path)['route']
                result = find_route(route, compiler, seconds=5, candidate_seconds=1,
                                    retries=0, python_executable=RUNTIME)
                self.assertNotEqual(result['status'], 'error', result)
                if result['best'] is not None:
                    exhaustive.append((result['best']['cost'], path))
            first_leg = compile_block_path(scene, ['start', 'tempting'])['route']
            reached = find_route(first_leg, compiler, seconds=5, candidate_seconds=1,
                                 retries=0, python_executable=RUNTIME)
            self.assertEqual(reached['status'], 'feasible', reached)
            pooled = find_blocks(request, compiler, seconds=20, route_seconds=4,
                                 candidate_seconds=1, retries=0, python_executable=RUNTIME)
        self.assertTrue(exhaustive)
        self.assertEqual(pooled['status'], 'feasible', pooled)
        self.assertIn((pooled['best']['cost'], pooled['best']['blockIds']), exhaustive)
        self.assertEqual(pooled['best']['blockIds'], ['start', 'around', 'goal'])
        self.assertEqual(pooled['best']['cost'], min(cost for cost, _ in exhaustive))

    def test_blocks_without_supplied_airtimes_feed_the_existing_run_search(self):
        request = json.loads((FIXTURES / 'blocks-flat-two-jumps.json').read_text())
        self.assertNotIn('airtimes', request)
        compiled = compile_open_air_route(request)
        self.assertEqual(compiled['status'], 'derived')
        self.assertEqual(compiled['route']['airtimes'], [12, 12])
        with PythonCompiler() as compiler:
            result = find_route(compiled['route'], compiler, seconds=25, candidate_seconds=3,
                                retries=0, python_executable=RUNTIME)
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['allocation'], [0, 0])
        self.assertLessEqual(result['best']['candidate']['violation'], 1e-7)

    def test_java_compiles_ground_jump_and_air_with_the_existing_model(self):
        r = prepare_route(json.loads((FIXTURES / 'one-required-run-tick.json').read_text()))
        plan = schedule(r, [1])
        with PythonCompiler() as c:
            coefficients = c.compile(r, plan, 3)
        self.assertEqual(coefficients['friction'][0], coefficients['friction'][1])
        self.assertLess(coefficients['friction'][0], coefficients['friction'][2])
        self.assertGreater(coefficients['magnitude'][1], coefficients['magnitude'][0])
        self.assertGreater(coefficients['magnitude'][0], coefficients['magnitude'][2])

    def test_java_reuses_the_mod_footprint_deriver(self):
        import math
        r = prepare_route(json.loads((FIXTURES / 'one-required-run-tick.json').read_text()))
        with PythonCompiler() as c:
            footprints = c.derive_footprints(r, 3)
            r['blocks'][1] = {'centerBounds': [4, 4.5, 0, 1], 'top': 1}
            supplied = c.derive_footprints(r, 3)
        lo, hi, zlo, zhi = footprints[0]
        self.assertTrue(supports(r['blocks'][0], lo, .5))
        self.assertFalse(supports(r['blocks'][0], math.nextafter(lo, -math.inf), .5))
        self.assertTrue(supports(r['blocks'][0], hi, .5))
        self.assertFalse(supports(r['blocks'][0], math.nextafter(hi, math.inf), .5))
        self.assertEqual(supplied[1], [4, 4.5, 0, 1])

    def test_known_routes_zero_one_and_backtracking(self):
        for name, expected in [('two-jumps.json', [0, 0]), ('one-required-run-tick.json', [1]),
                               ('backtrack.json', [2, 0]), ('turning-two-jumps.json', [0, 0])]:
            with self.subTest(name=name):
                request = json.loads((FIXTURES / name).read_text())
                original = copy.deepcopy(request)
                with PythonCompiler() as c:
                    result = find_route(request, c, seconds=25, candidate_seconds=3,
                                        retries=0, python_executable=RUNTIME)
                self.assertEqual(result['status'], 'feasible', result)
                self.assertEqual(result['best']['allocation'], expected, result)
                self.assertEqual(request, original)
                best = result['best']
                for state, block, kind in best['plan']['supportChecks']:
                    self.assertTrue(supports(request['blocks'][block], *best['candidate']['positions'][state]))
                if name == 'backtrack.json':
                    self.assertGreaterEqual(result['backtracks'], 2)
                if name == 'turning-two-jumps.json':
                    self.assertGreater(max(best['candidate']['yaw']) - min(best['candidate']['yaw']), 30)
                print(name, best['allocation'], round(result['elapsedSeconds'], 3), 's')

    def test_linkcraft_section_keeps_runoff_and_finds_without_recorded_facings(self):
        request = json.loads((FIXTURES / 'linkcraft11-j5-j9.json').read_text())
        self.assertFalse(request['source']['recordedFacingsUsed'])
        self.assertEqual(request['initial']['yaw'], 0)
        self.assertEqual(request['constraints'], [])
        self.assertFalse(request['source']['authoredScalarRulesUsed'])
        self.assertEqual(request['source']['recordedAllocation'], [0, 0, 0, 0, 0, 1])
        with PythonCompiler() as compiler:
            result = find_route(request, compiler, seconds=30, candidate_seconds=3,
                                retries=1, python_executable=RUNTIME)
            self.assertEqual(result['status'], 'feasible', result)
            best = result['best']
            self.assertEqual(best['allocation'], [0, 0, 0, 0, 0, 0])
            self.assertEqual(best['plan']['ticks'], 24)
            event = best['plan']['events'][1]
            self.assertEqual(event['kind'], 'walk_off')
            departure = event['jump']
            self.assertEqual(best['plan']['grounded'][departure:departure + 4], [True, False, False, False])
            self.assertFalse(any(best['plan']['jump'][departure:departure + 4]))
            self.assertFalse(supports(request['blocks'][1], *best['candidate']['positions'][departure]))
            coefficients = compiler.compile(prepare_route(request), best['plan'], 3)
            self.assertLess(coefficients['magnitude'][departure], .14)
            self.assertGreater(coefficients['magnitude'][0], .32)
        self.assertEqual(result['validationLevel'], 'continuous_horizontal_declared_regions_with_supplied_airtimes')
        self.assertEqual(result['gameReplay'], 'not_run')
        print('Linkcraft j5-j9', best['allocation'], round(result['elapsedSeconds'], 3), 's')


if __name__ == '__main__':
    unittest.main()
