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
$demoTitle = "Крымскотатарский: первые разговоры (" + (Get-Date -Format "yyyy-MM-dd HH:mm:ss") + ")"
$curriculumSource = "https://mon.gov.ua/static-objects/mon/sites/1/informatsia-dla-osib-z-tot-ta-vpo/materialy-mnp/modelni-navch-programy-na-tot/integrovanii-kurs-krimskotatarskoyi-movi-ta-literaturi.pdf"
$etiquetteSource = "https://ana-yurt.com/urok-4-rechevoy-etiket-selamlasuv-ve-miracaat-etuv"

$lessonPlans = @(
    @{
        Title = "Приветствия и вежливые ответы"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "Цель урока: научиться начинать короткий разговор и правильно отвечать на традиционное приветствие. Merhaba! — распространённое приветствие. Selâm aleyküm! — уважительная формула приветствия, на которую обычно отвечают Aleyküm selâm! Запоминайте последнюю пару вместе: приветствие и ответ — это разные реплики."; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Merhaba!"; Gloss = "Привет! / Здравствуйте!"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "Selâm aleyküm!"; Gloss = "Традиционное уважительное приветствие"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Aleyküm selâm!"; Gloss = "Ответ на «Selâm aleyküm!»"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 4; Term = "Selâm!"; Gloss = "Привет! (неформально)"; SourceLocator = $etiquetteSource }
            @{ Kind = "EXPLANATION"; Position = 5; Text = "Выбирайте приветствие по ситуации. С незнакомым человеком или старшим уместно начать с Selâm aleyküm! и дождаться ответной формулы Aleyküm selâm! С другом можно ограничиться Selâm! или Meraba! Не подменяйте ответ Aleyküm selâm! повторением той же самой первой реплики."; SourceLocator = $etiquetteSource }
            @{ Kind = "EXAMPLE"; Position = 6; Text = "Вежливый вариант: A: Selâm aleyküm! — Здравствуйте! / B: Aleyküm selâm! — Здравствуйте! / Неформальный вариант: A: Selâm! — Привет! / B: Selâm! — Привет!"; SourceLocator = $etiquetteSource }
            @{ Kind = "EXAMPLE"; Position = 7; Text = "Ситуация: вы встречаете нового соседа. A: Selâm aleyküm! / B: Aleyküm selâm! / Ситуация: вы увидели друга. A: Selâm!"; SourceLocator = $etiquetteSource }
        )
        Exercises = @(
            @{ Id = "greetings-1"; Position = 0; Prompt = "Вы хотите нейтрально поздороваться. Как сказать «Привет!» или «Здравствуйте!»?"; ExpectedAnswers = @("Merhaba", "Merhaba!") }
            @{ Id = "greetings-2"; Position = 1; Prompt = "Вы услышали «Selâm aleyküm!». Напишите правильный ответ на это приветствие."; ExpectedAnswers = @("Aleyküm selâm", "Aleyküm selâm!") }
            @{ Id = "greetings-3"; Position = 2; Prompt = "Вы встретили друга. Напишите короткое неформальное приветствие."; ExpectedAnswers = @("Selâm", "Selâm!", "Meraba", "Meraba!") }
            @{ Id = "greetings-4"; Position = 3; Prompt = "Какое приветствие обычно предшествует ответу «Aleyküm selâm!»?"; ExpectedAnswers = @("Selâm aleyküm", "Selâm aleyküm!") }
            @{ Id = "greetings-5"; Position = 4; Prompt = "Дополните диалог: A: Selâm aleyküm! B: ..."; ExpectedAnswers = @("Aleyküm selâm", "Aleyküm selâm!") }
            @{ Id = "greetings-6"; Position = 5; Prompt = "Вы знакомитесь со старшим человеком. Напишите традиционное уважительное приветствие."; ExpectedAnswers = @("Selâm aleyküm", "Selâm aleyküm!") }
        )
    }
    @{
        Title = "Приветствия в разное время суток"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "Цель урока: выбрать приветствие с учётом времени суток. Вежливые формы могут быть длиннее обычного Meraba! и буквально не переводятся слово в слово. Запомните устойчивые выражения целиком и используйте соответствующее времени суток приветствие."; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Sabahıñız hayırlı olsun!"; Gloss = "Доброго вам утра!"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "Künüñiz hayırlı olsun!"; Gloss = "Доброго вам дня!"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Aqşamıñız hayırlı olsun!"; Gloss = "Доброго вам вечера!"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 4; Term = "Hayırlı gece bolsun!"; Gloss = "Доброй ночи!"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 5; Term = "Selâm!"; Gloss = "Привет! (неформально)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 6; Term = "Meraba!"; Gloss = "Привет! / Здравствуй!"; SourceLocator = $etiquetteSource }
            @{ Kind = "EXPLANATION"; Position = 7; Text = "Сначала определите ситуацию: утро, день, вечер или прощание на ночь. Sabahıñız hayırlı olsun! относится к утру; Künüñiz hayırlı olsun! — ко дню; Aqşamıñız hayırlı olsun! — к вечеру; Hayırlı gece bolsun! — пожелание доброй ночи. Selâm! и Meraba! — короткие приветствия, которые удобно использовать с друзьями и знакомыми."; SourceLocator = $etiquetteSource }
            @{ Kind = "EXAMPLE"; Position = 8; Text = "Утром: A: Sabahıñız hayırlı olsun! — Доброго вам утра! / Днём: A: Künüñiz hayırlı olsun! — Доброго вам дня!"; SourceLocator = $etiquetteSource }
            @{ Kind = "EXAMPLE"; Position = 9; Text = "Вечером: A: Aqşamıñız hayırlı olsun! — Доброго вам вечера! / Перед сном: Hayırlı gece bolsun! — Доброй ночи!"; SourceLocator = $etiquetteSource }
        )
        Exercises = @(
            @{ Id = "daily-1"; Position = 0; Prompt = "Как вежливо пожелать доброго утра?"; ExpectedAnswers = @("Sabahıñız hayırlı olsun", "Sabahıñız hayırlı olsun!") }
            @{ Id = "daily-2"; Position = 1; Prompt = "Вы встречаете человека днём. Напишите подходящее приветствие."; ExpectedAnswers = @("Künüñiz hayırlı olsun", "Künüñiz hayırlı olsun!") }
            @{ Id = "daily-3"; Position = 2; Prompt = "Как сказать «Доброго вам вечера!»?"; ExpectedAnswers = @("Aqşamıñız hayırlı olsun", "Aqşamıñız hayırlı olsun!") }
            @{ Id = "daily-4"; Position = 3; Prompt = "Как пожелать собеседнику доброй ночи?"; ExpectedAnswers = @("Hayırlı gece bolsun", "Hayırlı gece bolsun!") }
            @{ Id = "daily-5"; Position = 4; Prompt = "Вы встретили друга днём. Напишите короткое неформальное приветствие."; ExpectedAnswers = @("Selâm", "Selâm!", "Meraba", "Meraba!") }
            @{ Id = "daily-6"; Position = 5; Prompt = "Вы прощаетесь поздно вечером и желаете доброй ночи. Какая фраза подходит?"; ExpectedAnswers = @("Hayırlı gece bolsun", "Hayırlı gece bolsun!") }
        )
    }
    @{
        Title = "Благодарность и прощание"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "Цель урока: поблагодарить человека и подобрать прощание по роли собеседника. Sağ ol — неформальное «спасибо». Sağ oluñız — вежливое «спасибо вам», а Çoq sağ oluñız — усиленная благодарность. Для прощания важно, кто уходит: уходящий говорит остающимся Sağlıqnen qalıñız; остающийся — уходящему Sağlıqnen barıñız."; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Sağ ol"; Gloss = "Спасибо / будь здоров (неформально)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "Sağ oluñız"; Gloss = "Спасибо вам (вежливо)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Çoq sağ oluñız!"; Gloss = "Большое спасибо!"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 4; Term = "Teşekkür etem."; Gloss = "Благодарю (более официально)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 5; Term = "Körüskence!"; Gloss = "До встречи! / Пока!"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 6; Term = "Sağlıqnen qalıñız."; Gloss = "До свидания (говорит уходящий остающимся)"; SourceLocator = $etiquetteSource }
            @{ Kind = "VOCABULARY"; Position = 7; Term = "Sağlıqnen barıñız."; Gloss = "До свидания (говорит остающийся уходящему)"; SourceLocator = $etiquetteSource }
            @{ Kind = "EXPLANATION"; Position = 8; Text = "Не смешивайте форму благодарности с прощанием. Если вам помогли, выберите Sağ ol, Sağ oluñız или Çoq sağ oluñız! в зависимости от того, насколько формально вы обращаетесь. Körüskence! — короткая неформальная фраза «До встречи!». Направление двух форм Sağlıqnen qalıñız / barıñız различается в зависимости от того, кто уходит."; SourceLocator = $etiquetteSource }
            @{ Kind = "EXAMPLE"; Position = 9; Text = "A: Çoq sağ oluñız! — Большое спасибо! / A: Sağlıqnen qalıñız! — До свидания! / B: Sağlıqnen barıñız! — И вам до свидания! / A: Körüskence! — До встречи!"; SourceLocator = $etiquetteSource }
        )
        Exercises = @(
            @{ Id = "farewell-1"; Position = 0; Prompt = "Как неформально сказать «Спасибо» знакомому или другу?"; ExpectedAnswers = @("Sağ ol", "Sağ ol!") }
            @{ Id = "farewell-2"; Position = 1; Prompt = "Как вежливо поблагодарить незнакомого человека?"; ExpectedAnswers = @("Sağ oluñız", "Sağ oluñız!") }
            @{ Id = "farewell-3"; Position = 2; Prompt = "Как сказать «Большое спасибо!»?"; ExpectedAnswers = @("Çoq sağ oluñız", "Çoq sağ oluñız!") }
            @{ Id = "farewell-4"; Position = 3; Prompt = "Какая фраза означает «До встречи!»?"; ExpectedAnswers = @("Körüskence", "Körüskence!") }
            @{ Id = "farewell-5"; Position = 4; Prompt = "Вы уходите, а собеседник остаётся. Какое прощание следует сказать?"; ExpectedAnswers = @("Sağlıqnen qalıñız", "Sağlıqnen qalıñız!") }
            @{ Id = "farewell-6"; Position = 5; Prompt = "Вы остаётесь, а собеседник уходит. Что вы ему скажете на прощание?"; ExpectedAnswers = @("Sağlıqnen barıñız", "Sağlıqnen barıñız!") }
        )
    }
    @{
        Title = "Знакомство и представление себя"
        Materials = @(
            @{ Kind = "EXPLANATION"; Position = 0; Text = "Цель урока: представиться, вежливо спросить имя и познакомить людей друг с другом. Menim adım ... — «Меня зовут ...». Siziñ adınız ne? — «Как Вас зовут?». Tanışqanımdan pek memnünim! — «Очень приятно познакомиться!». Tanış etmege musaade etiñiz! — «Позвольте представиться». Используйте целые выражения в подходящей социальной ситуации."; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 1; Term = "Menim adım ..."; Gloss = "Меня зовут ..."; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 2; Term = "Siziñ adınız ne?"; Gloss = "Как Вас зовут? (вежливо)"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 3; Term = "Tanış etmege musaade etiñiz!"; Gloss = "Позвольте представиться!"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 4; Term = "Tanışqanımdan pek memnünim!"; Gloss = "Очень приятно познакомиться!"; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 5; Term = "Bu — menim qomşum."; Gloss = "Это мой сосед / моя соседка."; SourceLocator = $curriculumSource }
            @{ Kind = "VOCABULARY"; Position = 6; Term = "Men de."; Gloss = "Мне тоже."; SourceLocator = $curriculumSource }
            @{ Kind = "EXPLANATION"; Position = 7; Text = "Постройте знакомство в логичном порядке: поздоровайтесь, назовите своё имя, спросите имя собеседника, затем обменяйтесь фразами вежливости. Если вы знакомите двух людей, можно добавить Bu — menim qomşum. — «Это мой сосед / моя соседка». Не забудьте, что Siziñ adınız ne? — вежливый вопрос, а имя меняется в зависимости от того, кого вы представляете."; SourceLocator = $curriculumSource }
            @{ Kind = "EXAMPLE"; Position = 8; Text = "A: Merhaba! Menim adım Ali. Siziñ adınız ne? — Здравствуйте! Меня зовут Али. Как Вас зовут? / B: Menim adım Ayshe. — Меня зовут Айше. / A: Tanışqanımdan pek memnünim! — Очень приятно познакомиться! / B: Men de. — Мне тоже."; SourceLocator = $curriculumSource }
            @{ Kind = "EXAMPLE"; Position = 9; Text = "A: Bu — menim qomşum, Ayshe. — Это моя соседка, Айше. / B: Merhaba, Ayshe! — Здравствуйте, Айше! / Ayshe: Merhaba!"; SourceLocator = $curriculumSource }
        )
        Exercises = @(
            @{ Id = "intro-1"; Position = 0; Prompt = "Как сказать «Меня зовут Али»?"; ExpectedAnswers = @("Menim adım Ali", "Menim adım Ali.") }
            @{ Id = "intro-2"; Position = 1; Prompt = "Как вежливо спросить «Как Вас зовут?»?"; ExpectedAnswers = @("Siziñ adınız ne", "Siziñ adınız ne?") }
            @{ Id = "intro-3"; Position = 2; Prompt = "Как сказать «Очень приятно познакомиться!»?"; ExpectedAnswers = @("Tanışqanımdan pek memnünim", "Tanışqanımdan pek memnünim!") }
            @{ Id = "intro-4"; Position = 3; Prompt = "Как вежливо начать знакомство словами «Позвольте представиться»?"; ExpectedAnswers = @("Tanış etmege musaade etiñiz", "Tanış etmege musaade etiñiz!") }
            @{ Id = "intro-5"; Position = 4; Prompt = "Как сказать «Это мой сосед / моя соседка»?"; ExpectedAnswers = @("Bu — menim qomşum", "Bu menim qomşum", "Bu — menim qomşum.") }
            @{ Id = "intro-6"; Position = 5; Prompt = "Составьте две реплики: «Меня зовут Айше. Как Вас зовут?»"; ExpectedAnswers = @("Menim adım Ayshe. Siziñ adınız ne?", "Menim adım Ayshe Siziñ adınız ne") }
        )
    }
)

Write-Host "Creating a four-lesson beginner course with Russian explanations, vocabulary, dialogues, and six exercises per lesson..."
$course = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
    kind = "COURSE"
    parentId = $null
    sourceLocale = "crh"
    position = 0
    payload = @{ type = "TEXT"; value = $demoTitle }
    provenance = @{
        sourceTitle = "Tildash-authored local acceptance course"
        sourceLocator = $curriculumSource
        copyrightStatus = "LICENSED"; licenseIdentifier = "Tildash Local Demo Content Terms v1"
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
            sourceTitle = "Tildash-authored local demo lesson"
            sourceLocator = $curriculumSource
            copyrightStatus = "LICENSED"; licenseIdentifier = "Tildash Local Demo Content Terms v1"
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
        $materialLocale = if ($material.Kind -eq "EXPLANATION") { "ru" } else { "crh" }

        $null = Invoke-TildashRequest -Method "POST" -Path "/api/v1/content/nodes" -Headers $teacher -Body @{
            kind = $material.Kind
            parentId = $lesson.id
            sourceLocale = $materialLocale
            position = $material.Position
            payload = $payload
            provenance = @{
                sourceTitle = "Tildash-authored bilingual teaching material with phrase references"
                sourceLocator = $material.SourceLocator
                copyrightStatus = "LICENSED"; licenseIdentifier = "Tildash Local Demo Content Terms v1"
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

Write-Host "Publishing the course and all four validated lessons..."
Publish-ContentNode -ContentId $course.id
foreach ($record in $createdLessons) {
    Publish-ContentNode -ContentId $record.Id
}

$catalog = Invoke-TildashRequest -Method "GET" -Path "/api/v1/learning/catalog" -Headers $learner
$publishedCourse = @($catalog.courses | Where-Object { $_.id -eq $course.id }) | Select-Object -First 1
if ($null -eq $publishedCourse) {
    throw "The newly published course was not returned by the learner catalog."
}
if (@($publishedCourse.lessons).Count -ne 4) {
    throw "Expected four published lessons; learner catalog returned $(@($publishedCourse.lessons).Count)."
}

foreach ($record in $createdLessons) {
    $package = Invoke-TildashRequest -Method "GET" -Path ("/api/v1/learning/lessons/" + $record.Id) -Headers $learner
    if (@($package.materials).Count -lt 8) {
        throw "Lesson '$($record.Plan.Title)' did not return its complete ordered teaching material."
    }
    if (@($package.exercises).Count -lt 6) {
        throw "Lesson '$($record.Plan.Title)' did not return all six practice exercises."
    }
}

Write-Host ""
Write-Host ("Ready to explore: " + $demoTitle)
Write-Host "The published course contains four lessons, Russian explanations, vocabulary, bilingual examples, and twenty-four exercises."
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
