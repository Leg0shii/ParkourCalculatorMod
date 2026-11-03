import copy

from standalone.ipopt.model import integer
from .movement_units import MOVEMENT_TYPES, movement_unit


GENERATORS = {}


def register_movement_type(name, generator, *, ladder=False):
    if not isinstance(name, str) or not name or not callable(getattr(generator, 'generate', None)):
        raise ValueError('A movement type requires a name and generator')
    MOVEMENT_TYPES[name] = {'ladder': ladder}
    GENERATORS[name] = generator


def generate_choices(kind, context, **options):
    if context.get('mode') == 'open' and (options.get('start', 0) not in (0, None) or options.get('ends') is not None):
        raise ValueError('Open choices use relative inputs; the composer owns retiming')
    if kind not in GENERATORS:
        raise ValueError('Unsupported movement generator: ' + str(kind))
    if kind=='normal' and context.get('mode')=='open' and context.get('terminalGoal') is not None:
        from .terminal_goals import finish_choices
        yield from finish_choices(context)
    for choice in GENERATORS[kind].generate(context, **options):
        if choice['unit']['type'] != kind:
            raise ValueError('Movement generator returned a different type')
        yield choice


def _unit(kind, context, start, end, **parameters):
    unit = movement_unit(kind, start, end, **parameters)
    if MOVEMENT_TYPES[kind]['ladder']:
        unit.update(region=context.get('regionId'), occupiedStates=[max(context['entryState'], start),
                    min(context['exitState'], end)], sneakInputs=[])
    return unit


class NormalGenerator:
    def sneak_inputs(self, unit, context, primary):
        return range(unit['start'], unit['end'])

    def generate(self, context, *, start=0, ends=None):
        if context.get('mode') == 'open':
            from .open_movement import normal_choices
            yield from normal_choices(context)
            return
        if context.get('mode') in ('support', 'profile'):
            from .block_profiles import ordinary_jump_profile, runoff_profile
            profiles = [(context.get('profileKind', 'ordinary'), context['profile'])] if context.get('mode') == 'profile' else [
                (name, derive(context['sourceTop'], context['targetTop'], max_inputs=context.get('maxInputs', 200)))
                for name, derive in [('ordinary', ordinary_jump_profile), ('run_off', runoff_profile)]]
            for name, profile in profiles:
                if profile is None:
                    continue
                end = start + profile['airtime']
                unit = movement_unit('normal', start, end, jumpInputs=[start] if profile['jump'][0] else [],
                    entryCondition={'kind': 'support', 'top': context['sourceTop']},
                    exitCondition={'kind': 'support', 'top': context['targetTop']})
                yield {'unit': unit, 'profile': profile, 'profileKind': name}
            return
        for end in ends if ends is not None else context.get('ends', []):
            if end > start:
                yield {'unit': _unit('normal', context, start, end), 'primaryHitInputs': []}


class LadderFallGenerator:
    def sneak_inputs(self, unit, context, primary):
        return range(unit['start'], unit['end'])

    def generate(self, context, *, start=0, ends=None):
        if context.get('mode') == 'open':
            from .open_ladder import ladder_choices
            yield from ladder_choices(context, 'ladder_fall')
            return
        entry, leave = context['entryState'], context['exitState']
        forced = {e['offset'] for e in context.get('contactEvents', [])
                  if e['axis'] != 'Y' and entry <= e['offset'] + 1 < leave}
        for end in ends if ends is not None else range(start + 1, leave + 1):
            if end <= start or any(start <= t < end for t in forced):
                continue
            yield {'unit': _unit('ladder_fall', context, start, end), 'primaryHitInputs': []}


class LadderClimbGenerator:
    def sneak_inputs(self, unit, context, primary):
        return [t for t in range(unit['start'], unit['end']) if t not in primary or t == context['entryState']]

    def _choice(self, context, primary, window, beginning=False, ending=False):
        entry, leave = context['entryState'], context['exitState']
        forced = {e['offset'] for e in context.get('contactEvents', [])
                  if e['axis'] != 'Y' and entry <= e['offset'] + 1 < leave}
        actual = sorted(set(primary) | forced)
        if not actual or actual != list(range(actual[0], actual[-1] + 1)):
            return None
        first, end = window if window is not None else (actual[0], actual[-1] + 1)
        first, end = min(first, actual[0]), max(end, actual[-1] + 1)
        if first < max(0, entry - 1) or end > leave - 1:
            return None
        stops = [t for t in range(first, end) if t not in actual]
        if any(t not in (first, end - 1) for t in stops) or len(stops) > 2:
            return None
        unit_end = leave if end == leave - 1 else end
        if context['exitKind'] == 'top' and unit_end != leave:
            return None
        unit = _unit('ladder_climb', context, first, unit_end,
                     hitWindow=[first, end], stopInputs=stops, hitInputs=actual)
        return {'unit': unit, 'primaryHitInputs': primary, 'climbWindow': window,
                'beginningStop': beginning, 'endStop': ending}

    def generate(self, context, *, start=None, ends=None, contact_only=False):
        if context.get('mode') == 'open':
            from .open_ladder import ladder_choices
            yield from ladder_choices(context, 'ladder_climb')
            return
        if not contact_only and not context.get('allowPrimaryClimb', True):
            return
        if contact_only:
            choice = self._choice(context, [], None)
            if choice is not None:
                yield choice
            return
        from .ladder_schedules import climb_variants
        leave = context['exitState']
        firsts = [start] if start is not None else range(
            max(0, context['entryState'] - 1, context.get('firstHitLowerBound', 0)), leave - 1)
        for first in firsts:
            limits = ends if ends is not None else ([leave - 1] if context['exitKind'] == 'top'
                                                   else range(leave - 1, first, -1))
            for end in limits:
                for variant in climb_variants(first, end, context['ticks']):
                    primary = [t for t, hit in enumerate(variant['hit']) if hit]
                    if not primary:
                        continue
                    choice = self._choice(context, primary, variant['climbWindow'],
                                          variant['beginningStop'], variant['endStop'])
                    if choice is not None:
                        yield choice


def generate_unit_programs(context):
    n, entry, leave = context['ticks'], context['entryState'], context['exitState']
    integer(n, 'movement generation ticks', 1, 200)
    first = max(0, entry - 1)
    before = [c['unit'] for c in generate_choices('normal', context, start=0, ends=[first])]
    after = [c['unit'] for c in generate_choices('normal', context, start=leave, ends=[n])]
    if context['exitKind'] != 'top':
        for choice in generate_choices('ladder_fall', context, start=first, ends=[leave]):
            yield {'units': before + [choice['unit']] + after, 'hit': [False] * n,
                   'climbWindow': None, 'stopInputs': [], 'beginningStop': False, 'endStop': False}
    for contact_only in (True, False):
        for choice in generate_choices('ladder_climb', context, contact_only=contact_only):
            unit = choice['unit']
            leading = [c['unit'] for c in generate_choices('ladder_fall', context, start=first, ends=[unit['start']])]
            trailing = [c['unit'] for c in generate_choices('ladder_fall', context, start=unit['end'], ends=[leave])]
            if first < unit['start'] and not leading or unit['end'] < leave and not trailing:
                continue
            primary = set(choice['primaryHitInputs'])
            window = choice['climbWindow']
            yield {'units': before + leading + [unit] + trailing + after,
                   'hit': [t in primary for t in range(n)], 'climbWindow': window,
                   'stopInputs': [t for t in range(*window) if t not in primary] if window else [],
                   'beginningStop': choice['beginningStop'], 'endStop': choice['endStop']}


def materialize_generated_units(units, ticks, sneak):
    selected = []
    for template in units:
        if template['start'] >= ticks:
            continue
        unit = copy.deepcopy(template)
        unit['end'] = min(unit['end'], ticks)
        unit['sneakInputs'] = [t for t in range(unit['start'], unit['end']) if sneak[t]]
        selected.append(unit)
    return selected


def sneak_domain(units, context, primary):
    selected = set()
    for unit in units:
        generator = GENERATORS[unit['type']]
        if callable(getattr(generator, 'sneak_inputs', None)):
            selected.update(generator.sneak_inputs(unit, context, primary))
    return sorted(selected)


from .ceiling_generation import CeilingGenerator
from .stepping_generation import SteppingGenerator


register_movement_type('ceiling', CeilingGenerator())
register_movement_type('stepping', SteppingGenerator())
register_movement_type('normal', NormalGenerator())
register_movement_type('ladder_climb', LadderClimbGenerator(), ladder=True)
register_movement_type('ladder_fall', LadderFallGenerator(), ladder=True)
