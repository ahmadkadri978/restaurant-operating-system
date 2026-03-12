# Owner Dashboard API Manual Test Guide

## Prerequisites
1. Backend is running on http://localhost:8080
2. You have a valid JWT token with OWNER role
3. You have created a restaurant with some orders

## Setup Test Data (Using existing API endpoints)

### 1. Login as Owner
```bash
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "firebaseUid": "your-owner-firebase-uid",
  "fcmToken": "optional-fcm-token"
}
```

Expected Response: JWT token with OWNER role

### 2. Get Dashboard Data

#### Example 1: Get today's dashboard (DAY period)
```bash
GET http://localhost:8080/api/v1/owner/dashboard?period=DAY
Authorization: Bearer YOUR_JWT_TOKEN
```

#### Example 2: Get weekly dashboard
```bash
GET http://localhost:8080/api/v1/owner/dashboard?period=WEEK
Authorization: Bearer YOUR_JWT_TOKEN
```

#### Example 3: Get monthly dashboard with specific date
```bash
GET http://localhost:8080/api/v1/owner/dashboard?period=MONTH&anchorDate=2026-03-12
Authorization: Bearer YOUR_JWT_TOKEN
```

#### Example 4: Get yearly dashboard with custom timezone
```bash
GET http://localhost:8080/api/v1/owner/dashboard?period=YEAR&zone=America/New_York
Authorization: Bearer YOUR_JWT_TOKEN
```

## Expected Response Structure

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
    "inProgressOrders": 15,
    "doneOrders": 120,
    "cancelledOrders": 5,
    "completionRate": 96.00,
    "cancellationRate": 3.33,
    "grossRevenue": 1234.56,
    "averageOrderValue": 10.29,
    "totalTablesCount": 20,
    "activeTablesCount": 15,
    "serviceCallsCount": 23
  },
  "revenueTrend": [
    {
      "timestamp": "2026-03-12T00:00:00Z",
      "revenue": 125.50,
      "orderCount": 12
    }
  ],
  "ordersByStatus": [
    {
      "status": "DONE",
      "count": 120
    },
    {
      "status": "SENT_TO_KITCHEN",
      "count": 15
    }
  ],
  "topItemsByQuantity": [
    {
      "itemId": 1,
      "itemName": "Pizza Margherita",
      "quantity": 45,
      "revenue": 450.00
    }
  ],
  "topItemsByRevenue": [
    {
      "itemId": 2,
      "itemName": "Beef Steak",
      "quantity": 20,
      "revenue": 600.00
    }
  ],
  "topTables": [
    {
      "tableId": 3,
      "tableNumber": "T-003",
      "orderCount": 25,
      "revenue": 312.50
    }
  ],
  "serviceCallsByType": [
    {
      "callType": "WAITER",
      "count": 15
    },
    {
      "callType": "BILL",
      "count": 8
    }
  ],
  "peakHours": [
    {
      "hour": 19,
      "orderCount": 35,
      "revenue": 425.00
    }
  ]
}
```

## Test Cases

### Test Case 1: Verify RestaurantId Isolation
- Login as Owner 1
- Call dashboard API -> Should only see data for Restaurant 1
- Login as Owner 2  
- Call dashboard API -> Should only see data for Restaurant 2

### Test Case 2: Verify Period Calculations
- Call API with period=DAY -> Should return last 24 hours
- Call API with period=WEEK -> Should return last 7 days
- Call API with period=MONTH -> Should return last 30 days
- Call API with period=YEAR -> Should return last 12 months

### Test Case 3: Verify Timezone Handling
- Call API with zone=Europe/Berlin -> Times should be in CET/CEST
- Call API with zone=America/New_York -> Times should be in EST/EDT
- Call API with invalid zone -> Should return 400 error

### Test Case 4: Verify Security
- Call API without JWT -> Should return 401 Unauthorized
- Call API with STAFF role -> Should return 403 Forbidden
- Call API with OWNER role -> Should return 200 OK

## cURL Examples

### Get Dashboard (Copy and paste, replace YOUR_JWT_TOKEN)

```bash
# DAY period
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=DAY" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# WEEK period with specific date
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=WEEK&anchorDate=2026-03-12" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# MONTH period with custom timezone
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=MONTH&zone=America/New_York" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# YEAR period
curl -X GET "http://localhost:8080/api/v1/owner/dashboard?period=YEAR" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Troubleshooting

### Error: "Restaurant ID not found in authentication principal"
- Make sure your user account has a restaurant associated
- Check that the JWT token contains the restaurantId claim

### Error: "Invalid timezone"
- Use valid IANA timezone IDs (e.g., Europe/Berlin, America/New_York)
- See: https://en.wikipedia.org/wiki/List_of_tz_database_time_zones

### Error: 403 Forbidden
- Make sure your user has OWNER role (not STAFF)
- Check JWT token role claims

### Empty or Zero Values
- Make sure you have test data (orders, menu items, tables)
- Check that orders are in the correct date range
- Verify that orders are not all CANCELLED

