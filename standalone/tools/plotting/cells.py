"""Developer rendering of spatial decomposition."""


def diagnostic_svg(decomposition, *, y, bounds=None):
    b = bounds or decomposition.bounds
    width, height = b[3] - b[0], b[5] - b[2]
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{b[0]} {b[2]} {width} {height}">']
    stroke = max(width, height) / 600
    for c in decomposition.cells:
        if c.bounds[1] <= y <= c.bounds[4]:
            x, _, z, xx, _, zz = c.bounds
            lines.append(f'<rect x="{x}" y="{z}" width="{xx-x}" height="{zz-z}" fill="hsl({c.id*137%360},50%,85%)" stroke="black" stroke-width="{stroke}"/>')
    lines.append('</svg>')
    return '\n'.join(lines)
