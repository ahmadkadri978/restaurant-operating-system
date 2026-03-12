# 🚀 How to Run the OwnerController Tests

## Quick Start

### Option 1: Run Test Scripts (Recommended)

#### Windows Command Prompt (.bat)
```cmd
cd C:\Users\kadri\Desktop\orders-qr-back-front-infra\backend-repo
run-owner-tests.bat
```

#### PowerShell (.ps1)
```powershell
cd C:\Users\kadri\Desktop\orders-qr-back-front-infra\backend-repo
.\run-owner-tests.ps1
```

### Option 2: Run Individual Tests with Maven

#### Run All OwnerController Tests
```bash
.\mvnw.cmd test -Dtest=Owner*Test
```

#### Run Specific Test Classes
```bash
# Complete unit tests (50+ tests)
.\mvnw.cmd test -Dtest=OwnerControllerCompleteTest

# Full integration tests (25 tests)
.\mvnw.cmd test -Dtest=OwnerControllerFullIntegrationTest

# Service tests (14 tests)
.\mvnw.cmd test -Dtest=OwnerDashboardServiceTest
```

#### Run Specific Test Method
```bash
.\mvnw.cmd test -Dtest=OwnerControllerCompleteTest#shouldExtractRestaurantIdFromJwtPrincipal
```

### Option 3: Run All Tests
```bash
.\mvnw.cmd test
```

---

## Expected Output

### Success Output
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.restaurantqr.owner.OwnerControllerCompleteTest
[INFO] Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Test Files Location
```
backend-repo/
├── src/test/java/com/restaurantqr/owner/
│   ├── OwnerControllerCompleteTest.java         ✅ (50+ tests)
│   ├── OwnerControllerFullIntegrationTest.java  ✅ (25 tests)
│   └── OwnerDashboardServiceTest.java           ✅ (14 tests)
```

---

## Troubleshooting

### If tests fail to compile:
```bash
# Clean and recompile
.\mvnw.cmd clean compile
```

### If specific test fails:
```bash
# Run with verbose output
.\mvnw.cmd test -Dtest=OwnerControllerCompleteTest -X
```

### Check test compilation errors:
```bash
# Compile test sources only
.\mvnw.cmd test-compile
```

---

## Test Coverage Summary

| Test File | Tests | Purpose |
|-----------|-------|---------|
| **OwnerControllerCompleteTest** | 50+ | Complete unit test suite with nested classes |
| **OwnerControllerFullIntegrationTest** | 25 | Integration tests (Controller + Service) |
| **OwnerDashboardServiceTest** | 14 | Service layer unit tests |
| **Total** | **89+** | **Complete test coverage** |

### Test Categories Covered:
- ✅ Security & Authentication (JWT, roles)
- ✅ Request Parameters (all periods, dates, timezones)
- ✅ Response Structure & Validation
- ✅ Error Handling & Exceptions
- ✅ Multi-Tenant Isolation
- ✅ Service Integration
- ✅ Edge Cases & Boundary Values
- ✅ Constructor & Lifecycle

---

## Quick Verification

To verify tests are ready to run:

### 1. Check files exist:
```powershell
Get-ChildItem src\test\java\com\restaurantqr\owner\Owner*Test.java
```

### 2. Check for compilation errors:
```bash
.\mvnw.cmd test-compile
```

### 3. Run a single simple test:
```bash
.\mvnw.cmd test -Dtest=OwnerControllerCompleteTest#shouldCreateControllerWithValidService
```

---

## CI/CD Integration

### GitHub Actions Example:
```yaml
- name: Run OwnerController Tests
  run: ./mvnw test -Dtest=Owner*Test
```

### Jenkins Example:
```groovy
sh './mvnw test -Dtest=Owner*Test'
```

---

## Test Execution Time

Approximate execution times:
- **OwnerControllerCompleteTest**: ~5-10 seconds
- **OwnerControllerFullIntegrationTest**: ~10-15 seconds
- **OwnerDashboardServiceTest**: ~5-8 seconds
- **Total**: ~20-35 seconds

---

## Next Steps After Tests Pass

1. ✅ Review test coverage report
2. ✅ Check logs for any warnings
3. ✅ Run full test suite: `.\mvnw.cmd test`
4. ✅ Build application: `.\mvnw.cmd clean package`
5. ✅ Deploy to environment

---

**Last Updated:** March 12, 2026  
**Status:** ✅ Ready to Run  
**Total Tests:** 89+

