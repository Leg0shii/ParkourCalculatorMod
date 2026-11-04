import json
import unittest

from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.regions import build_route_graph
from standalone.autonomy.cell_routes.routes import conditioned_anchor, corridor_from_walk, enumerate_routes
from standalone.autonomy.tests.test_cell_routes import side_scene
from standalone.autonomy.tests.test_route_regions import task_scene


def support_scene():
    scene=side_scene()
    scene['blocks'][1]['top']=1
    scene['collisionScene']['solids'][1][4]=1
    return scene


class SemanticRouteTests(unittest.TestCase):
    def test_support_visit_and_overflight_have_distinct_keys_and_paths(self):
        scene = support_scene()
        d = decompose(scene)
        graph = build_route_graph(scene, d)
        routes, report = enumerate_routes(scene, d, route_graph=graph, max_routes=12, max_expansions=3000, max_visits=1)
        wall = next(s.key for s in graph.supports if s.owner == 'wall')
        touching = [r for r in routes if ('support', wall) in r.semantic_key]
        flying = [r for r in routes if ('support', wall) not in r.semantic_key]
        self.assertTrue(touching, report['termination'])
        self.assertTrue(flying)
        self.assertTrue(all(r.schema == 2 and not r.signature for r in routes))
        self.assertTrue(all(r.projection for r in routes))
        self.assertEqual(len({json.dumps(r.semantic_key) for r in routes}), len(routes))
        for r in routes:
            self.assertTrue(r.route_variants[0]['transitions'])
            self.assertTrue(any(s['kind'] == 'support_arrival' for s in r.stages))
        # These remain conservative geometric proposals, not wall-top reach proofs.
        self.assertEqual(report['movementFeasibility'], 'unresolved')
        self.assertFalse(report['coverageProven'])

    def test_spatial_interruption_resume_preserves_order_and_untried_families(self):
        scene = side_scene()
        d = decompose(scene)
        graph = build_route_graph(scene, d)
        expected, _ = enumerate_routes(scene, d, route_graph=graph, max_routes=5, max_expansions=3000)
        routes, report = enumerate_routes(scene, d, route_graph=graph, max_routes=5, max_expansions=1)
        self.assertEqual(report['termination'], 'expansion_limit')
        for _ in range(30):
            if report['termination'] == 'route_limit':
                break
            saved = json.loads(json.dumps(report['continuation'], allow_nan=False))
            routes, report = enumerate_routes(scene, d, route_graph=graph, max_routes=5,
                                              max_expansions=60, continuation=saved)
        self.assertEqual([json.dumps(r.semantic_key) for r in routes], [json.dumps(r.semantic_key) for r in expected])
        with self.assertRaisesRegex(ValueError, 'Incompatible spatial'):
            enumerate_routes(scene, d, route_graph=graph, max_visits=1, continuation=report['continuation'])

    def test_equivalent_box_split_preserves_cells_and_support_route_keys(self):
        a = task_scene([[0,0,0,2,1,1], [4,0,0,5,1,1]], ['floor','goal'])
        b = task_scene([[0,0,0,1,1,1], [1,0,0,2,1,1], [4,0,0,5,1,1]], ['floor','floor','goal'])
        keys = []
        for scene in (a, b):
            d = decompose(scene)
            graph = build_route_graph(scene, d)
            routes, _ = enumerate_routes(scene, d, route_graph=graph, max_routes=5, max_expansions=3000)
            keys.append([r.semantic_key for r in routes])
        self.assertTrue(keys[0])
        self.assertEqual(keys[0], keys[1])

    def test_local_anchor_is_not_moved_by_distant_coverage(self):
        before, after = [0, 1, 0], [4, 2, 2]
        a = conditioned_anchor([(2, 1, -100, 2, 20, 100)], before, after)
        b = conditioned_anchor([(2, 1, -110, 2, 30, 110)], before, after)
        self.assertEqual(a, b)
        self.assertEqual(a, (2, 1.5, 1))

    def test_variant_keeps_region_order_and_does_not_union_opposite_paths(self):
        scene = side_scene()
        d = decompose(scene)
        graph = build_route_graph(scene, d)
        routes, _ = enumerate_routes(scene, d, route_graph=graph, max_routes=4)
        lookup = {t.key:t for t in graph.transitions}
        for r in routes:
            for v in r.route_variants:
                self.assertEqual(len(v['regions']), len(v['transitions'])+1)
                for a, b, key in zip(v['regions'], v['regions'][1:], v['transitions']):
                    self.assertEqual((lookup[key].source, lookup[key].target), (a,b))
            reconstructed = corridor_from_walk(scene, d, graph, **dict(
                region_path=r.route_variants[0]['regions'], transition_path=r.route_variants[0]['transitions']))
            self.assertEqual(r.semantic_key, reconstructed.semantic_key)


if __name__ == '__main__':
    unittest.main()
