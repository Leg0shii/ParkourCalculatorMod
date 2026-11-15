from tests.support.paths import AUTONOMY_DATA
import copy
import unittest

from parkour_solver.movement.program import append_choice, boundary_state, empty_program
from parkour_solver.movement.generation import generate_choices
from parkour_solver.movement.normal import event_choice, named_choice
from parkour_solver.physics.vertical import REST_CARRY, f32


def initial(y=1):
    return dict(position=y, velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)


class GenericCompositionTests(unittest.TestCase):
    def test_repeated_supported_units_do_not_stop_replay_at_first_landing(self):
        program = empty_program(initial())
        for t in range(7):
            context = dict(state=boundary_state(program))
            choice = event_choice(context, 'normal', context['state'], 1,
                vertical=[dict(state=1, plane=1, direction=-1)], support='floor')
            program = append_choice(program, choice)
            self.assertEqual(program['profile']['airtime'], t + 1)
        self.assertEqual(program['profile']['position'], [1] * 8)
        self.assertEqual(program['profile']['grounded'], [True] * 8)
        self.assertEqual(program['profile']['velocity'], [REST_CARRY] * 8)

    def test_unbounded_repeated_event_types_share_the_same_append_contract(self):
        program = empty_program(initial())
        for index in range(5):
            context = dict(state=boundary_state(program))
            y = context['state']['position']
            choice = named_choice(context, 'stepping', context['state'],
                dict(case='normal_step', input=0, plane=y + .5, box=[0, y, 0, 1, y + .5, 1], anchors={'hit': 1}), support=str(index))
            program = append_choice(program, choice)
        self.assertEqual(program['profile']['position'], [1, 1.5, 2, 2.5, 3, 3.5])
        self.assertEqual([u['type'] for u in program['movementUnits']], ['stepping'] * 5)
        self.assertEqual([e['anchors']['hit'] for e in program['profile']['caseEvents']], [1, 2, 3, 4, 5])
        self.assertEqual([c['state'] for c in program['profile']['contacts']], [1, 2, 3, 4, 5])

    def test_grind_can_end_in_air_then_choose_another_movement_type(self):
        program = empty_program(initial())
        context = dict(state=boundary_state(program))
        choice = named_choice(context, 'ceiling', context['state'],
            dict(case='grind', input=0, plane=1, clearance=.1, box=[1, 2.9, 0, 2, 4, 1], anchors={'jump': 1}), jumping=True)
        program = append_choice(program, choice)
        self.assertFalse(boundary_state(program)['onGround'])
        self.assertIsNone(program['terminalSupport'])
        context = dict(state=boundary_state(program))
        program = append_choice(program, event_choice(context, 'normal', context['state'], 1,
            vertical=[dict(state=1, plane=1, direction=-1)], support='floor'))
        context = dict(state=boundary_state(program))
        program = append_choice(program, event_choice(context, 'normal', context['state'], 1, jumping=True))
        self.assertAlmostEqual(program['profile']['position'][-1], 1 + f32(.42))
        self.assertEqual(program['profile']['jump'], [True, False, True])
        self.assertEqual([u['type'] for u in program['movementUnits']], ['ceiling', 'normal', 'normal'])

    def test_wrong_boundary_and_horizon_are_rejected_without_mutating_prefix(self):
        program = empty_program(initial())
        saved = copy.deepcopy(program)
        context = dict(state=initial())
        choice = event_choice(context, 'normal', initial(), 1, jumping=True)
        bad = copy.deepcopy(choice)
        bad['initial']['velocity'] = 1
        with self.assertRaises(ValueError):
            append_choice(program, bad)
        self.assertIsNone(append_choice(program, choice, max_inputs=0))
        self.assertEqual(program, saved)


class GenericOpenGenerationTests(unittest.TestCase):
    def test_all_stepping_cases_end_at_their_event_instead_of_a_forced_landing_tail(self):
        from tests.unit.movement.test_stepping_cases import fixture
        from parkour_solver.search.support_profiles import prepare_scene
        for name in ['normal_step', 'slab_boost', 'blip', 'blip_up', 'corner_blip_up', 'corner_jump_cancel', 'grind']:
            data = fixture(name)
            scene = prepare_scene(data['request'])
            program = empty_program(scene['initial'])
            context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=40, cases=[data['case']])
            kind = 'ceiling' if name == 'grind' else 'stepping'
            choice = next(c for c in generate_choices(kind, context)
                          if any(e['case'] == data['case'] for e in c['profile']['caseEvents']))
            event = choice['profile']['caseEvents'][0]
            self.assertEqual(choice['profile']['airtime'], event['input'] + 1)
            self.assertIsNotNone(append_choice(program, choice))
            if name in ('blip_up', 'corner_blip_up'):
                self.assertTrue(choice['profile']['grounded'][-1])
                self.assertIsNone(choice['terminalSupport'])
            if name in ('slab_boost', 'grind', 'corner_jump_cancel'):
                self.assertEqual(choice['profile']['airtime'], 1)
                self.assertFalse(choice['profile']['grounded'][-1])

    def test_boundary_entry_hit_refreshes_climbing_immediately_and_refines_ownership(self):
        from parkour_solver.movement.program import movement_choice
        from parkour_solver.movement.normal import replay
        from parkour_solver.physics.vertical import CLIMB_CARRY
        program = empty_program(initial())
        wall = dict(leg=0, offset=0, axis='X', direction=1, plane=.7, box=[1,0,0,2,3,1], role='contact')
        choice = event_choice({}, 'normal', initial(), 1, jumping=True, finite=[wall])
        program = append_choice(program, choice)
        original = copy.deepcopy(program)
        state = boundary_state(program, True)
        self.assertEqual(state['velocity'], CLIMB_CARRY)
        trace = replay(state, 1, ladder=[True, False])
        column = dict(id='ladder', x=0, z=0, minY=1, maxY=2)
        event = dict(column=column, wall=dict(mode='fall_only', axis='X', direction=1, plane=.5, parts=[]),
            entryFace=dict(family='side', channel='side:Z:-1', axis='Z', direction=-1, plane=0),
            exitFace=dict(axis='Z', direction=1, plane=1),
            schedule=dict(ticks=1, entryState=0, exitState=1, exitKind='side', hit=[False], sneak=[False],
                          ladderTicks=[True, False], trace=trace))
        units = [dict(type='ladder_fall', start=0, end=1, occupiedStates=[0,1], sneakInputs=[])]
        child = movement_choice('ladder_fall', state, trace, units=units, ladder_events=[event])
        combined = append_choice(program, child)
        self.assertEqual(combined['profile']['ladderTicks'], [False, True, False])
        self.assertEqual(combined['profile']['velocity'][1], CLIMB_CARRY)
        self.assertEqual(combined['profile']['position'][1], 1 + f32(.42))
        self.assertEqual(combined['movementUnits'][0]['type'], 'ladder_climb')
        self.assertEqual(combined['movementUnits'][0]['hitInputs'], [0])
        self.assertEqual(program, original)

    def test_five_existing_ladder_episodes_use_the_generic_composer(self):
        import json
        from pathlib import Path
        from parkour_solver.movement.ladder.generation import ladder_event_choice
        evidence = json.loads((AUTONOMY_DATA / 'experiments/contact-combinations/five-airborne-ladders.json').read_text())
        program = empty_program(evidence['route']['initial'])
        cut = 0
        for event in evidence['route']['ladderEvents']:
            program = append_choice(program, ladder_event_choice(event, start=cut))
            self.assertIsNotNone(program)
            cut = program['profile']['airtime']
        self.assertEqual(len(program['ladderEvents']), 5)
        self.assertEqual(program['profile']['position'], evidence['candidate']['y'][:cut + 1])
        self.assertFalse(program['profile']['grounded'][-1])


class GenericQueueTests(unittest.TestCase):
    def test_new_registered_types_and_cost_order_need_no_pair_specific_queue_logic(self):
        import json
        from pathlib import Path
        from parkour_solver.search.support_profiles import prepare_scene
        from parkour_solver.search.movement import iter_movement_programs, movement_settings
        from parkour_solver.movement.generation import GENERATORS, register_movement_type
        from parkour_solver.movement.units import MOVEMENT_TYPES
        from parkour_solver.movement.program import movement_choice
        from parkour_solver.movement.normal import replay
        request = json.loads((AUTONOMY_DATA / 'fixtures/generic-grind-blip.json').read_text())
        scene = prepare_scene(request)
        class Generator:
            def generate(self, context, **options):
                for duration in (2, 1):
                    trace = replay(context['state'], duration, contacts=[dict(state=t + 1, plane=1, direction=-1)
                                                                        for t in range(duration)])
                    unit = dict(type='test_extension', start=0, end=duration)
                    yield movement_choice('test_extension', context['state'], trace, units=[unit], support=scene['goalBlock'])
        register_movement_type('test_extension', Generator())
        try:
            self.assertIn('test_extension', movement_settings(dict(scene, movementSearch={}))['types'])
            cfg = movement_settings(dict(scene, movementSearch=dict(types=['test_extension'], maxInputs=3)))
            report = {}
            results = list(iter_movement_programs(scene, settings=cfg, report=report))
            self.assertEqual([p['profile']['airtime'] for p in results], [1, 2])
            self.assertEqual(report['termination'], 'exhausted')
        finally:
            GENERATORS.pop('test_extension')
            MOVEMENT_TYPES.pop('test_extension')

    def test_cancellation_deadlines_and_limits_preserve_unknown_coverage(self):
        import json
        import threading
        import time
        from pathlib import Path
        from parkour_solver.search.support_profiles import prepare_scene
        from parkour_solver.search.movement import iter_movement_programs
        scene = prepare_scene(json.loads((AUTONOMY_DATA / 'fixtures/generic-grind-blip.json').read_text()))
        cancel = threading.Event()
        cancel.set()
        for options, expected in [(dict(cancel_event=cancel), 'cancelled'), (dict(deadline=time.monotonic() - 1), 'deadline')]:
            report = {}
            self.assertEqual(list(iter_movement_programs(scene, report=report, **options)), [])
            self.assertEqual(report['termination'], expected)
            self.assertFalse(report['minimumTicksProven'])


import json
import os
from pathlib import Path
from parkour_solver.search.repair import floor_checks, solve_contact_branches
from parkour_solver.search.support_profiles import prepare_scene, find_blocks
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.program import compile_program


ROOT = AUTONOMY_DATA


def select(program, scene, kind, predicate):
    context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=100 - program['profile']['airtime'])
    choice = next(choice for choice in generate_choices(kind, context) if predicate(choice))
    combined = append_choice(program, choice)
    if combined is None:
        raise AssertionError('Generated choice cannot extend its consumed state')
    return combined


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class GenericNumericalTests(unittest.TestCase):
    def setUp(self):
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def solve(self, scene, program):
        compiled = compile_program(scene, program)
        result = solve_contact_branches(compiled['route'], compiled['profiles'],
            floor_checks(scene, [scene['startBlock'], scene['goalBlock']], compiled['profiles']), self.compiler,
            seconds=15, candidate_seconds=2, retries=1, max_branches=16, geometry=scene['collisionScene'],
            motion_bound=dict(storedSpeedNorm=2, movementAfterSupportCheck=2),
            python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'], 'feasible', result)
        self.assertTrue(result['best']['geometryCheck']['feasible'])
        self.assertEqual(result['best']['candidate']['positions'][0], scene['initial']['position'][::2])
        self.assertEqual(result['best']['candidate']['velocity'][0], scene['initial']['velocity'][::2])
        return result['best']

    def test_cold_shared_queue_discovers_grind_then_airborne_blip(self):
        request = json.loads((ROOT / 'fixtures/generic-grind-blip.json').read_text())
        result = find_blocks(request, self.compiler, seconds=15, route_seconds=3, candidate_seconds=1,
                             retries=0, python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'], 'feasible', result)
        best = result['best']
        self.assertTrue(best['geometryCheck']['feasible'])
        self.assertEqual(best['plan']['ticks'], 5)
        self.assertEqual([u['type'] for u in best['plan']['movementUnits']], ['ceiling', 'normal', 'stepping'])
        self.assertFalse(any(best['candidate']['onGround'][1:-1]))
        self.assertEqual(best['plan']['jump'], [True, False, False, False, False])
        self.assertFalse(result['minimumTicksProven'])

    def test_repeated_ceiling_type_with_blip_and_ground_movement_between(self):
        scene = prepare_scene(json.loads((ROOT / 'fixtures/generic-repeat-ceiling.json').read_text()))
        program = empty_program(scene['initial'])
        program = select(program, scene, 'ceiling', lambda c: any(e['case'] == 'grind' and e['box'][0] == .85
                                                               for e in c['profile']['caseEvents']))
        program = select(program, scene, 'stepping', lambda c: c.get('terminalSupport') == 'raised'
                         and c['profile']['caseEvents'][0]['case'] == 'blip')
        for _ in range(3):
            program = select(program, scene, 'normal', lambda c: c['profile']['airtime'] == 1
                             and not c['profile']['jump'][0] and c.get('terminalSupport') == 'raised')
        program = select(program, scene, 'ceiling', lambda c: any(e['box'][1] == 2.85 for e in c['profile']['contactEvents']))
        program = select(program, scene, 'normal', lambda c: not c['profile']['jump'][0] and c.get('terminalSupport') == 'goal')
        best = self.solve(scene, program)
        self.assertEqual(best['plan']['ticks'], 12)
        self.assertEqual(sum(u['type'] == 'ceiling' for u in best['plan']['movementUnits']), 2)
        self.assertEqual(best['candidate']['y'][5:9], [.75] * 4)
        self.assertEqual([t for t, jump in enumerate(best['plan']['jump']) if jump], [0, 8])

    def test_two_airborne_ladders_continue_into_blip_without_a_support_reset(self):
        from tests.unit.movement.test_ladder_composition import airborne_case
        from parkour_solver.movement.ladder.generation import ladder_event_choice
        scene, _, previous = airborne_case()
        scene['blocks'].append(dict(id='goal', sourceBlock='goal', solidIndex=3, bounds=[1,2,3,4], top=3.5))
        scene['collisionScene']['solids'].append([1,3,3,2,3.5,4])
        scene['goalBlock'] = 'goal'
        scene = prepare_scene(scene)
        program = empty_program(scene['initial'])
        cut = 0
        for event in previous['ladderEvents']:
            program = append_choice(program, ladder_event_choice(event, start=cut))
            cut = program['profile']['airtime']
        program = select(program, scene, 'stepping', lambda c: c.get('terminalSupport') == 'goal' and
            any(r.get('name') == 'blip:wall_avoid' and r['mode'] == 'Z' and r['cmp'] == 'LE'
                for r in c['profile']['positionConstraints']))
        best = self.solve(scene, program)
        self.assertEqual(len(best['route']['ladderEvents']), 2)
        self.assertEqual(best['plan']['ticks'], 28)
        self.assertFalse(any(best['candidate']['onGround'][1:-1]))
        self.assertEqual(best['plan']['movementUnits'][-1]['type'], 'stepping')

    def test_five_airborne_ladders_share_the_same_generic_route_compilation(self):
        from parkour_solver.movement.ladder.generation import ladder_event_choice
        path = ROOT / 'experiments/contact-combinations/five-airborne-ladders.json'
        evidence = json.loads(path.read_text())
        scene = prepare_scene(json.loads((ROOT / evidence['fixture']).read_text()))
        program = empty_program(scene['initial'])
        cut = 0
        for event in evidence['route']['ladderEvents']:
            program = append_choice(program, ladder_event_choice(event, start=cut))
            cut = program['profile']['airtime']
        program = select(program, scene, 'normal', lambda c: c.get('terminalSupport') == scene['goalBlock'])
        best = self.solve(scene, program)
        self.assertEqual(best['plan']['ticks'], 58)
        self.assertEqual(len(best['route']['ladderEvents']), 5)
        self.assertFalse(any(best['candidate']['onGround'][1:-1]))
