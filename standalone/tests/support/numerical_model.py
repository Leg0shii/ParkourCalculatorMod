"""Shared test builders extracted from tests.unit.numerics.test_model."""
import math

def problem(ticks=2):
    return {'version': 1, 'ticks': ticks, 'position': [0.0, 0.0], 'velocity': [0.1, 0.2],
            'magnitude': [0.0] * ticks, 'friction': [0.5] * ticks, 'base': [math.pi / 2] * ticks,
            'yaw': [0.0] * ticks, 'threshold': 0.0, 'contacts': [], 'walls': [],
            'objective': {'axis': 'X', 'sense': 'MAX', 'tick': ticks}, 'seconds': 2.0}
