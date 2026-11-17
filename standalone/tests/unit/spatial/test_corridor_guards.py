import collections
import itertools
import json
import unittest

from parkour_solver.spatial.compat.corridor_guards import movement_regions
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.spatial.compat.corridors import Corridor, audit_candidate, projected_segment_covered


def adapter(boxes):
    result = CorridorAdapter.__new__(CorridorAdapter)
    result.corridor = Corridor(0, [], [], [], (), boxes, 0.)
    result.gates, result.counts = [], collections.Counter()
    return result


def satisfies(rows, positions):
    return all((positions[r['offset']][0 if r['mode']=='X' else 1] >= r['rhs']-1e-8
                if r['cmp']=='GE' else positions[r['offset']][0 if r['mode']=='X' else 1] <= r['rhs']+1e-8)
               for r in rows)


class CorridorGuardTests(unittest.TestCase):
    def test_projection_failure_logs_exact_phase_and_detached_replay_data(self):
        a = adapter([(0., 0., 0., 1., 0., 1.)])
        positions = [[.5, .5], [1.5, 1.5]]
        wall = dict(mode='X', t1=0, cmp='GE', rhs=.25)
        plan = dict(ticks=1, events=[dict(jump=0)], walls=[wall])
        result = a.candidate(plan, dict(positions=positions, yaw=[12.]), dict(y=[1., 1.25]), partial=True)
        logged = json.loads(json.dumps(result['corridorAudit'], allow_nan=False))
        self.assertEqual(logged['failure'], dict(reason='corridor_projection', segment=1, input=0, phase='X',
            references=[1, 0], start=[.5, 1.25, .5], end=[1.5, 1.25, .5], tolerance=1e-7))
        self.assertEqual(logged['plan']['walls'], [wall])
        self.assertEqual(logged['repairAlternatives'], result['alternatives'])
        self.assertTrue(logged['scope']['partialSignature'])
        self.assertEqual(logged['scope']['projection'], 'whole_trajectory')
        self.assertEqual(logged['scope']['modeledInteractionScope'], 'unspecified')
        positions[1][0] = 99.
        plan['walls'].clear()
        self.assertEqual(result['corridorAudit']['candidate']['positions'][1][0], 1.5)
        self.assertEqual(result['corridorAudit']['plan']['walls'], [wall])
        self.assertEqual(a.counts['corridorProjectionRejections'], 1)

    def test_signature_failure_logs_expected_and_actual_crossing(self):
        a = adapter([(-3., 0., -3., 3., 0., 3.)])
        a.gates = [dict(id=0, axis=0, lateral=2, plane=0., lo=-1., hi=1.)]
        a.corridor.signature = ((0, 1, -1),)
        result = a.candidate(dict(events=[dict(jump=0)]),
            dict(positions=[[-1., 2.], [1., 2.]]), dict(y=[1., 1.]))
        logged = result['corridorAudit']
        self.assertEqual(logged['failure']['reason'], 'corridor_signature')
        self.assertEqual(logged['failure']['expected'], (0, 1, -1))
        self.assertEqual(logged['failure']['actual'], (0, 1, 1))
        self.assertEqual(logged['gates'], a.gates)
        self.assertEqual(a.counts['corridorSignatureRejections'], 1)
        self.assertTrue(result['alternatives'])

    def test_crossing_adjacent_rectangles_is_preserved_in_both_axes_and_directions(self):
        base = [(0.,0.,0.,1.,0.,1.), (1.,0.,0.,2.,0.,2.)]
        for swap, reverse in itertools.product((False, True), repeat=2):
            def point(x, z):
                return [z, x] if swap else [x, z]
            boxes = [(b[2],0.,b[0],b[5],0.,b[3]) for b in base] if swap else base
            valid = [point(.5,.5),point(1.5,.5)]
            invalid = [point(.5,.5),point(2.5,.5)]
            if reverse:
                valid.reverse()
                invalid.reverse()
            a = adapter(boxes)
            result = a.candidate(dict(events=[dict(jump=0)]), dict(positions=invalid), dict(y=[0.,0.]))
            self.assertFalse(result['accepted'])
            self.assertTrue(audit_candidate(a.corridor,dict(positions=valid,y=[0.,0.]),[])['accepted'])
            self.assertTrue(any(satisfies(rows, valid) for rows in result['alternatives']), (swap,reverse,result))

    def test_regions_cover_exactly_the_axis_movements_inside_union(self):
        boxes = [(0.,0.,0.,1.,0.,2.), (1.,0.,0.,2.,0.,1.), (2.,0.,0.,3.,0.,2.)]
        coordinates = [-.25,0.,.5,1.,1.5,2.,2.5,3.,3.25]
        for axis in (0, 2):
            regions = movement_regions(boxes,axis)
            lateral = 2-axis
            for start,end,cross in itertools.product(coordinates,repeat=3):
                a,b = [0.,0.,0.],[0.,0.,0.]
                a[axis],b[axis] = start,end
                a[lateral]=b[lateral]=cross
                covered = projected_segment_covered(boxes,a,b,tolerance=0.)
                encoded = any(all(box[i] <= p[i] <= box[i+3] for i in (0,2) for p in (a,b)) for box in regions)
                self.assertEqual(covered,encoded,(axis,a,b,regions))

    def test_real_gap_is_not_bridged_by_a_region(self):
        boxes = [(0.,0.,0.,1.,0.,1.), (2.,0.,0.,3.,0.,1.)]
        a=adapter(boxes)
        result=a.candidate(dict(events=[dict(jump=0)]),dict(positions=[[.5,.5],[2.5,.5]]),dict(y=[0.,0.]))
        self.assertFalse(any(satisfies(rows,[[.5,.5],[2.5,.5]]) for rows in result['alternatives']))


if __name__=='__main__':
    unittest.main()
