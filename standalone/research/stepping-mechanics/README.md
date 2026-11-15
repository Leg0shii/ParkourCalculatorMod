# Legacy stepping investigation

Planning evidence, 2026-10-29. The current Forge simulator delegates collision movement to Minecraft. The investigation compared the cached mapped Minecraft/Forge 1.8.9 Entity step branch with `core/.../replay/StaticScene.java`, the scalar `standalone/ipopt/ladder.py` step helper and orchestration's existing profiles.

The local cached source artifact was `forgeBin-1.8.9-11.15.1.1722.jar`, SHA-256 `92d94f1392f1c2ecc10d89f47f2584127b66d129cb3da76a4cb94a0c2fa04802`. Entity/EntityLivingBase bytecode was inspected and decompiled with the locally cached Vineflower 1.11.0. No Minecraft source is copied here. The mapped Entity movement method is named `move`; it is the legacy `moveEntity` collision procedure. The step branch matches the shared resolver's eligibility, original-box reset, A/B paths and progress/tie selection.

`StepMechanicsProbe.java` is a caller of the existing shared resolver. `probe-results.txt` contains its output for normal step, slab boost, blip, blip-up, grind, corner jump cancel, corner blip-up a return-leg equality boundary example, and mirrored Z-facing controls showing normal jumping/lower landing. The two corner names follow the user's 2026-10-30 corrections. They are X-facing cases with Z-blockage-style avoidance timing; Z-facing counterparts require ordinary movement.

The former partial-rise cancellation probe was removed from the active catalogue. Arbitrary collision boxes and prescribed displacements alone do not establish legal block geometry or a reachable route in the supported movement family. In particular, these probes must not be used to justify straight traversal through a minimum two-pixel obstacle: including the player width that requires about 0.725 displacement in one input. The current corner examples escape diagonally.

These are collision probes, not fresh full-route IPOPT or game replay witnesses. The user wants explicit case schedules and constraints, not reproduction of the internal resolver. See STEPPING_CASES.md for the authoritative taxonomy and STEPPING_PLAN.md for the implementation plan. No production movement or acceptance code changed during this investigation.

From the isolated worktree, after the existing compiler config/classes are available:

```sh
python3 - <<'PY'
import json
from pathlib import Path
import subprocess
import tempfile

command = json.loads(Path('standalone/autonomy/build/compiler.json').read_text())['command']
classpath = command[command.index('-cp') + 1]
source = Path('standalone/autonomy/experiments/stepping-mechanics/StepMechanicsProbe.java')
with tempfile.TemporaryDirectory(prefix='stepping-probe-') as output:
    subprocess.run([str(Path(command[0]).with_name('javac')), '-cp', classpath, '-d', output, str(source)], check=True)
    subprocess.run([command[0], '-cp', output + ':' + classpath, 'StepMechanicsProbe'], check=True)
PY
```
