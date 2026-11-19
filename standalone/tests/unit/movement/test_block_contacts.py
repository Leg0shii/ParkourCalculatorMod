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


class BlockTableTests(unittest.TestCase):
    def test_far_block_is_excluded_and_an_adjacent_one_kept(self):
        from parkour_solver.reachability.service import InteractionGate
        scene = box_scene([[0, 0, 0, 1, 1, 1], [1, 0, 3, 2, 1, 4], [100, 0, 0, 101, 1, 1], [0, 30, 0, 1, 31, 1]],
                          [('start', 1), ('near', 1), ('far', 1), ('high', 31)])
        table = InteractionGate(scene, None).reachable_blocks('start')
        self.assertEqual(table['blocks'], {'start', 'near'})
        self.assertEqual(table['solids'], {0, 1})

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

if __name__ == '__main__':
    unittest.main()
