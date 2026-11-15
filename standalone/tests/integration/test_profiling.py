import copy
import unittest
from unittest.mock import patch

from tools.profiling.runtime import CopyProxy, RuntimeProfile, instrument
from tools.profiling.bookkeeping import BookkeepingProfile, instrument as instrument_bookkeeping


class RuntimeProfileTests(unittest.TestCase):
    def test_nested_timings_are_exclusive_and_exception_safe(self):
        ticks = iter([0.,1.,3.,5.])
        p = RuntimeProfile(clock=lambda:next(ticks))
        with self.assertRaisesRegex(ValueError, 'stop'):
            with p.region('outer'):
                with p.region('inner'):
                    raise ValueError('stop')
        self.assertEqual(p.buckets['outer']['inclusiveSeconds'],5.)
        self.assertEqual(p.buckets['outer']['selfSeconds'],3.)
        self.assertEqual(p.buckets['inner']['selfSeconds'],2.)
        self.assertEqual(p.stack,[])

    def test_copy_proxy_preserves_aliases_cycles_and_original_library(self):
        original = copy.deepcopy
        p = RuntimeProfile();proxy = CopyProxy(p,'sample')
        shared = [];source = [shared,shared];shared.append(source)
        result = proxy.deepcopy(source)
        self.assertIs(result[0],result[1])
        self.assertIs(result[0][0],result)
        self.assertIsNot(result,source)
        self.assertIs(copy.deepcopy,original)
        self.assertEqual(sum(v['calls'] for v in p.buckets.values()),1)

    def test_generator_measures_only_active_next_and_closes(self):
        closed = []
        p = RuntimeProfile()
        def source():
            try:
                yield 1
                yield 2
            finally:
                closed.append(True)
        g = p.generator('choices',source)()
        self.assertEqual(next(g),1)
        self.assertEqual(p.stack,[])
        g.close()
        self.assertEqual(closed,[True])
        self.assertEqual(p.buckets['choices.next']['calls'],1)

    def test_scoped_instrumentation_restores_module_references_on_failure(self):
        from parkour_solver.search import frontier as movement_frontier
        from parkour_solver.search import dfs as depth_first
        before = (depth_first.copy,depth_first.search_goals,movement_frontier.generate_choices)
        with self.assertRaisesRegex(RuntimeError,'stop'):
            with instrument(RuntimeProfile()):
                self.assertIsNot(depth_first.copy,before[0])
                raise RuntimeError('stop')
        self.assertEqual((depth_first.copy,depth_first.search_goals,movement_frontier.generate_choices),before)

    def test_session_with_no_numerical_calls_may_omit_count(self):
        from parkour_solver.search import dfs as depth_first
        p = RuntimeProfile()
        empty = dict(work={},expandedPrefixes=0,termination='deadline')
        with patch.object(depth_first,'search_goals',return_value=empty):
            with instrument(p):
                result = depth_first.search_goals(resume_state=None,deadline=0.)
        self.assertIs(result,empty)
        self.assertEqual(p.sessions[0]['calls'],0)
        self.assertEqual(p.sessions[0]['expanded'],0)
        self.assertIn('dfs.session',p.sessions[0]['buckets'])


class BookkeepingProfileTests(unittest.TestCase):
    def test_alias_imported_during_context_is_restored(self):
        import sys
        from types import ModuleType
        from parkour_solver.search import identity
        original = identity.digest
        late = ModuleType('parkour_solver._profiling_late_import')
        with patch.dict(sys.modules, {late.__name__: late}):
            with instrument_bookkeeping(BookkeepingProfile(RuntimeProfile())):
                late.digest = identity.digest
                self.assertIsNot(late.digest, original)
            self.assertIs(late.digest, original)

    def test_instrumented_preparation_matches_unwrapped_model_and_cache_hits(self):
        from tests.support.checkpoint_guidance import flat_scene
        from tests.support.minimum_runs import flight
        from tests.support.proposal_analysis import Coefficients
        from parkour_solver.reachability.service import InteractionGate
        scene = flat_scene(); program = flight(scene, 0)
        expected = InteractionGate(scene, Coefficients()).analyze(program)
        compiler = Coefficients(); gate = InteractionGate(scene, compiler)
        profile = BookkeepingProfile(RuntimeProfile(), sample_every=1)
        with instrument_bookkeeping(profile):
            first = gate.analyze(program)
            self.assertIs(first, gate.analyze(program))
        self.assertEqual(first, expected)
        self.assertEqual(compiler.calls, 1)
        for kind in ('preparation', 'coefficient', 'analysis'):
            row = profile.snapshot()['caches'][kind]
            self.assertEqual((row['calls'], row['hits'], row['misses']), (2,1,1))

    def test_digest_sampling_is_bounded_and_preserves_canonical_output(self):
        from parkour_solver.search.identity import digest
        profile = BookkeepingProfile(RuntimeProfile(), sample_every=2, sample_limit=3)
        wrapped = profile.digest(digest)
        value = dict(z=[-0., 1.1234567890123457], a={'owner': 'retained'})
        expected = digest(value)
        results = [wrapped(value) for _ in range(20)]
        self.assertEqual(results, [expected]*20)
        row = next(iter(profile.snapshot()['digests'].values()))
        self.assertEqual((row['calls'], row['samples'], row['sampledDistinct'], row['sampledRepeated']), (20,3,1,2))

    def test_hooks_restore_aliases_and_copy_behavior_after_exception(self):
        from parkour_solver.search import identity, engine
        from parkour_solver.reachability.service import InteractionGate
        from parkour_solver.constraints import variants
        before = (identity.digest, variants.digest, engine.copy, InteractionGate.prepare)
        shared = []; source = [shared, shared]; shared.append(source)
        p = BookkeepingProfile(RuntimeProfile(), sample_every=1)
        with self.assertRaisesRegex(RuntimeError, 'stop'):
            with instrument_bookkeeping(p):
                result = engine.copy.deepcopy(source)
                self.assertIs(result[0], result[1])
                self.assertIs(result[0][0], result)
                self.assertEqual(identity.digest({'x': 1}), variants.digest({'x': 1}))
                raise RuntimeError('stop')
        self.assertEqual((identity.digest, variants.digest, engine.copy, InteractionGate.prepare), before)
        self.assertEqual(sum(r['calls'] for r in p.copies.values()), 1)

    def test_cache_measurement_preserves_invalidation_and_failed_build(self):
        from parkour_solver.reachability.service import InteractionGate
        gate = InteractionGate({}, None)
        p = BookkeepingProfile(RuntimeProfile(), distinct_limit=1)
        original = InteractionGate._cached
        def fail():
            raise TimeoutError('interrupted build')
        with instrument_bookkeeping(p):
            first = gate._cached(('test', 'one'), lambda: ['value'], 'test')
            self.assertIs(gate._cached(('test', 'one'), fail, 'test'), first)
            with self.assertRaises(TimeoutError):
                gate._cached(('test', 'two'), fail, 'test')
        self.assertIs(InteractionGate._cached, original)
        self.assertNotIn(('test', 'two'), gate.cache)
        row = p.snapshot()['caches']['test']
        self.assertEqual((row['calls'], row['hits'], row['misses']), (3,1,2))
        self.assertTrue(row['keyTrackingTruncated'])

    def test_copy_allocation_probe_does_not_reuse_or_mutate_supplied_memo(self):
        p = BookkeepingProfile(RuntimeProfile(), allocation_samples=1)
        memo = {}; original = [{'x': list(range(50000))}]
        result = p.copy(copy.deepcopy, 'site', original, memo)
        self.assertIs(memo[id(original)], result)
        self.assertEqual(result, original)
        self.assertEqual(len(p.copies['site']['allocations']), 1)
        import tracemalloc
        self.assertFalse(tracemalloc.is_tracing())

    def test_durable_timing_adapter_preserves_callback_protocol(self):
        from tools.profiling.run import DurableProgress
        from unittest.mock import Mock
        checkpoint = Mock()
        runtime = RuntimeProfile()
        adapter = DurableProgress(checkpoint, runtime)
        state, program, extra = {}, {}, [{}]
        adapter(state, program, extra_targets=extra)
        adapter.begin_repair(state, program, extra_targets=extra)
        adapter.repair_progress(state)
        checkpoint.assert_called_once_with(state, program, extra_targets=extra)
        checkpoint.begin_repair.assert_called_once_with(state, program, extra_targets=extra)
        checkpoint.repair_progress.assert_called_once_with(state)
        self.assertEqual(len(runtime.buckets), 3)


class EnumerationProfileTests(unittest.TestCase):
    def test_repeated_contexts_share_report_rows_without_losing_counts(self):
        from parkour_solver.constraints.variants import ConstraintVariants
        from tools.profiling.enumeration import EnumerationProfile,instrumented_option
        profile=EnumerationProfile(); function=instrumented_option(ConstraintVariants.option,profile)
        first=None
        for _ in range(2):
            source=self.source();cursor={}
            while function(source,source.items[0],cursor) is not None:pass
            snapshot=profile.snapshot()
            if first is None:
                first=(len(profile.rows),snapshot['totals']['visits'])
        self.assertEqual(len(profile.rows),first[0])
        self.assertEqual(snapshot['totals']['visits'],first[1]*2)
        self.assertTrue(snapshot['exitsPartitionVisits'])

    def test_enumeration_hooks_restore_after_exception_without_global_probe(self):
        from contextlib import nullcontext
        from parkour_solver.constraints.variants import ConstraintVariants
        from parkour_solver.search import engine
        from tools.profiling.enumeration import EnumerationProfile,instrument
        original, evaluate = ConstraintVariants.option, engine.evaluate_program
        with self.assertRaisesRegex(RuntimeError,'stop'):
            with instrument(EnumerationProfile(),nullcontext()):
                self.assertIsNot(ConstraintVariants.option,original)
                raise RuntimeError('stop')
        self.assertIs(ConstraintVariants.option,original)
        self.assertIs(engine.evaluate_program,evaluate)
        self.assertNotIn('_enumeration_probe',original.__globals__)

    @staticmethod
    def source(state=None):
        from parkour_solver.constraints.variants import ConstraintVariants
        from parkour_solver.reachability.intervals import reachable_envelopes
        model=dict(ticks=4,position=[-1.,0.],velocity=[0.,0.],magnitude=[1.]*4,friction=[.9]*4,walls=[])
        return ConstraintVariants(dict(passageCaps=dict(horizontal=2,vertical=2,total=3),
            supportClearance=0.,contactEvents=[]),dict(position=[1.]*5),
            dict(events=[dict(jump=0,runTicks=0)]),dict(solids=[[0.,0.,-.2,1.,3.,.2]]),
            reachable_envelopes(model,[1.]*5),dict(through=4,solidIndices=[0]),state=state)

    def test_option_observations_preserve_outputs_cursors_and_exit_partition(self):
        from parkour_solver.constraints.variants import ConstraintVariants
        from tools.profiling.enumeration import EnumerationProfile,instrumented_option
        original=ConstraintVariants.option
        def consume(source, function):
            cursor={};values=[]
            while True:
                value=function(source,source.items[0],cursor)
                if value is None:break
                values.append(value)
            return values,cursor
        expected=consume(self.source(),original)
        p=EnumerationProfile()
        actual=consume(self.source(),instrumented_option(original,p))
        self.assertEqual(actual,expected)
        summary=p.snapshot()
        self.assertTrue(summary['exitsPartitionVisits'])
        self.assertGreater(summary['totals']['visits'],len(actual[0]))
        self.assertEqual(summary['totals']['returned'],len(actual[0]))

    def test_json_interruption_cursor_is_identical_with_observations(self):
        import json
        from parkour_solver.constraints.variants import ConstraintVariants
        from tools.profiling.enumeration import EnumerationProfile,instrumented_option
        def partial(function):
            source=self.source();source.prepare_item(source.items[0]);cursor={};count=0;values=[]
            def check():
                nonlocal count
                count+=1
                if count>25:raise TimeoutError('fixed work')
            source.check=check
            with self.assertRaises(TimeoutError):
                while True:
                    value=function(source,source.items[0],cursor)
                    if value is None:break
                    values.append(value)
            return values,json.loads(json.dumps(cursor)),json.loads(json.dumps(source.items[0]))
        expected=partial(ConstraintVariants.option)
        p=EnumerationProfile()
        self.assertEqual(partial(instrumented_option(ConstraintVariants.option,p)),expected)
        self.assertTrue(p.snapshot()['exitsPartitionVisits'])

    def test_source_change_is_rejected_instead_of_silent_miscount(self):
        from tools.profiling.enumeration import EnumerationProfile,instrumented_option
        with self.assertRaisesRegex(RuntimeError,'source changed'):
            instrumented_option(self.source,EnumerationProfile())


if __name__ == '__main__':
    unittest.main()
