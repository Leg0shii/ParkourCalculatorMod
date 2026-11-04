import json
import unittest

from standalone.autonomy.cell_routes.event_routes import build_route_domain, build_event_graph, initial_cursor, next_goals, advance_cursor, semantic_prefix
from standalone.autonomy.tests.test_route_regions import task_scene


class EventRouteTests(unittest.TestCase):
    def domain(self):
        scene = task_scene([[0,0,0,1,1,1], [3,0,-2,4,2,-1], [3,0,2,4,2,3], [6,0,0,7,1,1]],
                           bounds=(-5,-5,-6,10,8,8))
        return build_route_domain(scene, dict(kind='landing',block='3'))

    def test_both_contacts_and_overflight_finish_remain_in_one_language(self):
        domain = self.domain(); data = domain.to_dict(); graph = build_event_graph(domain)
        requests, state, _ = next_goals(data, graph, initial_cursor(domain))
        self.assertTrue(state['exhausted'])
        self.assertEqual(len(requests), len(graph['events']))
        self.assertTrue(any(r['terminal'] and r['goal']['block'] == '3' for r in requests))
        self.assertTrue({'1','2'} <= {r['goal']['block'] for r in requests})
        self.assertTrue(all(r['connector']['selectedWalk'] is None for r in requests))
        raw = data['geometry']; air = {r['key'] for r in raw['regions'] if r['kind']=='air_volume'}
        # Opposite-side geometric alternatives and reverse edges are retained;
        # none has been converted into an independent complete itinerary.
        links = {(e['source'],e['target']) for e in raw['transitions'] if e['kind']=='open_portal'}
        self.assertTrue(links)
        self.assertTrue(all((b,a) in links for a,b in links))
        self.assertTrue(all(a in air and b in air for a,b in links))

    def test_work_limits_and_reload_preserve_all_next_goals(self):
        domain=self.domain(); graph=build_event_graph(domain); cursor=initial_cursor(domain)
        expected, _, _ = next_goals(domain,graph,cursor)
        state=None; actual=[]
        for _ in range(200):
            requests,state,report = next_goals(domain,graph,cursor,state,max_expansions=2,max_materializations=1)
            actual += requests
            state=json.loads(json.dumps(state,allow_nan=False))
            if state['exhausted']:break
        self.assertTrue(state['exhausted'])
        self.assertEqual({r['id'] for r in actual},{r['id'] for r in expected})
        self.assertEqual(len(actual),len(expected))

    def test_revisits_are_occurrences_without_a_global_visit_cap(self):
        domain=self.domain(); graph=build_event_graph(domain); cursor=initial_cursor(domain)
        for index in range(6):
            requests,_,_=next_goals(domain,graph,cursor)
            chosen=next(r for r in requests if not r['terminal'] and r['goal']['block']==str(index%2))
            cursor=advance_cursor(domain,graph,cursor,chosen,[])
        self.assertEqual(len(cursor['occurrences']),6)
        self.assertEqual([o['occurrence'] for o in cursor['occurrences']],list(range(6)))

    def test_unknown_boundary_cannot_certify_disconnection(self):
        domain=self.domain(); data=domain.to_dict(); graph=build_event_graph(domain)
        cursor=initial_cursor(domain); cursor['attachments']=[]
        requests,_,_=next_goals(data,graph,cursor)
        self.assertEqual(len(requests),len(graph['events']))
        self.assertTrue(all(r['connector']['outsideUnresolved'] for r in requests))

    def test_route_semantics_exclude_absolute_timing_but_keep_physical_feature_order(self):
        observed=dict(events=[dict(kind='named_contact',case='blip',owners=['floor'],input=3)],
            passageAlternatives=[[dict(features=[['face:left'],['face:top']],gates=[5],resources=dict(total=1))]])
        cursor=dict(occurrences=[dict(event='visit:target',occurrence=0,observed=observed)])
        before=semantic_prefix(cursor)
        observed['events'][0]['input']=9;observed['passageAlternatives'][0][0]['gates']=[12]
        self.assertEqual(semantic_prefix(cursor),before)
        observed['passageAlternatives'][0][0]['features'].reverse()
        self.assertNotEqual(semantic_prefix(cursor),before)


if __name__=='__main__':unittest.main()
