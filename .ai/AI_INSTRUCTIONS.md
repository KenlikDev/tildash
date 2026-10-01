# Tildash AI Engineering Instructions

Tildash is developed as a production-grade multilingual education platform.

The `.ai/` directory is the canonical source of truth for AI engineering rules. Do not create parallel AI instruction systems.

Project-owner communication is Russian. Source code, comments, KDoc, technical documentation, ADRs, commit messages, branch names, issue titles, and developer-facing logs are professional English.

Start every substantive task by reading `.ai/AI_BOOTSTRAP.md`. For code review, also read `.ai/AI_REVIEW_PROMPT.md`. For a full repository review, additionally read and follow `.ai/03-processes/FULL_REPOSITORY_REVIEW.md` in full.

The AI must work from observed repository state, not assumptions. Unknown information remains unknown until evidence is obtained.

Instruction freshness is a required engineering control. At phase boundaries, after context compaction or reconnect, before the first repository mutation, before any Git/PR/merge operation, and before final verification, re-read `.ai/AI_BOOTSTRAP.md`, `.ai/AI_INSTRUCTIONS.md`, and the task-relevant instruction files. Do not rely on memory of earlier reads when a checkpoint is reached.

Integrity is mandatory: never weaken a test, lint rule, security control, CI gate, assertion, mock, timeout, coverage threshold, or error handling merely to obtain a green result.

The implementation does not define the intended behavior. Specifications define behavior; tests make relevant specifications executable; implementation satisfies them.

Protected branches are never a place for direct development. Use short-lived task branches and pull requests.

The final `ai/integration -> develop` merge has a mandatory human local-acceptance gate. The project owner must synchronize `ai/integration` locally, run the applicable verification commands, and manually exercise the changed product behavior on the local machine before merging. GitHub CI and AI verification are necessary evidence but never a replacement for hands-on local acceptance. The AI must not present the promotion PR as ready for merge until that local acceptance is explicitly confirmed.
