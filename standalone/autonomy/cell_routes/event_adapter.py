"""Event stage provider for the existing DFS, with no raw-cell filters."""

import copy
import math
from dataclasses import dataclass

from ..movement_composition import boundary_state
from ..open_movement import approaches, first_descent
from .event_routes import initial_cursor, next_goals, advance_cursor, support_episodes
from .guides import guide_for, outgoing_objective, task_reference
from .regions import geometry_key


@dataclass(frozen=True)
class EventReference:
    domain: str
    request: str = ''
    guide: str = ''

    def to_dict(self):
        return dict(kind='event_guidance', schema=1, domainId=self.domain, request=self.request, guide=self.guide)


class EventGuideAdapter:
    search_contract = 'event_goals_v1'

    def __init__(self, data, graph, *, motion=None, compiler=None, candidate_seconds=1):
        self.data, self.graph = data, graph
        self.scene = data['scene']
        self.motion = motion or {}
        self.corridor = EventReference(data['domainId'])
        self.gates = []
        self.request = None
        self.program = None
        self.counts = dict(eventAudits=0, requiredEventRejections=0)
        self.interaction_cache = {}
        self.compiler, self.candidate_seconds = compiler, candidate_seconds
        self.flight_cache = {}

    def initial_cursor(self, roots):
        return initial_cursor(self.data, roots)

    def next_goals(self, cursor, parents, continuation=None, *, budget=None):
        budget = budget or {}
        requests, state, report = next_goals(self.data, self.graph, cursor, continuation,
            max_expansions=budget.get('expansions',5000), max_materializations=budget.get('materializations',50),
            check=budget.get('check'))
        # Scheduling hints only. A normal-flight radius is never a proof about
        # all admitted stepping/ceiling/incoming-state realizations.
        def rank(request):
            possible = False
            earliest = float('inf')
            target = request['goal']
            for parent in parents:
                remaining = self.data['inputDomain']['maxInputs']-parent['profile']['airtime']
                if remaining <= 0:
                    continue
                source = next((b for b in self.scene['blocks'] if b['id']==parent['terminalSupport']),None)
                entry = self.scene['initial']['position'] if source is None else [
                    (source['bounds'][0]+source['bounds'][1])/2,source['top'],(source['bounds'][2]+source['bounds'][3])/2]
                goal = (task_reference(self.data,entry) if request['terminal'] else
                        next(s['cover'][0][:3] for s in self.data['surfaces'] if s['id']==target['surface']))
                gap = math.hypot(goal[0]-entry[0],goal[2]-entry[2])
                ctx=dict(state=boundary_state(parent),maxInputs=remaining,threshold=0.)
                for _, jumping, trace in approaches(ctx):
                    height = target['height'] if target['kind']=='landing_height_region' else next(
                        b['top'] for b in self.scene['blocks'] if b['id']==target['block'])
                    tick=first_descent(trace,height)
                    if tick is not None:
                        radius=(tick+1)*self.motion.get('movementAfterSupportCheck',1.)
                        if callable(getattr(self.compiler,'compile',None)):
                            n=tick+1
                            key=(ctx['state']['onGround'],jumping,n)
                            if key not in self.flight_cache:
                                try:
                                    if budget.get('check'):budget['check']()
                                    flight=self.compiler.compile(dict(self.scene,sharedMovement=True),
                                        dict(grounded=[ctx['state']['onGround']]+[False]*(n-1),jump=[jumping]+[False]*(n-1)),
                                        self.candidate_seconds)
                                    speed=self.motion['storedSpeedNorm']; total=0.
                                    for magnitude,friction in zip(flight['magnitude'],flight['friction']):
                                        move=speed+magnitude;total+=move;speed=move*friction
                                    self.flight_cache[key]=total
                                except TimeoutError:
                                    pass
                            radius=self.flight_cache.get(key,radius)
                        if gap <= radius+1.:
                            possible=True;earliest=min(earliest,tick+1)
            event=next(e for e in self.graph['events'] if e['id']==request['event'])
            same=bool(set(event['attachments']).intersection(cursor['attachments']))
            reference=task_reference(self.data,self.scene['initial']['position'])
            target=guide_for(self.data,self.graph,request,self.scene['initial']['position'],max_expansions=0)['target']
            distance=math.hypot(target[0]-reference[0],target[2]-reference[2])
            return (not possible, same, not request['terminal'],earliest,distance,request['id'])
        requests.sort(key=rank)
        for request in requests:
            request['afterTick']=min(p['profile']['airtime'] for p in parents)
            event=next(e for e in self.graph['events'] if e['id']==request['event'])
            request['localSetupAlias']=not request['terminal'] and bool(
                set(event['attachments']).intersection(cursor['attachments']))
        return requests,state,report

    def for_goal(self, request, cursor):
        result=copy.copy(self)
        result.request=copy.deepcopy(request)
        result.cursor=copy.deepcopy(cursor)
        entry=self.scene['initial']['position']
        if cursor['occurrences']:
            previous=next(e for e in self.graph['events'] if e['id']==cursor['occurrences'][-1]['event'])
            if previous['cover']:
                from .routes import conditioned_anchor
                entry=conditioned_anchor(previous['cover'],entry,task_reference(self.data,entry))
        result.guide=guide_for(self.data,self.graph,request,entry)
        result.objective=outgoing_objective(self.data,self.graph,request,result.guide)
        result.score_context=geometry_key(dict(request=request['id'],commitments=request['commitments'],
                                               objective=result.objective,guide=result.guide['id']))
        result.corridor=EventReference(self.data['domainId'],request['id'],result.guide['id'])
        result.program=None
        return result

    def advance(self, cursor, child, observed, request):
        return advance_cursor(self.data,self.graph,cursor,request,observed)

    def context(self, context):
        target=self.request['goal']
        current=context['program']['terminalSupport']
        allowed=set(target.get('blocks',[target['block']]) if target['kind']=='landing' else [])
        allowed.add(current)
        context['supportAllowed']=lambda block:block['id'] in allowed

    def program_allowed(self, program):
        return True

    def scoped(self, scope):
        result=copy.copy(self);result.scope=scope
        return result

    def bind_program(self, program):
        result=copy.copy(self);result.program=program
        return result

    def eligible(self, envelopes, through):
        return self

    def candidate(self, plan, candidate, vertical, *, partial=False):
        if self.program is None or self.request is None:
            raise ValueError('Event validation requires a physical program and goal request')
        self.counts['eventAudits']+=1
        goal=self.request['goal']
        target_owners=[b.get('sourceBlock',b['id']) for b in self.scene['blocks']
                       if goal['kind']=='landing' and b['id'] in goal.get('blocks',[goal['block']])]
        episodes=support_episodes(self.data,self.program['profile'],required_owners=target_owners)
        required=True
        if goal['kind']=='landing':
            blocks=[b for b in self.scene['blocks'] if b['id'] in goal.get('blocks',[goal['block']])]
            solids={b['solidIndex'] for b in blocks}
            required=any(set(e['solids']).intersection(solids) and e['input'] >= self.request['afterTick']
                         for e in episodes if e['kind'] in ('support_visit','named_contact'))
        else:
            from ..terminal_goals import satisfied
            required=satisfied(self.program,candidate,goal,self.scene.get('supportClearance',1e-6))
        self.counts['requiredEventRejections']+=int(not required)
        return dict(accepted=required,reason=None if required else 'required_event_not_observed',
            requiredEventsSatisfied=required,taskSatisfied=required and self.request['terminal'],
            guideClassification='uncommitted',observedEvents=episodes,alternatives=[])

    def summary(self):
        return dict(self.counts,backend='event_goals_v1',domainId=self.data['domainId'],
            filtering='physical_constraints_and_required_events',coverage=self.data['coverage'])
