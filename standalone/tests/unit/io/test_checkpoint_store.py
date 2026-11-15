import copy
import gzip
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from parkour_solver.io.checkpoint_store import CheckpointStore, read_report
from parkour_solver.io.search_checkpoint import SearchCheckpoint
import tests.support.repair_resume as repair_tests
import tests.support.depth_first as dfs_tests


class StoreCase(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name)/'report.json.gz'


class CheckpointStoreTests(StoreCase):
    def test_lossless_sharing_without_mutable_aliases(self):
        shared = dict(array=[[.1,-2.,3.]]*200, literal=['r','not-a-reference'], escaped={'format':'pkc-checkpoint-store'})
        report = dict(a=shared,b=copy.deepcopy(shared),special=['d',[['r','fake']]],unicode='⛏',empty={},number=123)
        with CheckpointStore(self.path) as store:
            first = store.save(report)
            size = store.database.stat().st_size
            again = store.save(report)
            self.assertGreater(first['newObjects'],0)
            self.assertEqual(again['newObjects'],0)
            self.assertEqual(size,store.database.stat().st_size)
        restored = read_report(self.path)
        self.assertEqual(restored,report)
        restored['a']['array'][0][0] = 999
        self.assertEqual(restored['b'],shared)
        self.assertEqual(restored['a']['array'][1][0],.1)

    def test_changes_are_detected_across_saves_and_reopening(self):
        report = dict(queue=[dict(rows=[0.]*1000)],seen=['x'*3000])
        with CheckpointStore(self.path) as store:
            store.save(report)
            report['queue'][0]['rows'][0] = 42.
            store.save(report)
        self.assertEqual(read_report(self.path),report)
        with CheckpointStore(self.path) as store:
            self.assertEqual(store.save(report)['newObjects'],0)

    def test_failed_manifest_publication_preserves_previous_committed_root(self):
        with CheckpointStore(self.path) as store:
            store.save(dict(accepted='old'))
            with patch('parkour_solver.io.checkpoint_store._atomic_json',side_effect=OSError('interrupted')):
                with self.assertRaises(OSError):
                    store.save(dict(accepted='new'))
            self.assertEqual(read_report(self.path),dict(accepted='old'))
            store.save(dict(accepted='new'))
        self.assertEqual(read_report(self.path),dict(accepted='new'))

    def test_legacy_and_companion_errors(self):
        for name in ('old.json','old.json.gz'):
            path = self.path.with_name(name)
            data = json.dumps(dict(searchState={'version':5})).encode()
            path.write_bytes(gzip.compress(data) if path.suffix=='.gz' else data)
            self.assertEqual(read_report(path),dict(searchState={'version':5}))
        with CheckpointStore(self.path) as store:
            store.save(dict(pending=[1,2]))
            database = store.database
        manifest = json.loads(gzip.decompress(self.path.read_bytes()))
        manifest['storeId'] = 'wrong'
        self.path.write_bytes(gzip.compress(json.dumps(manifest).encode()))
        with self.assertRaisesRegex(ValueError,'does not match'):
            read_report(self.path)
        database.unlink()
        with self.assertRaisesRegex(ValueError,'Missing checkpoint companion'):
            read_report(self.path)

    def test_patch_and_compaction_keep_needed_roots(self):
        with CheckpointStore(self.path) as store:
            base, _ = store.archive(dict(unchanged={'log':['x'*4000]},candidate=None))
            store.patch(base,[(['candidate'],dict(objective=1.))])
            store.compact()
            self.assertEqual(read_report(self.path)['candidate'],dict(objective=1.))
            store.patch(base,[(['candidate'],dict(objective=2.))])
            self.assertEqual(read_report(self.path)['candidate'],dict(objective=2.))
            store.save(dict(final=True))
            store.compact()
            self.assertEqual(read_report(self.path),dict(final=True))
            self.assertLess(len(store.known),5)


class SearchCheckpointTests(StoreCase):
    def test_nested_internal_node_repair_patches_program_cache_and_calls(self):
        program=dict(repairState=None)
        entry=dict(status='suspended',repairState=None)
        frame=dict(inFlight=None,generation=dict(nodeInFlight=program,nodes={'node':entry}))
        dfs=dict(stack=[frame],windows=[],cumulativeCalls=0)
        outer=dict(kind='event_route_search',dfs=dfs,best=None)
        with CheckpointStore(self.path) as store:
            writer=SearchCheckpoint(store,{})
            writer.begin_repair(outer,program,(entry,))
            for i in range(2):
                repair=dict(pending=[i],attemptLog=[i])
                program['repairState']=repair;entry['repairState']=repair
                dfs['cumulativeCalls']+=1
                with patch.object(SearchCheckpoint,'__call__',side_effect=AssertionError('full checkpoint')):
                    writer.repair_progress(outer)
                self.assertEqual(read_report(self.path)['searchState'],outer)
            result=dict(searchState=outer,windows=[],best=None)
            writer.finish(result,[])
            self.assertEqual(read_report(self.path)['result'],result)

    def state(self):
        program = dict(repairState=dict(pending=[1],seen=[],attemptLog=[],contactLog=[]))
        frame = dict(id=3,inFlight=dict(program=program),records=[dict(program=program)])
        # Refinement frames and their programs can have multiple aliases.
        return dict(stack=[frame],frames=[frame],windows=[dict(candidates=[dict(id='past')])],
                    cumulativeCalls=1,transitions=[dict(kind='dispatch')])

    def test_repair_patch_is_full_state_equivalent_including_aliases(self):
        state = self.state()
        metadata = dict(source='test',fixture={})
        with CheckpointStore(self.path) as store:
            writer = SearchCheckpoint(store,metadata)
            writer(state)
            for i in range(3):
                state['stack'][-1]['inFlight']['program']['repairState'] = dict(pending=[2+i],completed=[i],attemptLog=[i])
                state['cumulativeCalls'] += 1
                # A repair publication must not walk/archive the outer tree.
                with patch.object(SearchCheckpoint,'__call__',side_effect=AssertionError('full save')):
                    writer.repair_progress(state)
                self.assertEqual(read_report(self.path)['searchState'],state)
            result = dict(searchState=state,windows=state['windows'],finalAttempts=[],best=None)
            writer.finish(result,[dict(passed=True)])
        restored = read_report(self.path)
        self.assertEqual(restored['result'],result)
        self.assertEqual(restored['windows'],state['windows'])
        self.assertEqual(restored['validation'],[dict(passed=True)])

    def test_completed_repair_is_not_resolved_after_persistent_resume(self):
        helper = repair_tests.RepairHarness()
        first = helper.invoke(repair_tests.route(),lambda *a,**k:repair_tests.outcome(),lambda *a:dict(accepted=True))
        state = self.state()
        with CheckpointStore(self.path) as store:
            writer = SearchCheckpoint(store,{})
            writer(state)
            state['stack'][-1]['inFlight']['program']['repairState'] = first['repairState']
            writer.repair_progress(state)
        restored = read_report(self.path)['searchState']['stack'][-1]['inFlight']['program']['repairState']
        second = helper.invoke(repair_tests.route(),lambda *a,**k:self.fail('Completed request restarted'),lambda *a:dict(accepted=True),
                               resume_state=restored)
        self.assertEqual(second['status'],'feasible')

        self.assertEqual(second['attempts'],[])

    def test_dfs_continues_siblings_without_repeating_completed_calls(self):
        helper = dfs_tests.DepthFirstHarness()
        helper.calls = []
        with CheckpointStore(self.path) as store:
            helper.progress = SearchCheckpoint(store,{})
            first = helper.run_search(3)
        self.assertEqual(read_report(self.path)['searchState'],first['searchState'])
        self.assertEqual(first['best']['candidate']['objective'],17.)
        with CheckpointStore(self.path) as store:
            helper.progress = SearchCheckpoint(store,{})
            second = helper.run_search(10,read_report(self.path)['searchState'])
            helper.progress.finish(second,[])
        self.assertEqual(helper.calls,[(17,17),(21,21),(17,30),(21,30)])
        self.assertEqual(read_report(self.path)['result'],json.loads(json.dumps(second)))

    def test_dfs_mid_solve_publication_restores_the_active_candidate(self):
        helper = dfs_tests.DepthFirstHarness()
        helper.calls, helper.interrupt_once = [], (17,30)
        with CheckpointStore(self.path) as store:
            helper.progress = SearchCheckpoint(store,{})
            first = helper.run_search(10)
        saved = read_report(self.path)['searchState']
        self.assertEqual(saved,first['searchState'])
        self.assertEqual(saved['stack'][-1]['inFlight']['program']['repairState']['marker'],'unfinished')
        with CheckpointStore(self.path) as store:
            helper.progress = SearchCheckpoint(store,{})
            second = helper.run_search(10,saved)
        self.assertEqual(helper.calls.count((17,17)),1)
        self.assertEqual(helper.calls.count((21,21)),1)
        self.assertEqual(helper.calls.count((17,30)),2)  # Only interrupted work repeats.
        self.assertEqual(second['best']['candidate']['objective'],21.)

    def test_interrupted_repair_keeps_pending_branch_and_audit(self):
        helper = repair_tests.RepairHarness()
        state = self.state()
        with CheckpointStore(self.path) as store:
            writer = SearchCheckpoint(store,{})
            writer(state)
            def progress(repair):
                state['stack'][-1]['inFlight']['program']['repairState'] = repair
                writer.repair_progress(state)
            def interrupted(*args,**kwargs):
                raise RuntimeError('interrupted worker')
            with self.assertRaisesRegex(RuntimeError,'interrupted worker'):
                helper.invoke(repair_tests.route(),interrupted,lambda *a:dict(accepted=True),on_progress=progress)
        restored = read_report(self.path)['searchState']
        self.assertEqual(restored,state)
        repair = restored['stack'][-1]['inFlight']['program']['repairState']
        self.assertIsNotNone(repair['inFlight'])
        second = helper.invoke(repair_tests.route(),lambda *a,**k:repair_tests.outcome(),lambda *a:dict(accepted=True),resume_state=repair)
        self.assertEqual(second['status'],'feasible')


@unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER') and os.environ.get('PKC_IPOPT_PYTHON'),
                     'Set compiler and IPOPT Python for native persistence checks')
class NativeCheckpointTests(StoreCase):
    def test_nested_anchor_and_internal_node_repair_publications_match_live_state(self):
        from parkour_solver.bridge.compiler import JavaCompiler
        from parkour_solver.search.engine import find_checkpoint_route
        from parkour_solver.search.domain import InitialJumpDomain
        from tests.support.checkpoint_guidance import flat_scene
        scene=flat_scene();scene['movementSearch'].update(maxInputs=28,maxPrefixes=64)
        checked=[];owners=[];testcase=self
        class CheckedWriter(SearchCheckpoint):
            def begin_repair(self,state,program,extra_targets=()):
                super().begin_repair(state,program,extra_targets)
                owners.append(bool(extra_targets))
            def repair_progress(self,state):
                super().repair_progress(state)
                testcase.assertEqual(read_report(testcase.path)['searchState'],json.loads(json.dumps(state)))
                checked.append(True)
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler, CheckpointStore(self.path) as store:
            writer=CheckedWriter(store,{})
            result=find_checkpoint_route(scene,compiler,anchor_search={},seconds=8,score_seconds=2,
                max_solver_calls=20,program_filter=InitialJumpDomain([0],max_inputs=28),
                python_executable=os.environ['PKC_IPOPT_PYTHON'],on_progress=writer)
            writer.finish(result,[])
            self.assertEqual(read_report(self.path)['result'],json.loads(json.dumps(result)))
        self.assertTrue(checked)
        self.assertIn(True,owners,'No internal-node checkpoint was exercised')

    def test_every_native_repair_publication_round_trips_before_completion(self):
        from parkour_solver.bridge.compiler import JavaCompiler
        from parkour_solver.search.engine import find_checkpoint_route
        from research.legacy_cell_routes.checkpoint_experiment import replay_witness
        from parkour_solver.search.compat.support_order import SupportOrder
        from tests.support.checkpoint_guidance import flat_scene
        scene = flat_scene()
        scene['movementSearch']['maxInputs'] = 20
        scene['objective'] = dict(axis='X',sense='MAX')
        goal = dict(kind='landing_height_region',height=2.,
            rows=[dict(mode='X',cmp='GE',rhs=1.5),dict(mode='Z',cmp='GE',rhs=-2.)])
        checked = []
        testcase = self
        class CheckedWriter(SearchCheckpoint):
            def repair_progress(self,state):
                super().repair_progress(state)
                testcase.assertEqual(read_report(testcase.path)['searchState'],json.loads(json.dumps(state)))
                checked.append(True)
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler, \
             CheckpointStore(self.path) as store:
            writer = CheckedWriter(store,{})
            result = find_checkpoint_route(scene,compiler,spatial_points=[[.5,1,.5],[8.,3,.5]],
                checkpoints=[],spatial_goals=[goal],program_filter=SupportOrder(scene,[],None,[0,1],max_inputs=20),
                seconds=12,max_candidates=8,max_solver_calls=24,candidate_seconds=.8,score_seconds=2,
                python_executable=os.environ['PKC_IPOPT_PYTHON'],on_progress=writer)
            self.assertEqual(result['status'],'feasible')
            replay = replay_witness(scene,result['best'],compiler)
            self.assertTrue(replay['passed'])
            writer.finish(result,[replay])
        self.assertTrue(checked)
        self.assertEqual(read_report(self.path)['result'],json.loads(json.dumps(result)))


if __name__ == '__main__':
    unittest.main()
