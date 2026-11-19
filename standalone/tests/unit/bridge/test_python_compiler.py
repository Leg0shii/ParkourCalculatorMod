import copy
import itertools
import json
import math
import os
from pathlib import Path
import random
import struct
import sys
import tempfile
import unittest
from unittest import mock

from parkour_solver.bridge.compiler import JavaCompiler, open_compiler
from parkour_solver.bridge.python_compiler import PythonCompiler
from parkour_solver.physics.vertical import f32
from parkour_solver.scene.geometry import BODY_HEIGHT, support_center_interval
from parkour_solver.scene.route import HALF_WIDTH

CONFIG = os.environ.get('PKC_RUN_TICKS_COMPILER')
RUNTIME = os.environ.get('PKC_IPOPT_PYTHON')
FLOOR = [-4, -1, -4, 4, 0, 4]
SCENE = dict(version=1, profile='minecraft-1.8.9', bounds=[-5, -4, -5, 5, 7, 5], solids=[FLOOR])


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'))


def reply(compiler, message):
    try:
        return compiler._exchange(copy.deepcopy(message), 30)
    except ValueError:
        return 'error'


def move(movement, expected, *solids, contact=None, start=(0., 0., 0.), grounded=True, grounded_end=False, **fields):
    return dict(action='sceneCheck', scene=dict(SCENE, solids=[FLOOR, *solids]), movement=[movement],
                positions=[list(start), expected], expectedGround=[grounded, grounded_end],
                contacts=[] if contact is None else [contact], **fields)


def scene_probes():
    """Boundary cases from the research comparison plus stepping, faces and coverage."""
    step = [1, 0, -2, 2, .5, 2]
    yield 'ordinary_step', move([1., -.1, 0.], [1., .5, 0.], step, grounded_end=True,
                                contact=dict(axis=1, state=1, direction=-1, plane=.5, boxes=[step], step=True))
    yield 'named_step', move([1., -.1, 0.], [1., .5, 0.], step, grounded_end=True,
                             contact=dict(axis=1, state=1, direction=-1, plane=.5, boxes=[step], movementCase='normal_step'))
    yield 'unplanned_step', move([1., -.1, 0.], [1., 0., 0.], step, grounded_end=True)
    roof = [-2, 2.125, -2, 2, 3, 2]
    plane = 2.125 - BODY_HEIGHT
    yield 'ceiling', move([0., .7, 0.], [0., plane, 0.], roof,
                          contact=dict(axis=1, state=1, direction=1, plane=plane, boxes=[roof]))
    yield 'unplanned_ceiling', move([0., .7, 0.], [0., .7, 0.], roof)
    wall = [1, 0, -2, 2, 3, 2]
    plane = 1 - HALF_WIDTH
    yield 'wall', move([1., 0., 0.], [plane, .5, 0.], wall, start=(0., .5, 0.), grounded=False,
                       contact=dict(axis=0, state=1, direction=1, plane=plane, boxes=[wall]))
    yield 'unplanned_wall', move([1., 0., 0.], [1., .5, 0.], wall, start=(0., .5, 0.), grounded=False)
    halves = [[1, 0, -2, 2, 3, 0], [1, 0, 0, 2, 3, 2]]
    yield 'coplanar_member_face', move([1., 0., 0.], [plane, .5, .1], *halves, start=(0., .5, .1), grounded=False,
                                       contact=dict(axis=0, state=1, direction=1, plane=plane, boxes=[halves[0]]))
    yield 'touching_wall_outward', move([-.2, 0., 0.], [plane - .2, .5, 0.], wall, start=(plane, .5, 0.),
                                        grounded=False)
    yield 'touching_wall_inward', move([.2, 0., 0.], [plane, .5, 0.], wall, start=(plane, .5, 0.), grounded=False,
                                       contact=dict(axis=0, state=1, direction=1, plane=plane, boxes=[wall]))
    yield 'sneak_edge', move([.45, -.1, 0.], [4.4, 0., 0.], start=(4., 0., 0.), grounded_end=True, sneak=[True])
    yield 'sneak_edge_diagonal', move([.45, -.1, .45], [4.4, 0., 4.4], start=(4., 0., 4.), grounded_end=True,
                                      sneak=[True])
    yield 'coverage', move([5., 0., 0.], [5., .5, 0.], start=(0., .5, 0.), grounded=False)
    yield 'nonfinite_move', move([math.inf, 0., 0.], [0., .5, 0.], start=(0., .5, 0.), grounded=False)
    ladder = move([.16, 0., 0.], [.16, .5, 0.], start=(0., .5, 0.), grounded=False)
    ladder['scene']['ladders'] = [[0, 0, 0]]
    yield 'ladder_initial_mismatch', dict(ladder, ladderTicks=[False, True])
    yield 'ladder_speed_cap', dict(ladder, ladderTicks=[True, True])
    yield 'ladder_carry', dict(ladder, movement=[[.1, 0., 0.]], positions=[[0., .5, 0.], [.1, .5, 0.]],
                               ladderTicks=[True, True], friction=[.91],
                               velocities=[[0., 0., 0.], [.091, -.08 * f32(.98), 0.]])
    yield 'surfaces_and_metadata', dict(move([.1, 0., 0.], [.1, .5, 0.], start=(0., .5, 0.), grounded=False),
                                        scene=dict(SCENE, surfaces=[dict(cell=[0, -1, 0], slipperiness=.8)],
                                                   metadata={'name': 'probe'}, stepHeight=.6))
    yield 'invalid_step_height', dict(move([.1, 0., 0.], [.1, .5, 0.], start=(0., .5, 0.), grounded=False),
                                      scene=dict(SCENE, stepHeight=.5))
    yield 'start_inside_solid', move([0., 0., 0.], [0., -.5, 0.], start=(0., -.5, 0.))
    yield 'inconsistent_lengths', dict(move([0., 0., 0.], [0., 0., 0.]), expectedGround=[True])


def translated(message, dx, dz, mirror=False):
    """The same check at other world coordinates, optionally reflected in X."""
    def box(b):
        b = [b[0] + dx, b[1], b[2] + dz, b[3] + dx, b[4], b[5] + dz]
        return [-b[3], b[1], b[2], -b[0], b[4], b[5]] if mirror else b

    def point(p):
        return [-(p[0] + dx) if mirror else p[0] + dx, p[1], p[2] + dz]

    out = copy.deepcopy(message)
    scene = out['scene']
    scene['bounds'], scene['solids'] = box(scene['bounds']), [box(b) for b in scene['solids']]
    if 'ladders' in scene:
        scene['ladders'] = [[-(c[0] + dx) - 1 if mirror else c[0] + dx, c[1], c[2] + dz] for c in scene['ladders']]
    out['positions'] = [point(p) for p in out['positions']]
    out['movement'] = [[-m[0] if mirror else m[0], m[1], m[2]] for m in out['movement']]
    if 'velocities' in out:
        out['velocities'] = [[-v[0] if mirror else v[0], v[1], v[2]] for v in out['velocities']]
    for contact in out['contacts']:
        contact['boxes'] = [box(b) for b in contact.get('boxes', [])]
        if contact['axis'] == 0:
            contact['plane'] = -(contact['plane'] + dx) if mirror else contact['plane'] + dx
            contact['direction'] *= -1 if mirror else 1
        elif contact['axis'] == 2:
            contact['plane'] += dz
    return out


def random_scene_check(rng):
    """One- or two-tick checks among nearby boxes, with expectations and contacts from Python."""
    solids = [FLOOR]
    for _ in range(rng.randint(0, 4)):
        x, z = rng.choice([-1.5, -1, -.5, .5, 1, 1.5]), rng.choice([-1.5, -1, -.5, .5, 1, 1.5])
        y = rng.choice([0, 0, .5, 1, 1.5, 2])
        size = rng.choice([.5, 1])
        solids.append([x, y, z, x + size, y + rng.choice([.5, 1, 1.5]), z + size])
    scene = dict(SCENE, solids=solids)
    grounded = rng.random() < .6
    start = [rng.uniform(-.2, .2), 0. if grounded else rng.choice([.2, .5, 1.]), rng.uniform(-.2, .2)]
    n = rng.choice([1, 2])
    movement = [[rng.choice([0., -.3, .3, rng.uniform(-.6, .6)]), rng.choice([-.1, -.0784, .42, .2, -.5]),
                 rng.choice([0., -.3, .3, rng.uniform(-.6, .6)])] for _ in range(n)]
    message = dict(action='sceneCheck', scene=scene, movement=movement, positions=[start] * (n + 1),
                   expectedGround=[grounded] * (n + 1), contacts=[])
    if rng.random() < .3:
        message['sneak'] = [rng.random() < .7 for _ in range(n)]
    return message


def realized(message, compiler):
    """Make expectations and planned contacts agree with the Python replay, tick by tick."""
    from parkour_solver.scene.static_collision import StaticScene
    scene = StaticScene(message['scene'])
    try:
        body = scene.start_box(tuple(message['positions'][0]))
    except ValueError:
        return message
    positions, ground, contacts = [message['positions'][0]], [message['expectedGround'][0]], []
    for t, step in enumerate(message['movement']):
        sneak = bool(message.get('sneak') and message['sneak'][t])
        try:
            hit = scene.move(body, tuple(step), ground[-1], sneak)
        except ValueError:
            return message
        positions.append(list(hit.position))
        ground.append(hit.on_ground)
        last = {}
        for clip in hit.clips:
            last[clip[1]] = clip
        for axis, clip in last.items():
            if axis == 1 and not (step[1] > 0 or hit.stepped):
                continue
            direction = 1 if clip[2] > 0 else -1
            contact = dict(state=t + 1, axis=axis, direction=direction, plane=hit.position[axis], boxes=[list(clip[0])])
            if hit.stepped and axis == 1:
                contact.update(direction=-1, step=True)
            contacts.append(contact)
        if hit.stepped and 1 not in last:
            contacts.append(dict(state=t + 1, axis=1, direction=-1, plane=hit.position[1], step=True,
                                 boxes=[list(b) for b in scene.solids if b[4] == hit.position[1]]))
        body = hit.box
    return dict(message, positions=positions, expectedGround=ground, contacts=contacts)


def shared_request(rng, n):
    grounded = [rng.random() < .5 for _ in range(n)]
    plan = dict(grounded=grounded, jump=[rng.random() < .4 for _ in range(n)],
                sceneContacts=[dict(axis=rng.choice([0, 1, 2]), state=rng.randint(1, n)) for _ in range(rng.randint(0, 4))])
    if rng.random() < .8:
        plan['sneak'] = [rng.random() < .15 for _ in range(n)]
    if rng.random() < .3:
        plan['ladderTicks'] = [rng.random() < .3 for _ in range(n + 1)]
    if rng.random() < .7:
        plan['vertical'] = dict(profile='minecraft-1.8.9', onGround=grounded[0],
                                previousHorizontalCollision=rng.random() < .3, threshold=0.,
                                contacts=[dict(state=1, plane=1, direction=-1)], sneak=None)
    return plan


@unittest.skipUnless(CONFIG, 'Set PKC_RUN_TICKS_COMPILER for the Java oracle')
class JavaOracleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = JavaCompiler(json.loads(Path(CONFIG).read_text()))
        cls.python = PythonCompiler()

    @classmethod
    def tearDownClass(cls):
        cls.java.close()

    def assertSameReply(self, message, name=None):
        expected, actual = reply(self.java, message), reply(self.python, message)
        self.assertEqual(canonical(actual), canonical(expected), name or message)

    def test_generated_tables_match_a_fresh_generation(self):
        from tools.generate_coefficient_tables import OUTPUT, render
        self.assertEqual(OUTPUT.read_text(), render(self.java))

    def test_shared_coefficients_match_whole_payloads(self):
        rng = random.Random(7)
        for trial in range(400):
            n = rng.choice([1, 2, 3, 11, 12, 40, 601, 640])
            plan = shared_request(rng, n)
            inputs = dict(sprint=rng.random() < .7, force45=rng.random() < .5)
            if rng.random() < .8:
                inputs['incomingSprint'] = rng.random() < .5
            route = dict(sharedMovement=True, inputs=dict(inputs, forward=rng.choice([.98, f32(.98)]), strafe=0.))
            with self.subTest(trial=trial, n=n):
                from parkour_solver.bridge.compiler import coefficient_request
                self.assertSameReply(coefficient_request(route, plan))

    def test_held_jumps_cross_the_cooldown_and_walls_end_sprint(self):
        from parkour_solver.bridge.compiler import coefficient_request
        for sprint, incoming, force45 in itertools.product((False, True), repeat=3):
            route = dict(sharedMovement=True, inputs=dict(sprint=sprint, incomingSprint=incoming, force45=force45))
            plan = dict(grounded=[True] * 25, jump=[True] * 25, sneak=[t in (3, 4) for t in range(25)],
                        sceneContacts=[dict(axis=0, state=6), dict(axis=2, state=15), dict(axis=1, state=9)],
                        vertical=dict(profile='minecraft-1.8.9', onGround=True, previousHorizontalCollision=True))
            with self.subTest(sprint=sprint, incoming=incoming, force45=force45):
                self.assertSameReply(coefficient_request(route, plan))

    def test_legacy_coefficients_match_at_every_float32_boundary(self):
        from parkour_solver.bridge.compiler import coefficient_request
        low, high = f32(.98) - 1e-7, f32(.98) + 1e-7
        bits = struct.unpack('<I', struct.pack('<f', f32(.98)))[0]
        forwards = {low, high, .98, f32(.98)}
        for b in range(bits - 3, bits + 4):
            value = struct.unpack('<f', struct.pack('<I', b))[0]
            following = struct.unpack('<f', struct.pack('<I', b + 1))[0]
            middle = (value + following) / 2
            forwards.update([value, middle, math.nextafter(middle, 0), math.nextafter(middle, 2)])
        rng = random.Random(3)
        checked = 0
        for forward in sorted(forwards):
            if abs(forward - f32(.98)) > 1e-7:
                continue
            for strafe in (0., -0.):
                n = rng.choice([1, 5, 30])
                route = dict(inputs=dict(forward=forward, strafe=strafe, sprint=rng.random() < .7,
                                         incomingSprint=rng.random() < .5, force45=rng.random() < .5))
                plan = dict(grounded=[rng.random() < .5 for _ in range(n)], jump=[rng.random() < .4 for _ in range(n)])
                with self.subTest(forward=forward.hex(), strafe=strafe):
                    self.assertSameReply(coefficient_request(route, plan))
                checked += 1
        self.assertGreater(checked, 20)

    def test_invalid_coefficient_requests_are_errors_on_both_bridges(self):
        base = dict(sharedMovement=True, grounded=[True, False], jump=[True, False], inputs=dict(sprint=True),
                    contacts=[], vertical=dict(profile='minecraft-1.8.9', onGround=True, previousHorizontalCollision=False))
        for name, change in [('empty', dict(grounded=[], jump=[])), ('wall_state', dict(contacts=[dict(axis=0, state=3)])),
                             ('initial_ground', dict(vertical=dict(base['vertical'], onGround=False))),
                             ('profile', dict(vertical=dict(base['vertical'], profile='other'))),
                             ('short_sneak', dict(sneak=[True]))]:
            with self.subTest(name=name):
                self.assertEqual(reply(self.python, dict(base, **change)), 'error')
                self.assertEqual(reply(self.java, dict(base, **change)), 'error')
        legacy = dict(grounded=[True], jump=[True, False], inputs=dict(forward=f32(.98), strafe=0.))
        self.assertEqual(reply(self.python, legacy), reply(self.java, legacy))

    def test_scene_probes_match_at_world_coordinates_and_reflections(self):
        for name, message in scene_probes():
            for dx, dz, mirror in [(0, 0, False), (729, -1095, False), (-3e6, 2e6, False), (12, 0, True)]:
                if name in ('nonfinite_move', 'invalid_step_height') and (dx, dz, mirror) != (0, 0, False):
                    continue
                with self.subTest(name=name, dx=dx, dz=dz, mirror=mirror):
                    self.assertSameReply(translated(message, dx, dz, mirror))

    def test_random_scene_checks_match(self):
        rng = random.Random(11)
        outcomes = set()
        for trial in range(1500):
            message = random_scene_check(rng)
            if rng.random() < .6:
                message = realized(message, self.python)
            if rng.random() < .3:
                message = translated(message, rng.choice([729, -1095.5, 1e5]), rng.choice([0, -1093.25]))
            with self.subTest(trial=trial):
                self.assertSameReply(message)
            result = reply(self.python, message)
            outcomes.add(result if result == 'error' else result.get('reason', 'feasible'))
        self.assertTrue({'feasible', 'scene_collision', 'unplanned_step', 'error'} <= outcomes, outcomes)

    def test_footprints_match(self):
        blocks = [dict(bounds=[0, 1, 0, 1], top=0, standingBounds=[.25, .75, .25, .75]),
                  dict(bounds=[1, 2, 1, 2], top=.5, centerBounds=[1.1, 1.9, 1.1, 1.9]),
                  dict(bounds=[729, 730, -1095, -1093], top=7),
                  dict(bounds=[-3.5, -2.25, -1e6, -999999.5], top=1),
                  dict(bounds=[.6, 1.4, -.9, -.1], top=2, standingBounds=[.7, 1.2, -.8, -.2])]
        rng = random.Random(5)
        for _ in range(200):
            x, z = rng.uniform(-2e3, 2e3), rng.uniform(-2e3, 2e3)
            blocks.append(dict(bounds=[x, x + rng.choice([.0625, .5, 1, 3]), z, z + rng.choice([.125, 1, 2])], top=0))
        for action in ('footprints', 'standingFootprints'):
            with self.subTest(action=action):
                self.assertSameReply(dict(action=action, blocks=blocks))
        self.assertSameReply(dict(action='footprints', blocks=blocks[:1]))

    @unittest.skipUnless(RUNTIME, 'Set PKC_IPOPT_PYTHON for fixture candidate calls')
    def test_fixture_candidate_calls_match(self):
        from parkour_solver.search.ordered_route import find_route
        from parkour_solver.search.support_profiles import find_blocks
        from tests.support.paths import AUTONOMY_DATA
        recorded = []

        class Recorder(JavaCompiler):
            def _exchange(self, message, seconds):
                result = super()._exchange(message, seconds)
                recorded.append((copy.deepcopy(message), copy.deepcopy(result)))
                return result

        fixtures = AUTONOMY_DATA / 'fixtures'
        with Recorder(json.loads(Path(CONFIG).read_text())) as java:
            find_route(json.loads((fixtures / 'two-jumps.json').read_text()), java, seconds=20, candidate_seconds=2,
                       retries=0, python_executable=RUNTIME)
            for name in ('blocks-required-run.json', 'obstacle-under-ceiling.json'):
                find_blocks(json.loads((fixtures / name).read_text()), java, seconds=20, route_seconds=8,
                            candidate_seconds=1, retries=0, python_executable=RUNTIME)
        actions = {message.get('action', 'compile') for message, _ in recorded}
        self.assertTrue({'compile', 'sceneCheck', 'footprints'} <= actions, actions)
        for index, (message, expected) in enumerate(recorded):
            with self.subTest(index=index, action=message.get('action', 'compile')):
                self.assertEqual(canonical(self.python._exchange(message, 30)), canonical(expected))

    @unittest.skipUnless(RUNTIME, 'Set PKC_IPOPT_PYTHON for checkpoint continuation')
    def test_saved_searches_resume_exactly_on_the_other_bridge(self):
        import threading
        from parkour_solver.io.checkpoint_store import CheckpointStore, read_report
        from parkour_solver.io.search_checkpoint import SearchCheckpoint
        from parkour_solver.search import ordered_route
        from parkour_solver.search.domain import InitialJumpDomain
        from parkour_solver.search.engine import find_checkpoint_route
        from tests.support.checkpoint_guidance import flat_scene
        solve = ordered_route.solve

        def search(compiler, requests, path, stop_after=None, resume_state=None):
            # A wall in the flight path makes the search repair contacts across 20 requests.
            scene = flat_scene()
            scene['movementSearch'].update(maxInputs=28, maxPrefixes=64)
            scene['collisionScene']['solids'].append([3.5, 1., -.2, 4., 3., .2])
            scene['blocks'].append(dict(id='wall', sourceBlock='wall', solidIndex=2, top=3., bounds=[3.5, 4., -.2, .2]))
            cancel = threading.Event()

            def record(problem, **kwargs):
                requests.append(canonical(problem))
                outcome = solve(problem, **kwargs)
                if stop_after is not None and len(requests) >= stop_after:
                    cancel.set()
                return outcome
            with CheckpointStore(path) as store, mock.patch.object(ordered_route, 'solve', record):
                writer = SearchCheckpoint(store, {})
                # Generous budgets keep outcomes independent of host load across the compared runs.
                result = find_checkpoint_route(scene, compiler, anchor_search={}, seconds=300, score_seconds=20,
                    candidate_seconds=20, max_solver_calls=20, program_filter=InitialJumpDomain([0], max_inputs=28),
                    python_executable=RUNTIME, on_progress=writer, cancel_event=cancel, resume_state=resume_state)
                writer.finish(result, [])
            return read_report(path)['result']

        with tempfile.TemporaryDirectory() as directory:
            control = []
            search(self.python, control, Path(directory) / 'control.json.gz')
            self.assertEqual(len(control), 20)
            for first, second in [(self.java, self.python), (self.python, self.java)]:
                with self.subTest(first=type(first).__name__):
                    before, crossed, same = [], [], []
                    path = Path(directory) / (type(first).__name__ + '.json.gz')
                    saved = search(first, before, path, stop_after=3)['searchState']
                    resumed = search(second, crossed, Path(str(path) + '.crossed'),
                                     resume_state=json.loads(json.dumps(saved)))
                    control_resume = search(first, same, Path(str(path) + '.same'),
                                            resume_state=json.loads(json.dumps(saved)))
                    # A resumed session gets a fresh call allowance, so it runs past the control.
                    self.assertEqual(crossed, same)
                    self.assertEqual((before + crossed)[:len(control)], control)
                    self.assertFalse(set(before) & set(crossed))
                    self.assertEqual(canonical(resumed['best']), canonical(control_resume['best']))

class PythonBridgeTests(unittest.TestCase):
    def test_support_edges_are_the_exact_strict_overlap_limits(self):
        for lo, hi in [(0, 1), (HALF_WIDTH, 2), (-1, -HALF_WIDTH), (-HALF_WIDTH, HALF_WIDTH), (729, 730),
                       (-1095.5, -1093), (1e9, 1e9 + 1), (-2.5e-17, 3e-17)]:
            first, last = support_center_interval(lo, hi)

            def supports(center):
                return center + HALF_WIDTH > lo and center - HALF_WIDTH < hi
            with self.subTest(lo=lo, hi=hi):
                self.assertTrue(supports(first) and supports(last))
                self.assertFalse(supports(math.nextafter(first, -math.inf)))
                self.assertFalse(supports(math.nextafter(last, math.inf)))

    def test_compiler_is_reusable_after_a_timeout(self):
        compiler = PythonCompiler()
        route = dict(sharedMovement=True, inputs=dict(sprint=True))
        plan = dict(grounded=[True] * 50, jump=[False] * 50)
        with mock.patch('time.monotonic', side_effect=[0., 5.]):
            with self.assertRaises(TimeoutError):
                compiler.compile(route, plan, 1)
        message = move([0., .7, 0.], [0., .7, 0.], start=(0., .5, 0.), grounded=False)
        with mock.patch('time.monotonic', side_effect=[0.] + [10.] * 5):
            with self.assertRaises(TimeoutError):
                compiler._exchange(message, 1)
        self.assertEqual(len(compiler.compile(route, plan, 5)['magnitude']), 50)

    def test_malformed_requests_are_value_errors(self):
        compiler = PythonCompiler()
        for message in [dict(action='footprints', blocks=[dict(top=0), dict(top=0)]),
                        dict(action='sceneCheck', scene=SCENE, positions=[[0, 0, 0]], movement=[[0, 0]],
                             expectedGround=[True, True]),
                        dict(grounded=[True], jump=[True], inputs=dict(forward=.5, strafe=0.))]:
            with self.subTest(message=message):
                self.assertRaises(ValueError, compiler._exchange, message, 5)

    def test_explicit_descriptor_overrides_the_environment_and_absence_selects_python(self):
        with tempfile.TemporaryDirectory() as directory:
            explicit, configured = Path(directory) / 'explicit.json', Path(directory) / 'configured.json'
            explicit.write_text(json.dumps(dict(command=[sys.executable, '-c', 'pass', 'explicit'])))
            configured.write_text(json.dumps(dict(command=[sys.executable, '-c', 'pass', 'configured'])))
            with mock.patch.dict(os.environ, {'PKC_RUN_TICKS_COMPILER': str(configured)}):
                with open_compiler(explicit) as compiler:
                    self.assertEqual(compiler.command[-1], 'explicit')
                with open_compiler() as compiler:
                    self.assertEqual(compiler.command[-1], 'configured')
            with mock.patch.dict(os.environ, {}, clear=True):
                with open_compiler() as compiler:
                    self.assertIsInstance(compiler, PythonCompiler)
                    self.assertEqual(compiler.provenance()['bridge'], 'python')


if __name__ == '__main__':
    unittest.main()
