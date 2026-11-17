import time

from research.route_guidance.compact import solve_compact
from research.route_guidance.conic import solve_conic
from research.route_guidance.graph import decisions
from research.route_guidance.physical import refine, rounded_assignment


ARMS = ('compact_circle', 'compact_disk', 'convex_disk')


def relaxation(family, arm, fixed=None, *, seconds=3, seed=None):
    if arm == 'convex_disk':
        return solve_conic(family, fixed, seconds=seconds)
    return solve_compact(family, arm, fixed, seconds=seconds, seed=seed)


def rank_candidates(family, arm, fixed, decision, *, seconds=3, seed=None):
    values = []
    for choice in decisions(family)[decision]:
        trial = dict(fixed, **{decision: choice})
        conic = solve_conic(family, trial, seconds=seconds)
        result = conic if arm == 'convex_disk' else relaxation(family, arm, trial, seconds=seconds,
                        seed=conic if conic['status'] == 'solved' else seed)
        values.append(result)
    return sorted(values, key=lambda r: (r['status'] != 'solved',
                  -(r['upperBound'] if r.get('upperBound') is not None else r['value'] or 0.)))


def guide_route(family, arm, compiler, *, seconds=3, refine_seconds=2, root=None, beam_width=2):
    if type(beam_width) is not int or beam_width < 1:
        raise ValueError('beam_width must be positive')
    started = time.monotonic()
    root = root or relaxation(family, arm, seconds=seconds)
    frontier, trace, drops = [({}, root)], [], 0
    for decision, domain in decisions(family).items():
        if len(domain) == 1:
            for fixed, _ in frontier:
                fixed[decision] = domain[0]
            continue
        expanded = []
        for fixed, current in frontier:
            ranked = rank_candidates(family, arm, fixed, decision, seconds=seconds, seed=current)
            solved = [r for r in ranked if r['status'] == 'solved']
            trace.append(dict(decision=decision, parentFixed=dict(fixed), candidates=ranked))
            expanded.extend((dict(fixed, **{decision: r['fixed'][decision]}), r) for r in solved)
        if not expanded:
            return dict(status='unknown', termination='no_solved_conditional_reference', trace=trace,
                        elapsedSeconds=time.monotonic()-started, beamWidth=beam_width,
                        objectiveOptimalityProven=False, heuristicDrops=drops)
        drops += max(0, len(expanded)-beam_width)
        frontier = sorted(expanded, key=lambda pair: -(pair[1]['upperBound']
                        if pair[1].get('upperBound') is not None else pair[1]['value']))[:beam_width]
    attempts, best = [], None
    for fixed, current in frontier:
        selection = rounded_assignment(family, current)
        if selection is None:
            continue
        if any(selection['fixed'].get(k) != v for k, v in fixed.items()):
            raise RuntimeError('Rounded flow violated a committed decision')
        accepted = refine(family, selection, compiler, seed=current,
                          extra_seeds=[root] if current is not root else (), seconds=refine_seconds)
        attempts.append(accepted)
        if accepted['status'] == 'feasible' and (best is None or accepted['value'] > best[2]['value']):
            best = (fixed, current, accepted)
    fixed, current, accepted = best if best else (frontier[0][0], frontier[0][1], dict(status='unknown'))
    return dict(status=accepted['status'], termination='physical_refinement', trace=trace, fixed=fixed,
                refinement=accepted, finalReference=current, refinementAttempts=attempts,
                elapsedSeconds=time.monotonic()-started, beamWidth=beam_width,
                objectiveOptimalityProven=False, heuristicDrops=drops,
                physicalCalls=sum(r['numericalCalls'] for r in attempts), backtracks=0,
                relaxationCalls=sum(len(t['candidates'])*(1 if arm == 'convex_disk' else 2) for t in trace))
