import json


_VARIABLE_ROUTE_FIELDS = {'airtimes', 'constraints', 'objective', 'guideObjective', 'warmYaw', 'verticalProfiles',
    'movementUnits', 'contactEvents', 'ceilingEvents', 'terminalSupportRequired', 'profileDerivation'}


def _rows(rows):
    return {json.dumps({k:v for k,v in row.items() if k != 'name'}, sort_keys=True) for row in rows
            if not row.get('name', '').startswith('checkpoint_probe:')}


def context(route, profiles, geometry, scope):
    return json.loads(json.dumps(dict(route={k:v for k,v in route.items() if k not in _VARIABLE_ROUTE_FIELDS},
        constraints=[r for r in route['constraints'] if not r.get('name', '').startswith('checkpoint_probe:')],
        profiles=profiles, geometry=geometry, scope=scope, objective=route.get('guideObjective', route['objective']),
        terminalSupportRequired=route.get('terminalSupportRequired', True),
        probeConstraints=[r for r in route['constraints'] if r.get('name', '').startswith('checkpoint_probe:')]), sort_keys=True))


def compatible(previous, current):
    if not isinstance(previous, dict) or previous.get('route') != current['route'] or previous.get('geometry') != current['geometry'] \
            or previous.get('scope') != current['scope']:
        return False
    if any(previous.get(k) != current.get(k) for k in ('objective', 'probeConstraints', 'terminalSupportRequired')):
        return False
    if not _rows(previous.get('constraints', [])) <= _rows(current['constraints']):
        return False
    old, new = previous.get('profiles', []), current['profiles']
    if old == new:
        return True
    if len(old) != 1 or len(new) != 1 or old[0].get('composition') != 'movement_program' \
            or new[0].get('composition') != 'movement_program':
        return False
    old, new = old[0], new[0]
    n = old['airtime']
    if n > new['airtime']:
        return False
    for key in ('position', 'velocity', 'grounded', 'ladderTicks', 'jump', 'sneak'):
        if old[key] != new[key][:len(old[key])]:
            return False
    for key, time_key, inclusive in [('contacts', 'state', True), ('contactEvents', 'offset', False),
                                    ('caseEvents', 'input', False)]:
        prior = [{k:v for k,v in e.items() if k != 'anchors'} for e in old.get(key, [])]
        later = [{k:v for k,v in e.items() if k != 'anchors'} for e in new.get(key, [])
                 if (e[time_key] <= n if inclusive else e[time_key] < n)]
        if prior != later:
            return False
    return _rows(old.get('positionConstraints', [])) <= _rows(new.get('positionConstraints', []))


def branch_key(rows, passages, hits):
    return json.dumps([sorted({(r['leg'], r['offset'], r['mode'], r['cmp'], r['rhs']) for r in rows}),
        sorted((p['leg'], p['solidIndex'], tuple((f['axis'], f['direction'], f['plane']) for f in p['faces']), tuple(p['gates'])) for p in passages),
        sorted((h['leg'], h['offset'], h['axis'], h['direction'], h['solidIndex']) for h in hits)], sort_keys=True)
