# Local Learning Acceptance

## One-command Windows demo

Requirements: Windows PowerShell, JDK 21, and Docker Desktop running.

First, update the local checkout to the task branch that contains the learner UI:

    git fetch origin
    git switch feat/learning-real-vertical-slice
    git pull --ff-only origin feat/learning-real-vertical-slice

From the repository root, run:

    .\scripts\run-local-learning-demo.ps1

The script starts PostgreSQL, starts the backend in a separate PowerShell window with the explicitly local development identity enabled, creates and publishes a fresh course and executable lesson through the real Content Studio API, verifies that the learner API returns the package, and launches the Desktop UI. It stops with an actionable error if Docker, PostgreSQL, backend health, publication, or package verification fails.

The development identity is only for isolated local acceptance. Do not expose this backend to a public or shared network. The script uses the repository's local development database credentials.

## Manual product acceptance

In the Tildash Desktop window, verify each step:

1. The Catalog tab displays the timestamped local demo course and the “Greetings” lesson.
2. Click Download. Wait for the action to change to Open.
3. Click Downloaded and verify that the lesson appears there.
4. Click Open. The lesson screen must display the exercise prompt, answer field, progress indicator, and Check answer button.
5. Submit an incorrect answer. “Try again” must appear, and the same exercise must remain active.
6. Submit “merhaba”. The lesson must show Completed and “Lesson complete.”
7. Return to lessons and click Remove. The downloaded lesson must disappear.
8. Download the lesson again, open it, and verify that its exercise and expected answers are restored.
9. Close and restart the Desktop app. Previously downloaded content must remain available.
10. After downloading, stop the backend and restart Desktop. The downloaded lesson must still open and accept answers offline.

Keep the backend PowerShell window open while testing catalog and download behavior. Close it after the UI run ends. PostgreSQL can be stopped with:

    docker compose stop postgres

Each run creates a timestamped demo course so the authoring and publication workflow is exercised rather than bypassed by inserting fixture rows directly into the database.

## Integration gate

The task branch and its automated checks are not proof of local acceptance. Do not merge this change into ai/integration until the owner has personally exercised the flow above. The final ai/integration-to-develop promotion additionally requires acceptance against the exact promotion PR head.
