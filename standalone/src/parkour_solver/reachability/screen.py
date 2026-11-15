from parkour_solver.reachability.intervals import reachable_envelopes, interaction_reachability


def contradiction(problem):
    checked=interaction_reachability(reachable_envelopes(problem),{},refine=True)
    return {k:v for k,v in checked.items() if k!='ruledOut'} if checked['ruledOut'] else None
