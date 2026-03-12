# Run Dashboard-related tests
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Running Dashboard Tests" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan

$tests = @(
    "OwnerDashboardServiceTest",
    "OwnerControllerTest"
)

foreach ($test in $tests) {
    Write-Host "`nRunning $test..." -ForegroundColor Yellow
    & .\mvnw.cmd test -Dtest=$test

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ $test PASSED" -ForegroundColor Green
    } else {
        Write-Host "✗ $test FAILED" -ForegroundColor Red
    }
}

Write-Host "`n=====================================" -ForegroundColor Cyan
Write-Host "Dashboard Tests Complete" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan

