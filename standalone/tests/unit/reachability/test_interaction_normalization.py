import copy
import itertools
import json
import unittest

from parkour_solver.constraints.variants import ConstraintVariants
from parkour_solver.reachability.service import query_interaction, normalize_alternatives
from parkour_solver.reachability.intervals import reachable_envelopes


def context():
    return dict(envelopes=reachable_envelopes(dict(ticks=3, position=[0., 0.], velocity=[0., 0.],
        magnitude=[1.]*3, friction=[.9]*3, walls=[]), [1.]*4))


def source(state=None):
    return ConstraintVariants(dict(passageCaps=dict(horizontal=2, vertical=2, total=3), supportClearance=1e-6),
        dict(position=[1.]*4), dict(events=[dict(jump=0, runTicks=0)]),
        dict(solids=[[1., 0., -.2, 2., 3., .2]]), context()['envelopes'],
        dict(through=3, solidIndices=[0]), state=state)


class InteractionNormalizationTests(unittest.TestCase):
    def test_far_avoid_is_omitted_but_absent_required_contact_rejects_owner(self):
        request = dict(box=[100., 0., 0., 101., 3., 1.], role='unwanted_contact',
            windows=[dict(tick=t, phase=p) for t in range(3) for p in ('Y','X','Z')])
        cache = {}
        avoid = query_interaction(request, context(), session=cache)
        self.assertEqual(avoid['status'], 'absent')
        required = query_interaction(dict(request, role='required_contact'), context(), session=cache)
        items, audit = normalize_alternatives([dict(requestId='avoid'), dict(requestId='land')],
                                              dict(avoid=avoid, land=required))
        self.assertEqual(items, [])
        self.assertEqual([a['action'] for a in audit], ['omit', 'reject_owner'])
        self.assertNotEqual(avoid['id'], required['id'])
        near = query_interaction(dict(request, box=[1.,0.,-.2,2.,3.,.2]), context(), session=cache)
        self.assertEqual(near['status'], 'not_ruled_out')
        self.assertEqual(query_interaction(request, {})['status'], 'unknown')

    def test_top_contact_boundary_does_not_share_interior_absence_proof(self):
        request = dict(box=[-.5,0.,-.5,.5,1.,.5], role='unwanted_contact', windows=[dict(tick=0, phase='Y')])
        self.assertEqual(query_interaction(request, context())['status'], 'absent')
        contact = query_interaction(dict(request, role='required_contact'), context())
        self.assertEqual(contact['status'], 'not_ruled_out')

    def test_context_and_probe_uncertainty_invalidate_proofs(self):
        request = dict(box=[1.,50.,0.,2.,51.,1.], role='approach_prerequisite', windows=[dict(tick=0,phase='X')])
        a = query_interaction(request, context())
        ctx = context(); ctx['envelopes']['phases'][0]['X']['X'] = [-1000., 1000.]
        ctx['envelopes']['phases'][0]['X']['Y'] = [-float('inf'),float('inf')]
        b = query_interaction(request, ctx)
        self.assertNotEqual(a['context'], b['context'])
        self.assertEqual(b['status'], 'not_ruled_out')

    def test_interrupted_phase_mask_and_table_preparation_resumes_exactly(self):
        expected = list(itertools.islice(source(), 12))
        self.assertTrue(expected)
        for allowance in (2, 12, 40, 80):
            with self.subTest(allowance=allowance):
                current = source()
                counter = 0
                def check():
                    nonlocal counter
                    counter += 1
                    if counter > allowance:
                        raise TimeoutError('allocation')
                current.check = check
                actual = []
                try:
                    for _ in range(12):
                        actual.append(next(current))
                except TimeoutError:
                    pass
                saved = json.loads(json.dumps(current.state, allow_nan=False))
                restored = source(saved)
                actual += list(itertools.islice(restored, 12-len(actual)))
                self.assertEqual(actual, expected)

    def test_old_or_changed_constraint_state_is_explicitly_rejected(self):
        saved = copy.deepcopy(source().state)
        saved['version'] = 1
        with self.assertRaisesRegex(ValueError, 'Incompatible constraint'):
            source(saved)

    def test_absent_prerequisite_rejects_the_whole_owned_alternative(self):
        proofs=dict(a=dict(id='a',status='absent',role='approach_prerequisite'),
                    b=dict(id='b',status='unknown',role='required_contact'),
                    c=dict(id='c',status='unknown',role='required_contact'))
        kept,audit=normalize_alternatives([dict(requestId='a',owner='case1'),dict(requestId='b',owner='case1'),
                                           dict(requestId='c',owner='case2')],proofs)
        self.assertEqual(kept,[dict(requestId='c',owner='case2')])
        self.assertEqual([v['action'] for v in audit],['reject_owner','reject_owner','retain'])


if __name__ == '__main__':
    unittest.main()
