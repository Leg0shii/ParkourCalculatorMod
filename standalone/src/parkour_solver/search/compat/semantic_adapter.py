"""Immutable stage views of a typed corridor for the existing movement DFS."""

import copy

from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.spatial.regions import geometry_key
from parkour_solver.constraints.route_guards import RouteGuards, physical_events
from parkour_solver.spatial.compat.progress import advance, initial_cursor, phase_trace


class SemanticCorridorAdapter(CorridorAdapter):
    def __init__(self, scene, corridor, *, decomposition=None):
        if corridor.schema != 2:
            raise ValueError('Semantic adapter requires corridor schema2')
        super().__init__(scene,corridor,[],decomposition=decomposition)
        self.full_corridor = corridor
        self.program = None
        self.goal_index = None
        self.contact_phase_only = False

    def goals(self):
        regions = {r['key']:r for r in self.full_corridor.region_graph['regions']}
        edges = {t['key']:t for t in self.full_corridor.region_graph['transitions']}
        result = []
        for key in self.full_corridor.route_variants[0]['transitions']:
            edge = edges[key]
            target = regions[edge['target']]
            if edge['kind']=='support_arrival':
                blocks = [b['id'] for b in self.scene['blocks'] if b['solidIndex'] in target['solids']]
                result.append(dict(kind='landing',block=blocks[0],blocks=blocks,surface=target['key']))
            elif edge['kind']=='finish':
                result.append(copy.deepcopy(target['predicate']))
        return result

    def for_goal(self, index):
        result = copy.copy(self)
        result.corridor = copy.deepcopy(self.full_corridor)
        edges = {t['key']:t for t in self.full_corridor.region_graph['transitions']}
        variants = []
        for variant in self.full_corridor.route_variants:
            seen = -1
            stop = len(variant['transitions'])
            required = len(variant['regions'])-1
            for i, key in enumerate(variant['transitions']):
                if edges[key]['kind'] in ('support_arrival','finish'):
                    seen += 1
                    if seen == index:
                        required=i+1
                    elif seen == index+1:
                        # The current landing can run off in its remaining X/Z
                        # phases. Permit the selected outgoing air itinerary,
                        # without requiring its next contact/finish yet.
                        stop=i
                        break
            variants.append(dict(regions=variant['regions'][:stop+1],transitions=variant['transitions'][:stop],required=required))
        result.corridor.route_variants = variants
        result.corridor.graph_context = geometry_key((self.full_corridor.graph_context,index,variants))
        result.goal_index = index
        final=variants[0]
        regions={r['key']:r for r in self.full_corridor.region_graph['regions']}
        result.contact_phase_only=(final['required']==len(final['regions'])-1 and
            regions[final['regions'][final['required']]]['kind']=='support_surface')
        result.program = None
        result.progress_cache = {}
        return result

    def outgoing_objective(self,index):
        from parkour_solver.spatial.checkpoints import Checkpoint, horizontal_segments
        goals = [i for i,s in enumerate(self.full_corridor.stages) if s['kind'] in ('support_arrival','finish')]
        after = goals[index]
        guide = copy.copy(self.full_corridor)
        guide.points = [s['guide_point'] for s in self.full_corridor.stages[after:]]
        segments = horizontal_segments(guide)
        if not segments:
            return self.scene['objective']
        first=segments[0]
        return Checkpoint(1,-1,first['direction'],tuple(first['start']),tuple(first['end']),()).objective

    def bind_program(self, program):
        result = copy.copy(self)
        result.program = program
        return result

    def context(self, context):
        # The existing InteractionGate still applies after this adapter. Keep
        # all physical parts of each selected support, not just one box ID.
        super().context(context)
        positions=self.program_states(context['program'])
        if positions is None:
            return
        regions={r['key']:r for r in self.full_corridor.region_graph['regions']}
        goals=self.goals()
        solids=set()
        for index,last in positions:
            solids.update(regions[last]['solids'])
            if index<len(goals) and goals[index]['kind']=='landing' and (self.goal_index is None or index<=self.goal_index):
                solids.update(regions[goals[index]['surface']]['solids'])
        context['supportAllowed']=lambda block:block['solidIndex'] in solids

    def program_states(self,program):
        regions={r['key']:r for r in self.full_corridor.region_graph['regions']}
        start=self.full_corridor.route_variants[0]['regions'][0]
        if regions[start]['kind']!='support_surface':
            # No fabricated contact interpretation for an airborne root.
            return None
        goals=self.goals()
        possible={(0,start)}
        for event in sorted(program['profile'].get('contactEvents',[]),key=lambda e:e['offset']):
            if event['axis']!='Y' or event.get('direction')!=-1:
                continue
            indices={b['solidIndex'] for b in self.scene['blocks']
                     if self.scene['collisionScene']['solids'][b['solidIndex']]==event['box']}
            def matches(key):
                region=regions[key]
                return bool(indices.intersection(region['solids']) and
                            any(abs(b[1]-event['plane'])<=1e-7 for b in region['cover']))
            following=set()
            for index,last in possible:
                if matches(last):
                    following.add((index,last))
                if index<len(goals) and goals[index]['kind']=='landing' and matches(goals[index]['surface']):
                    following.add((index+1,goals[index]['surface']))
            possible=following
            if not possible:
                break
        return possible

    def program_allowed(self,program):
        positions=self.program_states(program)
        return positions is None or any(self.goal_index is None or index<=self.goal_index+1 for index,_ in positions)

    def scoped(self, scope):
        result = copy.copy(self)
        result.validation_scope = scope
        return result

    def eligible(self, envelopes, through):
        # No horizontal landmark gates are used by the typed route contract.
        return self

    def _omitted(self):
        scope = getattr(self,'validation_scope',None)
        return scope is not None and (scope['purpose']=='preliminary' or
                                      len(scope['solidIndices']) != len(self.scene['collisionScene']['solids']))

    def constraint_guards(self, profile, envelopes, scope, deadline):
        if self._omitted():
            return None
        return RouteGuards(self.scene,self.corridor,profile,scope['through'],complete=True,
                           envelopes=envelopes,deadline=deadline,
                           phase_count=3*scope['through']-2 if self.contact_phase_only and
                           scope['through']==profile['airtime'] and profile.get('landingPhase')=='pre' else None)

    def candidate(self, plan, candidate, vertical, *, partial=False):
        self.counts['corridorAudits'] += 1
        if self._omitted():
            return dict(accepted=True,routeCertified=False,validation='preliminary_route_requirements_omitted')
        if self.program is None:
            raise ValueError('Semantic route validation needs the selected physical program')
        phases = phase_trace(dict(candidate,y=vertical['y']))
        profile = self.program['profile']
        if self.contact_phase_only and profile.get('landingPhase')=='pre':
            phases=phases[:-2]
        uncertain = {e['input'] for e in profile.get('caseEvents',[])}
        for phase in phases:
            if phase['input'] in uncertain:
                phase['uncertainProbes'] = True
        events = [dict(phase=p,owner=owner) for p,owners in physical_events(self.scene,profile).items()
                  for owner in sorted(owners) if p < len(phases)]
        virtual = self.program.get('terminalGoal')
        if virtual is not None:
            from parkour_solver.search.virtual_goals import satisfied
            events.append(dict(kind='finish',phase=len(phases)-1,
                validated=satisfied(self.program,candidate,virtual,self.scene.get('supportClearance',1e-6))))
        checked = advance(self.corridor,initial_cursor(self.corridor),phases,events,dict(complete=True))
        accepted = checked['status']=='compatible' and checked['complete']
        self.counts['corridorRejections'] += int(not accepted)
        self.counts['routeUnknowns'] += int(checked['status']=='unknown')
        return dict(accepted=accepted, reason=None if accepted else 'semantic_route_'+checked['status'],
                    routeCertified=accepted, routeProgress=checked, alternatives=[], repairComplete=False,
                    routeHorizonPhase=phases[-1]['phase'] if phases else None,
                    routeRequirement='physical_contact_phase' if self.contact_phase_only else 'selected_goal_with_outgoing_regions',
                    requiredOccurrences=[v.get('required',len(v['regions'])-1) for v in self.corridor.route_variants])

    def summary(self):
        return dict(self.counts,schema=2,graphContext=self.full_corridor.graph_context,
                    filtering='fixed_3d_axis_sweeps_and_physical_contact_itinerary',originalInitialState=self.initial)
