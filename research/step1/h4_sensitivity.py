import json, os, math

DATA = r'C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/research/copt/data'
F = 0.91

def load(name):
    return json.load(open(os.path.join(DATA, name)))

def vel_weight(nrem):
    # sensitivity of terminal position to a velocity carried into a segment of nrem downstream ticks:
    # contribution = sum_{j=1}^{nrem} f^j = f*(1-f^nrem)/(1-f)
    return F * (1 - F ** nrem) / (1 - F)

def move_weight(nrem):
    # weight of a MOVE at a tick with nrem ticks remaining incl. itself: (1-f^(nrem+1))/(1-f)
    return (1 - F ** (nrem + 1)) / (1 - F)

CAPS = ['struct-j1150-inputs-gone.json', 'struct-j008b-2jump.json',
        'struct-loopmm-3jump-lands.json', 'struct-j345_3jmm_True_Nix_Neo.json',
        'struct-j155-4jmm_3bcmm_4.9375b.json', 'struct-hpk-j1150-2x2bm_Nix_Neo.json']

print('friction f =', F, '  saturating vel-weight f/(1-f) =', round(F/(1-F), 4))
print()
for cap in CAPS:
    try:
        d = load(cap)
    except Exception as e:
        print(cap, 'MISSING', e); continue
    n = d['numTicks']
    jumps = [t['t'] for t in d['ticks'] if t.get('jump')]
    contacts = [t['t'] for t in d['ticks'] if t.get('contact')]
    print('===', cap, 'n=', n, 'objAxis', d['objAxisName'], d['objSense'])
    print('  jumps', jumps, 'contacts', contacts)
    # ground-touch split candidates = contact ticks that are not the very first
    splits = [c for c in contacts if 0 < c < n - 1]
    for s in splits:
        nrem = n - s
        Cv = vel_weight(nrem)
        # grid step to hold terminal pos to 1e-4 (per axis) via this interface:
        step_1e4 = 1e-4 / Cv if Cv > 0 else float('inf')
        # cross-segment coupling: influence of THIS split's velocity on a wall/objective
        # tick that is g ticks further decays as f^g relative to immediate
        print('  split@t=%2d nrem=%3d velWeight C=%.4f  interface step for 1e-4 end-pos = %.2e b/axis'
              % (s, nrem, Cv, step_1e4))
    print()

# cross-segment sensitivity decay table (Shin-Zavala): perturb velocity at split, effect g ticks later
print('cross-tick coupling decay f^g (licenses receding-horizon window split):')
for g in [1, 5, 10, 13, 20, 25, 30, 40]:
    print('  g=%2d ticks apart: relative coupling f^g = %.4e' % (g, F ** g))

# interface enumeration cost: how many grid cells for a plausible velocity window
print('\ninterface grid cell count for a 2D velocity window at step s over range R:')
for R in [0.2, 0.35, 0.6]:
    for step in [1e-5, 1e-4, 1e-3, 5e-3]:
        cells = (2 * R / step) ** 2
        print('  window +-%.2f b/tick, step %.0e -> %.3e cells' % (R, step, cells))
