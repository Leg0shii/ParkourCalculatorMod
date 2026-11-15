from tests.support.paths import AUTONOMY_DATA
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from parkour_solver.search.support_profiles import prepare_scene
from research.legacy_cell_routes.experiment import compact, run_arm, verify_witness
from parkour_solver.validation.scoped_scene import SearchWork
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.search.ordered_route import find_route
from tests.unit.search.test_search import route


ROOT = AUTONOMY_DATA / 'experiments/cell-routes/controls'


class CellExperimentTests(unittest.TestCase):
    def test_retry_budget_counts_actual_worker_calls(self):
        request = route(1)
        request.update(runBounds=[[0, 0]], maxTotalRunTicks=0)
        class Compiler:
            def compile(self, route, plan, seconds):
                n = plan['ticks']
                return dict(magnitude=[.4]*n, friction=[.91]*n, base=[0]*n)
        work = SearchWork(1)
        with patch('parkour_solver.search.ordered_route.solve', return_value=dict(status='unknown', feasible=False)) as solve:
            result = find_route(request, Compiler(), retries=2, work=work)
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(solve.call_count, 1)
        self.assertEqual(work.summary()['solverCalls'], 1)
        self.assertEqual(work.termination, 'numerical_call_limit')

    def test_prescribed_points_do_not_remove_remote_free_space(self):
        request = json.loads((ROOT/'remote-supports.json').read_text())
        points = json.loads((ROOT/'remote-supports-route.json').read_text())
        a = run_arm(request, 'generic', seconds=2)
        b = run_arm(request, 'prescribed', spatial_points=points, seconds=2)
        self.assertEqual(compact(b)['generatedChoices'], compact(a)['generatedChoices'])
        self.assertEqual(b['numericalCalls'], 0)
        self.assertEqual(b['results'][0]['adapter']['allowedBlocks'], len(request['blocks']))
        self.assertEqual(request['initial'], b['results'][0]['adapter']['originalInitialState'])

    def test_one_input_step_family_exhausts_without_losing_complete_programs(self):
        request=json.loads((AUTONOMY_DATA/'fixtures/stepping/normal_step.json').read_text())['request']
        request['movementSearch']=dict(types=['stepping'],maxInputs=1,maxPrefixes=64,maxQueuedPrefixes=64,maxChoicesPerType=128)
        points=json.loads((ROOT/'normal-step-route.json').read_text())
        runs=[run_arm(request,arm,seconds=2,spatial_points=points) for arm in ('generic','prescribed')]
        for result in runs:
            generation=result['results'][0]['movementGeneration']
            self.assertEqual(generation['termination'],'exhausted')
            self.assertEqual(generation['queueDrops'],0)
            self.assertGreater(compact(result)['completePrograms'],0)
        self.assertEqual(compact(runs[0])['completePrograms'],compact(runs[1])['completePrograms'])

    def test_deadline_retains_active_prefix_generation_records(self):
        from parkour_solver.movement.generation import GENERATORS
        from parkour_solver.search.movement import iter_movement_programs
        from tests.unit.spatial.test_cell_routes import side_scene
        scene = side_scene()
        scene['movementSearch']['types'] = ['normal']
        class TimedGenerator:
            def generate(self, context, **options):
                context['generationRecords'].append(dict(stats=dict(maskWork=17)))
                raise TimeoutError('synthetic deadline')
        report = {}
        with patch.dict(GENERATORS, normal=TimedGenerator()):
            self.assertEqual(list(iter_movement_programs(scene, report=report)), [])
        self.assertEqual(report['generation'][0]['stats']['maskWork'],17)
        self.assertEqual(report['termination'],'deadline')

    def test_prescribed_route_endpoints_are_checked(self):
        request = json.loads((ROOT/'open.json').read_text())
        with self.assertRaises(ValueError):
            run_arm(request, 'prescribed', spatial_points=[[1,1,.5],[3.5,1,.5]])
        with self.assertRaises(ValueError):
            run_arm(request, 'prescribed', spatial_points=[[.5,1,.5],[.5,2,.5]])


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class CellNumericalTests(unittest.TestCase):
    def setUp(self):
        self.compiler = JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))
        self.addCleanup(self.compiler.close)

    def test_automatic_blockage_routes_are_repaired_and_physically_checked(self):
        request = json.loads((ROOT/'wide-neo.json').read_text())
        result = run_arm(request,'automatic',mode='solve',seconds=8,route_cap=10,max_expansions=300,
            max_solver_calls=12,route_seconds=2,compiler=self.compiler,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertEqual(result['status'],'feasible',compact(result))
        self.assertLessEqual(result['best']['plan']['ticks'],13)
        self.assertTrue(result['best']['geometryCheck']['feasible'])
        self.assertTrue(result['best']['corridorCheck']['accepted'])
        self.assertLessEqual(result['numericalCalls'],12)

    def test_goal_region_preserves_faster_grind_landing_before_an_interior_gate(self):
        fixture = AUTONOMY_DATA/'fixtures/generic-grind-blip.json'
        request = json.loads(fixture.read_text())
        request['movementSearch']['maxPrefixes'] = 64
        points = json.loads((ROOT/'grind-route.json').read_text())
        runs = [run_arm(request,arm,mode='solve',seconds=8,max_solver_calls=12,route_seconds=2,
            compiler=self.compiler,python_executable=os.environ['PKC_IPOPT_PYTHON'],spatial_points=points)
            for arm in ('generic','prescribed')]
        for result in runs:
            self.assertEqual(result['status'],'feasible')
            self.assertEqual(result['best']['plan']['ticks'],5)
            self.assertTrue(result['best']['geometryCheck']['feasible'])
            self.assertLess(result['best']['candidate']['positions'][-1][0],2.425)
        self.assertAlmostEqual(runs[0]['best']['candidate']['objective'],runs[1]['best']['candidate']['objective'],places=6)

    def test_all_arms_preserve_original_state_acceptance_and_terminal_runoff(self):
        request = json.loads((ROOT/'open.json').read_text())
        points = json.loads((ROOT/'open-route.json').read_text())
        runs = [run_arm(request, arm, mode='solve', seconds=6, route_cap=3, max_expansions=100,
            max_solver_calls=4, compiler=self.compiler, python_executable=os.environ['PKC_IPOPT_PYTHON'],
            spatial_points=points) for arm in ('generic', 'prescribed', 'automatic')]
        for result in runs:
            self.assertEqual(result['status'], 'feasible', compact(result))
            self.assertEqual(result['best']['plan']['ticks'], 12)
            self.assertEqual(result['best']['route']['initial'], request['initial'])
            self.assertTrue(result['best']['candidate']['feasible'])
            self.assertTrue(result['best']['geometryCheck']['feasible'])
            self.assertLessEqual(result['numericalCalls'], 4)
            self.assertAlmostEqual(result['best']['candidate']['objective'], runs[0]['best']['candidate']['objective'], places=5)
        candidate = verify_witness(prepare_scene(request),
                                   runs[0], self.compiler, 3)
        self.assertEqual(candidate['positions'], runs[0]['best']['candidate']['positions'])
        self.assertGreater(candidate['positions'][-1][0], 4.3)


if __name__ == '__main__':
    unittest.main()
