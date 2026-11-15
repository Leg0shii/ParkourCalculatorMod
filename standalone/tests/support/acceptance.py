"""Shared test builders extracted from tests.integration.test_acceptance."""
class NoMovementCompiler:
    def compile(self, route, plan, seconds):
        return {'magnitude': [0.0] * plan['ticks'], 'friction': [.91] * plan['ticks'],
                'base': [0.0] * plan['ticks']}
