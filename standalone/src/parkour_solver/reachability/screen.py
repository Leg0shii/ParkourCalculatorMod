from parkour_solver.reachability.intervals import reachable_envelopes, interaction_reachability


def contradiction(problem, extra_rows=()):
    if extra_rows:
        rows=[dict(r,t1=r.get('t1',r.get('offset'))) for r in extra_rows]
        problem=dict(problem,walls=list(problem.get('walls',[]))+rows)
    checked=interaction_reachability(reachable_envelopes(problem),{},refine=True)
    return {k:v for k,v in checked.items() if k!='ruledOut'} if checked['ruledOut'] else None
