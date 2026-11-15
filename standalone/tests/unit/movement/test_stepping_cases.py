from tests.support.stepping_cases import FIXTURES, fixture
from tests.support.paths import AUTONOMY_DATA
import copy
import itertools
import json
import os
from pathlib import Path
import unittest

from parkour_solver.constraints.contacts import floor_checks
from parkour_solver.search.repair import solve_contact_branches; from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.search.support_profiles import build_transition_graph, compile_block_path, find_blocks
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.movement.cases import case_profile, corner_rows, entry_possible
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.generation import generate_choices
from parkour_solver.movement.units import compile_units, overlay_program, profile_program, shifted_unit
from parkour_solver.validation.scene import check_geometry
from parkour_solver.scene.route import prepare_route, schedule
from parkour_solver.numerics import prepare
from parkour_solver.physics.vertical import REST_CARRY, f32, vertical_step


def generated(data):
    scene = prepare_scene(data['request'])
    by_id = {b['id']: b for b in scene['blocks']}
    choices = list(generate_choices('ceiling' if data['case'] == 'grind' else 'stepping',
        dict(scene=scene, source=by_id['start'], target=by_id['goal'], cases=[data['case']], maxInputs=40)))
    choice = choices[data['choice']]
    edge = dict(source='start', target='goal', profileKind=choice['profileKind'], profile=choice['profile'],
                entryFace=choice.get('entryFace'), exitFace=None)
    return scene, edge


class SteppingCaseTests(unittest.TestCase):
    def test_legacy_prefix_without_landing_phase_keeps_pre_landing_timing(self):
        scene, edge = generated(fixture('blip'))
        profile = edge['profile']
        profile.pop('landingPhase')
        profile.pop('caseEvents')
        profile.pop('generatedUnits')
        compiled = compile_block_path(scene, ['start', 'goal'], [edge])
        self.assertEqual(compiled['route']['landingPhases'], ['pre'])
        self.assertIn((profile['airtime'] - 1, 1, 'landing'), schedule(compiled['route'], [0])['supportChecks'])

    def test_legacy_boost_profile_is_adapted_to_its_registered_owner(self):
        from parkour_solver.movement.profiles import stair_jump_profile
        profile = stair_jump_profile(1, 1.5)
        choice = next(generate_choices('stepping', dict(mode='profile', profileKind='stair', profile=profile)))
        self.assertEqual(choice['unit']['type'], 'stepping')
        self.assertEqual(choice['unit']['case'], 'slab_boost')
        self.assertEqual(choice['profile']['position'], profile['position'])
        self.assertEqual(choice['profile']['contacts'], profile['contacts'])

    def test_scalar_named_outcomes_and_rejected_controls(self):
        cases = [
            ('normal_step', 1, REST_CARRY, True, None, 1.5, {}, 1.5, True),
            ('slab_boost', 1, REST_CARRY, True, f32(.42), 1.5, {}, 1.5, False),
            ('blip', 1.2, -.4, False, None, 1.5, {'lowerHeight': 1, 'higherHeight': 1.5}, 1.5, True),
            ('blip_up', 1.6, -.8, False, None, 1.6, {'lowerHeight': 1, 'higherHeight': 1.5}, 1.6, True),
            ('corner_blip_up', 1.2, -.4, False, None, 1.2, {'lowerHeight': 1, 'higherHeight': 1.5}, 1.2, True),
            ('corner_jump_cancel', 1, REST_CARRY, True, f32(.42), 1, {'higherHeight': 1.5}, 1, False),
            ('grind', 1, REST_CARRY, True, f32(.42), 1, {'clearance': .1}, 1, False)]
        for kind, y, vy, ground, jump, plane, metadata, expected_y, expected_ground in cases:
            with self.subTest(case=kind):
                contact = dict(state=1, direction=-1, plane=plane, case=kind, **metadata)
                config = dict(threshold=.005, jumpVelocity=[jump], sneak=[False], contacts=[contact])
                actual_y, move, actual_ground, failures = vertical_step(config, 0, y, vy, False, ground, 0)
                self.assertEqual(failures, [])
                self.assertEqual((actual_y, move, actual_ground), (expected_y, 0, expected_ground))
                config['contacts'][0]['plane'] += 1
                self.assertTrue(vertical_step(config, 0, y, vy, False, ground, 0)[3])
        for height in [.1, .42, .5, f32(.6)]:
            p = case_profile(1, 1 + height, dict(case='normal_step', input=0, plane=1 + height),
                             finish_at=1, jump_at_start=False)
            self.assertEqual(p['position'], [1, 1 + height])
            self.assertEqual(p['jump'], [False])
        for height in [.60001, .75, 1.25]:
            self.assertIsNone(case_profile(1, 1 + height, dict(case='normal_step', input=0, plane=1 + height),
                                          finish_at=1, jump_at_start=False))

    def test_height_windows_and_free_hit_tick_are_required(self):
        for kind, y, vy, plane, metadata in [
            ('blip', 1.2, -.1, 1.5, {'lowerHeight': 1}),
            ('blip_up', 1.5, -.8, 1.5, {'lowerHeight': 1, 'higherHeight': 1.5}),
            ('corner_jump_cancel', 1, REST_CARRY, 1, {'higherHeight': 1 + f32(.42)}),
            ('grind', 1, REST_CARRY, 1, {'clearance': .42}),
            ('corner_blip_up', 1.2, -.4, 1.2, {'lowerHeight': 1, 'higherHeight': 1.9})]:
            jump = f32(.42) if kind in ('grind', 'corner_jump_cancel') else None
            config = dict(threshold=.005, jumpVelocity=[jump], sneak=[False],
                          contacts=[dict(state=1, direction=-1, plane=plane, case=kind, **metadata)])
            self.assertTrue(vertical_step(config, 0, y, vy, False, jump is not None, 0)[3], kind)

    def test_generated_units_retime_all_case_controls_constraints_and_scene_contacts(self):
        for path in FIXTURES.glob('*.json'):
            data = json.loads(path.read_text())
            scene, edge = generated(data)
            compiled = compile_block_path(scene, ['start', 'goal'], [edge])
            route = compiled['route']
            route.update(maxTotalRunTicks=2, runBounds=[[0, 2]])
            route = prepare_route(route)
            a, b = schedule(route, [0]), schedule(route, [2])
            self.assertEqual(a['ticks'], data['expectedTicks'])
            self.assertEqual(b['grounded'][2:], edge['profile']['grounded'][:-1])
            self.assertEqual(b['jump'][2:], edge['profile']['jump'])
            self.assertEqual([shifted_unit(u, 2) for u in a['movementUnits']], b['movementUnits'][1:])
            self.assertEqual([dict(c, state=c['state'] + 2) for c in a['sceneContacts']], b['sceneContacts'])
            self.assertTrue(validate_vertical_schedule(route, compiled['profiles'], b)['feasible'], path.name)
            partial = schedule(route, [2], stage='run')
            self.assertEqual(partial['ticks'], 2)
            self.assertTrue(validate_vertical_schedule(route, compiled['profiles'], partial)['feasible'], path.name)
            typed = [u for u in a['movementUnits'] if u['type'] in ('stepping', 'ceiling')]
            self.assertEqual([u['case'] for u in typed], [data['case']])
            expected_rows = edge['profile'].get('positionConstraints', [])
            for row in expected_rows:
                self.assertIn(dict(row, t1=row['t1'] + 2), b['walls'])
            broken = copy.deepcopy(route)
            broken['verticalProfiles'][0]['generatedUnits'][0]['jumpInputs'] = [199]
            with self.assertRaises(ValueError):
                prepare_route(broken)

    def test_elevated_ground_hands_off_to_jump_or_ground_then_air(self):
        for name in ['blip_up', 'blip_up-jump', 'corner_blip_up']:
            _, edge = generated(fixture(name))
            p = edge['profile']
            hit = p['caseEvents'][0]['anchors']['hit']
            self.assertEqual(p['position'][hit], p['position'][hit - 1])
            self.assertTrue(p['grounded'][hit])
            self.assertEqual(p['velocity'][hit], REST_CARRY)
            self.assertTrue(p['jump'][hit] if name.endswith('jump') else not p['jump'][hit])
            self.assertFalse(p['grounded'][hit + 1])
            self.assertAlmostEqual(p['position'][hit + 1] - p['position'][hit],
                                   f32(.42) if name.endswith('jump') else REST_CARRY)
            self.assertEqual(p['generatedUnits'][-1]['type'], 'normal')

    def test_ladder_ownership_survives_typed_air_overlay(self):
        units = [dict(type='normal', start=0, end=2, jumpInputs=[0], groundedInputs=[0], sneakInputs=[]),
                 dict(type='ladder_climb', start=2, end=5, occupiedStates=[3, 5], hitInputs=[2, 3]),
                 dict(type='normal', start=5, end=8, jumpInputs=[], groundedInputs=[], sneakInputs=[])]
        templates = [dict(type='ceiling', start=1, end=2, case='ceiling_hit'),
                     dict(type='ceiling', start=3, end=4, case='ceiling_hit'),
                     dict(type='ceiling', start=6, end=7, case='ceiling_hit')]
        overlaid = overlay_program(units, templates)
        self.assertEqual([u['type'] for u in overlaid], ['normal', 'ceiling', 'ladder_climb', 'normal', 'ceiling', 'normal'])
        self.assertEqual(compile_units(8, overlaid)['hit'], [False, False, True, True, False, False, False, False])
        self.assertEqual(overlaid[0]['jumpInputs'], [0])
        with self.assertRaises(ValueError):
            overlay_program(units, templates + [templates[0]])

    def test_corner_constraints_have_x_approach_and_consecutive_z_escape(self):
        box = [.85, 1, 0, 1.15, 1.5, .55]
        for direction, escape in itertools.product((-1, 1), repeat=2):
            rows = corner_rows(box, 4, 5, direction, escape)
            self.assertEqual(rows[0]['mode'], 'X')
            self.assertEqual(rows[0]['t1'], 4)
            self.assertEqual(rows[1]['mode'], 'Z')
            self.assertEqual(rows[1]['t1'], 5)
            self.assertEqual(rows[0]['cmp'], 'LE' if direction > 0 else 'GE')
            self.assertEqual(rows[1]['cmp'], 'GE' if escape > 0 else 'LE')

    def test_grind_and_corner_cancel_share_y_without_claiming_a_wall_hit(self):
        profiles = [generated(fixture(name))[1]['profile'] for name in ['grind', 'corner_jump_cancel']]
        self.assertEqual(profiles[0]['position'], profiles[1]['position'])
        for p in profiles:
            self.assertEqual(p['position'], [1, 1, 1])
            self.assertEqual(p['grounded'], [True, False, True])
            self.assertEqual(p.get('contactEvents'), [])
        self.assertEqual(profiles[0]['generatedUnits'][0]['type'], 'ceiling')
        self.assertEqual(profiles[1]['generatedUnits'][0]['type'], 'stepping')


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class SteppingNumericalTests(unittest.TestCase):
    def test_fresh_original_state_solves_for_every_case_and_both_blip_wall_choices(self):
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for path in sorted(FIXTURES.glob('*.json')):
                with self.subTest(fixture=path.name):
                    data = json.loads(path.read_text())
                    scene, edge = generated(data)
                    compiled = compile_block_path(scene, ['start', 'goal'], [edge])
                    result = solve_contact_branches(compiled['route'], compiled['profiles'],
                        floor_checks(scene, ['start', 'goal'], compiled['profiles']), compiler,
                        seconds=10, candidate_seconds=1, retries=0, max_branches=16,
                        geometry=scene['collisionScene'], motion_bound=dict(movementAfterSupportCheck=.8, storedSpeedNorm=.6),
                        python_executable=os.environ['PKC_IPOPT_PYTHON'])
                    self.assertEqual(result['status'], 'feasible', result)
                    best = result['best']
                    self.assertTrue(best['geometryCheck']['feasible'])
                    self.assertEqual(best['plan']['ticks'], data['expectedTicks'])
                    vertical = dict(vertical=best['vertical'], **{k: best['candidate'][k] for k in ('y', 'vy', 'onGround')})
                    missing = copy.deepcopy(best['plan'])
                    missing['sceneContacts'] = [c for c in missing['sceneContacts'] if not c.get('movementCase')]
                    self.assertFalse(check_geometry(scene['collisionScene'], missing, best['candidate'], vertical, compiler, 3)['feasible'])
                    if path.stem == 'blip-wall':
                        event = edge['profile']['contactEvents'][0]
                        landing = event['offset'] + 1
                        self.assertEqual(best['candidate']['velocity'][landing][0], 0)
                        self.assertTrue(best['candidate']['movement']['horizontalCollision'][landing])
                        self.assertFalse(best['candidate']['movement']['sprinting'][landing])
                        self.assertEqual(best['candidate']['y'][landing + 1], 1.5)
                    elif data['case'] in ('grind', 'corner_jump_cancel'):
                        self.assertFalse(any(best['candidate']['movement']['horizontalCollision']))
                    if data['case'] in ('blip_up', 'corner_blip_up'):
                        hit = edge['profile']['caseEvents'][0]['anchors']['hit']
                        self.assertEqual(best['candidate']['y'][hit], best['candidate']['y'][hit - 1])
                        self.assertTrue(best['candidate']['onGround'][hit])

    def test_z_facing_corner_controls_do_not_inherit_x_facing_vertical_cancellation(self):
        def swap_box(box):
            return [box[2], box[1], box[0], box[5], box[4], box[3]]
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for name in ['corner_jump_cancel', 'corner_blip_up']:
                scene, edge = generated(fixture(name))
                compiled = compile_block_path(scene, ['start', 'goal'], [edge])
                result = solve_contact_branches(compiled['route'], compiled['profiles'],
                    floor_checks(scene, ['start', 'goal'], compiled['profiles']), compiler,
                    seconds=8, candidate_seconds=1, retries=0, max_branches=16,
                    geometry=scene['collisionScene'], motion_bound=dict(movementAfterSupportCheck=.8, storedSpeedNorm=.6),
                    python_executable=os.environ['PKC_IPOPT_PYTHON'])
                self.assertEqual(result['status'], 'feasible', result)
                best = result['best']
                candidate = copy.deepcopy(best['candidate'])
                for key in ('positions', 'velocity', 'rawMovement'):
                    candidate[key] = [row[::-1] for row in candidate[key]]
                plan = copy.deepcopy(best['plan'])
                for contact in plan['sceneContacts']:
                    contact['box'] = swap_box(contact['box'])
                    if contact['axis'] in (0, 2):
                        contact['axis'] = 2 - contact['axis']
                geometry = copy.deepcopy(scene['collisionScene'])
                geometry['solids'] = [swap_box(box) for box in geometry['solids']]
                geometry['bounds'] = swap_box(geometry['bounds'])
                vertical = dict(vertical=best['vertical'], **{k: best['candidate'][k] for k in ('y', 'vy', 'onGround')})
                self.assertFalse(check_geometry(geometry, plan, candidate, vertical, compiler, 3)['feasible'])

    def test_normal_step_supports_both_horizontal_axes(self):
        data = fixture('normal_step')
        request = data['request']
        request['initial']['position'] = request['initial']['position'][::-1]
        request['initial']['velocity'] = request['initial']['velocity'][::-1]
        for block in request['blocks']:
            block['boxes'] = [[b[2], b[1], b[0], b[5], b[4], b[3]] for b in block['boxes']]
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result = find_blocks(request, compiler, seconds=10, route_seconds=3, candidate_seconds=1,
                                 retries=0, python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'], 'feasible', result)
        self.assertEqual(result['best']['plan']['ticks'], 1)
        self.assertTrue(result['best']['geometryCheck']['feasible'])

    def test_grind_continues_through_normal_air_to_a_lower_floor(self):
        data = fixture('grind')
        request = data['request']
        request['blocks'][0]['boxes'][0][3] = .55
        request['blocks'][-1]['boxes'][0][4] = .5
        scene, edge = generated(data)
        compiled = compile_block_path(scene, ['start', 'goal'], [edge])
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result = solve_contact_branches(compiled['route'], compiled['profiles'],
                floor_checks(scene, ['start', 'goal'], compiled['profiles']), compiler,
                seconds=8, candidate_seconds=1, retries=0, max_branches=8, geometry=scene['collisionScene'],
                motion_bound=dict(movementAfterSupportCheck=.8, storedSpeedNorm=.6),
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'], 'feasible', result)
        self.assertTrue(result['best']['geometryCheck']['feasible'])
        self.assertEqual(result['best']['candidate']['y'][:2], [1, 1])
        self.assertEqual(result['best']['candidate']['y'][-1], .5)
        self.assertGreater(result['best']['plan']['ticks'], 2)

    def test_cold_discovery_covers_the_catalogue_and_allows_composite_height_gain(self):
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for name in ['blip', 'blip_up', 'corner_blip_up', 'corner_jump_cancel', 'grind']:
                data = fixture(name)
                graph = build_transition_graph(data['request'], compiler, seconds=6)
                cases = {c['case'] for e in graph['edgeOptions']['start']['goal']
                         for c in graph['transitions'][e]['profile'].get('caseEvents', [])}
                self.assertIn(data['case'], cases)
            request = fixture('blip_up')['request']
            request['blocks'] = [dict(id='start', boxes=[[-2, 1, -1, 1, 2, 1]]),
                dict(id='lower', boxes=[[2, 2, -1, 3, 2.5, 1]]),
                dict(id='higher', boxes=[[3, 2.5, -1, 3.125, 2.625, 1]]),
                dict(id='goal', boxes=[[5, 2, -1, 6, 3.5, 1]])]
            graph = build_transition_graph(request, compiler, seconds=8)
            self.assertTrue(any(graph['transitions'][e]['profileKind'] == 'blip_up'
                                for e in graph['edgeOptions']['start']['goal']))

    def test_cold_geometry_discovers_normal_steps_and_exposes_generation_caps(self):
        data = fixture('normal_step')
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            graph = build_transition_graph(data['request'], compiler, seconds=6)
            edges = [graph['transitions'][e] for e in graph['edgeOptions']['start']['goal']]
            self.assertTrue(any(e['profileKind'] == 'step' for e in edges))
            self.assertTrue(any(e['profileKind'] == 'stair' for e in edges))
            self.assertTrue(any(e['profileKind'] == 'ordinary' for e in edges))
            result = find_blocks(data['request'], compiler, seconds=15, route_seconds=3, candidate_seconds=1,
                                 retries=0, python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'], 'feasible', result)
            self.assertEqual(result['best']['plan']['ticks'], 1)
            limited = dict(data['request'], maxSteppingProfiles=1)
            graph = build_transition_graph(limited, compiler, seconds=6)
            self.assertTrue(any(not g['complete'] for g in graph['steppingGeneration']))
            self.assertTrue(graph['profileLimitReached'])
