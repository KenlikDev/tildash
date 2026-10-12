# Use loopback only: this script sends local development-role headers.
$BaseUrl = "http://127.0.0.1:8080"
$environmentNames = @(
    "TILDASH_DATABASE_NAME",
    "TILDASH_DATABASE_USERNAME",
    "TILDASH_DATABASE_PASSWORD",
    "TILDASH_DATABASE_PORT",
    "TILDASH_DATABASE_URL",
    "TILDASH_SECURITY_DEVELOPMENT_ENABLED",
    "TILDASH_API_URL",
    "TILDASH_DEVELOPMENT_ROLE",
    "SERVER_ADDRESS"
)
$originalEnvironment = @{}
foreach ($name in $environmentNames) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "compose.yaml"

if (-not (Test-Path (Join-Path $repoRoot "gradlew.bat"))) {
    throw "Run this script from a checkout that contains gradlew.bat."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Install and start Docker Desktop, then retry."
}

function Invoke-ExternalCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    # Windows PowerShell 5.1 can stop on native stderr even when it is redirected.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $commandOutput = @(& $FilePath @Arguments 2>&1)
        $commandExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    [pscustomobject]@{
        ExitCode = $commandExitCode
        Output = $commandOutput
    }
}

function Write-ExternalCommandOutput {
    param(
        [object[]]$Lines
    )

    foreach ($line in $Lines) {
        if ($line -is [System.Management.Automation.ErrorRecord]) {
            Write-Host $line.Exception.Message
        } else {
            Write-Host ([string]$line)
        }
    }
}

$serverResponding = $false
$alreadyRunning = $false
try {
    $existingHealth = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 2
    $serverResponding = $true
    $alreadyRunning = $existingHealth.status -eq "UP"
} catch {
    # A refused connection is expected before the local backend starts.
}
if ($serverResponding -and -not $alreadyRunning) {
    throw "A server is responding at $BaseUrl but is not healthy. Stop it and retry the local demo."
}

# Use isolated, documented local-development credentials for this demo process only.
$env:TILDASH_DATABASE_NAME = "tildash"
$env:TILDASH_DATABASE_USERNAME = "tildash"
$env:TILDASH_DATABASE_PASSWORD = "tildash-dev"
$env:TILDASH_DATABASE_PORT = "5432"
$env:TILDASH_DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/tildash"

Write-Host "Starting the local PostgreSQL service..."
$composeResult = Invoke-ExternalCommand -FilePath "docker" -Arguments @(
    "compose", "-f", $composeFile, "up", "-d", "postgres"
)
Write-ExternalCommandOutput -Lines $composeResult.Output
if ($composeResult.ExitCode -ne 0) {
    $composeLogResult = Invoke-ExternalCommand -FilePath "docker" -Arguments @(
        "compose", "-f", $composeFile, "logs", "--no-color", "--tail", "100", "postgres"
    )
    if ($composeLogResult.Output) {
        Write-Host "Recent PostgreSQL container logs:"
        Write-ExternalCommandOutput -Lines $composeLogResult.Output
    }
    throw "Docker Compose could not start PostgreSQL. Check Docker Desktop and port 5432."
}

$containerIdResult = Invoke-ExternalCommand -FilePath "docker" -Arguments @(
    "compose", "-f", $composeFile, "ps", "-q", "postgres"
)
$containerId = (($containerIdResult.Output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" } | Select-Object -First 1) -as [string])
if ($containerIdResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    $composeLogResult = Invoke-ExternalCommand -FilePath "docker" -Arguments @(
        "compose", "-f", $composeFile, "logs", "--no-color", "--tail", "100", "postgres"
    )
    if ($composeLogResult.Output) {
        Write-Host "Recent PostgreSQL container logs:"
        Write-ExternalCommandOutput -Lines $composeLogResult.Output
    }
    throw "Docker Compose did not return a PostgreSQL container ID. Inspect the Docker output above."
}

$databaseReady = $false
$lastContainerState = "unknown"
$lastHealthState = "unknown"
$restartObservations = 0
for ($attempt = 1; $attempt -le 45; $attempt++) {
    $stateResult = Invoke-ExternalCommand -FilePath "docker" -Arguments @(
        "inspect", "--format", '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}', $containerId
    )
    if ($stateResult.ExitCode -eq 0) {
        $stateLine = (($stateResult.Output | Select-Object -First 1) -as [string]).Trim()
        $stateParts = $stateLine -split '\|', 2
        if ($stateParts.Count -ge 1) {
            $lastContainerState = $stateParts[0]
        }
        if ($stateParts.Count -ge 2) {
            $lastHealthState = $stateParts[1]
        }

        if ($lastContainerState -eq "running" -and $lastHealthState -eq "healthy") {
            $databaseReady = $true
            break
        }

        if ($lastContainerState -eq "restarting") {
            $restartObservations++
        } else {
            $restartObservations = 0
        }

        if ($lastContainerState -in @("exited", "dead") -or $restartObservations -ge 3) {
            break
        }
    }
    Start-Sleep -Seconds 2
}

if (-not $databaseReady) {
    $composeLogResult = Invoke-ExternalCommand -FilePath "docker" -Arguments @(
        "compose", "-f", $composeFile, "logs", "--no-color", "--tail", "100", "postgres"
    )
    if ($composeLogResult.Output) {
        Write-Host ""
        Write-Host "Recent PostgreSQL container logs (the startup error should appear above):"
        Write-ExternalCommandOutput -Lines $composeLogResult.Output
    }
    throw "PostgreSQL did not become healthy. Last observed container state: '$lastContainerState'; health: '$lastHealthState'. Resolve the reported container error and rerun the demo."
}

$escapedRoot = $repoRoot.Replace("'", "''")
$serverCommand = @'
$env:TILDASH_SECURITY_DEVELOPMENT_ENABLED = "true"
$env:SERVER_ADDRESS = "127.0.0.1"
$env:TILDASH_DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/tildash"
$env:TILDASH_DATABASE_USERNAME = "tildash"
$env:TILDASH_DATABASE_PASSWORD = "tildash-dev"
Set-Location -LiteralPath '__REPO_ROOT__'
& ".\gradlew.bat" ":server:bootRun" "--no-configuration-cache"
'@
$serverCommand = $serverCommand.Replace("__REPO_ROOT__", $escapedRoot)

$windowsPowerShell = Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
if (-not (Test-Path $windowsPowerShell)) {
    $windowsPowerShell = (Get-Command powershell.exe -ErrorAction Stop).Source
}
$encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($serverCommand))
if ($alreadyRunning) {
    Write-Host "Reusing the healthy local backend already listening on 127.0.0.1:8080..."
} else {
    Write-Host "Starting the backend in a separate PowerShell window..."
    Start-Process -FilePath $windowsPowerShell -WorkingDirectory $repoRoot -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-EncodedCommand", $encodedCommand
    )
}

$serverReady = $false
$healthAttempts = if ($alreadyRunning) { 1 } else { 180 }
for ($attempt = 1; $attempt -le $healthAttempts; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 2
        if ($health.status -eq "UP") {
            $serverReady = $true
            break
        }
    } catch {
        # The Gradle application may still be starting.
    }
    Start-Sleep -Seconds 2
}
if (-not $serverReady) {
    throw "The backend did not become healthy within six minutes. Inspect the backend PowerShell window before retrying."
}

function Invoke-TildashRequest {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [hashtable]$Headers,
        [object]$Body = $null
    )

    $parameters = @{
        Method = $Method
        Uri = ($BaseUrl.TrimEnd("/") + $Path)
        Headers = $Headers
        TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 20
    }
    Invoke-RestMethod @parameters
}

$teacher = @{ "X-Tildash-Development-Role" = "teacher" }
$reviewer = @{ "X-Tildash-Development-Role" = "reviewer" }
$learner = @{ "X-Tildash-Development-Role" = "learner" }
$demoTitle = "Crimean Tatar: First Conversations (" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss") + ")"
$curriculumSource = "https://mon.gov.ua/static-objects/mon/sites/1/informatsia-dla-osib-z-tot-ta-vpo/materialy-mnp/modelni-navch-programy-na-tot/integrovanii-kurs-krimskotatarskoyi-movi-ta-literaturi.pdf"
$etiquetteSource = "https://ana-yurt.com/urok-4-rechevoy-etiket-selamlasuv-ve-miracaat-etuv"

$lessonPlans = @(
    @{
        Title = "Greetings and polite replies"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "Use 'Merhaba!' as a general hello. 'Selâm aleyküm!' is another traditional greeting; a conventional reply is 'Aleyküm selâm!' Learn that pair as a greeting and response, rather than translating each word separately."; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Merhaba!"; Gloss = "Привет! / Здравствуйте!"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "Selâm aleyküm!"; Gloss = "Традиционное приветствие"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Aleyküm selâm!"; Gloss = "Ответ на «Selâm aleyküm!»"; SourceLocator = $curriculumSource }
            @{ Kind = "EXAMPLE"; Position = 4; Text = "A: Selâm aleyküm! — Здравствуйте! / B: Aleyküm selâm! — И вам здравствуйте! / A: Merhaba! — Привет!"; SourceLocator = $curriculumSource }
        )
        Exercises = @(
            @{ Id = "greetings-1"; Position = 0; Prompt = "Translate 'Hello!' into Crimean Tatar."; ExpectedAnswers = @("Merhaba", "Merhaba!") }
            @{ Id = "greetings-2"; Position = 1; Prompt = "Reply to 'Selâm aleyküm!' in Crimean Tatar."; ExpectedAnswers = @("Aleyküm selâm", "Aleyküm selâm!") }
            @{ Id = "greetings-3"; Position = 2; Prompt = "Type the traditional greeting that begins with 'Selâm'."; ExpectedAnswers = @("Selâm aleyküm", "Selâm aleyküm!") }
        )
    }
    @{
        Title = "Morning greetings and how things are"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "'Sabahlar hayır!' is a morning greeting. 'İşler nasıl?' asks how things are going. 'Yahşı' means good or well; 'yaramay' can mean not good. Try to understand a phrase in context, not as an isolated word list."; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Sabahlar hayır!"; Gloss = "Доброе утро!"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "İşler nasıl?"; Gloss = "Как дела? / Как идут дела?"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Yahşı"; Gloss = "Хорошо"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 4; Term = "Yaramay"; Gloss = "Плохо / нехорошо"; SourceLocator = $curriculumSource }
            @{ Kind = "EXAMPLE"; Position = 5; Text = "A: Sabahlar hayır! — Доброе утро! / B: Sabahlar hayır! / A: İşler nasıl? — Как дела? / B: Yahşı, sağ ol. — Хорошо, спасибо."; SourceLocator = $curriculumSource }
        )
        Exercises = @(
            @{ Id = "daily-1"; Position = 0; Prompt = "Translate 'Good morning!' into Crimean Tatar."; ExpectedAnswers = @("Sabahlar hayır", "Sabahlar hayır!") }
            @{ Id = "daily-2"; Position = 1; Prompt = "Type the question that means 'How are things?'"; ExpectedAnswers = @("İşler nasıl", "İşler nasıl?") }
            @{ Id = "daily-3"; Position = 2; Prompt = "Translate 'Good / well' into Crimean Tatar."; ExpectedAnswers = @("Yahşı") }
        )
    }
    @{
        Title = "Thanking and saying goodbye"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "'Sağ ol' is an informal way to say thank you; 'Sağ oluñız' is the polite form. 'Körüskence!' means 'See you!'. In a farewell, 'Sağlıqnen qalıñız' is said by a person leaving to those staying; 'Sağlıqnen barıñız' is said to the person who is leaving."; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Sağ ol"; Gloss = "Спасибо (неформально)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "Sağ oluñız"; Gloss = "Спасибо (вежливо)"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Körüskence!"; Gloss = "До встречи!"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 4; Term = "Sağlıqnen qalıñız"; Gloss = "До свидания! (уходящий говорит остающимся)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 5; Term = "Sağlıqnen barıñız"; Gloss = "До свидания! (говорят уходящему)"; SourceLocator = $etiquetteSource }
            @{ Kind = "EXAMPLE"; Position = 6; Text = "A: Sağ oluñız! — Спасибо вам! / B: Sağlıqnen qalıñız! — До свидания! / A: Körüskence! — До встречи!"; SourceLocator = $etiquetteSource }
        )
        Exercises = @(
            @{ Id = "farewell-1"; Position = 0; Prompt = "Type the polite form of 'Thank you'."; ExpectedAnswers = @("Sağ oluñız", "Sağ oluñız!") }
            @{ Id = "farewell-2"; Position = 1; Prompt = "Translate 'See you!' into Crimean Tatar."; ExpectedAnswers = @("Körüskence", "Körüskence!") }
            @{ Id = "farewell-3"; Position = 2; Prompt = "You are leaving and say goodbye to the people staying. Type the phrase."; ExpectedAnswers = @("Sağlıqnen qalıñız", "Sağlıqnen qalıñız!") }
        )
    }
)

Write-Host "Creating a three-lesson beginner course with teaching material and several exercises per lesson..."
$course = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
    kind = "COURSE"
    parentId = $null
    sourceLocale = "crh"
    position = 0
    payload = @{ type = "TEXT"; value = $demoTitle }
    provenance = @{
        sourceTitle = "Tildash original beginner course"
        sourceLocator = $curriculumSource
        copyrightStatus = "PUBLIC_DOMAIN"
        authorName = "Tildash local demo"
    }
}

$createdLessons = @()
for ($lessonIndex = 0; $lessonIndex -lt $lessonPlans.Count; $lessonIndex++) {
    $plan = $lessonPlans[$lessonIndex]
    $lesson = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
        kind = "LESSON"
        parentId = $course.id
        sourceLocale = "crh"
        position = $lessonIndex
        payload = @{ type = "TEXT"; value = $plan.Title }
        provenance = @{
            sourceTitle = "Tildash original Crimean Tatar learner lesson"
            sourceLocator = $curriculumSource
            copyrightStatus = "PUBLIC_DOMAIN"
            authorName = "Tildash local demo"
        }
    }
    $createdLessons += [pscustomobject]@{ Id = $lesson.id; Plan = $plan }

    foreach ($material in $plan.Materials) {
        if ($material.Kind -eq "VOCABULARY") {
            $payload = @{ type = "VOCABULARY"; term = $material.Term; gloss = $material.Gloss }
        } else {
            $payload = @{ type = "TEXT"; value = $material.Text }
        }

        $null = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
            kind = $material.Kind
            parentId = $lesson.id
            sourceLocale = "crh"
            position = $material.Position
            payload = $payload
            provenance = @{
                sourceTitle = "Crimean Tatar introductory curriculum and language etiquette references"
                sourceLocator = $material.SourceLocator
                copyrightStatus = "PUBLIC_DOMAIN"
                authorName = "Tildash local demo"
            }
        }
    }

    foreach ($exercise in $plan.Exercises) {
        $null = Invoke-TildashRequest -Method "POST" -Path ("/api/v1/content/" + $lesson.id + "/exercises") -Headers $teacher -Body @{
            id = $exercise.Id
            prompt = $exercise.Prompt
            position = $exercise.Position
            expectedAnswers = @($exercise.ExpectedAnswers)
        }
    }
}

function Publish-ContentNode {
    param([Parameter(Mandatory = $true)][string]$ContentId)

    foreach ($step in @(
        @{ Path = "/api/v1/content/" + $ContentId + "/submit"; Headers = $teacher },
        @{ Path = "/api/v1/content/" + $ContentId + "/review/start"; Headers = $reviewer },
        @{ Path = "/api/v1/content/" + $ContentId + "/review/approve"; Headers = $reviewer },
        @{ Path = "/api/v1/content/" + $ContentId + "/publish"; Headers = $reviewer }
    )) {
        $null = Invoke-TildashRequest -Method "POST" -Path $step.Path -Headers $step.Headers
    }
}

Write-Host "Publishing the course and all three validated lessons..."
Publish-ContentNode -ContentId $course.id
foreach ($record in $createdLessons) {
    Publish-ContentNode -ContentId $record.Id
}

$catalog = Invoke-TildashRequest -Method "GET" -Path "/api/v1/learning/catalog" -Headers $learner
$publishedCourse = @($catalog.courses | Where-Object { $_.id -eq $course.id }) | Select-Object -First 1
if ($null -eq $publishedCourse) {
    throw "The newly published course was not returned by the learner catalog."
}
if (@($publishedCourse.lessons).Count -ne 3) {
    throw "Expected three published lessons; learner catalog returned $(@($publishedCourse.lessons).Count)."
}

foreach ($record in $createdLessons) {
    $package = Invoke-TildashRequest -Method "GET" -Path ("/api/v1/learning/lessons/" + $record.Id) -Headers $learner
    if (@($package.materials).Count -lt 3) {
        throw "Lesson '$($record.Plan.Title)' did not return its explanation, vocabulary, and example blocks."
    }
    if (@($package.exercises).Count -lt 3) {
        throw "Lesson '$($record.Plan.Title)' did not return all three practice exercises."
    }
}

Write-Host ""
Write-Host ("Ready to explore: " + $demoTitle)
Write-Host "The published course contains three lessons, explanations, vocabulary, examples, and three exercises per lesson."
Write-Host "The backend stays open in its own PowerShell window."
Write-Host "Starting Desktop. Open Catalog, download the first lesson, and study its material before practising."
Write-Host ""

$env:TILDASH_API_URL = $BaseUrl.TrimEnd("/")
$env:TILDASH_DEVELOPMENT_ROLE = "learner"
Push-Location $repoRoot
$gradleExitCode = -1
try {
    # Keep Gradle output live while allowing ordinary native stderr diagnostics.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & ".\gradlew.bat" ":app:desktopApp:run" "--no-configuration-cache"
        $gradleExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
} finally {
    Pop-Location
}
if ($gradleExitCode -ne 0) {
    throw "The Desktop application did not exit successfully (Gradle exit code: $gradleExitCode). Review the Gradle output above."
}

Write-Host "Acceptance run finished. Close the backend window when done; stop PostgreSQL with 'docker compose stop postgres' if desired."
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], "Process")
    }
}
