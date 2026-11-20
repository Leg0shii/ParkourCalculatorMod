"""Required-anchor stages on the existing event/physical movement engine."""

import copy

from parkour_solver.search.identity import digest
from parkour_solver.search.events.adapter import EventGuideAdapter, EventReference
from parkour_solver.search.anchors.policy import audit, encounter_groups, owner, visit_state, semantic_settings
from parkour_solver.spatial.anchor_guides import build_guides, onward_objective


class AnchorAdapter(EventGuideAdapter):
    def __init__(self, data, graph, policy, **kwargs):
        super().__init__(data,graph,**kwargs)
        self.anchor_policy=policy
        self.guide_plan=build_guides(data,policy)
        self.guide_plan['groups']=encounter_groups(self.scene,self.guide_plan['anchors'])
        self.node_query=False

    def initial_cursor(self, roots):
        cursor=super().initial_cursor(roots)
        checks=[audit(self.scene,p,self.anchor_policy['anchors']) for p in roots]
        if any(not a['accepted'] for a in checks) or len({a['reached'] for a in checks})!=1:
            raise ValueError('Anchor roots violate ordered/no-return domain')
        index=checks[0]['reached']
        cursor.update(anchorIndex=index,groupEnd=self.group_end(index),spanStart=checks[0]['ticks'][-1] if index else 0)
        return cursor

    def group_end(self, index):
        """The last pair of the encounter group holding pair `index`; a frame searches the whole group."""
        return next(g['last'] for g in self.guide_plan['groups'] if g['first']<=index<=g['last'])

    def next_goals(self, cursor, parents, continuation=None, *, budget=None):
        if continuation is not None and continuation['exhausted']:
            return [],continuation,dict(expansions=0,materializations=0)
        budget=budget or {}
        if budget.get('check'):budget['check']()
        end=cursor['groupEnd']; anchors=self.anchor_policy['anchors']
        target=anchors[end] if end<len(anchors) else self.data['task']['block']
        terminal=end==len(anchors)
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
        span,end=cursor['anchorIndex'],cursor['groupEnd']
        points=self.guide_plan['points']
        result.guide=dict(version=1,id=self.guide_plan['id']+':'+str(span),
                          points=points[span:end+2],target=points[end+1],hardConstraints=False)
        result.objective=onward_objective(self.guide_plan,end,self.scene['objective'])
        result.score_context=digest(dict(request=request,guide=result.guide,objective=result.objective,
                                         policy=semantic_settings(self.anchor_policy)))
        result.corridor=EventReference(self.data['domainId'],request['id'],result.guide['id'])
        result.program=None
        return result

    def for_node(self, pair):
        """A decision check inside pair `pair` aims onward from that pair's target, not the group's end."""
        result=copy.copy(self);result.node_query=True
        result.objective=onward_objective(self.guide_plan,pair,self.scene['objective'])
        return result

    def advance(self, cursor, child, observed, request):
        result=super().advance(cursor,child,observed,request)
        result['anchorIndex']=result['groupEnd']+1
        result['groupEnd']=self.group_end(result['anchorIndex'])
        result['spanStart']=child['profile']['airtime']
        return result

    def context(self, context):
        state=visit_state(self.scene,context['program'])
        end=self.cursor['groupEnd']
        forbidden={owner(self.scene,a) for a in self.anchor_policy['anchors'][end+1:]}
        if end<len(self.anchor_policy['anchors']):
            forbidden.add(owner(self.scene,self.data['task']['block']))
        context['feetAllowed']=lambda block: (owner(self.scene,block['id']) not in state['departed'] and
                                             owner(self.scene,block['id']) not in forbidden)

    def program_allowed(self, program):
        anchors=self.anchor_policy['anchors'];end=self.cursor['groupEnd']
        target=anchors[end] if end<len(anchors) else self.data['task']['block']
        landed=owner(self.scene,program.get('terminalSupport'))==owner(self.scene,target)
        return audit(self.scene,program,anchors,
                     reached=min(end+1,len(anchors)) if landed else self.cursor['anchorIndex'])['accepted']

    def candidate(self, plan, candidate, vertical, *, partial=False):
        anchors=self.anchor_policy['anchors']
        # A group-end arrival must have visited every anchor inside the group.
        required=self.cursor['anchorIndex'] if self.node_query else min(self.cursor['groupEnd']+1,len(anchors))
        prior=audit(self.scene,self.program,anchors,reached=required)
        if not prior['accepted']:
            return dict(prior,alternatives=[])
        if self.node_query:
            return dict(accepted=True,requiredEventsSatisfied=True,taskSatisfied=False,
                        validation='exact_node_prior_anchors_only',alternatives=[])
        return super().candidate(plan,candidate,vertical,partial=partial)

    def summary(self):
        return dict(super().summary(),anchorPolicy=self.anchor_policy,guidePlan=self.guide_plan,
                    backend='anchor_goals_v1',visitScope='acyclic_owners_with_current_owner_hops')
