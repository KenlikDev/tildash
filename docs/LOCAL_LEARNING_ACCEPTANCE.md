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

The script publishes a timestamped four-lesson beginner course through the real authoring, validation, review, and publication API. Every lesson has a Russian explanation, Crimean Tatar vocabulary with Russian glosses, two example dialogues, and six exercises. The script verifies the learner catalog and all four lesson packages before launching Desktop.

The development-role headers are only for local acceptance. The script binds the backend and database to loopback; do not expose the development backend to a shared or public network.

**Content provenance:** the seeded materials are local-acceptance content, not a production curriculum. Their provenance points to the language phrases used as references; they no longer claim that the cited curriculum is public domain. See docs/LOCAL_DEMO_CONTENT_TERMS.md. Do not redistribute the seed course as production content without a content-owner review.

**Database caution:** this branch corrects the PostgreSQL 18 data mount. If Compose recreates a container created with the old mount target, its old database cluster may not be found at the new expected path, and the new container may initialize a fresh local demo database. Back up any valuable local database data before rerunning. The script does not run docker compose down -v and does not delete named volumes.

## What to test in Desktop

1. In Catalog, open the course titled “Крымскотатарский: первые разговоры” with a timestamp.
2. Confirm four lessons are listed: “Приветствия и вежливые ответы”, “Утреннее приветствие и как дела”, “Благодарность и прощание”, and “Знакомство и представление себя”.
3. Download the first lesson and open it from Downloaded.
4. Above Practice, verify that Study material displays a Russian explanation, vocabulary entries with Russian meanings, and a bilingual dialogue.
5. Work through all six exercises. An incorrect answer must show “Try again” and keep the current exercise. A correct answer must advance progress to the next exercise.
6. Return to the catalog and download/open each remaining lesson. Each has its own topic, contextual explanation, vocabulary, example dialogues, and six exercises; they are not the same exercise with a different title.
7. Remove one downloaded lesson and download it again. Its explanation, vocabulary, and example must be restored from the published lesson package.
8. With a lesson downloaded and not yet completed, stop the backend from its PowerShell window with Ctrl+C. Continue answering the active lesson while offline.
9. Close and restart Desktop while the backend remains stopped. Downloaded content and saved progress must remain available.

## Expected learning sequence

The local course now covers four short conversation situations:

- greetings and the conventional reply to “Selâm aleyküm!”;
- a morning greeting, asking how things are, and answering “good”;
- thanking politely and choosing a context-appropriate farewell;
- introducing yourself, asking someone’s name politely, and responding to an introduction.

The phrase selection follows the Ukrainian Ministry of Education’s Crimean Tatar model curriculum for greetings and introductions, including the later theme “Переїзд та нові знайомства”. The Russian explanations, dialogue composition, and exercises are written for this local demo and are not a claim that the referenced programme is public domain.

Curriculum reference: https://mon.gov.ua/static-objects/mon/sites/1/informatsia-dla-osib-z-tot-ta-vpo/materialy-mnp/modelni-navch-programy-na-tot/integrovanii-kurs-krimskotatarskoyi-movi-ta-literaturi.pdf

Language-etiquette reference: https://ana-yurt.com/urok-4-rechevoy-etiket-selamlasuv-ve-miracaat-etuv

## Automated verification

Run the target-specific UI test task on Windows:

    .\gradlew.bat :app:shared:jvmTest --no-configuration-cache

The Compose user journey verifies material display and learner interactions. Server integration tests verify that published lesson materials are read from the immutable published-version snapshot. SQLDelight tests verify that downloaded teaching materials survive a local database reopen. Check GitHub CI against the exact latest PR head SHA.

## Integration gate

Keep the PR in draft until all applicable CI checks pass and the project owner completes the local acceptance path above. Do not merge this task branch into ai/integration before that acceptance is explicitly confirmed. The final ai/integration-to-develop promotion requires fresh acceptance against its exact PR head.
