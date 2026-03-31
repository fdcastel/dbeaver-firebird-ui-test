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

# Load properties
if (-not (Test-Path $PropertiesFile)) {
    Write-Host "ERROR: Properties file not found: $PropertiesFile" -ForegroundColor Red
    Write-Host "  Copy ui-test.local.properties.example to ui-test.local.properties and edit it." -ForegroundColor Yellow
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
$fbHost = $props['FIREBIRD_HOST']
$fbPort = $props['FIREBIRD_PORT']

if (-not $isql -or -not (Test-Path $isql)) {
    Write-Host "ERROR: Firebird isql not found at: $isql" -ForegroundColor Red
    Write-Host "  Set FIREBIRD_ISQL in your properties file to the path of isql.exe." -ForegroundColor Yellow
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
    try {
        Remove-Item $database -Force -ErrorAction Stop
    } catch {
        Write-Host "ERROR: Cannot delete database file: $database" -ForegroundColor Red
        Write-Host "  The file may be locked by a running DBeaver instance or Firebird connection." -ForegroundColor Yellow
        Write-Host "  Close DBeaver and any Firebird connections, then try again." -ForegroundColor Yellow
        exit 1
    }
}

# Create new database
Write-Host "Creating test database: $database"
$createSql = "CREATE DATABASE '$database' USER '$user' PASSWORD '$password' DEFAULT CHARACTER SET UTF8;"
$createSql | & $isql -user $user -password $password -z 2>&1
if ($LASTEXITCODE -ne 0 -and -not (Test-Path $database)) {
    # Try alternative create method via services
    Write-Host "Trying alternative creation via localhost..."
    $createSql = "CREATE DATABASE '${fbHost}/${fbPort}:${database}' USER '$user' PASSWORD '$password' DEFAULT CHARACTER SET UTF8;"
    $createSql | & $isql -user $user -password $password 2>&1
}

if (-not (Test-Path $database)) {
    Write-Host "ERROR: Failed to create test database at: $database" -ForegroundColor Red
    Write-Host "  Check that the Firebird service is running: Get-Service FirebirdServerDefaultInstance" -ForegroundColor Yellow
    Write-Host "  Check that the user/password are correct in your properties file." -ForegroundColor Yellow
    exit 1
}

Write-Host "Database created successfully."

# Apply fixture SQL files in order
$fixtureDir = "$PSScriptRoot\..\fixtures\sql"
if (-not (Test-Path $fixtureDir)) {
    Write-Host "ERROR: Fixtures directory not found: $fixtureDir" -ForegroundColor Red
    exit 1
}
$fixtures = Get-ChildItem $fixtureDir -Filter "*.sql" | Sort-Object Name

if ($fixtures.Count -eq 0) {
    Write-Host "WARNING: No SQL fixture files found in $fixtureDir" -ForegroundColor Yellow
}

foreach ($fixture in $fixtures) {
    Write-Host "Applying fixture: $($fixture.Name)"
    & $isql -user $user -password $password "${fbHost}/${fbPort}:${database}" -input $fixture.FullName 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Fixture $($fixture.Name) returned exit code $LASTEXITCODE (may be non-fatal)"
    }
}

Write-Host "Database reset complete."
exit 0
