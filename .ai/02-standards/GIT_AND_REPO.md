# Git and Repository Standard

## Branch model

```
main
  <- release PRs

develop
  <- PRs from `ai/integration` only

ai/integration
  <- PRs from short-lived task branches

short-lived task branches
```

Never develop directly on `main` or `develop`.

`ai/integration` is a permanent integration branch used to combine verified task branches before the final human-controlled merge into `develop`.

Canonical flow:

```
task branch -> ai/integration -> develop
```

Never merge a task branch directly into `develop`.

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

`main`, `develop`, and `ai/integration` are integration boundaries and must be protected.

Required repository policy for `ai/integration`:
- pull requests are required;
- direct updates are not allowed;
- force pushes are not allowed;
- branch deletion is not allowed;
- non-fast-forward updates are not allowed;
- required CI checks must pass before merge;
- the branch must be up to date with its base before merge;
- unresolved review threads must block merge;
- merge method is squash unless preserving topology is materially necessary and explicitly justified;
- repository protection must not be bypassed.

Required repository policy for `develop`:
- pull requests are required;
- only `ai/integration` is an allowed source under the project workflow;
- direct updates are not allowed;
- force pushes are not allowed;
- branch deletion is not allowed;
- required CI checks must pass;
- the final `ai/integration` -> `develop` merge is human-controlled;
- repository protection must not be bypassed.

GitHub rulesets are the enforcement mechanism. The documented workflow is not a substitute for repository protection.

## Integration rules

A task branch may enter `ai/integration` only when:
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

For task integration, the normal target is `ai/integration`.

For final development integration, the normal source is `ai/integration` and the target is `develop`.

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

Never delete `ai/integration` as part of routine cleanup.


## Integration branch synchronization

After a squash-based promotion from `ai/integration` into `develop`, the protected branches may have different commit ancestry even when their file content is aligned at the promotion point.

Before the next final `ai/integration -> develop` promotion, synchronize `ai/integration` with the current `develop` ancestry through a dedicated short-lived task branch and pull request.

The synchronization PR must:
- preserve the current `ai/integration` tree;
- add the current `develop` tip as an ancestor;
- use a normal merge commit when preserving topology requires it;
- contain no unrelated source changes;
- pass the normal integration verification after merge.

The `ai/integration` ruleset must permit the topology-preserving merge method needed for this synchronization. Ordinary task PRs should continue to use squash merges.