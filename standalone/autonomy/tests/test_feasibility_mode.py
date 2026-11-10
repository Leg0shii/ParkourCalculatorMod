import copy
import json
import os
from pathlib import Path
import time
import unittest

from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program, _motion_bound
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter, SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points, landmarks
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene, programs


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for intermediate feasibility tests')
class FeasibilityModeTests(unittest.TestCase):
    def test_feasible_intermediate_and_optimized_comparison_use_distinct_repair_work(self):
        scene = flat_scene()
        decomposition, gates = decompose(scene), landmarks(scene)
        corridor = corridor_for_points(decomposition,[[.5,1,.5],[5.5,1,.5]],gates)
        adapter = CorridorAdapter(scene,corridor,gates,decomposition=decomposition)
        program = next(p for p in programs(scene,4) if p['profile']['jump'][0])
        original = copy.deepcopy(program)
        clock = Checkpoint(4,-1,(1.,0.),(.5,1.,.5),(5.5,1.,.5),())
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            motion = _motion_bound(scene,compiler,2)
            def evaluate(p, first):
                return evaluate_program(scene,p,compiler,adapter,SearchWork(16,deadline=time.monotonic()+8),motion,
                    checkpoint=clock,seconds=6,candidate_seconds=1.,solver_calls=8,
                    python_executable=os.environ['PKC_IPOPT_PYTHON'],first_witness=first)
            first = evaluate(program,True)
            self.assertEqual(first['status'],'feasible',first['termination'])
            self.assertEqual(first['objectiveMode'],'configured')
            self.assertTrue(replay_witness(scene,first['best'],compiler)['passed'])
            calls = [d for a in first['attempts'] for d in a.get('diagnostics',[])]
            self.assertTrue(calls)
            self.assertTrue(all(d['callOptions']['stopOnFeasible'] and d['objectiveMode']=='configured' for d in calls))
            # The same hard model can later be optimized, but its exhausted
            # feasibility cursor must not suppress the optimization request.
            resumed = dict(copy.deepcopy(program),repairState=first['repairState'])
            second = evaluate(resumed,False)
            self.assertEqual(second['status'],'feasible',second['termination'])
            self.assertEqual(second['objectiveMode'],'configured')
            self.assertFalse(second['repairResume']['compatible'])
            self.assertTrue(replay_witness(scene,second['best'],compiler)['passed'])
            self.assertGreaterEqual(second['best']['candidate']['objective'],first['best']['candidate']['objective']-1e-7)
            calls = [d for a in second['attempts'] for d in a.get('diagnostics',[])]
            self.assertTrue(calls)
            self.assertTrue(all(not d['callOptions']['stopOnFeasible'] and d['objectiveMode']=='configured' for d in calls))
        self.assertEqual(program,original)


if __name__=='__main__':
    unittest.main()
