"""Lazy phase covers for one obstacle-cluster encounter in a selected landing segment.

A cluster's sides at each phase are the maximal free rectangles around its members
active at that height (spatial/surfaces); Minecraft's Y/X/Z sweeps remain
authoritative in avoidance/geometry/reachability. This module chooses only
effective horizontal requirements; fixed vertical clearance is a gap, never a side.
"""
import math

from parkour_solver.constraints.avoidance import blockage_counts, passage_kind, phase_states
from parkour_solver.reachability.service import block_interaction
from parkour_solver.reachability.intervals import interaction_reachability
from parkour_solver.search.identity import digest
from parkour_solver.spatial.surfaces import intersects, nested


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


def settled_through(profile, through):
    """The tick before which every landing segment is closed: `through` when the prefix ends
    grounded, else the start of its last (airborne) segment."""
    ground = profile.get('grounded', [])
    if through < len(ground) and ground[through]:
        return through
    segments = landing_segments(profile, through)
    return segments[-1][0] if segments else 0


def region(side):
    return tuple((-math.inf if i < 2 else math.inf) if v is None else v for i, v in enumerate(side['region']))


def same(item, a, b):
    """Sides of different active sets describe one place when their regions nest."""
    if a == b:
        return True
    first, second = item['sides'][a], item['sides'][b]
    return 'Y' not in (first['axis'], second['axis']) and nested(region(first), region(second))


def satisfied(side, bounds):
    return all(bounds[b['axis']][0] >= b['plane'] if b['direction'] > 0 else bounds[b['axis']][1] <= b['plane']
               for b in side['bounds'])


def reaches(side, bounds):
    return all(bounds[b['axis']][1] >= b['plane'] if b['direction'] > 0 else bounds[b['axis']][0] <= b['plane']
               for b in side['bounds'])


def side_dict(side):
    bounds = [dict(axis=a, direction=d, plane=p, owners=list(o)) for (a, d, p), o in zip(side.bounds, side.owners)]
    axes = {b['axis'] for b in bounds}
    single = bounds[0] if len(bounds) == 1 else None
    return dict(key=[list(b) for b in side.bounds], bounds=bounds, owners=sorted(set().union(*side.owners)),
                region=[None if math.isinf(v) else v for v in side.region],
                axis=axes.pop() if len(axes) == 1 else 'XZ', direction=single['direction'] if single else 0,
                plane=single['plane'] if single else None)


def vertical_side(cluster, y_lo):
    below = [b[4] for b in cluster.boxes if b[4] <= y_lo]
    direction, plane = (1, max(below)) if below else (-1, min(b[1] for b in cluster.boxes))
    return dict(key=[['Y', direction, plane]], bounds=[], owners=[], region=[None]*4,
                axis='Y', direction=direction, plane=plane)


def support_region(owner, envelopes, tick):
    """The finite contact region precedes that input's horizontal movement.

    Keep it as entry/exit geometry, never as a new position pin at tick+1.
    Post-contact travel envelopes can extend beyond the support's footprint.
    """
    event = next((e for e in owner.profile.get('contactEvents', []) if e.get('axis') == 'Y'
                  and e.get('direction') == -1 and e['offset']+1 == tick and 'regions' in e), None)
    if event is None:
        return envelopes['states'][tick]
    regions = event['regions']
    return dict(X=[min(r[0] for r in regions), max(r[2] for r in regions)],
                Z=[min(r[1] for r in regions), max(r[3] for r in regions)], Y=[max(b[4] for b in event['boxes'])]*2)


def prepare_encounter(owner, item, envelopes):
    """Prepare atomically: a deadline leaves the unprocessed item resumable."""
    if 'phases' in item:
        return
    geometry = owner.geometry
    cluster = geometry.cluster(item['clusterId'], item['basis'])
    result = dict(item, sides=[], phases=[], verticalClear=[], coveredPhases=[], uncertain=[], excludedFaces=[])
    indices = {}

    def index(side):
        key = json_key(side['key'])
        if key not in indices:
            indices[key] = len(result['sides'])
            result['sides'].append(side)
        else:
            known = result['sides'][indices[key]]
            known['owners'] = sorted(set(known['owners']) | set(side['owners']))
            for bound, other in zip(known['bounds'], side['bounds']):
                bound['owners'] = sorted(set(bound['owners']) | set(other['owners']))
        return indices[key]

    def sides(active):
        return [index(side_dict(s)) for s in geometry.sides(cluster, active)]

    def boundary(tick):
        bounds = support_region(owner, envelopes, tick)
        active = geometry.active(cluster, *bounds['Y'])
        if not active:
            return [index(vertical_side(cluster, bounds['Y'][0]))]
        return [i for i in sides(active) if reaches(result['sides'][i], bounds)]
    result['entryFaces'] = boundary(item['begin'])
    result['exitFaces'] = boundary(item['end'])
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
            active = geometry.active(cluster, *bounds['Y'])
            if not active:
                vertical = index(vertical_side(cluster, bounds['Y'][0]))
                result['verticalClear'].append([at, vertical])
                result['coveredPhases'].append([at, [vertical]])
                owner.metric('excludedPhases')
                continue
            # Members the envelope rules out at this phase cannot be touched then; the sides are
            # built around the rest.
            reachable = [k for k in active
                         if not block_interaction(envelopes, cluster.solids[k], tick, (phase,), interior=True)['ruledOut']]
            if not reachable:
                certain = [i for i in sides(active) if satisfied(result['sides'][i], bounds)]
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
            states = {axis: phase_states(tick, phase, axis) for axis in ('X', 'Z')}
            options, costs = {}, {}
            for i in sides(reachable):
                rows = [owner.face_row(b, t) for b in result['sides'][i]['bounds'] for t in states[b['axis']]]
                if interaction_reachability(envelopes, rows)['ruledOut']:
                    owner.metric('excludedFaces')
                    result['excludedFaces'].append([at, i])
                    continue
                options[str(i)] = rows
                costs[str(i)] = sum(max(0., (1 if row['cmp'] == 'GE' else -1)*
                                        (row['rhs']-owner.coordinate(row['mode'], row['t1'], envelopes)))**2
                                     for row in rows)
            for axis in required:
                required[axis].update(states[axis])
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
        record = dict(clusterId=item['clusterId'], members=item['members'], segment=[item['begin'], item['end']],
                      phases=result['uncertain'])
        if record not in owner.state['uncertainInteractions']:
            owner.state['uncertainInteractions'].append(record)
    owner.state['encounterLog'].append(dict(
        clusterId=item['clusterId'], members=item['members'], names=item['names'], owner=item['owner'],
        segment=[item['begin'], item['end']],
        sides=result['sides'], possiblePhases=result['possiblePhases'], coveredPhases=result['coveredPhases'],
        excludedFaces=result['excludedFaces'], verticalClear=result['verticalClear'], uncertain=result['uncertain']))
    owner.metric('preparedEncounters')


def json_key(key):
    return tuple(tuple(bound) for bound in key)


def _continuation(history, face, same):
    """One encounter follows a simple side walk, with its entry allowed as exit.

    A repeated entry closes the encounter; it cannot start another bypass in the
    same segment. Holding a side across a known vertical-clear gap, or into a
    nested side of another active set, is the same walk. This is geometric
    ownership, not a per-obstacle switch-count budget.
    """
    if not history:
        return [face]
    if same(history[-1], face):
        return history[:-1]+[face]
    if len(history) > 1 and same(history[-1], history[0]):
        return None
    if any(same(f, face) for f in history):
        return history+[face] if same(face, history[0]) else None
    return history+[face]


def describe(item, spans):
    # Exact phase spans are diagnostic truth; integer gates remain convenient
    # labels for consumers that display a tick. Generated rows are never rebuilt
    # from these labels using the legacy full-leg passage compiler.
    timeline = [(p['at'], f) for a, b, f in spans for p in item['phases'] if a <= p['at'] <= b]
    if spans and len(item['entryFaces']) == 1 and not same(item, item['entryFaces'][0], spans[0][2]):
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
        if not chosen or not same(item, chosen[-1], face):
            chosen.append(face)
            switches.append(at)
    fs = [item['sides'][i] for i in chosen]
    return dict(leg=0, clusterId=item['clusterId'], members=item['members'], blockId=item['owner'],
        segment=[item['begin'], item['end']], faces=fs, gates=[at//3 for at in switches[1:]],
        switchPhases=switches[1:], phaseSpans=[[a, b, item['sides'][f]['key']] for a, b, f in spans],
        entryFaces=[item['sides'][i] for i in item['entryFaces']],
        exitFaces=[item['sides'][i] for i in item['exitFaces']],
        kind=passage_kind(fs), blockageCounts=blockage_counts(fs), blockageCount=max(0, len(fs)-1))


def encounter_option(owner, item, cursor, envelopes):
    prepare_encounter(owner, item, envelopes)
    entry = item['entryFaces']
    history = entry if len(entry) == 1 and item['sides'][entry[0]]['axis'] != 'Y' else []
    alike = lambda a, b: same(item, a, b)
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
                owner.state['droppedOptions'].append(dict(clusterId=item['clusterId'], members=item['members'],
                                                          segment=[item['begin'], item['end']],
                                                          reason='duplicate', rows=rows))
                continue
            # Same remaining capabilities: a completed weaker cover subsumes a
            # stricter description. Never use this rule on unfinished walks.
            if any(rows_entailed(envelopes, rows, weaker) for weaker in cursor.get('covers', [])):
                owner.metric('dominatedOptions')
                owner.state['droppedOptions'].append(dict(clusterId=item['clusterId'], members=item['members'],
                                                          segment=[item['begin'], item['end']],
                                                          reason='dominated', rows=rows))
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
                    any(not certain or any(not history or not alike(f, history[-1]) for f in certain)
                        for certain in bridges)):
                # An already proved-clear gap may contain the intervening side
                # (or unknown named probes). Its rows are already owned by this
                # branch. Do not reject the next face because that implicit
                # bridge was correctly omitted from the choice catalogue.
                history = []
            if history and not alike(history[-1], face):
                gap = any(previous_at < a < phases[at]['at'] for a, _ in item['verticalClear'])
                if not intersects(region(item['sides'][history[-1]]), region(item['sides'][face])) and not gap:
                    continue
            following = _continuation(history, face, alike)
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
