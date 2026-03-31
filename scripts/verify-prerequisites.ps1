<#
.SYNOPSIS
    Verifies that all prerequisites for running UI tests are met.
#>
[CmdletBinding()]
param(
    [string]$PropertiesFile = "$PSScriptRoot\..\ui-test.local.properties"
)

$ErrorActionPreference = "Stop"
$allOk = $true

function Check($description, $condition) {
    if ($condition) {
        Write-Host "  [OK] $description" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $description" -ForegroundColor Red
        $script:allOk = $false
    }
}

Write-Host "Checking prerequisites..."

# Java
$javaVersion = (java -version 2>&1 | Select-Object -First 1) -replace '.*"(\d+).*', '$1'
Check "Java 21+ installed" ($javaVersion -ge 21)

# Maven
$mvnAvailable = Get-Command mvn -ErrorAction SilentlyContinue
Check "Maven available" ($null -ne $mvnAvailable)

# Properties file
Check "ui-test.local.properties exists" (Test-Path $PropertiesFile)

if (Test-Path $PropertiesFile) {
    $props = @{}
    Get-Content $PropertiesFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
        $key, $value = $_ -split '=', 2
        $props[$key.Trim()] = $value.Trim()
    }

    # DBeaver repo
    $dbeaverRepo = $props['DBEAVER_REPO']
    Check "DBeaver repo exists at $dbeaverRepo" (Test-Path $dbeaverRepo)

    # DBeaver P2 repo (local build)
    $p2Repo = $props['DBEAVER_P2_REPO']
    Check "DBeaver local P2 repo exists at $p2Repo" ((Test-Path "$p2Repo\content.jar") -or (Test-Path "$p2Repo\content.xml.xz"))

    # Firebird isql
    $isql = $props['FIREBIRD_ISQL']
    Check "Firebird isql found at $isql" (Test-Path $isql)

    # Firebird service
    $fbService = Get-Service -Name 'FirebirdServerDefaultInstance' -ErrorAction SilentlyContinue
    if ($null -eq $fbService) {
        Check "Firebird service installed" $false
    } else {
        Check "Firebird service installed" $true
        Check "Firebird service running" ($fbService.Status -eq 'Running')
    }

    # Firebird TCP connectivity
    $fbHost = $props['FIREBIRD_HOST']
    $fbPort = [int]$props['FIREBIRD_PORT']
    $tcpOk = $false
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect($fbHost, $fbPort)
        $tcpOk = $tcp.Connected
        $tcp.Close()
    } catch { } finally { if ($tcp) { $tcp.Dispose() } }
    Check "Firebird listening on ${fbHost}:${fbPort}" $tcpOk

    # Firebird version (expect 5.x)
    if ($isql -and (Test-Path $isql)) {
        $versionLine = (& $isql -z 2>&1 | Select-Object -First 1) -as [string]
        if ($versionLine -match 'Firebird\s+(\d+)') {
            $fbMajor = $Matches[1]
            Check "Firebird version is 5.x (found $($versionLine.Trim()))" ($fbMajor -eq '5')
        } else {
            Check "Firebird version detected" $false
        }
    }
}

Write-Host ""
if ($allOk) {
    Write-Host "All prerequisites OK." -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some prerequisites are missing. Fix them before running tests." -ForegroundColor Red
    exit 1
}
