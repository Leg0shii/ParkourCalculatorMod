#!/bin/zsh
# Real11 acceptance matrix: serial paired runs, alternating arm order per repetition.
# Usage: matrix.sh <disk-revision> <reps> <fixture:seconds>...   (smoke: matrix.sh <rev> 1 unrestricted:20)
set -u
HERE=/Users/arvindmurty/CS/pkc-wt/real11-ipopt-solver-study-results/disk-integration
REV=$1; REPS=$2; shift 2
typeset -A ROOT
ROOT[disk]=/Users/arvindmurty/CS/pkc-wt/disk-interim-solver
ROOT[stock]=/Users/arvindmurty/CS/pkc-wt/disk-stock-c9deef4
LOG=$HERE/acceptance/matrix-$REV.jsonl
for k in $(seq 1 $REPS); do
  for spec in "$@"; do
    fixture=${spec%%:*}; seconds=${spec##*:}
    if (( k % 2 )); then arms=(disk stock); else arms=(stock disk); fi
    for arm in $arms; do
      root=${ROOT[$arm]}
      out=$HERE/runs/production-$REV-$fixture-${seconds}s-r$k-$arm
      load0=$(sysctl -n vm.loadavg)
      start=$(python3 -c 'import time;print(time.time())')
      (cd $root/standalone && env -u PKC_RUN_TICKS_COMPILER PKC_IPOPT_PYTHON=$root/.venv-guidance/bin/python \
        OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 /usr/bin/time -l $root/.venv-guidance/bin/python -B -m tools.profiling.run \
        --input $HERE/acceptance/$fixture.json --output $out --seconds $seconds --profile-mode bookkeeping \
        > $out.log 2>&1)
      code=$?
      end=$(python3 -c 'import time;print(time.time())')
      load1=$(sysctl -n vm.loadavg)
      echo "{\"rep\":$k,\"fixture\":\"$fixture\",\"seconds\":$seconds,\"arm\":\"$arm\",\"exit\":$code,\"wall\":$(python3 -c "print($end-$start)"),\"loadBefore\":\"$load0\",\"loadAfter\":\"$load1\",\"output\":\"$out\"}" >> $LOG
      echo "done rep=$k $fixture $arm exit=$code"
    done
  done
done
