from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.scene.route import HALF_WIDTH


def ladder_columns(cells):
    if not isinstance(cells, list):
        raise ValueError('Climbable cells require a list of integer X/Y/Z cells')
    grouped = {}
    for cell in cells:
        if not isinstance(cell, list) or len(cell) != 3 or any(type(v) is not int for v in cell):
            raise ValueError('Climbable cells require integer X/Y/Z')
        grouped.setdefault((cell[0], cell[2]), set()).add(cell[1])
    columns = []
    for (x, z), heights in sorted(grouped.items()):
        start = previous = None
        for y in sorted(heights):
            if previous is not None and y != previous + 1:
                columns.append({'id': str((x, start, z)), 'x': x, 'z': z, 'minY': start, 'maxY': previous + 1})
                start = None
            if start is None:
                start = y
            previous = y
        if start is not None:
            columns.append({'id': str((x, start, z)), 'x': x, 'z': z, 'minY': start, 'maxY': previous + 1})
    return columns


def _rectangular_regions(cells):
    regions = [dict(c, maxX=c['x'] + 1, maxZ=c['z'] + 1) for c in ladder_columns(cells)]
    changed = True
    while changed:
        changed = False
        for i, a in enumerate(regions):
            for j in range(i + 1, len(regions)):
                b = regions[j]
                if (a['minY'], a['maxY']) != (b['minY'], b['maxY']):
                    continue
                along_x = (a['z'], a['maxZ']) == (b['z'], b['maxZ']) and (a['maxX'] == b['x'] or b['maxX'] == a['x'])
                along_z = (a['x'], a['maxX']) == (b['x'], b['maxX']) and (a['maxZ'] == b['z'] or b['maxZ'] == a['z'])
                if along_x or along_z:
                    a.update(x=min(a['x'], b['x']), maxX=max(a['maxX'], b['maxX']),
                             z=min(a['z'], b['z']), maxZ=max(a['maxZ'], b['maxZ']))
                    a['id'] = str((a['x'], a['minY'], a['z'], a['maxX'], a['maxY'], a['maxZ']))
                    regions.pop(j)
                    changed = True
                    break
            if changed:
                break
    return regions


def ladder_regions(cells):
    ladder_columns(cells)
    remaining = {tuple(c) for c in cells}
    regions = []
    while remaining:
        first = min(remaining)
        remaining.remove(first)
        component, pending = {first}, [first]
        while pending:
            x, y, z = pending.pop()
            for neighbour in ((x-1,y,z), (x+1,y,z), (x,y-1,z), (x,y+1,z), (x,y,z-1), (x,y,z+1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    component.add(neighbour)
                    pending.append(neighbour)
        volumes = _rectangular_regions([list(c) for c in sorted(component)])
        if len(volumes) == 1:
            regions.append(volumes[0])
        else:
            regions.append({'id': 'region:' + repr(sorted(component)), 'x': min(c[0] for c in component),
                'maxX': max(c[0] for c in component) + 1, 'z': min(c[2] for c in component),
                'maxZ': max(c[2] for c in component) + 1, 'minY': min(c[1] for c in component),
                'maxY': max(c[1] for c in component) + 1, 'volumes': volumes})
    return regions


def region_rectangles(column, y):
    return [[c['x'], c.get('maxX', c['x'] + 1), c['z'], c.get('maxZ', c['z'] + 1)]
            for c in column.get('volumes', [column]) if c['minY'] <= y < c['maxY']]


def region_contains(column, position):
    return any(b[0] <= position[0] < b[1] and b[2] <= position[2] < b[3]
               for b in region_rectangles(column, position[1]))


def region_axis(column, axis):
    key = 'x' if axis == 'X' else 'z'
    return column[key], column.get('maxX' if axis == 'X' else 'maxZ', column[key] + 1)


def climb_walls(column, solids):
    groups = {}
    for index, box in enumerate(solids):
        if box[1] >= column['maxY'] + BODY_HEIGHT or box[4] <= column['minY']:
            continue
        for axis, a, tangent, c in [('X', 0, 2, column['x']), ('Z', 2, 0, column['z'])]:
            cell_tangent = column['z'] if axis == 'X' else column['x']
            tangent_hi = region_axis(column, 'Z' if axis == 'X' else 'X')[1]
            if box[tangent] >= tangent_hi + HALF_WIDTH or box[tangent + 3] <= cell_tangent - HALF_WIDTH:
                continue
            for direction in (-1, 1):
                plane = box[a] - HALF_WIDTH if direction > 0 else box[a + 3] + HALF_WIDTH
                if c <= plane < region_axis(column, axis)[1]:
                    key = axis, direction, plane
                    groups.setdefault(key, []).append((index, box))
    return [{'axis': axis, 'direction': direction, 'plane': plane,
             'parts': [{'solidIndex': i, 'box': b} for i, b in parts]}
            for (axis, direction, plane), parts in groups.items()]


def movement_contexts(column, solids, *, include_fall=True):
    contexts = climb_walls(column, solids)
    if include_fall:
        contexts.append({'mode': 'fall_only', 'axis': 'X', 'direction': 1,
                         'plane': sum(region_axis(column, 'X')) / 2, 'parts': []})
    return contexts


def entry_channels(column, wall):
    if wall.get('mode') == 'fall_only':
        return [{'family': 'side', 'channel': 'side:' + axis + ':' + str(direction),
                 'axis': axis, 'direction': direction, 'plane': region_axis(column, axis)[1 if direction > 0 else 0]}
                for axis in ('X', 'Z') for direction in (-1, 1)]
    axis = wall['axis']
    side = 'Z' if axis == 'X' else 'X'
    result = []
    for family, face_axis, direction in [('front', axis, -wall['direction']), ('side', side, -1), ('side', side, 1)]:
        lo, hi = region_axis(column, face_axis)
        result.append({'family': family, 'channel': family + ':' + face_axis + ':' + str(direction),
                       'axis': face_axis, 'direction': direction, 'plane': hi if direction > 0 else lo})
    return result


def outside_row(face, state, margin, name):
    return {'mode': face['axis'], 't1': state, 'cmp': 'GE' if face['direction'] > 0 else 'LE',
            'rhs': face['plane'] + face['direction'] * margin, 'name': name}


def schedule_constraints(schedule, column, wall, entry_face, exit_face, *, start=0, margin=1e-6):
    rows, contacts, scene_contacts = [], [], []
    ys = schedule['trace']['position']
    ladder = schedule['ladderTicks']
    for state, (y, inside) in enumerate(zip(ys, ladder)):
        if state < schedule.get('constraintStart', 0) or state >= schedule.get('constraintEnd', len(ys)):
            continue
        at = start + state
        if inside:
            rectangles = region_rectangles(column, y)
            if not rectangles:
                raise ValueError('Ladder occupancy has no cells at this height')
            choice = schedule.get('corridorChoices', {}).get(str(state), 0)
            if type(choice) is not int or not 0 <= choice < len(rectangles):
                raise ValueError('Invalid exact ladder corridor choice')
            bounds = rectangles[choice]
            for axis, lo, hi in [('X', bounds[0], bounds[1]), ('Z', bounds[2], bounds[3])]:
                rows.extend([{'mode': axis, 't1': at, 'cmp': 'GE', 'rhs': lo + margin, 'name': 'ladder:inside'},
                             {'mode': axis, 't1': at, 'cmp': 'LE', 'rhs': hi - margin, 'name': 'ladder:inside'}])
        elif state < schedule['entryState']:
            rows.append(outside_row(entry_face, at, margin, 'ladder:before_entry'))
        elif column['minY'] <= y < column['maxY'] and exit_face is not None:
            rows.append(outside_row(exit_face, at, margin, 'ladder:after_exit'))
        if any(y < part['box'][4] and y + BODY_HEIGHT > part['box'][1] for part in wall['parts']):
            rows.append({'mode': wall['axis'], 't1': at, 'cmp': 'LE' if wall['direction'] > 0 else 'GE',
                         'rhs': wall['plane'], 'name': 'ladder:wall_avoid'})
    for tick, hit in enumerate(schedule['hit']):
        if not hit:
            continue
        y = ys[tick + 1]
        parts = [p for p in wall['parts'] if y < p['box'][4] and y + BODY_HEIGHT > p['box'][1]]
        if not parts:
            raise ValueError('Selected climb has no finite wall at its Y stage')
        axis = 0 if wall['axis'] == 'X' else 1
        contacts.append({'state': start + tick + 1, 'axis': axis, 'plane': wall['plane'], 'direction': wall['direction']})
        tangent, a, stage = ('Z', 2, tick) if axis == 0 else ('X', 0, tick + 1)
        corridors = []
        for part in sorted(parts, key=lambda p: p['box'][a]):
            lo, hi = part['box'][a] - HALF_WIDTH, part['box'][a + 3] + HALF_WIDTH
            if corridors and lo < corridors[-1][1]:
                corridors[-1][1] = max(corridors[-1][1], hi)
                corridors[-1][2].append(part['box'])
            else:
                corridors.append([lo, hi, [part['box']]])
        choice = schedule.get('faceCorridorChoices', {}).get(str(tick), 0)
        if type(choice) is not int or not 0 <= choice < len(corridors):
            raise ValueError('Invalid finite wall corridor')
        lo, hi, boxes = corridors[choice]
        scene_contacts.append(dict(contacts[-1], axis=0 if axis == 0 else 2,
                                   **({'box': boxes[0]} if len(boxes) == 1 else {'boxes': boxes})))
        rows.extend([{'mode': tangent, 't1': start + stage, 'cmp': 'GE', 'rhs': lo + margin,
                      'name': 'ladder:finite_face'},
                     {'mode': tangent, 't1': start + stage, 'cmp': 'LE', 'rhs': hi - margin,
                      'name': 'ladder:finite_face'}])
    return rows, contacts, scene_contacts


def exit_faces(column, target):
    faces = [{'axis': axis, 'direction': sign, 'plane': region_axis(column, axis)[1 if sign > 0 else 0]}
             for axis, key in [('X', 'x'), ('Z', 'z')] for sign in (-1, 1)]
    return sorted(faces, key=lambda f: abs((target[0] if f['axis'] == 'X' else target[1]) - f['plane'])
                  - f['direction'] * ((target[0] if f['axis'] == 'X' else target[1]) - f['plane']))
