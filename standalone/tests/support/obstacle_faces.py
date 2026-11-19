"""The six expanded faces of one box, an oracle for avoidance tests."""
from parkour_solver.scene.geometry import expanded_box


def faces(box):
    bounds = expanded_box(box)
    return [{'axis': axis, 'direction': sign, 'plane': bounds[i + (3 if sign > 0 else 0)]}
            for i, axis in enumerate('XYZ') for sign in (-1, 1)]
