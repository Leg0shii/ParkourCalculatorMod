import random
import copy
import json
import math
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from parkour_solver.reachability.intervals import interaction_reachability, reachable_envelopes, travel_radii
from parkour_solver.reachability.screen import contradiction
from parkour_solver.numerics import prepare, validate


class ScreenTests(unittest.TestCase):
    def test_extra_prefix_rows_use_existing_model_without_mutating_it(self):
        p=dict(ticks=1,position=[0.,0.],velocity=[0.,0.],magnitude=[1.],friction=[.9],walls=[])
        before=copy.deepcopy(p)
        rows=[dict(mode=axis,offset=1,cmp='GE',rhs=.8) for axis in ('X','Z')]
        self.assertIsNone(contradiction(p,rows[:1]))
        self.assertEqual(contradiction(p,rows)['reason'],'norm_reach_contradiction')
        self.assertEqual(p,before)
        self.assertNotIn('t1',rows[0])

    def test_unreachable_wall_is_rejected(self):
        p = {'ticks': 1, 'position': [0, 0], 'velocity': [0, 0], 'magnitude': [.1],
             'friction': [.91], 'walls': [{'mode': 'X', 't1': 1, 'cmp': 'GE', 'rhs': .2}]}
        self.assertEqual(contradiction(p)['state'], 1)

    def test_per_axis_zeroing_and_turns_do_not_reject_a_known_witness(self):
        rng = random.Random(163)
        for _ in range(150):
            n = rng.randint(2, 25)
            p = prepare({'version': 1, 'ticks': n, 'position': [rng.uniform(-100, 100) for _ in range(2)],
                         'velocity': [rng.uniform(-.2, .2) for _ in range(2)],
                         'magnitude': [rng.uniform(.001, .33) for _ in range(n)],
                         'friction': [rng.uniform(.5, .95) for _ in range(n)],
                         'base': [rng.uniform(-3, 3) for _ in range(n)],
                         'yaw': [rng.uniform(-180, 180) for _ in range(n)], 'threshold': .005,
                         'walls': [], 'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}})
            witness = validate(p, p['yaw'])
            for t, point in enumerate(witness['positions']):
                for axis, coordinate in zip(('X', 'Z'), point):
                    p['walls'].extend([{'mode': axis, 't1': t, 'cmp': 'GE', 'rhs': coordinate - 1e-5},
                                       {'mode': axis, 't1': t, 'cmp': 'LE', 'rhs': coordinate + 1e-5}])
            self.assertIsNone(contradiction(p))

    def test_ladder_cap_cannot_claim_reaching_a_further_contact(self):
        p = {'ticks': 1, 'position': [0, 0], 'velocity': [.8, 0], 'magnitude': [.03],
             'friction': [.91], 'walls': [], 'ladderTicks': [True, True],
             'contacts': [{'state': 1, 'axis': 0, 'plane': .2, 'direction': 1}]}
        self.assertEqual(contradiction(p)['reason'], 'contact_reach_contradiction')
        p['ladderTicks'][0] = False
        self.assertIsNone(contradiction(p))

    def test_contact_cap_screen_keeps_random_valid_witnesses(self):
        rng = random.Random(75124)
        for _ in range(300):
            n = rng.randint(2, 20)
            p = prepare({'version': 1, 'ticks': n, 'position': [rng.uniform(-10, 10) for _ in range(2)],
                         'velocity': [rng.uniform(-.3, .3) for _ in range(2)], 'threshold': .005,
                         'magnitude': [rng.uniform(.005, .33) for _ in range(n)],
                         'friction': [rng.uniform(.5, .95) for _ in range(n)], 'base': [0] * n,
                         'yaw': [rng.uniform(-180, 180) for _ in range(n)],
                         'ladderTicks': [bool(rng.randrange(2)) for _ in range(n + 1)],
                         'walls': [], 'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}})
            for t in range(n):
                candidate = validate(p, p['yaw'])
                for axis in range(2):
                    delta = candidate['positions'][t + 1][axis] - candidate['positions'][t][axis]
                    if abs(delta) > 1e-5 and rng.random() < .3:
                        p['contacts'].append({'state': t + 1, 'axis': axis,
                            'plane': candidate['positions'][t][axis] + delta / 2, 'direction': 1 if delta > 0 else -1})
            witness = validate(p, p['yaw'])
            self.assertTrue(witness['feasible'])
            for t, point in enumerate(witness['positions']):
                for axis, value in zip(('X', 'Z'), point):
                    p['walls'].extend([{'mode': axis, 't1': t, 'cmp': 'GE', 'rhs': value - 1e-5},
                                       {'mode': axis, 't1': t, 'cmp': 'LE', 'rhs': value + 1e-5}])
            self.assertIsNone(contradiction(p))



class TravelTests(unittest.TestCase):
    def test_travel_radii_is_the_inlined_recurrence_bit_for_bit(self):
        rng = random.Random(4417)
        for _ in range(200):
            n = rng.randint(0, 40)
            speed = rng.uniform(0., .6)
            magnitudes = [rng.uniform(0., .4) for _ in range(n)]
            frictions = [rng.uniform(.5, .99) for _ in range(n)]
            for margin in (0., 1e-8):
                expected, total, carry = [0.], 0., speed
                for m, f in zip(magnitudes, frictions):
                    move = carry + m + margin if margin else carry + m
                    total += move
                    expected.append(total)
                    carry = move * f
                self.assertEqual(travel_radii(speed, magnitudes, frictions, margin=margin), expected)

class LinearCheckTests(unittest.TestCase):
    def model(self, rhs):
        return dict(ticks=2,position=[0.,0.],velocity=[0.,0.],magnitude=[1.,0.],friction=[1.,1.],threshold=0.,
                    walls=[dict(mode='X',t1=1,cmp='GE',rhs=.9),dict(mode='X',t1=2,cmp='LE',rhs=rhs)])

    def test_scipy_reports_a_model_error_as_status_2_so_only_native_infeasible_is_a_proof(self):
        from scipy.optimize import linprog
        error = linprog([0.],A_ub=[[1e20]],b_ub=[1.],bounds=[(None,None)],method='highs')
        self.assertEqual(error.status,2)
        self.assertNotIn('(HiGHS Status 8:',error.message)
        from parkour_solver.reachability import linear
        with patch('scipy.optimize.linprog',return_value=error):
            self.assertEqual(linear._check(json.dumps(linear_spec(self.model(1.5))))['outcome'],'undecided')
        self.assertEqual(linear._check(json.dumps(linear_spec(self.model(1.5))))['outcome'],'infeasible')

    def test_unknown_retries_once_with_interior_point_inside_the_budget(self):
        from scipy.optimize import linprog
        from parkour_solver.reachability import linear
        calls = []
        def solve(*args, **kwargs):
            calls.append(kwargs)
            if len(calls) == 1:
                return SimpleNamespace(status=4,message='(HiGHS Status 15: model_status is Unknown)')
            return linprog(*args, **kwargs)
        with patch('scipy.optimize.linprog',side_effect=solve):
            result = linear._check(json.dumps(linear_spec(self.model(1.5))))
        self.assertEqual([c['method'] for c in calls],['highs','highs-ipm'])
        self.assertLess(calls[1]['options']['time_limit'],1.)
        self.assertGreater(calls[1]['options']['time_limit'],0.)
        self.assertEqual((result['outcome'],result['proof']['solver'],result['retried']),('infeasible','ipm',True))

    def test_undecided_results_are_solved_again_and_decided_ones_are_cached(self):
        from parkour_solver.reachability import linear
        undecided = dict(outcome='undecided',status=1,message='Time limit reached.')
        with patch('parkour_solver.reachability.linear._check',side_effect=lambda e:dict(undecided)) as check:
            for _ in range(2):
                self.assertEqual(linear.linear_exclusion(self.model(1.25))['outcome'],'undecided')
        self.assertEqual(check.call_count,2)
        self.assertEqual(interaction_reachability(reachable_envelopes(self.model(1.75)),{},refine=True)['reason'],
                         'linear_control_relaxation_infeasible')
        with patch('parkour_solver.reachability.linear._check',side_effect=AssertionError('cached')):
            self.assertEqual(linear.linear_exclusion(self.model(1.75))['outcome'],'infeasible')

    def test_lp_cost_is_the_configured_ipopt_objective(self):
        from parkour_solver.reachability.linear import _cost
        n, width = 3, 11
        pos = lambda axis, t: axis*width+t
        spec = lambda **objective: dict(ticks=n,objective=dict(dict(tick=n,axis='X',sense='MAX',customYaw=None,type=None),**objective))
        def nonzero(cost):
            return {i:round(v,12) for i,v in enumerate(cost) if v}
        self.assertEqual(nonzero(_cost(dict(ticks=n),width,pos)),{})
        self.assertEqual(nonzero(_cost(spec(),width,pos)),{pos(0,n):-1.})
        self.assertEqual(nonzero(_cost(spec(axis='Z',sense='MIN'),width,pos)),{pos(1,n):1.})
        yaw = 30.
        dx, dz = -math.sin(math.radians(yaw)), math.cos(math.radians(yaw))
        self.assertEqual(nonzero(_cost(spec(customYaw=yaw,type='POSITION'),width,pos)),
                         {pos(0,n):round(-dx,12),pos(1,n):round(-dz,12)})
        self.assertEqual(nonzero(_cost(spec(customYaw=yaw,type='MOTION'),width,pos)),
                         {pos(0,n):round(-dx,12),pos(1,n):round(-dz,12),pos(0,n-1):round(dx,12),pos(1,n-1):round(dz,12)})

    def test_constant_zero_objective_mode_keeps_zero_cost(self):
        from parkour_solver.reachability import linear
        model = dict(self.model(1.6),objective=dict(tick=2,axis='X',sense='MAX'),objectiveMode='constant_zero')
        with patch('parkour_solver.reachability.linear._check',side_effect=lambda e:dict(outcome='feasible',spec=json.loads(e))):
            self.assertNotIn('objective',linear.linear_exclusion(model)['spec'])
        model.pop('objectiveMode')
        with patch('parkour_solver.reachability.linear._check',side_effect=lambda e:dict(outcome='feasible',spec=json.loads(e))):
            self.assertEqual(linear.linear_exclusion(model)['spec']['objective']['axis'],'X')


def linear_spec(model):
    rows = [(r['mode'],r['t1'],None,'PLUS',r['cmp'],r['rhs']) for r in model['walls']]
    spec = {k:model[k] for k in ('ticks','position','velocity','magnitude','friction')}
    spec.update(rows=sorted(set(rows),key=str),contacts=[],initialIntervals=None)
    return spec


if __name__ == '__main__':
    unittest.main()
