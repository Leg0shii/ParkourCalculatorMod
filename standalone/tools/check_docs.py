"""Check local links in maintained Markdown; frozen bundles retain source-era links."""
import json
import re
from pathlib import Path
from urllib.parse import unquote

from tools.docs_index import files
from tools.paths import STANDALONE_ROOT


def verify():
    manifest = json.loads((STANDALONE_ROOT / 'tools/layout-migration.json').read_text())
    frozen = {row['new'] for row in manifest['files'] if row['immutable']}
    errors, checked, documents = [], 0, 0
    for path in files():
        if path.suffix != '.md' or path.as_posix() in frozen:
            continue
        documents += 1
        code = False
        for line_number, line in enumerate((STANDALONE_ROOT / path).read_text().splitlines(), 1):
            if line.strip().startswith('```'):
                code = not code
            if code:
                continue
            for raw in re.findall(r'!?\[[^\]]*\]\(([^\s)]+)\)', line):
                if re.match(r'^[A-Za-z][\w+.-]*:', raw) or raw.startswith('#'):
                    continue
                target = unquote(raw.split('#')[0]).strip('<>')
                checked += 1
                if not (STANDALONE_ROOT / path.parent / target).exists():
                    errors.append(dict(file=path.as_posix(), line=line_number, target=target))
    return dict(documents=documents, localLinks=checked, errors=errors, passed=not errors)


def main():
    result = verify()
    print(json.dumps(result, indent=2))
    return int(not result['passed'])


if __name__ == '__main__':
    raise SystemExit(main())
