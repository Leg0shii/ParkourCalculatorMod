"""Required-anchor stages on the existing event/physical movement engine."""

import copy

from parkour_solver.search.identity import digest
from parkour_solver.search.events.adapter import EventGuideAdapter, EventReference
from parkour_solver.search.anchors.policy import audit, owner, visit_state, semantic_settings
from parkour_solver.spatial.anchor_guides import build_guides, onward_objective


class AnchorAdapter(EventGuideAdapter):
    def __init__(self, data, graph, policy, **kwargs):
        super().__init__(data,graph,**kwargs)
        self.anchor_policy=policy
        self.guide_plan=build_guides(data,policy)
        self.node_query=False

    def initial_cursor(self, roots):
        cursor=super().initial_cursor(roots)
        checks=[audit(self.scene,p,self.anchor_policy['anchors']) for p in roots]
        if any(not a['accepted'] for a in checks) or len({a['reached'] for a in checks})!=1:
            raise ValueError('Anchor roots violate ordered/no-return domain')
        index=checks[0]['reached']
        cursor.update(anchorIndex=index,spanStart=checks[0]['ticks'][-1] if index else 0)
        return cursor

    def next_goals(self, cursor, parents, continuation=None, *, budget=None):
        if continuation is not None and continuation['exhausted']:
            return [],continuation,dict(expansions=0,materializations=0)
        budget=budget or {}
        if budget.get('check'):budget['check']()
        index=cursor['anchorIndex']; anchors=self.anchor_policy['anchors']
        target=anchors[index] if index<len(anchors) else self.data['task']['block']
        terminal=index==len(anchors)
        requests=[]
        for event in self.graph['events']:
            if event['terminal']!=terminal:
                continue
            goal=event['goal']
            if goal['kind']!='landing' or target not in goal.get('blocks',[goal['block']]):
                continue
            request=dict(schema=1,id='anchor-request:'+digest([cursor,event['id'],target]),
                event=event['id'],goal=dict(goal,block=target,blocks=[target]),terminal=terminal,
                commitments=[],semanticPrefix=[],afterTick=min(p['profile']['airtime'] for p in parents),
                connector=dict(entry=cursor['attachments'],exit=event['attachments']))
            requests.append(request)
        if not requests:
            raise ValueError('Required anchor has no exposed contact event: '+target)
        return requests,dict(exhausted=True),dict(expansions=0,materializations=len(requests))

    def for_goal(self, request, cursor):
        result=copy.copy(self)
        result.request=copy.deepcopy(request);result.cursor=copy.deepcopy(cursor)
        span=cursor['anchorIndex']
        points=self.guide_plan['points']
        result.guide=dict(version=1,id=self.guide_plan['id']+':'+str(span),
                          points=points[span:span+2],target=points[span+1],hardConstraints=False)
        result.objective=onward_objective(self.guide_plan,span,self.scene['objective'])
        result.score_context=digest(dict(request=request,guide=result.guide,objective=result.objective,
                                         policy=semantic_settings(self.anchor_policy)))
        result.corridor=EventReference(self.data['domainId'],request['id'],result.guide['id'])
        result.program=None
        return result

    def for_node(self):
        result=copy.copy(self);result.node_query=True
        return result

    def advance(self, cursor, child, observed, request):
        result=super().advance(cursor,child,observed,request)
        result['anchorIndex']+=1
        result['spanStart']=child['profile']['airtime']
        return result

    def context(self, context):
        state=visit_state(self.scene,context['program'])
        index=self.cursor['anchorIndex']
        forbidden={owner(self.scene,a) for a in self.anchor_policy['anchors'][index+1:]}
        if index<len(self.anchor_policy['anchors']):
            forbidden.add(owner(self.scene,self.data['task']['block']))
        context['supportAllowed']=lambda block: (owner(self.scene,block['id']) not in state['departed'] and
                                                owner(self.scene,block['id']) not in forbidden)
        context['landingAllowed']=context['supportAllowed']

    def program_allowed(self, program):
        return audit(self.scene,program,self.anchor_policy['anchors'],
                     reached=self.cursor['anchorIndex'])['accepted']

    def candidate(self, plan, candidate, vertical, *, partial=False):
        prior=audit(self.scene,self.program,self.anchor_policy['anchors'],
                    reached=self.cursor['anchorIndex'])
        if not prior['accepted']:
            return dict(prior,alternatives=[])
        if self.node_query:
            return dict(accepted=True,requiredEventsSatisfied=True,taskSatisfied=False,
                        validation='exact_node_prior_anchors_only',alternatives=[])
        return super().candidate(plan,candidate,vertical,partial=partial)

    def summary(self):
        return dict(super().summary(),anchorPolicy=self.anchor_policy,guidePlan=self.guide_plan,
                    backend='anchor_goals_v1',visitScope='acyclic_owners_with_current_owner_hops')
