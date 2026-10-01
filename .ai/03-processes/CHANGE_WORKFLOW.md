# Change Workflow

1. Confirm the current branch and target branch.
2. Re-read `.ai/AI_BOOTSTRAP.md` and `.ai/AI_INSTRUCTIONS.md` at the start of the workflow, then read the relevant role, standards, and process instructions.
3. Inspect the actual repository state.
4. Define or verify specification and acceptance criteria.
5. Write tests first for behavior where applicable.
6. Implement the smallest coherent change.
7. Run focused checks early.
8. Run broader applicable checks.
9. Re-read the applicable instruction files at the final-review checkpoint.
10. Inspect the final diff.
11. Run an independent review for non-trivial changes.
For a full repository review, additionally follow `03-processes/FULL_REPOSITORY_REVIEW.md` in full.
12. Commit one coherent change.
12. Push only the authorized task branch.
13. Open a PR from the task branch to `ai/integration`.
14. Merge into `ai/integration` only after all applicable checks pass.
15. Re-verify the resulting `ai/integration` state after integration.
16. Remove obsolete task branches only when they are no longer needed.
17. Open the final PR from `ai/integration` to `develop`.
18. Have the project owner synchronize the exact `ai/integration` PR head locally.
19. Have the project owner run the applicable local verification and manually exercise the affected product behavior and acceptance path.
20. Confirm that local acceptance is complete before considering the final PR mergeable for the owner.
21. Merge into `develop` only after required GitHub checks and explicit human local acceptance.
22. Never merge a task branch directly into `develop`.

## Final promotion gate

The final promotion sequence is:

```text
ai/integration green
  -> final promotion PR
  -> owner syncs exact PR head locally
  -> owner runs applicable local checks
  -> owner manually exercises affected product behavior
  -> owner explicitly confirms local acceptance
  -> owner merges PR into develop
```

The AI must never substitute its own repository inspection, GitHub CI, PR metadata, or prior local results for the owner's hands-on verification of the exact current tip. If `ai/integration` moves after local acceptance, the owner must repeat acceptance against the new tip before merging.

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
