"""Declared movement domain settings, preserving registry-driven capabilities."""
from parkour_solver.schema import integer
from parkour_solver.movement.generation import GENERATORS


def movement_settings(scene):
    settings = dict(scene.get('movementSearch', {}))
    defaults = dict(maxInputs=200, maxPrefixes=256, maxQueuedPrefixes=512, maxChoicesPerType=64,
                    maxInteractions=200, maxEntryInputs=14, expansionsPerProposal=8,
                    types=list(GENERATORS))
    if set(settings) - set(defaults):
        raise ValueError('Unsupported movement search settings')
    for key, value in defaults.items():
        settings.setdefault(key, value)
        if key != 'types':
            integer(settings[key], key, 0 if key == 'maxEntryInputs' else 1, 200 if key in ('maxInputs', 'maxInteractions') else 200000)
    if not isinstance(settings['types'], list) or not settings['types'] or len(set(settings['types'])) != len(settings['types']) \
            or any(kind not in GENERATORS for kind in settings['types']):
        raise ValueError('Select registered movement types')
    return settings
