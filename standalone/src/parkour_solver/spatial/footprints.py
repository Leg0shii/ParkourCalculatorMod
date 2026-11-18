"""Player-space obstacle clusters and the free rectangles around them.

A cluster is a set of expanded solids the player cannot pass between. At a given
height its active members' X/Z rectangles form a union; the sides are every
maximal empty rectangle of the plane outside that union. Every free position lies
in some side, and a face two flush solids share is one side, not two.
"""
import itertools
import math
from dataclasses import dataclass

from parkour_solver.scene.geometry import expanded_box, thin


@dataclass(frozen=True)
class Cluster:
    id: int
    members: tuple
    solids: tuple
    boxes: tuple


@dataclass(frozen=True)
class Side:
    bounds: tuple
    owners: tuple
    region: tuple

    @property
    def key(self):
        return self.bounds


def nested(a, b):
    def contains(outer, inner):
        return outer[0] <= inner[0] and outer[1] <= inner[1] and inner[2] <= outer[2] and inner[3] <= outer[3]
    return contains(a.region, b.region) or contains(b.region, a.region)


def intersects(a, b):
    return all(max(a.region[i], b.region[i]) <= min(a.region[i+2], b.region[i+2]) for i in (0, 1))


def free_rectangles(boxes):
    """Maximal closed rectangles disjoint from the open X/Z rectangles of boxes."""
    xs = [-math.inf]+sorted({b[a] for b in boxes for a in (0, 3)})+[math.inf]

    def empty(x0, x1, z0, z1):
        return not any(b[0] < x1 and x0 < b[3] and b[2] < z1 and z0 < b[5] for b in boxes)

    result = []
    for i, j in itertools.combinations(range(len(xs)), 2):
        xa, xb = xs[i], xs[j]
        if thin(xb-xa):
            continue
        start, free = -math.inf, []
        for lo, hi in sorted((b[2], b[5]) for b in boxes if b[0] < xb and xa < b[3]):
            if lo > start:
                free.append((start, lo))
            start = max(start, hi)
        free.append((start, math.inf))
        for za, zb in free:
            if thin(zb-za):
                continue
            if i > 0 and empty(xs[i-1], xb, za, zb) or j < len(xs)-1 and empty(xa, xs[j+1], za, zb):
                continue
            result.append((xa, za, xb, zb))
    return result


class Footprints:
    def __init__(self, geometry, solid_indices):
        indices = sorted(solid_indices)
        boxes = {i: tuple(expanded_box(geometry['solids'][i])) for i in indices}
        parent = {i: i for i in indices}

        def root(i):
            while parent[i] != i:
                parent[i] = parent[parent[i]]
                i = parent[i]
            return i
        for a, b in itertools.combinations(indices, 2):
            if all(thin(max(boxes[a][k], boxes[b][k])-min(boxes[a][k+3], boxes[b][k+3])) for k in range(3)):
                parent[root(b)] = root(a)
        groups = {}
        for i in indices:
            groups.setdefault(root(i), []).append(i)
        self.clusters = tuple(Cluster(min(m), tuple(m), tuple(tuple(geometry['solids'][i]) for i in m),
                                      tuple(boxes[i] for i in m))
                              for m in sorted(groups.values()))
        self._by_id = {c.id: c for c in self.clusters}
        self._sides = {}

    def cluster(self, cluster_id):
        return self._by_id[cluster_id]

    def active(self, cluster, y_lo, y_hi):
        return tuple(k for k, b in enumerate(cluster.boxes) if b[1] < y_hi and y_lo < b[4])

    def sides(self, cluster, active):
        key = (cluster.id, tuple(active))
        if key not in self._sides:
            boxes = [cluster.boxes[k] for k in active]
            sides = []
            for region in free_rectangles(boxes):
                bounds, owners = [], set()
                for axis, edge, direction, plane in (('X', 3, 1, region[0]), ('X', 0, -1, region[2]),
                                                     ('Z', 5, 1, region[1]), ('Z', 2, -1, region[3])):
                    if math.isinf(plane):
                        continue
                    bounds.append((axis, direction, plane))
                    other = (2, 5, 1, 3) if axis == 'X' else (0, 3, 0, 2)
                    owners.update(cluster.members[k] for k, b in zip(active, boxes) if b[edge] == plane
                                  and b[other[0]] <= region[other[3]] and region[other[2]] <= b[other[1]])
                sides.append(Side(tuple(sorted(bounds)), tuple(sorted(owners)), region))
            self._sides[key] = tuple(sorted(sides, key=lambda s: s.bounds))
        return self._sides[key]
