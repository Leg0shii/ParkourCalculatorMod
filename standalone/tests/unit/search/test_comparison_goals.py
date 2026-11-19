from tests.support.paths import RESEARCH_ROOT
import json
import os
from pathlib import Path
import time
import unittest

from parkour_solver.search.engine import evaluate_program
from parkour_solver.reachability.intervals import motion_bound
from parkour_solver.search.goals import goal_spec
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks
from parkour_solver.search.compat.time_progress import clock_result
from research.legacy_cell_routes.window_diagnostic import controls
from research.legacy_cell_routes.checkpoint_experiment import replay_witness
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.bridge.compiler import JavaCompiler
from tests.support.checkpoint_guidance import flat_scene
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

    def test_full_regression_continues_a_discarded_actual_faster_route(self):
        from research.legacy_cell_routes.comparison_audit import audit_pool
        records=[current_events(list(r)) for r in controls(RESEARCH_ROOT) if r[0]=='ceiling']
        fixture=records[0][3]
        scene=prepare_scene(fixture['request'])
        d,gates=decompose(scene),landmarks(scene)
        route=corridor_for_points(d,fixture['spatialPoints'],gates)
        adapter=CorridorAdapter(scene,route,gates,decomposition=d)
        motion=motion_bound(scene,self.compiler,2)
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


if __name__=='__main__':unittest.main()
