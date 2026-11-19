from tests.support.paths import AUTONOMY_DATA
import copy
import json
import os
import threading
import time
import unittest
from unittest.mock import patch

from parkour_solver.search.climbable import entry_context
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.movement.ladder.episodes import compose_ladder_episodes
from parkour_solver.bridge.python_compiler import PythonCompiler
from parkour_solver.scene.ladders import entry_channels
from parkour_solver.movement.ladder.transfers import append_ladder_programs, open_program
from parkour_solver.search.entry import build_entry_problem, probe_region_entries, entry_contact_options
from parkour_solver.numerics.builders import _raw_candidate
from parkour_solver.validation.scene import check_geometry
from tests.support.ladder_composition import airborne_case
from parkour_solver.numerics import validate
from parkour_solver.numerics import solve
from parkour_solver.physics.vertical import CLIMB_CARRY
from tests.support.archived_events import current_events


class MovementEntryTests(unittest.TestCase):
    def test_initial_membership_needs_no_provisional_jump_or_fall(self):
        column={'x':0,'z':0,'minY':0,'maxY':2}
        face={'axis':'X','direction':-1,'plane':0,'family':'front','channel':'front:X:-1'}
        root={'base':{'ticks':0}, 'scene':{'initial':{'position':[.5,1,.5]},'ceilingHits':False},'route':{}}
        with patch('parkour_solver.search.entry.build_entry_problem') as builder:
            entries,records=probe_region_entries(root,column,{},None,deadline=time.monotonic()+1,
                candidate_seconds=.1,max_entry=0,channels=[face])
        builder.assert_not_called()
        self.assertEqual(entries[0]['firstValidatedState'],0)
        self.assertEqual(records[0]['reason'],'initial_membership')

    def test_expired_budget_retains_every_unknown_entry_as_unprobed(self):
        scene,episodes,p=airborne_case()
        entries,records=probe_region_entries({'scene':scene},episodes[1]['column'],episodes[1]['wall'],None,
            prefix=open_program(p['ladderEvents'][0]),deadline=time.monotonic()-1,candidate_seconds=.1,max_entry=6)
        self.assertEqual(len(entries),21)
        self.assertEqual({e['firstCandidateState'] for e in entries},set(range(7)))
        self.assertTrue(all('firstValidatedState' not in e for e in entries))
        self.assertEqual(records,[])

    def test_unknown_numerical_results_are_never_infeasibility(self):
        face={'family':'front','channel':'front:X:-1'}
        root={'scene':{},'base':{'ticks':0}}
        with patch('parkour_solver.search.entry.build_entry_problem',return_value=({},{})), \
                patch('parkour_solver.search.entry.solve',return_value={'feasible':False}):
            entries,records=probe_region_entries(root,{}, {},None,prefix={},channels=[face],
                deadline=time.monotonic()+1,candidate_seconds=.1,max_entry=2)
        self.assertEqual([e['firstCandidateState'] for e in entries],[0,1,2])
        self.assertTrue(all(r['status']=='unknown' for r in records))

    def test_worker_success_must_also_pass_independent_scalar_validation(self):
        face={'family':'front','channel':'front:X:-1'}
        with patch('parkour_solver.search.entry.build_entry_problem',return_value=({},{})), \
                patch('parkour_solver.search.entry.solve',return_value={'feasible':True,'candidate':{'yaw':[]}}), \
                patch('parkour_solver.search.entry.validate',return_value={'feasible':False}), \
                patch('parkour_solver.search.entry.check_geometry') as geometry:
            entries,records=probe_region_entries({'scene':{}}, {}, {}, None, prefix={},channels=[face],
                deadline=time.monotonic()+1,candidate_seconds=.1,max_entry=0)
        geometry.assert_not_called()
        self.assertNotIn('firstValidatedState',entries[0])
        self.assertTrue(all(r['status']=='unknown' and r['reason']=='Entry failed independent scalar validation' for r in records))

    def test_appended_entry_discovers_ceilings_from_the_carried_airborne_trace(self):
        folder=AUTONOMY_DATA
        evidence=current_events(json.loads((folder/'experiments/contact-combinations/appended-air-ceiling.json').read_text()))
        scene=prepare_scene(json.loads((folder/evidence['fixture']).read_text()))
        prefix=compose_ladder_episodes(1,None,evidence['episodes'][:1],max_inputs=48)
        choices=entry_contact_options({'scene':scene},prefix,6)
        expected=evidence['route']['contactEvents'][0]
        self.assertTrue(any(c['offset']==0 and c['plane']==expected['plane'] for cs in choices for c in cs))
        self.assertTrue(all(c['offset']>=0 for cs in choices for c in cs))

    def test_append_uses_shared_probe_and_keeps_delayed_entries(self):
        _,episodes,p=airborne_case()
        prefix=open_program(p['ladderEvents'][0])
        face=episodes[1]['entryFace']
        calls=[]
        def probe(whole,column,wall,**options):
            calls.append((whole,options))
            return [dict(face,firstValidatedState=5,unresolvedStates=[])],[{'status':'feasible','entryState':5}]
        result=append_ladder_programs(prefix,episodes[1]['column'],episodes[1]['wall'],toward=[.7,2.5],
            max_inputs=48,max_entry=6,max_programs=64,entry_probe=probe,entry_delay=3,max_contact_variants=0)
        self.assertEqual(len(calls),1)
        self.assertEqual(calls[0][0]['trace']['position'],prefix['trace']['position'])
        self.assertEqual(result['entryProbeMode'],'numerical')
        self.assertTrue(result['unprobedEntries'])
        self.assertEqual(result['generation']['entryWindows'][0]['entryStates'],[5,6,7,8])
        self.assertTrue(result['programs'])
        self.assertEqual(result['entryProbes'],[{'status':'feasible','entryState':5}])


@unittest.skipUnless(os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set PKC_IPOPT_PYTHON')
class MovementEntryIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.scene,self.episodes,self.program=airborne_case()
        self.compiler=PythonCompiler()
        self.addCleanup(self.compiler.close)

    def root(self,runs=0):
        scene=copy.deepcopy(self.scene)
        scene['maxRunTicksPerJump']=max(2,runs)
        scene['maxTotalRunTicks']=max(2,runs)
        if runs:
            scene['initial']['position']=[.5,1,-.9]
            scene['initial']['velocity'][0]=0
            scene['initial']['velocity'][2]=.2
        return entry_context(scene,{},('start',),(),'b',[runs],self.compiler,5)

    def accept(self,problem,plan):
        result=solve(problem,seconds=6,stop_on_feasible=True,python_executable=os.environ['PKC_IPOPT_PYTHON'])
        self.assertTrue(result['feasible'],{k:v for k,v in result.items() if k!='candidate'})
        candidate=_raw_candidate(problem,validate(problem,result['candidate']['yaw']))
        self.assertTrue(candidate['feasible'])
        vertical=dict(vertical=problem['vertical'],y=candidate['y'],vy=candidate['vy'],onGround=candidate['onGround'])
        checked=check_geometry(self.scene['collisionScene'],plan,candidate,vertical,self.compiler,5)
        self.assertTrue(checked['feasible'],checked)
        return candidate

    def test_initial_hit_on_entry_starts_climbing_without_preentry_cap(self):
        ep=self.episodes[0]
        problem,plan=build_entry_problem(self.root(),ep['column'],ep['wall'],ep['entryFace'],1,self.compiler,5,hit_on_entry=True)
        self.assertEqual(problem['ticks'],1)
        self.assertEqual(plan['ladderTicks'],[False,True])
        self.assertEqual(plan['jump'],[True])
        c=self.accept(problem,plan)
        self.assertEqual(c['vy'][-1],CLIMB_CARRY)
        self.assertFalse(c['onGround'][-1])

    def test_appended_entry_solves_whole_history_and_retains_run_retiming(self):
        prefix=open_program(self.program['ladderEvents'][0])
        ep=self.episodes[1]
        for runs in (0,2):
            with self.subTest(runs=runs):
                root=self.root(runs)
                approach=6 if runs else 5
                duration=prefix['profile']['airtime']+approach
                problem,plan=build_entry_problem(root,ep['column'],ep['wall'],ep['entryFace'],approach,self.compiler,5,
                                                  prefix=prefix,hit_on_entry=True)
                self.assertEqual(problem['ticks'],duration+runs)
                self.assertEqual(problem['position'],root['scene']['initial']['position'][::2])
                self.assertEqual(problem['velocity'],root['scene']['initial']['velocity'][::2])
                self.assertEqual(plan['jump'],[False]*runs+[True]+[False]*(duration-1))
                self.assertTrue(all(c['state']>runs for c in plan['contacts']))
                self.assertEqual(plan['vertical']['referencePosition'][runs:runs+7],prefix['trace']['position'])
                self.assertEqual(plan['ladderTicks'][-approach-1:],[False]*approach+[True])
                c=self.accept(problem,plan)
                self.assertEqual(c['vy'][-1],CLIMB_CARRY)
                self.assertFalse(any(c['onGround'][runs+1:]))

    def test_entry_after_four_climbs_keeps_every_prior_episode_free(self):
        folder=AUTONOMY_DATA
        evidence=json.loads((folder/'experiments/contact-combinations/five-climbing-ladders.json').read_text())
        self.scene=prepare_scene(json.loads((folder/evidence['fixture']).read_text()))
        root=entry_context(self.scene,{},('start',),(),self.scene['goalBlock'],[0],self.compiler,5)
        episodes=evidence['episodes']
        prefix=compose_ladder_episodes(self.scene['initial']['position'][1],None,episodes[:4],max_inputs=128,
                                      initial_velocity=self.scene['initial']['velocity'][1])
        ep=episodes[4]
        problem,plan=build_entry_problem(root,ep['column'],ep['wall'],ep['entryFace'],6,self.compiler,5,
                                          prefix=prefix,hit_on_entry=True)
        self.assertEqual(problem['ticks'],80)
        self.assertEqual(plan['vertical']['referencePosition'],evidence['candidate']['y'][:81])
        self.assertEqual(problem['position'],self.scene['initial']['position'][::2])
        self.assertEqual(sum(plan['jump']),1)
        problem['yaw']=evidence['candidate']['yaw'][:80]
        c=self.accept(problem,plan)
        self.assertEqual(c['vy'][-1],CLIMB_CARRY)
        self.assertFalse(any(c['onGround'][1:]))

    def test_append_entry_ceiling_uses_its_global_tick_and_original_y_history(self):
        folder=AUTONOMY_DATA
        evidence=current_events(json.loads((folder/'experiments/contact-combinations/appended-air-ceiling.json').read_text()))
        self.scene=prepare_scene(json.loads((folder/evidence['fixture']).read_text()))
        root=entry_context(self.scene,{},('start',),(),self.scene['goalBlock'],[0],self.compiler,5)
        episodes=evidence['episodes']
        prefix=compose_ladder_episodes(1,None,episodes[:1],max_inputs=48)
        ep=episodes[1]
        contact=copy.deepcopy(evidence['route']['contactEvents'][0])
        contact['offset']-=prefix['profile']['airtime']
        problem,plan=build_entry_problem(root,ep['column'],ep['wall'],ep['entryFace'],5,self.compiler,5,
                                          prefix=prefix,hit_on_entry=True,extra_contacts=[contact])
        self.assertEqual(plan['vertical']['contacts'][-1]['state'],7)
        self.assertEqual(plan['vertical']['referencePosition'],evidence['candidate']['y'][:12])
        problem['yaw']=evidence['candidate']['yaw'][:11]
        self.accept(problem,plan)

    def test_shared_probe_validates_a_later_region_and_reports_earlier_unknowns(self):
        prefix=open_program(self.program['ladderEvents'][0])
        ep=self.episodes[1]
        entries,records=probe_region_entries(self.root(),ep['column'],ep['wall'],self.compiler,prefix=prefix,
            channels=[ep['entryFace']],max_entry=6,deadline=time.monotonic()+15,candidate_seconds=.7,
            python_executable=os.environ['PKC_IPOPT_PYTHON'])
        validated=[e for e in entries if 'firstValidatedState' in e]
        self.assertTrue(validated,records)
        self.assertTrue(any(r['status']=='feasible' and r['geometryCheck']['feasible'] for r in records))
        self.assertTrue(all(e['firstCandidateState']<validated[0]['firstValidatedState'] for e in entries if 'firstCandidateState' in e))


if __name__ == '__main__':
    unittest.main()
