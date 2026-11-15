import copy
from dataclasses import asdict, dataclass
import math

from parkour_solver.numerics.model import integer, number
from parkour_solver.spatial.compat.corridors import crossings


def horizontal_segments(corridor):
    result = []
    for index, (a, b) in enumerate(zip(corridor.points, corridor.points[1:])):
        delta = (b[0]-a[0], b[2]-a[2])
        length = math.hypot(*delta)
        if length <= 1e-9:
            continue
        direction = tuple(v/length for v in delta)
        if result and sum(x*y for x, y in zip(result[-1]['direction'], direction)) >= 1-1e-9:
            result[-1].update(end=list(b), lastPoint=index+1)
        else:
            result.append(dict(start=list(a), end=list(b), firstPoint=index, lastPoint=index+1, direction=direction))
    return result


@dataclass(frozen=True)
class Checkpoint:
    tick: int
    segment: int
    direction: tuple
    start: tuple
    end: tuple
    required_signature: tuple
    region: tuple = ()
    bounded_end: bool = False

    @property
    def objective(self):
        return dict(axis='X', sense='MAX', customYaw=math.degrees(math.atan2(-self.direction[0], self.direction[1])))

    def to_dict(self):
        return dict(asdict(self), objective=self.objective, indexing='absolute_pre_input_state_zero_based', scoreUsesY=False)

    def rows(self):
        if not self.region:
            return []
        return [dict(leg=0, offset=self.tick, mode=mode, cmp=cmp, rhs=value, name='checkpoint_probe:'+str(self.tick))
                for mode, lo, hi in [('X', self.region[0], self.region[1]), ('Z', self.region[2], self.region[3])]
                for cmp, value in [('GE', lo), ('LE', hi)]]

    def stage(self, candidate, gates):
        from parkour_solver.spatial.compat.corridors import horizontal_trace
        points, _ = horizontal_trace(candidate)
        actual = tuple(e['token'] for e in crossings(points, gates))
        reached = actual[:len(self.required_signature)] == self.required_signature and len(actual) >= len(self.required_signature)
        x, z = candidate['positions'][self.tick]
        progress = self.direction[0]*(x-self.start[0])+self.direction[1]*(z-self.start[2])
        length = self.direction[0]*(self.end[0]-self.start[0])+self.direction[1]*(self.end[2]-self.start[2])
        before_next_turn = not self.bounded_end or progress <= length+1e-7
        return dict(accepted=reached and progress >= -1e-7 and before_next_turn,
                    requiredSignature=self.required_signature, actualSignature=actual,
                    outgoingProgress=progress, beforeNextTurn=before_next_turn)


def checkpoint(corridor, gates, spec):
    if isinstance(spec,Checkpoint):
        integer(spec.tick,'checkpoint tick',1,200)
        if len(spec.direction)!=2 or abs(math.hypot(*spec.direction)-1)>1e-7:
            raise ValueError('Explicit checkpoint direction must be a unit horizontal vector')
        return spec
    if not isinstance(spec, dict) or set(spec)-{'tick', 'segment', 'region'}:
        raise ValueError('Checkpoint needs tick and optional outgoing segment/region')
    tick = integer(spec.get('tick'), 'checkpoint tick', 1, 200)
    segments = horizontal_segments(corridor)
    if not segments:
        raise ValueError('The corridor has no horizontal objective direction')
    index = integer(spec.get('segment', len(segments)-1), 'checkpoint segment', 0, len(segments)-1)
    segment = segments[index]
    region = tuple(spec.get('region', ()))
    if region:
        if len(region) != 4:
            raise ValueError('Comparison region must be minX,maxX,minZ,maxZ')
        for value in region:
            number(value, 'comparison-region bound')
        if region[0] >= region[1] or region[2] >= region[3]:
            raise ValueError('Comparison region is empty')
    prefix = corridor.points[:segment['firstPoint']+1]
    required = tuple(e['token'] for e in crossings(prefix, gates))
    return Checkpoint(tick, index, segment['direction'], tuple(segment['start']), tuple(segment['end']),
                      required, region, index < len(segments)-1)


def retain_prefix(program, best):
    result = copy.deepcopy(program)
    result.pop('repairState', None)
    retained = result['profile']['positionConstraints']
    identities = {repr(sorted(r.items())) for r in retained}
    for row in best['plan']['walls']:
        if row['mode'] == 'F':
            raise ValueError('Checkpoint retention must not fix a facing')
        if row.get('name', '').startswith('checkpoint_probe:'):
            continue
        chosen = {k: v for k, v in row.items() if k in ('mode', 't1', 't2', 'op', 'cmp', 'rhs', 'name')}
        identity = repr(sorted(chosen.items()))
        if identity not in identities:
            retained.append(chosen)
            identities.add(identity)
    result['guideSeedYaw'] = best['candidate']['yaw'][:]
    return result


def retain_unresolved(program, state):
    result = copy.deepcopy(program)
    if state is not None:
        result['repairState'] = copy.deepcopy(state)
        for branch in state['pending']+state['unresolved']:
            if branch.get('warmYaw') is not None:
                result['guideSeedYaw'] = branch['warmYaw'][:]
                break
    return result


def retain_decisions(program, result, *, facing_guess=None):
    retained = copy.deepcopy(program)
    if result.get('repairState') is not None:
        retained['repairState'] = copy.deepcopy(result['repairState'])
    best = result.get('best')
    if best is not None:
        facing_guess = best['candidate']['yaw']
    if facing_guess is not None:
        retained['guideSeedYaw'] = facing_guess[:retained['profile']['airtime']]
    return retained
