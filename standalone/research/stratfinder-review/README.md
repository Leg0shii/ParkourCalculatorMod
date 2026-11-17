# Stratfinder source-review probe

2026-10-26. This is a small executed check of Curryocity's external input cracker, supporting [the orchestration investigation](../../STRATFINDER_INVESTIGATION.md). It is not a block-route solver, native-suite benchmark or game-physics certification.

`summary.json` records source identities, settings and counters. `probe-result.txt` contains the observed output. Both risky-prune modes returned the same four solutions on this one input, with very different search counts. The source explicitly permits risky pruning to miss inertia-dependent solutions; this run does not establish completeness of either mode.

To reproduce from the worktree root, clone the public source into the ignored build directory and select the inspected revision:

```sh
gh repo clone Curryocity/Stratfinder build/stratfinder-source -- --filter=blob:none
git -C build/stratfinder-source checkout 988695b9b23245ded9f8ffb07c7ffd984091225d
clang++ -std=c++20 -O2 -ffp-contract=off \
  -Ibuild/stratfinder-source/src \
  standalone/autonomy/experiments/stratfinder-review/probe.cpp \
  build/stratfinder-source/src/inputCracker.cpp \
  build/stratfinder-source/src/player.cpp \
  build/stratfinder-source/src/zEngine.cpp \
  build/stratfinder-source/src/segLerp.cpp \
  -o build/stratfinder-probe
./build/stratfinder-probe
```

Skip the clone if that directory already contains the repository. Timings depend on the compiler and machine. The probe uses the public API and the narrow example parameters in the external repository's `src/test.cpp`; it does not modify or vendor that project's implementation.
