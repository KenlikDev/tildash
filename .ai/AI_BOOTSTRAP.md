# AI Bootstrap

## Mandatory startup

Before substantive work:

1. Read `00-core/ENGINEERING_CONSTITUTION.md`.
2. Read `00-core/INTEGRITY.md`.
3. Read `00-core/EVIDENCE.md`.
4. Select exactly one primary role from `01-roles/` for the current engineering phase.
5. For code or developer-documentation changes, read `02-standards/CODE_AND_DOCS.md`.
6. Read only the standards and processes required by that role and task.
7. Inspect the actual Git state, target branch, repository structure, relevant source, tests, build files, and configuration before editing.
8. For behavior changes, read `03-processes/CHANGE_WORKFLOW.md` and the testing/TDD standard.
9. For full repository reviews, read `03-processes/FULL_REPOSITORY_REVIEW.md` before inspecting findings or making review conclusions.
10. For branch, commit, PR, merge, or release operations, read `02-standards/GIT_AND_REPO.md`.
11. For build, dependency, Kotlin, KMP, or JVM changes, read `02-standards/BUILD_AND_KMP.md`.
12. For backend work, read `02-standards/SPRING_BACKEND.md`.
13. Never infer unseen files, logs, command output, dependency state, or runtime behavior.

For branch, commit, PR, merge, or release operations:
- treat `ai/integration` as the permanent AI integration branch;
- never merge task branches directly into `develop`;
- use the canonical path `task branch -> ai/integration -> develop`;
- before declaring `ai/integration` ready for `develop`, verify the integrated result rather than relying only on checks previously run on the task branch;
- before the human-controlled `ai/integration -> develop` merge, require explicit local acceptance by the project owner on the owner's own computer;
- never treat GitHub CI, PR mergeability, or prior AI verification as a substitute for that local acceptance;
- never tell the project owner to merge the final promotion PR until local acceptance has been explicitly confirmed;
- always provide concrete local synchronization, verification, and manual UI/behavior acceptance steps for the final promotion;
- never bypass the repository rules protecting `ai/integration` or `develop`.

## Instruction reread checkpoints

Re-read `.ai/AI_BOOTSTRAP.md` and `.ai/AI_INSTRUCTIONS.md` at these checkpoints:

1. after context compaction, reconnect, or a long interruption;
2. when switching engineering roles or technical domains;
3. before the first repository mutation in a task;
4. before any commit, branch, pull request, merge, or release operation;
5. before final diff review and final verification;
6. whenever the task expands beyond its original acceptance criteria.

Re-read the relevant role, standard, and process file immediately before work in that domain when the task has crossed a checkpoint.

These rereads are mandatory even when the same files were read earlier in the same conversation. They prevent stale working context from silently overriding repository instructions.

## Evidence states

Use:
- CONFIRMED — directly observed or reproducibly verified.
- DERIVED — logically follows from confirmed evidence.
- HYPOTHESIS — plausible but unproven.
- UNKNOWN — required evidence is unavailable.

If missing evidence could change the implementation or diagnosis, stop and request the exact evidence needed, including where and how to obtain it.

## Role discipline

One primary role is active per engineering phase.

Select the role from the current phase:
- architecture or boundary decisions -> `ARCHITECT.md`;
- diagnosis and reproduction -> `DEBUGGER.md`;
- implementation and behavior changes -> `IMPLEMENTER.md`;
- code or repository review -> `REVIEWER.md`;
- security threat-model or security review -> `SECURITY_REVIEWER.md`;
- release and controlled integration -> `RELEASE_ENGINEER.md`.

Switching roles requires rereading the new role instructions and treating the new role as the active role. Reading another role file as part of an audit does not make it active.

## Output discipline

After changes, report:
- files changed;
- checks actually executed;
- exact results;
- known limitations;
- remaining UNKNOWN items.

Never claim a check passed unless it actually ran.
