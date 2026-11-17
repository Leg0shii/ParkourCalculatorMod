# Shared Codex and Claude Code setup

Use one `AGENTS.md` per scope. Every `CLAUDE.md` is a relative symlink to the
adjacent `AGENTS.md`; do not keep a second copy or an `@AGENTS.md` adapter.
The repository root and `standalone/` follow this convention. Relative links
continue to resolve in new Git worktrees.

## Global and project guidance

On this machine, the canonical personal rules are
`~/.config/agent-config/AGENTS.md`. Both `~/.codex/AGENTS.md` and
`~/.claude/AGENTS.md` link there; `~/.claude/CLAUDE.md` links to `AGENTS.md`.
The old Downloads bundle path links to the stable directory. The stale portable
Claude file is backed up there and is not an active instruction source.
Global files are machine-local; install their links separately on another
machine. Do not copy personal rules into the repository.

Start standalone sessions in the assigned worktree's `standalone/` directory.
The current Codex user setting `project_root_markers = []` treats that starting
directory as the project root for instruction discovery. It is an existing
machine setting, not a repository guarantee. Starting deeper can miss the
standalone entry point; starting at the repository root loads the mod guidance.

Claude also loads ancestor instruction files. The root `CLAUDE.md` symlink
therefore exposes the root `AGENTS.md` as well as the standalone guide. Symlinks
prevent divergent copies; they do not shorten a large ancestor file. The root
guide was not rewritten as part of this setup. See
[Codex discovery](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
and [Claude discovery](https://code.claude.com/docs/en/memory).

## Keep startup context small

- Keep durable scope and workflow rules in [the entry point](../AGENTS.md).
  Follow its documentation links according to the task; do not preload every
  architecture, historical plan, index or report.
- Keep current status in [current state](CURRENT_STATE.md), authority in
  [decisions](DECISIONS_AND_PLANS.md), and delayed work in
  [the deferred register](DEFERRED_WORK.md). A link is not authorization to start.
- Name the task's base commit, assigned branch/worktree, owned scope, acceptance
  checks and relevant handoff in the task prompt. Do not place each worker's
  assignment into global rules. Private session notes remain outside the repo;
  durable project contracts belong in the existing documentation.
- Use one environment and native build/config per worker. Do not reinstall a
  sibling checkout into another worker's editable environment. Follow
  [the runbook](RUNBOOK.md) for setup; keep fresh outputs in its own `runs/`.

## Check a new session

Before implementation, have the agent report its physical directory, branch,
instruction sources and assigned scope. Confirm it has the global rules and
standalone guide, and check for an unexpected `AGENTS.override.md` or alternate
`CODEX_HOME` / `CLAUDE_CONFIG_DIR` when guidance is missing.
In Claude, `/context all` lists loaded memory files on supported versions.
Do not use a second model session merely to check that filesystem links resolve.

This setup verified links and preserved settings, not a fresh Claude launch;
`claude` was not available on the setup shell's PATH. Installing the client,
authenticating and importing optional plugins/settings are separate tasks.
