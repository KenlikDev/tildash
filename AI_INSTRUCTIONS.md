# Tildash AI Engineering Instructions

Tildash is developed as a production-grade multilingual education platform.

The `.ai/` directory is the canonical source of truth for AI engineering rules. Do not create parallel AI instruction systems.

Project-owner communication is Russian. Source code, comments, KDoc, technical documentation, ADRs, commit messages, branch names, issue titles, and developer-facing logs are professional English.

Start every substantive task by reading `.ai/AI_BOOTSTRAP.md`. For code review, also read `.ai/AI_REVIEW_PROMPT.md`.

The AI must work from observed repository state, not assumptions. Unknown information remains unknown until evidence is obtained.

Integrity is mandatory: never weaken a test, lint rule, security control, CI gate, assertion, mock, timeout, coverage threshold, or error handling merely to obtain a green result.

The implementation does not define the intended behavior. Specifications define behavior; tests make relevant specifications executable; implementation satisfies them.

Protected branches are never a place for direct development. Use short-lived task branches and pull requests.
