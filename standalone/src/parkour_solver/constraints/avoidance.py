import math


AXES = ('X', 'Y', 'Z')
ORDER = {'Y': 0, 'X': 1, 'Z': 2}
COUNT_KINDS = ('horizontal', 'vertical', 'total')


def consecutive(first, second):
    return ORDER[first['axis']] > ORDER[second['axis']]


def phase_states(tick, phase, axis):
    """State coordinates of an ordinary Y/X/Z collision sweep."""
    if axis == 'X':
        return [tick] if phase == 'Y' else [tick, tick+1] if phase == 'X' else [tick+1]
    if axis == 'Z':
        return [tick, tick+1] if phase == 'Z' else [tick]
    return [tick, tick+1] if phase == 'Y' else [tick+1]


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
        counts = usage.setdefault(block_key(passage), dict.fromkeys(COUNT_KINDS, 0))
        for key, value in blockage_counts(passage['faces']).items():
            counts[key] += value
    return usage


def gate_timeline(passages, events):
    timeline = []
    for passage in passages:
        if 'phaseSpans' in passage:
            phases = ('Y', 'X', 'Z')
            for i, (first, second, at) in enumerate(zip(passage['faces'], passage['faces'][1:], passage['switchPhases'])):
                if 'phaseReferences' in passage:
                    reference = passage['phaseReferences'][i]
                    at = 3*(events[reference['leg']]['jump']+reference['offset'])+reference['phase']
                previous = max(0, at-1)
                axes = lambda face: ('X', 'Z') if face['axis'] == 'XZ' else (face['axis'],)
                timeline.append(dict(blockId=passage['blockId'], leg=passage['leg'],
                    firstState=max(s for a in axes(first) for s in phase_states(previous//3, phases[previous%3], a)),
                    secondState=min(s for a in axes(second) for s in phase_states(at//3, phases[at%3], a)),
                    phase=at, type=first['axis']+'->'+second['axis'],
                    kind='vertical' if 'Y' in (first['axis'], second['axis']) else 'horizontal'))
            continue
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


def validate_passages(route):
    # Source-era route files may carry these fields. They no longer restrict
    # avoidance; explicit authored passage rows retain their existing meaning.
    route.pop('passageCaps', None)
    passages = route.setdefault('passages', [])
    if not isinstance(passages, list):
        raise ValueError('Passages must be a list')
    for passage in passages:
        leg = passage.get('leg') if isinstance(passage, dict) else None
        if type(leg) is not int or not 0 <= leg < len(route['airtimes']):
            raise ValueError('Invalid passage leg')
        chain, gates = passage.get('faces'), passage.get('gates')
        if not isinstance(chain, list) or not 1 <= len(chain) or not isinstance(gates, list) or len(gates) != len(chain) - 1:
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
