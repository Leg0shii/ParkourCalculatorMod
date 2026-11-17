"""Developer rendering of physical support and free-volume regions."""


def diagnostic_svg(graph, *, y, bounds=None, support_all_heights=False):
    """Draw actual finite region rectangles, with physical support outlines."""
    boxes = [b for r in graph.regions for b in r.cover]
    if not boxes:
        raise ValueError('No finite route regions to draw')
    view=tuple(bounds) if bounds is not None else (
        min(b[0] for b in boxes),0,min(b[2] for b in boxes),max(b[3] for b in boxes),0,max(b[5] for b in boxes))
    x,z=view[0],view[2]
    width,height=view[3]-x,view[5]-z
    stroke = max(width, height)/600
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {z} {width} {height}">']
    for i, region in enumerate(graph.regions):
        for b in region.cover:
            if b[1] <= y <= b[4] or support_all_heights and region.kind=='support_surface':
                support = region.kind == 'support_surface'
                fill = 'none' if support else f'hsl({i*137%360},50%,85%)'
                color = 'red' if support else 'black'
                lines.append(f'<rect x="{b[0]}" y="{b[2]}" width="{b[3]-b[0]}" height="{b[5]-b[2]}" '
                             f'fill="{fill}" fill-opacity="0.25" stroke="{color}" stroke-width="{stroke*(3 if support else 1)}"/>')
    return '\n'.join(lines + ['</svg>'])
