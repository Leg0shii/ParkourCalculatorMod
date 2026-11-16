import copy
import gzip
import json
import unittest
from unittest.mock import patch

from parkour_solver.constraints.variants import ConstraintVariants, _OptionKeys
from parkour_solver.search.identity import digest
from tests.support.paths import REGRESSION_FIXTURES


class OptionKeyTests(unittest.TestCase):
    def test_canonical_keys_preserve_signed_zero_and_rebuild_after_model_change(self):
        owner=ConstraintVariants.__new__(ConstraintVariants)
        owner.route=dict(supportClearance=0.)
        owner.envelopes=dict(states=[dict(X=[-1.,1.],Z=[-1.,1.]) for _ in range(3)])
        item=dict(faces=[dict(axis='X',direction=-1,plane=value) for value in (-0.,0.)]+[
            dict(axis='Z',direction=1,plane=1.)],chains=[[0,2],[1,2]],required=dict(X=[0,1,2],Z=[0,1,2]))
        keys=_OptionKeys(owner,item)
        first=keys.effective(0,1,1,[1]);second=keys.effective(1,1,1,[1])
        self.assertNotEqual(first,second)
        for i in (0,1):
            rows=owner.segment_rows(item,item['faces'][i],0,1)
            expected=owner.effective_key(rows)
            self.assertEqual(keys.node(i,1,1,[1]),digest([i,1,1,expected]))
        with patch.object(_OptionKeys,'LIMIT',1):
            tiny=_OptionKeys(owner,item)
            for _ in range(3):
                self.assertEqual(tiny.effective(0,1,1,[1]),first)
                self.assertEqual(tiny.effective(1,1,1,[1]),second)
                self.assertLessEqual(len(tiny.row_keys),1)
        for state in owner.envelopes['states']:state['X']=[-1.,-1.]
        self.assertEqual(_OptionKeys(owner,item).effective(0,1,1,[1]),digest([]))


class OptionRepresentativeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture=json.loads(gzip.decompress((REGRESSION_FIXTURES/'option-representative-resume.json.gz').read_bytes()))

    def make(self):
        case=self.fixture['case'];owner=ConstraintVariants.__new__(ConstraintVariants)
        owner.__dict__.update(copy.deepcopy(case['attributes']))
        owner.envelopes=copy.deepcopy(case['envelopes'])
        owner.state=dict(context=case['metadata']['context'],duplicateVariants=0,
            timings=dict(preparationSeconds=0.,variantGenerationSeconds=0.))
        owner.deadline=None;owner.route_guards=None
        return owner,copy.deepcopy(case['item'])

    def consume(self,owner,item,cursor):
        outputs=[]
        while True:
            option=owner.option(item,cursor)
            if option is None:return outputs
            outputs.append(option)

    def test_same_ordered_options_and_first_gate_representatives_as_legacy(self):
        owner,item=self.make();cursor={}
        self.assertEqual(self.consume(owner,item,cursor),self.fixture['expected'])
        self.assertEqual(owner.state['duplicateVariants'],self.fixture['expectedFinalDuplicateVariants'])
        self.assertEqual(cursor['frontier'],[])

    def test_legacy_json_cursor_resumes_without_repeating_completed_option(self):
        owner,item=self.make();saved=self.fixture['legacyCursor']
        cursor=copy.deepcopy(saved['cursor']);owner.state['duplicateVariants']=saved['duplicateVariants']
        self.assertEqual(self.consume(owner,item,cursor),self.fixture['expected'][saved['after']:])

    def test_interrupted_key_preparation_does_not_consume_a_cursor_entry(self):
        owner,item=self.make()
        cursor=dict(chain=0,frontier=[],started=False,seenOptions=[],seenNodes=[])
        before=copy.deepcopy(cursor);actual=owner.face_row;calls=0
        def interrupted(*args):
            nonlocal calls
            calls+=1
            if calls==3:raise TimeoutError('preparing keys')
            return actual(*args)
        with patch.object(owner,'face_row',side_effect=interrupted):
            with self.assertRaises(TimeoutError):owner.option(item,cursor)
        self.assertEqual(cursor,before)
        self.assertEqual(self.consume(owner,item,cursor),self.fixture['expected'])

    def test_interrupted_generation_restores_order_and_preserves_unknown_work(self):
        owner,item=self.make();cursor={};outputs=[];calls=0
        def check():
            nonlocal calls
            calls+=1
            if calls>120:raise TimeoutError('interruption')
        owner.check=check
        with self.assertRaises(TimeoutError):
            while True:
                value=owner.option(item,cursor)
                if value is None:break
                outputs.append(value)
        cursor=json.loads(json.dumps(cursor,allow_nan=False))
        restored,item=self.make()
        restored.state['duplicateVariants']=owner.state['duplicateVariants']
        outputs+=self.consume(restored,item,cursor)
        self.assertEqual(outputs,self.fixture['expected'])
        self.assertEqual(restored.state['duplicateVariants'],self.fixture['expectedFinalDuplicateVariants'])


if __name__=='__main__':unittest.main()
