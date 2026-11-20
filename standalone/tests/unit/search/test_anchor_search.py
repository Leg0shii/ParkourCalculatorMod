import copy
import json
import os
import time
import unittest
from unittest.mock import patch

from parkour_solver.movement.program import empty_program
from parkour_solver.movement.settings import movement_settings
from parkour_solver.search.frontier import persistent_programs
from parkour_solver.search.anchors.policy import local_counts, single_jump_structure
from parkour_solver.search.feasibility import meaningful_choice
from parkour_solver.search.application import run
from tests.support.checkpoint_guidance import flat_scene
from tests.support.repair_resume import route, outcome, VERTICAL
from parkour_solver.search.repair import solve_contact_branches


class AnchorSearchTests(unittest.TestCase):
    def frontier(self, evaluator, *, continuation=None, report=None, limit=15, latched=None):
        s=flat_scene()
        cfg=dict(movement_settings(s),maxPrefixes=limit,maxInputs=60)
        return persistent_programs(s,settings=cfg,report=report if report is not None else {},
            continuation=continuation,threshold=0.,node_evaluator=evaluator,
            program_filter=lambda p:local_counts(p)['R']<=2 and (not latched or not latched() or
                single_jump_structure(s,p,0,'start','goal',complete=p['terminalSupport']=='goal')),
            policy_context=dict(version=1,span=0),deadline=time.monotonic()+3,context_adapter=lambda c:c.update(interactionGate=None))

    def test_take_hashes_only_tied_programs_and_matches_full_key_order(self):
        from parkour_solver.search.frontier import take_first
        def key(p,identity):return (p['T'],-p['J'],identity)
        identity=lambda p:p['id']
        for items in ([dict(T=3,J=1,id='a'),dict(T=2,J=1,id='z'),dict(T=2,J=2,id='y'),dict(T=2,J=2,id='b')],
                      [dict(T=5,J=0,id='q'),dict(T=4,J=0,id='p')],
                      [dict(T=1,J=0,id='same'),dict(T=1,J=0,id='same',copy=True)]):
            expected=min(items,key=lambda p:key(p,identity(p)))
            hashed=[]
            def counted(p):
                hashed.append(p['id'])
                return identity(p)
            queue=list(items)
            self.assertIs(take_first(queue,key,counted),expected)
            self.assertEqual(len(queue),len(items)-1)
            low=min(key(p,'')[:-1] for p in items)
            tied=[p['id'] for p in items if key(p,'')[:-1]==low]
            self.assertEqual(hashed,tied if len(tied)>1 else [])

    def test_held_alternatives_restore_one_at_a_time_when_their_branch_dies(self):
        from parkour_solver.search.frontier import restore_alternatives
        def node(name):return dict(decisionNode=name)
        def fresh(**fields):
            state=dict(pending=[],ready=[],expansion=None,deferredNodes=[],nodeInFlight=None,succeededNodes=[],
                decisionParents={'N':'P','c':'N','g':'c','a0':'P','a1':'P','x':'a0'},
                alternatives={'N':dict(programs=[node('a0'),node('a1')],restored=[],next=0)})
            state.update(fields)
            return state
        state=fresh(pending=[node('g')])
        self.assertFalse(restore_alternatives(state))
        self.assertFalse(restore_alternatives(fresh(),extra_live=[node('c')]))
        self.assertFalse(restore_alternatives(fresh(succeededNodes=['N'])))
        state['pending']=[]
        self.assertTrue(restore_alternatives(state))
        self.assertEqual(state['pending'],[node('a0')])
        # The restored alternative's own subtree keeps the next one held, also across a saved resume.
        state=json.loads(json.dumps(state))
        state['pending']=[node('x')]
        self.assertFalse(restore_alternatives(state))
        state['pending']=[]
        self.assertTrue(restore_alternatives(state))
        self.assertEqual(state['pending'],[node('a1')])
        state['pending']=[]
        self.assertFalse(restore_alternatives(state))
        self.assertEqual(state['alternatives']['N'],dict(programs=[node('a0'),node('a1')],restored=['a0','a1'],next=2))

    def test_watched_alternatives_wait_for_every_watched_branch(self):
        from parkour_solver.search.frontier import restore_alternatives
        def node(name):return dict(decisionNode=name)
        def fresh(**fields):
            state=dict(pending=[],ready=[],expansion=None,deferredNodes=[],nodeInFlight=None,succeededNodes=[],
                decisionParents={'R':'P','o1':'P','o2':'P','c':'o2'},
                alternatives={'R':dict(programs=[node('R')],restored=[],next=0,watch=['o1','o2'])})
            state.update(fields)
            return state
        self.assertFalse(restore_alternatives(fresh(pending=[node('o1')])))
        self.assertFalse(restore_alternatives(fresh(ready=[node('c')])))
        self.assertFalse(restore_alternatives(fresh(succeededNodes=['o2','P'])))
        # The parent P staying alive through other children does not hold the reserve back.
        state=fresh(pending=[node('P')])
        self.assertTrue(restore_alternatives(state))
        self.assertEqual(state['pending'],[node('P'),node('R')])

    def test_frontier_restores_a_held_alternative_after_the_winner_subtree_is_rejected(self):
        calls=[]
        def evaluate(p,nodes,forced):
            calls.append(p['decisionParent'])
            if not calls[1:]:
                held=copy.deepcopy(p);held.pop('needsNodeSolve',None)
                # A held alternative differs from the winner in its retained rows.
                held['profile']['positionConstraints'].append(dict(mode='X',t1=1,cmp='GE',rhs=-1e6,name='held'))
                held['profile'].get('constraintOwners',[]).append(0)
                held.update(decisionNode='held:0',alternativeOf=p['decisionNode'])
                return dict(status='feasible',program=p,alternatives=[held])
            return dict(status='oracle_rejected' if p['decisionParent']!='held:0' else 'feasible',program=p)
        report={}
        source=self.frontier(evaluate,report=report,limit=30)
        try:list(source)
        finally:source.close()
        held=report['continuation']['alternatives']
        winner,=held
        self.assertEqual(held[winner]['restored'],['held:0'])
        self.assertIn('held:0',calls)

    def test_multiple_same_block_jumps_generated_and_no_solve_for_run_ticks(self):
        calls=[]
        def evaluate(p,nodes,forced):
            calls.append(copy.deepcopy(p))
            return dict(status='feasible',program=p)
        source=self.frontier(evaluate,limit=30)
        try:
            programs=list(source)
        finally:source.close()
        self.assertTrue(any(local_counts(p)['J']>=2 for p in calls))
        self.assertTrue(programs)
        # Run ticks can construct longer preparations but never dispatch by themselves.
        self.assertTrue(all(local_counts(p)['J']>=1 for p in calls))
        self.assertTrue(all(p['decisionDepth']>=1 for p in calls))

    def test_rejected_child_preserves_parent_and_other_children(self):
        queried=[];report={}
        def evaluate(p,nodes,forced):
            queried.append((p['decisionNode'],p['decisionParent']))
            return dict(status='oracle_rejected',program=p)
        source=self.frontier(evaluate,report=report,limit=4)
        try:programs=list(source)
        finally:source.close()
        self.assertGreater(len(queried),1)
        self.assertTrue(programs,'Rejected local-hop child swallowed the direct goal sibling')
        self.assertNotIn('status',report['continuation']['pending'][0] if report['continuation']['pending'] else {})

    def test_unresolved_node_is_parked_without_blocking_or_retried_on_implicit_resume(self):
        report={};queried=[]
        def evaluate(p,nodes,forced):
            queried.append(p['decisionNode'])
            p['repairState']=dict(unresolved=[dict(branch='unknown')],pending=[],inFlight=None,
                                  constraintGeneration=dict(exhausted=True))
            return dict(status='suspended',program=p,deferUnknown=True)
        source=self.frontier(evaluate,report=report,limit=4)
        try:programs=list(source)
        finally:source.close()
        self.assertTrue(programs,'Unknown source hop blocked the direct goal sibling')
        saved=json.loads(json.dumps(report['continuation']))
        retained={p['decisionNode'] for p in saved['deferredNodes']}
        self.assertTrue(retained)
        self.assertIsNone(saved.get('nodeInFlight'))
        before=len(queried)
        resumed=self.frontier(evaluate,report={},continuation=saved,limit=4)
        try:list(resumed)
        finally:resumed.close()
        self.assertFalse(retained.intersection(queried[before:]))

    def test_unknown_retry_is_explicit_and_preserves_the_exact_node(self):
        from parkour_solver.search.feasibility import restore_unknown_nodes, unresolved_only
        node=dict(decisionNode='exact-child',decisionParent='actual-parent',repairState=dict(unresolved=[1]))
        source=dict(pending=[],deferredNodes=[node],exhausted=True)
        self.assertFalse(restore_unknown_nodes(source,False))
        self.assertEqual(source['deferredNodes'],[node])
        self.assertTrue(restore_unknown_nodes(source,True))
        self.assertIs(source['pending'][0],node)
        self.assertFalse(source['exhausted'])
        self.assertTrue(unresolved_only(dict(unresolved=[1],pending=[],inFlight=None)))
        self.assertFalse(unresolved_only(dict(unresolved=[1],pending=[dict(next='work')])))
        self.assertFalse(unresolved_only(dict(unresolved=[1],constraintGeneration=dict(exhausted=False))))

    def test_learned_latch_and_resume_keep_source_setup_hops_in_the_actual_source(self):
        report={};latched=False;calls=[]
        def evaluate(p,nodes,forced):
            calls.append(copy.deepcopy(p))
            return dict(status='feasible',program=p)
        source=self.frontier(evaluate,report=report,latched=lambda:latched)
        first=next(source);source.close()
        self.assertEqual(local_counts(first)['J'],1)
        latched=True
        saved=json.loads(json.dumps(report['continuation']))
        resumed=self.frontier(evaluate,report={},continuation=saved,limit=30,latched=lambda:latched)
        try:programs=list(resumed)
        finally:resumed.close()
        self.assertTrue(any(local_counts(p)['J']>=2 for p in programs))
        self.assertTrue(any(p['terminalSupport']=='start' and local_counts(p)['J']>=1 for p in calls))

    def test_suspended_exact_child_resumes_same_choice_before_siblings(self):
        calls=[];report={}
        def suspend(p,nodes,forced):
            calls.append(p['decisionNode']);p['repairState']=dict(marker='unfinished')
            return dict(status='suspended',program=p)
        source=self.frontier(suspend,report=report)
        try:list(source)
        finally:source.close()
        saved=json.loads(json.dumps(report['continuation']))
        self.assertEqual(saved['nodeInFlight']['repairState']['marker'],'unfinished')
        def resume(p,nodes,forced):
            self.assertEqual(p['decisionNode'],calls[0])
            self.assertEqual(p['repairState']['marker'],'unfinished')
            return dict(status='suspended',program=p)
        source=self.frontier(resume,continuation=saved,report={})
        try:list(source)
        finally:source.close()

    def test_stop_policy_keeps_native_validation_and_cannot_reuse_polished_context(self):
        requested=[]
        def solve(request,*args,**kwargs):
            requested.append(kwargs['stop_on_feasible']);return outcome()
        def invoke(**options):
            with patch('parkour_solver.search.repair.find_route',side_effect=solve), \
                 patch('parkour_solver.search.repair.validate_vertical_schedule',return_value=VERTICAL):
                return solve_contact_branches(route(),[],[],object(),seconds=1,candidate_seconds=.1,retries=0,
                    optimize_goal=True,rank_equal_cost=False,
                    candidate_filter=lambda *a:dict(accepted=True),**options)
        first=invoke(stop_on_feasible=True)
        second=invoke(stop_on_feasible=False,resume_state=first['repairState'])
        self.assertEqual(requested,[True,False])
        self.assertFalse(second['repairResume']['compatible'])

    def test_a_relaxed_repair_state_never_resumes_under_ipopt(self):
        from parkour_solver.search.feasibility import policy_spec
        def invoke(backend,resume_state=None):
            scope=dict(solvePolicy=policy_spec(dict(numerical=backend)))
            with patch('parkour_solver.search.repair.find_route',return_value=outcome()), \
                 patch('parkour_solver.search.repair.validate_vertical_schedule',return_value=VERTICAL):
                return solve_contact_branches(route(),[],[],object(),seconds=1,candidate_seconds=.1,retries=0,
                    repair_scope=scope,candidate_filter=lambda *a:dict(accepted=True),resume_state=resume_state)
        relaxed=invoke('relaxed')
        self.assertTrue(invoke('relaxed',relaxed['repairState'])['repairResume']['compatible'])
        self.assertFalse(invoke('ipopt',relaxed['repairState'])['repairResume']['compatible'])
        with self.assertRaises(ValueError):
            policy_spec(dict(numerical='gurobi'))

    def test_completed_oracle_rejects_only_selected_model_and_limits_stay_pending(self):
        def invoke(worker_status,termination='worker_finished'):
            response=dict(status='unknown',best=None,termination='search_exhausted',attempts=[dict(diagnostics=[
                dict(status='unknown',termination=termination,workerStatus=worker_status)])])
            with patch('parkour_solver.search.repair.find_route',return_value=response):
                return solve_contact_branches(route(),[],[],object(),seconds=1,candidate_seconds=.1,retries=0,
                    oracle_rejection=True)
        rejected=invoke('Infeasible_Problem_Detected')['repairState']
        limited=invoke('Maximum_CpuTime_Exceeded')['repairState']
        self.assertTrue(rejected['completed'][0]['oracleRejected'])
        self.assertEqual(rejected['unresolved'],[])
        self.assertFalse(limited['completed'][0]['oracleRejected'])
        self.assertEqual(len(limited['unresolved']),1)
        # A relaxation-infeasible request excludes the full model.
        self.assertTrue(invoke('PrimalInfeasible','disk_finished')['repairState']['completed'][0]['oracleRejected'])

    def test_pre_solve_proofs_complete_an_alternative_without_ipopt(self):
        from parkour_solver.search.feasibility import outcome, policy_spec
        certificate=dict(reason='disk_control_relaxation_infeasible')
        def invoke(termination):
            response=dict(status='unknown',best=None,termination=termination,
                          attempts=[dict(status='blocked',certificate=certificate)])
            with patch('parkour_solver.search.repair.find_route',return_value=response):
                return solve_contact_branches(route(),[],[],object(),seconds=1,candidate_seconds=.1,retries=0,
                    oracle_rejection=True)
        proven=invoke('search_exhausted')
        completed,=proven['repairState']['completed']
        self.assertEqual((completed['proven'],completed['oracleRejected'],completed['certificates']),(True,False,[certificate]))
        self.assertEqual(proven['repairState']['unresolved'],[])
        policy=policy_spec(dict(purpose='node',oracleRejection=True),first_witness=True)
        self.assertEqual(outcome(proven,'model',policy)['status'],'oracle_rejected')
        interrupted=invoke('deadline')['repairState']
        self.assertFalse(interrupted['completed'][0]['proven'])
        self.assertEqual(len(interrupted['unresolved']),1)

    def test_actual_landing_clock_binds_before_unused_suffix_and_drops_owned_future_rows(self):
        from parkour_solver.movement.normal import event_choice
        from parkour_solver.movement.program import boundary_state
        from parkour_solver.movement.contact_combinations import expanded_face
        s=flat_scene();root=empty_program(s['initial'],threshold=0.);root['terminalSupport']='start'
        goal=next(b for b in s['blocks'] if b['id']=='goal')
        box=s['collisionScene']['solids'][goal['solidIndex']]
        choice=event_choice(dict(threshold=0.),'normal',boundary_state(root),13,jumping=True,
            vertical=[dict(state=12,plane=1.,direction=-1)],
            finite=[dict(offset=11,axis='Y',direction=-1,boxes=[box],surface=None,regions=[expanded_face(box,1,1,1e-6)])],
            constraints=[dict(mode='X',t1=13,cmp='GE',rhs=100.,name='future-owned')])
        # The future row belongs to a later event, not the achieved landing.
        choice['profile']['constraintOwners']=[13]
        report={}
        with patch('parkour_solver.search.frontier.generate_choices',return_value=iter([choice])):
            source=persistent_programs(s,settings=movement_settings(s),report=report,initial_programs=[root],
                threshold=0.,node_evaluator=lambda *a:self.fail('Arrival should use mandatory anchor solve'),
                policy_context=dict(version=1),deadline=time.monotonic()+2)
            p=next(source);source.close()
        self.assertEqual(p['profile']['airtime'],12)
        self.assertEqual(p['terminalSupport'],'goal')
        self.assertEqual(p['profile']['positionConstraints'],[])


def step_scene():
    # The goal is one block higher than the start: only a jump reaches it.
    from parkour_solver.scene.normalize import prepare_scene
    return prepare_scene(dict(version=1, sceneModel='collision_boxes', geometryBounds=[-10,-5,-10,20,12,10],
        initial=dict(position=[.5,1,.5], velocity=[.25,-.0784000015258789,0], yaw=-90),
        startBlock='start', goalBlock='goal', maxWallHits=0, maxContactCombinations=0,
        blocks=[dict(id='start', boxes=[[-2,0,-2,3,1,2]]), dict(id='goal', boxes=[[5,1,-1,6,2,1]])],
        movementSearch=dict(types=['normal'], maxInputs=40)))


class MinimumRunTickTests(unittest.TestCase):
    def frontier(self, *, screen=None, ordinary=True, continuation=None, live=(), report=None, scene=None,
                 policy=True, keep_emitted=False):
        from parkour_solver.movement.program import boundary_state
        from parkour_solver.search.anchors.policy import advantageous_targets, next_anchor
        s=scene or step_scene()
        run_policy=dict(ordinary=lambda p:ordinary and boundary_state(p)['onGround'],
                        targets=lambda b,c:advantageous_targets(s,b,c,next_anchor(s,b,[],'goal')))
        report=report if report is not None else {}
        source=persistent_programs(s,settings=dict(movement_settings(s),maxPrefixes=1),report=report,
            continuation=continuation,threshold=0.,node_evaluator=lambda p,n,x:dict(status='feasible',program=p),
            policy_context=dict(version=1,span=0),deadline=time.monotonic()+20,screen=screen,
            context_adapter=lambda c:c.update(interactionGate=None),run_policy=run_policy if policy else None,
            live_programs=lambda:list(live)+(emitted if keep_emitted else []))
        emitted=[]
        try:
            for program in source:emitted.append(program)
            return emitted,report['continuation']
        finally:source.close()

    @staticmethod
    def runs(programs):
        return sorted(local_counts(p)['R'] for p in programs)

    @staticmethod
    def reserves(state, node=None):
        # Reserves registered for the root decision (its node is None).
        return sorted((h['programs'][0]['minimumRunSearch']['target'],h['programs'][0]['minimumRunSearch']['start'])
                      for h in state['alternatives'].values() if 'watch' in h and h['next']<len(h['programs'])
                      and h['programs'][0]['minimumRunSearch']['node']==node)

    @staticmethod
    def late(minimum):
        # A screen that rules out goal arrivals with fewer than `minimum` run ticks.
        return lambda p:dict(reason='late') if p['terminalSupport']=='goal' and local_counts(p)['R']<minimum else None

    def test_jump_now_reaching_the_next_anchor_queues_no_run_ticks(self):
        emitted,state=self.frontier()
        self.assertEqual((self.runs(emitted),self.runs(state['pending'])),([0],[0]))
        self.assertEqual(self.reserves(state),[('["anchor", "goal"]',1)])
        emitted,state=self.frontier(policy=False)
        self.assertIn(1,self.runs(state['pending']))

    def test_first_screened_run_count_is_queued_and_the_next_restored_when_it_dies(self):
        emitted,state=self.frontier(screen=self.late(3))
        self.assertEqual(self.runs(emitted),[3])
        self.assertEqual(self.runs(state['pending']),[0])
        self.assertEqual(self.reserves(state),[('["anchor", "goal"]',4)])
        self.assertEqual([(r['target'],r['level']) for r in state['minimumRunLevels']],[('["anchor", "goal"]',3)])
        saved=json.loads(json.dumps(state))
        # While the 3-tick arrival is alive the reserve waits and the queued hop is expanded instead.
        again,alive=self.frontier(screen=self.late(3),continuation=saved,live=emitted)
        self.assertEqual([h['next'] for h in alive['alternatives'].values() if 'watch' in h and
                          h['programs'][0]['minimumRunSearch']['node'] is None],[0])
        self.assertTrue(again)
        self.assertTrue(all(local_counts(p)['J']==2 for p in again))
        again,state=self.frontier(screen=self.late(3),continuation=saved)
        self.assertEqual(self.runs(again),[4])
        self.assertEqual(self.reserves(state),[('["anchor", "goal"]',5)])

    def test_no_run_option_or_reserve_past_the_cap(self):
        from parkour_solver.movement.normal import MAX_ADVANTAGEOUS_RUN_TICKS
        emitted,state=self.frontier(screen=self.late(MAX_ADVANTAGEOUS_RUN_TICKS+1))
        self.assertEqual((emitted,self.runs(state['pending']),self.reserves(state)),([],[0],[]))
        emitted,state=self.frontier(screen=self.late(MAX_ADVANTAGEOUS_RUN_TICKS))
        self.assertEqual((self.runs(emitted),self.reserves(state)),([MAX_ADVANTAGEOUS_RUN_TICKS],[]))

    def test_turnaround_decision_generates_run_ticks_as_before(self):
        from parkour_solver.movement.program import program_search_key
        keys=lambda programs:sorted(program_search_key(p) for p in programs)
        turned,state=self.frontier(ordinary=False)
        plain,before=self.frontier(policy=False)
        self.assertEqual((keys(turned),keys(state['pending'])),(keys(plain),keys(before['pending'])))
        self.assertEqual(state['alternatives'],{})

    def test_runoff_under_a_ceiling_is_queued_and_a_plain_runoff_is_not(self):
        from parkour_solver.scene.normalize import prepare_scene
        # Run-offs from start (top 2): roof's underside is 2 above low (top 1.5), so a jump from low hits
        # it; from plain (top -4) no jump reaches any underside or the goal. Heights alone decide here.
        s=prepare_scene(dict(version=1, sceneModel='collision_boxes', geometryBounds=[-10,-8,-10,20,12,10],
            initial=dict(position=[1,2,1], velocity=[0,-.0784000015258789,0], yaw=-90),
            startBlock='start', goalBlock='goal', maxWallHits=0, maxContactCombinations=0,
            blocks=[dict(id='start', boxes=[[0,1,0,2,2,2]]), dict(id='low', boxes=[[2,0,0,3,1.5,4]]),
                    dict(id='roof', boxes=[[2,3.5,2,3,4.5,4]]), dict(id='plain', boxes=[[-1,-5,0,0,-4,2]]),
                    dict(id='goal', boxes=[[6,2,0,7,3,2]])],
            movementSearch=dict(types=['normal','ceiling'], maxInputs=40)))
        emitted,state=self.frontier(scene=s)
        runoffs={(p['terminalSupport'],local_counts(p)['J']) for p in state['pending'] if local_counts(p)['R']}
        self.assertEqual(runoffs,{('low',0)})
        self.assertIn(['["ceiling", "roof"]',1],[[r['target'],r['level']] for r in state['minimumRunLevels']])

    def test_deadline_inside_the_minimum_run_step_redoes_it_on_resume(self):
        from parkour_solver.movement.program import program_search_key
        keys=lambda programs:sorted(program_search_key(p) for p in programs)
        late=self.late(3)
        whole,expected=self.frontier(screen=late)
        def interrupted(p):
            if local_counts(p)['R']>=2:raise TimeoutError('deadline')
            return late(p)
        report={}
        emitted,state=self.frontier(screen=interrupted,report=report)
        self.assertEqual((report['termination'],emitted,self.runs(state['pending']),self.reserves(state)),
                         ('deadline',[],[0],[]))
        self.assertIsNotNone(state['expansion'])
        # The resumed call also expands one more queued program; arrivals stay alive so no reserve fires.
        rest,state=self.frontier(screen=late,continuation=json.loads(json.dumps(state)),keep_emitted=True)
        self.assertEqual(keys(p for p in rest if local_counts(p)['J']==1),keys(whole))
        self.assertEqual(self.reserves(state),self.reserves(expected))
        root=lambda levels:[r for r in levels if r['node'] is None]
        self.assertEqual(root(state['minimumRunLevels']),root(expected['minimumRunLevels']))


class AnchorNativeTests(unittest.TestCase):
    def test_cold_finite_landing_with_observable_internal_queries(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        s=flat_scene()
        fixture=dict(request=s,anchorSearch=dict(anchors=[]),settings=dict(seconds=5,score_seconds=2,
            max_solver_calls=20,score_call_limit=4,max_candidates=4))
        with PythonCompiler() as c:
            result,validation=run(fixture,c,python_executable=os.environ.get('PKC_IPOPT_PYTHON'))
        self.assertIsNotNone(result['best'])
        self.assertTrue(validation[0]['passed'])
        self.assertTrue(result['best']['candidate']['onGround'][-1])
        self.assertEqual(result['searchState']['dfs']['version'],8)
        self.assertTrue(result['anchorSearch']['hardLatches'])
        # Interim queries are relaxed; IPOPT runs only for alarm confirmations and the finalization.
        self.assertEqual(result['work']['workerLaunches'],result['work']['confirmationIpoptCalls'])
        self.assertGreater(result['work']['conicCandidates'],0)
        self.assertGreaterEqual(result['work']['solverCalls'],result['work']['conicCandidates'])
        self.assertNotIn('model',result['best']['candidate'])
        self.assertTrue(result['anchorSearch']['nodes'])
        self.assertEqual(result['best']['route']['objective'],dict(axis='X',sense='MAX'))


@unittest.skipUnless(os.environ.get('PKC_IPOPT_PYTHON'), 'Set PKC_IPOPT_PYTHON for relaxed automatic search')
class RelaxedAutomaticSearchTests(unittest.TestCase):
    def search(self, **options):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.search.domain import InitialJumpDomain
        from parkour_solver.search.engine import find_checkpoint_route
        from tests.support.checkpoint_guidance import flat_scene
        scene = flat_scene()
        scene['movementSearch'].update(maxInputs=28, maxPrefixes=64)
        with PythonCompiler() as compiler:
            settings = dict(anchor_search={}, seconds=12, score_seconds=4, max_solver_calls=40,
                            program_filter=InitialJumpDomain([0], max_inputs=28),
                            python_executable=os.environ['PKC_IPOPT_PYTHON'])
            settings.update(options)
            result = find_checkpoint_route(scene, compiler, **settings)
            return scene, result, compiler

    def test_alarms_are_the_only_interim_ipopt_and_the_published_route_is_physical(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.validation.witness import replay_witness
        _, result, _ = self.search()
        work = result['work']
        self.assertEqual(work['workerLaunches'], work['confirmationIpoptCalls'])
        self.assertGreater(work['conicCandidates'], 0)
        self.assertNotIn('model', result['best']['candidate'])
        with PythonCompiler() as compiler:
            self.assertTrue(replay_witness(prepare_scene(result['effectiveRequest']), result['best'], compiler)['passed'])

    def test_search_runs_on_the_relaxation_and_publishes_one_physical_finalization(self):
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.numerics import disk
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.validation.witness import replay_witness
        from parkour_solver.search import engine
        repairs = []
        def recorded(route, *args, **kwargs):
            repairs.append((route, kwargs['initial_variants'], kwargs['repair_scope']['solvePolicy']))
            return original(route, *args, **kwargs)
        original = engine.solve_contact_branches
        # Without the alarm the finish stays relaxed until the finalization.
        with patch.object(engine, 'solve_contact_branches', side_effect=recorded), \
             patch.object(disk, 'ALARM_FRACTION', 1.):
            _, result, _ = self.search()
        relaxed = result['searchState']['dfs']['best']
        # Finalization repairs the relaxed witness's own branch, warm-started from its turned facings.
        (route, variants, policy), = [r for r in repairs if r[2]['numerical'] == 'ipopt']
        self.assertIsNone(variants)
        self.assertEqual(policy['rankEqualCost'], False)
        self.assertEqual(route, dict(relaxed['route'], warmYaw=disk.ipopt_seed(relaxed['candidate']['yaw'])))
        self.assertEqual(relaxed['candidate']['model'], 'relaxed_disk')
        self.assertNotIn('model', result['best']['candidate'])
        self.assertEqual(result['work']['workerLaunches'], 0)
        self.assertGreater(result['work']['conicCandidates'], 0)
        self.assertEqual(result['work']['finalizationIpoptCalls'], 1)
        self.assertEqual(result['finalization']['stage'], 'complete')
        with PythonCompiler() as compiler:
            scene = prepare_scene(result['effectiveRequest'])
            physical = replay_witness(scene, result['best'], compiler)
            self.assertTrue(physical['passed'])
            self.assertIsNone(physical['model'])
            checked = replay_witness(scene, relaxed, compiler)
            self.assertTrue(checked['passed'])
            self.assertEqual(checked['model'], 'relaxed_disk')
            perturbed = json.loads(json.dumps(relaxed))
            perturbed['candidate']['controls'][3][0] *= .9
            self.assertFalse(replay_witness(scene, perturbed, compiler)['passed'])

    def solve_relaxed(self, request):
        """Automatic search on the relaxed backend; returns its published, replayed physical route."""
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.scene.normalize import prepare_scene
        from parkour_solver.search.engine import find_checkpoint_route
        from parkour_solver.validation.witness import replay_witness
        with PythonCompiler() as compiler:
            result = find_checkpoint_route(prepare_scene(request), compiler, anchor_search={}, seconds=20, score_seconds=4,
                                           max_solver_calls=40, python_executable=os.environ['PKC_IPOPT_PYTHON'])
            best = result['best']
            self.assertIsNotNone(best, result['termination'])
            work = result['work']
            self.assertGreater(work['conicCandidates'], 0)
            self.assertEqual(work['workerLaunches'], work['confirmationIpoptCalls'])
            self.assertEqual(result['finalization']['stage'], 'complete')
            self.assertNotIn('model', best['candidate'])
            self.assertTrue(replay_witness(prepare_scene(result['effectiveRequest']), best, compiler)['passed'])
        return best

    def boxes(self, types, **blocks):
        return dict(version=1, sceneModel='collision_boxes', geometryBounds=[-10,-6,-10,20,12,10],
            initial=dict(position=[.5,1,.5], velocity=[.25,-.0784000015258789,0], yaw=-90),
            startBlock='start', goalBlock='goal', maxWallHits=0, maxContactCombinations=0,
            blocks=[dict(id=k, boxes=v) for k, v in blocks.items()],
            movementSearch=dict(types=types, maxInputs=30, maxPrefixes=96, maxQueuedPrefixes=128, maxChoicesPerType=16))

    def test_relaxed_route_slab_boosts_onto_a_step_and_jumps_on(self):
        best = self.solve_relaxed(self.boxes(['normal','stepping'], start=[[-2,0,-2,1,1,2]], slab=[[1,1,-2,4,1.5,2]],
                                             goal=[[6,0,-1,7,1.5,1]]))
        self.assertEqual(best['vertical']['contacts'][0]['case'], 'slab_boost')
        self.assertEqual(sum(best['plan']['jump']), 2)
        self.assertGreaterEqual(best['candidate']['positions'][-1][0], 5.7)

    def test_relaxed_routes_hit_a_ceiling_and_grind_under_one(self):
        from tests.support.stepping_cases import fixture
        best = self.solve_relaxed(self.boxes(['normal','ceiling'], start=[[-2,0,-2,3,1,2]], roof=[[1,3.5,-2,4,4.5,2]],
                                             goal=[[5,0,-1,6,1,1]]))
        self.assertIn(1, [c['direction'] for c in best['vertical']['contacts']])
        grind = dict(fixture('grind')['request'], movementSearch=dict(types=['normal','ceiling'], maxInputs=20))
        self.assertEqual(self.solve_relaxed(grind)['vertical']['contacts'][0]['case'], 'grind')

    def test_relaxed_route_turns_toward_a_goal_off_the_initial_heading(self):
        best = self.solve_relaxed(self.boxes(['normal'], start=[[-2,0,-2,2,1,2]], goal=[[-1,0,4.5,1,1,6]]))
        self.assertGreater(max(best['candidate']['yaw'])-min(best['candidate']['yaw']), 30)
        self.assertGreater(best['candidate']['positions'][-1][1], 4.2)

    def test_relaxed_route_runs_off_the_start_and_jumps_from_the_lower_block(self):
        best = self.solve_relaxed(self.boxes(['normal'], start=[[-2,0,-2,1,1,2]], middle=[[1,-1,-2,4,0,2]],
                                             goal=[[6,-1,-1,7,0,1]]))
        plan = best['plan']
        self.assertFalse(any(plan['jump'][:plan['grounded'].index(False)]))
        self.assertEqual(sum(plan['jump']), 1)

    def test_cancelled_sessions_publish_no_relaxed_route_and_resume_without_repeating_requests(self):
        import threading
        from parkour_solver.bridge.python_compiler import PythonCompiler
        from parkour_solver.numerics import disk
        from parkour_solver.numerics.model import fingerprint, prepare
        fixture = dict(request=self.boxes(['normal'], start=[[-2,0,-2,1,1,2]], middle=[[1,-1,-2,4,0,2]], goal=[[6,-1,-1,7,0,1]]),
                       anchorSearch=dict(anchors=[]), settings=dict(seconds=20, score_seconds=4, max_solver_calls=40))
        requests = []
        original = disk.RelaxedDispatch.solve
        def recorded(dispatch, problem, **options):
            requests.append(fingerprint(prepare(dict(problem, seconds=options['seconds']))))
            return original(dispatch, problem, **options)
        def session(compiler, saved, stop):
            cancel = threading.Event()
            result, validation = run(fixture, compiler, python_executable=os.environ['PKC_IPOPT_PYTHON'],
                resume_state=json.loads(json.dumps(saved)) if saved else None, cancel_event=cancel,
                on_progress=lambda state: stop(state['dfs'] or {}) and cancel.set())
            return result, validation
        relaxed = lambda dfs: (dfs.get('best') or {}).get('candidate', {}).get('model') == 'relaxed_disk'
        finalizing = lambda dfs: (dfs.get('finalization') or {}).get('stage') == 'running'
        with PythonCompiler() as compiler, patch.object(disk.RelaxedDispatch, 'solve', recorded):
            traversal, _ = session(compiler, None, relaxed)
            finalization, _ = session(compiler, traversal['searchState'], finalizing)
            rerun, validation = session(compiler, finalization['searchState'], lambda dfs: False)
            published = len(requests)
            complete, _ = session(compiler, rerun['searchState'], lambda dfs: False)
        for cancelled in (traversal, finalization):
            self.assertEqual((cancelled['status'], cancelled['termination'], cancelled['best']), ('cancelled', 'cancelled', None))
            self.assertEqual(cancelled['provisionalBest']['model'], 'relaxed_disk')
        self.assertNotIn('finalization', traversal)
        self.assertEqual((finalization['finalization']['stage'], finalization['work']['finalizationIpoptCalls']), ('running', 0))
        self.assertEqual((rerun['finalization']['stage'], rerun['work']['finalizationIpoptCalls']), ('complete', 1))
        self.assertTrue(validation[0]['passed'])
        self.assertNotIn('model', rerun['best']['candidate'])
        self.assertEqual(len(set(requests)), len(requests))
        self.assertEqual((len(requests), complete['work']['finalizationIpoptCalls']), (published, 0))
        self.assertEqual(complete['best']['candidate'], rerun['best']['candidate'])


if __name__=='__main__':unittest.main()
