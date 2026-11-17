"""Declared scene objective schema; no search dependency."""


def objective_spec(value=None):
    spec = dict(value) if isinstance(value, dict) else {'axis': 'X', 'sense': 'MAX'} if value is None else None
    if spec is None or set(spec) - {'axis', 'sense'}:
        raise ValueError('Objective must contain only axis and sense')
    if spec.get('axis') not in ('X', 'Z') or spec.get('sense') not in ('MAX', 'MIN'):
        raise ValueError('Objective requires axis X/Z and sense MAX/MIN')
    return spec
