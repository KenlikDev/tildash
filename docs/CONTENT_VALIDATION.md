# Tildash Content Validation

## Purpose

Content validation is deterministic and provider-neutral. The same lesson input must produce the same validation result regardless of whether validation is invoked from CI, a future authoring tool, a server use case, or an import pipeline.

The validator is implemented in:

core/src/commonMain/kotlin/com/kenlikdev/tildash/content/validation/ContentValidation.kt

The shared module contains no Spring, JDBC, Flyway, PostgreSQL, HTTP, or AI-provider dependency.

## Input

LessonValidationInput contains:

- the lesson ID being validated;
- the content tree;
- source revisions;
- localization revisions;
- exercises grouped by content ID.

Exercise definitions are validation inputs rather than trusted persisted entities. They are deliberately allowed to contain malformed values so the validator can report actionable diagnostics.

## Deterministic rules

### Structure

Validation rejects:

- a missing lesson target;
- a target that is not a LESSON;
- duplicate content IDs;
- missing parent nodes;
- courses with parents;
- invalid lesson ancestry;
- parent cycles;
- duplicate sibling positions.

A valid lesson is directly under a course. Descendants are validated as part of the same content tree.

### Source revisions

Every non-course content node in the lesson subtree must have at least one source revision.

For each content ID, a revision number may appear only once.

The latest source revision is the revision used for provenance and payload validation.

### Provenance and licensing

The validator reports an explicit warning when copyright status is unknown.

LICENSED and PERMISSION_GRANTED require a license reference.

IN_COPYRIGHT without a license or permission reference is a warning while content is still DRAFT or SUBMITTED, but blocks review for later states.

RESTRICTED content cannot be published.

The validator does not infer ownership or legal permission from free-form text. It only evaluates the explicit content contract.

### Localization

Localization revisions must belong to the validated lesson subtree and must reference an existing source revision with the same content ID.

Duplicate (contentId, locale, revision) keys are rejected.

### Payload integrity

Payload type must match the content kind:

- COURSE, LESSON, and EXAMPLE use Text;
- VOCABULARY uses Vocabulary;
- MEDIA_REFERENCE uses MediaReference.

Media references additionally require:

- a URI containing a syntactically valid scheme and no whitespace;
- a media type using type/subtype syntax when present.

The validator does not perform network access. Reachability and storage policy are separate infrastructure concerns.

### Exercises

Exercises must belong to the validated lesson subtree.

Exercise IDs must be unique within the validation input.

An exercise requires:

- a non-blank prompt;
- at least one expected answer;
- no blank expected answers;
- no duplicate answers after trimming and case normalization.

When an option set is provided, it must contain at least two non-blank, unique options, and every expected answer must be present in the option set.

These are deterministic consistency rules. They do not attempt to judge pedagogical quality that requires human or model interpretation.

## Result contract

A ReviewResult contains zero or more ValidationIssue values.

Each issue has:

- a stable ValidationCode;
- ERROR or WARNING severity;
- an actionable message;
- an optional path into the validated input.

The result exposes:

PASS
PASS_WITH_WARNINGS
FAIL

PASS_WITH_WARNINGS is intentionally distinct from PASS.

The result also exposes:

- canSubmit — false when blocking errors exist;
- canPublish — true only for a clean PASS.

## AI boundary

Deterministic validation does not call an AI provider.

Future AI-assisted review may add findings, explanations, suggestions, or confidence metadata, but it must not:

- replace deterministic structural rules;
- convert a deterministic error into a pass;
- publish content autonomously;
- become the source of truth for provenance or licensing.

AI review is a separate contract and remains future work.

## Verification

The validator's specification, implementation, and common tests are maintained together.

Changes to validation codes or rule semantics must update all three surfaces in the same change.

The durable architectural boundary is recorded in ADR-0008.