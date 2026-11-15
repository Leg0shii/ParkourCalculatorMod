from tests.support.paths import AUTONOMY_DATA
import hashlib
import json
import os
from pathlib import Path
import unittest

from parkour_solver.scene.route import schedule, supports
from parkour_solver.search.repair import contact_conflicts
from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.scene.geometry import check_geometry
from parkour_solver.constraints.avoidance import DEFAULT_CAPS, gate_timeline, within_caps
from parkour_solver.numerics import prepare, validate
from parkour_solver.numerics.model import fingerprint


class BlockRouteWitnessTests(unittest.TestCase):
    @unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER'), 'Set PKC_RUN_TICKS_COMPILER')
    def test_saved_interleaving_replays_with_per_block_caps_and_shared_collision_checks(self):
        root = AUTONOMY_DATA
        folder = root / 'experiments/interleaved-blockages'
        summary = json.loads((folder / 'summary.json').read_text())
        result = json.loads((folder / 'result.json').read_text())
        request = prepare(json.loads((folder / 'request.json').read_text()))
        self.assertEqual(hashlib.sha256((root / 'fixtures' / summary['fixture']).read_bytes()).hexdigest(), summary['fixtureSha256'])
        self.assertEqual(fingerprint(request), summary['fullProblemHash'])
        best = result['best']
        plan = schedule(best['route'], best['allocation'])
        timeline = gate_timeline(best['passages'], plan['events'])
        self.assertEqual(timeline, summary['blockageTimeline'])
        self.assertGreater(len(timeline), DEFAULT_CAPS['total'])
        self.assertTrue(within_caps(best['passages'], DEFAULT_CAPS))
        wall = [i for i, event in enumerate(timeline) if event['blockId'] == 'wall']
        self.assertTrue(any(e['blockId'] == 'ceiling' for e in timeline[wall[0] + 1:wall[-1]]))
        checked = validate(request, request['yaw'])
        self.assertTrue(checked['feasible'], checked)
        vertical = {key: checked[key] for key in ('y', 'vy', 'onGround')}
        vertical['vertical'] = request['vertical']
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            self.assertTrue(check_geometry(result['scene']['collisionScene'], plan, checked, vertical, compiler, 3)['feasible'])

    @unittest.skipUnless(os.environ.get('PKC_RUN_TICKS_COMPILER'), 'Set PKC_RUN_TICKS_COMPILER')
    def test_saved_obstacle_routes_pass_numerical_and_shared_swept_collision_checks(self):
        root = AUTONOMY_DATA
        artifacts = root / 'experiments' / 'obstacle-avoidance'
        summaries = json.loads((artifacts / 'summary.json').read_text())
        with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
            for label, summary in summaries.items():
                with self.subTest(label=label):
                    fixture = root / 'fixtures' / summary['fixture']
                    self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(), summary['fixtureSha256'])
                    result = json.loads((artifacts / (label + '-result.json')).read_text())
                    self.assertEqual(result['status'], summary['status'])
                    if summary['status'] == 'unknown':
                        self.assertIsNone(result['best'])
                        continue
                    request = prepare(json.loads((artifacts / (label + '-request.json')).read_text()))
                    self.assertEqual(fingerprint(request), summary['fullProblemHash'])
                    best = result['best']
                    checked = validate(request, best['candidate']['yaw'])
                    self.assertTrue(checked['feasible'], checked)
                    plan = schedule(best['route'], best['allocation'])
                    self.assertEqual(fingerprint(prepare(dict(request, walls=plan['walls']))), fingerprint(request))
                    self.assertEqual(plan['ticks'], summary['ticks'])
                    self.assertEqual(checked['onGround'], plan['grounded'] + [True])
                    self.assertEqual(contact_conflicts(best['contactChecks'], plan, checked), [])
                    for state, block, kind in plan['supportChecks']:
                        self.assertTrue(supports(best['route']['blocks'][block], *checked['positions'][state], kind=kind))
                    for state, block in plan['exitChecks']:
                        self.assertFalse(supports(best['route']['blocks'][block], *checked['positions'][state], kind='run'))
                    vertical = {key: checked[key] for key in ('y', 'vy', 'onGround')}
                    vertical['vertical'] = request['vertical']
                    geometry = check_geometry(result['scene']['collisionScene'], plan, checked, vertical, compiler, 3)
                    self.assertTrue(geometry['feasible'], geometry)

    def test_saved_runoffs_validate_no_jump_physics_and_actual_source_exits(self):
        root = AUTONOMY_DATA
        artifacts = root / 'experiments' / 'runoff-profiles'
        summaries = json.loads((artifacts / 'summary.json').read_text())
        for label, summary in summaries.items():
            with self.subTest(label=label):
                request = prepare(json.loads((artifacts / (label + '-request.json')).read_text()))
                result = json.loads((artifacts / (label + '-result.json')).read_text())
                fixture = root / 'fixtures' / summary['fixture']
                self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(), summary['fixtureSha256'])
                self.assertEqual(fingerprint(request), summary['fullProblemHash'])
                best = result['best']
                checked = validate(request, best['candidate']['yaw'])
                self.assertTrue(checked['feasible'], checked)
                self.assertEqual(checked['onGround'], best['plan']['grounded'] + [True])
                self.assertEqual(checked['y'], best['candidate']['y'])
                self.assertEqual(contact_conflicts(best['contactChecks'], best['plan'], checked), [])
                for state, block, kind in best['plan']['supportChecks']:
                    self.assertTrue(supports(best['route']['blocks'][block], *checked['positions'][state], kind=kind))
                for state, block in best['plan']['exitChecks']:
                    self.assertFalse(supports(best['route']['blocks'][block], *checked['positions'][state], kind='run'))
                jump_inputs = [i for i, jumping in enumerate(best['plan']['jump']) if jumping]
                self.assertEqual(jump_inputs, summary['jumpInputs'])
                self.assertEqual(best['plan']['ticks'], summary['ticks'])
                self.assertEqual(best['route']['airtimes'], summary['airtimes'])
                self.assertEqual([p['profileKind'] for p in best['profiles']], summary['profileKinds'])
                for event, profile in zip(best['plan']['events'], best['profiles']):
                    if profile['profileKind'] == 'run_off':
                        self.assertTrue(all(v is None for v in request['vertical']['jumpVelocity'][event['jump']:event['landing']]))

    def test_saved_height_and_stair_routes_include_valid_retimed_y_contacts(self):
        root = AUTONOMY_DATA
        artifacts = root / 'experiments' / 'height-and-stairs'
        summaries = json.loads((artifacts / 'summary.json').read_text())
        for label, summary in summaries.items():
            with self.subTest(label=label):
                request = prepare(json.loads((artifacts / (label + '-request.json')).read_text()))
                result = json.loads((artifacts / (label + '-result.json')).read_text())
                fixture = root / 'fixtures' / summary['fixture']
                self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(), summary['fixtureSha256'])
                self.assertEqual(fingerprint(request), summary['fullProblemHash'])
                best = result['best']
                checked = validate(request, best['candidate']['yaw'])
                self.assertTrue(checked['feasible'], checked)
                self.assertEqual(checked['onGround'], best['plan']['grounded'] + [True])
                self.assertEqual(checked['y'], best['candidate']['y'])
                self.assertEqual(contact_conflicts(best['contactChecks'], best['plan'], checked), [])
                for state, block, kind in best['plan']['supportChecks']:
                    self.assertTrue(supports(best['route']['blocks'][block], *checked['positions'][state], kind=kind))
                self.assertEqual(best['route']['airtimes'], summary['airtimes'])
                self.assertEqual([p['profileKind'] for p in best['profiles']], summary['profileKinds'])

    def test_saved_routes_validate_horizontally_vertically_and_on_actual_supports(self):
        root = AUTONOMY_DATA
        artifacts = root / 'experiments' / 'block-route-search'
        summaries = json.loads((artifacts / 'summary.json').read_text())
        for label, summary in summaries.items():
            with self.subTest(label=label):
                request = prepare(json.loads((artifacts / (label + '-request.json')).read_text()))
                result = json.loads((artifacts / (label + '-result.json')).read_text())
                vertical = json.loads((artifacts / (label + '-vertical.json')).read_text())
                fixture = root / 'fixtures' / summary['fixture']
                self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(), summary['fixtureSha256'])
                best = result['best']
                self.assertEqual(fingerprint(request), best['problemHash'])
                full = prepare(dict(request, vertical=vertical))
                self.assertEqual(fingerprint(full), summary['withVerticalHash'])
                checked = validate(full, best['candidate']['yaw'])
                self.assertTrue(checked['feasible'], checked)
                self.assertEqual(checked['onGround'], best['plan']['grounded'] + [True])
                self.assertEqual(best['blockIds'][0], result['scene']['startBlock'])
                self.assertEqual(best['blockIds'][-1], result['scene']['goalBlock'])
                self.assertFalse(any(w['mode'] == 'F' for w in request['walls']))
                self.assertTrue(best['route']['inputs']['force45'])
                for state, block, kind in best['plan']['supportChecks']:
                    self.assertTrue(supports(best['route']['blocks'][block], *checked['positions'][state], kind=kind))


if __name__ == '__main__':
    unittest.main()
