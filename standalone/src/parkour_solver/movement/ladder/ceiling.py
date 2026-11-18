import copy

from parkour_solver.movement.ceiling_profiles import ceiling_sequence
from parkour_solver.movement.units import ladder_units


def _unit_at(units, tick):
    return next((unit for unit in units if unit['start'] <= tick < unit['end']), None)


def own_ceiling_contacts(contacts, units):
    contacts, units = copy.deepcopy(list(contacts)), copy.deepcopy(list(units))
    for unit in units:
        if 'effects' in unit:
            unit['effects'] = [effect for effect in unit['effects'] if effect['type'] != 'ladder_ceiling']
            if not unit['effects']:
                unit.pop('effects')
    for contact in contacts:
        if contact['axis'] != 'Y' or contact.get('role') != 'ceiling':
            continue
        tick = contact['offset']
        owner = _unit_at(units, tick)
        following = _unit_at(units, tick + 1)
        if owner is None or owner['type'] != 'ladder_climb':
            if contact.get('effect') == 'ladder_ceiling':
                raise ValueError('Ladder ceiling requires a climbing owner')
            continue
        if following is None or following['type'] != 'ladder_climb':
            raise ValueError('Ladder ceiling must be followed by ladder_climb')
        if 'effects' in owner:
            owner['effects'] = [effect for effect in owner['effects']
                                if effect['type'] != 'ceiling' or effect['start'] != tick]
        contact.update(effect='ladder_ceiling', ownerType='ladder_climb', successorType='ladder_climb')
        if owner.get('region') is not None:
            contact['ownerRegion'] = owner['region']
        else:
            contact.pop('ownerRegion', None)
        effect = dict(type='ladder_ceiling', case='ceiling_hit', start=tick, end=tick + 1,
                      successorType='ladder_climb', anchors={'hit': tick + 1, 'nextClimb': tick + 1},
                      plane=contact['plane'], boxes=copy.deepcopy(contact['boxes']),
                      surface=contact.get('surface'), regions=copy.deepcopy(contact['regions']))
        if owner.get('region') is not None:
            effect['region'] = owner['region']
        owner.setdefault('effects', []).append(effect)
    return contacts, units


def own_schedule_ceilings(schedule, region_id=None):
    if not any(e['axis'] == 'Y' and e.get('role') == 'ceiling' for e in schedule.get('contactEvents', [])):
        return schedule
    result = copy.deepcopy(schedule)
    phases = ladder_units(result, region_id)
    contacts, _ = own_ceiling_contacts(result['contactEvents'], phases)
    result['contactEvents'] = contacts
    result['ladderCeilingEvents'] = [copy.deepcopy(e) for e in contacts if e.get('effect') == 'ladder_ceiling']
    for key in ('generatedUnits', 'movementUnits'):
        if key in result:
            _, result[key] = own_ceiling_contacts(contacts, result[key])
    return result


def predict_ceiling_sequences(schedule, option, *, start=0, end=None):
    sequence = ceiling_sequence(schedule, option, start=start)
    end = schedule['ticks'] if end is None else end
    phases = ladder_units(schedule) if 'entryState' in schedule else []
    selected = []
    for contact in sequence:
        tick = contact['offset']
        if tick >= end:
            continue
        owner = _unit_at(phases, tick)
        following = _unit_at(phases, tick + 1)
        if owner is not None and owner['type'] == 'ladder_climb' and (following is None or following['type'] != 'ladder_climb'):
            continue
        selected.append(contact)
    if not selected:
        return []
    result = [selected]
    result.extend(selected[:length] for length in range(1, len(selected)))
    return result
