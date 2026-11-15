import math
import struct


def f32(value):
    return struct.unpack('f', struct.pack('f', value))[0]


CAP = f32(0.15)
GRAVITY = 0.08
Y_DRAG = f32(0.98)
CLIMB_CARRY = (0.2 - GRAVITY) * Y_DRAG
REST_CARRY = -GRAVITY * Y_DRAG


def clamp(value):
    return max(-CAP, min(CAP, value))


def branch(value):
    return -1 if value < -CAP else 1 if value > CAP else 0


def vertical_move(config, tick, velocity, on_ladder):
    value = 0.0 if abs(velocity) < config['threshold'] else velocity
    jump = config['jumpVelocity'][tick]
    if jump is not None:
        value = jump
    if on_ladder:
        value = max(value, -0.15)
        if config['sneak'][tick] and value < 0:
            value = 0.0
    return value


def vertical_carry(movement, on_ladder, previous_horizontal_collision):
    if on_ladder and previous_horizontal_collision:
        return CLIMB_CARRY
    return (movement - GRAVITY) * Y_DRAG


def vertical_step(config, tick, position, velocity, on_ladder, grounded, tolerance):
    movement = vertical_move(config, tick, velocity, on_ladder)
    proposed = position + movement
    carry = movement
    ground = False
    failures = []
    if config['jumpVelocity'][tick] is not None and not grounded:
        failures.append({'state': tick, 'reason': 'jump_requires_ground'})
    for contact in config['contacts']:
        if contact['state'] != tick + 1:
            continue
        plane, direction = contact['plane'], contact['direction']
        if contact.get('case'):
            kind = contact['case']
            delta = plane-position
            lower = contact.get('lowerHeight',position)
            upper = contact.get('higherHeight',plane)
            jumping = config['jumpVelocity'][tick] is not None
            valid = False
            if kind == 'normal_step':
                valid = grounded and not jumping and 0 < delta <= f32(.6)+tolerance
            elif kind == 'slab_boost':
                valid = grounded and jumping and movement < delta <= f32(.6)+tolerance
            elif kind in ('blip','blip_up','corner_blip_up'):
                valid = not grounded and movement < 0 and position >= lower and proposed < lower
                if kind == 'blip':
                    valid = valid and 0 <= delta <= f32(.6)+tolerance
                else:
                    valid = valid and plane == position
                    valid = valid and (position > upper if kind=='blip_up' else 0 <= upper-position <= f32(.6)+tolerance)
            elif kind == 'corner_jump_cancel':
                valid = grounded and jumping and f32(.42) < upper-position <= f32(.6)+tolerance and plane==position
            elif kind == 'grind':
                valid = grounded and jumping and 0 <= contact.get('clearance',-1) < movement and plane==position
            if valid:
                proposed, carry = plane, 0.0
                ground = kind in ('normal_step','blip','blip_up','corner_blip_up')
            else:
                failures.append({'state':tick+1,'reason':'invalid_movement_case','case':kind})
        elif contact.get('step', False):
            valid = ((grounded or movement < 0) and movement < plane - position
                     and 0 < plane - position <= f32(0.6) + tolerance)
            if valid:
                proposed, carry, ground = plane, 0.0, movement < 0
            else:
                failures.append({'state': tick + 1, 'reason': 'invalid_step'})
        else:
            approach = direction * (position - plane)
            reach = direction * (proposed - plane)
            if approach > tolerance or reach < 0:
                failures.append({'state': tick + 1, 'reason': 'missed_vertical_contact',
                                 'shortBy': max(approach, -reach, 0.0)})
            else:
                proposed, carry = plane, 0.0
                ground = direction < 0 and movement < 0
    if not math.isfinite(proposed) or not math.isfinite(carry):
        raise ValueError('Non-finite vertical replay')
    return proposed, carry, ground, failures
