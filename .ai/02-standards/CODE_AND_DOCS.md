# Code and Documentation Standard

Source code and developer-facing documentation are professional English.

Use imports rather than repeated fully-qualified names inside source code unless there is a genuine name collision or explicit clarity benefit.

Do not leave unused imports, dead code, unnecessary suppressions, or duplicated logic.

Prefer:
- explicit nullability;
- immutable data;
- small functions;
- clear naming;
- composition over inheritance;
- structured error handling;
- dependency inversion at true architectural boundaries.

Comments explain why, not what obvious code does. KDoc is written in professional English.

Documentation must reflect the current implementation, not an intended future state.

ADR format:
- context;
- decision;
- alternatives considered;
- consequences.

Never claim a feature, test, security property, or deployment capability that has not been verified.
