# Local Learning Acceptance

This acceptance flow validates the first real learner vertical slice against the local backend and Desktop application.

## Preconditions

Start PostgreSQL using the repository's existing local development setup.

Start the backend with the explicit local development identity enabled:

    $env:TILDASH_SECURITY_DEVELOPMENT_ENABLED = "true"
    .\gradlew.bat :server:bootRun --no-configuration-cache

The development identity is disabled by default and is intended only for local acceptance.

## Create and publish real lesson content

From a second PowerShell window:

    $base = "http://127.0.0.1:8080"

    $teacher = @{ "X-Tildash-Development-Role" = "teacher" }
    $reviewer = @{ "X-Tildash-Development-Role" = "reviewer" }
    $learner = @{ "X-Tildash-Development-Role" = "learner" }

    $courseBody = @{
      kind = "COURSE"
      parentId = $null
      sourceLocale = "crh"
      position = 0
      payload = @{ type = "TEXT"; value = "Crimean Tatar basics" }
      provenance = @{ sourceTitle = "Local MVP content"; copyrightStatus = "PUBLIC_DOMAIN"; authorName = "Local developer" }
    } | ConvertTo-Json -Depth 10

    $course = Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/nodes" -Headers $teacher -ContentType "application/json" -Body $courseBody

    $lessonBody = @{
      kind = "LESSON"
      parentId = $course.id
      sourceLocale = "crh"
      position = 0
      payload = @{ type = "TEXT"; value = "Greetings" }
      provenance = @{ sourceTitle = "Local MVP content"; copyrightStatus = "PUBLIC_DOMAIN"; authorName = "Local developer" }
    } | ConvertTo-Json -Depth 10

    $lesson = Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/nodes" -Headers $teacher -ContentType "application/json" -Body $lessonBody

    $exerciseBody = @{
      id = "exercise-1"
      prompt = "Translate hello."
      position = 0
      expectedAnswers = @("merhaba")
    } | ConvertTo-Json -Depth 10

    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($lesson.id)/exercises" -Headers $teacher -ContentType "application/json" -Body $exerciseBody

    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($course.id)/submit" -Headers $teacher
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($course.id)/review/start" -Headers $reviewer
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($course.id)/review/approve" -Headers $reviewer
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($course.id)/publish" -Headers $reviewer

    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($lesson.id)/submit" -Headers $teacher
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($lesson.id)/review/start" -Headers $reviewer
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($lesson.id)/review/approve" -Headers $reviewer
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/content/$($lesson.id)/publish" -Headers $reviewer

Verify the learner package directly:

    Invoke-RestMethod -Method Get -Uri "$base/api/v1/learning/catalog" -Headers $learner
    Invoke-RestMethod -Method Get -Uri "$base/api/v1/learning/lessons/$($lesson.id)" -Headers $learner

The lesson package must contain exercise-1 with type MANUAL_INPUT.

## Run Desktop

    $env:TILDASH_API_URL = "http://127.0.0.1:8080"
    $env:TILDASH_DEVELOPMENT_ROLE = "learner"
    .\gradlew.bat :app:desktopApp:run --no-configuration-cache

## Manual product acceptance

1. Catalog opens and displays the published course and lesson created above.
2. Press Download. The lesson is stored locally and its action changes to Open.
3. Open Downloaded and verify the lesson is present.
4. Open the lesson, submit an incorrect answer, and verify the same exercise remains active with Try again feedback.
5. Submit merhaba and verify the lesson advances or completes.
6. Close and restart Desktop. Open the downloaded lesson and verify progress is restored.
7. Press Remove and verify the lesson disappears from Downloaded.
8. Return to Catalog, download it again, and verify the lesson opens with its exercises.
9. Stop the backend after downloading, restart Desktop, and verify the downloaded lesson still opens and can be answered offline.

The final ai/integration -> develop promotion must not proceed until the owner has personally completed this acceptance against the exact promotion PR head.