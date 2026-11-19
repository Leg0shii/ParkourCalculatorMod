"""Finite-scene collision replay of a continuous candidate.

A port of the standalone bridge's `sceneCheck` and the core `StaticScene` it
uses: one sweep per tick, Y then X then Z clipping in solid order, sneak edge
reduction, the two step-up alternatives and ladder carry. The Java bridge
remains the differential oracle; replies keep its fields and order.
"""
import math
import time

from parkour_solver.physics.vertical import CAP, GRAVITY, Y_DRAG, f32
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.scene.route import HALF_WIDTH
from parkour_solver.schema import TOLERANCE

SCENE_FIELDS = {'version', 'profile', 'bounds', 'solids', 'ladders', 'surfaces', 'stepHeight', 'metadata'}
STEP_HEIGHT = f32(0.6)


def _number(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError('Nonfinite scene number')
    return float(value)


def _box(values):
    if not isinstance(values, list) or len(values) != 6:
        raise ValueError('A box needs six finite bounds')
    box = tuple(_number(value) for value in values)
    if not (box[0] < box[3] and box[1] < box[4] and box[2] < box[5]):
        raise ValueError('A box must have positive volume')
    return box


def _cell(values):
    if not isinstance(values, list) or len(values) != 3:
        raise ValueError('A cell needs three integer coordinates')
    cell = tuple(_number(value) for value in values)
    if any(not value.is_integer() for value in cell):
        raise ValueError('A cell coordinate must be an integer')
    return tuple(map(int, cell))


def _contains(outer, inner):
    return (inner[0] >= outer[0] and inner[3] <= outer[3] and inner[1] >= outer[1]
            and inner[4] <= outer[4] and inner[2] >= outer[2] and inner[5] <= outer[5])


def _offset(box, x, y, z):
    return (box[0] + x, box[1] + y, box[2] + z, box[3] + x, box[4] + y, box[5] + z)


def _sweep(box, x, y, z):
    return (box[0] + min(0., x), box[1] + min(0., y), box[2] + min(0., z),
            box[3] + max(0., x), box[4] + max(0., y), box[5] + max(0., z))


def _center(box):
    return ((box[0] + box[3]) / 2, box[1], (box[2] + box[5]) / 2)


def _clip(obstacles, body, value, axis, clips=None):
    first, second = (axis + 1) % 3, (axis + 2) % 3
    for obstacle in obstacles:
        if (body[first + 3] <= obstacle[first] or body[first] >= obstacle[first + 3]
                or body[second + 3] <= obstacle[second] or body[second] >= obstacle[second + 3]):
            continue
        requested = value
        if value > 0 and body[axis + 3] <= obstacle[axis]:
            gap = obstacle[axis] - body[axis + 3]
            if gap < value:
                value = gap
        elif value < 0 and body[axis] >= obstacle[axis + 3]:
            gap = obstacle[axis + 3] - body[axis]
            if gap > value:
                value = gap
        if clips is not None and value != requested:
            clips.append((obstacle, axis, requested, value))
    return value


def _edge_step(value):
    if -0.05 <= value < 0.05:
        return 0.
    return value - 0.05 if value > 0 else value + 0.05


class Motion:
    __slots__ = ('box', 'position', 'velocity', 'on_ground', 'vertical', 'stepped', 'blocked_x', 'blocked_z',
                 'clips', 'step_triggers')

    def __init__(self, box, velocity, on_ground, blocked_x, blocked_z, vertical, stepped, clips, step_triggers):
        self.box, self.position, self.velocity = box, _center(box), velocity
        self.on_ground, self.vertical, self.stepped = on_ground, vertical, stepped
        self.blocked_x, self.blocked_z = blocked_x, blocked_z
        self.clips, self.step_triggers = clips, step_triggers


class StaticScene:
    """Validated supplied collision scene (`version` 1, `minecraft-1.8.9`)."""

    def __init__(self, scene):
        if not isinstance(scene, dict):
            raise ValueError('Expected a collision scene object')
        for key in scene:
            if key not in SCENE_FIELDS:
                raise ValueError('Unsupported scene field: ' + key)
        if scene.get('version') != 1 or scene.get('profile') != 'minecraft-1.8.9':
            raise ValueError('Expected scene version 1, minecraft-1.8.9')
        self.bounds = _box(scene.get('bounds'))
        self.step_height = f32(_number(scene['stepHeight'])) if 'stepHeight' in scene else STEP_HEIGHT
        if self.step_height not in (0., STEP_HEIGHT):
            raise ValueError('stepHeight must be 0 or the widened float 0.6F')
        if not isinstance(scene.get('solids'), list):
            raise ValueError('A scene needs a solids list')
        self.solids = []
        for values in scene['solids']:
            box = _box(values)
            if not _contains(self.bounds, box):
                raise ValueError('Solid outside scene bounds')
            self.solids.append(box)
        self.solid_set = set(self.solids)
        self.ladders = {_cell(values) for values in scene.get('ladders') or []}
        for surface in scene.get('surfaces') or []:
            slip = _number(surface.get('slipperiness') if isinstance(surface, dict) else None)
            if not 0 < slip <= 1:
                raise ValueError('Invalid slipperiness')
            _cell(surface.get('cell'))

    def ladder(self, position):
        return bool(self.ladders) and tuple(math.floor(value) for value in position) in self.ladders

    def collisions(self, area):
        if not _contains(self.bounds, area):
            raise ValueError('Replay collision query leaves the supplied scene bounds')
        return [solid for solid in self.solids if solid[0] < area[3] and solid[3] > area[0] and solid[1] < area[4]
                and solid[4] > area[1] and solid[2] < area[5] and solid[5] > area[2]]

    def start_box(self, position):
        x, y, z = position
        box = (x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + BODY_HEIGHT, z + HALF_WIDTH)
        if not _contains(self.bounds, box):
            raise ValueError('Player starts outside scene bounds')
        if self.collisions(box):
            raise ValueError('Player starts inside a solid')
        return box

    def move(self, original, velocity, grounded, sneak):
        x, y, z = velocity
        expected_x, expected_y, expected_z = x, y, z
        if grounded and sneak:
            while x != 0 and not self.collisions(_offset(original, x, -1., 0.)):
                expected_x = x = _edge_step(x)
            while z != 0 and not self.collisions(_offset(original, 0., -1., z)):
                expected_z = z = _edge_step(z)
            while x != 0 and z != 0 and not self.collisions(_offset(original, x, -1., z)):
                expected_x = x = _edge_step(x)
                expected_z = z = _edge_step(z)
        obstacles = self.collisions(_sweep(original, x, y, z))
        clips, triggers = [], []
        y = _clip(obstacles, original, y, 1, clips)
        moved = _offset(original, 0., y, 0.)
        can_step = grounded or expected_y != y and expected_y < 0
        x = _clip(obstacles, moved, x, 0, clips)
        moved = _offset(moved, x, 0., 0.)
        z = _clip(obstacles, moved, z, 2, clips)
        moved = _offset(moved, 0., 0., z)
        stepped = False
        if self.step_height > 0 and can_step and (expected_x != x or expected_z != z):
            steps = self.collisions(_sweep(original, expected_x, self.step_height, expected_z))
            clips_a, clips_b = [], []
            rise_a = _clip(steps, _sweep(original, expected_x, 0., expected_z), self.step_height, 1)
            a = _offset(original, 0., rise_a, 0.)
            ax = _clip(steps, a, expected_x, 0, clips_a)
            a = _offset(a, ax, 0., 0.)
            az = _clip(steps, a, expected_z, 2, clips_a)
            a = _offset(a, 0., 0., az)
            rise_b = _clip(steps, original, self.step_height, 1)
            b = _offset(original, 0., rise_b, 0.)
            bx = _clip(steps, b, expected_x, 0, clips_b)
            b = _offset(b, bx, 0., 0.)
            bz = _clip(steps, b, expected_z, 2, clips_b)
            b = _offset(b, 0., 0., bz)
            use_a = ax * ax + az * az > bx * bx + bz * bz
            step_box, sx, sz = (a, ax, az) if use_a else (b, bx, bz)
            step_clips = clips_a if use_a else clips_b
            sy = _clip(steps, step_box, -(rise_a if use_a else rise_b), 1, step_clips)
            step_box = _offset(step_box, 0., sy, 0.)
            if x * x + z * z < sx * sx + sz * sz:
                moved, x, y, z, stepped = step_box, sx, sy, sz, True
                triggers, clips = clips, step_clips
        blocked_x, blocked_z, vertical = expected_x != x, expected_z != z, expected_y != y
        carry = [0. if blocked_x else velocity[0], 0. if vertical else velocity[1], 0. if blocked_z else velocity[2]]
        if (blocked_x or blocked_z) and self.ladder(_center(moved)):
            carry[1] = 0.2
        return Motion(moved, carry, vertical and expected_y < 0, blocked_x, blocked_z, vertical, stepped,
                      clips, triggers)


def _vector(values):
    if not isinstance(values, (list, tuple)) or len(values) != 3:
        raise ValueError('Expected X/Y/Z')
    for value in values:
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError('Nonfinite scene check')
    return tuple(float(value) for value in values)


def _member(scene, values):
    if not isinstance(values, list) or len(values) != 6:
        return None
    try:
        box = tuple(float(value) for value in values)
    except (TypeError, ValueError):
        return None
    return box if box in scene.solid_set else None


def _same_face(box, contact, expected, before, hit):
    axis, direction, plane = contact.get('axis', 0), contact.get('direction', 0), contact.get('plane', 0.)
    width = BODY_HEIGHT if axis == 1 else HALF_WIDTH
    lo, hi = (box[0], box[3]) if axis == 0 else (box[2], box[5]) if axis == 2 else (box[1], box[4])
    actual = lo - width if direction > 0 else hi + (0 if axis == 1 else width)
    x = hit.position[0] if axis == 2 else (before[0] + before[3]) / 2
    y = hit.position[1]
    z = (before[2] + before[5]) / 2
    expected_plane = expected[axis] - width if direction > 0 else expected[axis + 3] + (0 if axis == 1 else width)
    overlap_x = x + HALF_WIDTH > expected[0] and x - HALF_WIDTH < expected[3]
    overlap_y = y + BODY_HEIGHT > expected[1] and y < expected[4]
    overlap_z = z + HALF_WIDTH > expected[2] and z - HALF_WIDTH < expected[5]
    return actual == plane and expected_plane == plane and (
        overlap_y and overlap_z if axis == 0 else overlap_x and overlap_y if axis == 2 else overlap_x and overlap_z)


def _matches_face(scene, box, contact, before, hit):
    if contact.get('boxes') is None:
        return True
    for values in contact['boxes']:
        member = _member(scene, values)
        if member is not None and (member == box or _same_face(box, contact, member, before, hit)):
            return True
    return False


def _contact_mismatch(scene, events, move, before, hit):
    if hit.stepped and not any(event.get('step', False) or event.get('movementCase') is not None for event in events):
        return True
    if not hit.stepped and hit.vertical and move[1] > 0 and not any(event.get('axis', 0) == 1 for event in events):
        return True
    axes = {event.get('axis', 0) for event in events}
    if hit.blocked_x != (0 in axes) or hit.blocked_z != (2 in axes):
        return True
    for event in events:
        axis, plane = event.get('axis', 0), event.get('plane', 0.)
        if event.get('movementCase') is not None:
            trigger = any(member is not None and any(clip_axis != 1 and obstacle == member
                                                     for obstacle, clip_axis, _, _ in hit.step_triggers)
                          for member in (_member(scene, values) for values in event.get('boxes') or []))
            if not hit.stepped or not trigger or axis != 1 or abs(hit.position[1] - plane) > TOLERANCE:
                return True
            continue
        if event.get('step', False):
            box = hit.box
            supported = any(member is not None and member[4] == plane and box[3] > member[0] and box[0] < member[3]
                            and box[5] > member[2] and box[2] < member[5]
                            for member in (_member(scene, values) for values in event.get('boxes') or []))
            if (not hit.stepped or axis != 1 or event.get('direction', 0) != -1 or not supported
                    or abs(hit.position[1] - plane) > TOLERANCE):
                return True
            continue
        last = None
        for clip in hit.clips:
            if clip[1] == axis:
                last = clip
        if (last is None or last[2] == last[3] or last[2] * event.get('direction', 0) <= 0
                or abs(hit.position[axis] - plane) > TOLERANCE
                or not _matches_face(scene, last[0], event, before, hit)):
            return True
    return False


def check_scene(request, deadline=None):
    """Answer one `sceneCheck` bridge message; `deadline` is a `time.monotonic()` limit."""
    scene = StaticScene(request.get('scene'))
    positions, movement, ground = request.get('positions'), request.get('movement'), request.get('expectedGround')
    if not isinstance(movement, list) or not isinstance(positions, list) or not isinstance(ground, list):
        raise ValueError('Scene check needs positions, movement and expectedGround')
    n = len(movement)
    if len(positions) != n + 1 or len(ground) != n + 1:
        raise ValueError('Scene check arrays have inconsistent lengths')
    sneak, ladder = request.get('sneak'), request.get('ladderTicks')
    velocities, friction = request.get('velocities'), request.get('friction')
    contacts = {}
    for event in request.get('contacts') or []:
        contacts.setdefault(event.get('state', 0), []).append(event)
    body = scene.start_box(_vector(positions[0]))
    if ladder is not None and (len(ladder) != n + 1 or scene.ladder(_vector(positions[0])) != ladder[0]):
        return dict(feasible=False, reason='initial_ladder_membership')
    for t in range(n):
        if deadline is not None and time.monotonic() > deadline:
            raise TimeoutError('Scene check exceeded the budget')
        move = movement[t]
        sneaking = sneak is not None and sneak[t]
        if scene.ladder(_center(body)) and (abs(move[0]) > CAP or abs(move[2]) > CAP or move[1] < -.15
                                            or sneaking and move[1] < 0):
            return dict(feasible=False, tick=t, reason='ladder_cap_contract')
        try:
            hit = scene.move(body, _vector(move), ground[t], sneaking)
        except ValueError as error:
            return dict(feasible=False, tick=t, reason='scene_query', message=str(error))
        expected = _vector(positions[t + 1])
        position = hit.position
        error = max(abs(position[0] - expected[0]), abs(position[1] - expected[1]), abs(position[2] - expected[2]))
        occupancy = ladder is not None and scene.ladder(position) != ladder[t + 1]
        velocity_error = 0.
        if velocities is not None:
            target = _vector(velocities[t + 1])
            vx, vy, vz = hit.velocity
            velocity_error = max(abs(vx * friction[t] - target[0]), abs((vy - GRAVITY) * Y_DRAG - target[1]),
                                 abs(vz * friction[t] - target[2]))
        mismatch = _contact_mismatch(scene, contacts.get(t + 1, ()), move, body, hit)
        if mismatch or occupancy or velocity_error > TOLERANCE or error > TOLERANCE or hit.on_ground != ground[t + 1]:
            diagnostics = hit.step_triggers if hit.stepped else hit.clips
            last = {clip[1]: i for i, clip in enumerate(diagnostics)}
            clips = []
            for i, (obstacle, axis, _, _) in enumerate(diagnostics):
                if i != last[axis] or hit.stepped and axis == 1:
                    continue
                if axis == 1 and abs(position[1] - expected[1]) <= TOLERANCE and hit.on_ground == ground[t + 1]:
                    continue
                clips.append(dict(box=list(obstacle), axis=axis))
            return dict(feasible=False, tick=t, reason='unplanned_step' if hit.stepped else 'scene_collision',
                        positionError=error, contactMismatch=mismatch, occupancyMismatch=occupancy,
                        velocityError=velocity_error, actualPosition=list(position), clips=clips)
        body = hit.box
    return dict(feasible=True, ticks=n)
