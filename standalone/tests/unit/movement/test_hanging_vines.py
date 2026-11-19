from tests.support.paths import AUTONOMY_DATA
import hashlib
import json
import os
import unittest

from parkour_solver.search.support_profiles import find_blocks
from parkour_solver.bridge.python_compiler import PythonCompiler
from parkour_solver.scene.ladders import entry_channels, movement_contexts, schedule_constraints
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.movement.ladder.transfers import append_ladder_programs, open_program
from parkour_solver.movement.generation import generate_unit_programs
from parkour_solver.movement.units import compile_units
from tests.support.ladder_composition import airborne_case
from parkour_solver.validation.scene import check_geometry
from parkour_solver.numerics import prepare, validate


ROOT = AUTONOMY_DATA


def falling_context():
    column={'id':'vines','x':0,'z':0,'minY':0,'maxY':5}
    return column, movement_contexts(column,[])[0]


class HangingVineTests(unittest.TestCase):
    def test_falling_region_does_not_require_a_wall_or_hide_real_wall_choices(self):
        column,fall=falling_context()
        self.assertEqual(fall['mode'],'fall_only')
        self.assertEqual(fall['parts'],[])
        choices=movement_contexts(column,[[1,0,0,2,5,1]])
        self.assertTrue(any(c.get('mode')!='fall_only' and c['parts'] for c in choices))
        self.assertEqual(choices[-1],fall)
        self.assertEqual(movement_contexts(column,[],include_fall=False),[])

    def test_all_four_horizontal_entry_faces_remain_available(self):
        column,fall=falling_context()
        faces=entry_channels(column,fall)
        self.assertEqual({(f['axis'],f['direction']) for f in faces},{('X',-1),('X',1),('Z',-1),('Z',1)})
        self.assertEqual(len({f['channel'] for f in faces}),4)

    def test_wall_free_generation_keeps_all_sneak_masks_without_phantom_climbing(self):
        request={'version':1,'initial':{'position':1.2,'velocity':-.8},'region':{'minY':0,'maxY':5},
                 'entries':[{'family':'side','firstCandidateState':0}], 'finishAtExit':True,
                 'exitKinds':['side'],'exitStates':[2],'maxInputs':2,'entryDelay':0,'allowPrimaryClimb':False}
        schedules=generate_ladder_schedules(request)['schedules']
        self.assertEqual({tuple(s['sneak']) for s in schedules},{(False,False),(True,False),(False,True),(True,True)})
        for s in schedules:
            self.assertFalse(any(s['hit']))
            self.assertEqual([u['type'] for u in s['movementUnits']],['ladder_fall'])
            self.assertFalse(any(s['trace']['climbRefreshed']))
        self.assertAlmostEqual(next(s for s in schedules if not any(s['sneak']))['trace']['position'][-1],.9)
        request['allowPrimaryClimb']=1
        with self.assertRaises(ValueError):
            generate_ladder_schedules(request)

    def test_a_separately_selected_real_contact_can_still_refresh_climbing(self):
        cfg={'ticks':8,'entryState':1,'exitState':6,'exitKind':'side','allowPrimaryClimb':False,
             'contactEvents':[{'axis':'Z','offset':2}]}
        choices=list(generate_unit_programs(cfg))
        self.assertTrue(choices)
        self.assertTrue(all(not any(c['hit']) for c in choices))
        self.assertTrue(all(compile_units(8,c['units'])['hit'][2] for c in choices))
        cfg['contactEvents']=[]
        cfg['exitKind']='top'
        self.assertEqual(list(generate_unit_programs(cfg)),[])

    def test_falling_constraints_do_not_create_a_wall_plane_or_collision(self):
        column,fall=falling_context()
        s={'entryState':1,'exitState':3,'exitKind':'side','hit':[False]*4,
           'ladderTicks':[False,True,True,False,False],'trace':{'position':[1.2,1.1,1.,.9,.8]}}
        rows,hits,scene=schedule_constraints(s,column,fall,entry_channels(column,fall)[0],{'axis':'Z','direction':1,'plane':1})
        self.assertEqual(hits,[])
        self.assertEqual(scene,[])
        self.assertFalse(any(r.get('name')=='ladder:wall_avoid' for r in rows))
        s['hit'][0]=True
        with self.assertRaisesRegex(ValueError,'finite wall'):
            schedule_constraints(s,column,fall,entry_channels(column,fall)[0],None)

    def test_append_can_add_a_wall_free_falling_region_to_an_airborne_prefix(self):
        _,episodes,program=airborne_case()
        prefix=open_program(program['ladderEvents'][0])
        column=episodes[1]['column']
        fall=movement_contexts(column,[])[0]
        result=append_ladder_programs(prefix,column,fall,toward=[.5,2.5],max_inputs=48,max_entry=6,
                                      max_programs=16,max_contact_variants=0)
        self.assertTrue(result['programs'])
        for p in result['programs']:
            self.assertEqual(p['episodes'][-1]['hitInputs'],[])
            self.assertEqual(sum(p['profile']['jump']),1)
            self.assertEqual(p['trace']['position'][:prefix['profile']['airtime']+1],prefix['trace']['position'])


@unittest.skipUnless(os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_IPOPT_PYTHON')
class HangingVineIntegrationTests(unittest.TestCase):
    def test_cold_discovery_solves_hanging_vines_without_a_climb_wall(self):
        with PythonCompiler() as compiler:
            result=find_blocks(json.loads((ROOT/'fixtures/hanging-vines.json').read_text()),compiler,
                seconds=45,route_seconds=18,candidate_seconds=1,retries=0,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'],'feasible',{'termination':result.get('termination')})
        best=result['best']
        self.assertTrue(best['geometryCheck']['feasible'])
        event=best['route']['ladderEvents'][0]
        self.assertEqual(event['wall']['mode'],'fall_only')
        self.assertEqual(event['wall']['parts'],[])
        self.assertFalse(any(best['ladderSchedule']['hit']))
        self.assertTrue(any(best['plan']['ladderTicks']))
        self.assertFalse(any(c['axis']!=1 for c in best['plan']['sceneContacts']))
        self.assertEqual(sum(best['plan']['jump']),1)
        self.assertTrue(best['candidate']['feasible'])
        self.assertLessEqual(best['candidate']['violation'],1e-7)
        self.assertFalse(result['minimumTicksProven'])

    def test_saved_wall_free_candidate_revalidates_scalar_physics_and_scene(self):
        e=json.loads((ROOT/'experiments/contact-combinations/hanging-vines.json').read_text())
        self.assertEqual(hashlib.sha256((ROOT/e['fixture']).read_bytes()).hexdigest(),e['fixtureSha256'])
        problem=prepare(e['problem'])
        candidate=validate(problem,e['candidate']['yaw'])
        self.assertTrue(candidate['feasible'])
        for key in ('positions','velocity','y','vy','onGround'):
            self.assertEqual(candidate[key],e['candidate'][key])
        vertical=dict(vertical=problem['vertical'],y=candidate['y'],vy=candidate['vy'],onGround=candidate['onGround'])
        with PythonCompiler() as compiler:
            self.assertTrue(check_geometry(e['geometry'],e['plan'],e['candidate'],vertical,compiler,5)['feasible'])


if __name__ == '__main__':
    unittest.main()
