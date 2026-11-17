import math
import time

import casadi as ca
import numpy as np

from research.route_guidance.graph import carry_vertices, edge_path, fixed_values


def _run_compact(family, arm, fixed=None, *, seconds=5, seed=None, witness=None):
    if arm not in ('compact_circle', 'compact_disk'):
        raise ValueError('Unknown compact arm')
    started = time.monotonic()
    variables, lower, upper, guess, rows, row_lo, row_hi = [], [], [], [], [], [], []
    n, edges = family['ticks'], family['edges']
    if witness:
        candidate = witness['candidate']
        path = edge_path(family, witness['selection']['allocation'])
        seed = dict(states=[p+v for p, v in zip(candidate['positions'], candidate['velocity'])],
                    masses=[float(i in path) for i in range(len(edges))],
                    choices=[float(i in witness['selection']['options']) for i in range(len(family['options']))])

    def variable(name, lo, hi, initial):
        vector = ca.SX.sym(name, len(lo))
        variables.append(vector)
        lower.extend(lo)
        upper.extend(hi)
        guess.extend(np.asarray(initial).tolist() if witness else np.clip(initial, lo, hi).tolist())
        return vector

    def row(value, lo=0., hi=0.):
        rows.append(value)
        row_lo.extend([lo]*value.numel())
        row_hi.extend([hi]*value.numel())

    states = [variable('state_'+str(t), *family['bounds'][t],
               seed['states'][t] if seed and seed.get('states') else family['bounds'][t][0]) for t in range(n+1)]
    masses = variable('mass', [0.]*len(edges), [1.]*len(edges),
                      seed['masses'] if seed and seed.get('masses') else [1/len(edges)]*len(edges))
    choices = variable('choice', [0.]*len(family['options']), [1.]*len(family['options']),
                       seed['choices'] if seed and seed.get('choices') else [.5]*len(family['options']))
    for group in family['groups']:
        row(sum(choices[i] for i in group)-1)
    for node in family['nodes']:
        rhs = 1 if node == family['start'] else -1 if node == family['end'] else 0
        row(sum(masses[i] for i in family['outgoing'][node])-
            sum(masses[i] for i in family['incoming'][node])-rhs)
    edge_zeros, option_zeros = fixed_values(family, fixed or {})
    for i in edge_zeros:
        row(masses[i])
    for i in option_zeros:
        row(choices[i])
    controls = []
    for t in range(n):
        lo, hi = family['bounds'][t]
        incoming = states[t]
        carry = []
        for axis in (0, 1):
            vertices = carry_vertices(lo[axis+2], hi[axis+2], family['threshold'])
            weights = [1/len(vertices)]*len(vertices)
            if witness:
                from research.route_guidance.physical import _hull_weights
                v = candidate['velocity'][t][axis]
                weights = _hull_weights(vertices, (v, v if abs(v) >= family['threshold'] else 0.)).tolist()
            eta = variable('hull_'+str(t)+'_'+str(axis), [0.]*len(vertices), [1.]*len(vertices),
                           weights)
            row(ca.sum1(eta)-1)
            row(incoming[axis+2]-sum(point[0]*eta[j] for j, point in enumerate(vertices)))
            carry.append(sum(point[1]*eta[j] for j, point in enumerate(vertices)))
        selected = [e for e in edges if e['tick'] == t]
        m = sum(masses[e['index']]*e['magnitude'] for e in selected)
        f = sum(masses[e['index']]*e['friction'] for e in selected)
        fm = sum(masses[e['index']]*e['friction']*e['magnitude'] for e in selected)
        direction = [1., 0.] if family['route']['objective']['axis'] == 'X' else [0., 1.]
        if family['route']['objective']['sense'] == 'MIN':
            direction = [-v for v in direction]
        if seed and seed.get('controls'):
            accel = np.sum([seed['controls'][e['index']] for e in selected], axis=0)
            scale = sum(seed['masses'][e['index']]*e['magnitude'] for e in selected)
            if np.linalg.norm(accel) > 1e-10:
                direction = (accel/(np.linalg.norm(accel) if arm == 'compact_circle' else max(scale, 1e-10))).tolist()
        if witness:
            angle = edges[path[t]]['base']+math.radians(candidate['yaw'][t])
            direction = [math.cos(angle), math.sin(angle)]
        q = variable('direction_'+str(t), [-1., -1.], [1., 1.], direction)
        row(ca.dot(q, q)-1, 0 if arm == 'compact_circle' else -math.inf, 0)
        controls.append(m*q)
        for axis in (0, 1):
            row(states[t+1][axis]-incoming[axis]-carry[axis]-m*q[axis])
            row(states[t+1][axis+2]-f*carry[axis]-fm*q[axis])
        for edge in selected:
            for guard in edge['guards']:
                at = t+int(guard['where'] == 'out')
                sign = 1 if guard['cmp'] == 'LE' else -1
                lower_state, upper_state = family['bounds'][at]
                maximum = upper_state[guard['axis']] if sign == 1 else -lower_state[guard['axis']]
                big_m = max(0, maximum-sign*guard['rhs'])
                inactive = 1-masses[edge['index']]
                if guard['option'] is not None:
                    inactive += 1-choices[guard['option']]
                row(sign*(states[at][guard['axis']]-guard['rhs'])-big_m*inactive, -math.inf, 0)
    for wall in family['walls']:
        value = states[wall['tick']][0 if wall['axis'] == 'X' else 1]-wall['rhs']
        row(value, 0 if wall['cmp'] != 'LE' else -math.inf,
            0 if wall['cmp'] != 'GE' else math.inf)
    objective = family['route']['objective']
    sign, axis = (1 if objective['sense'] == 'MAX' else -1), (0 if objective['axis'] == 'X' else 1)
    value = sign*states[n][axis]
    x, g = ca.vertcat(*variables), ca.vertcat(*rows)
    if witness:
        evaluated = np.array(ca.Function('audit', [x], [g])(guess)).ravel()
        error = max(float(np.max(np.array(row_lo)-evaluated, initial=0)),
                    float(np.max(evaluated-np.array(row_hi), initial=0)),
                    float(np.max(np.array(lower)-np.array(guess), initial=0)),
                    float(np.max(np.array(guess)-np.array(upper), initial=0)))
        return dict(violation=error, initialState=guess[:4])
    opts = dict(print_time=False, error_on_fail=False, ipopt=dict(print_level=0, sb='yes',
        max_iter=1500, max_wall_time=seconds, tol=1e-9, constr_viol_tol=1e-8,
        acceptable_tol=1e-7, bound_relax_factor=0, fixed_variable_treatment='make_parameter'))
    solver = ca.nlpsol('guide', 'ipopt', dict(x=x, f=-value, g=g), opts)
    build_time = time.monotonic()-started
    before_solve = time.monotonic()
    solution = solver(x0=guess, lbx=lower, ubx=upper, lbg=row_lo, ubg=row_hi)
    elapsed_solve = time.monotonic()-before_solve
    output = np.array(solution['x']).ravel()
    values = np.array(solution['g']).ravel()
    violation = max(float(np.max(np.array(row_lo)-values, initial=0)),
                    float(np.max(values-np.array(row_hi), initial=0)),
                    float(np.max(np.array(lower)-output, initial=0)),
                    float(np.max(output-np.array(upper), initial=0)))
    solved = solver.stats()['success'] and np.all(np.isfinite(output)) and violation <= 2e-7
    decode = ca.Function('decode', [x], [ca.horzcat(*states).T, masses, choices, ca.horzcat(*controls).T])
    state_values, mass_values, choice_values, control_values = decode(solution['x'])
    return dict(arm=arm, status='solved' if solved else 'unknown', solverStatus=solver.stats()['return_status'],
                value=float(-solution['f']) if solved else None, upperBound=None,
                boundKind='model_inclusion_under_assumed_NLP_solve_quality', violation=violation,
                states=np.array(state_values).tolist() if solved else None,
                masses=np.array(mass_values).ravel().tolist() if solved else None,
                choices=np.array(choice_values).ravel().tolist() if solved else None,
                tickControls=np.array(control_values).tolist() if solved else None,
                buildSeconds=build_time, solveSeconds=elapsed_solve, elapsedSeconds=time.monotonic()-started,
                variables=x.numel(), rows=g.numel(), iterations=solver.stats()['iter_count'], fixed=fixed or {})


def solve_compact(family, arm, fixed=None, *, seconds=5, seed=None):
    return _run_compact(family, arm, fixed, seconds=seconds, seed=seed)


def audit_compact_witness(family, arm, selection, candidate, fixed=None):
    return _run_compact(family, arm, fixed, witness=dict(selection=selection, candidate=candidate))
