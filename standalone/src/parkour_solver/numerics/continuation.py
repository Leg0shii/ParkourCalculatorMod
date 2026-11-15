import time

try:
    from parkour_solver.numerics.model import validate
except ImportError:
    from parkour_solver.numerics.model import validate


class Incumbent:
    def __init__(self, problem, emit):
        self.problem = problem
        self.emit = emit
        self.best = None

    def consider(self, yaw):
        try:
            candidate = validate(self.problem, yaw)
        except (ValueError, TypeError, OverflowError):
            return False
        if not candidate['feasible']:
            return False
        direction = 1 if self.problem['objective']['sense'] == 'MAX' else -1
        if self.best is None or direction * candidate['objective'] > direction * self.best['objective']:
            self.best = candidate
            self.emit({'type': 'candidate', 'yaw': candidate['yaw']})
        return True

    def pattern(self):
        threshold = self.problem['threshold']
        return [[0 if abs(v) < threshold else 1 if v > 0 else -1 for v in row]
                for row in self.best['velocity'][:-1]]


def run(problem, phase, infer, emit, clock=time.monotonic):
    end = clock() + problem['seconds']
    incumbent = Incumbent(problem, emit)
    incumbent.consider(problem['yaw'])
    if incumbent.best is not None and problem['stopOnFeasible']:
        return 'User_Requested_Stop'
    free = [[2, 2] for _ in range(problem['ticks'])]
    status = 'Not_Started'
    polished = set()

    def solve_phase(yaw, pattern, deadline, fraction, kind, seed):
        nonlocal status
        remaining = deadline - clock()
        if remaining <= 0.05:
            return yaw, False
        result, status = phase(problem, yaw, pattern, remaining * fraction,
                               problem['stopOnFeasible'], incumbent.consider, kind, seed)
        incumbent.consider(result)
        return result, True

    if 'inertiaPattern' in problem:
        solve_phase(problem['yaw'], problem['inertiaPattern'], end, 1, 'fixed_pattern', 'initial')
        return status

    seeds = [('initial', problem['yaw'])]
    fallback = problem.get('fallbackYaw')
    if fallback is not None and fallback != problem['yaw']:
        seeds.append(('fallback', fallback))
    if problem['threshold'] == 0:
        for index, (name, seed) in enumerate(seeds):
            incumbent.consider(seed)
            if incumbent.best is not None and problem['stopOnFeasible']:
                return 'User_Requested_Stop'
            remaining = end - clock()
            deadline = clock() + remaining * .4 if index + 1 < len(seeds) else end
            solve_phase(seed, free, deadline, 1, 'carry', name)
        return status
    for index, (name, seed) in enumerate(seeds):
        remaining = end - clock()
        deadline = clock() + remaining * 0.4 if index + 1 < len(seeds) else end
        incumbent.consider(seed)
        if incumbent.best is None or index > 0:
            yaw, ran = solve_phase(seed, free, deadline, 0.3, 'carry', name)
            if ran:
                if incumbent.best is not None and problem['stopOnFeasible']:
                    return 'User_Requested_Stop'
                zeros = [[0 if b == 0 else 2 for b in row] for row in infer(yaw)]
                yaw, _ = solve_phase(yaw, zeros, deadline, 0.35, 'zero_events', name)
        else:
            yaw = seed
        for _ in range(3):
            if incumbent.best is not None:
                if problem['stopOnFeasible']:
                    return 'User_Requested_Stop'
                if tuple(incumbent.best['yaw']) in polished:
                    break
                yaw, pattern = incumbent.best['yaw'], incumbent.pattern()
                kind = 'incumbent_polish'
            else:
                pattern = infer(yaw)
                kind = 'fixed_pattern'
            yaw, ran = solve_phase(yaw, pattern, deadline, 1, kind, name)
            if (ran and status in ('Solve_Succeeded', 'Solved_To_Acceptable_Level')
                    and incumbent.best is not None and incumbent.pattern() == pattern):
                polished.add(tuple(incumbent.best['yaw']))
            if not ran or status in ('Solve_Succeeded', 'Solved_To_Acceptable_Level', 'User_Requested_Stop'):
                break
    return status
