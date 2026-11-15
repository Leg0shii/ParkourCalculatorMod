"""Shared test builders extracted from tests.unit.reachability.test_proposal_analysis."""
class Coefficients:
    def __init__(self):
        self.calls=0

    def compile(self, route, plan, seconds):
        self.calls+=1
        n=plan['ticks']
        return dict(magnitude=[1.]*n,friction=[.9]*n,base=[0.]*n)
