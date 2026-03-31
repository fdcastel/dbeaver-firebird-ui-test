<#
.SYNOPSIS
    Resets the Firebird test database to a known state.
.DESCRIPTION
    Drops and recreates the UI test database, then applies fixture SQL.
#>
[CmdletBinding()]
param(
    [string]$PropertiesFile = "$PSScriptRoot\..\ui-test.local.properties"
)

$ErrorActionPreference = "Stop"

# Load properties
if (-not (Test-Path $PropertiesFile)) {
    Write-Error "Properties file not found: $PropertiesFile`nCopy ui-test.local.properties.example to ui-test.local.properties and edit it."
    exit 1
}

$props = @{}
Get-Content $PropertiesFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    $props[$key.Trim()] = $value.Trim()
}

$isql = $props['FIREBIRD_ISQL']
$database = $props['FIREBIRD_DATABASE']
$user = $props['FIREBIRD_USER']
$password = $props['FIREBIRD_PASSWORD']
$host = $props['FIREBIRD_HOST']
$port = $props['FIREBIRD_PORT']

if (-not $isql -or -not (Test-Path $isql)) {
    Write-Error "Firebird isql not found at: $isql"
    exit 1
}

$dbDir = Split-Path $database -Parent
if (-not (Test-Path $dbDir)) {
    New-Item -ItemType Directory -Path $dbDir -Force | Out-Null
    Write-Host "Created database directory: $dbDir"
}

# Drop existing database if it exists
if (Test-Path $database) {
    Write-Host "Dropping existing test database: $database"
    Remove-Item $database -Force
}

# Create new database
Write-Host "Creating test database: $database"
$createSql = "CREATE DATABASE '$database' USER '$user' PASSWORD '$password' DEFAULT CHARACTER SET UTF8;"
$createSql | & $isql -user $user -password $password -z 2>&1
if ($LASTEXITCODE -ne 0 -and -not (Test-Path $database)) {
    # Try alternative create method via services
    Write-Host "Trying alternative creation via localhost..."
    $createSql = "CREATE DATABASE '${host}/${port}:${database}' USER '$user' PASSWORD '$password' DEFAULT CHARACTER SET UTF8;"
    $createSql | & $isql -user $user -password $password 2>&1
}

if (-not (Test-Path $database)) {
    Write-Error "Failed to create test database at: $database"
    exit 1
}

Write-Host "Database created successfully."

# Apply fixture SQL files in order
$fixtureDir = "$PSScriptRoot\..\fixtures\sql"
$fixtures = Get-ChildItem $fixtureDir -Filter "*.sql" | Sort-Object Name

foreach ($fixture in $fixtures) {
    Write-Host "Applying fixture: $($fixture.Name)"
    & $isql -user $user -password $password "${host}/${port}:${database}" -input $fixture.FullName 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Fixture $($fixture.Name) returned exit code $LASTEXITCODE (may be non-fatal)"
    }
}

Write-Host "Database reset complete."
