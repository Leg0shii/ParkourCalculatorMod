import os
import subprocess
import textwrap
import unittest


RUNTIME = os.environ.get('PKC_IPOPT_PYTHON')


@unittest.skipUnless(RUNTIME, 'Set PKC_IPOPT_PYTHON for native bound-extraction checks')
class WorkerBoundTests(unittest.TestCase):
    def native(self, code):
        completed = subprocess.run([RUNTIME,'-c',textwrap.dedent(code)],capture_output=True,text=True,timeout=15)
        self.assertEqual(completed.returncode,0,completed.stdout+completed.stderr)

    def test_monitor_checks_expressions_instead_of_supplied_constraint_vector(self):
        self.native('''
            import casadi as ca
            from parkour_solver.numerics.worker import Monitor
            x = ca.SX.sym('x',2)
            constraints = ca.Function('original',[x],[ca.vertcat(x[0],x[0]**2+x[1])])
            decode = ca.Function('yaw',[x],[x[0]])
            seen = []
            monitor = Monitor(2,2,decode,constraints,[.2,.44],[.2,.44],False,lambda y:seen.append(y) or True)
            assert monitor.record(ca.DM([.2,.4]),ca.DM([999.,-999.]),-1.)
            assert not monitor.record(ca.DM([.3,.4]),ca.DM([.2,.44]),-2.)
            assert not monitor.record(ca.DM([float('nan'),.4]),ca.DM([.2,.44]),-2.)
            assert len(seen)==1 and monitor.best_yaw==[.2]
        ''')

    def test_constant_objective_tie_keeps_the_accepted_point(self):
        self.native('''
            import casadi as ca
            from parkour_solver.numerics.worker import Monitor
            x = ca.SX.sym('x')
            identity = ca.Function('identity',[x],[x])
            monitor = Monitor(1,1,identity,identity,[0.],[1.],True,lambda y:y[0]>.5)
            assert not monitor.record(ca.DM(.2),ca.DM(.2),0.)
            assert monitor.record(ca.DM(.8),ca.DM(.8),0.)
            assert monitor.best_yaw==[.8]
        ''')

    def fixed_phase(self, *, stop=False, final_only=False, pure=False):
        self.native('''
            import copy
            from unittest.mock import patch
            from parkour_solver.numerics import worker
            from parkour_solver.numerics.model import prepare,validate
            from tests.support.numerical_model import problem
            from parkour_solver.numerics.constraints import reduce_walls
            request = problem(2)
            request.update(velocity=[0.,0.],magnitude=[.1,.1],base=[0.,0.],yaw=[90.,90.])
            request['walls'] = [dict(mode='X',t1=2,cmp='GE',rhs=v) for v in (.08,.04,.08)]
            request['walls'] += [dict(mode='X',t1=2,cmp='LE',rhs=v) for v in (.18,.24)]
            request['walls'] += [dict(mode='F',t1=0,cmp='EQ',rhs=45.)]*2
            request['walls'] += [dict(mode='Z',t1=2,t2=0,op='MINUS',cmp='GE',rhs=-.3)]
            request['walls'] += [dict(mode='DXZ',t1=2,t2=0,cmp='GE',rhs=-.1)]*2
            request = prepare(request)
            request['stopOnFeasible']=STOP
            request['objectiveMode']='constant_zero' if PURE else 'configured'
            original = copy.deepcopy(request)
            assert not validate(request,request['yaw'])['feasible']
            candidates,phases = [],[]
            def accept(yaw):
                checked = validate(request,yaw)
                if checked['feasible']:candidates.append(checked)
                return checked['feasible']
            options = dict(stop=STOP,final_only=FINAL_ONLY)
            factory = worker.ca.nlpsol
            def inspect(name,plugin,nlp,settings):
                if PURE:
                    assert nlp['f'].is_constant() and float(nlp['f'])==0.
                else:
                    assert not nlp['f'].is_constant()
                return factory(name,plugin,nlp,settings)
            worker.ca.nlpsol = inspect
            with patch.object(worker,'send',side_effect=phases.append):
                if options['final_only']:
                    with patch.object(worker.Monitor,'eval',return_value=[0]):
                        yaw,status,cost = worker.fixed_phase(request,request['yaw'],[[2,2]]*2,3.,False,accept,
                            'carry','initial',[[0,0]]*2,0)
                else:
                    yaw,status,cost = worker.fixed_phase(request,request['yaw'],[[2,2]]*2,3.,options['stop'],accept,
                        'carry','initial',[[0,0]]*2,0)
            assert candidates and validate(request,yaw)['feasible'],(status,phases)
            if options['stop']:assert status=='User_Requested_Stop',status
            else:assert abs(validate(request,yaw)['objective']-.18)<1e-6,(yaw,status,cost)
            assert phases[0]['simpleBoundExtraction']
            assert phases[0]['objectiveMode']==request['objectiveMode']
            assert phases[0]['wallReduction']==reduce_walls(request['walls'])['audit']
            assert phases[0]['wallReduction']['retained']<len(request['walls'])
            assert request==original
        '''.replace('STOP',repr(stop)).replace('FINAL_ONLY',repr(final_only)).replace('PURE',repr(pure)))

    def test_extraction_with_mixed_rows_and_original_request_validation(self):
        self.fixed_phase()

    def test_extracted_final_point_is_preserved_without_iteration_candidates(self):
        self.fixed_phase(final_only=True)

    def test_feasibility_early_stop_preserves_validated_incumbent(self):
        self.fixed_phase(stop=True)

    def test_explicit_pure_feasibility_is_independent_of_early_stopping(self):
        self.fixed_phase(stop=True,pure=True)


if __name__ == '__main__':
    unittest.main()
