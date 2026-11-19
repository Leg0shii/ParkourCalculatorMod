from tests.support.movement_contacts import small_request
from tests.support.paths import AUTONOMY_DATA
import copy
import json
import os
import threading
import time
import unittest

from parkour_solver.movement.contact_combinations import contact_combinations
from parkour_solver.movement.contacts import contact_variants, mixed_schedule_pool, program_seed
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.movement.ladder.transfers import open_program, append_ladder_programs, recompose, finish_programs
from tests.support.ladder_composition import airborne_case
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.validation.scene import check_geometry
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.search.support_profiles import compile_block_path
from parkour_solver.search.repair import solve_contact_branches
from parkour_solver.constraints.contacts import floor_checks
from parkour_solver.bridge.python_compiler import PythonCompiler
from parkour_solver.numerics import prepare, validate
from parkour_solver.physics.vertical import CLIMB_CARRY
from parkour_solver.movement.contact_combinations import expanded_face


ROOT = AUTONOMY_DATA


class MovementContactTests(unittest.TestCase):
    def test_ceiling_regenerates_exit_time_instead_of_requiring_old_exit_to_work(self):
        request=small_request()
        baseline=generate_ladder_schedules(request)['schedules']
        self.assertTrue(any(s['exitState']==2 for s in baseline))
        ceiling=[-1,.95+BODY_HEIGHT,-1,2,3.5,2]
        mixed=mixed_schedule_pool(request,[ceiling],schedules=baseline,max_variants=16)
        changed=[s for s in mixed['schedules'] if s['exitState']==3 and not any(s['sneak'])
                 and [(e['offset'],e['axis']) for e in s['contactEvents']]==[(1,'Y')]]
        self.assertTrue(changed,mixed)
        s=changed[0]
        self.assertAlmostEqual(s['trace']['position'][2],.95)
        self.assertGreaterEqual(s['trace']['position'][3],1)
        self.assertTrue(s['trace']['climbRefreshed'][1])
        self.assertEqual(s['trace']['velocity'][2],CLIMB_CARRY)

    def test_contact_assisted_entry_is_generated_even_without_a_baseline_schedule(self):
        request=small_request()
        request['initial']['velocity']=.3
        request['entries'][0]['firstCandidateState']=1
        baseline=generate_ladder_schedules(request)['schedules']
        self.assertEqual(baseline,[])
        ceiling=[-1,.95+BODY_HEIGHT,-1,2,3.5,2]
        mixed=mixed_schedule_pool(request,[ceiling],schedules=[],max_variants=16)
        self.assertTrue(mixed['schedules'],mixed)
        self.assertTrue(any(s['entryState']==1 and s['trace']['position'][1]<1
                            and any(e['axis']=='Y' and e['offset']==0 for e in s['contactEvents'])
                            for s in mixed['schedules']))

    def test_repeated_ceiling_clips_during_climbing_are_generated_as_one_sequence(self):
        request=small_request()
        request.update(region={'minY':0,'maxY':2},exitKinds=['side'],exitStates=[5])
        baseline=generate_ladder_schedules(request)['schedules']
        ceiling=[-1,.95+BODY_HEIGHT,-1,2,3.5,2]
        mixed=mixed_schedule_pool(request,[ceiling],schedules=baseline,max_variants=16,max_events=2)
        choices=[s for s in mixed['schedules'] if len([e for e in s['contactEvents'] if e['axis']=='Y'])>=3]
        self.assertTrue(choices)
        self.assertTrue(any(sum(s['trace']['climbRefreshed'])>=3 for s in choices))

    def test_small_shared_mask_budget_can_emit_a_valid_regenerated_schedule(self):
        request=small_request()
        request['maxMaskWork']=128
        baseline=generate_ladder_schedules(request)['schedules']
        ceiling=[-1,.95+BODY_HEIGHT,-1,2,3.5,2]
        mixed=mixed_schedule_pool(request,[ceiling],schedules=baseline,max_variants=8)
        self.assertTrue(mixed['schedules'])
        self.assertLessEqual(mixed['generation']['maskWork'],128)

    def test_same_consecutive_and_independent_contacts_preserve_all_axis_types(self):
        box=[0,2,0,1,3,1]
        result=contact_variants([box],2,max_events=3,max_variants=256)
        variants=result['variants']
        for axes in [('X',),('Y',),('Z',),('X','Y'),('X','Z'),('Y','Z'),('X','Y','Z')]:
            self.assertTrue(any({e['axis'] for e in v}==set(axes)
                                and len({e['offset'] for e in v})==1 for v in variants),axes)
        self.assertTrue(any({e['offset'] for e in v}=={0,1} for v in variants))
        for axis in ('X','Z'):
            self.assertTrue(any(any(e['axis']==axis and e['direction']==-1 for e in v) for v in variants))
        events=[dict(leg=0,offset=t,axis=a,plane=0,direction=1,boxes=[box],surface=None,regions=[expanded_face(box,'XYZ'.index(a),-1,1e-6)])
                for t,a in [(0,'X'),(4,'Z')]]
        self.assertFalse(any(len(v)==2 for v in contact_combinations(events,max_events=2)))
        self.assertTrue(any(len(v)==2 for v in contact_combinations(events,max_events=2,independent_events=True)))

    def test_final_air_seed_preserves_original_state_and_never_inserts_another_jump(self):
        _,_,p=airborne_case()
        prefix=open_program(p['ladderEvents'][0])
        seed=program_seed(prefix,40)
        self.assertEqual(seed['trace']['position'][:7],prefix['trace']['position'])
        self.assertEqual(seed['trace']['velocity'][:7],prefix['trace']['velocity'])
        self.assertEqual(sum(v is not None for v in seed['trace']['vertical']['jumpVelocity']),1)
        self.assertFalse(any(seed['trace']['onGround'][1:]))

    def test_final_air_contact_recomputes_landing_without_moving_earlier_anchors(self):
        _,episodes,p=airborne_case()
        prefix=recompose(p,episodes,None,48)
        ceiling=[.1,3.07+BODY_HEIGHT,2,2,5.2,3]
        choices=list(finish_programs(prefix,3,[ceiling],max_inputs=48,max_contact_variants=8))
        changed=next(x for x in choices if [(e['offset'],e['axis']) for e in x['profile']['contactEvents']]==[(25,'Y')])
        self.assertEqual(choices[0]['profile']['airtime'],28)
        self.assertEqual(changed['profile']['airtime'],27)
        self.assertEqual(changed['episodes'],episodes)
        self.assertEqual(changed['trace']['position'][:26],prefix['trace']['position'])
        self.assertAlmostEqual(changed['trace']['position'][26],3.07)

    def test_existing_contacts_are_retained_and_conflicting_normals_are_not_emitted(self):
        event={'leg':0,'offset':0,'axis':'X','plane':-.3,'direction':1,'boxes':[[0,0,0,1,1,1]],'surface':None,
               'regions':[expanded_face([0,0,0,1,1,1],0,-1,1e-6)],'role':'contact'}
        result=contact_variants([[0,0,0,1,1,1]],2,existing=[event],max_variants=64)
        self.assertTrue(result['variants'])
        for selected in result['variants']:
            self.assertTrue(any(e['axis']=='X' and e['offset']==0 and e['direction']==1 for e in selected))
            self.assertFalse(any(e['axis']=='X' and e['offset']==0 and e['direction']==-1 for e in selected))

    def test_disabled_cancelled_and_deadline_generation_are_explicit(self):
        self.assertEqual(contact_variants([],3,max_variants=0)['termination'],'disabled')
        cancel=threading.Event()
        cancel.set()
        self.assertEqual(contact_variants([],3,cancel_event=cancel)['termination'],'cancelled')
        self.assertEqual(contact_variants([],3,deadline=time.monotonic()-1)['termination'],'deadline')
        request=small_request()
        saved=copy.deepcopy(request)
        result=mixed_schedule_pool(request,[],max_variants=0)
        self.assertEqual(result['schedules'],[])
        self.assertEqual(request,saved)


@unittest.skipUnless(os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_IPOPT_PYTHON')
class GeneratedMixedContactIntegrationTests(unittest.TestCase):
    def test_generated_contacts_on_approach_second_ladder_and_final_air_solve_jointly(self):
        _,episodes,base=airborne_case()
        with PythonCompiler() as compiler:
            for name,offset,ticks in [('appended-air-ceiling',6,29),('appended-ladder-ceiling',20,29),('final-air-ceiling',25,27)]:
                with self.subTest(name=name):
                    evidence=json.loads((ROOT/'experiments/contact-combinations'/(name+'.json')).read_text())
                    scene=prepare_scene(json.loads((ROOT/evidence['fixture']).read_text()))
                    if name=='final-air-ceiling':
                        prefix=recompose(base,episodes,None,48)
                        candidates=list(finish_programs(prefix,3,scene['collisionScene']['solids'],
                                                       max_inputs=48,max_contact_variants=16))
                    else:
                        generated=append_ladder_programs(open_program(base['ladderEvents'][0]),
                            episodes[1]['column'],episodes[1]['wall'],toward=[1,2.5],max_inputs=48,max_entry=6,
                            max_programs=128,solids=scene['collisionScene']['solids'],
                            max_contact_variants=8 if name=='appended-air-ceiling' else 16)
                        candidates=[recompose(p,p['episodes'],3,48) for p in generated['programs']
                                    if p['episodes'][-1]['entryState']==11 and p['episodes'][-1]['exitKind']=='top']
                    program=next((p for p in candidates if p is not None
                        and [(e['offset'],e['axis']) for e in p['profile']['contactEvents']]==[(offset,'Y')]),None)
                    self.assertIsNotNone(program,{'case':name,'candidates':len(candidates)})
                    edge=dict(source='start',target='b',profileKind='ladder',profile=program['profile'],
                              ladderEvents=program['ladderEvents'],entryFace=None,exitFace=None)
                    compiled=compile_block_path(scene,['start','b'],[edge])
                    result=solve_contact_branches(compiled['route'],compiled['profiles'],
                        floor_checks(scene,['start','b'],compiled['profiles']),compiler,seconds=30,
                        candidate_seconds=5,retries=1,geometry=scene['collisionScene'],
                        motion_bound={'storedSpeedNorm':2,'movementAfterSupportCheck':2},
                        python_executable=os.environ['PKC_IPOPT_PYTHON'])
                    self.assertEqual(result['status'],'feasible',result)
                    b=result['best']
                    self.assertEqual(b['plan']['ticks'],ticks)
                    self.assertTrue(b['geometryCheck']['feasible'])
                    self.assertEqual(len(b['route']['ladderEvents']),2)
                    self.assertEqual(sum(b['plan']['jump']),1)
                    self.assertFalse(any(b['candidate']['onGround'][1:-1]))
                    problem=prepare(evidence['problem'])
                    accepted=validate(problem,evidence['candidate']['yaw'])
                    self.assertTrue(accepted['feasible'])
                    self.assertEqual(accepted['positions'],evidence['candidate']['positions'])
                    vertical=dict(vertical=problem['vertical'],y=accepted['y'],vy=accepted['vy'],onGround=accepted['onGround'])
                    self.assertTrue(check_geometry(evidence['geometry'],evidence['plan'],evidence['candidate'],
                                                   vertical,compiler,5)['feasible'])


if __name__=='__main__':
    unittest.main()
