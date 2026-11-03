import copy
import itertools
import json
import os
from pathlib import Path
import time
import unittest

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoints import Checkpoint, checkpoint
from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.routes import Corridor
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_search import iter_movement_programs
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene, programs


class FixedClockTests(unittest.TestCase):
    def test_priority_proposes_the_actual_intermediate_contact_before_an_air_gap(self):
        from standalone.autonomy.cell_routes.support_order import SupportOrder
        scene=flat_scene()
        policy=SupportOrder(scene,['goal'],32)
        def first(priority):
            g=iter_movement_programs(scene,checkpoint_tick=16,threshold=0.,stop_on_goal=False,
                deadline=time.monotonic()+2,program_priority=priority)
            try:return next(g)
            finally:g.close()
        ordinary,ranked=first(None),first(policy.priority)
        self.assertEqual(ordinary['profile']['airtime'],16)
        self.assertEqual(ranked['profile']['airtime'],16)
        self.assertEqual(policy.audit(ordinary)['progress'],0)
        self.assertEqual(policy.audit(ranked)['progress'],1)

    def test_unknown_spatial_goal_is_rejected_before_compilation(self):
        scene=flat_scene()
        with self.assertRaisesRegex(ValueError,'Landing comparison requires'):
            find_checkpoint_route(scene,object(),checkpoints=[],spatial_goals=[dict(kind='landing',block='missing')],
                spatial_points=[[.5,1,.5],[5.5,1,.5]])

    def test_explicit_clock_preserves_direction_without_goal_region(self):
        route=Corridor(0,[],[],[[0,1,0],[2,1,0]],(),[],2.)
        cp=Checkpoint(8,-1,(0.,1.),(0.,1.,0.),(0.,1.,10.),())
        self.assertIs(checkpoint(route,[],cp),cp)
        self.assertEqual(cp.rows(),[])
        self.assertAlmostEqual(cp.objective['customYaw'],0.)

    def test_clock_generation_continues_after_goal_tag(self):
        scene=flat_scene()
        seed=next(p for p in programs(scene,1) if p['profile']['grounded'][-1])
        seed['terminalSupport']=scene['goalBlock']
        old=list(itertools.islice(iter_movement_programs(scene,initial_programs=[seed],threshold=0.,
            checkpoint_tick=2,deadline=time.monotonic()+2),1))
        new=list(itertools.islice(iter_movement_programs(scene,initial_programs=[seed],threshold=0.,
            checkpoint_tick=2,deadline=time.monotonic()+2,stop_on_goal=False),1))
        self.assertEqual(old[0]['profile']['airtime'],1)
        self.assertEqual(new[0]['profile']['airtime'],2)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for fixed-clock solves')
class FixedClockNumericalTests(unittest.TestCase):
    def test_terminal_clock_can_overshoot_goal_and_finish_airborne(self):
        request=dict(version=1,sceneModel='collision_boxes',geometryBounds=[-3,-3,-3,12,7,3],
            initial=dict(position=[.5,1,.5],velocity=[.3,-.0784000015258789,0],yaw=-90),
            startBlock='start',goalBlock='goal',maxWallHits=0,maxContactCombinations=0,
            blocks=[dict(id='start',boxes=[[0,0,0,1,1,1]]),dict(id='goal',boxes=[[1.5,0,0,2.5,1,1]])],
            movementSearch=dict(types=['normal'],maxInputs=16,maxPrefixes=64,maxQueuedPrefixes=64,maxChoicesPerType=16))
        original=copy.deepcopy(request)
        cp=Checkpoint(8,-1,(1.,0.),(.5,1.,.5),(8.,3.,.5),())
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result=find_checkpoint_route(request,compiler,terminal_checkpoint=cp,checkpoints=[],
                spatial_points=[[.5,1,.5],[.5,3,.5],[8,3,.5]],seconds=10,
                max_candidates=12,max_solver_calls=32,candidate_seconds=.7,score_seconds=2,
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'],'feasible',result['termination'])
            best=result['best']
            self.assertEqual(best['plan']['ticks'],8)
            self.assertGreater(best['candidate']['positions'][-1][0],2.8)
            self.assertFalse(best['candidate']['onGround'][-1])
            self.assertFalse(best['route']['terminalSupportRequired'])
            self.assertTrue(replay_witness(prepare_scene(request),best,compiler)['passed'])
            from standalone.ipopt.model import prepare, fingerprint
            calls=[d for r in result['finalAttempts'] for a in r['numericalAttempts'] for d in a.get('diagnostics',[])]
            self.assertTrue(calls)
            for call in calls:
                self.assertEqual(fingerprint(prepare(call['request'])),call['problemHash'])
        self.assertEqual(request,original)
        self.assertEqual(result['goalType'],'fixed_absolute_tick_objective')

    def test_unreachable_floor_gates_are_excluded_from_terminal_stage_checks(self):
        from unittest.mock import patch
        from standalone.autonomy.cell_routes.geometry import decompose
        from standalone.autonomy.cell_routes.routes import corridor_for_points, landmarks
        from standalone.autonomy.cell_routes.local import CorridorAdapter, SearchWork
        from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program, _motion_bound
        from standalone.autonomy.open_movement import event_choice
        from standalone.autonomy.movement_composition import empty_program, append_choice
        request=flat_scene()
        middle=[3.5,0.,-1.,4.5,1.,1.]
        request['collisionScene']['solids'].append(middle)
        request['blocks'].append(dict(id='middle',sourceBlock='middle',solidIndex=2,top=1.,bounds=[3.5,4.5,-1.,1.]))
        d=decompose(request);g=landmarks(request)
        corridor=corridor_for_points(d,[[.5,1,.5],[.5,3,.5],[8.,3,.5]],g)
        adapter=CorridorAdapter(request,corridor,g,decomposition=d)
        root=empty_program(request['initial'],threshold=0.)
        p=append_choice(root,event_choice(dict(threshold=0.),'normal',root['initial'],8,jumping=True))
        cp=Checkpoint(8,-1,(1.,0.),(.5,1.,.5),(8.,3.,.5),corridor.signature)
        original=Checkpoint.stage;seen=[]
        def stage(checkpoint,candidate,gates):
            seen.append(gates)
            self.assertFalse(any(gate['box']==middle for gate in gates))
            return original(checkpoint,candidate,gates)
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler, \
             patch.object(Checkpoint,'stage',stage):
            result=evaluate_program(request,p,compiler,adapter,SearchWork(16),_motion_bound(request,compiler,2),
                checkpoint=cp,seconds=5,candidate_seconds=.7,solver_calls=16,
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertIsNotNone(result['best'],result['termination'])
            self.assertTrue(seen)
            self.assertTrue(result['comparisonStage']['accepted'])
            self.assertGreater(adapter.counts['unreachableInteractionGatesRemoved'],0)
            self.assertTrue(replay_witness(request,result['best'],compiler)['passed'])


class SupportOrderTests(unittest.TestCase):
    def test_route_order_preserves_timings_and_does_not_require_terminal_landing(self):
        from standalone.autonomy.cell_routes.support_order import SupportOrder
        scene=flat_scene()
        policy=SupportOrder(scene,['goal'],8)
        goal=scene['collisionScene']['solids'][1]
        program=dict(profile=dict(airtime=8,contactEvents=[dict(offset=4,axis='Y',direction=-1,box=goal)]))
        self.assertTrue(policy(program))

        self.assertEqual(policy.audit(program)['observed'],[dict(tick=5,block='goal')])
        program['profile']['contactEvents'][0]['offset']=6
        self.assertTrue(policy(program))
        program['profile']['contactEvents']=[]
        self.assertFalse(policy(program))
        program['profile']['airtime']=4
        self.assertTrue(policy(program))


if __name__=='__main__':unittest.main()
