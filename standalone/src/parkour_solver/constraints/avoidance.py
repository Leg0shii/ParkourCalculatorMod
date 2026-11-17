import heapq
import itertools
import math
import time

from parkour_solver.scene.geometry import expanded_box
from parkour_solver.scene.route import center_bounds


AXES = ('X', 'Y', 'Z')
ORDER = {'Y': 0, 'X': 1, 'Z': 2}
DEFAULT_CAPS = {'horizontal': 2, 'vertical': 2, 'total': 3}


def consecutive(first, second):
    return ORDER[first['axis']] > ORDER[second['axis']]


def faces(box):
    bounds = expanded_box(box)
    return [{'axis': axis, 'direction': sign, 'plane': bounds[i + (3 if sign > 0 else 0)]}
            for i, axis in enumerate(AXES) for sign in (-1, 1)]


def outside(face, point, clearance=0):
    return face['direction'] * (point[AXES.index(face['axis'])] - face['plane']) >= clearance


def passage_kind(chain):
    count = blockage_counts(chain)['horizontal']
    return 'cross_neo' if count == 1 else 'wide_neo' if count == 2 else 'vertical_passage' if count == 0 else 'long_neo'


def blockage_counts(chain):
    horizontal = sum(a['axis'] != 'Y' and b['axis'] != 'Y' for a, b in zip(chain, chain[1:]))
    return {'horizontal': horizontal, 'vertical': len(chain) - 1 - horizontal, 'total': len(chain) - 1}


def block_key(passage):
    return ('block', passage['blockId']) if 'blockId' in passage else ('solid', passage.get('solidIndex'))


def blockage_usage(passages):
    usage = {}
    for passage in passages:
        counts = usage.setdefault(block_key(passage), dict.fromkeys(DEFAULT_CAPS, 0))
        for key, value in blockage_counts(passage['faces']).items():
            counts[key] += value
    return usage


def within_caps(passages, caps):
    return all(all(counts[key] <= caps[key] for key in DEFAULT_CAPS) for counts in blockage_usage(passages).values())


def gate_timeline(passages, events):
    timeline = []
    for passage in passages:
        if passage['leg'] >= len(events):
            continue
        event = events[passage['leg']]
        if event['landing'] is None:
            continue
        for first, second, offset in zip(passage['faces'], passage['faces'][1:], passage['gates']):
            state = event['jump'] + offset
            timeline.append({'blockId': passage.get('blockId', 'solid:' + str(passage.get('solidIndex'))),
                             'solidIndex': passage.get('solidIndex'), 'leg': passage['leg'],
                             'firstState': state, 'secondState': state + int(consecutive(first, second)),
                             'type': first['axis'] + '->' + second['axis'],
                             'kind': 'vertical' if 'Y' in (first['axis'], second['axis']) else 'horizontal'})
    return sorted(timeline, key=lambda gate: (gate['firstState'], gate['secondState'], gate['blockId'], gate['leg']))


def intervals(chain, gates, begin, end):
    start = begin
    result = []
    for i, gate in enumerate(gates):
        if gate < start or gate > end:
            return None
        result.append((start, gate))
        start = gate + int(consecutive(chain[i], chain[i + 1]))
    if start > end:
        return None
    result.append((start, end))
    return result


def face_chains(length):
    yield from itertools.permutations(range(6), length + 1)
    if length >= 2:
        for prefix in itertools.permutations(range(6), length):
            yield prefix + (prefix[0],)


def validate_passages(route):
    caps = route.setdefault('passageCaps', DEFAULT_CAPS.copy())
    if not isinstance(caps, dict) or set(caps) != set(DEFAULT_CAPS):
        raise ValueError('Supply horizontal, vertical and total passage caps')
    if any(type(caps[key]) is not int or not 0 <= caps[key] <= maximum for key, maximum in DEFAULT_CAPS.items()):
        raise ValueError('Passage caps cannot exceed two horizontal, two vertical and three total per block')
    passages = route.setdefault('passages', [])
    if not isinstance(passages, list):
        raise ValueError('Passages must be a list')
    for passage in passages:
        leg = passage.get('leg') if isinstance(passage, dict) else None
        if type(leg) is not int or not 0 <= leg < len(route['airtimes']):
            raise ValueError('Invalid passage leg')
        chain, gates = passage.get('faces'), passage.get('gates')
        if not isinstance(chain, list) or not 1 <= len(chain) <= 5 or not isinstance(gates, list) or len(gates) != len(chain) - 1:
            raise ValueError('Invalid blockage chain')
        for face in chain:
            if not isinstance(face, dict) or face.get('axis') not in AXES or type(face.get('direction')) is not int or face['direction'] not in (-1, 1):
                raise ValueError('Invalid blockage face')
            if type(face.get('plane')) not in (float, int) or not math.isfinite(face['plane']):
                raise ValueError('Invalid blockage plane')
        if any(a['axis'] == b['axis'] for a, b in zip(chain, chain[1:])):
            raise ValueError('A blockage joins faces on different axes')
        if any(type(k) is not int or not -1000 <= k <= route['airtimes'][leg] for k in gates):
            raise ValueError('Invalid blockage gate offset')
        if 'blockId' in passage and (not isinstance(passage['blockId'], str) or not passage['blockId']):
            raise ValueError('Invalid passage block ID')
    if not within_caps(passages, caps):
        raise ValueError('Selected passages exceed the per-block blockage caps')


def face_clearance(route, leg, offset, face, clearance):
    for hit in route.get('hitEvents', []) + route.get('contactEvents', []):
        if hit['leg'] == leg and hit['offset'] + 1 == offset and hit['axis'] == face['axis'] \
                and hit['plane'] == face['plane'] and hit['direction'] == -face['direction']:
            return 0.
    for episode in route.get('ladderEvents', []):
        wall, hits = episode['wall'], episode['schedule']['hit']
        if episode['leg'] == leg and 0 < offset <= len(hits) and hits[offset - 1] \
                and wall['axis'] == face['axis'] and wall['plane'] == face['plane'] and wall['direction'] == -face['direction']:
            return 0.
    return clearance


def compile_passages(route, events):
    rows, failures = [], []
    for passage in route['passages']:
        if passage['leg'] >= len(events):
            continue
        event = events[passage['leg']]
        if event['landing'] is None:
            continue
        ranges = intervals(passage['faces'], passage['gates'], -event['runTicks'], event['landing'] - event['jump'])
        if ranges is None:
            failures.append({'leg': passage['leg'], 'reason': 'passage_timing'})
            continue
        for face, (begin, end) in zip(passage['faces'], ranges):
            if face['axis'] == 'Y':
                continue
            for offset in range(begin, end + 1):
                rows.append({'mode': face['axis'], 't1': event['jump'] + offset,
                             'cmp': 'GE' if face['direction'] > 0 else 'LE',
                             'rhs': face['plane'] + face['direction'] * face_clearance(route, passage['leg'], offset, face, route['supportClearance']),
                             'name': 'blockage:' + str(passage.get('solidIndex'))})
    return rows, failures


def passage_y_valid(passage, event, profile):
    ranges = intervals(passage['faces'], passage['gates'], -event['runTicks'], profile['airtime'])
    if ranges is None:
        return False
    for face, (begin, end) in zip(passage['faces'], ranges):
        if face['axis'] == 'Y' and any(not outside(face, [0, profile['position'][max(0, t)], 0])
                                     for t in range(begin, end + 1)):
            return False
    return True


def passage_options(box, solid_index, leg, profile, route, plan, candidate, motion_bound,
                    *, max_blockages=3, max_horizontal=2, max_vertical=2,
                    max_candidates=128, deadline=None, block_id=None):
    retained = [p for p in route['passages'] if (p['leg'], p['solidIndex']) != (leg, solid_index)]
    key = ('block', block_id) if block_id is not None else ('solid', solid_index)
    used = blockage_usage(retained).get(key, dict.fromkeys(DEFAULT_CAPS, 0))
    caps = route.get('passageCaps', DEFAULT_CAPS)
    remaining = {key: min(caps[key], maximum) - used[key] for key, maximum in
                 [('horizontal', max_horizontal), ('vertical', max_vertical), ('total', max_blockages)]}
    event = plan['events'][leg]
    begin, end = -event['runTicks'], profile['airtime']
    offsets = list(range(begin, end + 1))
    clearance = max(route['supportClearance'], 2e-7)
    chain_faces = faces(box)
    source, target = center_bounds(route['blocks'][leg]), center_bounds(route['blocks'][leg + 1])
    reach_step = motion_bound['movementAfterSupportCheck']
    speed = motion_bound['storedSpeedNorm']
    origin = [source[0] - reach_step, source[1] + reach_step, source[2] - reach_step, source[3] + reach_step]
    if leg == 0:
        runs = min(route['runBounds'][0][1], route['maxTotalRunTicks'])
        radius = runs * reach_step
        px, _, pz = route['initial']['position']
        origin = [max(origin[0], px - radius), min(origin[1], px + radius),
                  max(origin[2], pz - radius), min(origin[3], pz + radius)]
        incoming = math.hypot(route['initial']['velocity'][0], route['initial']['velocity'][2])
        speed = incoming
        for _ in range(runs):
            incoming = (incoming + motion_bound['groundMagnitude']) * motion_bound['groundFriction']
            speed = max(speed, incoming)
    motion_key = ('jump:' if profile.get('jump', [True])[0] else 'run_off:') + str(end)
    coefficients = motion_bound.get('flightBounds', {}).get(motion_key)
    moves = []
    for tick in range(end):
        movement = speed + coefficients['magnitude'][tick] if coefficients else reach_step
        moves.append(movement)
        if coefficients:
            speed = movement * coefficients['friction'][tick]

    def travel(a, b):
        return sum(moves[t] if t >= 0 else reach_step for t in range(min(a, b), max(a, b)))

    points, envelopes = [], []
    for t in offsets:
        state = event['jump'] + t
        x, z = candidate['positions'][state]
        y = profile['position'][max(0, t)]
        points.append([x, y, z])
        forward, backward = travel(0, max(0, t)), travel(t, end - 1)
        envelopes.append([(max(origin[0] - forward, target[0] - backward), min(origin[1] + forward, target[1] + backward)),
                          (y, y),
                          (max(origin[2] - forward, target[2] - backward), min(origin[3] + forward, target[3] + backward))])
    valid, costs = [], []
    for face in chain_faces:
        axis = AXES.index(face['axis'])
        mask, penalty = [], []
        for offset, point, envelope in zip(offsets, points, envelopes):
            lo, hi = envelope[axis]
            edge = face['plane'] + (0 if axis == 1 else face['direction'] * face_clearance(route, leg, offset, face, clearance))
            mask.append(lo <= hi and (hi >= edge if face['direction'] > 0 else lo <= edge))
            penalty.append(max(0, face['direction'] * (face['plane'] - point[axis])) ** 2)
        valid.append([0] + list(itertools.accumulate(not v for v in mask)))
        costs.append([0.0] + list(itertools.accumulate(penalty)))

    def segment(face, start, stop):
        lo, hi = start - begin, stop - begin + 1
        if start > stop or valid[face][hi] != valid[face][lo]:
            return None
        return costs[face][hi] - costs[face][lo]

    def reachable(chain, gates):
        ranges = intervals(chain, gates, begin, end)
        limits = [[list(pair) for pair in envelope] for envelope in envelopes]
        for face, (start, stop) in zip(chain, ranges):
            axis = AXES.index(face['axis'])
            if axis == 1:
                continue
            for offset in range(start, stop + 1):
                edge = face['plane'] + face['direction'] * face_clearance(route, leg, offset, face, clearance)
                pair = limits[offset - begin][axis]
                if face['direction'] > 0:
                    pair[0] = max(pair[0], edge)
                else:
                    pair[1] = min(pair[1], edge)
        for axis in (0, 2):
            lo, hi = limits[0][axis]
            for i, offset in enumerate(offsets):
                if i:
                    reach = moves[offset - 1] if offset > 0 else reach_step
                    lo, hi = lo - reach, hi + reach
                lo, hi = max(lo, limits[i][axis][0]), min(hi, limits[i][axis][1])
                if lo > hi + 1e-8:
                    return False
        return True

    options, serial, generated, discarded, reach_pruned, budget_pruned = [], 0, 0, 0, 0, 0
    expansion_cap, expanded_total, incomplete = max_candidates * 128, 0, False
    for length in range(max(0, remaining['total'] + 1)):
        for indices in face_chains(length):
            if expanded_total >= expansion_cap or deadline is not None and time.monotonic() >= deadline:
                incomplete = True
                break
            if any(chain_faces[a]['axis'] == chain_faces[b]['axis'] for a, b in zip(indices, indices[1:])):
                continue
            chain = [chain_faces[i] for i in indices]
            counts = blockage_counts(chain)
            if any(counts[key] > remaining[key] for key in DEFAULT_CAPS):
                budget_pruned += 1
                continue
            if segment(indices[0], begin, begin) is None or segment(indices[-1], end, end) is None:
                continue
            states = [(0.0, 0, begin, ())]
            while states and expanded_total < expansion_cap:
                if deadline is not None and time.monotonic() >= deadline:
                    incomplete = True
                    break
                score, depth, start, gates = heapq.heappop(states)
                expanded_total += 1
                if depth == length:
                    tail = segment(indices[-1], start, end)
                    if tail is None:
                        continue
                    if not reachable(chain, gates):
                        reach_pruned += 1
                        continue
                    rank = score + tail + length * 1e-8
                    passage = {'leg': leg, 'solidIndex': solid_index,
                               'faces': chain, 'gates': list(gates), 'blockageCounts': counts,
                               'kind': passage_kind(chain), 'blockageCount': length,
                               'blockageTypes': [a['axis'] + '->' + b['axis'] for a, b in zip(chain, chain[1:])]}
                    if block_id is not None:
                        passage['blockId'] = block_id
                    serial += 1
                    generated += 1
                    item = (-rank, serial, passage)
                    if len(options) < max_candidates:
                        heapq.heappush(options, item)
                    elif item[0] > options[0][0]:
                        heapq.heapreplace(options, item)
                    continue
                next_face = indices[depth + 1]
                shift = int(consecutive(chain[depth], chain[depth + 1]))
                for gate in range(start, end + 1 - shift):
                    value = segment(indices[depth], start, gate)
                    if value is None or segment(next_face, gate + shift, gate + shift) is None:
                        continue
                    heapq.heappush(states, (score + value, depth + 1, gate + shift, gates + (gate,)))
                if len(states) > max_candidates * 16:
                    discarded += len(states) - max_candidates * 16
                    states = heapq.nsmallest(max_candidates * 16, states)
                    heapq.heapify(states)
            discarded += len(states)
            incomplete |= bool(states)
    ranked = sorted([(-rank, serial, passage) for rank, serial, passage in options])
    return {'options': [{'score': rank, 'passage': passage} for rank, _, passage in ranked],
            'generated': generated, 'candidateDrops': max(0, generated - len(options)),
            'timingPoolDrops': discarded,
            'generationIncomplete': incomplete,
            'reachPruned': reach_pruned,
            'budgetPruned': budget_pruned,
            'verticalFacesPruned': sum(valid[i][-1] == len(offsets) for i, f in enumerate(chain_faces) if f['axis'] == 'Y')}
