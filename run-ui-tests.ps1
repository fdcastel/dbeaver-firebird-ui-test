<#
.SYNOPSIS
    Single-command entry point for the DBeaver UI test suite.
.DESCRIPTION
    Loads local config, verifies prerequisites, resets the Firebird test database,
    runs the Maven/Tycho UI test reactor, and collects artifacts.
.PARAMETER SkipDbReset
    Skip the Firebird database reset step.
.PARAMETER SkipPrereqCheck
    Skip the prerequisite verification step.
.PARAMETER TestFilter
    Run only tests matching this pattern (passed as -Dtest=...).
.PARAMETER MavenVerbose
    Enable verbose Maven output.
#>
[CmdletBinding()]
param(
    [switch]$SkipDbReset,
    [switch]$SkipPrereqCheck,
    [string]$TestFilter,
    [switch]$MavenVerbose
)

# Do NOT use $ErrorActionPreference = "Stop" globally — it causes silent deaths.
# Each section handles its own errors with try/catch and clear messages.
$scriptDir = $PSScriptRoot
$startTime = Get-Date

Write-Host "===== DBeaver UI Test Suite =====" -ForegroundColor Cyan
Write-Host "Started: $startTime"
Write-Host "Script dir: $scriptDir"
Write-Host ""

# --- Load properties ---
$propsFile = Join-Path $scriptDir "ui-test.local.properties"
if (-not (Test-Path $propsFile)) {
    Write-Host "ERROR: Local properties file not found: $propsFile" -ForegroundColor Red
    Write-Host "  Copy ui-test.local.properties.example to ui-test.local.properties and edit it." -ForegroundColor Yellow
    exit 1
}

$props = @{}
Get-Content $propsFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    $props[$key.Trim()] = $value.Trim()
}

$dbeaverRepo = $props['DBEAVER_REPO']
$dbeaverP2 = $props['DBEAVER_P2_REPO']
$artifactsDir = $props['ARTIFACTS_DIR']
if (-not $artifactsDir) { $artifactsDir = Join-Path $scriptDir "artifacts" }

# --- Verify prerequisites ---
if (-not $SkipPrereqCheck) {
    Write-Host "--- Verifying prerequisites ---" -ForegroundColor Yellow
    try {
        & "$scriptDir\scripts\verify-prerequisites.ps1" -PropertiesFile "$propsFile"
        # verify-prerequisites.ps1 calls 'exit 1' on failure but falls through on success.
        # $LASTEXITCODE is unreliable after a PS script — check $? instead.
        if (-not $?) {
            Write-Host "ERROR: Prerequisite check script failed." -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "ERROR: Prerequisite check failed: $_" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}

# --- Reset Firebird database ---
if (-not $SkipDbReset) {
    Write-Host "--- Resetting Firebird test database ---" -ForegroundColor Yellow
    try {
        & "$scriptDir\scripts\reset-firebird-db.ps1" -PropertiesFile "$propsFile"
        if (-not $?) {
            Write-Host "ERROR: Database reset script reported a failure." -ForegroundColor Red
            Write-Host "  Check that Firebird is running and no other process has the database locked." -ForegroundColor Yellow
            Write-Host "  You can skip this step with -SkipDbReset if the database already exists." -ForegroundColor Yellow
            exit 1
        }
    } catch {
        Write-Host "ERROR: Database reset failed: $_" -ForegroundColor Red
        Write-Host "  Check that Firebird is running and no other process has the database locked." -ForegroundColor Yellow
        Write-Host "  You can skip this step with -SkipDbReset if the database already exists." -ForegroundColor Yellow
        exit 1
    }
    Write-Host ""
}

# --- Prepare artifacts directory ---
if (Test-Path $artifactsDir) {
    Remove-Item "$artifactsDir\*" -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null

# --- Build P2 URL ---
if (-not $dbeaverP2 -or -not (Test-Path $dbeaverP2)) {
    Write-Host "ERROR: DBeaver P2 repository not found at: $dbeaverP2" -ForegroundColor Red
    Write-Host "  Set DBEAVER_P2_REPO in $propsFile to the local P2 repository." -ForegroundColor Yellow
    Write-Host "  Build DBeaver first: cd $dbeaverRepo && mvn verify -pl product/repositories/org.jkiss.dbeaver.ce.repository" -ForegroundColor Yellow
    exit 1
}
$p2Url = "file:///" + ($dbeaverP2 -replace '\\', '/')
Write-Host "DBeaver P2 repo: $p2Url"

# --- Verify Maven is available ---
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    Write-Host "ERROR: Maven (mvn) not found in PATH." -ForegroundColor Red
    Write-Host "  Install Maven and ensure 'mvn' is on the PATH." -ForegroundColor Yellow
    exit 1
}

# --- Run Maven ---
Write-Host ""
Write-Host "--- Running UI tests ---" -ForegroundColor Yellow

$mvnArgs = @(
    "clean", "verify",
    "-f", (Join-Path $scriptDir "pom.xml"),
    "-Ddbeaver.p2.url=$p2Url",
    "-Dfirebird.host=$($props['FIREBIRD_HOST'])",
    "-Dfirebird.port=$($props['FIREBIRD_PORT'])",
    "-Dfirebird.database=$($props['FIREBIRD_DATABASE'])",
    "-Dfirebird.user=$($props['FIREBIRD_USER'])",
    "-Dfirebird.password=$($props['FIREBIRD_PASSWORD'])"
)

if ($TestFilter) {
    $mvnArgs += "-Dtest=$TestFilter"
}

if ($MavenVerbose) {
    $mvnArgs += "-X"
}

Write-Host "mvn $($mvnArgs -join ' ')"
Write-Host ""

& mvn @mvnArgs
$mvnExitCode = $LASTEXITCODE

if ($null -eq $mvnExitCode) { $mvnExitCode = 1 }

# --- Collect artifacts ---
Write-Host ""
Write-Host "--- Collecting artifacts ---" -ForegroundColor Yellow

$surefireReports = Get-ChildItem -Path "$scriptDir\plugins\*\target\surefire-reports\TEST-*.xml" -ErrorAction SilentlyContinue
if ($surefireReports) {
    foreach ($report in $surefireReports) {
        Copy-Item $report.FullName $artifactsDir -Force
        Write-Host "  Report: $($report.Name)"
    }
} else {
    Write-Host "  No surefire reports found — tests may not have run." -ForegroundColor Yellow
    Write-Host "  Check Maven output above for errors." -ForegroundColor Yellow
}

# Copy screenshots if any
Get-ChildItem $artifactsDir -Filter "*.png" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  Screenshot: $($_.Name)"
}

# --- Summary ---
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "===== Test Run Complete =====" -ForegroundColor Cyan
Write-Host "Duration: $($duration.ToString('mm\:ss'))"
Write-Host "Artifacts: $artifactsDir"

if ($mvnExitCode -eq 0) {
    Write-Host "Result: PASSED" -ForegroundColor Green
} else {
    Write-Host "Result: FAILED (exit code $mvnExitCode)" -ForegroundColor Red
}

exit $mvnExitCode
