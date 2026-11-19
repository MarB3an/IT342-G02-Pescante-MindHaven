$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $repoRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "Missing .env file in $repoRoot. Copy .env.example to .env and fill in your Supabase password and secrets first."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}

if (-not $env:JAVA_HOME) {
    $jdkCandidates = @(
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-21",
        "C:\Program Files\Java\jdk-26.0.2.1",
        "C:\Program Files\Java\latest"
    )
    foreach ($candidate in $jdkCandidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}

$mvnCmd = (Get-Command mvn -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
if (-not $mvnCmd) {
    $ideaMvn = Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter "mvn.cmd" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
    if ($ideaMvn) {
        $mvnCmd = $ideaMvn
    } else {
        Write-Error "Apache Maven (mvn) was not found on PATH or inside JetBrains IntelliJ IDEA."
        exit 1
    }
}

Write-Host "Starting MindHaven Backend using $mvnCmd (JAVA_HOME=$env:JAVA_HOME)..."
& $mvnCmd -f (Join-Path $repoRoot "backend\pom.xml") spring-boot:run
