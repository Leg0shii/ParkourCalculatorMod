import contextlib
import json
import math
import sys
import time

import casadi as ca
import numpy as np

try:
    from .continuation import run
    from .ladder import CAP
    from .constraints import reduce_walls
except ImportError:
    from continuation import run
    from ladder import CAP
    from constraints import reduce_walls


def send(message):
    sys.stdout.write(json.dumps(message, allow_nan=False, separators=(',', ':')) + '\n')
    sys.stdout.flush()


def forward(problem, yaw, threshold, pattern=None, cap_pattern=None, details=False):
    n = problem['ticks']
    positions = np.zeros((n + 1, 2))
    velocities = np.zeros((n + 1, 2))
    velocities[0] = problem['velocity']
    chosen = np.full((n, 2), 2, dtype=int)
    cap_choices = np.zeros((n, 2), dtype=int)
    raw_moves = np.zeros((n, 2))
    ladder = problem.get('ladderTicks', [False] * (n + 1))
    contacts = {(c['state'], c['axis']): c for c in problem['contacts']}
    angles = np.array(problem['base']) + np.deg2rad(yaw)
    controls = np.array(problem['magnitude'])[:, None] * np.column_stack((np.cos(angles), np.sin(angles)))
    for t in range(n):
        chosen[t] = np.where(np.abs(velocities[t]) < threshold, 0, np.where(velocities[t] >= 0, 1, -1))
        carry = np.where((chosen if pattern is None else pattern)[t] == 0, 0, velocities[t])
        move = carry + controls[t]
        raw_moves[t] = move
        if ladder[t]:
            cap_choices[t] = np.where(move < -CAP, -1, np.where(move > CAP, 1, 0))
            move = (np.clip(move, -CAP, CAP) if cap_pattern is None
                    else np.where(np.array(cap_pattern[t]) == 0, move, np.array(cap_pattern[t]) * CAP))
        positions[t + 1] = positions[t] + move
        velocities[t + 1] = problem['friction'][t] * move
        for a in range(2):
            contact = contacts.get((t + 1, a))
            if contact:
                positions[t + 1, a] = contact['plane'] - problem['position'][a]
                velocities[t + 1, a] = 0
    return (positions, velocities, chosen, cap_choices, raw_moves) if details else (positions, velocities, chosen)


class Monitor(ca.Callback):
    def __init__(self, nx, ng, decode, constraints, lower, upper, stop, on_candidate):
        ca.Callback.__init__(self)
        self.nx, self.ng, self.decode = nx, ng, decode
        self.constraints = constraints
        self.lower, self.upper = np.array(lower), np.array(upper)
        self.stop = stop
        self.on_candidate = on_candidate
        self.best = math.inf
        self.best_yaw = None
        self.construct('monitor', {})

    def get_n_in(self):
        return ca.nlpsol_n_out()

    def get_n_out(self):
        return 1

    def get_name_in(self, i):
        return ca.nlpsol_out(i)

    def get_name_out(self, i):
        return 'stop'

    def get_sparsity_in(self, i):
        name = self.get_name_in(i)
        if name in ('x', 'lam_x'):
            return ca.Sparsity.dense(self.nx, 1)
        if name in ('g', 'lam_g'):
            return ca.Sparsity.dense(self.ng, 1)
        return ca.Sparsity.dense(1, 1) if name == 'f' else ca.Sparsity(0, 1)

    def record(self, x, g, f):
        # With bound extraction, returned/callback g need not carry the original
        # expressions. Check the unextracted NLP at x on callbacks AND final
        # output. The parent still replays every original requested wall.
        values = np.array(self.constraints(x)).ravel()
        if not np.all(np.isfinite(np.array(x))) or not np.all(np.isfinite(values)) or not math.isfinite(f):
            return False
        violation = max(float(np.max(self.lower - values, initial=0)),
                        float(np.max(values - self.upper, initial=0)))
        if violation <= 1e-8:
            yaw = np.array(self.decode(x)).ravel().tolist()
            accepted = self.on_candidate(yaw)
            if f < self.best or accepted and self.stop:
                self.best, self.best_yaw = f, yaw
            return accepted
        return False

    def eval(self, arguments):
        accepted = self.record(arguments[ca.nlpsol_out().index('x')],
                               arguments[ca.nlpsol_out().index('g')],
                               float(arguments[ca.nlpsol_out().index('f')]))
        return [1 if accepted and self.stop else 0]


def fixed_phase(problem, yaw, pattern, seconds, stop, on_candidate, kind, seed_name, caps, attempt):
    started = time.monotonic()
    pattern = np.array(pattern, dtype=int)
    n = problem['ticks']
    seed_p, seed_v, _ = forward(problem, yaw, problem['threshold'], pattern, caps)
    ladder = problem.get('ladderTicks', [False] * (n + 1))
    variables, initial, rows, lower, upper = [], [], [], [], []
    contacts = {(c['state'], c['axis']): c for c in problem['contacts']}

    def variable(name, seed):
        seed = np.atleast_1d(seed)
        value = ca.SX.sym(name, len(seed))
        variables.append(value)
        initial.extend(seed.tolist())
        return value

    def row(value, lo, hi):
        rows.append(value)
        lower.extend([lo] * value.numel())
        upper.extend([hi] * value.numel())

    p, v, facings = [ca.DM.zeros(2)], [ca.DM(problem['velocity'])], []
    threshold = problem['threshold']
    for t in range(n):
        for a in range(2):
            branch = pattern[t, a]
            if branch == 0:
                row(v[t][a], -threshold + 1e-8, threshold - 1e-8)
            elif branch in (-1, 1):
                row(branch * v[t][a], threshold + 1e-8, math.inf)
        theta = variable('theta' + str(t), math.radians(yaw[t]))
        facings.append(theta * (180 / math.pi))
        angle = problem['base'][t] + theta
        control = problem['magnitude'][t] * ca.vertcat(ca.cos(angle), ca.sin(angle))
        carry = ca.vertcat(*[0 if pattern[t, a] == 0 else v[t][a] for a in range(2)])
        pn, vn = variable('p' + str(t), seed_p[t + 1]), variable('v' + str(t), seed_v[t + 1])
        raw = carry + control
        movement = []
        for a in range(2):
            b = caps[t][a] if ladder[t] else 0
            if ladder[t]:
                if b:
                    row(b * raw[a], CAP, math.inf)
                else:
                    row(raw[a], -CAP, CAP)
            movement.append(b * CAP if b else raw[a])
        displacement = ca.vertcat(*movement)
        proposed = p[t] + displacement
        for a in range(2):
            contact = contacts.get((t + 1, a))
            if contact:
                plane = contact['plane'] - problem['position'][a]
                row(pn[a] - plane, 0, 0)
                row(vn[a], 0, 0)
                row(contact['direction'] * (proposed[a] - plane), 1e-7, math.inf)
                row(contact['direction'] * (p[t][a] - plane), -math.inf, 0)
            else:
                row(pn[a] - proposed[a], 0, 0)
                row(vn[a] - problem['friction'][t] * displacement[a], 0, 0)
        p.append(pn)
        v.append(vn)

    numerical = reduce_walls(problem['walls'])
    for wall in numerical['walls']:
        mode, t1, t2 = wall['mode'], wall['t1'], wall.get('t2')
        if mode in ('DXZ', 'DZX'):
            dx, dz = ca.fabs(p[t1][0] - p[t2][0]), ca.fabs(p[t1][1] - p[t2][1])
            value = dx - dz if mode == 'DXZ' else dz - dx
        elif mode == 'F':
            value = facings[t1]
            if t2 is not None:
                value += (1 if wall['op'] == 'PLUS' else -1) * facings[t2]
        elif mode in ('X', 'Z'):
            a = 0 if mode == 'X' else 1
            value = p[t1][a] + problem['position'][a]
            if t2 is not None:
                value += (1 if wall['op'] == 'PLUS' else -1) * (p[t2][a] + problem['position'][a])
        else:
            raise ValueError('Unsupported constraint mode: ' + mode)
        residual = value - wall['rhs']
        if mode == 'F':
            seed = yaw[t1] - wall['rhs']
            if t2 is not None:
                seed += (1 if wall['op'] == 'PLUS' else -1) * yaw[t2]
            residual -= 360 * math.floor((seed + 180) / 360)
            row(residual, 0 if wall['cmp'] in ('GE', 'EQ') else -180,
                0 if wall['cmp'] in ('LE', 'EQ') else 180)
        else:
            row(residual, 0 if wall['cmp'] in ('GE', 'EQ') else -math.inf,
                0 if wall['cmp'] in ('LE', 'EQ') else math.inf)

    objective = problem['objective']
    tick = objective['tick']
    point = p[tick] + ca.DM(problem['position'])
    if objective.get('customYaw') is not None:
        angle = math.radians(objective['customYaw'])
        direction = ca.DM([-math.sin(angle), math.cos(angle)])
        if objective['type'] == 'MOTION' and tick > 0:
            point = p[tick] - p[tick - 1]
        cost = ca.dot(direction, point)
    else:
        cost = point[0 if objective['axis'] == 'X' else 1]
    cost *= -1 if objective['sense'] == 'MAX' else 1
    if problem.get('objectiveMode') == 'constant_zero':
        cost = ca.SX(0)
    x, g = ca.vertcat(*variables), ca.vertcat(*rows)
    decode = ca.Function('decode', [x], [ca.vertcat(*facings)])
    constraints = ca.Function('original_constraints', [x], [g])
    monitor = Monitor(x.numel(), g.numel(), decode, constraints, lower, upper, stop, on_candidate)
    solver = ca.nlpsol('local_ipopt', 'ipopt', {'x': x, 'g': g, 'f': cost}, {
        'print_time': False, 'error_on_fail': False, 'iteration_callback': monitor,
        'detect_simple_bounds': True,
        'ipopt.print_level': 0, 'ipopt.sb': 'yes', 'ipopt.max_wall_time': max(0.01, seconds),
        'ipopt.max_iter': problem.get('maxIterations', 10000), 'ipopt.tol': 1e-9, 'ipopt.constr_viol_tol': 1e-9,
        'ipopt.acceptable_tol': 1e-8, 'ipopt.acceptable_constr_viol_tol': 1e-9,
        'ipopt.bound_relax_factor': 0.0})
    solution = solver(x0=initial, lbg=lower, ubg=upper)
    monitor.record(solution['x'], solution['g'], float(solution['f']))
    stats = solver.stats()
    send({'type': 'phase', 'status': stats['return_status'], 'iterations': stats['iter_count'],
          'seconds': time.monotonic() - started, 'zeros': int(np.count_nonzero(pattern == 0)),
          'survivors': int(np.count_nonzero(np.abs(pattern) == 1)), 'kind': kind, 'seed': seed_name,
          'ladderSaturated': sum(b != 0 for row_values in caps for b in row_values), 'capAttempt': attempt,
          'simpleBoundExtraction': True, 'constraintRowsBeforeExtraction': g.numel(),
          'wallReduction': numerical['audit'], 'objectiveMode': problem.get('objectiveMode','configured'),
          'stopOnFeasible': stop})
    return monitor.best_yaw if monitor.best_yaw is not None else yaw, stats['return_status'], monitor.best


def phase(problem, yaw, pattern, seconds, stop, on_candidate, kind, seed_name):
    end = time.monotonic() + seconds
    has_ladders = any(problem.get('ladderTicks', []))
    explicit = problem.get('ladderPattern')
    caps = explicit if explicit is not None else forward(problem, yaw, problem['threshold'], pattern,
                                                        details=True)[3].tolist()
    queue, seen = [(yaw, caps)], set()
    best, best_cost, best_status = yaw, math.inf, 'Not_Started'
    attempt = 0
    limit = 4 if has_ladders and explicit is None else 1
    while queue and attempt < limit:
        if end - time.monotonic() < 0.05:
            break
        seed_yaw, chosen_caps = queue.pop(0)
        key = tuple(tuple(row) for row in chosen_caps)
        if key in seen:
            continue
        seen.add(key)
        remaining = end - time.monotonic()
        budget = remaining * 0.65 if has_ladders and explicit is None and attempt < 3 else remaining
        result, status, cost = fixed_phase(problem, seed_yaw, pattern, budget, stop, on_candidate,
                                           kind, seed_name, chosen_caps, attempt)
        attempt += 1
        if cost < best_cost:
            best, best_cost, best_status = result, cost, status
        elif best_status == 'Not_Started':
            best_status = status
        if explicit is not None or not has_ladders or status == 'User_Requested_Stop':
            break
        _, _, _, inferred, raw = forward(problem, result, problem['threshold'], pattern,
                                          chosen_caps, details=True)
        if not np.array_equal(inferred, chosen_caps):
            queue.append((result, inferred.tolist()))
        adjacent = [list(row) for row in chosen_caps]
        changed = False
        for t, active in enumerate(problem['ladderTicks'][:-1]):
            if not active:
                continue
            for a in range(2):
                if abs(abs(raw[t, a]) - CAP) <= 2e-6:
                    adjacent[t][a] = 0 if chosen_caps[t][a] else 1 if raw[t, a] >= 0 else -1
                    changed = True
        if changed:
            queue.append((result, adjacent))
        if not math.isfinite(cost):
            queue.append((seed_yaw, [[0, 0] for _ in range(problem['ticks'])]))
    return best, best_status


def main():
    problem = json.loads(sys.stdin.readline())
    if problem['version'] != 1:
        raise ValueError('Unsupported protocol version')
    with contextlib.redirect_stdout(sys.stderr):
        ca.nlpsol('check_ipopt', 'ipopt', {'x': ca.SX.sym('x'), 'f': 0},
                  {'print_time': False, 'ipopt.print_level': 0, 'ipopt.sb': 'yes'})
    status = run(problem, phase, lambda yaw: forward(problem, yaw, problem['threshold'])[2].tolist(), send)
    send({'type': 'done', 'status': status})


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        send({'type': 'error', 'message': str(error)})
        raise
