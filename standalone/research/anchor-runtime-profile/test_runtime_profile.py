import copy
import unittest
from unittest.mock import patch

from runtime_profile import CopyProxy, RuntimeProfile, instrument


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
        from standalone.autonomy import movement_frontier
        from standalone.autonomy.cell_routes import depth_first
        before = (depth_first.copy,depth_first.search_goals,movement_frontier.generate_choices)
        with self.assertRaisesRegex(RuntimeError,'stop'):
            with instrument(RuntimeProfile()):
                self.assertIsNot(depth_first.copy,before[0])
                raise RuntimeError('stop')
        self.assertEqual((depth_first.copy,depth_first.search_goals,movement_frontier.generate_choices),before)

    def test_session_with_no_numerical_calls_may_omit_count(self):
        from standalone.autonomy.cell_routes import depth_first
        p = RuntimeProfile()
        empty = dict(work={},expandedPrefixes=0,termination='deadline')
        with patch.object(depth_first,'search_goals',return_value=empty):
            with instrument(p):
                result = depth_first.search_goals(resume_state=None,deadline=0.)
        self.assertIs(result,empty)
        self.assertEqual(p.sessions[0]['calls'],0)
        self.assertEqual(p.sessions[0]['expanded'],0)
        self.assertIn('dfs.session',p.sessions[0]['buckets'])


if __name__ == '__main__':
    unittest.main()
