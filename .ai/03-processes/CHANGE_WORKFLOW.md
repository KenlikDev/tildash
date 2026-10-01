# Change Workflow

1. Confirm the current branch and target branch.
2. Read the relevant role, standards, and process instructions.
3. Inspect the actual repository state.
4. Confirm the task branch is based on the current `develop` state or explicitly record the required immutable baseline.
5. Define or verify specification and acceptance criteria.
6. Write tests first for behavior where applicable.
7. Implement the smallest coherent change.
8. Run focused checks early.
9. Run broader applicable checks.
10. Inspect the final diff.
11. Run an independent review for non-trivial changes. For a full repository review, additionally follow `03-processes/FULL_REPOSITORY_REVIEW.md` in full.
12. Commit one coherent change.
13. Push only the authorized task branch.
14. Before opening the PR, verify that CI/review evidence refers to the exact current task-branch HEAD.
15. Open a PR from the task branch to `develop`.
16. Merge into `develop` only after all applicable checks pass, review conditions are satisfied, and the project owner authorizes the merge when human approval is required.
17. Re-verify the resulting `develop` state after integration.
18. Remove obsolete task branches only when they are no longer needed.

The release flow below is a separate operation and is not performed automatically for every task.

## Integration discipline

Use this flow:

```
task branch -> develop -> main
```

A green task branch is evidence about that branch only. The integrated `develop` state must be verified separately before release promotion.

Before integrating into `develop`:
- inspect the branch diff against the current `develop`;
- verify task scope and acceptance criteria;
- verify applicable tests and checks on the exact task-branch HEAD;
- verify there are no unrelated changes;
- verify the target branch is the intended branch;
- verify no obsolete workflow target such as `ai/integration` is being used.

After integrating into `develop`:
- inspect the resulting `develop` state;
- run the applicable integration-level checks on the exact resulting `develop` commit;
- preserve and report any failures;
- do not declare `develop` ready for `main` until the integrated result is verified.

## Release promotion

When a release is explicitly requested:
- the release PR source is `develop`;
- verify the exact release candidate and all required checks;
- perform the documented release acceptance steps;
- the final merge remains human-controlled;
- never bypass branch protection.

## Branch cleanup

Delete a short-lived task branch only after its merge is confirmed and it is no longer needed for review, recovery, follow-up work, or traceability.

For a retired workflow, preserve required work before closing its PR or removing its branch.

Never delete `main` or `develop` during routine cleanup.

Never delete a branch merely because the working tree is clean.
