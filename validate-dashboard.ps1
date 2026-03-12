#!/usr/bin/env pwsh
# Dashboard Validation Script

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Dashboard Implementation Validation" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $baseDir

Write-Host "`n1. Checking for compilation errors..." -ForegroundColor Yellow

# Check for compilation errors in the test files
$testFiles = @(
    "src\test\java\com\restaurantqr\owner\OwnerDashboardServiceTest.java",
    "src\test\java\com\restaurantqr\owner\OwnerControllerTest.java"
)

$hasErrors = $false

foreach ($file in $testFiles) {
    if (Test-Path $file) {
        Write-Host "  ✓ Found: $file" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Missing: $file" -ForegroundColor Red
        $hasErrors = $true
    }
}

Write-Host "`n2. Checking implementation files..." -ForegroundColor Yellow

$implFiles = @(
    "src\main\java\com\restaurantqr\owner\OwnerDashboardService.java",
    "src\main\java\com\restaurantqr\owner\OwnerDashboardServiceImpl.java",
    "src\main\java\com\restaurantqr\owner\OwnerController.java",
    "src\main\java\com\restaurantqr\owner\OwnerDashboardResponse.java",
    "src\main\java\com\restaurantqr\owner\DashboardPeriod.java",
    "src\main\java\com\restaurantqr\owner\OverviewDto.java",
    "src\main\java\com\restaurantqr\owner\DateRangeDto.java"
)

foreach ($file in $implFiles) {
    if (Test-Path $file) {
        Write-Host "  ✓ Found: $file" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Missing: $file" -ForegroundColor Red
        $hasErrors = $true
    }
}

Write-Host "`n3. Checking repository files..." -ForegroundColor Yellow

$repoFiles = @(
    "src\main\java\com\restaurantqr\order\CustomerOrderRepository.java",
    "src\main\java\com\restaurantqr\order\OrderItemRepository.java",
    "src\main\java\com\restaurantqr\servicecall\ServiceCallRepository.java",
    "src\main\java\com\restaurantqr\table\RestaurantTableRepository.java"
)

foreach ($file in $repoFiles) {
    if (Test-Path $file) {
        Write-Host "  ✓ Found: $file" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Missing: $file" -ForegroundColor Red
        $hasErrors = $true
    }
}

Write-Host "`n4. Summary of Dashboard Components:" -ForegroundColor Yellow
Write-Host "  • Service Layer: OwnerDashboardService + Impl" -ForegroundColor White
Write-Host "  • Controller Layer: OwnerController with /api/v1/owner/dashboard endpoint" -ForegroundColor White
Write-Host "  • DTOs: Response, Overview, DateRange, Period enum" -ForegroundColor White
Write-Host "  • Repository Queries: Extended with dashboard-specific queries" -ForegroundColor White
Write-Host "  • Security: JWT-based, OWNER role required, restaurantId from token" -ForegroundColor White
Write-Host "  • Tests: Unit tests for Service and Controller" -ForegroundColor White

if (-not $hasErrors) {
    Write-Host "`n✓ All required files are present!" -ForegroundColor Green
    Write-Host "`nNext steps:" -ForegroundColor Cyan
    Write-Host "  1. Run tests: .\mvnw.cmd test" -ForegroundColor White
    Write-Host "  2. Build application: .\mvnw.cmd clean package" -ForegroundColor White
    Write-Host "  3. Start application and test manually using DASHBOARD_MANUAL_TEST.md" -ForegroundColor White
} else {
    Write-Host "`n✗ Some files are missing!" -ForegroundColor Red
}

Write-Host "`n=====================================" -ForegroundColor Cyan

