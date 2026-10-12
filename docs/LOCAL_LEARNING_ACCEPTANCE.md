# Local Learning Acceptance

## One-command Windows demo

Requirements: Windows PowerShell 5.1, JDK 21, Docker Desktop running, and local ports 5432 and 8080 available.

First, preserve any local work and update the checkout to the task branch:

    git status --short
    git fetch origin
    git switch feat/learning-real-vertical-slice
    git pull --ff-only origin feat/learning-real-vertical-slice

From the repository root, run:

    .\scripts\run-local-learning-demo.ps1

The script starts PostgreSQL, starts or reuses a healthy backend bound to loopback, creates and publishes a timestamped course and lesson through the real authoring/review/publish API, verifies the learner catalog and lesson package, and launches the Desktop UI. The development-role headers are for isolated local acceptance only; do not expose the development backend to a shared or public network.

The script uses the local demo database credentials documented in `compose.yaml`. It does not delete Docker volumes or reset existing database contents.

## Manual product acceptance

Exercise this order; it intentionally checks re-download **before** completing the lesson because learning progress must survive removing the downloaded content package.

1. In Catalog, confirm the timestamped course and its “Greetings” lesson appear.
2. Click Download. Wait for the action to change to Open.
3. Click Downloaded and open the lesson.
4. Confirm the exercise prompt, answer field, progress indicator, and Check answer button appear.
5. Submit an incorrect answer. “Try again” must appear, and the exercise must remain active.
6. Return to lessons, click Remove, and confirm the downloaded list is empty.
7. Return to Catalog, download the same lesson again, open Downloaded, and open the lesson. The exercise prompt and answer field must be restored.
8. While the lesson is still active, stop the backend from its separate PowerShell window with Ctrl+C. Return to the open Desktop app and submit “merhaba”. The lesson must show Completed and “Lesson complete.” while the backend is stopped. This verifies answering offline using the downloaded package.
9. Close Desktop while leaving the backend stopped.
10. Restart Desktop using the explicit offline command below. Catalog may show a connection error; open Downloaded and verify the lesson remains present and opens as Completed. Progress is durable and therefore must not reset to an active exercise after restart.

The remove/re-download step happens before the correct answer on purpose. Removing a package does not erase durable learning attempts; once the correct answer is saved, reopening the same lesson version should show Completed.

After testing, restart the backend by rerunning the demo script when online catalog/download behavior is needed. Stop PostgreSQL, if desired, from the repository root with:

    docker compose stop postgres

Each demo run creates a timestamped course so the authoring and publication path is exercised rather than bypassed by writing fixture rows directly to the database.

## Explicit offline restart command

After the first demo run has opened the Desktop app and you have stopped the backend as described in step 8, close Desktop. Open a new PowerShell window at the repository root and run:

    $env:TILDASH_API_URL = "http://127.0.0.1:8080"
    $env:TILDASH_DEVELOPMENT_ROLE = "learner"
    .\gradlew.bat :app:desktopApp:run --no-configuration-cache

The app should report that the catalog cannot be reached while the backend is stopped, but downloaded lesson content and local progress must remain accessible. Open Downloaded and verify the lesson state is Completed. Do not expect another answer input for a lesson version whose correct answer was already saved.

## Automated verification

The Compose Desktop user journey is covered by `AppUserJourneyTest`:

    .\gradlew.bat :app:shared:jvmTest --no-configuration-cache

The CI workflow also parses the demo script and tests its Windows PowerShell native-command stderr handling. CI must be checked against the latest PR head SHA; older runs are not evidence for a newer commit.

## Integration gate

Keep the PR in draft until applicable CI checks are green and the project owner has manually exercised the complete flow on the local machine. Do not merge this task branch into `ai/integration` before that local acceptance. The final `ai/integration`-to-`develop` promotion requires fresh acceptance against the exact promotion PR head.
