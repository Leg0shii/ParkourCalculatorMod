import copy
import json
import itertools
import math
import os
import random
from pathlib import Path
import unittest
from unittest.mock import patch

from standalone.autonomy.reachability import reachable_envelopes, interaction_reachability
from standalone.autonomy.solve_spec import solve_scope, processing_key, motion_record
from standalone.autonomy.prefix_viability import prefix_viability
from standalone.autonomy.movement_search import iter_movement_programs, movement_settings
from standalone.autonomy.movement_composition import empty_program, program_physics_key
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene, programs
from standalone.autonomy.cell_routes.time_progress import retain_two_goals
from standalone.autonomy.tests.test_time_progress import label
from standalone.ipopt import prepare, validate


class InteractionReachabilityTests(unittest.TestCase):
    def test_diagonal_distance_bound_rejects_box_relaxation_false_positive(self):
        envelope=reachable_envelopes(dict(ticks=1,position=[0.,0.],velocity=[0.,0.],magnitude=[1.],friction=[.9],
            walls=[dict(mode=axis,t1=1,cmp='GE',rhs=.9) for axis in ('X','Z')]))
        self.assertEqual(envelope['contradiction']['reason'],'norm_reach_contradiction')
    def test_all_acceleration_directions_and_swept_phases_remain_in_the_envelope(self):
        rng = random.Random(17923)
        for _ in range(40):
            n = 12
            model = prepare(dict(version=1, ticks=n, position=[rng.uniform(-3, 3), rng.uniform(-3, 3)],
                velocity=[rng.uniform(-.5, .5), rng.uniform(-.5, .5)], magnitude=[rng.uniform(.01, .35) for _ in range(n)],
                friction=[rng.uniform(.5, .95) for _ in range(n)], base=[0.]*n,
                yaw=[rng.uniform(-180, 180) for _ in range(n)], threshold=0., walls=[],
                objective=dict(axis='X', sense='MAX', tick=n)))
            candidate = validate(model, model['yaw'])
            for t in (4, 8, 12):
                for axis, coordinate in zip(('X', 'Z'), candidate['positions'][t]):
                    model['walls'].extend([dict(mode=axis,t1=t,cmp='GE',rhs=coordinate-.01),
                                           dict(mode=axis,t1=t,cmp='LE',rhs=coordinate+.01)])
            envelope = reachable_envelopes(model, [2.]*(n+1))
            self.assertIsNone(envelope['contradiction'])
            self.assertFalse(interaction_reachability(envelope,{},refine=True)['ruledOut'])
            for t, (a, b) in enumerate(zip(candidate['positions'], candidate['positions'][1:])):
                for phase, points in [('Y',[a]), ('X',[a,[b[0],a[1]]]), ('Z',[[b[0],a[1]],b])]:
                    for point in points:
                        for axis, value in zip(('X','Z'),point):
                            lo, hi = envelope['phases'][t][phase][axis]
                            self.assertLessEqual(lo-1e-7, value)
                            self.assertGreaterEqual(hi+1e-7, value)

    def test_required_and_unwanted_interactions_use_the_same_query(self):
        envelope = reachable_envelopes(dict(ticks=1,position=[0.,0.],velocity=[0.,0.],magnitude=[.1],friction=[.9],walls=[]), [1.,1.])
        self.assertTrue(interaction_reachability(envelope,[dict(mode='X',t1=1,cmp='GE',rhs=3.)])['ruledOut'])
        distant = dict(regions=[dict(tick=0,phase='X',bounds=[3.,0.,-1.,4.,2.,1.],interior=True)])
        self.assertTrue(interaction_reachability(envelope,distant)['ruledOut'])
        nearby = dict(regions=[dict(tick=0,phase='X',bounds=[-.2,0.,-.2,.2,2.,.2],interior=True)])
        self.assertFalse(interaction_reachability(envelope,nearby)['ruledOut'])
        self.assertFalse(interaction_reachability(envelope,dict(alternatives=[distant,nearby]))['ruledOut'])

    def test_linear_relaxation_rules_out_velocity_position_coupling(self):
        model=dict(ticks=2,position=[0.,0.],velocity=[0.,0.],magnitude=[1.,0.],friction=[1.,1.],threshold=0.,
            walls=[dict(mode='X',t1=1,cmp='GE',rhs=.9),dict(mode='X',t1=2,cmp='LE',rhs=1.5)])
        envelope=reachable_envelopes(model)
        self.assertFalse(interaction_reachability(envelope,{})['ruledOut'])
        self.assertEqual(interaction_reachability(envelope,{},refine=True)['reason'],'linear_control_relaxation_infeasible')
        model['walls'][-1]['rhs']=2.1
        self.assertFalse(interaction_reachability(reachable_envelopes(model),{},refine=True)['ruledOut'])

    def test_selected_clips_do_not_exclude_their_feasible_controls(self):
        rng=random.Random(37091)
        for axis in (0,1):
            for _ in range(15):
                model=prepare(dict(version=1,ticks=8,position=[0.,0.],velocity=[.2,-.1],
                    magnitude=[.2]*8,friction=[.91]*8,base=[0.]*8,yaw=[rng.uniform(-180,180) for _ in range(8)],
                    threshold=0.,walls=[],objective=dict(axis='X',sense='MAX',tick=8)))
                free=validate(model,model['yaw'])['positions']
                a,b=free[3][axis],free[4][axis]
                model['contacts']=[dict(state=4,axis=axis,plane=(a+b)/2,direction=1 if b>a else -1)]
                clipped=validate(model,model['yaw'])
                self.assertTrue(clipped['feasible'])
                for t in (4,8):
                    for name,value in zip(('X','Z'),clipped['positions'][t]):
                        model['walls'] += [dict(mode=name,t1=t,cmp='LE',rhs=value+1e-5),
                                           dict(mode=name,t1=t,cmp='GE',rhs=value-1e-5)]
                self.assertFalse(interaction_reachability(reachable_envelopes(model),{},refine=True)['ruledOut'])


class ScopeAndViabilityTests(unittest.TestCase):
    def test_complete_scope_cannot_silently_omit_a_block(self):
        scene=flat_scene();p=programs(scene,2)[0]
        with self.assertRaises(ValueError):
            solve_scope(scene,p,dict(purpose='complete',modeledBlocks=[scene['startBlock']]))
        value=solve_scope(scene,p,dict(purpose='preliminary',modeledBlocks=[scene['startBlock']]))
        self.assertEqual(value['solidIndices'],[0])

    def test_fall_below_every_floor_is_dead_only_when_a_contact_is_required(self):
        scene=flat_scene();p=copy.deepcopy(programs(scene,2)[0])
        p['profile']['position'][-1]=-3.
        p['profile']['velocity'][-1]=-.5
        p['profile']['grounded'][-1]=False
        self.assertTrue(prefix_viability(scene,p,required_contacts=['goal'],terminal_tick=20)['dead'])
        self.assertFalse(prefix_viability(scene,p,required_contacts=[],terminal_tick=20)['dead'])
        scene['blocks'].append(dict(id='lower',sourceBlock='lower',solidIndex=2,top=-4.,bounds=[-2.,2.,-2.,2.]))
        self.assertFalse(prefix_viability(scene,p,required_contacts=['goal'],terminal_tick=20)['dead'])

    def test_unsupported_first_event_family_is_not_proof_of_death(self):
        scene=flat_scene();p=copy.deepcopy(programs(scene,2)[0])
        p['profile']['position'][-1]=-3.;p['profile']['velocity'][-1]=-.5;p['profile']['grounded'][-1]=False
        self.assertFalse(prefix_viability(scene,p,required_contacts=['goal'],terminal_tick=20,enabled_types=['ladder_climb'])['dead'])


class PersistentGenerationTests(unittest.TestCase):
    def test_route_side_still_allows_clearing_an_obstacle_vertically(self):
        from standalone.autonomy.constraint_variants import ConstraintVariants
        route=dict(passageCaps=dict(horizontal=2,vertical=2,total=3),supportClearance=1e-6)
        profile=dict(airtime=3,position=[1.,2.5,2.5,1.])
        positions=[[-.5,.5],[.5,-.5],[1.5,-.5],[2.5,-.5]]
        box=[0.,0.,0.,1.,2.,1.]
        envelope=reachable_envelopes(dict(ticks=3,position=positions[0],velocity=[0.,0.],
            magnitude=[2.]*3,friction=[.9]*3,walls=[]),profile['position'])
        source=ConstraintVariants(route,profile,dict(events=[dict(jump=0,landing=3,runTicks=0)]),
            dict(solids=[box]),envelope,dict(through=3,solidIndices=[0]),
            gates=[dict(id=0,box=box,axis=0,lateral=2,plane=.5)],signature=[(0,1,-1)],reference=positions)
        def satisfied(variant):
            for row in variant['rows']:
                value=positions[row['offset']][0 if row['mode']=='X' else 1]
                if row['cmp']=='GE' and value<row['rhs'] or row['cmp']=='LE' and value>row['rhs']:
                    return False
            return True
        self.assertTrue(any(satisfied(v) for v in itertools.islice(source,1000)))

    def test_choice_and_queue_sizes_do_not_erase_a_late_candidate_after_reload(self):
        scene=flat_scene();cfg=dict(movement_settings(scene),maxInputs=1,maxPrefixes=1,maxQueuedPrefixes=2,maxChoicesPerType=2,types=['normal'])
        root=empty_program(scene['initial'],threshold=0.)
        def child(program, choice, **kwargs):
            p=copy.deepcopy(root);q=p['profile'];q.update(airtime=1,position=[1.,1.],velocity=[0.,0.],grounded=[True,True],
                jump=[False],sneak=[False],ladderTicks=[False,False],positionConstraints=[dict(mode='X',t1=1,cmp='GE',rhs=float(choice))])
            p['marker']=choice
            return p
        def generated(*args):return iter(range(30))
        with patch('standalone.autonomy.movement_frontier.generate_choices',side_effect=generated), \
             patch('standalone.autonomy.movement_frontier.append_choice',side_effect=child):
            report={}
            gen=iter_movement_programs(scene,settings=cfg,checkpoint_tick=1,threshold=0.,persistent=True,report=report)
            first=next(gen);saved=json.loads(json.dumps(report['continuation']));gen.close()
            resumed={}
            rest=list(iter_movement_programs(scene,settings=cfg,checkpoint_tick=1,threshold=0.,persistent=True,
                report=resumed,continuation=saved))
        self.assertEqual({p['marker'] for p in [first]+rest},set(range(30)))
        self.assertEqual(len(rest),29)
        self.assertEqual(resumed['queueDrops'],0)
        self.assertTrue(resumed['continuation']['exhausted'])

    def test_interrupted_screen_keeps_the_unprocessed_choice(self):
        scene=flat_scene();cfg=dict(movement_settings(scene),maxPrefixes=1,types=['normal'])
        report={}
        def interrupted(p):raise TimeoutError('screen_deadline')
        list(iter_movement_programs(scene,settings=cfg,checkpoint_tick=2,threshold=0.,persistent=True,report=report,screen=interrupted))
        state=json.loads(json.dumps(report['continuation']))
        self.assertIn('choice',state['expansion'])
        resumed={}
        values=list(iter_movement_programs(scene,settings=cfg,checkpoint_tick=2,threshold=0.,persistent=True,report=resumed,continuation=state))
        self.assertTrue(values)

    def test_constraint_generation_cursor_survives_a_new_facing_guess(self):
        from standalone.autonomy.constraint_variants import ConstraintVariants
        route=dict(passageCaps=dict(horizontal=2,vertical=2,total=3),supportClearance=1e-6)
        profile=dict(airtime=4,position=[1.]*5)
        plan=dict(events=[dict(jump=0,landing=4,runTicks=0)])
        geometry=dict(solids=[[0.,0.,-.2,1.,5.,.2]])
        envelope=reachable_envelopes(dict(ticks=4,position=[-2.,0.],velocity=[1.,0.],magnitude=[.3]*4,friction=[.9]*4,walls=[]),[1.]*5)
        scope=dict(through=4,solidIndices=[0])
        source=ConstraintVariants(route,profile,plan,geometry,envelope,scope)
        first=next(source);saved=json.loads(json.dumps(source.state,allow_nan=False))
        expected=list(itertools.islice(source,6))
        restored=ConstraintVariants(route,profile,plan,geometry,envelope,scope,state=saved,reference=[[99.,99.]]*5)
        actual=list(itertools.islice(restored,6))
        self.assertEqual(actual,expected)
        self.assertTrue(actual)
        self.assertNotEqual(first['rows'],actual[0]['rows'])


class StateProtectionTests(unittest.TestCase):
    def check_protected(self, field, value):
        a,b=label('best',10,5),label('protected',15,3)
        b[0]['clock_results']['clock:10']['state'][field]=value
        kept,_=retain_two_goals([a,b],cap=10)
        self.assertEqual({r['id'] for r,_ in kept},{'best','protected'})

    def test_forward_velocity_and_speed_are_independent_guards(self):
        self.check_protected('forwardVelocity',.2)
        self.check_protected('speed',.3)

    def test_different_schedules_are_not_clock_compared_even_with_equal_counts(self):
        a,b=label('best',10,5),label('other',15,3)
        b[0]['clock_results']['clock:10']['state']['motion']['schedule'][0]='J'
        kept,_=retain_two_goals([a,b],cap=10)
        self.assertEqual(len(kept),2)

    def test_missing_velocity_keeps_candidate_unknown_rather_than_inferior(self):
        a,b=label('best',10,5),label('unknown',15,3)
        b[0]['clock_results']['clock:10'].pop('state')
        self.assertEqual(len(retain_two_goals([a,b],cap=10)[0]),2)

    def test_queue_key_counts_effective_grounded_inputs(self):
        p=dict(profile=dict(airtime=4,grounded=[True,True,False,False,False],jump=[False,True,True,False]))
        self.assertEqual(processing_key(p,'x'),(4,-1,1,'x'))


class OuterResumeTests(unittest.TestCase):
    def test_split_search_does_not_dispatch_the_completed_terminal_again(self):
        from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
        from standalone.autonomy.cell_routes.checkpoints import Checkpoint
        scene=flat_scene();seeds=list(programs(scene,2))[:2]
        self.assertEqual(len(seeds),2)
        cp=Checkpoint(2,-1,(1.,0.),tuple(scene['initial']['position']),(5.5,1.,.5),())
        calls=[]
        def generated(scene,*,report,continuation=None,**kwargs):
            state=copy.deepcopy(continuation) if continuation else dict(cursor=0,expanded=0,exhausted=False)
            report['continuation']=state
            while state['cursor'] < len(seeds):
                value=copy.deepcopy(seeds[state['cursor']]);state['cursor']+=1
                yield value
            state['exhausted']=True;report['termination']='exhausted'
        def evaluate(scene,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});identity=program_physics_key(p);calls.append(identity)
            score=float(seeds.index(next(q for q in seeds if program_physics_key(q)==identity)))
            best=dict(allocation=[0],cost=0,plan=dict(ticks=2,walls=[]),profiles=[p['profile']],
                candidate=dict(objective=score,positions=[[0.,0.],[score,0.],[score,0.]],velocity=[[0.,0.]]*3,yaw=[0.,0.]))
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),
                fullSceneValidated=True,repairState=dict(pending=[],unresolved=[],constraintGeneration=dict(exhausted=True)))
        class Compiler:pass
        options=dict(terminal_checkpoint=cp,spatial_points=[[.5,1.,.5],[5.5,1.,.5]],seconds=3,rescue_passes=0)
        with patch('standalone.autonomy.cell_routes.checkpoint_search._motion_bound',return_value={}), \
             patch('standalone.autonomy.cell_routes.depth_first.iter_movement_programs',side_effect=generated), \
             patch('standalone.autonomy.cell_routes.depth_first.generate_choices',side_effect=lambda *args,**kwargs:(v for v in ())), \
             patch('standalone.autonomy.cell_routes.checkpoint_search.evaluate_program',side_effect=evaluate), \
             patch('standalone.autonomy.cell_routes.checkpoint_experiment.replay_witness',return_value=dict(passed=True)):
            first=find_checkpoint_route(scene,Compiler(),max_solver_calls=1,**options)
            saved=json.loads(json.dumps(first['searchState']))
            second=find_checkpoint_route(scene,Compiler(),max_solver_calls=5,resume_state=saved,**options)
        self.assertEqual(len(calls),2)
        self.assertEqual(len(set(calls)),2)
        self.assertTrue(second['resume']['exact'])
        self.assertEqual(second['best']['candidate']['objective'],1.)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),'Requires standalone numerical bridge')
class ScopedNumericalTests(unittest.TestCase):
    def test_omitted_future_obstacle_does_not_reject_preliminary_guidance(self):
        from standalone.autonomy.compiler import JavaCompiler
        from standalone.autonomy.cell_routes.geometry import decompose
        from standalone.autonomy.cell_routes.routes import corridor_for_points,landmarks
        from standalone.autonomy.cell_routes.local import CorridorAdapter,SearchWork
        from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program,_motion_bound
        from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
        scene=flat_scene()
        scene['initial']['velocity'][0]=.4
        scene['collisionScene']['solids'][1]=[4.,0.,-1.,5.,1.,1.]
        scene['blocks'][1]['bounds']=[4.,5.,-1.,1.]
        scene['collisionScene']['solids'].append([2.,0.,.3,3.,4.,1.])
        scene['blocks'].append(dict(id='wall',sourceBlock='wall',solidIndex=2,top=4.,bounds=[2.,3.,.3,1.]))
        program=next(p for p in programs(scene,12) if p['terminalSupport']=='goal' and p['profile']['jump'][0])
        d=decompose(scene);g=landmarks(scene)
        corridor=corridor_for_points(d,[[.5,1.,.5],[1.5,1.,-1.],[4.,1.,-1.],[4.5,1.,.5]],g)
        adapter=CorridorAdapter(scene,corridor,g,decomposition=d)
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            motion=_motion_bound(scene,compiler,3)
            preliminary=evaluate_program(scene,program,compiler,adapter,SearchWork(12),motion,
                seconds=8,candidate_seconds=1,solver_calls=12,python_executable=os.environ['PKC_IPOPT_PYTHON'],
                scope=dict(purpose='preliminary',modeledBlocks=['start','goal']))
            self.assertIsNotNone(preliminary['best'],preliminary['termination'])
            self.assertFalse(preliminary['fullSceneValidated'])
            self.assertFalse(replay_witness(scene,preliminary['best'],compiler)['geometry']['feasible'])
            program['guideSeedYaw']=preliminary['best']['candidate']['yaw']
            full=evaluate_program(scene,program,compiler,adapter,SearchWork(24),motion,
                seconds=12,candidate_seconds=1,solver_calls=24,python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertIsNotNone(full['best'],full['termination'])
            self.assertTrue(full['fullSceneValidated'])
            self.assertTrue(replay_witness(scene,full['best'],compiler)['passed'])


if __name__ == '__main__':
    unittest.main()
