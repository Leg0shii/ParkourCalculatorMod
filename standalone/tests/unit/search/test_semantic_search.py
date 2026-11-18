import copy
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from parkour_solver.spatial.cells import decompose
from parkour_solver.spatial.regions import build_route_graph
from parkour_solver.spatial.compat.corridors import enumerate_routes
from parkour_solver.search.compat.semantic_adapter import SemanticCorridorAdapter
from parkour_solver.search.domain import InitialJumpDomain
from tests.support.cell_routes import side_scene
from tests.support.semantic_routes import support_scene
from tests.support.checkpoint_guidance import flat_scene


class SemanticStageTests(unittest.TestCase):
    def test_first_ready_proposal_is_scored_before_requesting_more(self):
        from parkour_solver.search.engine import find_checkpoint_route
        from tests.support.depth_first import program
        scene=support_scene();g=build_route_graph(scene,decompose(scene))
        routes,_=enumerate_routes(scene,decompose(scene),route_graph=g,max_routes=12,max_visits=1)
        route=next(r for r in routes if len(SemanticCorridorAdapter(scene,r).goals())==2)
        scored=[]
        def generate(s,*,report,**kwargs):
            cursor=dict(cursor=1,expanded=1,exhausted=False);report['continuation']=cursor
            p=program(scene,17,17);p['terminalSupport']='wall'
            yield p
            self.assertTrue(scored,'Buffered another proposal before scoring the ready arrival')
            cursor['cursor']=2
            p=program(scene,21,17);p['terminalSupport']='wall'
            yield p
        def evaluate(s,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});scored.append(p['profile']['airtime']);n=p['profile']['airtime']
            best=dict(candidate=dict(yaw=[0.]*n,positions=[[.5,0.]]*(n+1),velocity=[[.2,0.]]*(n+1),objective=0.),
                plan=dict(ticks=n,walls=[]),profiles=[p['profile']],allocation=[0],cost=0)
            work.deadline=0.
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),fullSceneValidated=True)
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(scene,object(),corridor=route,seconds=3,max_solver_calls=8)
        self.assertEqual(scored,[17])
        self.assertEqual(result['searchState']['stack'][0]['generation']['continuation']['cursor'],1)

    def test_new_child_continues_before_catalogue_drain_but_stall_yields(self):
        from parkour_solver.search.compat.corridor_queue import search_routes
        calls=[]
        def child(request,compiler,*,corridor,resume_state=None,**kwargs):
            calls.append(corridor.id)
            n=resume_state['cumulativeCalls'] if resume_state else 0
            return dict(status='unknown',termination='numerical_call_limit',best=None,work=dict(solverCalls=1),
                searchState=dict(version=6,cumulativeCalls=n+1,expanded=n+1,
                                 stack=[dict(id=0,index=1)],suspended=[],refinements=[]))
        options=dict(seconds=3,candidate_seconds=.2,score_seconds=.2,max_solver_calls=3,
                     score_call_limit=1,minimum_run_pruning=True)
        with patch('parkour_solver.search.compat.corridor_queue.annotate_normal_proposals',side_effect=lambda s,g,c,**k:g), \
             patch('parkour_solver.search.engine.find_checkpoint_route',side_effect=child):
            result=search_routes(flat_scene(),object(),spatial_settings=dict(max_routes=2,max_expansions=300),**options)
        self.assertEqual(calls,[0,0,1])
        self.assertEqual(set(result['searchState']['queue']),{0,1})

    def test_expired_slice_commits_a_scored_arrival_before_suspending(self):
        from parkour_solver.search.engine import find_checkpoint_route
        from tests.support.depth_first import program
        scene=support_scene();d=decompose(scene);g=build_route_graph(scene,d)
        routes,_=enumerate_routes(scene,d,route_graph=g,max_routes=12,max_visits=1)
        route=next(r for r in routes if len(SemanticCorridorAdapter(scene,r).goals())==2)
        def generate(s,*,report,**kwargs):
            self.assertEqual(s['goalBlock'],'wall')
            report['continuation']=dict(expanded=1,exhausted=True)
            for n in (17,21):
                p=program(scene,n,17);p['terminalSupport']='wall'
                yield p
        def evaluate(s,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});n=p['profile']['airtime']
            best=dict(candidate=dict(yaw=[0.]*n,positions=[[.5,0.]]*(n+1),velocity=[[.2,0.]]*(n+1),objective=0.),
                plan=dict(ticks=n,walls=[]),profiles=[p['profile']],allocation=[0],cost=0)
            work.deadline=0.
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),fullSceneValidated=True)
        def checkpoint(state):
            event=state['transitions'][-1]
            if event['kind']=='choose' and event['depth']==0:
                self.assertTrue(any(f.get('parent',{}).get('candidate')==event['candidate']
                                    for f in state['stack'] if f.get('parent')),
                                'Published a tried arrival without its child execution owner')
        def interrupt(window):
            raise TimeoutError('suspend_at_promotion')
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(scene,object(),corridor=route,seconds=3,max_solver_calls=8,
                                         on_progress=checkpoint,on_window=interrupt)
        self.assertEqual(result['work']['solverCalls'],1)
        self.assertEqual(result['searchState']['stack'][-1]['index'],1)
        self.assertTrue(result['windows'])

    def test_spatial_resume_keeps_each_running_variant_language_immutable(self):
        from parkour_solver.search.compat.corridor_queue import search_routes
        from parkour_solver.search.identity import digest
        def child(request,compiler,*,corridor,resume_state=None,**kwargs):
            context=digest(corridor.to_dict())
            if resume_state is not None:
                self.assertEqual(resume_state['context'],context)
            n=resume_state['cumulativeCalls'] if resume_state else 0
            return dict(status='unknown',termination='numerical_call_limit',best=None,work=dict(solverCalls=1),
                searchState=dict(version=6,context=context,cumulativeCalls=n+1,expanded=n+1,
                                 stack=[dict(id=0)],suspended=[],refinements=[]))
        options=dict(seconds=1,candidate_seconds=.2,score_seconds=.2,max_solver_calls=1,
                     score_call_limit=1,minimum_run_pruning=True)
        state=None
        with patch('parkour_solver.search.compat.corridor_queue.annotate_normal_proposals',side_effect=lambda s,g,c,**k:g), \
             patch('parkour_solver.search.engine.find_checkpoint_route',side_effect=child):
            for _ in range(10):
                result=search_routes(flat_scene(),object(),spatial_settings=dict(max_routes=12,max_expansions=20),
                                     resume_state=state,**options)
                state=json.loads(json.dumps(result['searchState'],allow_nan=False))
                self.assertEqual(len(state['queue']),len(set(state['queue'])))
        self.assertTrue(state['children'])
        self.assertGreater(len(state['routes']),1)

    def test_stage_views_are_immutable_and_support_order_is_discovered(self):
        scene=support_scene();d=decompose(scene);g=build_route_graph(scene,d)
        routes,_=enumerate_routes(scene,d,route_graph=g,max_routes=12,max_expansions=3000,max_visits=1)
        for route in routes:
            adapter=SemanticCorridorAdapter(scene,route,decomposition=d)
            if len(adapter.goals())>1:
                break
        self.assertGreater(len(adapter.goals()),1)
        first=adapter.for_goal(0)
        before=copy.deepcopy(first.corridor.to_dict())
        second=adapter.for_goal(1)
        self.assertEqual(first.corridor.to_dict(),before)
        self.assertNotEqual(first.corridor.graph_context,second.corridor.graph_context)
        self.assertLess(len(first.corridor.route_variants[0]['transitions']),
                        len(second.corridor.route_variants[0]['transitions']))
        self.assertIn('customYaw',adapter.outgoing_objective(0))
        self.assertEqual(first.gates,[])
        preliminary=first.scoped(dict(purpose='preliminary',solidIndices=[],through=1))
        self.assertIsNone(preliminary.constraint_guards({},None,dict(through=1),None))
        self.assertFalse(preliminary.candidate({}, {}, {})['routeCertified'])

    def test_initial_domain_is_independent_of_support_order(self):
        from parkour_solver.movement.program import empty_program
        scene=flat_scene();p=empty_program(scene['initial'],threshold=0.)
        domain=InitialJumpDomain([0,8],max_inputs=30)
        self.assertTrue(domain(p))
        self.assertTrue(InitialJumpDomain([9,10,11],max_inputs=30)(dict(profile=dict(
            airtime=12,jump=[False]*10+[True,False],grounded=[True]*13))))
        self.assertFalse(hasattr(domain,'remaining'))
        p['profile'].update(airtime=12,jump=[False]*9+[True]+[False]*2,grounded=[True]*13)
        self.assertFalse(domain(p))
        self.assertTrue(InitialJumpDomain(None,max_inputs=30)(p))
        p['profile']['jump'][9]=False;p['profile']['jump'][8]=True
        self.assertTrue(domain(p))


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for semantic route solves')
class SemanticNumericalTests(unittest.TestCase):
    def compiler(self):
        from parkour_solver.bridge.compiler import JavaCompiler
        return JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))

    def test_inferred_intermediate_support_uses_outgoing_guide_objective(self):
        from parkour_solver.search.engine import find_checkpoint_route
        from research.legacy_cell_routes.checkpoint_experiment import replay_witness
        scene=support_scene();scene['movementSearch']['maxPrefixes']=1
        d=decompose(scene);g=build_route_graph(scene,d)
        routes,_=enumerate_routes(scene,d,route_graph=g,max_routes=12,max_visits=1)
        route=next(r for r in routes if len(SemanticCorridorAdapter(scene,r).goals())==2)
        with self.compiler() as compiler:
            first=find_checkpoint_route(scene,compiler,corridor=route,seconds=12,max_solver_calls=8,
                candidate_seconds=.7,score_seconds=2,python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertIsNone(first['best'])
            self.assertTrue(first['windows'])
            self.assertLessEqual(first['expandedPrefixes'],1)
            result=find_checkpoint_route(scene,compiler,corridor=route,seconds=12,max_solver_calls=8,
                candidate_seconds=.7,score_seconds=2,resume_state=json.loads(json.dumps(first['searchState'])),
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'],'feasible',result['termination'])
            self.assertLessEqual(result['expandedPrefixes'],1)
            self.assertTrue(result['windows'])
            self.assertTrue(replay_witness(scene,result['best'],compiler)['passed'])
            self.assertEqual(result['searchState']['version'],6)

    def test_automatic_finite_landing_without_points_support_list_or_clocks(self):
        from parkour_solver.search.engine import find_checkpoint_route
        from research.legacy_cell_routes.checkpoint_experiment import replay_witness
        scene=flat_scene();original=copy.deepcopy(scene)
        with self.compiler() as compiler:
            result=find_checkpoint_route(scene,compiler,seconds=8,max_solver_calls=3,
                candidate_seconds=.7,score_seconds=2,discovery_settings=dict(maxGuideMaterializations=2,maxGraphExpansions=300),
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'],'feasible',result['termination'])
            self.assertTrue(replay_witness(scene,result['best'],compiler)['passed'])
            self.assertEqual(result['searchState']['kind'],'event_route_search')
            self.assertEqual(result['searchState']['dfs']['version'],8)
            self.assertLessEqual(result['work']['solverCalls'],3)
            self.assertLessEqual(result['expandedPrefixes'],scene['movementSearch']['maxPrefixes'])
            saved=json.loads(json.dumps(result['searchState'],allow_nan=False))
            resumed=find_checkpoint_route(scene,compiler,seconds=2,max_solver_calls=2,
                candidate_seconds=.7,score_seconds=2,discovery_settings=dict(maxGuideMaterializations=2,maxGraphExpansions=300),
                resume_state=saved,python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertTrue(resumed['resume']['exact'])
            self.assertEqual(resumed['best']['plan']['ticks'],result['best']['plan']['ticks'])
        self.assertEqual(scene,original)

    def test_free_virtual_finish_has_no_outstanding_physical_goal_contact(self):
        from parkour_solver.search.engine import find_checkpoint_route
        from research.legacy_cell_routes.checkpoint_experiment import replay_witness
        from parkour_solver.search.virtual_goals import satisfied
        scene=flat_scene();scene['movementSearch']['maxInputs']=20
        scene['objective']=dict(axis='X',sense='MAX')
        goal=dict(kind='landing_height_region',height=2.,rows=[dict(mode='X',cmp='GE',rhs=1.5),dict(mode='Z',cmp='GE',rhs=-2.)])
        with self.compiler() as compiler:
            result=find_checkpoint_route(scene,compiler,task_goal=goal,seconds=12,max_solver_calls=4,
                candidate_seconds=.7,score_seconds=2,discovery_settings=dict(maxGuideMaterializations=4,maxGraphExpansions=500),
                program_filter=InitialJumpDomain([0,1],max_inputs=20),python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['status'],'feasible',result['termination'])
            best=result['best']
            self.assertEqual(best['plan']['ticks'],9)
            self.assertFalse(best['candidate']['onGround'][-1])
            self.assertTrue(satisfied(best['movementProgram'],best['candidate'],goal))
            self.assertTrue(replay_witness(result['effectiveRequest'],best,compiler)['passed'])
            self.assertFalse(any(e['box']==scene['collisionScene']['solids'][1] for e in best['movementProgram']['profile']['contactEvents']))


if __name__=='__main__':unittest.main()
