"""Offline geometry reporting preserves unions, overheads and compound steps."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from parkour_solver.scene.geometry import normalize_geometry
from parkour_solver.spatial.cells import contains
from parkour_solver.spatial.faces import FaceCatalog, merge_faces
from tests.support.paths import STANDALONE_ROOT
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

    def test_optional_face_report_agrees_with_catalog_and_preserves_bounds_contract(self):
        scene = dict(sceneModel='collision_boxes', geometryBounds=[-5, -5, -5, 10, 10, 10],
                     blocks=[dict(id='pair', boxes=[[0, 0, 0, 1, 1, 1], [1, 0, 0, 2, 1, 1]])])
        original = copy.deepcopy(scene)
        basic = player_bounds_report(scene)
        enhanced = player_bounds_report(dict(request=scene), include_faces=True)
        self.assertEqual(scene, original)
        self.assertEqual(basic, {k: v for k, v in enhanced.items() if k not in ('faceCatalog', 'faceStatistics')})
        catalog = merge_faces(normalize_geometry(copy.deepcopy(scene)))
        self.assertEqual(enhanced['faceCatalog'], catalog.to_dict())
        self.assertEqual(enhanced['faceStatistics']['mergedFaces'], 6)
        self.assertEqual(FaceCatalog.from_dict(enhanced['faceCatalog']).catalog_id, catalog.catalog_id)
        self.assertNotIn('Merged physical faces', markdown_report(basic))
        markdown = markdown_report(enhanced)
        self.assertIn('Merged physical faces', markdown)
        self.assertIn('pair#0, pair#1', markdown)
        for normal in ('+X', '−X', '+Y', '−Y', '+Z', '−Z'):
            self.assertIn(normal, markdown)

    def test_faces_cli_writes_exact_catalog_and_readable_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output, markdown = root/'scene.json', root/'out.json', root/'out.md'
            source.write_text(json.dumps(dict(sceneModel='collision_boxes',
                geometryBounds=[-2, -2, -2, 4, 4, 4],
                blocks=[dict(id='box', boxes=[[0, 0, 0, 1, 1, 1]])])))
            result = subprocess.run([sys.executable, '-B', '-m', 'tools.geometry_bounds',
                '--input', str(source), '--output', str(output), '--markdown', str(markdown), '--faces'],
                cwd=STANDALONE_ROOT, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(output.read_text())
            self.assertEqual(len(FaceCatalog.from_dict(report['faceCatalog']).faces), 6)
            self.assertIn('physical faces', markdown.read_text())


if __name__ == '__main__':
    unittest.main()
