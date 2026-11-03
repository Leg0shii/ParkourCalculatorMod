import copy
import gzip
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.minimum_runs import latest_jump, remember, exclusion
from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.movement_composition import empty_program, append_choice, boundary_state
from standalone.autonomy.open_movement import normal_choices, event_choice
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene


def flight(scene,runs):
    p=empty_program(scene['initial'],threshold=0.)
    for _ in range(runs):
        options=normal_choices(dict(scene=scene,state=boundary_state(p),maxInputs=20,threshold=0.))
        p=append_choice(p,next(c for c in options if not c['profile']['jump'][0] and c['terminalSupport']=='start'))
    options=normal_choices(dict(scene=scene,state=boundary_state(p),maxInputs=20,threshold=0.))
    return append_choice(p,next(c for c in options if c['profile']['jump'][0] and c['terminalSupport']=='goal'))


class MinimumRunTests(unittest.TestCase):
    def setUp(self):self.scene=flat_scene()

    def test_same_last_jump_discards_more_runs_but_keeps_equal_and_fewer(self):
        for minimum in (0,1,2,5,9):
            with self.subTest(minimum=minimum):
                reps={};p=flight(self.scene,minimum)
                remember(reps,latest_jump(p,self.scene),'feasible',p['profile']['airtime'])
                self.assertIsNone(exclusion(reps,latest_jump(flight(self.scene,minimum),self.scene)))
                q=exclusion(reps,latest_jump(flight(self.scene,minimum+1),self.scene))
                self.assertEqual((q['runTicks'],q['minimumFeasibleRunTicks']),(minimum+1,minimum))
                self.assertEqual(q['representative'],'feasible')
                if minimum:
                    shorter=flight(self.scene,minimum-1);description=latest_jump(shorter,self.scene)
                    self.assertIsNone(exclusion(reps,description))
                    remember(reps,description,'shorter_feasible',shorter['profile']['airtime'])
                    q=exclusion(reps,latest_jump(p,self.scene))
                    self.assertEqual(q['minimumFeasibleRunTicks'],minimum-1)
                    self.assertEqual(q['representative'],'shorter_feasible')

    def test_equal_run_timing_variants_and_distinct_obstacle_choices_survive(self):
        p=flight(self.scene,0);q=flight(self.scene,1)
        for v,delta in [(p,0),(q,1)]:
            v['profile']['positionConstraints'].append(dict(mode='X',t1=4+delta,cmp='LE',rhs=4.,name='chosen_side'))
            v['profile']['constraintOwners'].append(v['profile']['airtime'])
        reps={};remember(reps,latest_jump(p,self.scene),'a',p['profile']['airtime'])
        self.assertIsNotNone(exclusion(reps,latest_jump(q,self.scene)))
        q['profile']['positionConstraints'][-1]['cmp']='GE'
        self.assertIsNone(exclusion(reps,latest_jump(q,self.scene)))

    def test_interacting_or_unclassified_ground_preparation_is_retained(self):
        p=flight(self.scene,1)
        p['profile']['contactEvents'].append(dict(offset=0,axis='X',direction=1,plane=1.,box=[1,0,0,2,3,1]))
        self.assertIsNone(latest_jump(p,self.scene))
        p=flight(self.scene,1)
        p['profile']['positionConstraints'].append(dict(mode='X',t1=1,cmp='GE',rhs=.6,name='task'))
        p['profile']['constraintOwners'].append(0)
        self.assertIsNone(latest_jump(p,self.scene))

    def test_changed_prior_decisions_do_not_share_a_representative(self):
        p=flight(self.scene,0);q=flight(self.scene,1)
        q['profile']['positionConstraints'].append(dict(mode='X',t1=0,cmp='GE',rhs=.4,name='prior_decision'))
        q['profile']['constraintOwners'].append(0)
        reps={};remember(reps,latest_jump(p,self.scene),'a',p['profile']['airtime'])
        self.assertIsNone(exclusion(reps,latest_jump(q,self.scene)))


class ArchivedMinimumRunTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base=Path(__file__).parents[1]/'experiments/virtual-finish/evidence'
        cls.runs={}
        for name in ['cold','initial0-cold']:
            raw=json.loads(gzip.decompress((base/(name+'.json.gz')).read_bytes()));s=raw['result']['searchState']
            frames={f['id']:f for k in ['frames','stack','suspended','refinements'] for f in s[k]}
            order={t['candidate']:i for i,t in enumerate(s['transitions']) if t['kind']=='dispatch'}
            records=sorted((v for f in frames.values() for v in f['records'] if not v.get('variantOf')),key=lambda v:order.get(v['id'],-1))
            calls=[rec for rec in records for a in rec['numericalAttempts'] for d in a.get('diagnostics',[])]
            cls.runs[name]=(prepare_scene(raw['fixture']['request']),calls)

    def test_real11_last_jump_three_runs_excludes_four_not_total_fifteen(self):
        scene,cs=self.runs['cold'];d=latest_jump(cs[6]['program'],scene)
        self.assertEqual((d['groundState'],d['jumpInput'],d['runTicks']),(40,43,3))
        reps={};remember(reps,d,'call7',58)
        for call,runs in [(10,4),(11,8),(12,9)]:
            rejected=exclusion(reps,latest_jump(cs[call-1]['program'],scene))
            self.assertEqual((rejected['runTicks'],rejected['minimumFeasibleRunTicks']),(runs,3))

    def test_real11_zero_run_last_jump_excludes_all_delayed_prefix_calls(self):
        scene,cs=self.runs['initial0-cold'];d=latest_jump(cs[3]['program'],scene)
        self.assertEqual(d['runTicks'],0)
        reps={};remember(reps,d,'call4',55)
        for i in range(9,16):self.assertIsNotNone(exclusion(reps,latest_jump(cs[i-1]['program'],scene)))

    def test_real11_initial_histories_and_terminal_contacts_stay_distinct(self):
        scene,cs=self.runs['cold']
        self.assertNotEqual(latest_jump(cs[0]['program'],scene)['pattern'],latest_jump(cs[1]['program'],scene)['pattern'])
        scene,cs=self.runs['initial0-cold']
        self.assertNotEqual(latest_jump(cs[4]['program'],scene)['pattern'],latest_jump(cs[6]['program'],scene)['pattern'])


class MinimumRunDfsTests(unittest.TestCase):
    def search(self,*,enabled=True,unknown_zero=False,state=None,limit=30):
        scene=flat_scene();self.calls=[]
        def generate(s,*,report,continuation=None,initial_programs=None,checkpoint_tick=None,**kw):
            report['continuation']=dict(continuation or dict(cursor=0,expanded=0,exhausted=False));saved=report['continuation']
            ps=[flight(scene,r) for r in range(3)] if checkpoint_tick is None else [append_choice(initial_programs[0],
                event_choice(dict(threshold=0.),'normal',boundary_state(initial_programs[0]),checkpoint_tick-initial_programs[0]['profile']['airtime'],jumping=True))]
            while saved['cursor']<len(ps):
                p=ps[saved['cursor']];saved['cursor']+=1;yield p
            saved['exhausted']=True;report['termination']='exhausted'
        def evaluate(s,p,compiler,adapter,work,motion,**kw):
            work.before_solve({});n=p['profile']['airtime'];self.calls.append(n)
            best=None if unknown_zero and n==12 else dict(candidate=dict(yaw=[0.]*n,positions=[[.5,0.]]*(n+1),velocity=[[.2,0.]]*(n+1),objective=float(n)),
                plan=dict(ticks=n,walls=p['profile']['positionConstraints']),profiles=[p['profile']],allocation=[0],cost=0)
            return dict(status='feasible' if best else 'unknown',termination='done',best=best,
                work=dict(solverCalls=1),repairState=dict(pending=[],unresolved=[],constraintGeneration=dict(exhausted=True)))
        with patch('standalone.autonomy.cell_routes.checkpoint_search._motion_bound',return_value={}), \
             patch('standalone.autonomy.cell_routes.depth_first.iter_movement_programs',side_effect=generate), \
             patch('standalone.autonomy.cell_routes.depth_first.generate_choices',side_effect=lambda *a,**kw:(v for v in ())), \
             patch('standalone.autonomy.cell_routes.checkpoint_search.evaluate_program',side_effect=evaluate), \
             patch('standalone.autonomy.cell_routes.checkpoint_experiment.replay_witness',return_value=dict(passed=True)):
            return find_checkpoint_route(scene,object(),spatial_points=[[.5,1,.5],[5.5,1,.5]],checkpoints=[],
                spatial_goals=[dict(kind='landing',block='goal')],terminal_checkpoint=Checkpoint(30,-1,(1.,0.),(.5,1.,.5),(20.,1.,.5),()),
                seconds=3,max_solver_calls=limit,resume_state=state,minimum_run_pruning=enabled)

    def test_prunes_before_dispatch_and_can_replay_exclusions_as_a_control(self):
        r=self.search();self.assertEqual(self.calls,[12,30]);self.assertEqual(r['minimumRunPruning']['excluded'],2)
        self.assertEqual([v['proof']['runTicks'] for v in r['searchState']['minimumRunExclusions']],[1,2])
        self.search(enabled=False);self.assertEqual(self.calls,[12,30,13,30,14,30])

    def test_unknown_earlier_timing_is_preserved_and_does_not_establish_minimum(self):
        r=self.search(unknown_zero=True);self.assertEqual(self.calls,[12,13,30])
        self.assertEqual(r['searchState']['minimumRunExclusions'][0]['proof']['minimumFeasibleRunTicks'],1)
        self.assertTrue(any(v['status']=='unknown' for f in r['searchState']['frames'] for v in f['records']))

    def test_feasible_representative_survives_resume_and_policy_mismatch_is_rejected(self):
        r=self.search(limit=1);s=json.loads(json.dumps(r['searchState']))
        second=self.search(state=s);self.assertEqual(self.calls,[30]);self.assertEqual(second['minimumRunPruning']['excluded'],2)
        with self.assertRaisesRegex(ValueError,'Incompatible DFS'):self.search(enabled=False,state=s)
