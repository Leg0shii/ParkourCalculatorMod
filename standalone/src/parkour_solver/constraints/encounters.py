"""Lazy phase covers for one box encounter in a selected landing segment.

The six separating faces and Minecraft's Y/X/Z sweeps remain authoritative in
avoidance/geometry/reachability. This module chooses only effective horizontal
requirements; fixed vertical clearance is a gap, never a face permutation.
"""
from parkour_solver.constraints.avoidance import faces, blockage_counts, passage_kind, phase_states
from parkour_solver.reachability.service import block_interaction
from parkour_solver.reachability.intervals import interaction_reachability
from parkour_solver.search.identity import digest


PHASES = ('Y', 'X', 'Z')


def canonical_rows(rows):
    bounds = {}
    for row in rows:
        t = row.get('t1', row.get('offset'))
        key = (row.get('leg', 0), t, row['mode'], row['cmp'])
        prior = bounds.get(key)
        if prior is None or (row['rhs'] > prior['rhs'] if row['cmp'] == 'GE' else row['rhs'] < prior['rhs']):
            bounds[key] = dict(row, leg=key[0], offset=t, t1=t)
    return [bounds[k] for k in sorted(bounds)]


def rows_entailed(envelopes, selected, required):
    bounds = {}
    for row in selected:
        t, axis = row.get('t1', row.get('offset')), row['mode']
        lo, hi = bounds.get((t, axis), envelopes['states'][t][axis])
        bounds[(t, axis)] = [max(lo, row['rhs']) if row['cmp'] == 'GE' else lo,
                             min(hi, row['rhs']) if row['cmp'] == 'LE' else hi]
    for row in required:
        t, axis = row.get('t1', row.get('offset')), row['mode']
        lo, hi = bounds.get((t, axis), envelopes['states'][t][axis])
        if (row['cmp'] == 'GE' and lo < row['rhs']) or (row['cmp'] == 'LE' and hi > row['rhs']):
            return False
    return True


def landing_segments(profile, through):
    # A query ending at an airborne head hit does not create a landing boundary.
    cuts = {0, through}
    cuts.update(e['offset']+1 for e in profile.get('contactEvents', [])
                if e.get('axis') == 'Y' and e.get('direction') == -1
                and e.get('role') in ('landing', 'step') and 0 < e['offset']+1 < through)
    ground = profile.get('grounded', [])
    cuts.update(t for t in range(1, min(through, len(ground))) if ground[t] and not ground[t-1])
    return list(zip(sorted(cuts), sorted(cuts)[1:]))


def boundary_faces(item, envelopes, tick):
    state = envelopes['states'][tick]
    result = []
    for i, face in enumerate(item['faces']):
        if face['axis'] not in state:
            continue
        lo, hi = state[face['axis']]
        if (hi >= face['plane'] if face['direction'] > 0 else lo <= face['plane']):
            result.append(i)
    return result


def support_faces(owner, item, envelopes, tick):
    """The finite contact region precedes that input's horizontal movement.

    Keep it as entry/exit geometry, never as a new position pin at tick+1.
    Post-contact travel envelopes can extend beyond the support's footprint.
    """
    event = next((e for e in owner.profile.get('contactEvents', []) if e.get('axis') == 'Y'
                  and e.get('direction') == -1 and e['offset']+1 == tick and 'box' in e), None)
    if event is None:
        return boundary_faces(item, envelopes, tick)
    from parkour_solver.scene.geometry import expanded_box
    b = expanded_box(event['box'])
    margin = max(owner.route['supportClearance'], 2e-7)
    region = dict(X=[b[0]+margin, b[3]-margin], Z=[b[2]+margin, b[5]-margin],
                  Y=[event['box'][4]]*2)
    return [i for i, f in enumerate(item['faces']) if
            (region[f['axis']][1] >= f['plane'] if f['direction'] > 0 else region[f['axis']][0] <= f['plane'])]


def prepare_encounter(owner, item, envelopes):
    """Prepare atomically: a deadline leaves the unprocessed item resumable."""
    if 'phases' in item:
        return
    result = dict(item, faces=faces(item['box']), phases=[], verticalClear=[], coveredPhases=[], uncertain=[])
    result['entryFaces'] = support_faces(owner, result, envelopes, item['begin'])
    result['exitFaces'] = support_faces(owner, result, envelopes, item['end'])
    uncertain = {e.get('input', e.get('offset')) for e in owner.profile.get('caseEvents', [])}
    uncertain.update(c['state']-1 for c in envelopes.get('model', {}).get('vertical', {}).get('contacts', [])
                     if c.get('case'))
    required = dict(X=set(), Z=set())
    for tick in range(item['begin'], item['end']):
        for phase in PHASES:
            owner.check()
            owner.metric('phaseChecks')
            at = 3*tick+PHASES.index(phase)
            bounds = envelopes['phases'][tick][phase]
            vertical = next((i for i, f in enumerate(result['faces']) if f['axis'] == 'Y' and
                (bounds['Y'][0] >= f['plane'] if f['direction'] > 0 else bounds['Y'][1] <= f['plane'])), None)
            if vertical is not None:
                result['verticalClear'].append([at, vertical])
            if block_interaction(envelopes, item['box'], tick, (phase,), interior=True)['ruledOut']:
                certain = [i for i, f in enumerate(result['faces']) if
                    (bounds[f['axis']][0] >= f['plane'] if f['direction'] > 0 else bounds[f['axis']][1] <= f['plane'])]
                result['coveredPhases'].append([at, certain])
                owner.metric('excludedPhases')
                continue
            if tick in uncertain:
                # Named cases have extra internal probes. Neither their realized
                # endpoints nor ordinary sweep rows specify those probes. Preserve
                # the unknown and let the existing named-case scene replay judge it.
                result['uncertain'].append(at)
                owner.metric('uncertainPhases')
                continue
            indices = {axis: phase_states(tick, phase, axis) for axis in ('X', 'Z')}
            options, costs = {}, {}
            for i, face in enumerate(result['faces']):
                if face['axis'] == 'Y':
                    continue
                rows = [owner.face_row(face, t) for t in indices[face['axis']]]
                if interaction_reachability(envelopes, rows)['ruledOut']:
                    owner.metric('excludedFaces')
                    continue
                options[str(i)] = rows
                costs[str(i)] = sum(max(0., face['direction']*(row['rhs']-owner.coordinate(face['axis'], row['t1'], envelopes)))**2
                                     for row in rows)
            for axis in required:
                required[axis].update(indices[axis])
            result['phases'].append(dict(at=at, options=options, costs=costs))
    result['required'] = {k: sorted(v) for k, v in required.items()}
    result['possiblePhases'] = [[p['at']//3, PHASES[p['at']%3]] for p in result['phases']]
    # This is a ranking lower bound only: it cannot exclude a side or timing.
    suffix = [0.]*(len(result['phases'])+1)
    for i in range(len(result['phases'])-1, -1, -1):
        suffix[i] = suffix[i+1]+min(result['phases'][i]['costs'].values(), default=0.)
    result['suffixCost'] = suffix
    item.clear()
    item.update(result)
    if result['uncertain']:
        record = dict(solidIndex=item['solidIndex'], segment=[item['begin'], item['end']], phases=result['uncertain'])
        if record not in owner.state['uncertainInteractions']:
            owner.state['uncertainInteractions'].append(record)
    owner.metric('preparedEncounters')


def _continuation(history, face):
    """One encounter follows a simple face walk, with its entry allowed as exit.

    A repeated entry closes the encounter; it cannot start another bypass in the
    same segment. Holding a side across a known vertical-clear gap is the same
    walk. This is geometric ownership, not a per-obstacle switch-count budget.
    """
    if not history or history[-1] == face:
        return history or [face]
    if len(history) > 1 and history[-1] == history[0]:
        return None
    if face in history:
        return history+[face] if face == history[0] else None
    return history+[face]


def describe(item, spans):
    # Exact phase spans are diagnostic truth; integer gates remain convenient
    # labels for consumers that display a tick. Generated rows are never rebuilt
    # from these labels using the legacy full-leg passage compiler.
    timeline = [(p['at'], f) for a, b, f in spans for p in item['phases'] if a <= p['at'] <= b]
    if spans and len(item['entryFaces']) == 1 and item['entryFaces'][0] != spans[0][2]:
        # Geometric entry is already covered before the first emitted row.
        # Its phase is the selected landing, never an invented switch at zero.
        timeline.insert(0, (max(0, 3*item['begin']-1), item['entryFaces'][0]))
    for at, certain in item['coveredPhases']:
        if spans and timeline[0][0] < at < spans[-1][1] and len(certain) == 1:
            timeline.append((at, certain[0]))
    for a, f in item['verticalClear']:
        if spans and timeline[0][0] < a <= spans[-1][1]:
            timeline.append((a, f))
    tail = next(((a, f) for a, f in item['verticalClear'] if spans and a > spans[-1][1]), None)
    if tail is not None:
        timeline.append(tail)
    timeline.sort()
    chosen, switches = [], []
    for at, face in timeline:
        if not chosen or chosen[-1] != face:
            chosen.append(face)
            switches.append(at)
    fs = [item['faces'][i] for i in chosen]
    return dict(leg=0, solidIndex=item['solidIndex'], blockId=item.get('owner', 'solid:'+str(item['solidIndex'])),
        segment=[item['begin'], item['end']], faces=fs, gates=[at//3 for at in switches[1:]],
        switchPhases=switches[1:], phaseSpans=spans, entryFaces=[item['faces'][i] for i in item['entryFaces']],
        exitFaces=[item['faces'][i] for i in item['exitFaces']],
        kind=passage_kind(fs), blockageCounts=blockage_counts(fs), blockageCount=max(0, len(fs)-1))


def encounter_option(owner, item, cursor, envelopes):
    prepare_encounter(owner, item, envelopes)
    entry = item['entryFaces']
    history = entry if len(entry) == 1 and item['faces'][entry[0]]['axis'] != 'Y' else []
    cursor.setdefault('pending', [dict(at=0, rows=[], history=history, spans=[])])
    cursor.setdefault('seen', [])
    cursor.setdefault('completed', [])
    seen = set(cursor['seen'])
    phases = item['phases']
    while cursor['pending']:
        owner.check()
        node = cursor['pending'][-1]
        at = node['at']
        # Existing rows can already cover later sweeps, including the terminal
        # one. Do not manufacture an optional third face to cover them again.
        while at < len(phases) and any(rows_entailed(envelopes, node['rows'], rows)
                                      for rows in phases[at]['options'].values()):
            at += 1
        if at == len(phases):
            rows = canonical_rows(node['rows'])
            key = owner.effective_key(rows)
            cursor['pending'].pop()
            owner.metric('optionLeaves')
            if key in cursor['completed']:
                owner.metric('duplicateOptions')
                continue
            # Same remaining capabilities: a completed weaker cover subsumes a
            # stricter description. Never use this rule on unfinished walks.
            if any(rows_entailed(envelopes, rows, weaker) for weaker in cursor.get('covers', [])):
                owner.metric('dominatedOptions')
                continue
            cursor['completed'].append(key)
            cursor.setdefault('covers', []).append(rows)
            return dict(rows=rows, **(dict(passage=describe(item, node['spans'])) if node['spans'] else {}))
        children = []
        for face_string in phases[at]['options']:
            face = int(face_string)
            history = node['history']
            previous_at = node['spans'][-1][1] if node['spans'] else 3*item['begin']-1
            bridges = [certain for p, certain in item['coveredPhases']
                       if previous_at < p < phases[at]['at']]
            if (any(previous_at < p < phases[at]['at'] for p in item['uncertain']) or
                    any(not certain or any(not history or f != history[-1] for f in certain) for certain in bridges)):
                # An already proved-clear gap may contain the intervening side
                # (or unknown named probes). Its rows are already owned by this
                # branch. Do not reject the next face because that implicit
                # bridge was correctly omitted from the choice catalogue.
                history = []
            if history and history[-1] != face:
                gap = any(previous_at < a < phases[at]['at'] for a, _ in item['verticalClear'])
                if item['faces'][history[-1]]['axis'] == item['faces'][face]['axis'] and not gap:
                    continue
            following = _continuation(history, face)
            if following is None:
                continue
            rows, paid = list(node['rows']), 0.
            for stop in range(at, len(phases)):
                owner.check()
                phase = phases[stop]
                if face_string not in phase['options']:
                    break
                rows = canonical_rows(rows+phase['options'][face_string])
                if interaction_reachability(envelopes, rows)['ruledOut']:
                    break
                paid += phase['costs'][face_string]
                child = dict(at=stop+1, rows=rows, history=following,
                    spans=node['spans']+[[phases[at]['at'], phase['at'], face]])
                key = digest([child['at'], owner.effective_key(rows), following])
                if key not in seen:
                    children.append((paid+item['suffixCost'][stop+1]+len(following)*1e-9, key, child))
        children.sort(key=lambda v: (v[0], -v[2]['at'], v[1]))
        # Publish the whole expansion atomically. An interrupted build leaves its
        # parent pending; no untried timing is marked consumed.
        cursor['pending'].pop()
        owner.metric('optionNodes')
        for _, key, child in reversed(children):
            if key in seen:
                continue
            seen.add(key)
            cursor['seen'].append(key)
            cursor['pending'].append(child)
            owner.metric('generatedChildren')
    return None
