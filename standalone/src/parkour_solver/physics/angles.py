"""Shared scalar angle convention."""
import math


def wrap(value):
    value = math.fmod(value, 360)
    if value > 180:
        value -= 360
    if value <= -180:
        value += 360
    return value
