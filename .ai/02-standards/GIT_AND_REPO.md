# Git and Repository Standard

## Branch model

```
main
  <- release PRs
develop
  <- feature/fix/refactor/test/security/docs/chore PRs
short-lived task branches
```

Never develop directly on `main` or `develop`.

Recommended names:
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

`main` and `develop` should require pull requests, required CI checks, resolved review findings, and no force pushes or deletion.

Never bypass repository protection.

## Pull requests

A PR must explain:
- problem/requirement;
- scope;
- behavior;
- verification evidence;
- limitations or follow-up work.

AI review is not a fictional second human reviewer. Final integration remains a human decision while the project has only one human maintainer.

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
