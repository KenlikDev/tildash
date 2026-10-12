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
$demoTitle = "Tildash Local Demo " + (Get-Date -Format "yyyy-MM-dd HH:mm:ss")

if ($alreadyRunning) {
    try {
        $null = Invoke-TildashRequest -Method "GET" -Path "/api/v1/learning/catalog" -Headers $learner
    } catch {
        throw "The existing backend is healthy but does not accept the local development learner identity. Stop it and re-run this script."
    }
}

Write-Host "Creating and publishing a fresh demo course and exercise..."
$course = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
    kind = "COURSE"
    parentId = $null
    sourceLocale = "crh"
    position = 0
    payload = @{ type = "TEXT"; value = $demoTitle }
    provenance = @{
        sourceTitle = "Tildash local acceptance demo"
        copyrightStatus = "PUBLIC_DOMAIN"
        authorName = "Local developer"
    }
}

$lesson = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
    kind = "LESSON"
    parentId = $course.id
    sourceLocale = "crh"
    position = 0
    payload = @{ type = "TEXT"; value = "Greetings" }
    provenance = @{
        sourceTitle = "Tildash local acceptance demo"
        copyrightStatus = "PUBLIC_DOMAIN"
        authorName = "Local developer"
    }
}

$null = Invoke-TildashRequest -Method "POST" -Path ("/api/v1/content/" + $lesson.id + "/exercises") -Headers $teacher -Body @{
    id = "exercise-1"
    prompt = "Translate hello into Crimean Tatar."
    position = 0
    expectedAnswers = @("merhaba", "salam")
}

foreach ($step in @(
    @{ Path = "/api/v1/content/" + $course.id + "/submit"; Headers = $teacher },
    @{ Path = "/api/v1/content/" + $course.id + "/review/start"; Headers = $reviewer },
    @{ Path = "/api/v1/content/" + $course.id + "/review/approve"; Headers = $reviewer },
    @{ Path = "/api/v1/content/" + $course.id + "/publish"; Headers = $reviewer },
    @{ Path = "/api/v1/content/" + $lesson.id + "/submit"; Headers = $teacher },
    @{ Path = "/api/v1/content/" + $lesson.id + "/review/start"; Headers = $reviewer },
    @{ Path = "/api/v1/content/" + $lesson.id + "/review/approve"; Headers = $reviewer },
    @{ Path = "/api/v1/content/" + $lesson.id + "/publish"; Headers = $reviewer }
)) {
    $null = Invoke-TildashRequest -Method "POST" -Path $step.Path -Headers $step.Headers
}

$catalog = Invoke-TildashRequest -Method "GET" -Path "/api/v1/learning/catalog" -Headers $learner
$publishedCourse = @($catalog.courses | Where-Object { $_.id -eq $course.id }) | Select-Object -First 1
if ($null -eq $publishedCourse) {
    throw "The newly published demo course was not returned by the learner catalog."
}
$publishedLesson = @($publishedCourse.lessons | Where-Object { $_.id -eq $lesson.id })
if ($publishedLesson.Count -eq 0) {
    throw "The newly published demo lesson was not returned by the learner catalog."
}
$package = Invoke-TildashRequest -Method "GET" -Path ("/api/v1/learning/lessons/" + $lesson.id) -Headers $learner
if (@($package.exercises | Where-Object { $_.id -eq "exercise-1" }).Count -eq 0) {
    throw "The published lesson package does not contain the expected demo exercise."
}

Write-Host ""
Write-Host ("Ready to explore: " + $demoTitle)
Write-Host "The backend stays open in its own PowerShell window."
Write-Host "Starting the Desktop app. In the UI: Download -> Open -> enter a wrong answer -> retry with merhaba."
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
