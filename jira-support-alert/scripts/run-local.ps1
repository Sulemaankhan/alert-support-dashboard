# Local dev: JDK 17+ for Spring Boot 3. Maven reads .mvn/jvm.config (TLS workaround for some networks).
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$jdk17 = "C:\Program Files\Java\jdk-17"

if (Test-Path $jdk17) {
  $env:JAVA_HOME = $jdk17
}

if (-not ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe")))) {
  Write-Host "Install JDK 17+ and set JAVA_HOME, or install to: $jdk17" -ForegroundColor Red
  exit 1
}

$ver = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Out-String
if ($ver -notmatch 'version "1[7-9]\.|version "[2-9][0-9]\.') {
  Write-Host "JAVA_HOME must be JDK 17+. Current: $env:JAVA_HOME" -ForegroundColor Red
  Write-Host $ver
  exit 1
}

Write-Host "JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan

Set-Location $repoRoot
mvn -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$uiRoot = Join-Path (Split-Path $repoRoot -Parent) "jira-support-alert-UI"
if (-not (Test-Path (Join-Path $uiRoot "package.json"))) {
  $uiRoot = "D:\ai-tool\jira-support-alert-UI"
}

Write-Host "`nStarting API: http://localhost:8081 (8080 avoided if already in use)" -ForegroundColor Green
Start-Process powershell -ArgumentList @(
  "-NoExit", "-Command",
  "`$env:JAVA_HOME='$env:JAVA_HOME'; Set-Location '$repoRoot'; mvn spring-boot:run -q"
) -WorkingDirectory $repoRoot

Start-Sleep -Seconds 10

Write-Host "Starting UI: http://127.0.0.1:5173" -ForegroundColor Green
if (-not (Test-Path (Join-Path $uiRoot "node_modules"))) {
  Set-Location $uiRoot
  npm install
}
Start-Process powershell -ArgumentList @(
  "-NoExit", "-Command",
  "Set-Location '$uiRoot'; npm run dev"
) -WorkingDirectory $uiRoot

Write-Host "`nOpen http://127.0.0.1:5173 — close the spawned PowerShell windows to stop servers.`n" -ForegroundColor Yellow
