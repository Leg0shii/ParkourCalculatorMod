"""Clarabel solve of the acceleration-disk relaxation of a threshold-0 request.

Each tick's horizontal acceleration ``|a[t]| == magnitude[t]`` becomes
``|a[t]| <= magnitude[t]``. Every other row is the IPOPT worker's: exact
dynamics, fixed starts, contact resets and EQ walls in the zero cone, contact
approach/reach and LE/GE walls as nonnegative rows. Infeasibility therefore
excludes the full request; a feasible point is a relaxed candidate.
"""
import collections
import json
import math
import time

MODEL_VERSION = 1
ALLOWANCE = 1e-9
PENETRATION = 1e-7
CAP = 0.1
MAX_ITER = 200
TOL = 1e-9
INFEASIBILITY_TOL = 1e-8
ALARM_FRACTION = 0.01
MATERIAL_GAP = 1e-4

FEASIBLE = ('Solved', 'AlmostSolved')
INFEASIBLE = ('PrimalInfeasible', 'AlmostPrimalInfeasible')

STATS = dict(screen=collections.Counter(), candidate=collections.Counter())
_DECIDED = {}


def spec(problem, extra_rows=(), objective=None, *, strict):
    """Canonical numerical content, or None outside the supported class.

    Screens (strict=False) drop rows the relaxation cannot express, which keeps
    them a relaxation; candidates (strict=True) must keep every row.
    """
    if problem.get('threshold', 0.) != 0 or any(problem.get('ladderTicks', [])) or 'inertiaPattern' in problem:
        return None
    n = problem['ticks']
    rows = []
    for r in list(problem.get('walls', []))+list(extra_rows):
        t, t2 = r.get('t1', r.get('offset')), r.get('t2')
        if (r.get('mode') in ('X', 'Z') and type(t) is int and 0 <= t <= n
                and (t2 is None or type(t2) is int and 0 <= t2 <= n)):
            rows.append((r['mode'], t, t2, r.get('op', 'PLUS'), r['cmp'], r['rhs']))
        elif strict:
            return None
    result = {k: problem[k] for k in ('ticks', 'position', 'velocity', 'magnitude', 'friction')}
    result.update(version=MODEL_VERSION, rows=sorted(set(rows), key=str), contacts=problem.get('contacts', []),
                  initialIntervals=problem.get('initialIntervals'))
    if problem.get('objective') is not None and problem.get('objectiveMode') != 'constant_zero':
        objective = problem['objective']
    elif objective is not None:
        objective = dict(objective, tick=n)
    if objective is not None:
        result['objective'] = {k: objective.get(k) for k in ('tick', 'axis', 'sense', 'customYaw', 'type')}
    return result


def solve(content, *, seconds, kind='candidate'):
    """Solve a spec; decided results are cached process-wide by content."""
    encoded = json.dumps(content, sort_keys=True)
    stats = STATS[kind]
    if encoded in _DECIDED:
        stats['cacheHits'] += 1
        return dict(_DECIDED[encoded], cached=True)
    stats['solved'] += 1
    try:
        result = _solve(content, min(CAP, seconds))
    except (ValueError, RuntimeError) as error:
        result = dict(status='exception', outcome='undecided', controls=None, objective=None,
                      iterations=0, message=repr(error))
    stats[result['outcome']] += 1
    if result['outcome'] != 'undecided':
        _DECIDED[encoded] = result
        while len(_DECIDED) > 1024:
            _DECIDED.pop(next(iter(_DECIDED)))
    return dict(result, cached=False)


def screen(model, extra_rows=(), objective=None):
    """Interval-screen refinement: None when not applicable, else a verdict."""
    content = spec(model, extra_rows, objective, strict=False)
    if content is None or not content['rows'] and not content['contacts']:
        return None
    result = solve(content, seconds=CAP, kind='screen')
    checked = dict(outcome=result['outcome'], status=result['status'])
    if result['outcome'] == 'infeasible':
        checked['proof'] = dict(reason='disk_control_relaxation_infeasible', relaxation='acceleration_disk',
                                status=result['status'])
    elif result['outcome'] == 'undecided':
        checked['message'] = result.get('message', result['status'])
    return checked


def deficits(problem, controls):
    """Per-tick relative acceleration shortfall: (peak, values, tick of peak)."""
    values = [max(0., m-math.hypot(*c))/m if m > 0 else 0. for m, c in zip(problem['magnitude'], controls)]
    peak = max(values, default=0.)
    return peak, values, values.index(peak) if values else None


def _solve(content, time_limit):
    import clarabel
    import numpy as np
    from scipy.sparse import coo_matrix, csc_matrix
    started = time.perf_counter()
    n = content['ticks']
    width = 3*n+2

    def pos(axis, t): return axis*width+t
    def vel(axis, t): return axis*width+n+1+t
    def control(axis, t): return axis*width+2*(n+1)+t
    equalities, inequalities = ([], [], [], []), ([], [], [], [])  # rows, columns, values, rhs

    def row(block, entries, rhs):
        index = len(block[3])
        for column, value in entries.items():
            block[0].append(index); block[1].append(column); block[2].append(value)
        block[3].append(rhs)

    contacts = {(c['state'], c['axis']): c for c in content['contacts']}
    initial = content['initialIntervals']
    for axis in range(2):
        if initial is None:
            row(equalities, {pos(axis, 0): 1.}, 0.)
            row(equalities, {vel(axis, 0): 1.}, content['velocity'][axis])
        else:
            # Interval starts are absolute; positions are offsets from `position`.
            origin = content['position'][axis]
            row(inequalities, {pos(axis, 0): -1.}, origin-initial[0][axis])
            row(inequalities, {pos(axis, 0): 1.}, initial[1][axis]-origin)
            row(inequalities, {vel(axis, 0): -1.}, -initial[2][axis])
            row(inequalities, {vel(axis, 0): 1.}, initial[3][axis])
        for t in range(n):
            contact = contacts.get((t+1, axis))
            if contact is None:
                row(equalities, {pos(axis, t+1): 1., pos(axis, t): -1., vel(axis, t): -1., control(axis, t): -1.}, 0.)
                friction = content['friction'][t]
                row(equalities, {vel(axis, t+1): 1., vel(axis, t): -friction, control(axis, t): -friction}, 0.)
            else:
                plane, d = contact['plane']-content['position'][axis], float(contact['direction'])
                row(equalities, {pos(axis, t+1): 1.}, plane)
                row(equalities, {vel(axis, t+1): 1.}, 0.)
                row(inequalities, {pos(axis, t): d}, d*plane)
                row(inequalities, {pos(axis, t): -d, vel(axis, t): -d, control(axis, t): -d}, -d*plane-PENETRATION)
    for mode, t, t2, op, cmp, rhs in content['rows']:
        axis = 0 if mode == 'X' else 1
        entries, offset = {pos(axis, t): 1.}, content['position'][axis]
        if t2 is not None:
            sign = -1. if op == 'MINUS' else 1.
            entries[pos(axis, t2)] = entries.get(pos(axis, t2), 0.)+sign
            offset += sign*content['position'][axis]
        if cmp == 'EQ':
            row(equalities, entries, rhs-offset)
        elif cmp == 'LE':
            row(inequalities, entries, rhs-offset)
        else:
            row(inequalities, {k: -v for k, v in entries.items()}, offset-rhs)
    disks = ([], [], [], [])
    for t, magnitude in enumerate(content['magnitude']):
        disks[3].extend([magnitude+ALLOWANCE, 0., 0.])
        for axis in range(2):
            disks[0].append(3*t+1+axis); disks[1].append(control(axis, t)); disks[2].append(-1.)
    blocks = [equalities, inequalities, disks]
    offsets = [sum(len(b[3]) for b in blocks[:i]) for i in range(len(blocks))]
    matrix = coo_matrix((np.concatenate([np.asarray(b[2], dtype=float) for b in blocks]),
                         (np.concatenate([np.asarray(b[0], dtype=int)+o for b, o in zip(blocks, offsets)]),
                          np.concatenate([np.asarray(b[1], dtype=int) for b in blocks]))),
                        shape=(sum(len(b[3]) for b in blocks), 2*width)).tocsc()
    rhs = np.concatenate([np.asarray(b[3], dtype=float) for b in blocks])
    cost, offset, sense = _cost(content, width, pos)
    cones = [cone(len(block[3])) for cone, block in ((clarabel.ZeroConeT, equalities),
                                                     (clarabel.NonnegativeConeT, inequalities)) if block[3]]
    cones += [clarabel.SecondOrderConeT(3) for _ in range(n)]
    settings = clarabel.DefaultSettings()
    settings.verbose = False
    settings.max_threads = 1
    settings.direct_solve_method = 'qdldl'
    settings.time_limit = time_limit
    settings.max_iter = MAX_ITER
    settings.tol_feas = settings.tol_gap_abs = settings.tol_gap_rel = TOL
    settings.tol_infeas_abs = settings.tol_infeas_rel = INFEASIBILITY_TOL
    assembled = time.perf_counter()
    solver = clarabel.DefaultSolver(csc_matrix((2*width, 2*width)), cost, matrix, rhs, cones, settings)
    ready = time.perf_counter()
    solution = solver.solve()
    finished = time.perf_counter()
    status = str(solution.status)
    x = np.asarray(solution.x)
    controls = objective = None
    if status in FEASIBLE and np.isfinite(x).all():
        outcome = 'feasible'
        controls = [[float(x[control(axis, t)]) for axis in range(2)] for t in range(n)]
        objective = offset+sense*float(cost@x) if sense else None
    else:
        outcome = 'infeasible' if status in INFEASIBLE else 'undecided'
    return dict(status=status, outcome=outcome, controls=controls, objective=objective,
                iterations=int(solution.iterations), assemblySeconds=assembled-started,
                setupSeconds=ready-assembled, solveSeconds=finished-ready)


def _cost(content, width, pos):
    """Minimized cost vector, the world offset and sign recovering the score."""
    import numpy as np
    cost = np.zeros(2*width)
    objective = content.get('objective')
    if objective is None or not 0 <= objective['tick'] <= content['ticks']:
        return cost, 0., 0.
    sign = -1. if objective['sense'] == 'MAX' else 1.
    tick = objective['tick']
    origin = content['position']
    if objective.get('customYaw') is not None:
        angle = math.radians(objective['customYaw'])
        weights = (-math.sin(angle), math.cos(angle))
        motion = objective.get('type') == 'MOTION' and tick > 0
        offset = 0. if motion else weights[0]*origin[0]+weights[1]*origin[1]
        for axis, weight in enumerate(weights):
            cost[pos(axis, tick)] += sign*weight
            if motion:
                cost[pos(axis, tick-1)] -= sign*weight
    else:
        axis = 0 if objective['axis'] == 'X' else 1
        cost[pos(axis, tick)] = sign
        offset = origin[axis]
    # cost@x is sign*(score-offset); the minimizer's sign is its own inverse.
    return cost, offset, sign
