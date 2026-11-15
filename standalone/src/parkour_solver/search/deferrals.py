"""Peer-index ownership and decision orchestration; no comparison arithmetic."""
import json

from parkour_solver.search.comparison import compare_candidates, priority_key


def remember_peer(peers, label):
    key=json.dumps([label.get(k) for k in ('comparisonContext','mode','landingPhase','J','tickCost')])
    old=peers.get(key)
    chosen=label if old is None else min((old,label),key=priority_key)
    # Actual physical programs/variants stay in the owning frame records.
    peers[key]={k:v for k,v in chosen.items() if k!='schedule'}
    return peers[key]!=old


def assess(label, peers, context):
    decision=compare_candidates(label,context=context)
    if not decision['peerLookupAllowed']:
        return decision
    protected=None;checks=0
    for peer in peers.values():
        checks+=1
        checked=compare_candidates(label,peer,context=context)
        if checked['action']=='defer_preparation':
            return dict(checked,peerChecks=checks)
        if checked['action']=='keep_protected':
            protected=checked
    return dict(protected or decision,peerChecks=checks)
