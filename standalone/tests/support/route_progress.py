"""Shared test builders extracted from tests.unit.spatial.test_route_progress."""
from dataclasses import asdict

from parkour_solver.spatial.regions import RouteRegion, RouteTransition

from parkour_solver.spatial.compat.corridors import Corridor

def line_route(*, with_supports=True):
    regions = [RouteRegion('a', 'air_volume', ((0,0,-1,2,4,1),), (0,)),
               RouteRegion('b', 'air_volume', ((2,0,-1,4,4,1),), (1,)),
               RouteRegion('c', 'air_volume', ((4,0,-1,6,4,1),), (2,))]
    edges = [RouteTransition('ab', 'a','b','open_portal', ((2,0,-1,2,4,1),)),
             RouteTransition('bc', 'b','c','open_portal', ((4,0,-1,4,4,1),))]
    path, transitions = ['a','b','c'], ['ab','bc']
    if with_supports:
        regions += [RouteRegion('s','support_surface', ((0,1,-1,1,1,1),), (0,), ('start',)),
                    RouteRegion('g','support_surface', ((5,1,-1,6,1,1),), (2,), ('goal',))]
        edges += [RouteTransition('sa','s','a','support_departure', ((0,1,-1,1,1,1),)),
                  RouteTransition('cg','c','g','support_arrival', ((5,1,-1,6,1,1),))]
        path, transitions = ['s']+path+['g'], ['sa']+transitions+['cg']
    return Corridor(0, [0,1,2], [], [], (), [], 0, schema=2, graph_context='test',
        route_variants=[dict(regions=path, transitions=transitions)],
        region_graph=dict(regions=[asdict(r) for r in regions], transitions=[asdict(t) for t in edges]))
