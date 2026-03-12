# Manual API Testing Guide for Owner Dashboard

## Prerequisites

Before testing, you need:
1. ✅ Application running (Spring Boot)
2. ✅ Valid JWT token with OWNER role
3. ✅ RestaurantId in JWT token

---

## Step 1: Start the Application

```bash
cd C:\Users\kadri\Desktop\orders-qr-back-front-infra\backend-repo
.\mvnw.cmd spring-boot:run
```

Wait until you see:
```
Started RestaurantQrApplication in X.XXX seconds
```

---

## Step 2: Get JWT Token

### Option A: Use existing login endpoint
```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"owner@restaurant.com\",\"password\":\"password\"}"
```

### Option B: Check database for existing owner user
Look for users with role 'OWNER' in the database.

---

## Step 3: Test Owner Dashboard API

Replace `YOUR_JWT_TOKEN` with actual token from Step 2.

### Test 1: Get Today's Dashboard (DAY period)
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=DAY" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

**Expected Response (200 OK):**
```json
{
  "restaurantId": 1,
  "restaurantName": "Test Restaurant",
  "period": "DAY",
  "dateRange": {
    "startDate": "2026-03-11T23:00:00Z",
    "endDate": "2026-03-12T23:00:00Z",
    "period": "DAY"
  },
  "overview": {
    "totalOrders": 150,
    "newOrders": 10,
    "sentToKitchenOrders": 15,
    "doneOrders": 120,
    "cancelledOrders": 5,
    "completionRate": 96.00,
    "cancellationRate": 3.33,
    "grossRevenue": 1234.56,
    "averageOrderValue": 10.29,
    "activeTablesCount": 20,
    "tablesWithOrdersCount": 15,
    "serviceCallsCount": 23
  },
  "revenueTrend": [...],
  "ordersByStatus": [...],
  "topItemsByQuantity": [...],
  "topItemsByRevenue": [...],
  "topTables": [...],
  "serviceCallsByType": [...],
  "peakHours": [...]
}
```

### Test 2: Get Weekly Dashboard
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=WEEK" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

### Test 3: Get Monthly Dashboard
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=MONTH" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

### Test 4: Get Yearly Dashboard
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=YEAR" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

### Test 5: Dashboard with Specific Date
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=DAY&anchorDate=2026-03-10" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

### Test 6: Dashboard with Different Timezone
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=DAY&zone=America/New_York" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

### Test 7: All Parameters Combined
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=WEEK&anchorDate=2026-03-12&zone=Europe/Berlin" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

---

## Error Scenarios to Test

### Test 8: No Authentication (Should return 401)
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=DAY" ^
  -H "Content-Type: application/json"
```

**Expected Response:**
```
401 Unauthorized
```

### Test 9: Invalid Timezone (Should return 400)
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=DAY&zone=Invalid/Timezone" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

**Expected Response:**
```
400 Bad Request
{
  "error": "Invalid timezone: Invalid/Timezone"
}
```

### Test 10: Invalid Period (Should return 400)
```bash
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=INVALID" ^
  -H "Authorization: Bearer YOUR_JWT_TOKEN" ^
  -H "Content-Type: application/json"
```

---

## Using PowerShell (Alternative)

If cURL doesn't work, use PowerShell:

```powershell
$token = "YOUR_JWT_TOKEN"
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# Test DAY period
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/owner/dashboard?period=DAY" `
    -Headers $headers -Method Get | ConvertTo-Json -Depth 10
```

---

## Using Swagger UI (Easiest)

1. Open browser: `http://localhost:8080/swagger-ui/index.html`
2. Find "Owner - Dashboard" section
3. Click "Authorize" button
4. Enter JWT token: `Bearer YOUR_JWT_TOKEN`
5. Click "Try it out" on `/api/v1/owner/dashboard`
6. Select period, enter optional parameters
7. Click "Execute"

---

## Verification Checklist

Test each scenario and verify:

### ✅ Success Scenarios
- [ ] DAY period returns 200 OK with data
- [ ] WEEK period returns 200 OK with data
- [ ] MONTH period returns 200 OK with data
- [ ] YEAR period returns 200 OK with data
- [ ] AnchorDate parameter works correctly
- [ ] Timezone parameter works correctly
- [ ] All parameters combined work together
- [ ] Response has all required fields
- [ ] RestaurantId matches JWT token

### ✅ Error Scenarios
- [ ] No authentication returns 401
- [ ] Invalid timezone returns 400
- [ ] Invalid period returns 400
- [ ] Missing required parameters handled

### ✅ Data Validation
- [ ] Overview metrics are calculated correctly
- [ ] Date ranges are correct for each period
- [ ] Revenue trend data present
- [ ] Top items lists present
- [ ] Service calls data present

---

## Sample Valid JWT Token Structure

The JWT should have:
```json
{
  "sub": "1",           // User ID
  "role": "ROLE_OWNER", // Must be OWNER
  "restaurantId": 1,    // Restaurant ID
  "iat": 1234567890,
  "exp": 1234567890
}
```

---

## Troubleshooting

### Problem: 401 Unauthorized
**Solution:** Check JWT token is valid and not expired

### Problem: 403 Forbidden
**Solution:** Ensure JWT has ROLE_OWNER, not ROLE_STAFF

### Problem: 400 Bad Request - Invalid timezone
**Solution:** Use valid IANA timezone IDs (Europe/Berlin, America/New_York, etc.)

### Problem: 404 Not Found
**Solution:** Check application is running and endpoint path is correct

### Problem: 500 Internal Server Error
**Solution:** Check application logs for details

---

**Ready to test!** Start with Swagger UI for easiest testing experience.

