"""Offline geometry reporting preserves unions, overheads and compound steps."""
import copy
import unittest

from parkour_solver.spatial.cells import contains
from tools.geometry_bounds import markdown_report, player_bounds_report


class GeometryBoundsTests(unittest.TestCase):
    def report(self, boxes):
        scene = dict(sceneModel='collision_boxes', geometryBounds=[-5, -5, -5, 10, 10, 10],
                     blocks=[dict(id=name, boxes=value) for name, value in boxes])
        original = copy.deepcopy(scene)
        report = player_bounds_report(dict(request=scene))
        self.assertEqual(scene, original)
        return report

    def test_corner_has_both_arms_without_filling_obstructed_corner(self):
        report = self.report([('floor', [[0, 0, 0, 3, 1, 3]]),
                              ('wall', [[0, 1, 0, 1, 4, 1]])])
        floor = report['blocks'][0]
        inside = lambda point: any(contains(b, point) for b in floor['exposedLandingClosure'])
        self.assertTrue(inside([2, 1, .5]))
        self.assertTrue(inside([.5, 1, 2]))
        self.assertFalse(inside([.5, 1, .5]))
        self.assertEqual(floor['blockers'][0]['relation'], 'side')

    def test_low_overhang_blocks_floor_but_sufficient_headroom_does_not(self):
        report = self.report([('floor', [[0, 0, 0, 3, 1, 1]]),
                              ('low', [[0, 2.5, 0, 1, 3, 1]]),
                              ('high', [[2, 3, 0, 3, 4, 1]])])
        floor = report['blocks'][0]
        self.assertEqual([b['id'] for b in floor['blockers']], ['low'])
        self.assertEqual(floor['blockers'][0]['relation'], 'overhead')
        self.assertFalse(any(contains(b, [.5, 1, .5]) for b in floor['exposedLandingClosure']))
        self.assertTrue(any(contains(b, [2.5, 1, .5]) for b in floor['exposedLandingClosure']))

    def test_compound_stair_reports_both_heights_and_upper_riser_clip(self):
        report = self.report([('stair', [[0, 0, 0, 1, .5, .5], [0, 0, .5, 1, 1, 1]])])
        lower, upper = report['blocks']
        self.assertEqual([lower['id'], upper['id']], ['stair#0', 'stair#1'])
        self.assertEqual(lower['owner'], upper['owner'])
        self.assertFalse(any(contains(b, [.5, .5, .4]) for b in lower['exposedLandingClosure']))
        self.assertTrue(any(contains(b, [.5, .5, .1]) for b in lower['exposedLandingClosure']))
        self.assertEqual({s['height'] for s in report['supportComponents']}, {.5, 1})
        self.assertIn('stair#0', markdown_report(report))

    def test_buried_block_has_no_support_from_merely_tangent_strata(self):
        report = self.report([('wall', [[0, 0, 0, 4, 5, 1]]),
                              ('buried', [[1, 1, 0, 3, 2, 1]])])
        self.assertEqual(report['blocks'][1]['exposedLandingClosure'], ())
        self.assertFalse(any(s['owner'] == 'buried' for s in report['supportComponents']))


if __name__ == '__main__':
    unittest.main()
