import unittest

from parkour_solver.search.comparison import comparison_label, compare_candidates, priority_key


def label(t, j, r, *, mode='ordinary', identity='candidate', context='span'):
    return dict(id=identity, T=t, start=0, J=j, R=r, A=t-j-r, mode=mode,
                tickCost=t-j-r if mode=='turnaround' else r,
                comparisonContext=context, basis='anchor_arrival', validated=True)


CONTEXT = dict(purpose='hard_anchor', backend='automatic_anchor', hardLatched=True,
               localDeferralEnabled=True, comparisonContextId='span')


class CandidateComparisonTests(unittest.TestCase):
    def compare(self, candidate, peer=None, **kwargs):
        return compare_candidates(candidate, peer, context=kwargs.pop('context', CONTEXT), **kwargs)

    def test_later_real11_preparation_is_protected_without_feasibility_fallback(self):
        fast, chained = label(17,1,8,identity='initial8'), label(21,2,0,identity='initial0')
        verdict = self.compare(chained, fast)
        self.assertEqual(verdict['action'], 'keep_protected')
        self.assertEqual(set(verdict['protectionReasons']), {'more_jump_inputs','fewer_run_inputs'})
        self.assertEqual(priority_key(fast)[:3], (17,-1,8))
        self.assertEqual(priority_key(chained)[:3], (21,-2,0))

    def test_each_structural_guard_is_independent(self):
        peer = label(20,1,8)
        self.assertEqual(self.compare(label(21,2,9),peer)['protectionReasons'], ['more_jump_inputs'])
        self.assertEqual(self.compare(label(21,1,7),peer)['protectionReasons'], ['fewer_run_inputs'])

    def test_more_air_alone_does_not_protect_an_ordinary_candidate(self):
        self.assertEqual(self.compare(label(21,1,0), label(20,1,0))['action'], 'defer_preparation')

    def test_turnaround_protects_fewer_air_not_raw_more_run(self):
        peer = label(20,1,10,mode='turnaround')
        better_air = label(21,1,12,mode='turnaround')
        extra_run = label(21,1,11,mode='turnaround')
        self.assertEqual(self.compare(better_air,peer)['protectionReasons'], ['fewer_air_inputs'])
        self.assertEqual(self.compare(extra_run,peer)['action'], 'defer_preparation')
        self.assertEqual(priority_key(better_air)[:3], (21,-1,8))

    def test_equal_time_and_incompatible_modes_or_incoming_histories_stay_live(self):
        peer = label(20,2,0)
        self.assertEqual(self.compare(label(20,1,1),peer)['action'], 'keep_equal_time')
        self.assertEqual(self.compare(label(21,1,1,context='other'),peer)['action'], 'keep_incomparable')
        self.assertEqual(self.compare(label(21,1,1,mode='turnaround'),peer)['action'], 'keep_incomparable')

    def test_unlatched_and_disabled_contexts_skip_peer_work_but_still_check_finish(self):
        for field in ('hardLatched','localDeferralEnabled'):
            context = dict(CONTEXT, **{field:False})
            decision = self.compare(label(21,1,1),label(20,1,1),context=context)
            self.assertEqual(decision['action'],'keep')
            self.assertFalse(decision['peerLookupAllowed'])
            self.assertEqual(self.compare(label(21,1,1),context=context,incumbent=dict(T=20))['action'], 'exclude_bound')

    def test_finish_bound_overrides_local_protection_and_keeps_equality(self):
        candidate=label(21,2,0)
        candidate['remainingFinishBound']=dict(ticks=43,proofIds=['flight'])
        self.assertEqual(self.compare(candidate,label(17,1,8),incumbent=dict(T=63))['action'], 'exclude_bound')
        self.assertEqual(self.compare(candidate,label(17,1,8),incumbent=dict(T=64))['action'], 'keep_protected')
        candidate['remainingFinishBound']=dict(ticks=0)
        self.assertNotEqual(self.compare(candidate,incumbent=dict(T=21))['action'], 'exclude_bound')

    def test_partial_prefix_cannot_be_deferred_against_a_complete_arrival(self):
        candidate=dict(label(25,1,9),basis='open_prefix')
        self.assertEqual(self.compare(candidate,label(17,1,8))['action'], 'keep_incomparable')
        self.assertEqual(self.compare(candidate,incumbent=dict(T=24))['action'], 'exclude_bound')

    def test_fixed_clock_and_unvalidated_peer_do_not_create_new_exclusions(self):
        candidate=label(21,1,9)
        self.assertNotEqual(self.compare(candidate,incumbent=dict(T=20),context=dict(CONTEXT,fixedTerminal=True))['action'], 'exclude_bound')
        self.assertEqual(self.compare(candidate,dict(label(20,1,8),validated=False))['action'], 'keep_incomparable')

    def test_interval_counts_cancelled_ground_impulses_and_ignored_air_presses(self):
        program=dict(profile=dict(airtime=5,grounded=[True,True,False,False,True,True],
                                  jump=[False,True,True,False,True]))
        c=comparison_label(program,start=1,mode='turnaround')
        self.assertEqual({k:c[k] for k in ('T','J','R','A','start')}, dict(T=5,J=2,R=0,A=2,start=1))
        self.assertEqual(priority_key(c)[:3], (5,-2,2))
        c=comparison_label(program,start=1,through=4)
        self.assertEqual({k:c[k] for k in ('T','J','R','A')}, dict(T=4,J=1,R=0,A=2))

    def test_missing_count_metadata_is_not_zero_for_peer_pruning(self):
        missing=comparison_label(dict(profile=dict(airtime=21)))
        self.assertIsNone(missing['J'])
        self.assertEqual(self.compare(missing,label(17,1,8))['action'], 'keep_incomparable')
        self.assertEqual(self.compare(missing,incumbent=dict(T=20))['action'], 'exclude_bound')

    def test_legacy_missing_velocity_metadata_preserves_candidate(self):
        state=dict(motion=dict(schedule='JAA',phase='air',jumps=1,runs=0))
        record=dict(id='a',clock_results={'clock':dict(status='feasible',objective=1.,state=state)},
                    spatial_results={'goal':dict(status='feasible',ticks=20)})
        peer=dict(record,id='b')
        verdict=compare_candidates(record,peer,context=dict(purpose='common_clock'))
        self.assertEqual(verdict['action'],'keep')
        self.assertIn('missing_state_metadata',verdict['protectionReasons'])


if __name__ == '__main__':
    unittest.main()
