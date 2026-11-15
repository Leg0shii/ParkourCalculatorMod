"""Primitive request validators shared by all runtime layers."""
import math

TOLERANCE = 1e-7


def number(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(name + ' must be a finite number')
    return value


def integer(value, name, lower, upper):
    if isinstance(value, bool) or not isinstance(value, int) or not lower <= value <= upper:
        raise ValueError(name + ' is out of range')
    return value
