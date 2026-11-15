import math
import time

import numpy as np

from research.route_guidance.graph import carry_vertices, fixed_values


def linear(*pairs):
    result = {}
    for coefficient, variable in pairs:
        result[variable] = result.get(variable, 0.) + coefficient
    return {key: value for key, value in result.items() if value}


class ConeProgram:
    def __init__(self):
        self.names, self.lower, self.upper = [], [], []
        self.equalities, self.inequalities, self.cones = [], [], []

    def variable(self, name, lower, upper):
        index = len(self.names)
        self.names.append(name)
        self.lower.append(lower)
        self.upper.append(upper)
        self.le({index: 1}, upper)
        self.le({index: -1}, -lower)
        return index

    def vector(self, name, lower, upper):
        return [self.variable(name+':'+str(a), lo, hi) for a, (lo, hi) in enumerate(zip(lower, upper))]

    def eq(self, row, rhs=0.):
        self.equalities.append((row, rhs))

    def le(self, row, rhs=0.):
        self.inequalities.append((row, rhs))

    def scaled_bounds(self, variables, lower, upper, mass):
        for var, lo, hi in zip(variables, lower, upper):
            self.le(linear((1, var), (-hi, mass)))
            self.le(linear((-1, var), (lo, mass)))

    def data(self, objective):
        import clarabel
        from scipy import sparse
        rows = self.equalities + self.inequalities
        cones = [clarabel.ZeroConeT(len(self.equalities)), clarabel.NonnegativeConeT(len(self.inequalities))]
        for cone in self.cones:
            rows.extend((row, 0.) for row in cone)
            cones.append(clarabel.SecondOrderConeT(len(cone)))
        ri, ci, values = [], [], []
        for i, (row, _) in enumerate(rows):
            for j, value in row.items():
                ri.append(i)
                ci.append(j)
                values.append(value)
        a = sparse.csc_matrix((values, (ri, ci)), shape=(len(rows), len(self.names)))
        b = np.array([rhs for _, rhs in rows])
        q = np.zeros(len(self.names))
        for j, value in objective.items():
            q[j] = value
        return a, b, q, cones

    def violation(self, values):
        value = lambda row: sum(v*values[j] for j, v in row.items())
        error = max([abs(value(row)-rhs) for row, rhs in self.equalities] + [0.])
        error = max(error, max([value(row)-rhs for row, rhs in self.inequalities] + [0.]))
        for cone in self.cones:
            slack = [-value(row) for row in cone]
            error = max(error, math.hypot(*slack[1:])-slack[0])
        return error

    def upper_value(self, a, b, q, dual):
        z = np.array(dual, dtype=float)
        start = len(self.equalities)
        stop = start+len(self.inequalities)
        z[start:stop] = np.maximum(z[start:stop], 0.)
        for cone in self.cones:
            end = stop+len(cone)
            radius, norm = z[stop], np.linalg.norm(z[stop+1:end])
            if norm <= -radius:
                z[stop:end] = 0.
            elif norm > radius:
                scale = (norm+radius)/2
                z[stop] = scale
                z[stop+1:end] *= scale/norm
            z[stop] = max(z[stop], np.linalg.norm(z[stop+1:end])) + 1e-12
            stop = end
        residual = q+a.T@z
        correction = np.minimum(residual*np.array(self.lower), residual*np.array(self.upper)).sum()
        raw = float(b@z-correction)
        return raw + 1e-9*(1+abs(raw)), float(np.max(np.abs(residual), initial=0))


def build_conic(family, fixed=None, *, carry_pattern=None):
    started = time.monotonic()
    p = ConeProgram()
    n, edges = family['ticks'], family['edges']
    if carry_pattern is not None and (len(carry_pattern) != n or any(
            len(row) != 2 or any(type(b) is not int or b not in (-1, 0, 1) for b in row) for row in carry_pattern)):
        raise ValueError('A restricted carry pattern needs two signed/zero branches per input')
    states = [p.vector('state:'+str(t), *family['bounds'][t]) for t in range(n+1)]
    masses = [p.variable('mass:'+str(i), 0, 1) for i in range(len(edges))]
    choices = [p.variable('choice:'+str(i), 0, 1) for i in range(len(family['options']))]
    for group in family['groups']:
        p.eq(linear(*[(1, choices[i]) for i in group]), 1)
    edge_zeros, option_zeros = fixed_values(family, fixed or {})
    for i in edge_zeros:
        p.eq({masses[i]: 1})
    for i in option_zeros:
        p.eq({choices[i]: 1})
    inputs, outputs, carries, controls, weights, carry_points = [], [], [], [], [], []
    for i, edge in enumerate(edges):
        t, z = edge['tick'], masses[i]
        lo, hi = family['bounds'][t]
        next_lo, next_hi = family['bounds'][t+1]
        x = p.vector('in:'+str(i), [min(0, v) for v in lo], [max(0, v) for v in hi])
        y = p.vector('out:'+str(i), [min(0, v) for v in next_lo], [max(0, v) for v in next_hi])
        p.scaled_bounds(x, lo, hi, z)
        p.scaled_bounds(y, next_lo, next_hi, z)
        a = p.vector('acceleration:'+str(i), [-edge['magnitude']]*2, [edge['magnitude']]*2)
        p.cones.append([{z: -edge['magnitude']}, {a[0]: -1}, {a[1]: -1}])
        c, eta, points = [], [], []
        for axis in (0, 1):
            vertices = carry_vertices(lo[axis+2], hi[axis+2], family['threshold'])
            if carry_pattern is not None:
                branch = carry_pattern[t][axis]
                vertices = [(v, v if branch else 0.) for v in (lo[axis+2], hi[axis+2])]
                if branch:
                    p.le(linear((-branch, x[axis+2]), (family['threshold'], z)))
                else:
                    p.le(linear((1, x[axis+2]), (-family['threshold'], z)))
                    p.le(linear((-1, x[axis+2]), (-family['threshold'], z)))
            v = p.variable('carry:'+str(i)+':'+str(axis), min(0, min(v[1] for v in vertices)),
                           max(0, max(v[1] for v in vertices)))
            w = [p.variable('hull:'+str(i)+':'+str(axis)+':'+str(j), 0, 1) for j in range(len(vertices))]
            p.eq(linear(*[(1, h) for h in w], (-1, z)))
            p.eq(linear((1, x[axis+2]), *[(-point[0], h) for point, h in zip(vertices, w)]))
            p.eq(linear((1, v), *[(-point[1], h) for point, h in zip(vertices, w)]))
            p.eq(linear((1, y[axis]), (-1, x[axis]), (-1, v), (-1, a[axis])))
            p.eq(linear((1, y[axis+2]), (-edge['friction'], v), (-edge['friction'], a[axis])))
            c.append(v)
            eta.append(w)
            points.append(vertices)
        for guard in edge['guards']:
            output = guard['where'] == 'out'
            var = (y if output else x)[guard['axis']]
            sign = 1 if guard['cmp'] == 'LE' else -1
            row = linear((sign, var), (-sign*guard['rhs'], z))
            if guard['option'] is not None:
                lower, upper = family['bounds'][t+int(output)]
                maximum = upper[guard['axis']] if sign == 1 else -lower[guard['axis']]
                big_m = max(0, maximum-sign*guard['rhs'])
                row = linear(*[(v, j) for j, v in row.items()], (big_m, choices[guard['option']]))
                p.le(row, big_m)
            else:
                p.le(row)
        inputs.append(x)
        outputs.append(y)
        controls.append(a)
        carries.append(c)
        weights.append(eta)
        carry_points.append(points)
    for node in family['nodes']:
        ins, outs = family['incoming'][node], family['outgoing'][node]
        p.eq(linear(*[(1, masses[i]) for i in outs], *[(-1, masses[i]) for i in ins]),
             1 if node == family['start'] else -1 if node == family['end'] else 0)
        if node != family['end']:
            for axis in range(4):
                rhs = family['bounds'][0][0][axis] if node == family['start'] else 0
                p.eq(linear(*[(1, inputs[i][axis]) for i in outs],
                            *[(-1, outputs[i][axis]) for i in ins]), rhs)
    for t in range(n+1):
        selected = [(outputs[i] if t == n else inputs[i]) for i, edge in enumerate(edges)
                    if edge['tick'] == (n-1 if t == n else t)]
        for axis in range(4):
            p.eq(linear((1, states[t][axis]), *[(-1, x[axis]) for x in selected]))
    for wall in family['walls']:
        var = states[wall['tick']][0 if wall['axis'] == 'X' else 1]
        sign = -1 if wall['cmp'] == 'GE' else 1
        (p.eq if wall['cmp'] == 'EQ' else p.le)({var: sign}, sign*wall['rhs'])
    objective = family['route']['objective']
    sign = 1 if objective['sense'] == 'MAX' else -1
    axis = 0 if objective['axis'] == 'X' else 1
    cost = {states[n][axis]: -sign}
    return dict(program=p, states=states, masses=masses, choices=choices, inputs=inputs, outputs=outputs,
                carries=carries, controls=controls, weights=weights, cost=cost,
                carryPoints=carry_points, carryPatternRestricted=carry_pattern is not None,
                family=family, fixed=dict(fixed or {}),
                buildSeconds=time.monotonic()-started)


def solve_conic(family, fixed=None, *, seconds=5, built=None):
    import clarabel
    from scipy import sparse
    started = time.monotonic()
    if built is not None and (built['family'] is not family or built['fixed'] != dict(fixed or {})):
        raise ValueError('A built conic model must match its family and committed decisions')
    model = built or build_conic(family, fixed)
    p = model['program']
    a, b, q, cones = p.data(model['cost'])
    settings = clarabel.DefaultSettings()
    settings.verbose = False
    settings.time_limit = seconds
    settings.max_iter = 300
    settings.tol_gap_abs = settings.tol_feas = settings.tol_gap_rel = 1e-9
    solver = clarabel.DefaultSolver(sparse.csc_matrix((len(q), len(q))), q, a, b, cones, settings)
    result = solver.solve()
    x = np.array(result.x)
    violation = p.violation(x) if np.all(np.isfinite(x)) else math.inf
    solved = str(result.status) in ('Solved', 'AlmostSolved') and violation <= 2e-7
    bound, dual_residual = p.upper_value(a, b, q, result.z) if solved else (None, None)
    states = [[float(x[j]) for j in indices] for indices in model['states']] if solved else None
    return dict(arm='convex_disk', status='solved' if solved else 'unknown', solverStatus=str(result.status),
                value=float(-q@x) if solved else None, upperBound=bound,
                boundKind='projected_conic_dual_with_bounded_stationarity_residual' if solved else None,
                violation=violation if math.isfinite(violation) else None, dualResidual=dual_residual,
                states=states, masses=[float(x[j]) for j in model['masses']] if solved else None,
                choices=[float(x[j]) for j in model['choices']] if solved else None,
                controls=[[float(x[j]) for j in indices] for indices in model['controls']] if solved else None,
                iterations=result.iterations, buildSeconds=model['buildSeconds'], solveSeconds=result.solve_time,
                elapsedSeconds=time.monotonic()-started, variables=len(q), rows=len(b), fixed=fixed or {},
                carryPatternRestricted=model['carryPatternRestricted'])
