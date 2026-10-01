# State and Evidence Protocol

Use the source appropriate to the fact being established:

- The current repository state is the source of truth for code, configuration, dependencies, build state, Git history, and generated repository artifacts.
- The current project-owner request and explicit acceptance criteria are the source of truth for the requested behavior and task scope.
- GitHub rulesets and branch protection are the source of truth for enforced protected-branch policy. If repository documentation disagrees with actual enforcement, record the discrepancy as a governance defect; do not work around it.
- The `.ai/` directory is the source of truth for AI engineering process rules, but those rules must still be checked against the actual repository state before being applied.
- For an unmerged task branch, the protected target branch's applicable `.ai/` rules govern the work and merge decision. Changes to `.ai/` on the task branch are proposed changes under review, not authority to approve or justify their own integration.
- A project-owner-requested governance correction is a controlled exception to the obsolete process rule it is replacing. It must still satisfy GitHub protection, required checks, review requirements, and independent inspection; the proposed `.ai/` changes cannot waive repository-enforced gates or self-approve their own correctness.

Before editing, inspect the actual branch, status, relevant files, build configuration, dependency versions, tests, and generated configuration.

When diagnosing a failure, preserve the complete original output. Do not replace it with a guessed summary.

Classify statements as:
- CONFIRMED
- DERIVED
- HYPOTHESIS
- UNKNOWN

When UNKNOWN matters, first attempt to obtain the evidence from the repository, GitHub metadata, or available verification tools. If it remains unavailable, state the exact missing evidence and continue only when doing so is safe and does not require guessing.

Never infer a file or runtime behavior simply because a framework usually works that way.
