import copy
import math
import unittest

from parkour_solver.constraints.contacts import avoid_contact_rows, contact_conflicts, floor_checks
from parkour_solver.movement.profiles import ordinary_jump_profile, runoff_profile, stair_jump_profile
from parkour_solver.search.support_profiles import compile_block_path
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.scene.route import HALF_WIDTH, schedule


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



def box_scene(solids, tops):
    return dict(collisionScene=dict(solids=solids),
                blocks=[dict(id=name, sourceBlock=name, solidIndex=i, top=top) for i, (name, top) in enumerate(tops)])


class ExposedTopTests(unittest.TestCase):
    def landings(self, scene, **context):
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.physics.vertical import REST_CARRY
        state = dict(position=1.2, velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)
        return [c for c in normal_choices(dict(dict(scene=scene, state=state, maxInputs=30, threshold=0., interactionGate=None), **context))
                if c.get('terminalSupport') == 'floor']

    def test_landing_next_to_a_taller_neighbour_is_the_clipped_l(self):
        scene = box_scene([[0, 0, 0, 2, 1, 2], [2, 0, 1, 3, 3, 2]], [('floor', 1), ('pillar', 3)])
        walk = self.landings(scene, regionItems=True)
        self.assertTrue(walk)
        for choice in walk:
            event, = choice['profile']['contactEvents']
            self.assertEqual((event['boxes'], event['role']), ([[0, 0, 0, 2, 1, 2]], 'landing'))
            self.assertEqual(len(event['regions']), 2)
            entry, = choice['profile']['regionChoices']
            self.assertEqual((entry['owner'], len(entry['options'])), ('floor', 2))
            self.assertEqual(choice['profile']['positionConstraints'], [])
        self.assertTrue(all('regionChoices' not in c['profile'] for c in self.landings(scene)))

    def test_generation_without_a_gate_key_is_an_error_and_none_opts_out(self):
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.physics.vertical import REST_CARRY
        scene = box_scene([[0, 0, 0, 2, 1, 2], [2, 0, 1, 3, 3, 2]], [('floor', 1), ('pillar', 3)])
        state = dict(position=1.2, velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)
        with self.assertRaises(ValueError):
            list(normal_choices(dict(scene=scene, state=state, maxInputs=30, threshold=0.)))
        self.assertTrue(self.landings(scene))

    def test_a_departed_block_gets_no_landing_and_supports_still_filter(self):
        scene = box_scene([[0, 0, 0, 2, 1, 2], [2, 0, 1, 3, 3, 2]], [('floor', 1), ('pillar', 3)])
        self.assertTrue(self.landings(scene))
        self.assertEqual(self.landings(scene, feetAllowed=lambda block: block['id'] != 'floor'), [])
        self.assertTrue(self.landings(scene, feetAllowed=lambda block: block['id'] != 'pillar'))
        self.assertEqual(self.landings(scene, supportAllowed=lambda block: block['id'] != 'floor',
                                       feetAllowed=lambda block: True), [])

    def test_real11_run_tick_landing_drops_the_rectangle_it_cannot_reach(self):
        import json
        from tools.paths import STANDALONE_ROOT
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.movement.program import boundary_state, empty_program
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.scene.normalize import prepare_scene
        scene = prepare_scene(json.loads((STANDALONE_ROOT/'cases/real11/physical-anchors.json').read_text())['request'])
        program = empty_program(scene['initial'])
        def b1(gate=None):
            context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=8,
                           threshold=0., regionItems=True, interactionGate=gate)
            choice, = [c for c in normal_choices(context) if c.get('terminalSupport') == 'b1']
            event, = choice['profile']['contactEvents']
            return event['regions'], choice['profile'].get('regionChoices', [])
        regions, items = b1()
        self.assertEqual((len(regions), len(items)), (2, 1))
        with PythonCompiler() as compiler:
            reached, items = b1(InteractionGate(scene, compiler))
        self.assertEqual(items, [])
        self.assertEqual(len(reached), 1)
        self.assertIn(reached[0], regions)
        self.assertGreaterEqual(reached[0][0], 742.3)

    def test_one_long_approach_answers_every_shorter_approach_exactly(self):
        import json
        from unittest.mock import patch
        from tools.paths import STANDALONE_ROOT
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.movement import normal
        from parkour_solver.movement.program import append_choice, boundary_state, empty_program
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.scene.normalize import prepare_scene
        scene = prepare_scene(json.loads((STANDALONE_ROOT/'cases/real11/physical-anchors.json').read_text())['request'])
        root = empty_program(scene['initial'], threshold=0.)
        horizon = 24
        def context(program):
            return dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=horizon,
                        threshold=0., interactionGate=None)
        with PythonCompiler() as compiler:
            gate = InteractionGate(scene, compiler)
            landed = next(c for c in normal.normal_choices(context(root)) if c.get('terminalSupport') == 'b1')
            prefixes = [root, append_choice(root, landed)]
            for prefix in prefixes:
                start = prefix['profile']['airtime']
                for jumping in (False, True):
                    ctx = context(prefix)
                    def envelope(inputs):
                        return gate.envelopes(append_choice(prefix, normal.event_choice(ctx, 'normal', ctx['state'], inputs,
                                                                                         jumping=jumping)))
                    long = envelope(horizon)
                    for inputs in (1, 5, 12, horizon-1):
                        short = envelope(inputs)
                        end = start+inputs
                        self.assertEqual(short['states'], long['states'][:end+1])
                        self.assertEqual(short['phases'], long['phases'][:end])
                        self.assertEqual(short['radii'], long['radii'][:end+1])
            ctx = dict(context(root), interactionGate=gate)
            far = [10000., 0., 10000., 10001., 1., 10001.]
            self.assertTrue(gate.query(ctx, far, 3, label=dict(owner='far', role='test'), jumping=True)['ruledOut'])
            self.assertNotIn('approachFallbacks', gate.metrics)
            builds = gate.metrics['approachBuilds']
            other = dict(context(root), interactionGate=gate)
            self.assertTrue(gate.query(other, far, 5, label=dict(owner='far', role='test'), jumping=True)['ruledOut'])
            self.assertEqual(gate.metrics['approachBuilds'], builds)
            original = normal.event_choice
            def no_long(context, kind, state, inputs, **kwargs):
                return original(context, kind, state, inputs, **kwargs) if inputs == 4 else None
            with patch('parkour_solver.movement.normal.event_choice', side_effect=no_long):
                fresh_gate = InteractionGate(scene, compiler)
                fresh = dict(context(root), interactionGate=fresh_gate)
                self.assertTrue(fresh_gate.query(fresh, far, 3, label=dict(owner='far', role='test'), jumping=True)['ruledOut'])
            self.assertEqual(fresh_gate.metrics['approachFallbacks'], 1)

    def test_exact_height_slot_keeps_its_floor(self):
        from parkour_solver.movement.contact_combinations import scene_contact_options
        from parkour_solver.scene.geometry import BODY_HEIGHT
        scene = box_scene([[0, 0, 0, 1, 1, 1], [0, 1 + BODY_HEIGHT, 0, 1, 3, 1]], [('floor', 1), ('roof', 3)])
        option, = scene_contact_options(scene, 1, 1, members=[0])
        self.assertEqual(len(option.regions), 1)

    def test_floor_checks_follow_each_exposed_rectangle_of_a_clipped_top(self):
        scene = box_scene([[0, 0, 0, 2, 1, 2], [2, 0, 1, 3, 3, 2]], [('floor', 1), ('pillar', 3)])
        profile = dict(airtime=2, position=[1.5, 1.2, .9], velocity=[-.1, -.2, -.3], jump=[False, False], sneak=[False, False])
        start = dict(id='start', top=5)
        checks = [c for c in floor_checks(dict(scene, blocks=scene['blocks'] + [start]), ['start', 'start'], [profile])
                  if c['block']['id'] == 'floor' and c['reason'] == 'earlier_floor']
        self.assertEqual(len(checks), 2)
        plan = dict(events=[dict(jump=0)])
        inside = dict(positions=[[0, 0], [1, 1.5], [0, 0]])
        notch = dict(positions=[[0, 0], [2.0, 1.5], [0, 0]])
        self.assertEqual(len(contact_conflicts(checks, plan, inside)), 1)
        self.assertEqual(contact_conflicts(checks, plan, notch), [])
        conflict, = contact_conflicts(checks, plan, inside)
        rows = avoid_contact_rows(conflict, 1e-6)
        region = conflict['region']
        self.assertEqual({(r['mode'], r['cmp'], r['rhs']) for r in rows},
                         {('X', 'LE', region[0] - 1e-6), ('X', 'GE', region[2] + 1e-6),
                          ('Z', 'LE', region[1] - 1e-6), ('Z', 'GE', region[3] + 1e-6)})


def boxes_request(initial, velocity, yaw, **blocks):
    return dict(version=1, sceneModel='collision_boxes', geometryBounds=[-10,-6,-10,20,12,10],
                initial=dict(position=initial, velocity=velocity, yaw=yaw), startBlock='start', goalBlock='goal',
                maxWallHits=0, maxContactCombinations=0, blocks=[dict(id=k, boxes=[v]) for k, v in blocks.items()],
                movementSearch=dict(types=['normal'], maxInputs=30))


class WalkOffFaceTests(unittest.TestCase):
    def faces(self, landing, x=(-9., 9.), z=(-9., 9.), **blocks):
        """walk_off_faces at tick 1 after landing on `start` (top 1) at tick 0, under a box envelope at state 1."""
        from parkour_solver.constraints.contacts import walk_off_faces
        scene = prepare_scene(boxes_request([1, 1, 1], [0, -.0784000015258789, 0], 0, start=[0, 0, 0, 2, 1, 2],
                                            goal=[8, -1, 8, 9, 0, 9], **blocks))
        solids = scene['collisionScene']['solids']
        start = next(b for b in scene['blocks'] if b['id'] == 'start')
        program = dict(profile=dict(grounded=[True, True], ladderTicks=[False, False], position=[1, 1], contactEvents=[
            dict(offset=0, role='landing', boxes=[solids[start['solidIndex']]])]))
        state = dict(X=list(x), Z=list(z))
        envelopes = dict(states=[state]*2, phases=[dict(Y=dict(state, Y=[-math.inf, math.inf]))]*2)
        faces = walk_off_faces(scene, program, 1, envelopes, [solids[b['solidIndex']] for b in scene['blocks']
                                                               if b['id'] == landing])
        if faces is not None:
            faces['faces'] = [(r['mode'], r['cmp']) for r in faces['options']]
            faces['ids'] = sorted(b['id'] for b in scene['blocks'] if b['solidIndex'] in faces['solids'])
        return faces

    def test_faces_follow_the_landing_direction_and_skip_blocked_ones(self):
        beyond = dict(low=[4, -1, 1, 6, 0, 3])
        self.assertEqual(self.faces('low', **beyond)['faces'], [('X', 'GE'), ('Z', 'GE'), ('Z', 'LE')])
        self.assertEqual(self.faces('low', wall=[2, 0, 0, 3, 3, 2], **beyond)['faces'], [('Z', 'GE'), ('Z', 'LE')])
        self.assertEqual(self.faces('low', wall=[2, 0, 0, 3, 3, 1], **beyond)['faces'],
                         [('X', 'GE'), ('Z', 'GE'), ('Z', 'LE')])
        walls = dict(east=[2, 0, -1, 3, 3, 3], north=[-1, 0, 2, 3, 3, 3], south=[-1, 0, -1, 3, 3, 0])
        self.assertEqual(self.faces('low', **walls, **beyond)['faces'], [('X', 'LE')])
        self.assertEqual(self.faces('low', west=[-1, 0, -1, 0, 3, 3], **walls, **beyond)['faces'], [])
        diagonal = dict(low=[3, -1, 3, 5, 0, 5])
        faces = self.faces('low', **diagonal)
        self.assertEqual((faces['faces'], faces['diagonal']), ([('X', 'GE'), ('Z', 'GE')], True))
        self.assertEqual(self.faces('low', east=walls['east'], north=walls['north'], **diagonal)['faces'],
                         [('X', 'LE'), ('Z', 'LE')])
        faces = self.faces('floor', x=(-1., 3.2), z=(-1., 2.6), floor=[-2, -1, -2, 4, 0, 4])
        self.assertEqual((faces['faces'], faces['diagonal']), ([('Z', 'LE'), ('X', 'GE'), ('X', 'LE'), ('Z', 'GE')], False))
        self.assertEqual(self.faces('low', x=(.5, 1.5), z=(.5, 1.5), **beyond)['faces'], [])

    def test_the_support_is_the_launch_component_and_must_tile_a_rectangle(self):
        north = dict(low=[0, -1, 3, 2, 0, 5])
        self.assertEqual(self.faces('low', far=[3, 0, 0, 4, 1, 2], **north)['ids'], ['start'])
        faces = self.faces('low', ext=[2, 0, 0, 3, 1, 2], **north)
        self.assertEqual((faces['ids'], faces['region'][2]), (['ext', 'start'], 3+HALF_WIDTH))
        self.assertIsNone(self.faces('low', ext=[2, 0, 0, 3, 1, 1], **north))

    def test_runoff_scene_walks_off_once_the_edge_is_reachable(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.movement.program import append_choice, boundary_state, empty_program
        from parkour_solver.reachability.service import InteractionGate
        scene = prepare_scene(boxes_request([.5, 1, .5], [.25, -.0784000015258789, 0], -90, start=[-2, 0, -2, 1, 1, 2],
                                            middle=[1, -1, -2, 4, 0, 2], goal=[6, -1, -1, 7, 0, 1]))
        start = next(b['solidIndex'] for b in scene['blocks'] if b['id'] == 'start')
        with PythonCompiler() as compiler:
            gate = InteractionGate(scene, compiler)
            def choices(program, gated=True, **match):
                context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=30,
                               threshold=0., interactionGate=gate if gated else None)
                return [c for c in normal_choices(context) if all(c.get(k) == v for k, v in match.items())
                        and not c['profile']['jump'][0]]
            program, generated = empty_program(scene['initial'], threshold=0.), []
            for k in range(4):
                generated.append([append_choice(program, c) for c in choices(program, terminalSupport='middle')])
                if k == 1:
                    ungated, = [append_choice(program, c) for c in choices(program, False, terminalSupport='middle')]
                program = append_choice(program, choices(program, False, terminalSupport='start')[0])
            self.assertEqual([len(g) for g in generated], [0, 0, 0, 1])
            self.assertEqual(sorted(e['input'] for e in gate.exclusions if e['proof']['reason'] == 'launch_support_not_left'),
                             [0, 1, 2])
            self.assertEqual(gate.check_program(ungated)['reason'], 'launch_support_not_left')
            runoff, = generated[3]
            self.assertIsNone(gate.check_program(runoff))
            selection, = runoff['walkOffs']
            row, = selection['options']
            self.assertEqual((selection['offset'], selection['solids'], row['mode'], row['cmp'], row['offset']),
                             (3, [start], 'X', 'GE', 3))

    def test_stepping_flight_cases_from_a_run_off_wait_for_the_edge_too(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.movement.stepping import open_stepping_choices
        from parkour_solver.movement.program import append_choice, boundary_state, empty_program
        from parkour_solver.reachability.service import InteractionGate
        request = dict(boxes_request([.5, 1, .5], [.25, -.0784000015258789, 0], -90, start=[-2, 0, -2, 1, 1, 2],
                                     middle=[1, -1, -2, 2, 0, 2], slab=[2, -1, -2, 3, .5, 2], goal=[8, -1, -1, 9, 0, 1]),
                       movementSearch=dict(types=['normal', 'stepping'], maxInputs=30))
        scene = prepare_scene(request)
        with PythonCompiler() as compiler:
            gate = InteractionGate(scene, compiler)
            program, offered = empty_program(scene['initial'], threshold=0.), []
            for k in range(4):
                context = lambda g: dict(mode='open', scene=scene, program=program, state=boundary_state(program),
                                         maxInputs=30, threshold=0., interactionGate=g)
                offered.append([bool([c for c in open_stepping_choices(context(g)) if not c['profile']['jump'][0]])
                                for g in (None, gate)])
                program = append_choice(program, next(c for c in normal_choices(context(None))
                                                      if c.get('terminalSupport') == 'start' and c['profile']['airtime'] == 1))
            self.assertEqual(offered, [[True, False]]*3+[[True, True]])
            self.assertEqual(sorted(e['input'] for e in gate.exclusions
                                    if e['proof']['reason'] == 'launch_support_not_left' and e['owner'] == 'middle'), [0, 1, 2])

    def test_a_diagonal_tie_carries_both_facing_faces(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.movement.program import append_choice, boundary_state, empty_program
        from parkour_solver.reachability.service import InteractionGate
        scene = prepare_scene(boxes_request([.5, 1, .5], [0, -.0784000015258789, 0], -45, start=[-2, 0, -2, 1, 1, 1],
                                            corner=[1.5, -1, 1.5, 4, 0, 4], goal=[7, -1, 7, 8, 0, 8]))
        with PythonCompiler() as compiler:
            gate = InteractionGate(scene, compiler)
            program = empty_program(scene['initial'], threshold=0.)
            def choices(support):
                context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=30,
                               threshold=0., interactionGate=None)
                return [c for c in normal_choices(context) if c.get('terminalSupport') == support and not c['profile']['jump'][0]]
            for _ in range(4):
                program = append_choice(program, choices('start')[0])
            runoff = append_choice(program, choices('corner')[0])
            self.assertIsNone(gate.check_program(runoff))
            selection, = runoff['walkOffs']
            self.assertEqual([(r['mode'], r['cmp']) for r in selection['options']], [('X', 'GE'), ('Z', 'GE')])
            analysis = gate.analyze(runoff)
            for row in selection['options']:
                self.assertNotEqual(gate.relaxation(runoff, analysis, scene['objective'], [row])['outcome'], 'infeasible')


class BlockTableTests(unittest.TestCase):
    def test_far_block_is_excluded_and_an_adjacent_one_kept(self):
        from parkour_solver.reachability.service import InteractionGate
        scene = box_scene([[0, 0, 0, 1, 1, 1], [1, 0, 3, 2, 1, 4], [100, 0, 0, 101, 1, 1], [0, 30, 0, 1, 31, 1]],
                          [('start', 1), ('near', 1), ('far', 1), ('high', 31)])
        table = InteractionGate(scene, None).reachable_blocks('start')
        self.assertEqual(table['blocks'], {'start', 'near'})
        self.assertEqual(table['solids'], {0, 1})

    def test_real11_extended_table_kinds_for_b5(self):
        import json
        from tools.paths import STANDALONE_ROOT
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.scene.normalize import prepare_scene
        request = json.loads((STANDALONE_ROOT/'cases/real11/extended-anchors.json').read_text())['request']
        scene = prepare_scene(request)
        table = InteractionGate(scene, None).reachable_blocks('b5')
        solid = {b['id']: b['solidIndex'] for b in scene['blocks']}
        self.assertEqual(table['landing'], {'b5', 'b7', 'b11', 'b12', 'b13#0', 'b13#1', 'b18'})
        self.assertEqual(table['stepping']-table['landing'], {'b19'})
        self.assertEqual(table['ceilingSolids'], {solid[n] for n in ('b8', 'b9a', 'b9b', 'b10', 'b22')})
        self.assertEqual(table['blocks'], {'b4', 'b5', 'b6', 'b7', 'b8', 'b9a', 'b9b', 'b10', 'b11', 'b12', 'b13#0',
                                           'b13#1', 'b14', 'b18', 'b19', 'b20', 'b22'})

    def assert_table_keeps_every_choice(self, scene, roots):
        from tests.support.block_table import compiler, generated, nodes
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.search.anchors.policy import launch
        removed = 0
        with compiler() as java:
            gate = InteractionGate(scene, java, seconds=10)
            for node in nodes(scene, roots, gate):
                _, owner = launch(scene, node)
                plain = [[(c['profile'], c.get('terminalSupport')) for c in kind]
                         for kind in generated(scene, node, gate, None)]
                tabled = [[(c['profile'], c.get('terminalSupport')) for c in kind]
                          for kind in generated(scene, node, gate, owner)]
                self.assertEqual(tabled, plain, (owner, node['profile']['airtime']))
                removed += len(scene['blocks'])-len(gate.reachable_blocks(owner)['blocks'])
        return removed

    def test_table_keeps_every_real11_choice(self):
        from tests.support.block_table import real11_inputs
        for scene, roots in real11_inputs():
            self.assertGreater(self.assert_table_keeps_every_choice(scene, roots), 0)

    def test_table_keeps_every_stepping_fixture_choice(self):
        from tests.support.stepping_cases import FIXTURES, fixture
        from parkour_solver.movement.program import empty_program
        from parkour_solver.scene.normalize import prepare_scene
        for name in sorted(p.stem for p in FIXTURES.glob('*.json')):
            with self.subTest(name=name):
                scene = prepare_scene(fixture(name)['request'])
                self.assert_table_keeps_every_choice(scene, [empty_program(scene['initial'], threshold=0.)])

    def test_table_keeps_every_choice_in_random_box_scenes(self):
        import random
        from tests.support.block_table import random_box_request
        from parkour_solver.movement.program import empty_program
        from parkour_solver.scene.normalize import prepare_scene
        rng = random.Random(20261118)
        removed = 0
        for index in range(20):
            with self.subTest(scene=index):
                scene = prepare_scene(random_box_request(rng))
                removed += self.assert_table_keeps_every_choice(scene, [empty_program(scene['initial'], threshold=0.)])
        self.assertGreater(removed, 0)

class RunTickChoiceTests(unittest.TestCase):
    def test_withheld_run_tick_choices_are_exactly_the_grounded_no_jump_ones(self):
        from tests.support.block_table import compiler, nodes, real11_inputs
        from tests.support.stepping_cases import FIXTURES, fixture
        from parkour_solver.movement.generation import generate_choices
        from parkour_solver.movement.program import boundary_state, empty_program
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.scene.normalize import prepare_scene
        cases = real11_inputs()+[(prepare_scene(fixture(name)['request']), None)
                                 for name in sorted(p.stem for p in FIXTURES.glob('*.json'))]
        withheld, airborne = set(), 0
        with compiler() as java:
            for scene, roots in cases:
                gate = InteractionGate(scene, java, seconds=10)
                for node in nodes(scene, roots or [empty_program(scene['initial'], threshold=0.)], gate):
                    grounded = boundary_state(node)['onGround']
                    airborne += not grounded
                    for kind in ('normal', 'stepping', 'ceiling'):
                        def choices(**flag):
                            context = dict(mode='open', scene=scene, program=node, state=boundary_state(node),
                                           maxInputs=40, threshold=0., interactionGate=gate, **flag)
                            return [(c['profile'], c.get('terminalSupport')) for c in generate_choices(kind, context)]
                        every = choices()
                        kept = [c for c in every if not grounded or c[0]['jump'][0]]
                        self.assertEqual(choices(runTickChoices=False), kept, (kind, node['profile']['airtime']))
                        withheld |= {(kind, e['case']) for c in every if c not in kept
                                     for e in c[0].get('caseEvents', [])} | {(kind, None) for c in every if c not in kept}
        self.assertGreater(airborne, 0)
        self.assertLessEqual({('normal', None), ('stepping', 'normal_step')}, withheld)

    def test_advantageous_run_ticks_stop_before_a_flat_jump_could_land(self):
        from parkour_solver.movement.normal import FLAT_JUMP_TICKS, MAX_ADVANTAGEOUS_RUN_TICKS, first_descent, replay
        from parkour_solver.physics.vertical import REST_CARRY
        rest = dict(position=0., velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)
        self.assertEqual(first_descent(replay(rest, 30, jumping=True, threshold=0.), 0.)+1, FLAT_JUMP_TICKS)
        self.assertEqual((FLAT_JUMP_TICKS, MAX_ADVANTAGEOUS_RUN_TICKS), (12, 11))


if __name__ == '__main__':
    unittest.main()
