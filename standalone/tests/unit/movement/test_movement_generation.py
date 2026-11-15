import copy
import unittest
from unittest.mock import patch

from parkour_solver.movement.profiles import ordinary_jump_profile, runoff_profile
from parkour_solver.movement.generation import GENERATORS, generate_choices, generate_unit_programs, materialize_generated_units, sneak_domain
from parkour_solver.movement.units import compile_units, movement_program
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules


def context(**options):
    return dict({'ticks': 10, 'entryState': 1, 'exitState': 8, 'exitKind': 'side', 'contactEvents': []}, **options)


class MovementGenerationTests(unittest.TestCase):
    def test_normal_uses_existing_jump_and_runoff_physics(self):
        for target in (-2, 0, .5, 1, 2):
            choices = list(generate_choices('normal', {'mode': 'support', 'sourceTop': 0, 'targetTop': target}))
            expected = [(name, fn(0, target)) for name, fn in [('ordinary', ordinary_jump_profile), ('run_off', runoff_profile)]]
            self.assertEqual([(c['profileKind'], c['profile']) for c in choices], [(name, p) for name, p in expected if p is not None])
            for c in choices:
                self.assertEqual(c['unit']['end'], c['profile']['airtime'])
                self.assertEqual(c['unit']['jumpInputs'], [0] if c['profile']['jump'][0] else [])
                self.assertEqual(c['unit']['exitCondition'], {'kind': 'support', 'top': target})

    def test_composer_uses_registered_generators_and_emits_exact_five_grammars(self):
        with patch.object(GENERATORS['normal'], 'generate', wraps=GENERATORS['normal'].generate) as normal, \
                patch.object(GENERATORS['ladder_climb'], 'generate', wraps=GENERATORS['ladder_climb'].generate) as climb, \
                patch.object(GENERATORS['ladder_fall'], 'generate', wraps=GENERATORS['ladder_fall'].generate) as fall:
            choices = list(generate_unit_programs(context()))
        self.assertTrue(normal.called and climb.called and fall.called)
        types = {tuple(u['type'] for u in c['units'] if u['type'] != 'normal') for c in choices}
        self.assertEqual(types, {('ladder_fall',), ('ladder_climb',), ('ladder_fall','ladder_climb'),
                                ('ladder_climb','ladder_fall'), ('ladder_fall','ladder_climb','ladder_fall')})
        for c in choices:
            masks = compile_units(10, c['units'])
            self.assertEqual(masks['ladderTicks'], [False] + [True]*7 + [False]*3)
            self.assertEqual(masks['hit'], c['hit'])

    def test_front_entry_hit_has_no_forced_falling_phase(self):
        c = next(c for c in generate_unit_programs(context()) if c['climbWindow'] == [0,7] and c['hit'][0])
        self.assertEqual(c['units'][0]['type'], 'ladder_climb')
        self.assertEqual(c['units'][0]['occupiedStates'][0], 1)
        self.assertTrue(c['hit'][0])
        self.assertFalse(compile_units(10,c['units'])['ladderTicks'][0])

    def test_all_four_stop_variants_are_internal_and_omit_last_refresh_hit(self):
        choices = [c for c in generate_unit_programs(context()) if c['climbWindow'] == [0,7]]
        self.assertEqual({(c['beginningStop'],c['endStop']) for c in choices}, {(False,False),(True,False),(False,True),(True,True)})
        for c in choices:
            self.assertEqual([u['type'] for u in c['units']], ['ladder_climb','normal'])
            unit = c['units'][0]
            self.assertEqual(unit['end'], 8)
            self.assertEqual(c['hit'][6], not c['endStop'])
            self.assertFalse(c['hit'][7])
            self.assertEqual(unit['stopInputs'], c['stopInputs'])

    def test_top_ends_climbing_and_bottom_can_end_in_either_phase(self):
        for kind in ('top','bottom','side'):
            choices = list(generate_unit_programs(context(exitKind=kind)))
            ends = {next(u['type'] for u in reversed(c['units']) if u['type'] != 'normal') for c in choices}
            self.assertEqual(ends, {'ladder_climb'} if kind == 'top' else {'ladder_climb','ladder_fall'})

    def test_forced_contacts_are_owned_by_climbing_and_cannot_restart_it(self):
        cfg=context(contactEvents=[{'axis':'Z','offset':2},{'axis':'X','offset':5}])
        choices=list(generate_unit_programs(cfg))
        self.assertTrue(choices)
        for c in choices:
            hits=[t for t, hit in enumerate(compile_units(10,c['units'])['hit']) if hit]
            self.assertIn(2,hits)
            self.assertIn(5,hits)
            self.assertEqual(hits,list(range(min(hits),max(hits)+1)))

    def test_sneak_domain_keeps_falling_and_endpoint_stop_and_first_clamp(self):
        cfg=context()
        choice=next(c for c in generate_unit_programs(cfg) if c['climbWindow']==[0,7] and c['beginningStop'] and c['endStop'])
        domain=sneak_domain(choice['units'],cfg,{t for t,hit in enumerate(choice['hit']) if hit})
        self.assertEqual(domain,[0,1,6,7,8,9])
        materialized=materialize_generated_units(choice['units'],9,[True]*9)
        self.assertEqual(materialized[-1]['end'],9)
        self.assertEqual(compile_units(9,materialized)['sneak'],[True]*9)

    def test_generated_units_are_compiled_and_invalid_edits_are_rejected(self):
        request={'version':1,'initial':{'position':.5,'velocity':0},'region':{'minY':0,'maxY':4},
                 'entries':[{'family':'front','firstCandidateState':1}], 'finishAtExit':True,
                 'maxInputs':8,'entryDelay':0,'exitStates':[6],'exitKinds':['side']}
        s=next(s for s in generate_ladder_schedules(request)['schedules'] if any(s['hit']))
        event={'schedule':s,'column':{'id':'a'}}
        units=movement_program(s['ticks'],[event])
        self.assertEqual(compile_units(s['ticks'],units)['hit'],s['hit'])
        broken=copy.deepcopy(event)
        climb=next(u for u in broken['schedule']['movementUnits'] if u['type']=='ladder_climb')
        climb['hitInputs']=[]
        with self.assertRaisesRegex(ValueError,'disagree'):
            movement_program(s['ticks'],[broken])

    def test_unknown_type_is_rejected(self):
        with self.assertRaises(ValueError):
            list(generate_choices('water',context()))


if __name__ == '__main__':
    unittest.main()
