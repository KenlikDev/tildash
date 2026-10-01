# Git and Repository Standard

## Branch model

```
main
  <- release PRs from `develop`

develop
  <- PRs from short-lived task branches

short-lived task branches
```

`main` and `develop` are the project's two protected long-lived branches.

Never develop directly on `main` or `develop`.

Canonical flow:

```
task branch -> develop -> main
```

Task work is reviewed and merged into `develop`. Promotion from `develop` to `main` is the release boundary. Do not create or use an AI-specific integration branch.

Recommended task branch names:
`feature/<description>`
`fix/<description>`
`refactor/<description>`
`test/<description>`
`security/<description>`
`docs/<description>`
`chore/<description>`
`hotfix/<description>`

## Protected branches

`main` and `develop` are the project's two protected long-lived branches.

Required repository policy for `develop`:
- pull requests are required;
- direct updates are not allowed;
- force pushes are not allowed;
- branch deletion is not allowed;
- required CI checks must pass;
- unresolved review threads must block merge;
- repository protection must not be bypassed.

Required repository policy for `main`:
- pull requests are required;
- direct updates are not allowed;
- force pushes are not allowed;
- branch deletion is not allowed;
- required CI checks must pass;
- unresolved review threads must block merge;
- release promotion is from `develop`;
- repository protection must not be bypassed.

GitHub rulesets are the enforcement mechanism. The documented workflow is not a substitute for repository protection.

## Task branch freshness

Before creating a task branch:
- inspect the current `develop` reference and confirm the branch is current;
- create the task branch from the current `develop` state unless the task explicitly requires another immutable baseline.

Before opening or merging a task PR:
- compare the task branch with the current `develop`;
- if `develop` has advanced, determine explicitly whether to refresh the task branch or preserve the documented immutable baseline;
- after any rebase, merge-from-`develop`, or other task-head change, re-run the applicable verification on the resulting head.

Never treat CI, review, or acceptance evidence from an older task-branch SHA as evidence for a newer SHA.

Do not rewrite a task branch history after review or verification unless necessary. If history is rewritten, treat the resulting branch as a new verification target and repeat affected review and checks.

## Integration rules

A task branch may enter `develop` only when:
1. the applicable project instructions have been read;
2. the task branch diff has been inspected;
3. acceptance criteria are satisfied;
4. focused checks pass;
5. broader applicable checks pass;
6. no unresolved merge conflict remains;
7. the change is within task scope;
8. the required checks and review evidence cover the exact task-branch head.

For security-sensitive changes, applicable security verification must also pass even when the corresponding security check is not currently a required protected-branch status check.

After integration, re-verify the resulting `develop` state before treating the change as part of a releasable state.

Do not merge code simply because:
- it compiles;
- another branch already contains similar changes;
- the PR is open or marked ready;
- the change is convenient to integrate;
- the branch has passed checks that do not cover the combined integration state.

## Pull requests

A PR must explain:
- problem/requirement;
- scope;
- behavior;
- verification evidence;
- limitations or follow-up work;
- source branch;
- target branch.

For task integration, the normal target is `develop`.

For release promotion, the normal source is `develop` and the target is `main`.

Final integration into protected branches remains human-controlled. AI may execute a protected-branch merge only when the project owner explicitly authorizes that exact operation; explicit authorization never permits bypassing repository protection or required checks.

## Commits

Use Conventional Commits:

```
<type>(<scope>): <imperative summary>
```

Use professional English, imperative mood, lowercase subject start, no trailing period, and preferably <=72 characters.

Types:
`feat`, `fix`, `test`, `refactor`, `perf`, `docs`, `build`, `ci`, `security`, `chore`, `revert`.

Examples:
- `chore: initialize Tildash project`
- `feat(learning): add review scheduling`
- `fix(auth): reject expired refresh tokens`
- `test(review): cover failed recall scheduling`

One coherent change per commit. Never use a commit to hide a failing check.

AI may prepare or execute Git operations only when explicitly authorized by the project owner. Destructive operations such as closing PRs, deleting branches, or rewriting branch history require that authorization as well.

## Branch cleanup

After a task branch has been successfully integrated and is no longer needed for review, recovery, follow-up work, or traceability, it may be deleted.

If a branch or PR belongs to a retired workflow, first preserve or migrate any still-required work into the current task flow, then close the obsolete PR and remove the obsolete branch/ruleset when repository permissions allow.

Never delete `main` or `develop` as part of routine cleanup.

## Release promotion

Release promotion is a separate operation, not an automatic step of every task.

When a release is requested:
- inspect the exact `develop` tree and compare it with `main`;
- verify all required checks on the exact release candidate;
- confirm local acceptance for the current `develop` state;
- inspect known open blocking findings relevant to the release candidate;
- open the release PR from `develop` to `main`;
- merge only after the required checks pass and the project owner gives the required approval/authorization.

Do not introduce an intermediate AI integration branch or an additional permanent branch for release promotion.
