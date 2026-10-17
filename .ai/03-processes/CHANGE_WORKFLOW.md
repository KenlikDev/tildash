# Change Workflow

1. Confirm the current branch and target branch.
2. Re-read `.ai/AI_BOOTSTRAP.md` and `.ai/AI_INSTRUCTIONS.md` at the start of the workflow, then read the relevant role, standards, and process instructions.
3. Inspect the actual repository state.
4. Define or verify specification and acceptance criteria.
5. Write tests first for behavior where applicable.
6. Implement the smallest coherent change.
7. Run focused checks early.
8. Run broader applicable checks.
9. For user-visible changes, launch the affected application and exercise the relevant UI journey when the available environment permits; use automated UI tests as complementary evidence.
10. Re-read the applicable instruction files at the final-review checkpoint.
11. Inspect the final diff.
12. Switch the active role to `REVIEWER` and reread its role instructions before running the independent review.
13. Run an independent review for non-trivial changes.
For a full repository review, additionally follow `03-processes/FULL_REPOSITORY_REVIEW.md` in full.
14. Commit one coherent change.
15. Push only the authorized task branch.
16. Open a PR from the task branch to `ai/integration`.
17. Merge into `ai/integration` only after all applicable checks pass.
18. Re-verify the resulting `ai/integration` state after integration.
19. Remove obsolete task branches only when they are no longer needed.
20. Open the final PR from `ai/integration` to `develop`.
21. Have the project owner synchronize the exact `ai/integration` PR head locally.
22. Have the project owner run the applicable local verification and manually exercise the affected product behavior and acceptance path.
23. Confirm that local acceptance is complete before considering the final PR mergeable for the owner.
24. Merge into `develop` only after required GitHub checks and explicit human local acceptance.
25. Never merge a task branch directly into `develop`.

## Final promotion gate

The final promotion sequence is:

```
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

## CI iteration discipline

Group multiple known corrections into one coherent patch whenever practical. Do not create a commit solely to retrigger or refresh CI. Once a relevant check is running, avoid changing the branch unless new evidence identifies a concrete defect or missing verification; this prevents unnecessary CI cancellation and preserves useful evidence from the most recent complete run.
