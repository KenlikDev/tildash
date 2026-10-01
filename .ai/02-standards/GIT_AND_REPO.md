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
`release/<version-or-description>`

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

## Integration rules

A task branch may enter `develop` only when:
1. the applicable project instructions have been read;
2. the task branch diff has been inspected;
3. acceptance criteria are satisfied;
4. focused checks pass;
5. broader applicable checks pass;
6. no unresolved merge conflict remains;
7. the change is within task scope;
8. the resulting integration state is re-verified after merge.

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

AI review is not a fictional second human reviewer. Final integration into `develop` remains a human decision.

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

AI may prepare or execute Git operations only when explicitly authorized by the project owner.

## Branch cleanup

After a task branch has been successfully integrated and is no longer needed for review, recovery, follow-up work, or traceability, it may be deleted.

Never delete `main` or `develop` as part of routine cleanup.


## Release promotion

Before a `develop -> main` promotion:
- inspect the exact `develop` tree and compare it with `main`;
- verify all required checks on the exact release candidate;
- confirm local acceptance for the current `develop` state;
- open the release PR from `develop` to `main`.

Do not introduce an intermediate AI integration branch or an additional permanent branch for release promotion.
