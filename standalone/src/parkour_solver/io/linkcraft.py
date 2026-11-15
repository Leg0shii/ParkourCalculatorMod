import argparse
import hashlib
import json
import math
from pathlib import Path

from parkour_solver.scene.route import HALF_WIDTH, center_bounds, prepare_route, supports
from parkour_solver.physics.vertical import f32


def extract_section(capture, start, end, source_hash, expanded=False):
    states, rows = capture['debug'], capture['rows']
    if capture.get('mcVersion') != '1.8.9' or not 0 <= start < end < len(states):
        raise ValueError('Expected a captured Minecraft 1.8.9 section')
    if capture['angleSolver'].get('defaultInputs') != 'FORCE_45':
        raise ValueError('This extractor requires the captured Force 45 policy')
    for t in range(start, end):
        if rows[t].get('speedAmplifier', 0) or rows[t].get('slownessAmplifier', 0):
            raise ValueError('Movement effects require an extended coefficient compiler')
    table = {entry['tick']: entry for entry in capture['angleSolver']['ticks']}
    for t, entry in table.items():
        if not start <= t < end:
            continue
        override = entry.get('override', {})
        if override.get('inputs', 'FORCE_45') != 'FORCE_45':
            raise ValueError('Per-tick movement policies require an extended extractor')
        if override.get('slipperiness', 'AIR') not in ('AIR', 'DEFAULT') or override.get('medium', 'NONE') != 'NONE':
            raise ValueError('Special movement surfaces require an extended extractor')

    def region(tick, required=True):
        bounds = {}
        for c in table.get(tick, {}).get('constraints', []):
            if c.get('disabled') or not c.get('range') or c.get('field') not in ('X', 'Z'):
                continue
            axis = c['field']
            lo, hi = bounds.get(axis, (-float('inf'), float('inf')))
            bounds[axis] = max(lo, c['lo']), min(hi, c['hi'])
        if set(bounds) != {'X', 'Z'}:
            if not required:
                return None
            raise ValueError('No complete authored landing region at source state ' + str(tick))
        return list(bounds['X'] + bounds['Z'])

    if not states[start]['onGround'] or not states[end]['onGround']:
        raise ValueError('Section boundaries must be grounded')
    inferred = []

    def block_at(tick, state, top, name):
        found = region(tick, required=not expanded)
        if found is not None:
            return {'id': name, 'centerBounds': found, 'top': top}
        x, _, z = states[state]['pos']
        bx, bz = math.floor(x), math.floor(z)
        inferred.append({'regionTick': tick, 'witnessState': state, 'kind': 'full-grid-block'})
        return {'id': name, 'bounds': [bx, bx + 1, bz, bz + 1], 'top': top}

    def outside_face(block, point):
        bounds = center_bounds(block)
        x, _, z = point
        options = [('X', -1, bounds[0] - x), ('X', 1, x - bounds[1]),
                   ('Z', -1, bounds[2] - z), ('Z', 1, z - bounds[3])]
        axis, direction, gap = max(options, key=lambda item: item[2])
        return {'axis': axis, 'direction': direction} if gap >= -1e-7 else None

    standing_extensions = []

    def extend_standing(block, first, departure):
        points = [states[t]['pos'] for t in range(first, departure)]
        if not points or all(supports(block, p[0], p[2]) for p in points):
            return
        if not expanded:
            raise ValueError('Run-up leaves the supplied support region')
        b = center_bounds(block)
        raw = [b[0] + HALF_WIDTH, b[1] - HALF_WIDTH, b[2] + HALF_WIDTH, b[3] - HALF_WIDTH]
        if any(abs(v - round(v)) > 1e-5 for v in raw) or raw[0] >= raw[1] or raw[2] >= raw[3]:
            raise ValueError('Cannot infer a coplanar strip from a non-grid landing region')
        raw = [float(round(v)) for v in raw]
        in_x = all(p[0] + HALF_WIDTH > raw[0] and p[0] - HALF_WIDTH < raw[1] for p in points)
        in_z = all(p[2] + HALF_WIDTH > raw[2] and p[2] - HALF_WIDTH < raw[3] for p in points)
        if not in_x and not in_z:
            raise ValueError('A two-axis support union needs explicit block geometry')
        axis = 2 if in_x else 0
        for p in points:
            value = p[2] if axis == 2 else p[0]
            while value + HALF_WIDTH <= raw[axis]:
                raw[axis] -= 1
            while value - HALF_WIDTH >= raw[axis + 1]:
                raw[axis + 1] += 1
        block['standingBounds'] = raw
        standing_extensions.append({'block': block['id'], 'states': [first, departure - 1],
                                    'standingBounds': raw, 'kind': 'grid-aligned coplanar strip from ground samples'})

    initial = states[start]
    blocks = [block_at(start - 1, start, initial['pos'][1], 'source:' + str(start))]
    departures, landings, run_counts, airtimes, kinds, exits, phases, entries = [], [], [], [], [], [], [], []
    cursor = start
    while cursor < end:
        departure = next((t for t in range(cursor, end)
                          if states[t]['onGround'] and not states[t + 1]['onGround']), None)
        step = next((t for t in range(cursor, end)
                     if states[t]['onGround'] and states[t + 1]['onGround']
                     and abs(states[t + 1]['pos'][1] - states[t]['pos'][1]) > 1e-7), None)
        is_step = step is not None and (departure is None or step < departure)
        if is_step:
            if not expanded:
                raise ValueError('A grounded height/contact change requires an explicit transition')
            departure = step
        if departure is None:
            raise ValueError('Trailing grounded movement is outside this section extractor')
        if any(not states[t]['onGround'] or abs(states[t]['pos'][1] - states[cursor]['pos'][1]) > 1e-7
               for t in range(cursor, departure + 1)):
            raise ValueError('A grounded height/contact change requires an explicit transition')
        landing = departure + 1 if is_step else next((t for t in range(departure + 1, end + 1) if states[t]['onGround']), None)
        if landing is None:
            raise ValueError('Transition lands beyond the section')
        kind = 'step' if is_step else 'jump' if 'JUMP' in rows[departure]['keys'] else 'walk_off'
        top = states[landing]['pos'][1]
        post = is_step or states[landing - 1]['pos'][1] < top - 1e-7
        phase = 'post' if post else 'pre'
        target = block_at(landing if post else landing - 1, landing if post else landing - 1,
                          top, 'source:' + str(landing))
        extend_standing(blocks[-1], cursor, departure)
        exit_face = None
        if kind == 'walk_off':
            exit_face = outside_face(blocks[-1], states[departure]['pos'])
            if exit_face is None:
                raise ValueError('Recorded run-off is still inside the declared support region')
        entry = None
        if expanded and (is_step or (kind == 'jump' and
                         abs(states[departure + 1]['pos'][1] - states[departure]['pos'][1] - f32(.42)) > 1e-5)):
            face = outside_face(target, states[departure]['pos'])
            if face is not None and supports(target, states[departure + 1]['pos'][0], states[departure + 1]['pos'][2]):
                entry = dict(face, outsideFrom=0, outsideThrough=0, enter=1)
        if expanded and post and not is_step:
            crossing = next((t for t in range(departure, landing)
                             if states[t]['pos'][1] >= top and states[t + 1]['pos'][1] < top), None)
            if crossing is None:
                raise ValueError('No known pre-step floor crossing')
            face = outside_face(target, states[crossing]['pos'])
            if face is None:
                raise ValueError('Post-move landing needs an explicit approach region')
            entry = dict(face, outsideFrom=crossing - departure,
                         outsideThrough=landing - departure - 1, enter=landing - departure)
        departures.append(departure)
        landings.append(landing)
        run_counts.append(departure - cursor)
        airtimes.append(landing - departure)
        kinds.append(kind)
        exits.append(exit_face)
        phases.append(phase)
        entries.append(entry)
        blocks.append(target)
        cursor = landing
    jumps = [t for t, row in enumerate(rows) if 'JUMP' in row['keys'] and states[t]['onGround']]
    return prepare_route({'version': 1, 'initial': {'position': initial['pos'][:],
                                                  'velocity': initial['vel'][:], 'yaw': 0},
                          'blocks': blocks, 'airtimes': airtimes, 'kinds': kinds, 'walkOffExits': exits,
                          'landingPhases': phases, 'contactEntries': entries,
                          'inputs': {'force45': True, 'sprint': True,
                                     'incomingSprint': states[max(0, start - 1)]['sprinting']},
                          'maxTotalRunTicks': 12,
                          'runBounds': [[0, 6] for _ in airtimes],
                          'source': {'name': 'Linkcraft 11', 'sha256': source_hash,
                                     'startState': start, 'endState': end,
                                     'departures': departures, 'landings': landings,
                                     'jumpIndices': [jumps.index(t) if t in jumps else None for t in departures],
                                     'recordedAllocation': run_counts, 'recordedFacingsUsed': False,
                                     'authoredScalarRulesUsed': False,
                                     'inferredFullBlockRegions': inferred,
                                     'standingSupportExtensions': standing_extensions,
                                     'mandatoryGroundSteps': sum(k == 'step' for k in kinds),
                                     'geometry': 'authored landing center regions, no picked world geometry'}})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--start', type=int, default=55)
    parser.add_argument('--end', type=int, default=80)
    parser.add_argument('--expanded', action='store_true')
    args = parser.parse_args()
    data = Path(args.input).read_bytes()
    route = extract_section(json.loads(data), args.start, args.end, hashlib.sha256(data).hexdigest(), args.expanded)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(route, indent=2, allow_nan=False) + '\n')
    print(json.dumps(route['source']))


if __name__ == '__main__':
    main()
