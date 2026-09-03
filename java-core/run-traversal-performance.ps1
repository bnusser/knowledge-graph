[CmdletBinding()]
param(
    [ValidateSet("elliptic", "paysim")]
    [string]$Dataset = "elliptic",

    [string]$ApiUrl = "http://127.0.0.1:8080",

    [string]$ApiKey = $env:APP_SECURITY_API_KEY,

    [string]$Neo4jUri = $env:TRAVERSAL_PERF_NEO4J_URI,

    [string]$Neo4jUsername = $env:TRAVERSAL_PERF_NEO4J_USERNAME,

    [string]$Neo4jPassword = $env:TRAVERSAL_PERF_NEO4J_PASSWORD,

    [string]$SeedManifest,

    [ValidateRange(200, 2147483647)]
    [int]$Requests = 240,

    [ValidateRange(10, 2147483647)]
    [int]$Warmups = 24
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = "local-dev-key"
}
if ([string]::IsNullOrWhiteSpace($Neo4jUri)) {
    $Neo4jUri = "bolt://localhost:7687"
}
if ([string]::IsNullOrWhiteSpace($Neo4jUsername)) {
    $Neo4jUsername = "neo4j"
}
if ([string]::IsNullOrWhiteSpace($Neo4jPassword)) {
    $Neo4jPassword = "local-dev-password"
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($SeedManifest)) {
    $SeedManifest = Join-Path $repositoryRoot "specs/003-graph-traversal-analysis/performance/$Dataset-seeds.csv"
}

if (-not (Test-Path -LiteralPath $SeedManifest -PathType Leaf)) {
    throw "Seed manifest not found: $SeedManifest"
}
$resolvedSeedManifest = (Resolve-Path -LiteralPath $SeedManifest).Path

$env:TRAVERSAL_PERF_NEO4J_URI = $Neo4jUri
$env:TRAVERSAL_PERF_NEO4J_USERNAME = $Neo4jUsername
$env:TRAVERSAL_PERF_NEO4J_PASSWORD = $Neo4jPassword

$mavenArguments = @(
    "verify"
    "-Dsurefire.excludedGroups="
    "-Dtest=TraversalLoggingTest"
    "-Dit.test=TraversalDatasetPerformanceIT"
    "-Dtraversal.perf.enabled=true"
    "-Dtraversal.perf.dataset=$Dataset"
    "-Dtraversal.perf.apiUrl=$ApiUrl"
    "-Dtraversal.perf.apiKey=$ApiKey"
    "-Dtraversal.perf.seedManifest=$resolvedSeedManifest"
    "-Dtraversal.perf.requests=$Requests"
    "-Dtraversal.perf.warmups=$Warmups"
)

Write-Host "Running $Dataset traversal performance validation against $ApiUrl"
Write-Host "Seed manifest: $resolvedSeedManifest"

Push-Location $PSScriptRoot
try {
    & mvn @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Traversal performance validation failed with Maven exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
