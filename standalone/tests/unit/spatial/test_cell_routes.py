import unittest

from parkour_solver.search.support_profiles import prepare_scene
from parkour_solver.spatial.cells import decompose, segment_covered
from parkour_solver.spatial.compat.corridors import audit_candidate, corridor_for_points, crossings, enumerate_routes, horizontal_trace, landmarks


def side_scene():
    return prepare_scene(dict(version=1, sceneModel='collision_boxes', geometryBounds=[-4, -4, -5, 10, 9, 7],
        initial=dict(position=[.5, 1, .5], velocity=[0, -.0784000015258789, 0], yaw=-90),
        startBlock='start', goalBlock='goal', blocks=[
            dict(id='start', boxes=[[0, 0, 0, 1, 1, 1]]),
            dict(id='wall', boxes=[[2, 0, 0, 3, 5, 1]]),
            dict(id='goal', boxes=[[5, 0, 0, 6, 1, 1]])],
        maxWallHits=0, maxContactCombinations=0,
        movementSearch=dict(types=['normal'], maxInputs=30, maxPrefixes=24, maxChoicesPerType=12)))


class CellRouteTests(unittest.TestCase):
    def test_support_corner_keeps_both_free_regions_without_filling_the_obstacle(self):
        scene = prepare_scene(dict(version=1, sceneModel='collision_boxes',
            geometryBounds=[-3,-3,-2,7,5,4],
            initial=dict(position=[2,1,.5],velocity=[0,-.0784000015258789,0],yaw=0),
            startBlock='support',goalBlock='neighbor',maxWallHits=0,
            blocks=[dict(id='support',boxes=[[0,0,0,5,1,2]]),
                    dict(id='neighbor',boxes=[[-1,0,1,0,2,2]])]))
        d = decompose(scene)
        corner,turn,other = [-.1,1,.2],[.5,1,.2],[.5,1,1.5]
        self.assertTrue(d.locate(corner))
        self.assertTrue(d.locate(other))
        self.assertFalse(d.locate([-.1,1,.9]))
        self.assertTrue(segment_covered(d,corner,turn))
        self.assertTrue(segment_covered(d,turn,other))
        self.assertFalse(segment_covered(d,corner,other))

    def test_both_obstacle_sides_and_overflight_are_distinct_proposals(self):
        scene = side_scene()
        d = decompose(scene)
        routes, report = enumerate_routes(scene, d, max_routes=10, max_expansions=3000, max_visits=1)
        sides = {r.signature[0][2] for r in routes if len(r.signature) == 1}
        self.assertEqual(sides, {-1, 0, 1}, report)
        for r in routes:
            for a, b in zip(r.points, r.points[1:]):
                self.assertTrue(segment_covered(d, a, b))
        self.assertFalse(report['coverageProven'])

    def test_prescribed_spatial_points_hide_timing_and_preserve_crossing_side(self):
        scene = side_scene()
        d, gates = decompose(scene), landmarks(scene)
        left = corridor_for_points(d, [[.5, 1, .5], [1, 1, -1], [4, 1, -1], [5.5, 1, .5]], gates)
        candidate = dict(positions=[[.5, .5], [1, -1], [4, -1], [5.5, .5]], y=[1] * 4)
        self.assertTrue(audit_candidate(left, candidate, gates)['accepted'])
        wrong = dict(positions=[[.5, .5], [1, 2], [4, 2], [5.5, .5]], y=[1] * 4)
        self.assertFalse(audit_candidate(left, wrong, gates)['accepted'])
        self.assertEqual(left.signature, ((0, 1, -1),))

    def test_phase_trace_sees_multiple_crossings_in_one_tick(self):
        scene = side_scene()
        gates = landmarks(scene)
        candidate = dict(positions=[[.5, -1], [5.5, -1]], y=[1, 1])
        points, references = horizontal_trace(candidate)
        self.assertEqual(len(points), 4)
        self.assertEqual(crossings(points, gates)[0]['token'], (0, 1, -1))
        self.assertEqual(references[1], (1, 0))
        two = gates + [dict(gates[0], id=1, plane=4)]
        self.assertEqual(len(crossings(points, two)), 2)

    def test_returns_are_kept_in_signature_and_explicitly_limited(self):
        scene = side_scene()
        gates = landmarks(scene)
        points = [[0, 1, -1], [4, 1, -1], [1, 1, -1], [5, 1, -1]]
        self.assertEqual([e['token'][1] for e in crossings(points, gates)], [1, -1, 1])
        routes, report = enumerate_routes(scene, decompose(scene), max_routes=1)
        self.assertEqual(len(routes), 1)
        self.assertEqual(report['termination'], 'route_limit')
        self.assertGreater(report['remaining'], 0)
        self.assertEqual(report['maxVisits'], 2)

    def test_goal_is_a_landing_region_even_when_its_center_is_blocked(self):
        scene = side_scene()
        scene['blocks'] = [scene['blocks'][0], dict(id='goal',sourceBlock='goal',solidIndex=1,top=1,bounds=[4,8,0,1]),
                           dict(id='wall',sourceBlock='wall',solidIndex=2,top=5,bounds=[6,7,-1,2])]
        scene['collisionScene']['solids'] = [[0,0,0,1,1,1],[4,0,0,8,1,1],[6,0,-1,7,5,2]]
        d = decompose(scene)
        self.assertEqual(d.locate([6,1,.5]), [])
        routes,report = enumerate_routes(scene,d,max_routes=1,max_expansions=200)
        self.assertTrue(routes,report)
        self.assertFalse(report['missingGoal'])
        self.assertTrue(d.locate(routes[0].points[-1]))
        self.assertEqual(landmarks(scene),[])

    def test_touching_a_gate_then_returning_does_not_create_crossings(self):
        gate = dict(id=0, axis=0, lateral=2, plane=2, lo=0, hi=1)
        for points in [[[1,1,-1],[2,1,-1],[1,1,-1]], [[3,1,-1],[2,1,-1],[3,1,-1]]]:
            self.assertEqual(crossings(points, [gate]), [])
        self.assertEqual(len(crossings([[1,1,-1],[2,1,-1],[2,2,-1],[3,2,-1]], [gate])), 1)

    def test_blocked_prescribed_trace_and_budget_expiry_are_explicit(self):
        scene = side_scene()
        d = decompose(scene)
        with self.assertRaises(ValueError):
            corridor_for_points(d, [[.5, 1, .5], [5.5, 1, .5]], landmarks(scene))
        routes, report = enumerate_routes(scene, d, deadline=0)
        self.assertEqual(routes, [])
        self.assertEqual(report['termination'], 'deadline')
        with self.assertRaises(ValueError):
            enumerate_routes(scene, d, max_routes=0)


if __name__ == '__main__':
    unittest.main()
