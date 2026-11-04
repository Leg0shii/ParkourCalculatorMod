import itertools

from .geometry import merge_boxes


def movement_regions(projection, axis):
    if axis not in (0, 1, 2):
        raise ValueError('Movement axis must be X, Y or Z')
    lateral = [a for a in range(3) if a != axis]
    bands = []
    for a in lateral:
        boundaries = sorted({box[i] for box in projection for i in (a, a+3)})
        bands.append([(v, v) for v in boundaries]+list(zip(boundaries, boundaries[1:])))
    regions = []
    for band in itertools.product(*bands):
        intervals = sorted((box[axis], box[axis+3]) for box in projection
                           if all(box[a] <= lo and hi <= box[a+3] for a, (lo, hi) in zip(lateral, band)))
        merged = []
        for start, end in intervals:
            if merged and start <= merged[-1][1]:
                merged[-1] = (merged[-1][0], max(end, merged[-1][1]))
            else:
                merged.append((start, end))
        for start, end in merged:
            box = [0.]*6
            box[axis], box[axis+3] = start, end
            for a, (lo, hi) in zip(lateral, band):
                box[a], box[a+3] = lo, hi
            regions.append(tuple(box))
    regions = merge_boxes(regions)
    return [box for box in regions if not any(other != box and all(
        other[a] <= box[a] and box[a+3] <= other[a+3] for a in range(3)) for other in regions)]
