"""Archived programs predate member boxes; convert their events to the current fields."""
from parkour_solver.movement.contact_combinations import contact_clearance, expanded_face


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
        for item in value.values():
            current_events(item, clearance)
    return value
