import json, os, math
from math import comb, log10

DATA = os.path.join(os.path.dirname(__file__), '..', 'copt', 'data')

def edges_needed(mm_pattern):
    e=0
    for i in range(1,len(mm_pattern)):
        if mm_pattern[i]!=mm_pattern[i-1]: e+=1
    return e

def analyze(fname, E_edges):
    d=json.load(open(os.path.join(DATA,fname)))
    n=d['numTicks']
    print("== %s  n=%d =="%(d['capture'],n))
    # 1. per-tick combo raw 9^n vs edge-structured (<=E edges over 8 nonzero combos + NONE)
    raw_combo = n*log10(9)
    # schedules with at most E edges: choose edge positions and a combo per segment
    seg = E_edges+1
    edge_combo = log10(comb(max(n-1,1), min(E_edges,n-1))) + seg*log10(9)
    # 2. sprint raw 2^n vs monotone single-engage (n+1)
    raw_sprint = n*log10(2)
    mono_sprint = log10(n+1)
    # 3. 45-deg relabel quotient /4 (log10 4)
    relabel = log10(4)
    # 4. wrap legality: general per-tick facing mod 360 with |gf|<=720 -> ~4 wrap sheets/tick vs unbounded
    #    (only meaningful per turn tick; illustrate as bounded multiplicity)
    print("  combo: raw 9^n = 1e%.1f ; <=%d-edge = 1e%.1f  (reduction 1e%.1f)"%(raw_combo,E_edges,edge_combo,raw_combo-edge_combo))
    print("  sprint: raw 2^n = 1e%.1f ; monotone single-engage = 1e%.1f (reduction 1e%.1f)"%(raw_sprint,mono_sprint,raw_sprint-mono_sprint))
    print("  45-deg relabel quotient: /4 = 1e%.1f"%relabel)
    tot_raw = raw_combo+raw_sprint
    tot_q = edge_combo+mono_sprint+relabel
    print("  TOTAL discrete: raw 1e%.1f -> quotiented 1e%.1f  (reduction 1e%.1f)"%(tot_raw,tot_q,tot_raw-tot_q))
    print()

analyze('struct-hpk-j1150-2x2bm_Nix_Neo.json', 3)   # human j1150 = 3 combo edges
analyze('struct-j154.json', 6)                        # human j154 = 6 combo edges
