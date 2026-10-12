# Local Learning Acceptance

## One-command Windows demo

Requirements: Windows PowerShell 5.1, JDK 21, Docker Desktop running, and local ports 5432 and 8080 available.

Preserve uncommitted work and update the task branch from the repository root:

    git status --short
    git fetch origin
    git switch feat/learning-real-vertical-slice
    git pull --ff-only origin feat/learning-real-vertical-slice
    git rev-parse HEAD

Then run:

    .\scripts\run-local-learning-demo.ps1

The script publishes a three-lesson beginner course through the real authoring, validation, review and publication API. Each lesson contains an explanation, a vocabulary list, a translated example dialogue and three exercises. The learner API is checked for all three lessons and their materials before the Desktop app is launched.

The development-role headers are only for local acceptance. The script binds the backend and database to loopback; do not expose the development backend to a shared or public network.

**Database caution:** this branch corrects the PostgreSQL 18 data mount. If Compose recreates a container created with the old mount target, its old database cluster may not be found at the new expected path, and the new container may initialize a fresh local demo database. Back up any valuable local database data before rerunning. The script does not run `docker compose down -v` and does not delete named volumes.

## What to test in Desktop

1. In Catalog, open the course titled “Crimean Tatar: First Conversations” with a timestamp.
2. Confirm three lessons are listed: “Greetings and polite replies”, “Morning greetings and how things are”, and “Thanking and saying goodbye”.
3. Download the first lesson and open it from Downloaded.
4. Above Practice, verify that Study material displays an explanation, multiple vocabulary entries with Russian meanings, and an example dialogue.
5. Work through all three exercises. An incorrect answer must show “Try again” and keep the current exercise. A correct answer must advance progress to the next exercise.
6. Return to the catalog and download/open each remaining lesson. Each has different material and three exercises, not just a renamed copy of the first lesson.
7. Remove one downloaded lesson and download it again. Its explanation, vocabulary and example must be restored from the published lesson package.
8. With a lesson downloaded and not yet completed, stop the backend from its PowerShell window with Ctrl+C. Continue answering the active lesson while offline.
9. Close and restart Desktop while the backend remains stopped. Downloaded content and saved progress must remain available.

## Expected content

The course teaches short introductory interactions: greetings and conventional replies, asking how things are, responding “good / not good”, thanking informally and politely, and context-appropriate farewells. The language examples are grounded in the Crimean Tatar model curriculum and language-etiquette references listed below; the explanations, lesson structure and exercises are authored for this demo.

Curriculum reference: https://mon.gov.ua/static-objects/mon/sites/1/informatsia-dla-osib-z-tot-ta-vpo/materialy-mnp/modelni-navch-programy-na-tot/integrovanii-kurs-krimskotatarskoyi-movi-ta-literaturi.pdf

Language-etiquette reference: https://ana-yurt.com/urok-4-rechevoy-etiket-selamlasuv-ve-miracaat-etuv

## Automated verification

The Compose user journey includes displayed lesson material as well as practice interactions. Run the target-specific test task on Windows:

    .\gradlew.bat :app:shared:jvmTest --no-configuration-cache

The server integration test verifies that published lesson material is returned from the immutable published-version snapshot, even if a draft child node receives a later source revision. SQLDelight tests verify that downloaded teaching materials survive a local database reopen. GitHub CI must be checked against the latest PR head SHA.

## Integration gate

Keep the PR in draft until the applicable CI checks pass and the project owner has completed the local acceptance path above. Do not merge this task branch into `ai/integration` before that acceptance is explicitly confirmed. The final `ai/integration`-to-`develop` promotion requires fresh acceptance against its exact PR head.
