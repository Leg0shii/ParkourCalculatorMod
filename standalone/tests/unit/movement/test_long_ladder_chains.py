from tests.support.paths import AUTONOMY_DATA
import hashlib
import json
import os
from pathlib import Path
import unittest

from parkour_solver.search.repair import floor_checks, solve_contact_branches
from parkour_solver.search.support_profiles import prepare_scene, compile_block_path
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.scene.geometry import check_geometry
from parkour_solver.numerics import prepare, validate


ROOT=AUTONOMY_DATA


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_RUN_TICKS_COMPILER and PKC_IPOPT_PYTHON')
class LongLadderChainTests(unittest.TestCase):
    def test_saved_cold_three_and_prescribed_five_climbs_revalidate_full_scene(self):
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for name,count,ticks in [('three-climbing-ladders',3,50),('five-climbing-ladders',5,99)]:
                with self.subTest(name=name):
                    e=json.loads((ROOT/'experiments/contact-combinations'/(name+'.json')).read_text())
                    self.assertEqual(hashlib.sha256((ROOT/e['fixture']).read_bytes()).hexdigest(),e['fixtureSha256'])
                    p=prepare(e['problem'])
                    c=validate(p,e['candidate']['yaw'])
                    self.assertTrue(c['feasible'])
                    self.assertEqual(c['positions'],e['candidate']['positions'])
                    self.assertEqual(c['velocity'],e['candidate']['velocity'])
                    self.assertEqual(c['y'],e['candidate']['y'])
                    self.assertEqual(e['plan']['ticks'],ticks)
                    self.assertEqual(len(e['route']['ladderEvents']),count)
                    self.assertTrue(all(sum(v['schedule']['hit'])>=2 for v in e['route']['ladderEvents']))
                    self.assertFalse(any(c['onGround'][1:-1]))
                    self.assertEqual(sum(e['plan']['jump']),1)
                    vertical=dict(vertical=p['vertical'],y=c['y'],vy=c['vy'],onGround=c['onGround'])
                    self.assertTrue(check_geometry(e['geometry'],e['plan'],e['candidate'],vertical,compiler,5)['feasible'])

    def test_fresh_solves_start_inside_first_ladder_without_turning_back_to_its_centre(self):
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for name,count,ticks in [('three-climbing-ladders',3,50),('five-climbing-ladders',5,99)]:
                with self.subTest(name=name):
                    e=json.loads((ROOT/'experiments/contact-combinations'/(name+'.json')).read_text())
                    request=json.loads((ROOT/e['fixture']).read_text())
                    scene=prepare_scene(request)
                    self.assertGreater(scene['initial']['position'][2],.5)
                    self.assertEqual(e['route']['ladderEvents'][0]['schedule']['entryState'],0)
                    edge=dict(source='start',target=scene['goalBlock'],profileKind='ladder',profile=e['profiles'][0],
                              ladderEvents=e['route']['ladderEvents'],entryFace=None,exitFace=None)
                    path=['start',scene['goalBlock']]
                    compiled=compile_block_path(scene,path,[edge])
                    r=solve_contact_branches(compiled['route'],compiled['profiles'],floor_checks(scene,path,compiled['profiles']),
                        compiler,seconds=45,candidate_seconds=10,retries=1,max_branches=8,geometry=scene['collisionScene'],
                        motion_bound={'storedSpeedNorm':2,'movementAfterSupportCheck':2},
                        python_executable=os.environ['PKC_IPOPT_PYTHON'])
                    self.assertEqual(r['status'],'feasible',r)
                    b=r['best']
                    self.assertEqual(b['plan']['ticks'],ticks)
                    self.assertEqual(len(b['route']['ladderEvents']),count)
                    self.assertTrue(b['geometryCheck']['feasible'])
                    self.assertGreater(b['candidate']['positions'][1][1],scene['initial']['position'][2])
                    self.assertEqual(sum(b['plan']['jump']),1)
                    self.assertFalse(any(b['candidate']['onGround'][1:-1]))


if __name__=='__main__':
    unittest.main()
