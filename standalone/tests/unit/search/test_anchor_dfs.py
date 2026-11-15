import copy
import json
import threading
import unittest
from unittest.mock import patch

from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.search.feasibility import solve_due
from tests.unit.search.test_anchor_policy import scene, history


def extend(s,parent,target,n,extra=False):
    p=history(s,[]);start=parent['profile']['airtime']
    p['profile'].update(airtime=n,position=[1.]*(n+1),velocity=[0.]*(n+1),grounded=[True]*(n+1),
        jump=[False]*n,sneak=[False]*n,ladderTicks=[False]*(n+1))
    p['profile']['jump'][:start]=parent['profile']['jump']
    p['profile']['jump'][start]=True
    if extra:p['profile']['jump'][start+1]=True
    p['profile']['contactEvents']=copy.deepcopy(parent['profile']['contactEvents'])
    b=next(b for b in s['blocks'] if b['id']==target)
    p['profile']['contactEvents'].append(dict(offset=n-1,axis='Y',direction=-1,
        box=s['collisionScene']['solids'][b['solidIndex']]))
    p['terminalSupport']=target
    p['decisionDepth']=parent.get('decisionDepth',0)+1
    return p


class AnchorDFSTests(unittest.TestCase):
    def run_tree(self, *, logging=True, resume=None, calls=20, interval=1, setup=False, seconds=3,
                 on_progress=None,cancel_event=None):
        s=scene();s['maxTotalRunTicks']=60;s['maxRunTicksPerJump']=60;dispatched=[]
        def generate(target_scene,*,report,initial_programs,continuation=None,node_evaluator,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(expanded=1,cursor=0,exhausted=False)
            report['continuation']=saved
            parent=initial_programs[0];target=target_scene['goalBlock']
            if target=='mid':
                choices=[extend(s,parent,'mid',10),extend(s,parent,'mid',11)]
                if setup:
                    prepared=extend(s,parent,'mid',11)
                    prepared['profile']['jump'][1]=True
                    start=next(b for b in s['blocks'] if b['id']=='start')
                    prepared['profile']['contactEvents'].insert(0,dict(offset=0,axis='Y',direction=-1,
                        box=s['collisionScene']['solids'][start['solidIndex']]))
                    choices.append(prepared)
            else:
                # Certifying subtree learns the global hard latch. A later
                # incoming history must not reopen a two-jump local structure.
                choices=[extend(s,parent,'goal',20,extra=parent['profile']['airtime']==11)]
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1
                yield p
            saved['exhausted']=True
        def evaluate(target_scene,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({})
            dispatched.append((p['terminalSupport'],p['profile']['airtime'],kwargs.get('solve_policy')))
            n=p['profile']['airtime']
            candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.)
            best=dict(candidate=candidate,plan=dict(ticks=n,walls=[]),allocation=[0],cost=0)
            return dict(status='feasible',termination='done',best=best,work=dict(solverCalls=1),
                        fullSceneValidated=True,nodeFeasibility=dict(status='feasible',model=str(n)))
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate), \
             patch('parkour_solver.validation.witness.replay_witness',return_value=dict(passed=True)):
            result=find_checkpoint_route(s,object(),anchor_search=dict(anchors=['mid'],observability=logging,
                solveEveryDecisionLevels=interval),minimum_run_pruning=False,seconds=seconds,score_seconds=2,
                max_solver_calls=calls,resume_state=resume,on_progress=on_progress,cancel_event=cancel_event)
        return result,dispatched

    def test_global_latch_survives_abandoned_certifier_and_changed_incoming_prefix(self):
        result,calls=self.run_tree()
        self.assertEqual([(b,t) for b,t,_ in calls],[('mid',10),('goal',20),('mid',11)])
        dfs=result['searchState']['dfs']
        self.assertIn('mid→goal',dfs['hardLatches'])
        self.assertIn('start→mid',dfs['hardLatches'])
        self.assertGreaterEqual(sum(t['kind']=='backtrack' for t in dfs['transitions']),2)
        self.assertTrue(any(t['kind']=='candidate_exclusion' for t in dfs['transitions']))
        self.assertEqual(dfs['best']['plan']['ticks'],20)

    def test_logging_does_not_change_solver_allocation_and_anchors_force_queries(self):
        on,on_calls=self.run_tree(logging=True,interval=4)
        off,off_calls=self.run_tree(logging=False,interval=4)
        self.assertEqual(on_calls,off_calls)
        self.assertEqual(on['work']['solverCalls'],off['work']['solverCalls'])
        self.assertEqual(on['searchState']['dfs']['hardLatches'],off['searchState']['dfs']['hardLatches'])
        self.assertTrue(on['searchState']['dfs']['observations']['decisions'])
        self.assertEqual(off['searchState']['dfs']['observations']['decisions'],{})
        self.assertTrue(all(p['stopOnFeasible'] for b,t,p in on_calls if b=='mid'))
        self.assertTrue(all(not p['stopOnFeasible'] for b,t,p in on_calls if b=='goal'))

    def test_latch_resume_keeps_exact_parent_and_does_not_repeat_scored_nodes(self):
        first,calls1=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        second,calls2=self.run_tree(resume=saved)
        self.assertEqual([(b,t) for b,t,_ in calls1+calls2],[('mid',10),('goal',20),('mid',11)])
        self.assertEqual(first['searchState']['dfs']['hardLatches'],second['searchState']['dfs']['hardLatches'])

    def test_resume_keeps_nested_incumbent_even_without_time_for_a_service_slice(self):
        first,_=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        saved['best']=None  # A callback may publish DFS before the allocator catches up.
        result,calls=self.run_tree(resume=saved,seconds=1e-6)
        self.assertEqual(result['best']['plan']['ticks'],20)
        self.assertEqual(calls,[])
        self.assertIsNone(saved['best'])

    def queued_tree(self, refinement=False):
        first,_=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        root=saved['dfs']['stack'][0]
        template=root['records'][0]
        for n in (11,12,13):
            p=extend(scene(),history(scene(),[]),'mid',n)
            record=copy.deepcopy(template)
            record.update(id='queued-'+str(n),program=p,child=copy.deepcopy(p),arrival=n,ticks=n,tried=False,
                witness=dict(plan=dict(ticks=n,walls=[]),allocation=[0],cost=0,
                    candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.)))
            if refinement:
                record.pop('witness')
                record['program']['repairState']=dict(pending=[dict(branch='unfinished')])
            root['records'].append(record)
        root.update(phase='select',ready=[],sourceExhausted=True)
        return saved

    def check_lazy_queue_bound(self, *, refinement=False, remaining=15):
        saved=self.queued_tree(refinement)
        checked=[];cancel=threading.Event()
        def bound(guides,span,program,motion,**kwargs):
            checked.append(program['profile']['airtime'])
            return dict(remainingTicks=remaining)
        def progress(state):
            last=state['dfs']['transitions'][-1]
            if last['kind']=='candidate_exclusion' or last['kind']=='open' and last['depth']==1:
                cancel.set()
        with patch('parkour_solver.search.bounds.remaining.remaining_bound',side_effect=bound):
            result,calls=self.run_tree(resume=saved,on_progress=progress,cancel_event=cancel)
        self.assertEqual(checked,[11])
        self.assertEqual(calls,[])
        root=result['searchState']['dfs']['stack'][0]
        unselected=[r for r in root['records'] if r['id'] in ('queued-12','queued-13')]
        self.assertTrue(all(not r['tried'] and not r.get('timeBoundExclusion') and not r.get('refinementTimeExclusion') for r in unselected))
        return result

    def test_time_bound_checks_only_selected_arrival_not_other_queued_records(self):
        result=self.check_lazy_queue_bound()
        self.assertEqual(result['searchState']['dfs']['transitions'][-1]['kind'],'candidate_exclusion')

    def test_time_bound_checks_only_selected_refinement_not_other_pending_records(self):
        self.check_lazy_queue_bound(refinement=True)

    def test_equal_optimistic_finish_is_kept_without_checking_other_queued_records(self):
        result=self.check_lazy_queue_bound(remaining=9)
        self.assertEqual(result['searchState']['dfs']['stack'][-1]['parents'][0]['profile']['airtime'],11)

    def test_global_latch_accepts_setup_after_certifier_and_backtracking(self):
        result,calls=self.run_tree(setup=True)
        frames=result['searchState']['dfs']['frames']
        prepared=[r for f in frames if f['index']==0 for r in f['records']
                  if r.get('transferStructure',{}).get('preparationJumps')]
        self.assertEqual(len(prepared),1)
        self.assertEqual(prepared[0]['status'],'feasible')
        self.assertEqual(prepared[0]['transferStructure']['transferJumps'],[1])
        from parkour_solver.search.anchors.policy import HARD_JUMP_POLICY_VERSION
        self.assertEqual(result['searchState']['dfs']['hardLatches']['start→mid']['version'],HARD_JUMP_POLICY_VERSION)
        self.assertEqual(prepared[0]['transferStructure']['assumption'],'hard_transfer_without_stepping')

    def test_incompatible_old_latch_policy_is_rejected_on_resume(self):
        first,_=self.run_tree(calls=2)
        with patch('parkour_solver.search.anchors.policy.HARD_JUMP_POLICY_VERSION',1):
            with self.assertRaisesRegex(ValueError,'Incompatible automatic event'):
                self.run_tree(resume=first['searchState'])

    def test_completed_decision_cadence_and_forced_anchor_or_terminal(self):
        for interval,expected in ((1,[1,2,3,4]),(2,[2,4]),(4,[4])):
            solved=[];last=0
            for depth in (1,2,3,4):
                p=dict(decisionDepth=depth,lastSolvedDepth=last)
                if solve_due(p,interval):solved.append(depth);last=depth
            self.assertEqual(solved,expected)
        self.assertTrue(solve_due(dict(decisionDepth=1,lastSolvedDepth=0),4,forced=True))
        self.assertFalse(solve_due(dict(decisionDepth=1,lastSolvedDepth=1),1))

    def run_recovery_tree(self, reject_first=True, oracle=True, include_setup=False, finish_tick=30):
        from parkour_solver.search.support_profiles import prepare_scene
        from tests.unit.spatial.test_checkpoint_guidance import flat_scene
        from tests.unit.search.test_minimum_runs import flight
        s=flat_scene();s['goalBlock']='finish';s['maxTotalRunTicks']=60;s['maxRunTicksPerJump']=60
        # Extend the normalized scene through its raw request representation.
        raw={k:v for k,v in s.items() if k not in ('collisionScene',)}
        raw['blocks']=[dict(id=b['id'],boxes=[s['collisionScene']['solids'][b['solidIndex']]]) for b in s['blocks']]
        raw['blocks'].append(dict(id='finish',boxes=[[8,0,0,9,1,1]]))
        raw['movementSearch']['maxInputs']=max(raw['movementSearch']['maxInputs'],finish_tick)
        s=prepare_scene(raw);calls=[]
        def generate(target_scene,*,report,initial_programs,continuation=None,**kwargs):
            saved=copy.deepcopy(continuation) if continuation else dict(expanded=1,cursor=0,exhausted=False)
            report['continuation']=saved
            if target_scene['goalBlock']=='goal':
                base=dict(s,goalBlock='goal')
                choices=[flight(base,r) for r in (0,1,2)]
                if include_setup:
                    from parkour_solver.movement.normal import normal_choices
                    from parkour_solver.movement.program import empty_program, append_choice, boundary_state
                    parent=empty_program(base['initial'],threshold=0.);parent['terminalSupport']='start'
                    options=normal_choices(dict(scene=base,state=boundary_state(parent),maxInputs=30,threshold=0.))
                    parent=append_choice(parent,next(c for c in options if c['terminalSupport']=='start' and c['profile']['jump'][0]))
                    options=normal_choices(dict(scene=base,state=boundary_state(parent),maxInputs=30,threshold=0.))
                    choices.append(append_choice(parent,next(c for c in options if c['terminalSupport']=='goal' and c['profile']['jump'][0])))
            else:choices=[extend(s,initial_programs[0],'finish',finish_tick)]
            while saved['cursor']<len(choices):
                p=choices[saved['cursor']];saved['cursor']+=1;yield p
            saved['exhausted']=True
        def evaluate(target_scene,p,compiler,adapter,work,motion,**kwargs):
            work.before_solve({});n=p['profile']['airtime'];calls.append((p['terminalSupport'],n))
            rejected=reject_first and p['terminalSupport']=='finish' and p['profile']['jump'][12]
            best=None if rejected else dict(plan=dict(ticks=n,walls=p['profile']['positionConstraints']),
                candidate=dict(yaw=[0.]*n,positions=[[.5,.5]]*(n+1),velocity=[[0.,0.]]*(n+1),objective=0.),
                allocation=[0],cost=0)
            return dict(status='unknown' if rejected else 'feasible',termination='done',best=best,
                nodeFeasibility=dict(status=('oracle_rejected' if oracle else 'suspended') if rejected else 'feasible',model=str(n)),
                work=dict(solverCalls=1),fullSceneValidated=not rejected,
                repairState=dict(pending=[],unresolved=[],constraintGeneration=dict(exhausted=True)))
        with patch('parkour_solver.search.engine._motion_bound',return_value={}), \
             patch('parkour_solver.search.dfs.iter_movement_programs',side_effect=generate), \
             patch('parkour_solver.search.engine.evaluate_program',side_effect=evaluate):
            result=find_checkpoint_route(s,object(),anchor_search=dict(anchors=['goal']),seconds=3,max_solver_calls=20)
        return result,calls

    def test_failed_finish_restores_deferred_runs_on_actual_parent_without_redeferring(self):
        result,calls=self.run_recovery_tree()
        dfs=result['searchState']['dfs']
        self.assertEqual(calls[:4],[('goal',12),('finish',30),('goal',13),('finish',30)])
        self.assertEqual(result['anchorSearch']['runDeferrals'],2)
        self.assertEqual(result['anchorSearch']['runRestorations'],1)
        self.assertEqual(calls.count(('goal',13)),1)
        self.assertEqual(calls.count(('goal',14)),0)
        self.assertIsNotNone(result['best'])
        self.assertTrue(all(r['program']['terminalSupport']=='goal' for r in dfs['minimumRunExclusions']))

    def test_more_jump_history_remains_first_class_after_an_earlier_finish_succeeds(self):
        result,calls=self.run_recovery_tree(reject_first=False,include_setup=True,finish_tick=50)
        self.assertIn(('goal',24),calls)
        self.assertEqual(calls.count(('finish',50)),2)
        self.assertNotIn(('goal',13),calls)
        self.assertEqual(result['anchorSearch']['runRestorations'],0)
        decisions=result['searchState']['dfs']['comparisonDecisions'].values()
        self.assertTrue(any(d['action']=='keep_protected' and 'more_jump_inputs' in d['protectionReasons'] for d in decisions))

    def test_successful_finish_never_restores_extra_hard_jump_runs(self):
        result,calls=self.run_recovery_tree(reject_first=False)
        self.assertEqual(calls,[('goal',12),('finish',30)])
        self.assertEqual(result['anchorSearch']['runRestorations'],0)
        self.assertEqual(result['anchorSearch']['runDeferrals'],2)

    def test_unknown_descendant_does_not_trigger_infeasible_recovery(self):
        result,calls=self.run_recovery_tree(oracle=False)
        self.assertEqual(calls,[('goal',12),('finish',30)])
        self.assertEqual(result['anchorSearch']['runRestorations'],0)

    def test_external_resume_is_isolated_from_owned_internal_service(self):
        first,_=self.run_tree(calls=2)
        saved=json.loads(json.dumps(first['searchState']))
        before=copy.deepcopy(saved)
        self.run_tree(resume=saved)
        self.assertEqual(saved,before)


if __name__=='__main__':unittest.main()
