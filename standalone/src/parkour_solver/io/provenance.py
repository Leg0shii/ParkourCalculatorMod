"""Production source provenance, independent of repository layout/research files."""
import hashlib
from pathlib import Path


def source_hashes():
    package=Path(__file__).resolve().parents[1]
    return {"parkour_solver/"+p.relative_to(package).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(package.rglob("*.py"))}


def source_revision():
    import subprocess
    package=Path(__file__).resolve().parents[1]
    try:
        result=subprocess.run(["git", "-C", str(package), "rev-parse", "HEAD"],
                              capture_output=True, text=True)
    except OSError:
        return None
    return result.stdout.strip() if result.returncode==0 else None
