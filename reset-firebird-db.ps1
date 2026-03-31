<# 
.Synopsis
    Creates (or recreates) the Firebird test database for UI tests.
.Description
    Drops and recreates UI_TEST_FB5.FDB with all test fixtures.
    Requires Firebird 5 isql.exe to be available.
#>
[CmdletBinding()]
param(
    [string]$FirebirdBin = "C:\Program Files\Firebird\Firebird_5_0",
    [string]$DbPath = "$PSScriptRoot\firebird\UI_TEST_FB5.FDB",
    [string]$User = "SYSDBA",
    [string]$Password = "masterkey"
)

$ErrorActionPreference = 'Stop'
$isql = Join-Path $FirebirdBin "isql.exe"

if (-not (Test-Path $isql)) {
    throw "isql.exe not found at $isql"
}

# Remove existing database
if (Test-Path $DbPath) {
    Write-Host "Removing existing database: $DbPath"
    Remove-Item $DbPath -Force
}

# Create database
$dbDir = Split-Path $DbPath -Parent
if (-not (Test-Path $dbDir)) {
    New-Item -ItemType Directory -Path $dbDir -Force | Out-Null
}

Write-Host "Creating database: $DbPath"
$createSql = "CREATE DATABASE '$DbPath' USER '$User' PASSWORD '$Password' PAGE_SIZE 16384 DEFAULT CHARACTER SET UTF8;"
echo $createSql | & $isql -quiet -user $User -password $Password 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create database"
}

# Run fixtures
$fixtureFile = Join-Path $PSScriptRoot "firebird\create-test-db.sql"
Write-Host "Running fixtures from: $fixtureFile"
& $isql -quiet -user $User -password $Password $DbPath -input $fixtureFile 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Failed to run fixtures"
}

Write-Host "Test database created successfully at: $DbPath"
