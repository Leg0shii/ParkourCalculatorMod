import random
import copy
import math
import unittest
from unittest.mock import patch

from parkour_solver.reachability.intervals import interaction_reachability, reachable_envelopes, travel_radii
from parkour_solver.reachability.screen import contradiction
from parkour_solver.numerics import disk, prepare, validate


class ScreenTests(unittest.TestCase):
    def test_extra_prefix_rows_use_existing_model_without_mutating_it(self):
        p=dict(ticks=1,position=[0.,0.],velocity=[0.,0.],magnitude=[1.],friction=[.9],walls=[])
        before=copy.deepcopy(p)
        rows=[dict(mode=axis,offset=1,cmp='GE',rhs=.8) for axis in ('X','Z')]
        self.assertIsNone(contradiction(p,rows[:1]))
        self.assertEqual(contradiction(p,rows)['reason'],'norm_reach_contradiction')
        self.assertEqual(p,before)
        self.assertNotIn('t1',rows[0])

    def test_travel_pairs_after_the_last_constrained_state_never_fire(self):
        rng = random.Random(1118)
        fired = 0
        for _ in range(400):
            n = rng.randint(2, 40)
            magnitude, friction = [rng.uniform(.05, .7) for _ in range(n)], [rng.uniform(.5, .95) for _ in range(n)]
            reach = travel_radii(0., magnitude, friction)
            walls = []
            for _ in range(rng.randint(0, 3)):
                t = rng.randint(1, n)
                a = rng.uniform(.5, .95)*reach[t]
                walls += [dict(mode='X', t1=t, cmp='GE', rhs=a), dict(mode='Z', t1=t, cmp='GE', rhs=a)][:rng.randint(1, 2)]
            p = dict(ticks=n, position=[0., 0.], velocity=[0., 0.], magnitude=magnitude, friction=friction,
                     walls=walls, threshold=0.)
            envelope = reachable_envelopes(p)
            if envelope['contradiction'] is not None and envelope['contradiction']['reason'] != 'norm_reach_contradiction':
                continue
            states, radii, expected = envelope['states'], envelope['radii'], None
            for j in range(1, len(states)):
                for i in range(j):
                    gap = math.hypot(*(max(0., states[j][a][0]-states[i][a][1], states[i][a][0]-states[j][a][1])
                                       for a in ('X', 'Z')))
                    if gap > radii[j]-radii[i]+1e-7:
                        expected = dict(state=j, previousState=i, reason='norm_reach_contradiction',
                                        gap=gap, travelUpperBound=radii[j]-radii[i])
                        break
                if expected is not None:
                    break
            self.assertEqual(envelope['contradiction'], expected, p)
            fired += expected is not None
        self.assertGreater(fired, 20)

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

class DiskScreenTests(unittest.TestCase):
    def model(self, rhs):
        return dict(ticks=2,position=[0.,0.],velocity=[0.,0.],magnitude=[1.,0.],friction=[1.,1.],threshold=0.,
                    walls=[dict(mode='X',t1=1,cmp='GE',rhs=.9),dict(mode='X',t1=2,cmp='LE',rhs=rhs)])

    def test_coupled_rows_the_intervals_miss_are_excluded_by_the_relaxation(self):
        envelopes = reachable_envelopes(self.model(1.75))
        self.assertFalse(interaction_reachability(envelopes,{})['ruledOut'])
        proof = interaction_reachability(envelopes,{},refine=True)
        self.assertEqual((proof['reason'],proof['relaxation']),('disk_control_relaxation_infeasible','acceleration_disk'))
        self.assertIn(proof['status'],disk.INFEASIBLE)
        self.assertFalse(interaction_reachability(reachable_envelopes(self.model(1.85)),{},refine=True)['ruledOut'])

    def test_undecided_results_are_solved_again_and_decided_ones_are_cached(self):
        undecided = dict(status='MaxIterations',outcome='undecided',controls=None,objective=None,iterations=200)
        with patch.object(disk,'_solve',side_effect=lambda *a:dict(undecided)) as solved:
            for _ in range(2):
                checked = interaction_reachability(reachable_envelopes(self.model(1.25)),{},refine=True)
                self.assertEqual((checked['ruledOut'],checked['reason']),(False,'relaxation_undecided'))
        self.assertEqual(solved.call_count,2)
        self.assertTrue(interaction_reachability(reachable_envelopes(self.model(1.7)),{},refine=True)['ruledOut'])
        with patch.object(disk,'_solve',side_effect=AssertionError('cached')):
            self.assertEqual(disk.screen(self.model(1.7))['outcome'],'infeasible')

    def test_screen_objective_is_the_model_objective_unless_constant_zero_else_the_supplied_one(self):
        supplied = dict(axis='Z',sense='MIN')
        model = dict(self.model(1.6),objective=dict(tick=1,axis='X',sense='MAX'),objectiveMode='constant_zero')
        self.assertEqual(disk.spec(model,(),supplied,strict=False)['objective']['axis'],'Z')
        self.assertEqual(disk.spec(model,(),supplied,strict=False)['objective']['tick'],2)
        self.assertNotIn('objective',disk.spec(model,strict=False))
        model.pop('objectiveMode')
        self.assertEqual(disk.spec(model,(),supplied,strict=False)['objective'],
                         dict(tick=1,axis='X',sense='MAX',customYaw=None,type=None))

    def test_screens_drop_rows_the_relaxation_cannot_express(self):
        model = self.model(1.75)
        model['walls'].append(dict(mode='F',t1=0,cmp='EQ',rhs=90.))
        self.assertIsNone(disk.spec(model,strict=True))
        self.assertEqual(disk.screen(model)['outcome'],'infeasible')
        self.assertIsNone(disk.screen(dict(model,walls=[],contacts=[])))
        self.assertIsNone(disk.screen(dict(model,threshold=.005)))


if __name__ == '__main__':
    unittest.main()
