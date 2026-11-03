import copy
import json
import os
from pathlib import Path
import unittest

from standalone.autonomy.terminal_goals import prepare_goal, height_clock, bind_goal, satisfied
from standalone.autonomy.movement_composition import empty_program, append_choice
from standalone.autonomy.open_movement import event_choice
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene


class TerminalGoalTests(unittest.TestCase):
    def setUp(self):
        self.scene=flat_scene()
        self.goal=prepare_goal(dict(kind='landing_height_region',height=2.,
            rows=[dict(mode='X',cmp='GE',rhs=1.5,strict=True),dict(mode='Z',cmp='GE',rhs=-2.)]))

    def program(self,n):
        root=empty_program(self.scene['initial'],threshold=0.)
        return append_choice(root,event_choice(dict(threshold=0.),'normal',root['initial'],n,jumping=True))

    def test_downward_height_clock_is_not_a_fixed_tick_or_apex(self):
        self.assertFalse(height_clock(self.program(8),self.goal))
        self.assertTrue(height_clock(self.program(9),self.goal))
        self.assertFalse(height_clock(self.program(10),self.goal))

    def test_goal_rows_are_one_sided_center_bounds_with_no_fake_landing(self):
        p=self.program(9);before=copy.deepcopy(p)
        tagged=bind_goal(p,self.goal)
        self.assertEqual(p,before)
        rows=tagged['profile']['positionConstraints']
        self.assertEqual(len(rows),2)
        self.assertEqual([r['t1'] for r in rows],[9,9])
        self.assertAlmostEqual(rows[0]['rhs'],1.500001)
        self.assertEqual(rows[1]['rhs'],-2.)
        self.assertFalse(tagged['profile']['grounded'][-1])
        self.assertLess(tagged['profile']['position'][-1],2.)
        self.assertTrue(satisfied(tagged,dict(positions=[[100.,20.]]*10),self.goal))
        self.assertFalse(satisfied(tagged,dict(positions=[[1.,20.]]*10),self.goal))
        self.assertEqual(bind_goal(tagged,self.goal),tagged)
        left=prepare_goal(dict(kind='landing_height_region',height=2.,
            rows=[dict(mode='X',cmp='LE',rhs=3.,strict=True)]))
        limited=bind_goal(p,left)
        self.assertAlmostEqual(limited['profile']['positionConstraints'][-1]['rhs'],2.999999)
        self.assertTrue(satisfied(limited,dict(positions=[[-100.,100.]]*10),left))
        self.assertFalse(satisfied(limited,dict(positions=[[3.,100.]]*10),left))

    def test_unknown_or_vertical_terminal_rows_are_rejected(self):
        bad=copy.deepcopy(self.goal);bad['rows'][0]['mode']='Y'
        with self.assertRaises(ValueError):prepare_goal(bad)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for virtual terminal solves')
class TerminalGoalNumericalTests(unittest.TestCase):
    def test_generic_height_goal_can_finish_airborne_beyond_the_physical_block(self):
        from standalone.autonomy.compiler import JavaCompiler
        from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
        from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
        from standalone.autonomy.cell_routes.support_order import SupportOrder
        scene=flat_scene();scene['movementSearch']['maxInputs']=20
        scene['objective']=dict(axis='X',sense='MAX')
        goal=dict(kind='landing_height_region',height=2.,
            rows=[dict(mode='X',cmp='GE',rhs=1.5),dict(mode='Z',cmp='GE',rhs=-2.)])
        original=copy.deepcopy(scene)
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result=find_checkpoint_route(scene,compiler,spatial_points=[[.5,1,.5],[8.,3,.5]],
                checkpoints=[],spatial_goals=[goal],program_filter=SupportOrder(scene,[],None,[0,1],max_inputs=20),
                seconds=12,max_candidates=8,max_solver_calls=24,candidate_seconds=.8,score_seconds=2,
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'],'feasible',result['termination'])
            best=result['best']
            self.assertEqual(best['plan']['ticks'],9)
            self.assertTrue(satisfied(best['movementProgram'],best['candidate'],goal))
            self.assertFalse(best['candidate']['onGround'][-1])
            self.assertLess(best['candidate']['y'][-1],2.)
            self.assertTrue(replay_witness(scene,best,compiler)['passed'])
            self.assertEqual(result['goalType'],'earliest_virtual_landing_height')
            restored=find_checkpoint_route(scene,compiler,spatial_points=[[.5,1,.5],[8.,3,.5]],
                checkpoints=[],spatial_goals=[goal],program_filter=SupportOrder(scene,[],None,[0,1],max_inputs=20),
                resume_state=json.loads(json.dumps(result['searchState'])),seconds=2,
                max_candidates=8,max_solver_calls=24,candidate_seconds=.8,score_seconds=2,
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertTrue(restored['resume']['exact'])
            self.assertEqual(restored['best']['plan']['ticks'],9)
            self.assertTrue(replay_witness(scene,restored['best'],compiler)['passed'])
            changed=dict(goal,height=3.)
            with self.assertRaisesRegex(ValueError,'Incompatible DFS'):
                find_checkpoint_route(scene,compiler,spatial_points=[[.5,1,.5],[8.,3,.5]],
                    checkpoints=[],spatial_goals=[changed],program_filter=SupportOrder(scene,[],None,[0,1],max_inputs=20),
                    resume_state=result['searchState'],seconds=2,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(scene,original)


if __name__=='__main__':unittest.main()
