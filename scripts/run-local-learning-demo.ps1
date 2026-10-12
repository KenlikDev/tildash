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

$alreadyRunning = $false
try {
    $null = Invoke-RestMethod -Uri ($BaseUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 2
    $alreadyRunning = $true
} catch {
    # A refused connection is expected before the local backend starts.
}
if ($alreadyRunning) {
    throw "A server is already responding at $BaseUrl. Stop it first so this script can start the backend with local development identity enabled."
}

# Use isolated, documented local-development credentials for this demo process only.
$env:TILDASH_DATABASE_NAME = "tildash"
$env:TILDASH_DATABASE_USERNAME = "tildash"
$env:TILDASH_DATABASE_PASSWORD = "tildash-dev"
$env:TILDASH_DATABASE_PORT = "5432"
$env:TILDASH_DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/tildash"

Write-Host "Starting the local PostgreSQL service..."
& docker compose -f $composeFile up -d postgres
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose could not start PostgreSQL. Check Docker Desktop and port 5432."
}

$databaseReady = $false
for ($attempt = 1; $attempt -le 45; $attempt++) {
    & docker compose -f $composeFile exec -T postgres pg_isready -U tildash -d tildash *> $null
    if ($LASTEXITCODE -eq 0) {
        $databaseReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $databaseReady) {
    throw "PostgreSQL did not become ready. Inspect Docker Desktop and the postgres container logs."
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
Write-Host "Starting the backend in a separate PowerShell window..."
Start-Process -FilePath $windowsPowerShell -WorkingDirectory $repoRoot -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-EncodedCommand", $encodedCommand
)

$serverReady = $false
for ($attempt = 1; $attempt -le 180; $attempt++) {
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
try {
    & ".\gradlew.bat" ":app:desktopApp:run" "--no-configuration-cache"
    if ($LASTEXITCODE -ne 0) {
        throw "The Desktop application did not exit successfully. Review the Gradle output above."
    }
} finally {
    Pop-Location
}

Write-Host "Acceptance run finished. Close the backend window when done; stop PostgreSQL with 'docker compose stop postgres' if desired."
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], "Process")
    }
}
