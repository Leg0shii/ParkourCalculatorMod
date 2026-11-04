"""Immutable geometric domain and lazy semantic route language.

The raw incidence graph covers positions. It does not decide dynamic
reachability and its individual air-cell walks are never route identities.
"""

import json
from dataclasses import asdict, dataclass

from ..movement_search import movement_settings
from ..obstacle_geometry import BODY_HEIGHT, expanded_box
from ..route import HALF_WIDTH
from .geometry import contains, decompose, intersection
from .regions import build_geometry_graph, canonical_cover, geometry_key, support_components


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

    Positive-area components can meet across a positive-width seam. A point
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
                if overlap is not None and (overlap[0] < overlap[3] or overlap[2] < overlap[5]):
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
        surfaces = sorted(s['id'] for s in groups if any(b['solidIndex'] in s['solids'] for b in blocks)
                          and abs(s['height']-profile['position'][min(tick+1, profile['airtime'])]) <= 1e-7)
        if named:
            result.append(dict(kind='named_contact', input=tick, case=event.get('case'),
                owners=owners, surfaces=surfaces, event=event, classification='observed_program'))
        elif not (profile['grounded'][tick] and not profile['jump'][tick] and
                  current.intersection(surfaces) and not set(owners).intersection(required_owners)):
            result.append(dict(kind='support_visit', input=tick, tick=tick+1, owners=owners,
                surfaces=surfaces, classification='observed_program' if surfaces else 'unknown'))
        if surfaces and profile['grounded'][min(tick+1, profile['airtime'])]:
            current = set(surfaces)
    return result
