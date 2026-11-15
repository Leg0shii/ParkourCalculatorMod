import copy
import json
import unittest

from research.legacy_cell_routes.generation_diagnostic import KeyAudit, QueueAudit, instrumented_generator
from parkour_solver.search.movement import iter_movement_programs, movement_settings
from tests.unit.spatial.test_checkpoint_guidance import flat_scene


class FailureDiagnosticTests(unittest.TestCase):
    def test_instrumented_baseline_preserves_generator_outputs_and_counts(self):
        scene = flat_scene()
        cfg = dict(movement_settings(scene),maxInputs=16,maxPrefixes=64,maxQueuedPrefixes=96)
        scene['blocks'][-1]['top'] = 1
        original_report,probe_report = {},{}
        original = list(iter_movement_programs(scene,settings=cfg,threshold=0.,report=original_report,queue_policy='legacy'))
        self.assertTrue(original)
        probe = list(instrumented_generator(QueueAudit(scene['goalBlock'],'shortest'),KeyAudit(False))(
            scene,settings=cfg,threshold=0.,report=probe_report))
        self.assertEqual(original,probe)
        for key in ('expandedPrefixes','generated','remainingPrefixes','screenedPrefixes','queueDrops','termination'):
            self.assertEqual(original_report[key],probe_report[key])

    def test_completed_candidate_priority_is_separate_from_partial_length(self):
        queue = QueueAudit('goal','complete_first')
        pending = []
        def item(t,serial,goal):
            return t,serial,dict(terminalSupport='goal' if goal else 'start',profile=dict(grounded=[True]))
        queue.heappush(pending,item(2,1,False))
        queue.heappush(pending,item(11,2,True))
        self.assertEqual(queue.heappop(pending)[0],11)
        self.assertEqual(queue.heappop(pending)[0],2)

    def test_tick_fair_pruning_preserves_multiple_elapsed_buckets(self):
        queue = QueueAudit('goal','tick_fair')
        pending = [(t,i,dict(terminalSupport=None,profile=dict(grounded=[False])))
                   for i,t in enumerate([1,1,1,2,2,3,3])]
        kept = queue.nsmallest(3,pending)
        self.assertEqual([v[0] for v in kept],[1,2,3])

    def test_canonical_key_keeps_different_interaction_budgets_separate(self):
        from parkour_solver.movement.program import empty_program
        program = empty_program(dict(position=1,velocity=-.0784,onGround=True),threshold=0.)
        alternative = copy.deepcopy(program)
        alternative['interactions'] += 1
        audit = KeyAudit(True)
        self.assertNotEqual(audit(program),audit(alternative))

    def test_canonicalization_removes_only_metadata_in_the_key_audit(self):
        from parkour_solver.movement.program import empty_program
        program = empty_program(dict(position=1,velocity=-.0784,onGround=True),threshold=0.)
        alternative = copy.deepcopy(program)
        alternative['profile']['generatedUnits'] = [dict(type='normal',start=0,end=1)]
        audit = KeyAudit(True)
        self.assertEqual(audit(program),audit(alternative))
        self.assertEqual(audit.metadata_duplicates,1)
        alternative['profile']['grounded'] = [False]
        self.assertNotEqual(audit(program),audit(alternative))


if __name__=='__main__':
    unittest.main()
