import copy
import itertools
import unittest

from parkour_solver.movement.ladder.schedules import generate_ladder_schedules, climb_variants
from parkour_solver.movement.units import ladder_units, movement_program, compile_units, exit_matches
from tests.support.ladder_composition import airborne_case
from parkour_solver.movement.ladder.episodes import compose_ladder_episodes
from parkour_solver.physics.vertical import REST_CARRY


def schedule(first=None, end=None, *, entry=1, leave=8, kind='side', stops=()):
    return {'entryState': entry, 'exitState': leave, 'exitKind': kind, 'ticks': 10,
            'hit': [first is not None and first <= t < end and t not in stops for t in range(10)],
            'sneak': [False] * 10, 'climbWindow': [first, end] if first is not None else None}


class MovementUnitTests(unittest.TestCase):
    def test_exactly_five_phase_sequences_and_top_rule(self):
        cases = [(None, None), (0, 7), (2, 7), (0, 5), (2, 5)]
        expected = [('ladder_fall',), ('ladder_climb',), ('ladder_fall', 'ladder_climb'),
                    ('ladder_climb', 'ladder_fall'), ('ladder_fall', 'ladder_climb', 'ladder_fall')]
        for (first, end), types in zip(cases, expected):
            for kind in ('side', 'bottom', 'top'):
                s = schedule(first, end, kind=kind)
                if kind == 'top' and types[-1] != 'ladder_climb':
                    with self.assertRaises(ValueError):
                        ladder_units(s)
                else:
                    self.assertEqual(tuple(u['type'] for u in ladder_units(s)), types)

    def test_all_endpoint_variants_remain_owned_by_climbing(self):
        for variant in climb_variants(0, 7, 10):
            s = dict(schedule(0, 7), **variant)
            units = ladder_units(s)
            self.assertEqual([u['type'] for u in units], ['ladder_climb'])
            self.assertEqual(units[0]['stopInputs'], variant['stopInputs'])
            self.assertEqual(units[0]['end'], 8)
        s = schedule(0, 7, stops=(3,))
        with self.assertRaises(ValueError):
            ladder_units(s)

    def test_exhaustive_hit_masks_allow_one_contiguous_climb_and_endpoint_stops(self):
        for mask in itertools.product((False, True), repeat=7):
            s = schedule()
            s['hit'] = list(mask) + [False] * 3
            indices = [i for i, yes in enumerate(mask) if yes]
            valid = not indices or indices == list(range(indices[0], indices[-1] + 1))
            if valid:
                units = ladder_units(s)
                self.assertLessEqual(sum(u['type'] == 'ladder_climb' for u in units), 1)
            else:
                with self.assertRaises(ValueError):
                    ladder_units(s)

    def test_open_bottom_exit_uses_real_fall_cap_and_no_landing(self):
        request = {'version': 1, 'initial': {'position': .1, 'velocity': -.8},
                   'region': {'minY': 0, 'maxY': 2},
                   'entries': [{'family': 'front', 'firstCandidateState': 0}],
                   'exitKinds': ['bottom'], 'exitStates': [1], 'entryDelay': 0,
                   'finishAtExit': True, 'maxInputs': 6}
        result = generate_ladder_schedules(request)
        self.assertTrue(result['schedules'])
        for s in result['schedules']:
            self.assertEqual(s['ticks'], 1)
            self.assertAlmostEqual(s['trace']['position'][-1], -.05)
            self.assertEqual(s['trace']['movement'], [-.15])
            self.assertEqual(s['terminal'], 'airborne')
            self.assertFalse(any(s['trace']['onGround']))
            self.assertEqual([u['type'] for u in s['movementUnits']], ['ladder_fall'])
        self.assertFalse(exit_matches('bottom', 0, request['region']))
        self.assertTrue(exit_matches('side', 0, request['region']))
        with self.assertRaises(ValueError):
            exit_matches('unknown', 0, request['region'])

    def test_open_prefix_does_not_require_a_reachable_provisional_floor(self):
        _, episodes, complete = airborne_case()
        prefix = compose_ladder_episodes(1, None, episodes[:1], max_inputs=60)
        self.assertEqual(prefix['terminal'], 'airborne')
        self.assertEqual(prefix['profile']['airtime'], 6)
        self.assertEqual(prefix['trace']['position'], complete['trace']['position'][:7])
        self.assertEqual(prefix['trace']['velocity'], complete['trace']['velocity'][:7])
        self.assertFalse(prefix['trace']['onGround'][-1])
        self.assertEqual(sum(prefix['profile']['jump']), 1)
        self.assertIsNone(compose_ladder_episodes(1, 100, episodes[:1], max_inputs=60))

    def test_open_terminal_rejects_a_real_floor_contact_instead_of_labeling_it_airborne(self):
        request={'version':1,'initial':{'position':.1,'velocity':-.8},'region':{'minY':0,'maxY':2},
                 'entries':[{'family':'front','firstCandidateState':0}], 'exitKinds':['side'],
                 'exitStates':[1],'entryDelay':0,'finishAtExit':True,'maxInputs':1,
                 'contactVariants':[[{'leg':0,'offset':0,'axis':'Y','direction':-1,'plane':0,
                                      'box':[0,-1,0,1,0,1],'role':'landing'}]]}
        result=generate_ladder_schedules(request)
        self.assertEqual(result['schedules'],[])
        self.assertGreater(result['stats']['rejected'].get('airborne_terminal_required',0),0)

    def test_unit_materialization_matches_five_phase_occupancy_and_contacts(self):
        for first, end in [(None,None), (0,7), (2,7), (0,5), (2,5)]:
            s = schedule(first, end)
            event = {'schedule': s, 'column': {'id': 'a'}}
            units = movement_program(10, [event])
            compiled = compile_units(10, units)
            self.assertEqual(compiled['ladderTicks'], [False] + [True] * 7 + [False] * 3)
            self.assertEqual(compiled['hit'], s['hit'])
            broken = copy.deepcopy(units)
            broken[0]['start'] = 1
            with self.assertRaises(ValueError):
                compile_units(10, broken)

    def test_two_episode_composition_carries_units_and_shared_masks(self):
        _, _, program = airborne_case()
        units = program['movementUnits']
        self.assertEqual([u['type'] for u in units], ['ladder_climb', 'normal', 'ladder_climb', 'normal'])
        masks = compile_units(program['profile']['airtime'], units)
        self.assertEqual(masks['ladderTicks'], program['profile']['ladderTicks'])
        self.assertEqual(masks['hit'], program['trace']['hit'])
        self.assertNotEqual(program['trace']['velocity'][11], REST_CARRY)


if __name__ == '__main__':
    unittest.main()
