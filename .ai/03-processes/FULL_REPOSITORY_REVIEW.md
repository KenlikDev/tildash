# Full Repository Review Procedure

## Purpose

This procedure is mandatory for a full repository review.

A full review is an adversarial, first-contact audit of the repository as it exists at the selected Git reference. The reviewer must not rely on earlier reviews, issue descriptions, PR descriptions, previous green CI runs, or assumptions about framework behavior.

The goal is to establish whether the repository is internally coherent, secure, maintainable, testable, documented, and faithful to its declared architecture and requirements.

## When this procedure applies

Use this procedure when the task is described as:

- full repository review;
- complete audit;
- professional review of the whole project;
- review as if seeing the project for the first time;
- review of all code, documentation, build, CI, security, and architecture;
- audit after a major milestone when the complete repository state must be re-established.

For a narrow change review, use the normal task workflow instead. Do not silently downgrade a requested full review into a partial review.

## Source of truth

The actual repository state is the source of truth.

At the beginning of the review, record:

- repository owner/name;
- selected reference or branch;
- exact HEAD commit SHA;
- default branch;
- protected branch topology when available;
- repository visibility;
- current open and recently merged PRs relevant to the reviewed state;
- current issue state relevant to the reviewed milestone;
- latest CI and security workflow results for the reviewed HEAD.

Resolve a moving branch or tag to the exact HEAD SHA being reviewed and treat that SHA as the frozen review reference. If the branch moves later, the new HEAD is a new repository state and requires final verification before its results can be applied.

Never treat a PR description, issue description, README claim, or previous review as evidence that the current implementation satisfies a requirement.

## Evidence states

Every material finding or conclusion must be classified:

- CONFIRMED — directly observed in repository contents, GitHub metadata, workflow output, or reproducible verification;
- DERIVED — logically follows from confirmed evidence;
- HYPOTHESIS — plausible but not proven;
- UNKNOWN — required evidence is unavailable.

Never present an UNKNOWN as CONFIRMED.

When an UNKNOWN could change the finding, explicitly state what evidence is missing and how it can be obtained.

## Phase 0: Establish the repository map

Before reviewing individual files, build a complete inventory of the repository at the selected HEAD.

Inventory:

- all tracked text files;
- all tracked source files;
- all build files;
- all test files;
- all CI and automation files;
- all configuration files;
- all migrations and schema files;
- all scripts;
- all documentation;
- all ADRs;
- all native platform projects, including non-Gradle application directories such as iosApp;
- all generated or IDE-owned files that are committed;
- binary assets and their metadata when present;
- repository-level files such as LICENSE, SECURITY.md, CODEOWNERS, README, and ignore files.

Count and account for every tracked file using the repository Git tree/index, not inferred directory contents. Record the inventory method and total file count. Every file must end in one of these states:

- reviewed;
- intentionally excluded with a documented reason;
- unreadable or unavailable, with UNKNOWN status.

Do not assume a directory is irrelevant because it is not a Gradle module.

Do not add a non-Gradle application such as iosApp to Gradle settings merely because it exists in the repository.

## Phase 1: Read project instructions first

Read all mandatory project instructions required by the repository bootstrap and task.

For this repository, that includes when present:

- .ai/AI_BOOTSTRAP.md;
- .ai/AI_INSTRUCTIONS.md;
- .ai/AI_REVIEW_PROMPT.md for review tasks;
- the active role instructions;
- ENGINEERING_CONSTITUTION.md;
- EVIDENCE.md;
- INTEGRITY.md;
- GIT_AND_REPO.md;
- BUILD_AND_KMP.md;
- SPRING_BACKEND.md for backend review;
- TESTING_AND_TDD.md;
- CHANGE_WORKFLOW.md.

If a referenced instruction file does not exist at the selected HEAD, record that as an explicit repository finding or UNKNOWN according to the surrounding project policy. Do not silently fabricate the file's contents.

## Phase 2: Line-by-line source review

Every tracked text source file in scope must be read completely.

Review line-by-line, not merely by file summary.

For each source file, inspect:

- package/module ownership;
- imports and dependencies;
- nullability and type assumptions;
- public and internal APIs;
- invariants;
- state transitions;
- error handling;
- resource ownership;
- concurrency behavior;
- transaction boundaries;
- security assumptions;
- serialization/deserialization;
- logging;
- retries and timeouts;
- platform assumptions;
- performance-sensitive loops;
- hidden global state;
- side effects;
- dead code;
- duplicated logic;
- speculative abstractions;
- comments that disagree with code;
- TODO/FIXME markers and whether they represent real unfinished behavior;
- exception swallowing or unsafe fallbacks;
- test seams and observability.

For every public class, interface, function, endpoint, configuration property, persistence object, and cross-module contract, identify its consumers or prove that there are none.

Never stop after finding one defect in a file. Complete the file.

## Phase 3: Cross-file contract review

After line-by-line review, trace relationships across the repository.

At minimum trace:

- API DTOs -> controllers -> application services -> domain -> repositories/gateways;
- shared core models -> platform consumers;
- server models -> migrations and SQL schema;
- persistence repositories -> transactions and schema constraints;
- configuration properties -> environment variables -> deployment behavior;
- security configuration -> protected endpoints -> authorization checks -> identity model;
- validators -> workflows -> publication/review rules;
- tests -> behavior they actually assert;
- documentation -> implementation it claims to describe;
- ADR decisions -> current code and configuration;
- CI checks -> the risks they actually detect.

Check for:

- undocumented breaking changes;
- contracts that exist only in documentation;
- code paths with no tests where the project requires them;
- tests that do not exercise the claimed production path;
- domain logic leaked into transport or persistence;
- transport models serialized directly from domain/persistence objects;
- framework types leaking into core/domain contracts;
- configuration names that do not match code;
- schema columns or constraints with no corresponding model behavior;
- model fields with no persistence or API representation where required;
- migrations that cannot reproduce the claimed schema from an empty database.

## Phase 4: Build and dependency review

Inspect all build configuration and dependency management.

Review:

- settings.gradle.kts;
- root and module build.gradle.kts files;
- version catalogs;
- Gradle plugins;
- toolchains;
- JVM targets;
- Kotlin Multiplatform targets;
- Android configuration;
- iOS/native configuration;
- Desktop/JVM configuration;
- Web configuration;
- compiler flags;
- lint and formatting configuration;
- test configuration;
- Jacoco/coverage configuration;
- dependency versions;
- dependency scope;
- duplicate dependencies;
- dynamic versions;
- unnecessary libraries;
- transitive-risk dependencies;
- generated code or plugin behavior.

Verify that every dependency is justified by actual usage.

Do not remove or downgrade a dependency simply because it appears unused without verifying the full build graph.

Do not preserve obsolete wizard-generated code merely because it is harmless.

## Phase 5: Test and verification review

Inspect the complete test tree.

For every test suite, determine:

- what behavior it specifies;
- whether the assertion is strong enough;
- whether it tests production behavior or a mock-only approximation;
- whether important branches are untested;
- whether error paths are tested;
- whether concurrency is tested where needed;
- whether security boundaries are tested;
- whether migration behavior is tested from an empty database;
- whether KMP common behavior is tested across applicable targets;
- whether native-specific code is verified where the project claims support.

Verify that every acceptance-critical test source set is actually executed by its named CI or local task before treating it as coverage evidence. Look for:

- tests that pass without asserting meaningful behavior;
- overly broad mocks;
- assertions that merely check non-null or success status;
- test data that makes invalid states impossible to reach;
- flaky timing assumptions;
- test-only branches in production code;
- disabled tests;
- skipped tests;
- suppressions;
- retries masking failures;
- coverage thresholds that can be satisfied without critical paths.

A green suite is evidence only for the exact code and environment that produced it.

## Phase 6: CI/CD review

Inspect every workflow under .github/workflows and every referenced action/configuration.

Review:

- triggers;
- branch filters;
- permissions;
- secrets exposure;
- artifact handling;
- concurrency;
- cancellation behavior;
- matrix coverage;
- caching;
- environment assumptions;
- platform coverage;
- dependency review;
- CodeQL;
- lint;
- tests;
- coverage;
- build outputs;
- required checks;
- failure propagation.

Determine whether a workflow can report green while an important check was skipped, cancelled, or never executed. For aggregate Gradle commands, inspect task output or equivalent evidence to confirm that required target-specific suites actually ran.

Check whether push and pull-request events require different security behavior.

Verify that security scans are not disabled on protected branches.

## Phase 7: Security review

Review security as an independent threat-model pass.

Inspect:

- authentication;
- authorization;
- identity propagation;
- token validation;
- issuer/audience assumptions;
- secrets handling;
- TLS requirements;
- CORS;
- CSRF;
- session state;
- cookie settings;
- input validation;
- output encoding;
- SQL injection surfaces;
- command execution;
- path traversal;
- SSRF;
- unsafe deserialization;
- file uploads;
- logging of credentials or personal data;
- dependency vulnerabilities;
- security workflow permissions;
- CodeQL findings;
- secret scanning configuration;
- push protection configuration when available.

For every security control, identify the exact code or repository setting that enforces it.

Do not infer that a security feature is enabled merely because documentation says it is.

Do not dismiss a security finding by suppressing the scanner. First determine whether the underlying code or design should change.

## Phase 8: Database and migration review

Inspect all migrations and database-facing code.

Verify:

- migration ordering;
- repeatability on an empty database;
- primary keys;
- foreign keys;
- unique constraints;
- check constraints;
- nullability;
- indexes;
- timestamps;
- version history;
- append-only requirements;
- transaction boundaries;
- optimistic/concurrency behavior;
- dangerous destructive operations;
- schema names;
- environment configuration;
- test database strategy.

Every persistence invariant stated in documentation must have either a schema constraint, executable application invariant, or explicit documented reason why enforcement is deferred.

## Phase 9: Documentation and ADR review

Read all documentation, not only README.

Review:

- README;
- architecture documents;
- API documentation;
- security documentation;
- database documentation;
- content/model documentation;
- process documents;
- ADRs;
- contributor guidance;
- scripts and operational instructions;
- comments that define contracts.

Check each claim against implementation.

Flag:

- stale claims;
- contradictory documents;
- duplicate sources of truth;
- undocumented behavior;
- missing status markers;
- roadmap items presented as implemented;
- security claims without repository evidence;
- instructions that conflict with branch protection or workflow policy.

ADRs must explain a durable decision that is still reflected in the current code.

## Phase 10: Native and platform project review

Review each platform as a real product surface.

For Android, inspect Gradle target configuration and platform-specific code.

For iOS, inspect the native Xcode project and Swift/Objective-C code where present. Do not assume a non-Gradle iOS application is incomplete simply because it is absent from settings.gradle.kts.

For Desktop/JVM and Web, inspect target-specific code and packaging.

Verify platform wiring to shared/common contracts.

Look for:

- platform-specific forks of shared business rules;
- platform behavior that silently differs from common logic;
- missing resource handling;
- build-only code paths;
- native configuration drift;
- unsupported target claims.

## Phase 11: Repository hygiene and governance review

Inspect:

- .gitignore;
- license;
- CODEOWNERS if present;
- branch/ruleset configuration when available;
- required status checks;
- Dependabot configuration;
- security policies;
- repository metadata;
- accidental generated files;
- secrets or credentials committed to history or current tree;
- stale branches and stale PRs when relevant.

Check that branch topology matches the project workflow.

Do not modify branch protection to make a review or CI pass.

## Phase 12: History and regression review

Review relevant Git history, not just the current tree.

At minimum inspect:

- recent commits affecting architecture;
- recent security/build fixes;
- merge commits affecting protected integration branches;
- suspicious revert/forward-fix patterns;
- large changes without tests;
- changes that modified tests and production code simultaneously;
- migration history.

Use history to identify regressions or stale design assumptions, not to excuse current defects.

## Phase 13: Independent second pass

After completing the first review, perform a second pass from the defect perspective rather than file-order perspective.

Ask independently:

- What could lose data?
- What could expose credentials or personal data?
- What could publish invalid content?
- What could authorize the wrong user?
- What could break on one KMP target only?
- What could pass CI while remaining broken in production?
- What happens on an empty database?
- What happens on duplicate input?
- What happens on malformed input?
- What happens under concurrency?
- What happens when external services are unavailable?
- What happens when configuration is missing?
- What happens during rollback or partial deployment?
- What happens when a migration is rerun?
- What happens when a stale client calls the API?

Every important answer must be grounded in evidence.

## Findings format

Each finding must include:

- severity;
- evidence state;
- file/path;
- exact line or smallest available range;
- concrete observed behavior;
- why it matters;
- affected contract or invariant;
- recommended fix;
- whether the fix is blocking or non-blocking.

Use severity:

- CRITICAL — exploitable security issue, unrecoverable data loss, or severe correctness failure;
- HIGH — major functional, security, architecture, or integrity defect;
- MEDIUM — meaningful defect with bounded impact;
- LOW — minor correctness, maintainability, or documentation issue;
- INFO — improvement or observation without a demonstrated defect.

Do not rank projects, people, or choices. Findings rank the severity of technical defects only.

## Completeness gate

Before declaring the review complete, verify:

- every tracked file was reviewed or explicitly accounted for;
- every top-level directory was inspected;
- every build module was inspected;
- native non-Gradle projects were inspected;
- every documentation file was read;
- every migration was read;
- every workflow was read;
- every security control was traced to evidence;
- all open security/code-scanning findings were checked;
- CI results for the reviewed HEAD were checked;
- relevant history was inspected;
- cross-file contracts were traced;
- the independent second pass was completed.

If any item is not satisfied, the review is incomplete.

## Review output

The final report must contain:

1. Scope and exact reviewed HEAD;
2. Repository inventory and file-count accounting;
3. Findings ordered by technical severity;
4. Contract/architecture inconsistencies;
5. Test and CI gaps;
6. Security findings;
7. Documentation inconsistencies;
8. Repository hygiene/governance findings;
9. Confirmed strengths only when directly evidenced;
10. UNKNOWN items and the exact missing evidence;
11. Concrete remediation sequence.

Do not provide a generic project summary instead of findings.

Do not declare the repository healthy merely because CI is green.

Do not hide findings behind averages, scores, or an overall grade.

## Remediation discipline

During a review-only task, do not silently modify production code to make the review look better.

When a fix is explicitly requested after the review, switch back to the normal change workflow:

task branch -> ai/integration -> develop

Run focused and broader verification again after each material fix.