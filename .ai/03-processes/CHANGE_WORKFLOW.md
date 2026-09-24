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
11. Commit one coherent change.
12. Push only the authorized task branch.
13. Open a PR from the task branch to `ai/integration`.
14. Merge into `ai/integration` only after all applicable checks pass.
15. Re-verify the resulting `ai/integration` state after integration.
16. Remove obsolete task branches only when they are no longer needed.
17. Open the final PR from `ai/integration` to `develop`.
18. Merge into `develop` only after required checks and human approval.
19. Never merge a task branch directly into `develop`.

## Integration discipline

The permanent AI integration branch is `ai/integration`.

Use this flow:

```
task branch -> ai/integration -> develop
```

A green task branch is evidence about that branch only. Integration must be treated as a separate verification point.

Before integrating into `ai/integration`:
- inspect the branch diff against the current integration base;
- verify task scope and acceptance criteria;
- verify applicable tests and checks;
- verify there are no unrelated changes;
- verify the target branch is the intended branch.

After integrating into `ai/integration`:
- inspect the resulting diff/state;
- run the applicable integration-level checks;
- preserve and report any failures;
- do not declare the branch ready for `develop` until the integrated result is verified.

For `develop`:
- only the `ai/integration` branch is used for final integration under this project workflow;
- the final merge remains human-controlled;
- required repository rules and checks must pass;
- never bypass branch protection.

## Branch cleanup

Delete a short-lived task branch only after its merge is confirmed and it is no longer needed for review, recovery, or follow-up work.

Never delete `ai/integration` during routine cleanup.

Never delete a branch merely because the working tree is clean.
