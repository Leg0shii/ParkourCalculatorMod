"""Archived programs predate member boxes, landing regions and floor-check regions; convert them to the current fields."""
from parkour_solver.movement.contact_combinations import contact_clearance, expanded_face
from parkour_solver.scene.route import center_bounds


def current_events(value, clearance=None):
    """Recursively give archived contact and case events `boxes` (and contacts `surface`/`regions`)."""
    if isinstance(value, list):
        for item in value:
            current_events(item, clearance)
    elif isinstance(value, dict):
        if 'box' in value and ('axis' in value and 'direction' in value and 'offset' in value or 'case' in value):
            box = value.pop('box')
            value['boxes'] = [box]
            if 'case' not in value:
                value.update(surface=None, regions=[expanded_face(box, 'XYZ'.index(value['axis']), -value['direction'],
                                                                  contact_clearance({}) if clearance is None else clearance)])
        if (value.get('reason') in ('earlier_floor', 'unplanned_stair') and 'region' not in value
                and any(k in value.get('block', {}) for k in ('bounds', 'centerBounds', '_derivedFootprint'))):
            bounds = center_bounds(value['block'])
            value['region'] = [bounds[0], bounds[2], bounds[1], bounds[3]]
        rows = value.get('positionConstraints')
        if isinstance(rows, list) and any(r.get('name') == 'normal:landing' for r in rows):
            # A landing's finite region now lives in its contact event, not in owned rows.
            owners = value.get('constraintOwners', [0]*len(rows))
            kept = [(r, o) for r, o in zip(rows, owners) if r.get('name') != 'normal:landing']
            value['positionConstraints'] = [r for r, _ in kept]
            if 'constraintOwners' in value:
                value['constraintOwners'] = [o for _, o in kept]
        for item in value.values():
            current_events(item, clearance)
    return value
