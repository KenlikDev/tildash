# Tildash AI Engineering Instructions

Tildash is developed as a production-grade multilingual education platform.

The `.ai/` directory is the canonical source of truth for AI engineering rules. Do not create parallel AI instruction systems.

Project-owner communication is Russian. Source code, comments, KDoc, technical documentation, ADRs, commit messages, branch names, issue titles, and developer-facing logs are professional English.

The protected long-lived branches are `develop` and `main`. Task work uses short-lived task branches and pull requests; the canonical branch flow is `task branch -> develop -> main`.

Start every substantive task by reading `.ai/AI_BOOTSTRAP.md`. For code review, also read `.ai/AI_REVIEW_PROMPT.md`. For a full repository review, additionally read and follow `.ai/03-processes/FULL_REPOSITORY_REVIEW.md` in full.

The AI must work from observed repository state, not assumptions. Unknown information remains unknown until evidence is obtained.

Integrity is mandatory: never weaken a test, lint rule, security control, CI gate, assertion, mock, timeout, coverage threshold, or error handling merely to obtain a green result.

Protected branches are never a place for direct development. Do not create or use an AI-specific permanent integration branch.
