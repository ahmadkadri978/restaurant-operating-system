# ADMIN Restaurant Setup Feature - Implementierungsdokumentation

## Überblick

Dieses Feature ermöglicht es ADMIN-Benutzern, ein vollständiges Restaurant-Setup in einer einzigen API-Anfrage zu erstellen.

## Architektur

### Neue ADMIN-Rolle

- **Systemweit**: ADMIN-Benutzer sind nicht an ein bestimmtes Restaurant gebunden
- **User.restaurant = null**: Für ADMIN-Benutzer ist das Restaurant-Feld leer
- **JWT**: Token enthält `restaurantId: null` für ADMIN-Benutzer

### Komponenten

```
┌─────────────────────────────────────────────────────────────┐
│                    AdminRestaurantController                │
│                    /api/v1/admin/restaurants/setup          │
│                    @PreAuthorize("hasRole('ADMIN')")        │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   RestaurantSetupService                    │
│                   @Transactional                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 1. Validate Uniqueness Constraints                    │  │
│  │ 2. Create Restaurant                                  │  │
│  │ 3. Create Categories + Menu Items                     │  │
│  │ 4. Create Tables (QR Tokens auto-generated)           │  │
│  │ 5. Create Users (Password hashed)                     │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## API Endpoint

### POST /api/v1/admin/restaurants/setup

**Authentifizierung**: JWT Bearer Token mit ADMIN-Rolle

**Vollständiges Request-Beispiel**:
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
          "description": "Tomato, Mozzarella, Basil",
          "price": 12.50,
          "imageUrl": "/uploads/menu/margherita.jpg",
          "displayOrder": 1,
          "isAvailable": true
        },
        {
          "name": "Pepperoni",
          "description": "Tomato, Mozzarella, Pepperoni",
          "price": 14.00,
          "imageUrl": "/uploads/menu/pepperoni.jpg",
          "displayOrder": 2,
          "isAvailable": true
        },
        {
          "name": "Quattro Formaggi",
          "description": "Four cheese blend - coming soon",
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
          "description": "Egg, Pancetta, Parmesan",
          "price": 11.00,
          "imageUrl": "/uploads/menu/carbonara.jpg",
          "displayOrder": 1,
          "isAvailable": true
        }
      ]
    },
    {
      "name": "Drinks",
      "displayOrder": 3,
      "items": [
        {
          "name": "Cola",
          "description": null,
          "price": 3.00,
          "imageUrl": null,
          "displayOrder": 1,
          "isAvailable": true
        },
        {
          "name": "Wasser",
          "description": "Still or sparkling",
          "price": 2.50,
          "imageUrl": null,
          "displayOrder": 2,
          "isAvailable": true
        }
      ]
    }
  ],
  "tables": [
    { "tableNumber": 1, "label": null },
    { "tableNumber": 2, "label": "Window Seat" },
    { "tableNumber": 3, "label": "Terrace 1" },
    { "tableNumber": 4, "label": "Terrace 2" },
    { "tableNumber": 5, "label": null }
  ],
  "users": {
    "owner": {
      "username": "bella_owner",
      "password": "securePass123"
    },
    "manager": {
      "username": "bella_manager",
      "password": "securePass123"
    },
    "staff": {
      "username": "bella_staff",
      "password": "securePass123"
    },
    "kitchen": {
      "username": "bella_kitchen",
      "password": "securePass123"
    }
  }
}
```

**Response (201 Created)**:
```json
{
  "restaurantId": 5,
  "restaurantName": "Bella Italia",
  "restaurantCode": "BELLA-001",
  "createdCategories": 3,
  "createdMenuItems": 6,
  "createdTables": [
    {
      "tableId": 25,
      "tableNumber": 1,
      "qrToken": "BELLA-001-T1-550e8400-e29b-41d4-a716-446655440000"
    },
    {
      "tableId": 26,
      "tableNumber": 2,
      "qrToken": "BELLA-001-T2-660e8500-f39c-52e5-b817-557766551111"
    },
    {
      "tableId": 27,
      "tableNumber": 3,
      "qrToken": "BELLA-001-T3-770e8600-g49d-63f6-c918-668877662222"
    },
    {
      "tableId": 28,
      "tableNumber": 4,
      "qrToken": "BELLA-001-T4-880e8700-h59e-74g7-d019-779988773333"
    },
    {
      "tableId": 29,
      "tableNumber": 5,
      "qrToken": "BELLA-001-T5-990e8800-i69f-85h8-e120-880099884444"
    }
  ],
  "createdUsers": [
    { "userId": 10, "username": "bella_owner", "role": "OWNER" },
    { "userId": 11, "username": "bella_manager", "role": "MANAGER" },
    { "userId": 12, "username": "bella_staff", "role": "STAFF" },
    { "userId": 13, "username": "bella_kitchen", "role": "KITCHEN" }
  ],
  "createdAt": "2026-03-15T10:30:00Z"
}
```

## DTO-Struktur (kompatibel mit Entities)

### CreateRestaurantSetupRequest
| Feld | Typ | Erforderlich | Entity-Mapping |
|------|-----|--------------|----------------|
| `restaurantName` | String | ✅ | Restaurant.name |
| `restaurantCode` | String | ✅ | Restaurant.code |
| `categories` | List | ✅ (min 1) | MenuCategory |
| `tables` | List | ✅ (min 1) | RestaurantTable |
| `users` | Object | ✅ | User |

### CreateCategoryRequest
| Feld | Typ | Erforderlich | Entity-Mapping |
|------|-----|--------------|----------------|
| `name` | String | ✅ | MenuCategory.name |
| `displayOrder` | Integer | ❌ | MenuCategory.displayOrder |
| `items` | List | ❌ | MenuItem[] |

### CreateMenuItemRequest
| Feld | Typ | Erforderlich | Entity-Mapping |
|------|-----|--------------|----------------|
| `name` | String | ✅ | MenuItem.name |
| `description` | String | ❌ | MenuItem.description |
| `price` | BigDecimal | ✅ | MenuItem.price |
| `imageUrl` | String | ❌ | MenuItem.imageUrl |
| `displayOrder` | Integer | ❌ | MenuItem.displayOrder |
| `isAvailable` | Boolean | ❌ (default: true) | MenuItem.isAvailable |

### CreateTableRequest
| Feld | Typ | Erforderlich | Entity-Mapping |
|------|-----|--------------|----------------|
| `tableNumber` | Integer | ✅ | RestaurantTable.tableNumber |
| `label` | String | ❌ | (für zukünftige Erweiterung) |

### CreateUsersRequest
| Feld | Typ | Erforderlich | Entity-Mapping |
|------|-----|--------------|----------------|
| `owner` | Object | ❌ | User (Role.OWNER) |
| `manager` | Object | ✅ | User (Role.MANAGER) |
| `staff` | Object | ✅ | User (Role.STAFF) |
| `kitchen` | Object | ✅ | User (Role.KITCHEN) |

### CreateUserRequest
| Feld | Typ | Erforderlich | Entity-Mapping |
|------|-----|--------------|----------------|
| `username` | String | ✅ | User.username |
| `password` | String | ✅ | User.passwordHash (BCrypt) |

## Auto-generierte Felder

Diese Felder werden automatisch vom Service gesetzt:

| Entity | Feld | Wert |
|--------|------|------|
| Restaurant | isActive | true |
| Restaurant | createdAt | Instant.now() |
| Restaurant | updatedAt | Instant.now() |
| MenuCategory | isActive | true |
| MenuCategory | createdAt | Instant.now() |
| MenuCategory | updatedAt | Instant.now() |
| MenuItem | isActive | true |
| MenuItem | createdAt | Instant.now() |
| MenuItem | updatedAt | Instant.now() |
| RestaurantTable | qrToken | Auto-generiert (Format: CODE-T#-UUID) |
| RestaurantTable | isActive | true |
| RestaurantTable | createdAt | Instant.now() |
| RestaurantTable | updatedAt | Instant.now() |
| User | isActive | true |
| User | createdAt | Instant.now() |
| User | updatedAt | Instant.now() |

## Validierungsregeln

| Feld | Regel |
|------|-------|
| `restaurantName` | 2-100 Zeichen, nicht leer |
| `restaurantCode` | 2-20 Zeichen, nur `[A-Z0-9_-]`, **global eindeutig** |
| `categories` | Min. 1, max. 50 |
| `items` | Max. 100 pro Kategorie |
| `tables` | Min. 1, max. 100 |
| `tableNumber` | Min. 1, **eindeutig im Request** |
| `username` | 3-50 Zeichen, nur `[a-z0-9_]`, **global eindeutig** |
| `password` | 6-100 Zeichen |
| `price` | Min. 0.01, max. 8 Vorkommastellen, 2 Nachkommastellen |
| `label` | Max. 50 Zeichen |
| `description` | Max. 1000 Zeichen |
| `imageUrl` | Max. 500 Zeichen |

## Fehlerbehandlung

| Fehler | HTTP Status | Error Code |
|--------|-------------|------------|
| Restaurant-Code existiert | 400 | BUSINESS_RULE_VIOLATION |
| Username existiert | 400 | BUSINESS_RULE_VIOLATION |
| Doppelte Tischnummer | 400 | BUSINESS_RULE_VIOLATION |
| Doppelter Username im Request | 400 | BUSINESS_RULE_VIOLATION |
| Validierungsfehler | 400 | VALIDATION_ERROR |
| Nicht authentifiziert | 401 | UNAUTHORIZED |
| Keine ADMIN-Rolle | 403 | FORBIDDEN |

## Transaktion

- **Atomare Operation**: Alle Entitäten werden in einer einzigen Transaktion erstellt
- **Rollback**: Bei jedem Fehler wird die gesamte Transaktion zurückgerollt
- **Konsistenz**: Keine Teilzustände möglich

## Standard-Admin-Benutzer

- **Username**: `admin`
- **Password**: `admin123`
- **Erstellt durch**: DataSeeder1 (bei app.demo.seed=true) oder V13 Migration



