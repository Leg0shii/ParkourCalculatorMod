"""Immutable geometric domain and lazy semantic route language.

The raw incidence graph covers positions. It does not decide dynamic
reachability and its individual air-cell walks are never route identities.
"""

import json
from dataclasses import asdict, dataclass

from parkour_solver.movement.settings import movement_settings
from parkour_solver.scene.geometry import BODY_HEIGHT, expanded_box
from parkour_solver.scene.route import HALF_WIDTH
from parkour_solver.spatial.cells import contains, decompose, intersection
from parkour_solver.spatial.regions import build_geometry_graph, canonical_cover, geometry_key, support_components


DOMAIN_SCHEMA = 1
GRAPH_SCHEMA = 1
CURSOR_SCHEMA = 1


def _json(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


@dataclass(frozen=True)
class SurfaceGroup:
    id: str
    height: float
    cover: tuple
    components: tuple
    owners: tuple
    solids: tuple


def surface_groups(supports):
    """Group certified coplanar walkable patches, preserving exact unions.

    Components need a common positive-area body-overlap region. A rim
    or a component made only of rims does not certify walkable connectivity.
    Physical owner components always survive separately in the domain.
    """
    def connected(a, b):
        if a.height != b.height:
            return False
        for x in a.cover:
            for y in b.cover:
                if not all(z[0] < z[3] and z[2] < z[5] for z in (x, y)):
                    continue
                overlap = intersection(x, y)
                if overlap is not None and overlap[0] < overlap[3] and overlap[2] < overlap[5]:
                    return True
        return False
    pending = {s.key: s for s in supports}
    result = []
    while pending:
        first = pending.pop(min(pending))
        members, queue = [first], [first]
        while queue:
            current = queue.pop()
            adjacent = [k for k, s in pending.items() if connected(current, s)]
            for k in sorted(adjacent):
                other = pending.pop(k)
                members.append(other)
                queue.append(other)
        cover = canonical_cover(b for s in members for b in s.cover)
        # Geometry/owners define the surface; physical array indices are provenance.
        owners = tuple(sorted({s.owner for s in members}))
        result.append(SurfaceGroup('surface:'+geometry_key((first.height, cover, owners)),
            first.height, cover, tuple(sorted(s.key for s in members)), owners,
            tuple(sorted({i for s in members for i in s.solids}))))
    return tuple(sorted(result, key=lambda s: s.id))


@dataclass(frozen=True)
class RouteDomain:
    """Canonical JSON storage prevents shared mutable graph/cursor ownership.

    A caller obtains one independent session snapshot via to_dict. Work cursors
    reference its domainId instead of nesting this table in every frame.
    """
    payload: str

    @property
    def id(self):
        return json.loads(self.payload)['domainId']

    def to_dict(self):
        return json.loads(self.payload)

    @classmethod
    def from_dict(cls, value):
        if value.get('schema') != DOMAIN_SCHEMA:
            raise ValueError('Incompatible event domain schema')
        data = dict(value)
        identity = data.pop('domainId', None)
        if identity != geometry_key(data):
            raise ValueError('Event domain content does not match domainId')
        return cls(_json(value))


def build_route_domain(scene, task, *, deadline=None, max_cells=20000, decomposition=None):
    """Build all raw coverage or fail explicitly; never return a truncated graph.

    Query-boundary enclosure is deliberately unknown. Horizon XY query bounds
    alone cannot certify Y/probe enclosure. Outside attachments remain latent.
    """
    d = decomposition if decomposition is not None else decompose(scene, deadline=deadline, max_cells=max_cells)
    supports = support_components(d)
    graph = build_geometry_graph(scene, d, supports, task, deadline=deadline)
    groups = surface_groups(supports)
    features = []
    owners = {b['solidIndex']: b.get('sourceBlock', b['id']) for b in scene['blocks']}
    for i, solid in enumerate(scene['collisionScene']['solids']):
        box = tuple(expanded_box(solid))
        for axis in range(3):
            for side in (-1, 1):
                plane = box[axis if side < 0 else axis+3]
                bounds = list(box)
                bounds[axis] = bounds[axis+3] = plane
                feature = dict(owner=owners[i], axis=axis, side=side, bounds=bounds)
                features.append(dict(id='face:'+geometry_key(feature), **feature, solidIndex=i))
    cfg = movement_settings(scene)
    data = dict(schema=DOMAIN_SCHEMA, scene={k: v for k, v in scene.items() if k != 'movementSearch'},
        body=dict(halfWidth=HALF_WIDTH, height=BODY_HEIGHT), task=task,
        inputDomain={k: v for k, v in cfg.items() if k not in
            ('maxPrefixes', 'maxQueuedPrefixes', 'maxChoicesPerType', 'expansionsPerProposal')},
        mechanics=list(cfg['types']), geometry=graph.to_dict(),
        surfaces=[asdict(s) for s in groups], features=sorted(features, key=lambda f: f['id']),
        coverage=dict(local='complete', enclosure='unknown', outsideAttachment='unresolved', bounds=d.bounds))
    data['domainId'] = geometry_key(data)
    return RouteDomain(_json(data))


def support_episodes(domain, profile, *, required_owners=()):
    """Project physical event rows, preserving named events and contact revisits.

    This classifies an already selected program; it is not scene validation.
    Uncertain component membership is kept as alternatives, never guessed.
    """
    data = domain.to_dict() if isinstance(domain, RouteDomain) else domain
    scene = data['scene']
    groups = data['surfaces']
    by_box = {}
    for block in scene['blocks']:
        box = tuple(scene['collisionScene']['solids'][block['solidIndex']])
        by_box.setdefault(box, []).append(block)
    result = []
    current = {s['id'] for s in groups if profile['grounded'][0] and
               any(contains(b, scene['initial']['position']) for b in s['cover'])}
    events = [(e['offset'], 0, e) for e in profile.get('contactEvents', [])
              if e['axis'] == 'Y' and e.get('direction') == -1]
    events += [(e['input'], 1, e) for e in profile.get('caseEvents', [])]
    for tick, named, event in sorted(events, key=lambda x: (x[0], x[1], _json(x[2]))):
        blocks = by_box.get(tuple(event.get('box', ())), [])
        owners = sorted({b.get('sourceBlock', b['id']) for b in blocks})
        solids = sorted({b['solidIndex'] for b in blocks})
        surfaces = sorted(s['id'] for s in groups if any(b['solidIndex'] in s['solids'] for b in blocks)
                          and abs(s['height']-profile['position'][min(tick+1, profile['airtime'])]) <= 1e-7)
        if named:
            result.append(dict(kind='named_contact', input=tick, case=event.get('case'),
                owners=owners, solids=solids, surfaces=surfaces, event=event, classification='observed_program'))
        elif not (profile['grounded'][tick] and not profile['jump'][tick] and
                  current.intersection(surfaces) and not set(owners).intersection(required_owners)):
            result.append(dict(kind='support_visit', input=tick, tick=tick+1, owners=owners,
                solids=solids, surfaces=surfaces, classification='observed_program' if surfaces else 'unknown'))
        if surfaces and profile['grounded'][min(tick+1, profile['airtime'])]:
            current = set(surfaces)
    return result


def build_event_graph(domain):
    """Compile event attachments; connectors keep the full cyclic raw graph.

    The graph is implicit between event attachments, so no O(paths) catalogue
    or simple-path restriction is needed. Closure work is requested lazily.
    """
    data = domain.to_dict() if isinstance(domain, RouteDomain) else domain
    raw = data['geometry']
    scene = data['scene']
    task = data['task'] or dict(kind='landing', block=scene['goalBlock'])
    events = []
    for surface in data['surfaces']:
        blocks = sorted(b['id'] for b in scene['blocks'] if b['solidIndex'] in surface['solids'])
        if task['kind']=='landing' and blocks == [task['block']]:
            # This exact contact already is the task; do not score it twice
            # under a generic visit and an identical terminal request.
            continue
        events.append(dict(id='visit:'+surface['id'], kind='support_visit', surface=surface['id'],
            attachments=surface['components'], cover=surface['cover'],
            goal=dict(kind='landing', block=blocks[0], blocks=blocks, surface=surface['id']), terminal=False))
    # Keep an explicit owner-specific task even on a shared coplanar surface.
    events.append(dict(id='finish:'+geometry_key(task), kind='finish', attachments=raw['goals'],
        cover=[], goal=task, terminal=True))
    result = dict(schema=GRAPH_SCHEMA, domainId=data['domainId'], events=sorted(events, key=lambda e:e['id']),
        backingContext=raw['context'], outsideAttachment=data['coverage']['outsideAttachment'])
    result['id'] = geometry_key(result)
    return result


def initial_cursor(domain, roots=None):
    data = domain.to_dict() if isinstance(domain, RouteDomain) else domain
    attachments=data['geometry']['starts']
    if roots and any(p['profile']['airtime'] for p in roots):
        attachments=set()
        for program in roots:
            if not program['profile']['grounded'][-1]:
                attachments.update(r['key'] for r in data['geometry']['regions'] if r['kind']=='air_volume')
            else:
                solid=next((b['solidIndex'] for b in data['scene']['blocks'] if b['id']==program['terminalSupport']),None)
                attachments.update(s['key'] for s in data['geometry']['supports'] if solid in s['solids'] and
                    abs(s['height']-program['profile']['position'][-1])<=1e-7)
        attachments=sorted(attachments)
    return dict(schema=CURSOR_SCHEMA, domainId=data['domainId'], occurrences=[],
        attachments=attachments, commitments=[], outsideUnresolved=True)


def next_goals(domain, graph, cursor, continuation=None, *, max_expansions=5000, max_materializations=50, check=None):
    """Budgeted silent closure, with contacts as outputs rather than barriers.

    Air edges can pass over supports without consuming a contact. Closure never
    traverses an arrival into another departure: that would splice a landing
    into an alleged no-contact connector. The backing graph remains cyclic.
    Work quotas suspend this query; they do not limit its represented language.
    """
    import copy
    data = domain.to_dict() if isinstance(domain, RouteDomain) else domain
    if cursor.get('domainId') != data['domainId'] or graph['domainId'] != data['domainId']:
        raise ValueError('Incompatible semantic cursor/domain')
    key = geometry_key((graph['id'], cursor))
    if continuation is not None and continuation.get('context') != key:
        raise ValueError('Incompatible next-goal continuation')
    state = copy.deepcopy(continuation) if continuation is not None else dict(schema=1, context=key,
        pending=list(cursor['attachments']), seen=[], reachable=[], emitted=[], exhausted=False)
    edges = {}
    for edge in data['geometry']['transitions']:
        edges.setdefault(edge['source'], []).append(edge)
    seen, reachable = set(state['seen']), set(state['reachable'])
    work = 0
    while state['pending'] and work < max_expansions:
        if check:
            try:
                check()
            except TimeoutError:
                break
        current = state['pending'].pop(0)
        if current in seen:
            continue
        seen.add(current); work += 1
        for edge in edges.get(current, []):
            if edge['kind'] in ('open_portal', 'support_departure'):
                if edge['target'] not in seen and edge['target'] not in state['pending']:
                    state['pending'].append(edge['target'])
            else:
                reachable.add(edge['target'])
    state['seen'], state['reachable'] = sorted(seen), sorted(reachable)
    requests = []
    for event in graph['events']:
        if len(requests) >= max_materializations:
            break
        # Unknown enclosure keeps unreachable events optimistic. They must not
        # be declared impossible because a finite query clips their attachment.
        discovered = bool(set(event['attachments']).intersection(reachable))
        if not discovered and (state['pending'] or not cursor['outsideUnresolved']):
            continue
        if event['id'] in state['emitted']:
            continue
        connector = dict(id='connector:'+geometry_key((graph['id'],cursor['attachments'],event['id'])),
            backingGraph=graph['backingContext'], entry=cursor['attachments'], exit=event['attachments'],
            outsideUnresolved=not discovered, refinement='unresolved', selectedWalk=None)
        request = dict(schema=1, id='request:'+geometry_key((key,event['id'])), event=event['id'],
            goal=event['goal'], terminal=event['terminal'], connector=connector,
            commitments=cursor['commitments'], semanticPrefix=semantic_prefix(cursor))
        requests.append(request); state['emitted'].append(event['id'])
    eligible = [e for e in graph['events'] if cursor['outsideUnresolved'] or
                set(e['attachments']).intersection(reachable)]
    state['exhausted'] = not state['pending'] and all(e['id'] in state['emitted'] for e in eligible)
    return requests, state, dict(expansions=work, materializations=len(requests),
        termination='exhausted' if state['exhausted'] else 'graph_limit' if state['pending'] else 'guide_limit')


def semantic_prefix(cursor):
    """Untimed route meaning, separate from the exact execution-owner cursor."""
    result=[]
    for occurrence in cursor['occurrences']:
        observed=occurrence.get('observed',{})
        if not isinstance(observed,dict):
            observed={}
        result.append(dict(event=occurrence['event'],occurrence=occurrence['occurrence'],
            named=[dict(case=e['case'],owners=e['owners']) for e in observed.get('events',[]) if e['kind']=='named_contact'],
            passages=[[p['features'] for p in option] for option in observed.get('passageAlternatives',[])]))
    return json.loads(_json(result))


def advance_cursor(domain, graph, cursor, request, observed):
    import copy
    data = domain.to_dict() if isinstance(domain, RouteDomain) else domain
    event = next(e for e in graph['events'] if e['id'] == request['event'])
    result = copy.deepcopy(cursor)
    result['attachments'] = event['attachments']
    result['occurrences'].append(dict(event=event['id'], occurrence=len(cursor['occurrences']), observed=observed))
    # No selected raw path is carried forward as a hard requirement.
    result['outsideUnresolved'] = data['coverage']['enclosure'] != 'complete'
    return result


def observed_route(data, profile, witness, *, after=0):
    """Keep physical approach evidence distinct from optional guide labels.

    Equal-row physical alternatives retain their provenance/resource union.
    Timed rows remain in the fixed model; semantic words use physical features.
    """
    episodes = [e for e in support_episodes(data,profile) if e['input']>=after]
    alternatives = witness.get('selectedInteractionAlternatives', [witness.get('selectedInteractions',[])])
    passages=[]
    for bundle in alternatives:
        variant=[]
        for passage in bundle:
            features=[]
            for face in passage['faces']:
                matches=[f['id'] for bound in face.get('bounds') or [face] for f in data['features']
                    if f['solidIndex'] in passage.get('members', [passage.get('solidIndex')]) and f['axis']=='XYZ'.index(bound['axis']) and
                    f['side']==bound['direction'] and f['bounds'][f['axis']]==bound['plane']]
                features.append(matches)
            variant.append(dict(kind='passage',features=features,gates=passage['gates'],
                resources=passage['blockageCounts'],classification='selected_model',
                window=[0,profile['airtime']]))
        passages.append(variant)
    return dict(events=episodes,passageAlternatives=passages,optionalGuide='uncommitted',
        validation='fixed_model_evidence',after=after)
