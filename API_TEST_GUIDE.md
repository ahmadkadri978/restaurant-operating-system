# API Test - Restaurant Setup

## Voraussetzungen

1. Docker Desktop muss gestartet sein
2. Im Terminal ausführen:
   ```bash
   cd local-infra
   docker-compose up -d
   ```
3. Warten bis alle Services laufen (ca. 30-60 Sekunden)

---

## TEST 1: Admin Login

**Endpoint:** `POST /api/v1/auth/login`

**URL:** `http://localhost:8080/api/v1/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Expected Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "role": "ADMIN",
  "restaurantId": null,
  "userId": 1
}
```

**Wichtig:** Speichern Sie den `token` für den nächsten Request!

---

## TEST 2: Create Restaurant Setup

**Endpoint:** `POST /api/v1/admin/restaurants/setup`

**URL:** `http://localhost:8080/api/v1/admin/restaurants/setup`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer <TOKEN_FROM_LOGIN>
```

**Vollständiger Request Body:**
```json
{
  "restaurantName": "Bella Italia",
  "restaurantCode": "BELLA-001",
  "categories": [
    {
      "name": "Pizzas",
      "displayOrder": 1,
      "items": [
        {
          "name": "Margherita",
          "description": "Tomato, Mozzarella, Fresh Basil",
          "price": 12.50,
          "imageUrl": "/uploads/menu/margherita.jpg",
          "displayOrder": 1,
          "isAvailable": true
        },
        {
          "name": "Pepperoni",
          "description": "Tomato, Mozzarella, Spicy Pepperoni",
          "price": 14.00,
          "imageUrl": "/uploads/menu/pepperoni.jpg",
          "displayOrder": 2,
          "isAvailable": true
        },
        {
          "name": "Quattro Formaggi",
          "description": "Four cheese blend - Mozzarella, Gorgonzola, Parmesan, Goat Cheese",
          "price": 15.00,
          "imageUrl": null,
          "displayOrder": 3,
          "isAvailable": false
        }
      ]
    },
    {
      "name": "Pasta",
      "displayOrder": 2,
      "items": [
        {
          "name": "Spaghetti Carbonara",
          "description": "Egg, Pancetta, Parmesan, Black Pepper",
          "price": 11.00,
          "imageUrl": "/uploads/menu/carbonara.jpg",
          "displayOrder": 1,
          "isAvailable": true
        },
        {
          "name": "Penne Arrabiata",
          "description": "Spicy tomato sauce with garlic and chili",
          "price": 10.00,
          "imageUrl": "/uploads/menu/arrabiata.jpg",
          "displayOrder": 2,
          "isAvailable": true
        }
      ]
    },
    {
      "name": "Desserts",
      "displayOrder": 3,
      "items": [
        {
          "name": "Tiramisu",
          "description": "Classic Italian coffee-flavored dessert",
          "price": 7.50,
          "imageUrl": "/uploads/menu/tiramisu.jpg",
          "displayOrder": 1,
          "isAvailable": true
        },
        {
          "name": "Panna Cotta",
          "description": "Creamy vanilla with berry sauce",
          "price": 6.50,
          "imageUrl": null,
          "displayOrder": 2,
          "isAvailable": true
        }
      ]
    },
    {
      "name": "Drinks",
      "displayOrder": 4,
      "items": [
        {
          "name": "Coca Cola",
          "description": "330ml",
          "price": 3.00,
          "imageUrl": null,
          "displayOrder": 1,
          "isAvailable": true
        },
        {
          "name": "Sparkling Water",
          "description": "San Pellegrino 500ml",
          "price": 3.50,
          "imageUrl": null,
          "displayOrder": 2,
          "isAvailable": true
        },
        {
          "name": "House Wine Red",
          "description": "Glass 175ml",
          "price": 6.00,
          "imageUrl": null,
          "displayOrder": 3,
          "isAvailable": true
        }
      ]
    }
  ],
  "tables": [
    { "tableNumber": 1, "label": "Window 1" },
    { "tableNumber": 2, "label": "Window 2" },
    { "tableNumber": 3, "label": "Center 1" },
    { "tableNumber": 4, "label": "Center 2" },
    { "tableNumber": 5, "label": "Terrace 1" },
    { "tableNumber": 6, "label": "Terrace 2" },
    { "tableNumber": 7, "label": "Bar 1" },
    { "tableNumber": 8, "label": null },
    { "tableNumber": 9, "label": null },
    { "tableNumber": 10, "label": "VIP" }
  ],
  "users": {
    "owner": {
      "username": "bella_owner",
      "password": "owner123"
    },
    "manager": {
      "username": "bella_manager",
      "password": "manager123"
    },
    "staff": {
      "username": "bella_staff",
      "password": "staff123"
    },
    "kitchen": {
      "username": "bella_kitchen",
      "password": "kitchen123"
    }
  }
}
```

**Expected Response (201 Created):**
```json
{
  "restaurantId": 2,
  "restaurantName": "Bella Italia",
  "restaurantCode": "BELLA-001",
  "createdCategories": 4,
  "createdMenuItems": 10,
  "createdTables": [
    {
      "tableId": 1,
      "tableNumber": 1,
      "qrToken": "BELLA-001-T1-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    },
    {
      "tableId": 2,
      "tableNumber": 2,
      "qrToken": "BELLA-001-T2-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    },
    ...
  ],
  "createdUsers": [
    { "userId": 2, "username": "bella_owner", "role": "OWNER" },
    { "userId": 3, "username": "bella_manager", "role": "MANAGER" },
    { "userId": 4, "username": "bella_staff", "role": "STAFF" },
    { "userId": 5, "username": "bella_kitchen", "role": "KITCHEN" }
  ],
  "createdAt": "2026-03-15T..."
}
```

---

## TEST 3: Verify Created Users Can Login

### 3a. Owner Login
```json
{
  "username": "bella_owner",
  "password": "owner123"
}
```

### 3b. Manager Login
```json
{
  "username": "bella_manager",
  "password": "manager123"
}
```

### 3c. Staff Login
```json
{
  "username": "bella_staff",
  "password": "staff123"
}
```

### 3d. Kitchen Login
```json
{
  "username": "bella_kitchen",
  "password": "kitchen123"
}
```

---

## Postman Collection Import

```json
{
  "info": {
    "name": "Restaurant QR - Admin Setup",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "1. Admin Login",
      "request": {
        "method": "POST",
        "header": [
          { "key": "Content-Type", "value": "application/json" }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"username\": \"admin\",\n  \"password\": \"admin123\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/v1/auth/login",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "v1", "auth", "login"]
        }
      }
    },
    {
      "name": "2. Create Restaurant Setup",
      "request": {
        "method": "POST",
        "header": [
          { "key": "Content-Type", "value": "application/json" },
          { "key": "Authorization", "value": "Bearer {{admin_token}}" }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"restaurantName\": \"Bella Italia\",\n  \"restaurantCode\": \"BELLA-001\",\n  \"categories\": [\n    {\n      \"name\": \"Pizzas\",\n      \"displayOrder\": 1,\n      \"items\": [\n        {\n          \"name\": \"Margherita\",\n          \"description\": \"Tomato, Mozzarella, Fresh Basil\",\n          \"price\": 12.50,\n          \"imageUrl\": \"/uploads/menu/margherita.jpg\",\n          \"displayOrder\": 1,\n          \"isAvailable\": true\n        }\n      ]\n    }\n  ],\n  \"tables\": [\n    { \"tableNumber\": 1, \"label\": \"Window 1\" },\n    { \"tableNumber\": 2, \"label\": \"Window 2\" }\n  ],\n  \"users\": {\n    \"owner\": {\n      \"username\": \"bella_owner\",\n      \"password\": \"owner123\"\n    },\n    \"manager\": {\n      \"username\": \"bella_manager\",\n      \"password\": \"manager123\"\n    },\n    \"staff\": {\n      \"username\": \"bella_staff\",\n      \"password\": \"staff123\"\n    },\n    \"kitchen\": {\n      \"username\": \"bella_kitchen\",\n      \"password\": \"kitchen123\"\n    }\n  }\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/v1/admin/restaurants/setup",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "v1", "admin", "restaurants", "setup"]
        }
      }
    }
  ]
}
```

---

## Fehlerszenarien

### 400 Bad Request - Restaurant Code existiert
```json
{
  "timestamp": "2026-03-15T...",
  "status": 400,
  "error": "Bad Request",
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Restaurant code already exists: BELLA-001",
  "path": "/api/v1/admin/restaurants/setup"
}
```

### 400 Bad Request - Username existiert
```json
{
  "timestamp": "2026-03-15T...",
  "status": 400,
  "error": "Bad Request",
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Username already exists: bella_owner",
  "path": "/api/v1/admin/restaurants/setup"
}
```

### 401 Unauthorized - Kein Token
```json
{
  "timestamp": "2026-03-15T...",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Full authentication is required",
  "path": "/api/v1/admin/restaurants/setup"
}
```

### 403 Forbidden - Keine ADMIN-Rolle
```json
{
  "timestamp": "2026-03-15T...",
  "status": 403,
  "error": "Forbidden",
  "code": "ACCESS_DENIED",
  "message": "Access Denied",
  "path": "/api/v1/admin/restaurants/setup"
}
```

---

## Entity-Verifizierung

Nach dem erfolgreichen API-Aufruf sollten folgende Entities erstellt worden sein:

| Entity | Count | Verifizierung |
|--------|-------|---------------|
| Restaurant | 1 | name="Bella Italia", code="BELLA-001" |
| MenuCategory | 4 | Pizzas, Pasta, Desserts, Drinks |
| MenuItem | 10 | Alle Items mit price, description, etc. |
| RestaurantTable | 10 | Alle mit uniquem qrToken |
| User | 4 | OWNER, MANAGER, STAFF, KITCHEN |

Alle Entities haben:
- ✅ `createdAt` gesetzt
- ✅ `updatedAt` gesetzt
- ✅ `isActive = true`
- ✅ Korrekte `restaurant_id` Foreign Keys

