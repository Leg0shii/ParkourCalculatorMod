from tests.support.depth_first import DepthFirstHarness
from tests.support.depth_first import program
from tests.support.paths import AUTONOMY_DATA
import copy
import itertools
import json
import math
import time
import unittest
from unittest.mock import patch

from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.spatial.checkpoints import Checkpoint
from parkour_solver.movement.program import empty_program, append_choice, boundary_state, truncate_program
from parkour_solver.movement.normal import event_choice
from tests.support.checkpoint_guidance import flat_scene
from parkour_solver.reachability.service import block_interaction
from parkour_solver.reachability.intervals import reachable_envelopes
from parkour_solver.constraints.variants import ConstraintVariants
from tests.support.archived_events import current_events


class DepthFirstTests(DepthFirstHarness):

    def test_common_goal_arrival_wins_and_completes_before_later_sibling(self):
        self.calls=[]
        result=self.run_search(10)
        self.assertEqual(self.calls,[(17,17),(21,21),(17,30),(21,30)])
        self.assertEqual(result['best']['candidate']['objective'],21.)
        for frame in result['searchState']['frames']:
            for record in frame['records']:
                self.assertEqual(record['objectiveMode'],'configured')
        chosen=[t for t in result['searchState']['transitions'] if t['kind']=='choose' and t['depth']==0]
        self.assertEqual([t['arrival'] for t in chosen],[17,21])
        self.assertFalse(result['objectiveOptimalityProven'])

    def test_deadline_preserves_incumbent_and_unvisited_sibling_without_redispatch(self):
        self.calls=[]
        first=self.run_search(3)
        self.assertEqual(first['best']['candidate']['objective'],17.)
        saved=json.loads(json.dumps(first['searchState'],allow_nan=False))
        second=self.run_search(10,saved)
        self.assertEqual(self.calls,[(17,17),(21,21),(17,30),(21,30)])
        self.assertEqual(second['best']['candidate']['objective'],21.)
        self.assertTrue(second['resume']['exact'])

    def test_in_flight_state_and_identity_survive_a_mid_solve_stop(self):
        self.calls=[];self.interrupt_once=(17,30)
        first=self.run_search(10)
        f=first['searchState']['stack'][-1]
        identity=f['inFlight']['id']
        self.assertEqual(f['inFlight']['program']['repairState']['marker'],'unfinished')
        second=self.run_search(10,json.loads(json.dumps(first['searchState'])))
        self.assertEqual(self.calls.count((17,17)),1)
        self.assertEqual(self.calls.count((21,21)),1)
        self.assertEqual(self.calls.count((17,30)),2)
        scored=[t for t in second['searchState']['transitions'] if t['kind']=='scored' and t['depth']==1]
        self.assertEqual(scored[0]['candidate'],identity)

    def test_other_valid_constraint_variants_are_not_lost_to_local_objective_ranking(self):
        self.calls=[];self.alternate_once=True
        result=self.run_search(10)
        self.assertEqual(self.calls.count((17,30)),2)
        self.assertEqual(self.calls.count((21,30)),1)
        self.assertTrue(any(r.get('variantOf') for f in result['searchState']['frames'] for r in f['records']))
        locations=[f['id'] for key in ('stack','suspended','refinements') for f in result['searchState'][key]]
        self.assertEqual(len(locations),len(set(locations)))

    def test_spatial_decision_goals_do_not_require_clock_windows(self):
        self.calls=[];self.no_clocks=True
        result=self.run_search(10)
        self.assertEqual(result['checkpoints'],[])
        self.assertEqual(self.calls,[(17,17),(21,21),(17,30),(21,30)])

    def test_old_checkpoint_state_is_not_silently_loaded_as_dfs(self):
        self.calls=[]
        with self.assertRaisesRegex(ValueError,'Incompatible DFS'):
            self.run_search(10,dict(version=1,context='old'))


class InteractionOwnershipTests(unittest.TestCase):
    def test_uncommitted_future_contact_cannot_leave_early_approach_rows(self):
        scene=flat_scene();p=empty_program(scene['initial'],threshold=0.)
        context=dict(threshold=0.)
        choice=event_choice(context,'normal',p['initial'],3,jumping=True,
            finite=[dict(leg=0,offset=2,axis='Y',direction=-1,plane=1.,boxes=[scene['collisionScene']['solids'][1]])],
            constraints=[dict(mode='X',t1=1,cmp='GE',rhs=2.,name='approach')])
        full=append_choice(p,choice)
        full['profile']['positionConstraints'].append(dict(mode='Z',t1=1,cmp='LE',rhs=1.,name='task'))
        full['profile']['constraintOwners'].append(0)
        short=truncate_program(full,2)
        self.assertEqual([r['name'] for r in short['profile']['positionConstraints']],['task'])
        self.assertFalse(short['profile']['contactEvents'])
        from parkour_solver.search.dfs import commit_arrival
        best=dict(plan=dict(walls=full['profile']['positionConstraints']),candidate=dict(yaw=[0.]*3))
        committed=commit_arrival(short,best,source_program=full)
        self.assertEqual([r['name'] for r in committed['profile']['positionConstraints']],['task'])
        self.assertEqual(len(committed['guideSeedYaw']),2)
        full['committedThrough']=3
        self.assertEqual(len(truncate_program(full,2)['profile']['positionConstraints']),2)

    def test_region_choices_shift_truncate_and_close_when_their_leg_is_kept(self):
        from parkour_solver.search.dfs import commit_arrival
        scene=flat_scene();p=empty_program(scene['initial'],threshold=0.)
        def rows(offset,rhs):
            return [dict(leg=0,offset=offset,mode='X',cmp='LE',rhs=rhs,name='contact:region')]
        first=event_choice(dict(threshold=0.),'normal',p['initial'],3,jumping=True,
            region_choices=[dict(offset=2,surface='surface:test',owner='goal',options=[rows(2,1.),rows(2,2.)])])
        program=append_choice(p,first)
        second=event_choice(dict(threshold=0.),'normal',boundary_state(program),2,
            region_choices=[dict(offset=1,surface='surface:test',owner='goal',options=[rows(1,3.),rows(1,4.)])])
        program=append_choice(program,second)
        self.assertEqual([(c['offset'],c['options'][0][0]['offset']) for c in program['profile']['regionChoices']],[(2,2),(4,4)])
        self.assertEqual([c['offset'] for c in truncate_program(program,3)['profile']['regionChoices']],[2])
        best=dict(plan=dict(walls=[dict(mode='X',t1=4,cmp='LE',rhs=3.,name='contact:region')]),candidate=dict(yaw=[0.]*5))
        self.assertNotIn('regionChoices',commit_arrival(program,best)['profile'])
        checked=commit_arrival(program,best,keep_region_choices=True)
        self.assertEqual(checked['profile']['regionChoices'],program['profile']['regionChoices'])

    def test_shared_gate_distinguishes_swept_phase_and_keeps_unknown(self):
        e=reachable_envelopes(dict(ticks=1,position=[0.,0.],velocity=[2.,0.],magnitude=[0.],friction=[.9],walls=[]),[1.,1.])
        box=[.8,0.,-.2,1.2,3.,.2]
        self.assertTrue(block_interaction(e,box,0,('Y',))['ruledOut'])
        self.assertFalse(block_interaction(e,box,0,('X',))['ruledOut'])
        self.assertFalse(block_interaction(e,box,99)['ruledOut'])
        high=copy.deepcopy(box);high[1]=10.;high[4]=11.
        self.assertTrue(block_interaction(e,high,0)['ruledOut'])

    def test_realized_grind_height_is_not_the_collision_probe_height(self):
        model=dict(ticks=1,position=[0.,0.],velocity=[.2,0.],magnitude=[.1],friction=[.9],walls=[])
        ceiling=[.35,2.9,-1.,1.,4.,1.]
        self.assertTrue(block_interaction(reachable_envelopes(model,[1.,1.]),ceiling,0)['ruledOut'])
        model['vertical']=dict(contacts=[dict(state=1,case='grind')])
        envelope=reachable_envelopes(model,[1.,1.])
        self.assertFalse(block_interaction(envelope,ceiling,0)['ruledOut'])
        far=ceiling[:];far[0]+=20;far[3]+=20
        self.assertTrue(block_interaction(envelope,far,0)['ruledOut'])
        clipped=dict(model,contacts=[dict(state=1,axis=0,plane=.02,direction=1)])
        probe=reachable_envelopes(clipped,[1.,1.])
        self.assertLess(probe['states'][1]['X'][1],.05)
        self.assertGreater(probe['phases'][0]['X']['X'][1],.05)
        self.assertFalse(block_interaction(probe,ceiling,0)['ruledOut'])


class VariantCoverageTests(unittest.TestCase):
    def test_distinct_options_cover_small_exhaustible_clear_trajectories(self):
        from parkour_solver.constraints.avoidance import consecutive
        from tests.support.obstacle_faces import faces
        from parkour_solver.scene.geometry import expanded_box
        box=[0.,0.,-.2,1.,3.,.2];n=2
        model=dict(ticks=n,position=[-1.,0.],velocity=[0.,0.],magnitude=[1.]*n,friction=[.9]*n,walls=[])
        envelope=reachable_envelopes(model,[1.]*(n+1))
        source=ConstraintVariants(dict(passageCaps=dict(horizontal=2,vertical=2,total=3),supportClearance=0.,contactEvents=[]),
            dict(position=[1.]*(n+1)),dict(events=[dict(jump=0,runTicks=0)]),dict(solids=[box]),envelope,
            dict(through=n,solidIndices=[0]))
        cursor={};options=[]
        while True:
            option=source.option(source.items[0],cursor)
            if option is None:break
            options.append(option)
        keys=[(source.effective_key(o['rows']),tuple(sorted(o['passage']['blockageCounts'].items()))) for o in options]
        self.assertEqual(len(keys),len(set(keys)))
        self.assertGreater(len(options),1)
        b=expanded_box(box)
        for angles in itertools.product(range(0,360,45),repeat=n):
            p=[[-1.,0.]];v=[0.,0.]
            for angle in angles:
                d=[v[0]+math.cos(math.radians(angle)),v[1]+math.sin(math.radians(angle))]
                p.append([p[-1][i]+d[i] for i in range(2)]);v=[x*.9 for x in d]
            def satisfies(rows):
                return all((p[r['t1']][0 if r['mode']=='X' else 1]-r['rhs'])*(1 if r['cmp']=='GE' else -1)>=-1e-9 for r in rows)
            # Test the existing face-chain language independently of optimized
            # generation. This measures exact union coverage, not option count.
            raw=False
            for chain in itertools.chain(itertools.permutations(faces(box),1),itertools.permutations(faces(box),2)):
                if len(chain)==2 and chain[0]['axis']==chain[1]['axis']:continue
                for gate in (range(n+1) if len(chain)==2 else [n]):
                    ranges=[(0,gate)] if len(chain)==1 else [(0,gate),(gate+int(consecutive(*chain)),n)]
                    if ranges[-1][0]>n:continue
                    valid=True
                    for face,(a,z) in zip(chain,ranges):
                        for t in range(a,z+1):
                            coord=1. if face['axis']=='Y' else p[t][0 if face['axis']=='X' else 1]
                            if face['direction']*(coord-face['plane']) < -1e-9:valid=False
                    if valid:raw=True;break
                if raw:break
            if raw:
                self.assertTrue(any(satisfies(o['rows']) for o in options),angles)


class BuilderGateTests(unittest.TestCase):
    def test_all_open_families_gate_before_creating_hard_block_rows(self):
        from parkour_solver.movement.normal import normal_choices
        from parkour_solver.movement.stepping import open_stepping_choices
        from parkour_solver.movement.ceiling_generation import open_ceiling_choices
        from parkour_solver.movement.program import boundary_state
        scene=flat_scene()
        scene['blocks'][1]['top']=1.5
        scene['collisionScene']['solids'][1][4]=1.5
        scene['blocks'].append(dict(id='ceiling',solidIndex=2,top=4.,sourceBlock='ceiling',bounds=[-1.,2.,-1.,2.]))
        scene['collisionScene']['solids'].append([-1.,3.,-1.,2.,4.,2.])
        p=empty_program(scene['initial'],threshold=0.)
        class Gate:
            def __init__(self):self.calls=0
            def query(self,*args,**kwargs):
                self.calls+=1
                return dict(ruledOut=True)
        for build,module in [(normal_choices,'open_movement'),(open_stepping_choices,'stepping_generation'),
                             (open_ceiling_choices,'ceiling_generation')]:
            gate=Gate()
            ctx=dict(scene=scene,program=p,state=boundary_state(p),maxInputs=12,threshold=0.,interactionGate=gate)
            with patch('parkour_solver.movement.normal.event_choice',side_effect=AssertionError('builder bypassed eligibility')):
                self.assertEqual(list(build(ctx)),[])
            self.assertGreater(gate.calls,0)


class CompilerRecoveryTests(unittest.TestCase):
    def test_timed_out_bridge_can_restart_for_independent_validation(self):
        import sys
        from parkour_solver.bridge.compiler import JavaCompiler
        script='import sys,json,time\nfor line in sys.stdin:\n m=json.loads(line);time.sleep(m.get("delay",0));print(json.dumps(m),flush=True)'
        with JavaCompiler(dict(command=[sys.executable,'-u','-c',script])) as compiler:
            with self.assertRaises(TimeoutError):compiler._exchange(dict(delay=.2),.03)
            self.assertEqual(compiler._exchange(dict(value=7),2),dict(value=7))


@unittest.skipUnless(__import__('os').environ.get('PKC_RUN_TICKS_COMPILER'),'Requires coefficient compiler')
class HistoricalInteractionTests(unittest.TestCase):
    def test_real11_b4_blip_is_excluded_before_its_wall_options_are_built(self):
        import gzip, os
        from pathlib import Path
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.bridge.compiler import JavaCompiler
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.movement.program import boundary_state
        from parkour_solver.movement.stepping import open_stepping_choices
        root=AUTONOMY_DATA
        fixture=json.loads((root/'experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json').read_text())
        scene=prepare_scene(fixture['request'])
        with gzip.open(root/'experiments/orchestrator-fixes/audit/candidate-ledger.json.gz','rt') as stream:
            saved=current_events(json.load(stream))
        original=next(p for p in saved['programs'] if p['label']=='P03')['record']['decision_program']
        p=truncate_program(original,21);p['terminalSupport']='b2'
        ctx=dict(mode='open',scene=scene,program=p,state=boundary_state(p),maxInputs=43,threshold=0.,interactionGate=None)
        b4=scene['collisionScene']['solids'][next(b['solidIndex'] for b in scene['blocks'] if b['id']=='b4')]
        ungated=[c for c in open_stepping_choices(dict(ctx)) if any(b4 in e['boxes'] for e in c['profile']['caseEvents'])]
        self.assertTrue(ungated)
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            gate=InteractionGate(scene,compiler)
            gate.context(ctx)
            gated=list(open_stepping_choices(ctx))
        self.assertFalse(any(e['box']==b4 for c in gated for e in c['profile']['caseEvents']))
        self.assertTrue(gate.exclusions)
