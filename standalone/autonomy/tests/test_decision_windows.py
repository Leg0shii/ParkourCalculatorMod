import copy
import itertools
import json
import os
from pathlib import Path
import time
import unittest
from unittest.mock import patch

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.checkpoint_search import find_checkpoint_route
from standalone.autonomy.cell_routes.decision_windows import DecisionPolicy, fair_programs, plan_windows, retain_candidates
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_search import iter_movement_programs
from standalone.autonomy.tests.test_checkpoint_guidance import flat_scene


def decision(name, lo, hi, **kwargs):
    return dict(id=name,inputs=[lo,hi],**kwargs)


class DecisionWindowTests(unittest.TestCase):
    def test_fixture_domains_include_the_known_physical_controls(self):
        from standalone.autonomy.cell_routes.window_diagnostic import controls
        root = Path(__file__).parents[1]/'experiments'
        for name,_,label,fixture,best in controls(root):
            scene = prepare_scene(fixture['request'])
            plan = plan_windows(fixture['opportunities'],gap=fixture['settings']['cluster_gap'],
                                max_inputs=scene['movementSearch']['maxInputs'])
            audit = DecisionPolicy(plan,scene).audit(best['movementProgram'])
            self.assertTrue(audit['accepted'],(name,label,audit['unmatched']))
            if name=='grind-or-run':
                self.assertEqual(len(plan['clusters']),1)

    def test_joint_cluster_runs_to_the_next_input_not_one_state_earlier(self):
        plan = plan_windows([decision('step',0,1),decision('jump',1,3),decision('ceiling',4,5),
                             decision('landing',12,15)],gap=2,max_inputs=30)
        self.assertEqual(plan['clusters'][0]['ids'],['step','jump','ceiling'])
        self.assertEqual(plan['checkpoints'],[dict(tick=12)])
        self.assertIsNone(plan['clusters'][-1]['checkpoint'])

    def test_dependencies_merge_the_intervening_decisions(self):
        plan = plan_windows([decision('a',0,1),decision('b',5,6),decision('c',10,11,dependsOn=['a']),
                             decision('d',20,25)],gap=0)
        self.assertEqual(plan['clusters'][0]['ids'],['a','b','c'])
        self.assertEqual(plan['checkpoints'],[dict(tick=20)])

    def test_incompatible_post_turn_boundary_absorbs_the_next_decision(self):
        plan = plan_windows([decision('turn',0,2,readyState=12),decision('contact',10,11),
                             decision('next',20,21)],gap=0)
        self.assertEqual(plan['clusters'][0]['ids'],['turn','contact'])
        self.assertEqual(plan['checkpoints'][0]['tick'],20)

    def test_earliest_onset_across_a_timing_range_is_the_shared_horizon(self):
        plan = plan_windows([decision('jump',0,4,segment=1),decision('next',10,14)],gap=0)
        self.assertEqual(plan['checkpoints'],[dict(tick=10,segment=1)])

    def test_rejects_bad_domains_and_dependencies(self):
        for values in [[decision('a',4,3)],[decision('a',0,1),decision('a',3,4)],
                       [decision('a',0,1,dependsOn=['missing'])]]:
            with self.assertRaises(ValueError):
                plan_windows(values)

    def test_band_retention_is_translation_invariant_and_adaptive(self):
        solved = [(dict(id=str(i),value=v),{}) for i,v in enumerate([10,9.9,9.6,8])]
        retained, detail = retain_candidates(solved,[],cap=8,epsilon=.15)
        self.assertEqual([r['id'] for r,_ in retained],['0','1'])
        self.assertEqual(detail['outsideBand'],2)
        translated = [(dict(r,value=r['value']-100),p) for r,p in solved]
        again,_ = retain_candidates(translated,[],cap=8,epsilon=.15)
        self.assertEqual([r['id'] for r,_ in again],['0','1'])
        wide,_ = retain_candidates(solved,[],cap=8,epsilon=.5)
        self.assertEqual(len(wide),3)

    def test_band_drops_caps_and_unknowns_have_separate_accounting(self):
        solved = [(dict(id=str(i),value=v),{}) for i,v in enumerate([1,.99,.98,.5])]
        unknown = [(dict(id='u'+str(i)),{}) for i in range(2)]
        retained, detail = retain_candidates(solved,unknown,cap=3,unknown_slots=1,epsilon=.1)
        self.assertEqual([r['id'] for r,_ in retained],['0','1','u0'])
        self.assertEqual(detail['outsideBand'],1)
        self.assertEqual(detail['scoredCapDrops'],1)
        self.assertEqual(detail['unknownCapDrops'],1)

    def test_band_does_not_fill_unused_score_slots_with_unknowns(self):
        solved = [(dict(id='good',value=1),{}),(dict(id='behind',value=0),{})]
        unknown = [(dict(id='u'+str(i)),{}) for i in range(8)]
        retained, detail = retain_candidates(solved,unknown,cap=8,unknown_slots=1,epsilon=.1)
        self.assertEqual([r['id'] for r,_ in retained],['good','u0'])
        self.assertEqual(detail['unknownCapDrops'],7)

    def test_policy_disallows_new_jump_choices_in_the_clear_continuation(self):
        scene = flat_scene()
        policy = DecisionPolicy(plan_windows([decision('launch',0,0,kinds=['jump']),
                                              decision('next',10,15,kinds=['landing'])]),scene)
        programs = list(itertools.islice(iter_movement_programs(scene,checkpoint_tick=4,threshold=0.,
                            deadline=time.monotonic()+2),32))
        initial = next(p for p in programs if p['profile']['jump'][0])
        delayed = next(p for p in programs if not p['profile']['jump'][0] and any(p['profile']['jump']))
        self.assertTrue(policy(initial))
        self.assertFalse(policy(delayed))
        self.assertTrue(any(e['kind']=='jump' for e in policy.audit(delayed)['unmatched']))

    def test_fair_generation_gives_each_parent_a_turn_and_bounded_quota(self):
        def generate(scene,settings,initial_programs,report,**kwargs):
            for i in range(settings['maxPrefixes']):
                report['expandedPrefixes'] = i+1
                yield dict(seed=initial_programs[0]['tag'],index=i)
        report = {}
        with patch('standalone.autonomy.movement_search.iter_movement_programs',generate), \
                patch('standalone.autonomy.cell_routes.checkpoint_search._key',lambda p:p['tag']):
            g = fair_programs({},[dict(tag='a'),dict(tag='b')],cfg=dict(maxPrefixes=6),fair=True,report=report)
            values = [next(g) for _ in range(4)]
            g.close()
        self.assertEqual([v['seed'] for v in values],['a','b','a','b'])
        self.assertEqual([r['prefixQuota'] for r in report['parents']],[3,3])
        self.assertEqual(report['expandedPrefixes'],4)


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for numerical tests')
class DecisionWindowNumericalTests(unittest.TestCase):
    def test_joint_decision_policy_and_original_state_replay_under_dfs(self):
        scene = flat_scene()
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            result = find_checkpoint_route(scene,compiler,opportunities=[decision('launch',0,2,kinds=['jump']),
                decision('finish',6,20,kinds=['landing'])],cluster_gap=1,objective_gap=.25,fair_parents=True,
                spatial_points=[[.5,1,.5],[5.5,1,.5]],seconds=12,max_candidates=12,beam_width=6,
                max_solver_calls=48,score_call_limit=4,score_seconds=2,candidate_seconds=.7,
                python_executable=os.environ['PKC_IPOPT_PYTHON'])
            self.assertEqual(result['checkpoints'][0]['tick'],6)
            self.assertEqual(result['comparisonPolicy'],'goal_arrival_dfs')
            self.assertEqual(len(result['decisionPlan']['clusters']),2)
            scored = [c for c in result['finalAttempts'] if c.get('witness')]
            self.assertTrue(scored)
            for record in scored:
                self.assertTrue(record['decisionAudit']['accepted'])
                self.assertTrue(all(not (2<e['input']<6) for e in record['decisionAudit']['events']))
                self.assertEqual(record['goal'],dict(kind='landing',block=scene['goalBlock']))
                self.assertTrue(replay_witness(scene,record['witness'],compiler)['passed'])
            self.assertEqual(result['status'],'feasible',result['termination'])
            self.assertTrue(replay_witness(scene,result['best'],compiler)['passed'])


if __name__=='__main__':
    unittest.main()
