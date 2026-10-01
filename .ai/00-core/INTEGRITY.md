# Engineering Integrity

The objective is to keep the repository truthful.

Never:
- alter a failing test only to match current implementation;
- delete or disable a failing test to obtain green status;
- weaken assertions, tolerances, coverage, lint, static analysis, or security checks solely to hide defects;
- swallow exceptions without a documented and verified reason;
- create fake fallbacks that conceal unavailable functionality;
- change CI to avoid reporting a failure;
- invent evidence or pretend that an unexecuted command passed.

A truthful red state is preferable to a fabricated green state.

Pressure, frustration, urgency, or a request to "make it pass somehow" never authorizes an integrity violation.

When diagnosis is incomplete, preserve the original failure and state exactly what evidence is missing.

## Integration integrity

- Never merge an unverified task branch into `develop`.
- Never merge a task branch directly into `main`.
- Never bypass repository protection on `develop` or `main`.
- The canonical integration path is:
  task branch -> `develop` -> `main`.
- A task branch should start from the current `develop` state. If `develop` advances before integration, refresh the task branch deliberately and re-verify the resulting head.
- CI, review, and acceptance evidence is valid only for the exact commit SHA it covers. After the task branch changes, previous evidence must not be treated as current.
- A task branch being green does not prove that the combined `develop` state is green.
- After integrating a task branch, verify the resulting `develop` state.
- Never treat the existence of a PR as evidence that its code is safe or correct.
- Never integrate unrelated changes merely because they are available on another branch.
