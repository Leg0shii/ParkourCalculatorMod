from tests.support.repair_resume import RepairHarness
from tests.support.repair_resume import route, best, outcome, VERTICAL
import copy
import json
import time
import unittest
from unittest.mock import patch

from parkour_solver.search.repair import solve_contact_branches
from parkour_solver.spatial.checkpoints import retain_unresolved
from parkour_solver.search.engine import _key
from parkour_solver.movement.program import compile_program, program_physics_key
from parkour_solver.search.movement import iter_movement_programs
from parkour_solver.search.repair_state import compatible, context
from parkour_solver.search.work import SearchWork
from tests.support.checkpoint_guidance import flat_scene, programs


class RepairResumeTests(RepairHarness, unittest.TestCase):
    def test_exhausted_call_budget_keeps_unstarted_branch_pending_without_unknown_retry(self):
        rows=[dict(leg=0,offset=1,mode='X',cmp='GE',rhs=v) for v in (1,2)]
        def filter_candidate(*args):return dict(accepted=False,alternatives=[[r] for r in rows])
        seeded=self.invoke(route(),lambda *a,**k:outcome(),filter_candidate)
        work=SearchWork(1)
        work.before_solve({})
        first=self.invoke(route(),lambda *a,**k:self.fail('Dispatched beyond the run budget'),filter_candidate,
            work=work,resume_state=json.loads(json.dumps(seeded['repairState'])),retry_unresolved=False)
        self.assertEqual(first['termination'],'numerical_call_limit')
        self.assertEqual(len(first['repairState']['pending']),2)
        self.assertEqual(first['repairState']['unresolved'],[])
        self.assertEqual(first['repairState']['completed'],seeded['repairState']['completed'])
        requests=[]
        def solve(request,*args,**kwargs):
            requests.append(request['constraints'])
            return outcome()
        resumed=self.invoke(route(),solve,lambda *a:dict(accepted=True),
            resume_state=json.loads(json.dumps(first['repairState'])),retry_unresolved=False)
        self.assertEqual(requests,[[rows[0]]])
        self.assertEqual(resumed['status'],'feasible')

    def test_generation_expiry_preserves_new_branch_before_dispatch(self):
        row=dict(leg=0,offset=1,mode='X',cmp='GE',rhs=1)
        clock=[100.]
        def variants():
            clock[0]+=2.
            yield dict(rows=[row])
        with patch('time.monotonic',side_effect=lambda:clock[0]):
            first=self.invoke(route(),lambda *a,**k:self.fail('Dispatched after generation expired'),
                lambda *a:dict(accepted=True),initial_variants=variants(),work=SearchWork(2,deadline=101.))
        self.assertEqual(first['termination'],'deadline')
        self.assertTrue(any(b['rows']==[row] for b in first['repairState']['pending']))
        self.assertEqual(first['repairState']['unresolved'],[])
        requests=[]
        def solve(request,*args,**kwargs):
            requests.append(request['constraints'])
            return outcome()
        resumed=self.invoke(route(),solve,lambda *a:dict(accepted=True),
            resume_state=json.loads(json.dumps(first['repairState'])),retry_unresolved=False)
        self.assertEqual(requests,[[row]])
        self.assertEqual(resumed['status'],'feasible')

    def test_extended_program_keeps_guess_without_copying_exact_old_problem_work(self):
        scene=flat_scene()
        parent=next(p for p in programs(scene,2) if p['profile']['jump'][0])
        parent['guideSeedYaw']=[12.,13.]
        parent['repairState']=dict(context=dict(scope=dict(solveIdentity='old-exact-problem')),pending=[dict(rows=[])])
        generator=iter_movement_programs(scene,checkpoint_tick=4,threshold=0.,initial_programs=[parent])
        child=next(generator);generator.close()
        self.assertNotIn('repairState',child)
        self.assertEqual(child['guideSeedYaw'],[12.,13.])
        self.assertEqual(len(parent['repairState']['pending']),1)

    def test_valid_saved_incumbent_does_not_need_another_numerical_request(self):
        first=self.invoke(route(),lambda *a,**k:outcome(),lambda *a:dict(accepted=True))
        second=self.invoke(route(),lambda *a,**k:self.fail('Completed request restarted'),lambda *a:dict(accepted=True),
                           resume_state=json.loads(json.dumps(first['repairState'])))
        self.assertEqual(second['status'],'feasible')
        self.assertEqual(second['attempts'],[])

    def test_worker_interruption_restores_the_inflight_branch(self):
        checkpoints=[]
        def interrupted(*args,**kwargs):raise RuntimeError('synthetic interruption')
        with self.assertRaises(RuntimeError):
            self.invoke(route(),interrupted,lambda *a:dict(accepted=True),
                on_progress=lambda state:checkpoints.append(json.loads(json.dumps(state))))
        self.assertIsNotNone(checkpoints[-1]['inFlight'])
        called=[]
        def resumed(*args,**kwargs):called.append(True);return outcome()
        second=self.invoke(route(),resumed,lambda *a:dict(accepted=True),resume_state=checkpoints[-1])
        self.assertEqual(called,[True])
        self.assertEqual(second['status'],'feasible')
        self.assertIsNone(second['repairState']['inFlight'])

    def test_unknown_retry_is_explicit_and_not_a_completed_result(self):
        first=self.invoke(route(),lambda *a,**k:dict(status='unknown',termination='deadline',best=None,attempts=[]),
            lambda *a:dict(accepted=True))
        second=self.invoke(route(),lambda *a,**k:self.fail('Unknown retried without request'),lambda *a:dict(accepted=True),
            resume_state=first['repairState'],retry_unresolved=False)
        self.assertEqual(len(second['repairState']['unresolved']),1)
        self.assertIsNone(second['best'])


    def test_corridor_diagnostics_survive_branch_result_without_new_replay_calls(self):
        from parkour_solver.validation.scoped_scene import CorridorAdapter
        from parkour_solver.spatial.compat.corridors import Corridor
        request = route()
        scene = flat_scene()
        corridor = Corridor(3, [], [], [], (), [(-1., 0., -1., 1., 0., 1.)], 0.)
        adapter = CorridorAdapter(scene, corridor, [])
        response = outcome()
        wall = dict(mode='X', t1=0, cmp='GE', rhs=-.5)
        response['best']['plan'].update(events=[dict(jump=0)], walls=[wall])
        response['best']['candidate']['positions'] = [[0., 0.], [2., 0.], [3., 0.]]
        response['attempts'] = [dict(problemHash='fixed-problem', status='feasible', allocation=[0], stage='jump', leg=0)]
        def filter_candidate(plan, candidate, vertical):
            return adapter.candidate(plan, candidate, vertical, partial=True)
        with patch('parkour_solver.search.repair.check_geometry') as physical:
            result = self.invoke(request, lambda *a, **k: copy.deepcopy(response), filter_candidate,
                geometry=scene['collisionScene'])
        physical.assert_not_called()
        logged = json.loads(json.dumps(result, allow_nan=False))['contactAttempts'][0]['candidateFilter']['corridorAudit']
        self.assertEqual(logged['numericalRequests'][0]['problemHash'], 'fixed-problem')
        self.assertEqual(logged['physicalReplay']['status'], 'not_run')
        self.assertEqual(logged['failure']['input'], 0)
        self.assertEqual(logged['failure']['phase'], 'X')
        self.assertEqual(logged['plan']['walls'], [wall])
        self.assertEqual(logged['selectedPassages'], [])
        self.assertEqual(logged['selectedHits'], [])
        self.assertIsNone(result['best'])
        self.assertEqual(len(result['repairState']['pending']), 1)

    def test_pending_guards_and_facing_guesses_resume_without_repeating_root(self):
        rows=[dict(leg=0,offset=1,mode='X',cmp='GE',rhs=v) for v in (1,2)]
        def filter_candidate(*args):
            return dict(accepted=False,alternatives=[[r] for r in rows])
        first=self.invoke(route(),lambda *a,**k:outcome(7),filter_candidate)
        state=json.loads(json.dumps(first['repairState']))
        self.assertEqual(len(state['pending']),2)
        self.assertEqual(state['pending'][0]['warmYaw'],[7,7])
        requests=[]
        def resumed(request,*args,**kwargs):
            requests.append(copy.deepcopy(request))
            return dict(status='unknown',termination='done',best=None,attempts=[])
        second=self.invoke(route(),resumed,filter_candidate,resume_state=state)
        self.assertTrue(second['repairResume']['exact'])
        self.assertEqual(requests[0]['constraints'],[rows[0]])
        self.assertEqual(requests[0]['warmYaw'],[7,7])
        self.assertEqual(second['repairState']['pending'][0]['rows'],[rows[1]])
        self.assertEqual(second['repairState']['unresolved'][0]['rows'],[rows[0]])
        self.assertEqual(requests[0]['initial'],route()['initial'])

    def test_unknown_numeric_branch_is_stored_and_revalidated_on_resume(self):
        request=dict(route(),warmYaw=[8,8])
        first=self.invoke(request,lambda *a,**k:dict(status='unknown',termination='done',best=None,attempts=[]),
                          lambda *a:dict(accepted=True))
        self.assertEqual(first['repairState']['unresolved'][0]['warmYaw'],[8,8])
        second=self.invoke(request,lambda *a,**k:outcome(9),lambda *a:dict(accepted=True),resume_state=first['repairState'])
        self.assertEqual(second['status'],'feasible')
        self.assertTrue(second['repairResume']['compatible'])

    def test_changed_start_or_corridor_discards_incompatible_state(self):
        first=self.invoke(route(),lambda *a,**k:dict(status='unknown',termination='done',best=None,attempts=[]),
                          lambda *a:dict(accepted=True),repair_scope={'corridor':1})
        for change in ('start','scope'):
            request=route()
            if change=='start':request['initial']['velocity'][0]=.1
            scope={'corridor':2 if change=='scope' else 1}
            seen=[]
            def find(request,*args,**kwargs):
                seen.append(request)
                return outcome()
            result=self.invoke(request,find,lambda *a:dict(accepted=True),
                resume_state=first['repairState'],repair_scope=scope)
            self.assertFalse(result['repairResume']['compatible'])
            self.assertEqual(seen[0]['constraints'],[])

    def test_changed_constraints_cannot_reuse_prior_acceptance(self):
        first=self.invoke(route(),lambda *a,**k:outcome(),lambda *a:dict(accepted=True))
        self.assertEqual(first['status'],'feasible')
        second=self.invoke(route(),lambda *a,**k:outcome(),lambda *a:dict(accepted=False),resume_state=first['repairState'])
        self.assertIsNone(second['best'])
        self.assertEqual(second['status'],'unknown')

    def test_cancellation_keeps_queue_serializable_for_resume(self):
        import threading
        cancelled=threading.Event();cancelled.set()
        result=self.invoke(route(),lambda *a:self.fail('Cancelled search solved a branch'),
                           lambda *a:dict(accepted=True),cancel_event=cancelled)
        state=json.loads(json.dumps(result['repairState']))
        self.assertEqual(len(state['pending']),1)

    def test_compatible_extension_preserves_symbolic_state_and_has_distinct_search_identity(self):
        scene=flat_scene()
        first=next(p for p in programs(scene,2) if p['profile']['jump'][0])
        compiled=compile_program(scene,first,require_complete=False)
        ctx=context(compiled['route'],compiled['profiles'],scene['collisionScene'],{'corridor':1})
        branch=dict(score=1,rows=[dict(leg=0,offset=1,mode='Z',cmp='LE',rhs=.4)],passages=[],hits=[],warmYaw=[1,2])
        state=dict(version=1,context=ctx,pending=[branch],unresolved=[],seen=[])
        retained=retain_unresolved(first,state)
        self.assertEqual(program_physics_key(first),program_physics_key(retained))
        self.assertEqual(_key(first),_key(retained))
        generator=iter_movement_programs(scene,checkpoint_tick=4,threshold=0.,initial_programs=[retained],deadline=time.monotonic()+2)
        extended=next(generator);generator.close()
        self.assertEqual(extended['repairState'],state)
        self.assertEqual(extended['guideSeedYaw'],[1,2])
        later=compile_program(scene,extended,require_complete=False)
        new_ctx=context(later['route'],later['profiles'],scene['collisionScene'],{'corridor':1})
        self.assertTrue(compatible(ctx,new_ctx))
        new_ctx['profiles'][0]['jump'][0]=False
        self.assertFalse(compatible(ctx,new_ctx))
        self.assertNotIn('positions',retained)
        self.assertNotIn('velocity',retained)


if __name__=='__main__':
    unittest.main()
