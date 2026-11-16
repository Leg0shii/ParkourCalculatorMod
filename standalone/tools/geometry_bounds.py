"""Report player-foot collision and exposed-support bounds using solver geometry."""
import argparse
import copy
import json
from pathlib import Path

from parkour_solver.scene.geometry import BODY_HEIGHT, expanded_box, normalize_geometry
from parkour_solver.scene.route import HALF_WIDTH, supports
from parkour_solver.spatial.cells import decompose, intersection
from parkour_solver.spatial.faces import merge_faces
from parkour_solver.spatial.regions import canonical_cover, support_components


def _positive_support_cover(boxes, blocks):
    # Decomposition retains tangent strata for geometry incidence. A player
    # merely touching a support's horizontal edge is not supported by it.
    return canonical_cover(b for b in boxes if any(
        supports(block, (b[0]+b[3])/2, (b[2]+b[5])/2) for block in blocks))


def player_bounds_report(value, *, include_faces=False):
    """Accept a collision scene or task fixture; do not run routing or dynamics."""
    scene = copy.deepcopy(value.get('request', value))
    if scene.get('sceneModel') != 'collision_boxes':
        raise ValueError('Expected a collision_boxes scene or task fixture')
    scene = normalize_geometry(scene)
    decomposition = decompose(scene)
    solids = scene['collisionScene']['solids']
    expanded = list(map(expanded_box, solids))
    components = support_components(decomposition)
    by_solid = {b['solidIndex']: b for b in scene['blocks']}
    records = []
    for block in scene['blocks']:
        index = block['solidIndex']
        box, forbidden = solids[index], expanded[index]
        top = box[4]
        raw = (forbidden[0], top, forbidden[2], forbidden[3], top, forbidden[5])
        blockers = []
        for other in scene['blocks']:
            obstacle = expanded[other['solidIndex']]
            if not obstacle[1] < top < obstacle[4]:
                continue
            overlap = intersection(raw, obstacle)
            if overlap is not None and all(overlap[a] < overlap[a+3] for a in (0, 2)):
                blockers.append(dict(id=other['id'], overlap=overlap,
                    relation='overhead' if solids[other['solidIndex']][1] > top else 'side'))
        cover = _positive_support_cover((p.bounds for p in decomposition.contacts
                                        if p.kind == 'support' and p.solid == index), [block])
        records.append(dict(id=block['id'], owner=block['sourceBlock'], physicalBox=box,
            forbiddenFeetInterior=forbidden, top=top, rawLandingClosure=raw,
            exposedLandingClosure=cover, blockers=blockers))
    report = dict(version=1, body=dict(halfWidth=HALF_WIDTH, height=BODY_HEIGHT),
        semantics='Feet/center coordinates; open forbidden interiors; support covers are closures. '
                  'Positive support overlap and movement/replay validation remain required.',
        geometryBounds=scene['collisionScene']['bounds'], blocks=records,
        supportComponents=[dict(owner=s.owner, height=s.height, cover=cover)
            for s in components
            if (cover := _positive_support_cover(s.cover, [by_solid[i] for i in s.solids]))])
    if include_faces:
        catalog = merge_faces(scene)
        report.update(faceCatalog=catalog.to_dict(), faceStatistics=catalog.statistics)
    return report


def markdown_report(report):
    """Render rounded bounds; JSON retains the solver's exact doubles."""
    def interval(lo, hi):
        return '[' + ', '.join(f'{v:.6f}'.rstrip('0').rstrip('.') for v in (lo, hi)) + ']'

    lines = ['# Player bounds', '', report['semantics'], '',
             'Rounded to six decimals below; exact JSON retains float-derived body dimensions.', '',
             '| Support | Feet Y | Available center X × Z (union of rectangles) | Blockers at this height |',
             '| --- | ---: | --- | --- |']
    for block in report['blocks']:
        pieces = [interval(b[0], b[3]) + ' × ' + interval(b[2], b[5])
                  for b in block['exposedLandingClosure']]
        blockers = ', '.join(b['id'] + ' (' + b['relation'] + ')' for b in block['blockers'])
        lines.append(f"| {block['id']} | {block['top']:g} | {'; '.join(pieces) or 'None'} | {blockers or '—'} |")
    if 'faceCatalog' in report:
        catalog = report['faceCatalog']
        parts = {s['solidIndex']: s['partId'] for s in catalog['solids']}
        lines += ['', '## Merged physical faces', '',
                  'Physical tangent coordinates: X faces use Y/Z, Y faces use X/Z, Z faces use X/Y.',
                  'These covers describe the exposed solid boundary; player clearance remains a separate check.', '',
                  '| Count | Value |', '| --- | ---: |']
        lines += [f'| {name} | {value} |' for name, value in report['faceStatistics'].items()]
        lines += ['', '| Face | Outward normal | Plane | Tangent rectangle cover | Source parts |',
                  '| --- | --- | ---: | --- | --- |']
        for face in catalog['faces']:
            normal = ('+' if face['normalSign'] > 0 else '−') + 'XYZ'[face['axis']]
            cover = '; '.join(interval(r[0], r[2]) + ' × ' + interval(r[1], r[3]) for r in face['cover'])
            names = ', '.join(sorted({parts[c['sourceFaceKey'][0]] for c in face['contributions']}))
            lines.append(f"| {face['id'].split(':')[1][:12]} | {normal} | {face['plane']:g} | {cover} | {names} |")
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True, help='Exact JSON report')
    parser.add_argument('--markdown', help='Optional rounded table')
    parser.add_argument('--faces', action='store_true', help='Include merged physical faces in all six orientations')
    args = parser.parse_args()
    report = player_bounds_report(json.loads(Path(args.input).read_text()), include_faces=args.faces)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, allow_nan=False) + '\n')
    if args.markdown:
        path = Path(args.markdown)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(markdown_report(report))


if __name__ == '__main__':
    main()
