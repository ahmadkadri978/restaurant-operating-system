@echo off
REM Test Execution Script for OwnerController Tests

echo ========================================
echo Running OwnerController Tests
echo ========================================
echo.

cd /d "%~dp0"

echo [1/3] Running OwnerControllerCompleteTest...
echo.
call mvnw.cmd test -Dtest=OwnerControllerCompleteTest
set TEST1_RESULT=%ERRORLEVEL%
echo.

echo [2/3] Running OwnerControllerFullIntegrationTest...
echo.
call mvnw.cmd test -Dtest=OwnerControllerFullIntegrationTest
set TEST2_RESULT=%ERRORLEVEL%
echo.

echo [3/3] Running OwnerDashboardServiceTest...
echo.
call mvnw.cmd test -Dtest=OwnerDashboardServiceTest
set TEST3_RESULT=%ERRORLEVEL%
echo.

echo ========================================
echo Test Results Summary
echo ========================================
echo.

if %TEST1_RESULT%==0 (
    echo [OK] OwnerControllerCompleteTest: PASSED
) else (
    echo [FAIL] OwnerControllerCompleteTest: FAILED
)

if %TEST2_RESULT%==0 (
    echo [OK] OwnerControllerFullIntegrationTest: PASSED
) else (
    echo [FAIL] OwnerControllerFullIntegrationTest: FAILED
)

if %TEST3_RESULT%==0 (
    echo [OK] OwnerDashboardServiceTest: PASSED
) else (
    echo [FAIL] OwnerDashboardServiceTest: FAILED
)

echo.
echo ========================================

if %TEST1_RESULT%==0 if %TEST2_RESULT%==0 if %TEST3_RESULT%==0 (
    echo All tests PASSED!
    exit /b 0
) else (
    echo Some tests FAILED!
    exit /b 1
)

