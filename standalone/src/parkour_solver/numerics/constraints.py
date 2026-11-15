"""Equivalent numerical rows; keep the caller's full constraint list intact."""


def reduce_walls(walls):
    groups = {}
    for index, wall in enumerate(walls):
        mode, t2 = wall['mode'], wall.get('t2')
        simple = mode in ('X', 'Z') and t2 is None and wall['cmp'] in ('GE', 'LE')
        # Facing residuals wrap, so different facing bounds are not ordered like
        # scalar X/Z bounds. Relative and cross-axis rows only deduplicate exact
        # expressions; no coordinate rounding or algebraic relaxation occurs.
        op = wall.get('op', 'PLUS') if t2 is not None and mode not in ('DXZ', 'DZX') else None
        key = (mode, wall['t1'], t2, op, wall['cmp'], None if simple else wall['rhs'])
        if key not in groups:
            groups[key] = dict(representative=index, sources=[])
        group = groups[key]
        group['sources'].append(index)
        if simple:
            old = walls[group['representative']]['rhs']
            stronger = wall['rhs'] > old if wall['cmp'] == 'GE' else wall['rhs'] < old
            if stronger:
                group['representative'] = index
    selected = [value['representative'] for value in groups.values()]
    return dict(walls=[walls[i] for i in selected],
                audit=dict(original=len(walls), retained=len(selected),
                    representatives=selected, sourceIndices=[v['sources'] for v in groups.values()]))
