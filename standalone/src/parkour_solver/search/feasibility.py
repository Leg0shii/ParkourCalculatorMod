"""Solve intent and exact-model outcomes; child failures never label ancestors."""

import copy

from parkour_solver.search.identity import digest
from parkour_solver.movement.program import program_search_key


def policy_spec(value=None, *, first_witness=False):
    defaults = dict(version=1, purpose='anchor' if first_witness else 'terminal',
                    stopOnFeasible=first_witness, rankEqualCost=not first_witness,
                    oracleRejection=False, numerical='ipopt')
    if value is not None:
        if set(value)-set(defaults):
            raise ValueError('Unknown solve policy field')
        defaults.update(value)
    if (defaults['version'] != 1 or defaults['purpose'] not in ('node', 'anchor', 'terminal')
            or defaults['numerical'] not in ('ipopt', 'relaxed')):
        raise ValueError('Unsupported solve policy')
    if any(type(defaults[k]) is not bool for k in ('stopOnFeasible', 'rankEqualCost', 'oracleRejection')):
        raise ValueError('Solve policy flags must be boolean')
    if defaults['purpose'] == 'terminal' and defaults['stopOnFeasible']:
        raise ValueError('Terminal optimization cannot stop at the first feasible seed')
    return defaults


def node_identity(program, context):
    # Repair cursors, warm yaws, observations and mutable queue state do not
    # identify a physical node. program_search_key retains scope/row ownership.
    physical = {k:v for k,v in program.items() if k != 'repairState'}
    return digest(dict(version=1, program=program_search_key(physical), context=context))


def outcome(result, model, policy):
    repair = result.get('repairState', {})
    complete = repair.get('completed', [])
    generation = repair.get('constraintGeneration')
    if result.get('best') is not None:
        status = 'feasible'
    elif result.get('status') == 'error':
        status = 'error'
    elif (policy['oracleRejection'] and complete and
          not repair.get('pending') and not repair.get('repairSources') and not repair.get('inFlight') and not repair.get('unresolved') and
          (repair.get('constraintGeneration') is None or repair['constraintGeneration'].get('exhausted')) and
          all(c.get('oracleRejected') or c.get('proven') for c in complete)):
        status = 'oracle_rejected'
    elif ((complete or generation is not None and generation.get('exhausted'))
          and not repair.get('pending') and not repair.get('repairSources') and not repair.get('inFlight') and not repair.get('unresolved')
          and (generation is None or generation.get('exhausted'))):
        # Exhausting repair/physical alternatives is a backtrack, not a
        # numerical infeasibility label for this node or its ancestors.
        # A fully screened-out generator can exhaust before emitting any solve.
        status = 'exhausted'
    else:
        status = 'suspended'
    return dict(version=1, status=status, model=model, policy=copy.deepcopy(policy),
                termination=result.get('termination'),
                rejectedAlternatives=[c['branch'] for c in complete if c.get('oracleRejected') or c.get('proven')])


def meaningful_choice(choice):
    p = choice['profile']
    return bool(any(p['jump']) or p.get('caseEvents') or any(
        e['axis'] != 'Y' or not p['grounded'][e['offset']]
        for e in p.get('contactEvents', [])))


def solve_due(program, interval, *, forced=False):
    return forced or program.get('decisionDepth',0)-program.get('lastSolvedDepth',0)>=interval


def unresolved_only(repair):
    """No runnable repair remains unless the caller explicitly retries unknowns."""
    return bool(repair.get('unresolved') and not repair.get('pending') and not repair.get('repairSources') and not repair.get('inFlight') and
                (repair.get('constraintGeneration') is None or repair['constraintGeneration'].get('exhausted')))


def restore_unknown_nodes(source, retry_unknown):
    if not retry_unknown or not source.get('deferredNodes'):
        return False
    source['pending'].extend(source['deferredNodes'])
    source['deferredNodes']=[]
    source['exhausted']=False
    return True
