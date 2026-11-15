import copy
import math
import random
import unittest

from parkour_solver.numerics.constraints import reduce_walls
from parkour_solver.numerics.model import prepare, validate, fingerprint
from tests.unit.numerics.test_model import problem


class NumericalConstraintTests(unittest.TestCase):
    def test_strongest_bounds_preserve_all_source_indices_without_mutation(self):
        walls = [dict(mode='X',t1=2,cmp='GE',rhs=v,name=str(i),owner=i) for i,v in enumerate((1.,3.,2.,3.))]
        walls += [dict(mode='X',t1=2,cmp='LE',rhs=v) for v in (9.,7.,8.)]
        original = copy.deepcopy(walls)
        reduced = reduce_walls(walls)
        self.assertEqual([r['rhs'] for r in reduced['walls']],[3.,7.])
        self.assertEqual(reduced['audit']['representatives'],[1,5])
        self.assertEqual(reduced['audit']['sourceIndices'],[[0,1,2,3],[4,5,6]])
        self.assertEqual(walls,original)

    def test_only_exact_facing_relative_and_cross_axis_duplicates_merge(self):
        walls = [dict(mode='F',t1=1,cmp='GE',rhs=v) for v in (170.,-170.)]
        walls += [dict(mode='X',t1=2,t2=0,op='MINUS',cmp='LE',rhs=v) for v in (1.,2.)]
        walls += [dict(mode='DXZ',t1=2,t2=0,cmp='GE',rhs=v) for v in (1.,2.)]
        repeated = [dict(row,name='different provenance') for row in walls]
        reduced = reduce_walls(walls+repeated)
        self.assertEqual(reduced['walls'],walls)
        self.assertEqual(reduced['audit']['sourceIndices'],[[i,i+6] for i in range(6)])

    def test_contradictions_equalities_distinct_ticks_and_axes_survive(self):
        walls = [dict(mode='X',t1=2,cmp=cmp,rhs=v) for cmp,v in [('GE',3.),('LE',2.),('EQ',1.),('EQ',2.)]]
        walls += [dict(mode='Z',t1=2,cmp='GE',rhs=3.),dict(mode='X',t1=1,cmp='GE',rhs=3.)]
        self.assertEqual(reduce_walls(walls)['walls'],walls)

    def test_operator_defaults_and_float_precision(self):
        row = dict(mode='Z',t1=2,t2=0,cmp='LE',rhs=1.)
        walls = [row,dict(row,op='PLUS'),dict(row,op='MINUS'),dict(row,rhs=math.nextafter(1.,2.))]
        self.assertEqual(len(reduce_walls(walls)['walls']),3)

    def test_reduction_preserves_acceptance_on_translated_random_trajectories(self):
        rng = random.Random(12661)
        for _ in range(30):
            request = problem(5)
            request.update(position=[rng.uniform(-1000,1000),rng.uniform(-1000,1000)],
                magnitude=[.1]*5,velocity=[.1,-.1],base=[0.]*5)
            known = validate(prepare(request),[-90.]+[rng.uniform(-180,180) for _ in range(4)])
            walls = []
            for axis,mode in enumerate(('X','Z')):
                for t in (0,2,5):
                    value = known['positions'][t][axis]
                    walls += [dict(mode=mode,t1=t,cmp=cmp,rhs=value+offset) for cmp,offset in
                        [('GE',-.02),('GE',-.1),('LE',.02),('LE',.1),('LE',.02)]]
            walls += [dict(mode='F',t1=0,cmp='GE',rhs=170.),dict(mode='F',t1=0,cmp='GE',rhs=-170.),
                dict(mode='X',t1=4,t2=1,op='MINUS',cmp='GE',
                    rhs=known['positions'][4][0]-known['positions'][1][0]-.02),
                dict(mode='DZX',t1=5,t2=0,cmp='LE',
                    rhs=abs(known['positions'][5][1]-known['positions'][0][1])-
                        abs(known['positions'][5][0]-known['positions'][0][0])+.02)]
            request['walls'] = walls
            original = prepare(request)
            digest = fingerprint(original)
            reduced = prepare(dict(original,walls=reduce_walls(original['walls'])['walls']))
            self.assertTrue(validate(original,known['yaw'])['feasible'])
            for yaw in [known['yaw']]+[[rng.uniform(-180,180) for _ in range(5)] for _ in range(10)]:
                self.assertEqual(validate(original,yaw)['feasible'],validate(reduced,yaw)['feasible'])
            self.assertEqual(fingerprint(original),digest)


if __name__ == '__main__':
    unittest.main()
