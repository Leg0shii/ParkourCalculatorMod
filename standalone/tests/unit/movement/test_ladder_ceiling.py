from tests.support.paths import AUTONOMY_DATA
import copy
import json
import os
from pathlib import Path
import unittest

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.movement.ceiling_profiles import ceiling_contact, ceiling_opportunities
from parkour_solver.movement.contact_profiles import contact_opportunities
from parkour_solver.movement.ladder.ceiling import own_ceiling_contacts, own_schedule_ceilings, predict_ceiling_sequences
from parkour_solver.scene.ladders import ladder_regions, climb_walls, entry_channels, exit_faces
from parkour_solver.movement.program import empty_program, boundary_state, append_choice, compile_program, movement_choice
from parkour_solver.movement.contacts import contact_variants, mixed_schedule_pool, contact_signature
from parkour_solver.movement.generation import generate_choices
from parkour_solver.movement.units import ladder_units, shifted_unit
from parkour_solver.movement.ladder.generation import ladder_event_choice
from parkour_solver.movement.normal import event_choice, replay
from parkour_solver.movement.vertical_profiles import trace_ladder_schedule
from parkour_solver.physics.vertical import CLIMB_CARRY, REST_CARRY, f32


ROOT = AUTONOMY_DATA


def climbing_case(beginning=False, ending=False):
    request = json.loads((ROOT / 'fixtures/ladder-climbing-ceiling.json').read_text())
    scene = prepare_scene(request)
    column = ladder_regions(request['climbableCells'])[0]
    wall = next(w for w in climb_walls(column, scene['collisionScene']['solids'])
                if w['axis'] == 'X' and w['direction'] == 1)
    hits = [not beginning, True, not ending, False]
    finite = [ceiling_contact(scene['collisionScene']['solids'][2], t) for t in ([0] if beginning else [0, 1])]
    trace = trace_ladder_schedule(dict(position=2, velocity=REST_CARRY, onGround=True),
        [True] * 4 + [False], [False] * 4, hits, jump=[f32(.42), None, None, None],
        contacts=[dict(state=e['offset'] + 1, plane=e['plane'], direction=1) for e in finite], stop_on_landing=False)
    schedule = dict(ticks=4, entryState=0, exitState=4, exitKind='side', hit=hits, sneak=[False] * 4,
        ladderTicks=[True] * 4 + [False], climbWindow=[0, 3], beginningStop=beginning, endStop=ending,
        stopInputs=[t for t in range(3) if not hits[t]], trace=trace, contactEvents=finite)
    event = dict(schedule=schedule, column=column, wall=wall, entryFace=entry_channels(column, wall)[0],
                 exitFace=next(f for f in exit_faces(column, [.5, 2]) if f['axis'] == 'Z' and f['direction'] == 1))
    choice = ladder_event_choice(event)
    if choice is None:
        raise AssertionError('The climbing ceiling schedule was rejected')
    return scene, event, choice


def complete_case(beginning=False, ending=False):
    scene, event, choice = climbing_case(beginning, ending)
    program = append_choice(empty_program(scene['initial']), choice)
    context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=30)
    finish = next(c for c in generate_choices('normal', context) if c.get('terminalSupport') == 'goal')
    return scene, event, append_choice(program, finish)


class LadderCeilingTests(unittest.TestCase):
    def test_ownership_is_idempotent_finite_and_does_not_rewrite_controls_or_physics(self):
        for beginning in (False, True):
            for ending in (False, True):
                _, event, choice = climbing_case(beginning, ending)
                original = copy.deepcopy(event['schedule'])
                owned = own_schedule_ceilings(original, 'ladder')
                self.assertEqual(original, event['schedule'])
                self.assertEqual(owned, own_schedule_ceilings(owned, 'ladder'))
                for key in ('trace', 'hit', 'sneak', 'stopInputs', 'climbWindow'):
                    self.assertEqual(owned[key], original[key])
                contacts, units = own_ceiling_contacts(choice['profile']['contactEvents'], choice['units'])
                self.assertEqual((contacts, units), own_ceiling_contacts(contacts, units))
                ceilings = [c for c in contacts if c['axis'] == 'Y']
                self.assertEqual(len(ceilings), 1 if beginning else 2)
                self.assertTrue(all(c['role'] == 'ceiling' and c['effect'] == 'ladder_ceiling' for c in ceilings))
                self.assertEqual(sum(len(u.get('effects', [])) for u in units), len(ceilings))
                self.assertTrue(all(u['type'] == 'ladder_climb' for u in units))

    def test_only_climbing_successors_are_accepted_and_stops_stay_inside_climbing(self):
        contact = ceiling_contact([-1, 4, -1, 1, 5, 1], 0)
        first = dict(type='ladder_climb', start=0, end=1, hitInputs=[], stopInputs=[0])
        for kind in ('normal', 'stepping', 'ceiling', 'ladder_fall', None):
            units = [first] + ([dict(type=kind, start=1, end=2)] if kind else [])
            with self.subTest(successor=kind), self.assertRaises(ValueError):
                own_ceiling_contacts([contact], units)
        contacts, units = own_ceiling_contacts([contact], [dict(first, end=2, stopInputs=[0, 1])])
        self.assertEqual(contacts[0]['successorType'], 'ladder_climb')
        self.assertEqual(units[0]['hitInputs'], [])
        self.assertEqual(units[0]['stopInputs'], [0, 1])
        with self.assertRaises(ValueError):
            own_ceiling_contacts(contacts, [dict(type='normal', start=0, end=2)])

    def test_actual_clip_and_unclipped_endpoint_stops_produce_distinct_heights(self):
        traces = {(b, e): climbing_case(b, e)[1]['schedule']['trace'] for b in (False, True) for e in (False, True)}
        plane = 4 - f32(1.8)
        carry1 = (CLIMB_CARRY - .08) * f32(.98)
        carry2 = (carry1 - .08) * f32(.98)
        for (beginning, ending), trace in traces.items():
            with self.subTest(beginning=beginning, ending=ending):
                low = plane + REST_CARRY if beginning else plane
                y3 = low + CLIMB_CARRY
                expected = [2, plane, low, y3, y3 + (carry1 if ending else CLIMB_CARRY)]
                for actual, y in zip(trace['position'], expected):
                    self.assertAlmostEqual(actual, y)
                self.assertEqual(trace['velocity'][1], REST_CARRY if beginning else CLIMB_CARRY)
                self.assertEqual(trace['velocity'][2], CLIMB_CARRY)
                self.assertEqual(trace['velocity'][3], carry1 if ending else CLIMB_CARRY)
                self.assertEqual(trace['velocity'][4], carry2 if ending else carry1)
                self.assertEqual(trace['climbRefreshed'], [not beginning, True, not ending, False])
                self.assertFalse(any(trace['sneak']))
        self.assertLess(traces[False, True]['position'][-1], traces[False, False]['position'][-1])
        self.assertLess(traces[True, False]['position'][-1], traces[False, False]['position'][-1])

    def test_repeated_prediction_includes_prefixes_and_rejects_last_climb_input(self):
        _, event, _ = climbing_case()
        schedule = copy.deepcopy(event['schedule'])
        box = [-1, 4, -1, 1, 5, 1]
        sequences = predict_ceiling_sequences(schedule, box)
        self.assertEqual([[c['offset'] for c in sequence] for sequence in sequences], [[0, 1, 2], [0], [0, 1]])
        self.assertEqual([[c['offset'] for c in s] for s in predict_ceiling_sequences(schedule, box, start=1, end=3)],
                         [[1, 2], [1]])
        self.assertTrue(all(c['offset'] != 3 for s in sequences for c in s))
        generated = contact_variants([box], 4, schedules=[schedule, schedule], max_events=1, max_variants=32)
        signatures = [contact_signature(s) for s in generated['variants']]
        self.assertEqual(len(signatures), len(set(signatures)))
        self.assertTrue(any(len(s) == 3 and all(c['axis'] == 'Y' for c in s) for s in generated['variants']))

    def test_neutral_ceiling_provider_preserves_provenance_and_existing_normal_contacts(self):
        box = [-1, 4, -1, 1, 5, 1]
        shared = list(ceiling_opportunities([box], 2))
        old = list(contact_opportunities([box], 2, horizontal=False))
        self.assertEqual(shared, old)
        shared[0]['box'][0] = -2
        self.assertEqual(box[0], -1)
        plain = old[0]
        self.assertEqual(own_ceiling_contacts([plain], [dict(type='normal', start=0, end=2)])[0], [plain])

    def test_regeneration_owns_the_changed_exit_and_rejects_direct_fall_successor(self):
        from tests.support.movement_contacts import small_request
        from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
        request = small_request()
        baseline = generate_ladder_schedules(request)['schedules']
        box = [-1, .95 + f32(1.8), -1, 2, 3.5, 2]
        mixed = mixed_schedule_pool(request, [box], schedules=baseline, max_variants=16)
        schedule = next(s for s in mixed['schedules'] if s['exitState'] == 3 and not any(s['sneak'])
                        and [(e['offset'], e['axis']) for e in s['contactEvents']] == [(1, 'Y')])
        self.assertEqual(schedule['ladderCeilingEvents'][0]['offset'], 1)
        self.assertEqual(schedule['ladderCeilingEvents'][0]['successorType'], 'ladder_climb')
        self.assertEqual(schedule['trace']['velocity'][2], CLIMB_CARRY)
        phases = ladder_units(schedule)
        self.assertTrue(any(u['type'] == 'ladder_climb' and u['start'] <= 2 < u['end'] for u in phases))
        schedule = copy.deepcopy(schedule)
        schedule['contactEvents'].append(ceiling_contact(box, 2))
        with self.assertRaises(ValueError):
            own_schedule_ceilings(schedule)

    def test_composed_entry_ceiling_transfers_ownership_without_duplicate_contact_or_effect(self):
        initial = dict(position=1, velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)
        wall = dict(leg=0, offset=0, axis='X', direction=1, plane=.7, box=[1, 0, 0, 2, 3, 1], role='contact')
        ceiling = ceiling_contact([-1, 3, -1, 2, 4, 2], 0)
        approach = event_choice({}, 'ceiling', initial, 1, jumping=True, finite=[wall, ceiling],
                                vertical=[dict(state=1, plane=ceiling['plane'], direction=1)])
        program = append_choice(empty_program(initial), approach)
        before = copy.deepcopy(program)
        state = boundary_state(program, True)
        self.assertEqual(state['velocity'], CLIMB_CARRY)
        trace = replay(state, 1, ladder=[True, False])
        column = dict(id='entry', x=0, z=0, minY=1, maxY=2)
        event = dict(column=column, wall=dict(mode='fall_only', axis='X', direction=1, plane=.5, parts=[]),
            entryFace=dict(family='side', channel='side:Z:-1', axis='Z', direction=-1, plane=0),
            exitFace=dict(axis='Z', direction=1, plane=1),
            schedule=dict(ticks=1, entryState=0, exitState=1, exitKind='side', hit=[False], sneak=[False],
                          ladderTicks=[True, False], trace=trace))
        child = movement_choice('ladder_fall', state, trace,
            units=[dict(type='ladder_fall', start=0, end=1, occupiedStates=[0, 1], sneakInputs=[])], ladder_events=[event])
        combined = append_choice(program, child)
        self.assertIsNotNone(combined)
        self.assertEqual(program, before)
        self.assertEqual(combined['profile']['ladderTicks'], [False, True, False])
        self.assertEqual(combined['profile']['velocity'][1], CLIMB_CARRY)
        self.assertEqual(combined['profile']['position'][1], ceiling['plane'])
        self.assertEqual(len([c for c in combined['profile']['contactEvents'] if c['axis'] == 'Y']), 1)
        unit = combined['movementUnits'][0]
        self.assertEqual(unit['type'], 'ladder_climb')
        self.assertEqual([e['type'] for e in unit['effects']], ['ladder_ceiling'])
        self.assertEqual(combined['ladderEvents'][0]['schedule']['ladderCeilingEvents'][0]['ownerRegion'], 'entry')

    def test_second_ladder_contact_effects_and_schedule_offsets_agree_after_retiming(self):
        evidence = json.loads((ROOT / 'experiments/contact-combinations/appended-ladder-ceiling.json').read_text())
        program = empty_program(evidence['route']['initial'])
        cut = 0
        for event in evidence['route']['ladderEvents']:
            event = copy.deepcopy(event)
            event['schedule']['contactEvents'] = [copy.deepcopy(c) for c in evidence['route']['contactEvents']
                if cut <= c['offset'] < event['schedule']['exitState']]
            choice = ladder_event_choice(event, start=cut)
            self.assertIsNotNone(choice)
            local_events = choice['ladderEvents'][0]['schedule']['ladderCeilingEvents']
            program = append_choice(program, choice)
            self.assertIsNotNone(program)
            global_events = program['ladderEvents'][-1]['schedule']['ladderCeilingEvents']
            self.assertEqual([e['offset'] + cut for e in local_events], [e['offset'] for e in global_events])
            cut = program['profile']['airtime']
        owned = [c for c in program['profile']['contactEvents'] if c.get('effect') == 'ladder_ceiling']
        self.assertEqual([c['offset'] for c in owned], [20])
        effects = [e for u in program['movementUnits'] for e in u.get('effects', []) if e['type'] == 'ladder_ceiling']
        self.assertEqual([e['start'] for e in effects], [20])
        self.assertEqual(effects[0]['region'], owned[0]['ownerRegion'])
        shifted = shifted_unit(effects[0], 7)
        self.assertEqual(shifted['start'], 27)
        self.assertEqual(shifted['anchors'], {'hit': 28, 'nextClimb': 28})


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class LadderCeilingNumericalTests(unittest.TestCase):
    def setUp(self):
        from parkour_solver.bridge.compiler import JavaCompiler
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_saved_examples_match_fixture_and_pass_scalar_and_shared_scene_acceptance(self):
        import hashlib
        from parkour_solver.validation.scene import check_geometry
        from parkour_solver.numerics import prepare, validate
        for name in ('continuous', 'beginning-stop', 'end-stop', 'both-stops'):
            path = ROOT / 'experiments/ladder-climbing-ceiling' / (name + '.json')
            with self.subTest(example=path.name):
                evidence = json.loads(path.read_text())
                self.assertEqual(hashlib.sha256((ROOT / evidence['fixture']).read_bytes()).hexdigest(), evidence['fixtureSha256'])
                problem = prepare(evidence['problem'])
                candidate = evidence['candidate']
                checked = validate(problem, candidate['yaw'])
                self.assertTrue(checked['feasible'])
                for key in ('positions', 'velocity', 'y', 'vy', 'onGround'):
                    self.assertEqual(checked[key], candidate[key])
                vertical = dict(vertical=problem['vertical'], **{k: checked[k] for k in ('y', 'vy', 'onGround')})
                self.assertTrue(check_geometry(evidence['geometry'], evidence['plan'], candidate, vertical, self.compiler, 5)['feasible'])
                self.assertEqual(evidence['plan']['ticks'], 10 if evidence['endStop'] else 12)

    def test_fresh_joint_solves_for_climbing_and_each_endpoint_stop_combination(self):
        from parkour_solver.constraints.contacts import floor_checks
        from parkour_solver.search.repair import solve_contact_branches
        from parkour_solver.validation.scene import check_geometry
        results = {}
        for beginning in (False, True):
            for ending in (False, True):
                with self.subTest(beginning=beginning, ending=ending):
                    scene, event, program = complete_case(beginning, ending)
                    compiled = compile_program(scene, program)
                    result = solve_contact_branches(compiled['route'], compiled['profiles'],
                        floor_checks(scene, ['start', 'goal'], compiled['profiles']), self.compiler,
                        seconds=15, candidate_seconds=2, retries=1, geometry=scene['collisionScene'],
                        motion_bound=dict(storedSpeedNorm=2, movementAfterSupportCheck=2),
                        python_executable=os.environ['PKC_IPOPT_PYTHON'])
                    self.assertEqual(result['status'], 'feasible', result.get('termination'))
                    best = result['best']
                    results[beginning, ending] = best
                    self.assertTrue(best['geometryCheck']['feasible'])
                    self.assertEqual(best['plan']['ticks'], 10 if ending else 12)
                    self.assertEqual(best['candidate']['positions'][0], scene['initial']['position'][::2])
                    self.assertEqual(best['candidate']['velocity'][0], scene['initial']['velocity'][::2])
                    self.assertEqual(best['candidate']['y'][:5], event['schedule']['trace']['position'])
                    self.assertFalse(any(best['plan']['sneak']))
                    self.assertTrue(best['candidate']['feasible'])
                    vertical = dict(vertical=best['vertical'], **{k: best['candidate'][k] for k in ('y', 'vy', 'onGround')})
                    for mutation in ('missing', 'mistimed', 'wrong_box', 'extra'):
                        broken = copy.deepcopy(best['plan'])
                        ceilings = [c for c in broken['sceneContacts'] if c['axis'] == 1 and c['direction'] == 1]
                        selected = ceilings[0]
                        if mutation == 'missing':
                            broken['sceneContacts'].remove(selected)
                        elif mutation == 'mistimed':
                            selected['state'] += 1
                        elif mutation == 'wrong_box':
                            selected['box'] = [v + (20 if i in (0, 3) else 0) for i, v in enumerate(selected['box'])]
                        else:
                            broken['sceneContacts'].append(dict(selected, state=3))
                        checked = check_geometry(scene['collisionScene'], broken, best['candidate'], vertical, self.compiler, 5)
                        self.assertFalse(checked['feasible'], mutation)
        self.assertLess(results[False, True]['candidate']['y'][4], results[False, False]['candidate']['y'][4])


if __name__ == '__main__':
    unittest.main()
