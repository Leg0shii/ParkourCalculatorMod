from tests.support.paths import RESEARCH_ROOT
import json
import os
from pathlib import Path
import time
import unittest

from parkour_solver.spatial.checkpoints import checkpoint
from parkour_solver.search.engine import _motion_bound, evaluate_program
from parkour_solver.search.goals import goal_spec, probe_spatial
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks
from parkour_solver.search.compat.time_progress import clock_result, retain_two_goals
from research.legacy_cell_routes.window_diagnostic import controls
from research.legacy_cell_routes.checkpoint_experiment import replay_witness
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.movement.program import truncate_program
from tests.support.checkpoint_guidance import flat_scene, programs
from tests.support.archived_events import current_events


class ComparisonGoalTests(unittest.TestCase):
    def test_goal_validation_uses_real_blocks_and_separate_region_semantics(self):
        scene=flat_scene()
        self.assertEqual(goal_spec(scene),dict(kind='landing',block=scene['goalBlock']))
        with self.assertRaises(ValueError):goal_spec(scene,dict(kind='landing',block='missing'))
        with self.assertRaises(ValueError):goal_spec(scene,dict(kind='region',region=[2,1,0,1]))

    def test_landing_does_not_mean_airborne_or_grounded_above_the_block(self):
        from parkour_solver.search.goals import landing_satisfied
        scene=flat_scene()
        self.assertFalse(landing_satisfied(scene,scene['goalBlock'],dict(y=[1.],onGround=[False])))
        self.assertFalse(landing_satisfied(scene,scene['goalBlock'],dict(y=[1.2],onGround=[True])))
        self.assertTrue(landing_satisfied(scene,scene['goalBlock'],dict(y=[1.],onGround=[True])))

    def test_region_arrival_cannot_use_a_gate_crossed_later(self):
        from types import SimpleNamespace
        from parkour_solver.search.goals import region_arrival
        from parkour_solver.spatial.compat.corridors import Corridor
        corridor=Corridor(0,[],[],[[0,1,0],[2,1,0],[-1,1,0]],(),[],5.)
        gate=dict(id=0,axis=0,lateral=2,plane=1.,lo=-1.,hi=1.)
        adapter=SimpleNamespace(corridor=corridor,gates=[gate])
        candidate=dict(positions=[[0,0],[.2,0],[2,0],[.3,0]],y=[1]*4)
        spec=dict(kind='region',region=[-.5,.5,-1.,1.],segment=1,first_tick=1)
        self.assertEqual(region_arrival(candidate,spec,adapter),3)

    def test_terminal_before_clock_is_unknown_without_held_endpoint(self):
        from parkour_solver.spatial.checkpoints import Checkpoint
        cp=Checkpoint(10,0,(1.,0.),(0.,0.,0.),(10.,0.,0.),())
        key,result=clock_result(cp,dict(best=dict(candidate=dict(positions=[[0.,0.],[1.,0.]]))))
        self.assertEqual(result['status'],'unknown')
        self.assertIsNone(result['objective'])
        self.assertEqual(result['unavailableReason'],'terminal_before_clock')


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for comparison goal tests')
class ComparisonGoalNumericalTests(unittest.TestCase):
    def setUp(self):
        self.compiler=JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_landing_time_and_same_clock_progress_are_distinct(self):
        rows=[]
        root=RESEARCH_ROOT
        for name,_,label,fixture,known in ((*c[:4],current_events(c[4])) for c in controls(root)):
            if name!='ceiling':continue
            scene=prepare_scene(fixture['request'])
            d,gates=decompose(scene),landmarks(scene)
            route=corridor_for_points(d,fixture['spatialPoints'],gates)
            adapter=CorridorAdapter(scene,route,gates,decomposition=d)
            full=known['movementProgram'].copy()
            full['guideSeedYaw']=known['candidate']['yaw'][:]
            program=truncate_program(full,11)
            cp=checkpoint(route,gates,dict(tick=11))
            work=SearchWork(24,deadline=time.monotonic()+8)
            motion=_motion_bound(scene,self.compiler,2)
            clock=evaluate_program(scene,program,self.compiler,adapter,work,motion,checkpoint=cp,
                seconds=3,candidate_seconds=.7,solver_calls=8,python_executable=os.environ['PKC_IPOPT_PYTHON'])
            spatial=probe_spatial(scene,program,self.compiler,adapter,work,motion,seconds=3,
                candidate_seconds=.7,solver_calls=8,python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertIsNotNone(clock['best'])
            self.assertIsNotNone(spatial['best'])
            self.assertEqual(spatial['measurement']['ticks'],known['plan']['ticks'])
            key,measurement=clock_result(cp,clock)
            rows.append((dict(id=label,clock_results={key:measurement},
                spatial_results={spatial['key']:spatial['measurement']}),program))
            self.assertTrue(replay_witness(scene,spatial['best'],self.compiler)['passed'])
            self.assertEqual(clock['best']['candidate']['positions'][0],scene['initial']['position'][::2])
            self.assertEqual(spatial['best']['candidate']['velocity'][0],scene['initial']['velocity'][::2])
        early,late=[r for r,_ in rows]
        clock=next(iter(early['clock_results']))
        goal=next(iter(early['spatial_results']))
        self.assertLess(early['spatial_results'][goal]['ticks'],late['spatial_results'][goal]['ticks'])
        self.assertLess(early['clock_results'][clock]['objective'],late['clock_results'][clock]['objective'])
        kept,_=retain_two_goals(rows,cap=8)
        self.assertEqual(len(kept),2)

    def test_full_regression_continues_a_discarded_actual_faster_route(self):
        from research.legacy_cell_routes.comparison_audit import audit_pool
        records=[current_events(list(r)) for r in controls(RESEARCH_ROOT) if r[0]=='ceiling']
        fixture=records[0][3]
        scene=prepare_scene(fixture['request'])
        d,gates=decompose(scene),landmarks(scene)
        route=corridor_for_points(d,fixture['spatialPoints'],gates)
        adapter=CorridorAdapter(scene,route,gates,decomposition=d)
        motion=_motion_bound(scene,self.compiler,2)
        entries=[]
        for _,_,label,_,known in records:
            program=known['movementProgram'].copy()
            program['guideSeedYaw']=known['candidate']['yaw'][:]
            entries.append(dict(id=label,selection='markedly_worse' if label=='early_jump' else 'retained',program=program))
        def evaluate(entry):
            result=evaluate_program(scene,entry['program'],self.compiler,adapter,SearchWork(8,deadline=time.monotonic()+4),
                motion,seconds=3,candidate_seconds=.7,solver_calls=8,python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertIsNotNone(result['best'])
            self.assertTrue(replay_witness(scene,result['best'],self.compiler)['passed'])
            return dict(best=result['best'],complete=True)
        incumbent=evaluate(entries[1])['best']
        result=audit_pool(entries,evaluate,objective=scene['objective'],incumbent=incumbent)
        self.assertTrue(result['allCandidatesProcessed'])
        self.assertTrue(result['heuristicQualityLoss'])
        self.assertIn('early_jump',result['discardedBeatsHeuristic'])
        self.assertEqual(result['best']['plan']['ticks'],12)

    def test_region_time_is_an_evaluator_on_existing_candidate_not_generation_target(self):
        scene=flat_scene()
        d,gates=decompose(scene),landmarks(scene)
        route=corridor_for_points(d,[[.5,1,.5],[5.5,1,.5]],gates)
        adapter=CorridorAdapter(scene,route,gates,decomposition=d)
        program=next(p for p in programs(scene,4) if p['profile']['jump'][0])
        original=program['profile']['jump'][:]
        result=probe_spatial(scene,program,self.compiler,adapter,SearchWork(8,deadline=time.monotonic()+5),
            _motion_bound(scene,self.compiler,2),spec=dict(kind='region',region=[.7,2.,-1.,2.],segment=0),
            seconds=4,solver_calls=8,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['measurement']['status'],'feasible')
        self.assertEqual(result['measurement']['ticks'],1)
        self.assertEqual(program['profile']['jump'],original)
        self.assertFalse(result['finalGoal'])
        self.assertTrue(replay_witness(scene,result['best'],self.compiler)['passed'])


if __name__=='__main__':unittest.main()
