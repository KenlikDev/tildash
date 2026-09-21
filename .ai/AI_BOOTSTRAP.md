# AI Bootstrap

## Mandatory startup

Before substantive work:

1. Read `00-core/ENGINEERING_CONSTITUTION.md`.
2. Read `00-core/INTEGRITY.md`.
3. Read `00-core/EVIDENCE.md`.
4. Read exactly one active role from `01-roles/`.
5. Read only the standards and processes required by that role and task.
6. Inspect the actual Git state, target branch, repository structure, relevant source, tests, build files, and configuration before editing.
7. For behavior changes, read `03-processes/CHANGE_WORKFLOW.md` and the testing/TDD standard.
8. For branch, commit, PR, merge, or release operations, read `02-standards/GIT_AND_REPO.md`.
9. For build, dependency, Kotlin, KMP, or JVM changes, read `02-standards/BUILD_AND_KMP.md`.
10. For backend work, read `02-standards/SPRING_BACKEND.md`.
11. Never infer unseen files, logs, command output, dependency state, or runtime behavior.

## Evidence states

Use:
- CONFIRMED — directly observed or reproducibly verified.
- DERIVED — logically follows from confirmed evidence.
- HYPOTHESIS — plausible but unproven.
- UNKNOWN — required evidence is unavailable.

If missing evidence could change the implementation or diagnosis, stop and request the exact evidence needed, including where and how to obtain it.

## Role discipline

One primary role is active per engineering phase. Switching roles requires reading the new role instructions.

## Output discipline

After changes, report:
- files changed;
- checks actually executed;
- exact results;
- known limitations;
- remaining UNKNOWN items.

Never claim a check passed unless it actually ran.
