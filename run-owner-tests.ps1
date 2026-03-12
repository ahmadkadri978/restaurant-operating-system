# Test Execution Script for OwnerController Tests (PowerShell)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running OwnerController Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$tests = @(
    @{Name="OwnerControllerCompleteTest"; Result=$null},
    @{Name="OwnerControllerFullIntegrationTest"; Result=$null},
    @{Name="OwnerDashboardServiceTest"; Result=$null}
)

$testNumber = 1
$totalTests = $tests.Count

foreach ($test in $tests) {
    Write-Host "[$testNumber/$totalTests] Running $($test.Name)..." -ForegroundColor Yellow
    Write-Host ""

    $output = & .\mvnw.cmd test "-Dtest=$($test.Name)" 2>&1
    $test.Result = $LASTEXITCODE

    if ($test.Result -eq 0) {
        Write-Host "[OK] $($test.Name): PASSED" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $($test.Name): FAILED" -ForegroundColor Red
        Write-Host "Error output:" -ForegroundColor Yellow
        $output | Select-Object -Last 20 | ForEach-Object { Write-Host $_ }
    }

    Write-Host ""
    $testNumber++
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$allPassed = $true
foreach ($test in $tests) {
    if ($test.Result -eq 0) {
        Write-Host "[OK] $($test.Name): PASSED" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $($test.Name): FAILED" -ForegroundColor Red
        $allPassed = $false
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

if ($allPassed) {
    Write-Host "All tests PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some tests FAILED!" -ForegroundColor Red
    exit 1
}

