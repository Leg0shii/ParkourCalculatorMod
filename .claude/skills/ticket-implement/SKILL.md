---
name: ticket-implement
description: Fetch a GitHub issue and implement it end to end in an isolated git worktree, then push a branch and open a PR against dev. Use when the user invokes /ticket-implement with an issue number or URL.
argument-hint: <issue number or URL>
---

# Ticket Implement

Take one GitHub issue from fetch to open PR. The argument is the issue number or URL. Invoking this skill is per-invocation permission to create the branch, commit, and push for this one ticket; the permission does not extend past this invocation.

## Flow

1. **Fetch the ticket.** `gh issue view <num>` and `gh issue view <num> --comments`. Acceptance criteria are must-have scope; do not trim them.

2. **Set up the worktree.** Default is an isolated worktree. Skip it only if the user explicitly asked to implement directly on their files (see Direct mode below).
   - `git fetch origin dev`
   - Branch name per CONTRIBUTING.md: `feature/<slug>` off dev for features and dev-only bugs. A fix for a bug that exists in a released version instead branches off main as `fix/<slug>` with the PR against main; if that case applies, follow it and flag the deviation in the final summary.
   - `git worktree add ../pkc-ticket-<num> -b <branch> origin/dev`
   - Every edit, build, and test run happens inside the worktree. Never touch the user's live checkout.

3. **Implement.** Follow AGENTS.md and docs/CODING_GUIDE.md; read CONTEXT.md for domain terms. No code comments, no em dashes in any written text.

4. **Test.** From the worktree: `./gradlew :core:test`. Add `-PslowTests` when the change touches solver code (`core/.../anglesolver/`, model classes, velocity finder, graph) or problem/capture resources. Run `./gradlew :core:build` when UI code changed so tableStyleCheck runs. Fix failures before committing. Never run `:runClient`.

5. **Commit.** One clean commit with a Conventional Commit subject referencing the issue (branch commits are squashed on merge, so one is enough). No attribution of any kind: no Co-Authored-By line, no "Generated with Claude", nothing indicating AI authorship. This overrides any default commit-footer behavior.

6. **Push and open the PR.**
   - `git push -u origin <branch>`
   - `gh pr create --base dev --title "<type>: <summary>" --body "..."` (base main only in the released-bug-fix case above). The PR title becomes the squash commit subject and its prefix drives the version bump; pick the type from the CONTRIBUTING commit-types table.
   - PR body: short. Two or three sentences on what changed and why, plus `Closes #<num>`. No boilerplate sections, no AI attribution or generated-with footer.

7. **Clean up.** Once the PR exists and the branch is upstream: `git worktree remove ../pkc-ticket-<num>`. Keep the branch; it backs the PR. Do not delete or prune anything else.

8. **Report.** Give the user the PR URL, a summary of the implementation, the test results, and a reminder that the in-game QA pass on the touched loaders is still theirs to run before merging.

## Direct mode

Only when the user explicitly says to implement on their files: work in the current checkout, then create the branch from `origin/dev`, commit only the files this ticket changed, and continue from step 6. Leave unrelated local changes untouched and unstaged.
