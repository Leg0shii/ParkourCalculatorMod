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

    def test_multiple_same_block_jumps_generated_and_no_solve_for_run_hold(self):
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
        # Holds can construct longer preparations but never dispatch by themselves.
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
        # A relaxation-infeasible request excludes the full model; an undecided one does not.
        self.assertTrue(invoke('PrimalInfeasible','disk_finished')['repairState']['completed'][0]['oracleRejected'])
        self.assertFalse(invoke('MaxIterations','disk_undecided')['repairState']['completed'][0]['oracleRejected'])

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
        # Finalization repairs the relaxed witness's own branch, warm-started from its facings.
        (route, variants, policy), = [r for r in repairs if r[2]['numerical'] == 'ipopt']
        self.assertIsNone(variants)
        self.assertEqual(policy['rankEqualCost'], False)
        self.assertEqual(route, dict(relaxed['route'], warmYaw=relaxed['candidate']['yaw']))
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


if __name__=='__main__':unittest.main()
