import collections
import json
import math
import random
import sys
import time
from pathlib import Path

from parkour_solver.movement.normal import first_descent, replay
from parkour_solver.physics.vertical import REST_CARRY
from parkour_solver.reachability.service import InteractionGate
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.search.anchors.policy import launch
from parkour_solver.spatial.surfaces import scene_geometry


def per_tick_table(scene, owner):
    blocks = scene['blocks']
    geometry = scene_geometry(scene['collisionScene'])
    lowest = min(b['top'] for b in blocks)
    by_solid = collections.defaultdict(list)
    for b in blocks:
        by_solid[b['solidIndex']].append(b['id'])
    ticks = collections.defaultdict(set)
    for block in blocks:
        if block.get('sourceBlock', block['id']) != owner:
            continue
        start = dict(position=block['top'], velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)
        jump = replay(start, 200, jumping=True, threshold=0.)
        fall = replay(start, 200, jumping=False, threshold=0.)
        descent = first_descent(jump, lowest)
        flight = 200 if descent is None else descent+1
        own = geometry.boxes[block['solidIndex']]
        foot = [own[0], own[2], own[3], own[5]]
        widest = 1.1+.4*(flight-1)
        for i in geometry.near([foot[0]-widest, foot[1]-widest, foot[2]+widest, foot[3]+widest]):
            box = geometry.boxes[i]
            distance = math.hypot(max(0., box[0]-foot[2], foot[0]-box[3]), max(0., box[2]-foot[3], foot[1]-box[5]))
            for t in range(flight):
                low = min(fall['position'][t], fall['position'][t+1])
                high = max(jump['position'][t], jump['position'][t+1])+.6
                if distance <= 1.1+.4*t and box[1] <= high+BODY_HEIGHT and low <= box[4]:
                    for name in by_solid[i]:
                        ticks[name].add(t)
    return {name: sorted(v) for name, v in ticks.items()}


def owners(scene):
    return sorted({b.get('sourceBlock', b['id']) for b in scene['blocks']})


def table_sizes(scene):
    gate = InteractionGate(scene, None)
    rows = []
    for owner in owners(scene):
        started = time.process_time()
        coarse = gate.reachable_blocks(owner)['blocks']
        coarse_seconds = time.process_time()-started
        started = time.process_time()
        fine = per_tick_table(scene, owner)
        fine_seconds = time.process_time()-started
        rows.append(dict(owner=owner, blocks=len(coarse), perTickBlocks=len(fine),
                         blockTicks=sum(len(v) for v in fine.values()),
                         seconds=coarse_seconds, perTickSeconds=fine_seconds))
    return rows


def instrumented_real11(output):
    from parkour_solver.reachability import service
    from parkour_solver.search import engine
    questions = collections.Counter()
    items = collections.Counter()
    cache = {}

    def table(scene, owner):
        key = (id(scene['blocks']), owner)
        if key not in cache:
            cache[key] = per_tick_table(scene, owner)
        return cache[key]
    query = service.InteractionGate.query

    def counted(self, context, bounds, tick, **kwargs):
        proof = query(self, context, bounds, tick, **kwargs)
        found = launch(self.scene, context['program'])
        questions['total'] += 1
        if found is None:
            return proof
        at = context['program']['profile']['airtime']+tick-found[0]
        names = kwargs['label']['owner'].split('+')
        fine = table(self.scene, found[1])
        if all(at not in fine.get(name, ()) for name in names):
            questions['perTickExcluded'] += 1
            questions['perTickExcludedGateKept' if not proof['ruledOut'] else 'perTickExcludedGateRuledOut'] += 1
        return proof
    service.InteractionGate.query = counted
    segment_solids = engine.segment_solids

    def counted_segments(scene, program, profile, through, gate):
        result = segment_solids(scene, program, profile, through, gate)
        geometry = scene_geometry(scene['collisionScene'])
        names = {b['solidIndex']: b['id'] for b in scene['blocks']}
        clusters = geometry.clusters(range(len(scene['collisionScene']['solids'])))
        from parkour_solver.constraints.encounters import landing_segments
        for begin, end in landing_segments(profile, through):
            if begin not in result:
                continue
            tick, owner = launch(scene, program, begin, end)
            fine = table(scene, owner)
            for c in clusters:
                if set(c.members).isdisjoint(result[begin]):
                    continue
                items['kept'] += 1
                if not any(t in fine.get(names.get(i), ()) for i in c.members for t in range(begin-tick, end-tick)):
                    items['perTickExcluded'] += 1
        return result
    engine.segment_solids = counted_segments
    sys.argv = ['run', '--input', 'cases/real11/physical-anchors.json', '--output', str(output), '--seconds', '300',
                '--profile-mode', 'none']
    from tools.profiling import run
    run.main()
    return dict(questions=dict(questions), encounterItems=dict(items))


if __name__ == '__main__':
    out = Path(sys.argv[1])
    from parkour_solver.scene.normalize import prepare_scene
    from tests.support.block_table import random_box_request
    real11 = prepare_scene(json.loads(Path('cases/real11/physical-anchors.json').read_text())['request'])
    rng = random.Random(20261118)
    scenes = [('real11', real11)]+[('random%d' % i, prepare_scene(random_box_request(rng))) for i in range(20)]
    result = dict(tables={name: table_sizes(scene) for name, scene in scenes})
    result['real11Run'] = instrumented_real11(out.parent/'per-tick-run')
    out.write_text(json.dumps(result, indent=1))
