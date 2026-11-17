"""Shared test builders extracted from tests.integration.test_repair_resume."""
def route():
    return dict(constraints=[], passages=[], hitEvents=[], maxTotalRunTicks=0, supportClearance=1e-6,
                initial=dict(position=[.5,1,.5],velocity=[0,0,0]), objective=dict(axis='X',sense='MAX'))

def best(value):
    return dict(allocation=[0],cost=0,plan=dict(ticks=2,events=[]),
                candidate=dict(yaw=[value]*2,positions=[[0,0]]*3,objective=value))

def outcome(value=1):
    return dict(status='feasible',termination='done',attempts=[],best=best(value))

VERTICAL=dict(feasible=True,vertical={},y=[1]*3,vy=[0]*3,onGround=[True]*3)

from unittest.mock import patch
from parkour_solver.search.repair import solve_contact_branches


class RepairHarness:
    def invoke(self, request, find, filter_candidate, **kwargs):
        from parkour_solver.search.work import SearchWork
        budget = kwargs.setdefault('work', SearchWork(1))
        def dispatch(*args, **options):
            budget.before_solve({})
            return find(*args, **options)
        with patch('parkour_solver.search.repair.find_route',side_effect=dispatch), \
             patch('parkour_solver.search.repair.validate_vertical_schedule',return_value=VERTICAL):
            return solve_contact_branches(request,[],[],object(),seconds=1,candidate_seconds=.1,retries=0,
                rank_equal_cost=True,optimize_goal=True,candidate_filter=filter_candidate,**kwargs)
