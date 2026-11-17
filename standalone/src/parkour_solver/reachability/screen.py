import copy

from parkour_solver.reachability.intervals import reachable_envelopes, interaction_reachability


def condition_envelopes(envelopes, extra_rows=(), *, refine=True):
    """Return the conservative branch bounds as well as its exclusion verdict.

    The input is immutable. Reuse the compiled model, including its named-probe
    metadata; a realized Y trace must never replace uncertain probe heights.
    Lightweight callers without a model still get sound same-state tightening.
    """
    rows = [dict(r, t1=r.get('t1', r.get('offset'))) for r in extra_rows]
    proof = interaction_reachability(envelopes, rows)
    model = envelopes.get('model')
    if not rows:
        tightened = envelopes
    elif model is not None:
        model = dict(model, walls=list(model.get('walls', []))+rows)
        states = envelopes['states']
        y = [s['Y'][0] for s in states] if all(
            'Y' in s and s['Y'][0] == s['Y'][1] for s in states) else None
        tightened = reachable_envelopes(model, y)
    else:
        tightened = copy.deepcopy(envelopes)
        for row in rows:
            if row['mode'] not in ('X', 'Z') or row.get('t2') is not None:
                continue
            lo, hi = tightened['states'][row['t1']][row['mode']]
            if row['cmp'] in ('GE', 'EQ'):
                lo = max(lo, row['rhs']-1e-7)
            if row['cmp'] in ('LE', 'EQ'):
                hi = min(hi, row['rhs']+1e-7)
            tightened['states'][row['t1']][row['mode']] = [lo, hi]
        # The old phase boxes remain a conservative superset when no motion
        # model is supplied. Do not invent a second phase propagator here.
    return dict(envelopes=tightened,
                proof=proof if proof['ruledOut'] else interaction_reachability(tightened, {}, refine=refine))


def contradiction(problem, extra_rows=()):
    if extra_rows:
        rows=[dict(r,t1=r.get('t1',r.get('offset'))) for r in extra_rows]
        problem=dict(problem,walls=list(problem.get('walls',[]))+rows)
    checked=condition_envelopes(reachable_envelopes(problem))['proof']
    return {k:v for k,v in checked.items() if k!='ruledOut'} if checked['ruledOut'] else None
