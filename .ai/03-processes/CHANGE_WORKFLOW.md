# Change Workflow

1. Confirm the current branch and target branch.
2. Read the relevant role, standards, and process instructions.
3. Inspect the actual repository state.
4. Define or verify specification and acceptance criteria.
5. Write tests first for behavior where applicable.
6. Implement the smallest coherent change.
7. Run focused checks early.
8. Run broader applicable checks.
9. Inspect the final diff.
10. Run an independent review for non-trivial changes.
For a full repository review, additionally follow `03-processes/FULL_REPOSITORY_REVIEW.md` in full.
11. Commit one coherent change.
12. Push only the authorized task branch.
13. Open a PR from the task branch to `develop`.
14. Merge into `develop` only after all applicable checks pass.
15. Re-verify the resulting `develop` state after integration.
16. Remove obsolete task branches only when they are no longer needed.
17. Open the release PR from `develop` to `main`.
18. Merge into `main` only after required checks and explicit human approval.
19. Never merge a task branch directly into `main`.

## Integration discipline

Use this flow:

```
task branch -> develop -> main
```

A green task branch is evidence about that branch only. The integrated `develop` state must be verified separately before release promotion.

Before integrating into `develop`:
- inspect the branch diff against `develop`;
- verify task scope and acceptance criteria;
- verify applicable tests and checks;
- verify there are no unrelated changes;
- verify the target branch is the intended branch.

After integrating into `develop`:
- inspect the resulting `develop` state;
- run the applicable integration-level checks;
- preserve and report any failures;
- do not declare `develop` ready for `main` until the integrated result is verified.

For `main`:
- the release PR source is `develop`;
- the final merge remains human-controlled;
- required repository rules and checks must pass;
- never bypass branch protection.

## Branch cleanup

Delete a short-lived task branch only after its merge is confirmed and it is no longer needed for review, recovery, or follow-up work.

Never delete `main` or `develop` during routine cleanup.

Never delete a branch merely because the working tree is clean.
