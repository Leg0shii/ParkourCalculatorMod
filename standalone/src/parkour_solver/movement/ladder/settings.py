"""Declared ladder search domain validation."""
import copy
from parkour_solver.schema import integer


def ladder_settings(scene):
    settings = copy.deepcopy(scene.get('ladderSearch', {}))
    defaults = {'maxEntryInputs': 14, 'maxInputs': 48, 'entryDelay': 5, 'maxSkeletons': 2048,
                'maxSchedules': 2000, 'maxMaskWork': 30000, 'maxRanked': 256, 'representatives': 3,
                'maxNumericalSchedules': 32, 'maxOrdinaryPaths': 8, 'maxSuffixPaths': 3,
                'maxRouteContexts': 24, 'maxRunAllocations': 4, 'minClimbInputs': 0,
                'maxInteractions': 12, 'requireLadder': False,
                'maxContactVariants': 16, 'maxMixedContacts': 3, 'maxTransferPrograms': 16, 'maxTransferWindows': 4,
                'maxTransferPrefixes': 64, 'maxTransferQueuedPrefixes': 256}
    if not isinstance(settings, dict) or set(settings) - set(defaults):
        raise ValueError('Unsupported ladder search settings')
    for key, default in defaults.items():
        value = settings.setdefault(key, default)
        if key == 'requireLadder':
            if type(value) is not bool:
                raise ValueError('requireLadder must be boolean')
        else:
            integer(value, key, 0 if key in ('entryDelay', 'minClimbInputs', 'maxContactVariants', 'maxTransferPrograms', 'maxTransferWindows') else 1,
                    200 if key == 'maxInteractions' else 5 if key == 'entryDelay' else 200000)
    if settings['maxInputs'] > 200 or settings['maxEntryInputs'] > 199:
        raise ValueError('Ladder input horizons exceed 200')
    return settings
