import os, re, glob, sqlite3
from collections import Counter

HPK = r'C:/Users/benja/Desktop/jumps/hpk'
DB = r'C:/Users/benja/Desktop/jumps/cosmicnetwork/checkpoints.db'

NUM = re.compile(r'([a-z_]+):\s*(-?\d+\.?\d*)')
STR = re.compile(r"strategy:\s*'(.*)'", re.S)
STR2 = re.compile(r'strategy:\s*"(.*)"', re.S)

def read_fields(path):
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            return f.read()
    except Exception:
        return ''

def parse_loc(raw):
    d = {}
    for k, v in NUM.findall(raw):
        if k in ('x', 'y', 'z') and k not in d:
            d[k] = float(v)
    return d

def load_onejumps():
    out = []
    for p in glob.glob(os.path.join(HPK, 'onejump', '*.yml')):
        raw = read_fields(p)
        loc = parse_loc(raw)
        if 'x' not in loc:
            continue
        m = re.search(r"difficulty:\s*'?([^'\n]+)'?", raw)
        out.append({'id': os.path.splitext(os.path.basename(p))[0],
                    'diff': (m.group(1).strip() if m else ''),
                    'x': loc.get('x'), 'y': loc.get('y'), 'z': loc.get('z')})
    return out

def load_checkpoint_strats():
    strats = []
    for p in glob.glob(os.path.join(HPK, 'checkpoint', '*.yml')):
        raw = read_fields(p)
        if 'world: onejump' not in raw:
            continue
        m = STR.search(raw) or STR2.search(raw)
        if not m:
            continue
        s = m.group(1).strip()
        if not s:
            continue
        loc = parse_loc(raw)
        if 'x' not in loc:
            continue
        strats.append({'x': loc.get('x'), 'y': loc.get('y'), 'z': loc.get('z'), 'text': s})
    return strats

def load_db_strats():
    strats = []
    c = sqlite3.connect(DB)
    for r in c.execute("SELECT plate_x,plate_y,plate_z,dest_x,dest_y,dest_z,description FROM checkpoints WHERE dest_world='onejump'"):
        px, py, pz, dx, dy, dz, desc = r
        if not desc or not desc.strip():
            continue
        strats.append({'x': dx, 'y': dy, 'z': dz, 'text': desc.strip()})
    return strats

FAMILIES = ['jam', 'run', 'hold', 'walk', 'pessi', 'mark', 'fmm', 'bwmm', 'mm', 'c45']
FAMILY_PATTERNS = {
    'jam': [r'\bjam'],
    'run': [r'\brun\b', r'tick run', r'\d+\s*t(?:ick)? run'],
    'hold': [r'\bhold\b'],
    'walk': [r'\bwalk'],
    'pessi': [r'\bpessi'],
    'mark': [r'\bmark'],
    'fmm': [r'\bfmm\b'],
    'bwmm': [r'\bbwmm\b'],
    'mm': [r'\bmm\b', r'\bmomentum\b'],
    'c45': [r'\b45\b', r'45 strafe', r'double 45'],
}
MODIFIERS = ['wad', 'sidestep', 'noturn', 'turnin', 'turnout', 'wdwa', 'headhitter',
             'microturn', 'chinese', 'ja', 'neo', 'sneak', 'shift', 'smooth', 'rex']

def family_hits(text):
    t = text.lower()
    hits = []
    for fam in FAMILIES:
        for pat in FAMILY_PATTERNS[fam]:
            if re.search(pat, t):
                hits.append(fam)
                break
    return hits

STOP = set('the a an to of do and or in on at is it for you your with then after before '
           'later do a some this that then just make sure not early press release hold '
           'keep tap time timing new skill for around half block into onto out over up '
           'down go going get first second third st nd rd th key spacebar space sprint jump '
           'shift ms when as be by so if all one two three four five six seven eight nine ten '
           'do can will use using need needs about right left forward back turn strafe '.split())

KEY_RE = re.compile(r'^[wasd]{1,4}$')
TIME_RE = re.compile(r'^\d+(?:\.\d+)?t?$')
DOT_RE = re.compile(r'^[\.\-\+/,;:!()"\'\u00bb\u00ab]+$')

def full_parse(text):
    t = text.lower()
    for fam in FAMILIES:
        for pat in FAMILY_PATTERNS[fam]:
            t = re.sub(pat, ' ', t)
    for mo in MODIFIERS:
        t = t.replace(mo, ' ')
    toks = re.findall(r"[a-z]+\+?[a-z]*|\d+\.?\d*t?|[^\sa-z0-9]", t)
    unknown = []
    for tok in toks:
        tok = tok.strip('.,;:!?()"\'')
        if not tok:
            continue
        base = tok.replace('+', '')
        if KEY_RE.match(base):
            continue
        if TIME_RE.match(tok):
            continue
        if DOT_RE.match(tok):
            continue
        if tok in STOP:
            continue
        if tok.isdigit():
            continue
        unknown.append(tok)
    return unknown

def match_key(a, b, radius=8.0):
    if a['y'] is None or b['y'] is None:
        return False
    if abs(float(a['y']) - float(b['y'])) > 0.01:
        return False
    return abs(float(a['x']) - float(b['x'])) <= radius and abs(float(a['z']) - float(b['z'])) <= radius

def main():
    jumps = load_onejumps()
    cp = load_checkpoint_strats()
    db = load_db_strats()
    allstrats = cp + db
    print('onejump-list', len(jumps), 'checkpoint-strats(onejump)', len(cp), 'db-strats(onejump)', len(db), 'total strat texts', len(allstrats))

    # dedupe by normalized text
    seen = {}
    for s in allstrats:
        key = re.sub(r'\s+', ' ', s['text'].strip().lower())
        if key not in seen:
            seen[key] = s['text']
    uniq = list(seen.values())
    print('unique strat texts (deduped):', len(uniq))

    for label, texts in (('ALL-instances', [s['text'] for s in allstrats]), ('UNIQUE', uniq)):
        famhit = 0
        fullparse = 0
        famc = Counter()
        segdist = Counter()
        noparse = []
        for txt in texts:
            hits = family_hits(txt)
            if hits:
                famhit += 1
                for h in set(hits):
                    famc[h] += 1
                segdist[len(set(hits))] += 1
            unk = full_parse(txt)
            if len(unk) == 0:
                fullparse += 1
            elif len(noparse) < 30:
                noparse.append((txt[:90], unk[:6]))
        M = len(texts)
        print('\n===', label, 'N=', M)
        print(' family-hit (>=1 family kw):', famhit, '=', round(100.0*famhit/M, 1), '%')
        print(' FULL parse (no unknown content tok):', fullparse, '=', round(100.0*fullparse/M, 1), '%')
        print(' distinct-family-count per text:', dict(sorted(segdist.items())))
        print(' family histogram:', dict(famc.most_common()))
        if label == 'UNIQUE':
            print(' sample non-full-parse (unknown tokens):')
            for txt, unk in noparse[:22]:
                print('    ', repr(txt), '->', unk)

    # difficulty distribution of the onejump catalogue (for the escalation argument in H6)
    dc = Counter(j['diff'] for j in jumps)
    print('\nonejump difficulty histogram:', dict(dc.most_common()))

if __name__ == '__main__':
    main()
