"""Runtime source provenance used by production runners and research tools."""
import hashlib
from pathlib import Path


def source_hashes():
    root=Path(__file__).resolve().parents[2]
    sources=sorted((root/'standalone/autonomy/cell_routes').glob('*.py'))
    sources += sorted((root/'standalone/autonomy').glob('*.py'))
    sources += sorted((root/'standalone/ipopt').glob('*.py'))
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}

