# Admin Restaurant Management Feature

## 1. Overview

### Problem Statement
Previously, the admin could only **create** new restaurants through the setup endpoint. There was no way to:
- View a list of existing restaurants
- Search for specific restaurants
- Edit restaurant data (name, code, categories, items, tables, users)
- Delete or deactivate restaurants

### Solution
This feature extends the admin functionality with full **CRUD** (Create, Read, Update, Delete) operations for restaurant management, including all related child entities.

### Key Functionality Added
- **List restaurants** with search and pagination
- **View full details** of any restaurant (categories, items, tables, users)
- **Update** restaurant data including all child entities
- **Soft delete** (deactivate) or **hard delete** (permanent removal)
- **Admin UI tab** for restaurant management in `admin-setup.html`

---

## 2. Backend Changes

### 2.1 Controllers

**File:** `AdminRestaurantController.java`

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| `GET` | `/api/v1/admin/restaurants` | List all restaurants with search & pagination |
| `GET` | `/api/v1/admin/restaurants/{id}` | Get complete restaurant details |
| `PUT` | `/api/v1/admin/restaurants/{id}` | Update restaurant (JSON body) |
| `PUT` | `/api/v1/admin/restaurants/{id}/with-images` | Update restaurant with image uploads |
| `DELETE` | `/api/v1/admin/restaurants/{id}` | Delete restaurant (soft or hard) |

**Existing endpoints (unchanged):**
| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| `POST` | `/api/v1/admin/restaurants/setup` | Create new restaurant setup |
| `POST` | `/api/v1/admin/restaurants/setup-with-images` | Create with image uploads |

---

### 2.2 Services

**Interface:** `RestaurantSetupService.java`

New methods added:

```java
// List restaurants with search and pagination
RestaurantListResponse listRestaurants(String search, int page, int size, boolean activeOnly);

// Get complete restaurant details
RestaurantDetailResponse getRestaurantDetails(Long restaurantId);

// Update restaurant and all related data
RestaurantDetailResponse updateRestaurant(Long restaurantId, UpdateRestaurantRequest request);

// Update restaurant with image uploads
RestaurantDetailResponse updateRestaurantWithImages(
    Long restaurantId, 
    UpdateRestaurantRequest request, 
    Map<String, MultipartFile> itemImages
);

// Delete restaurant (soft or hard)
DeleteRestaurantResponse deleteRestaurant(Long restaurantId, boolean hardDelete);
```

**Implementation:** `RestaurantSetupServiceImpl.java`

Key business logic:

1. **List Restaurants**
   - Supports partial search by name or code
   - Pagination with configurable page size (max 100)
   - Optional filter for active-only restaurants

2. **Get Details**
   - Loads restaurant with all related entities
   - Maps to detailed response DTOs
   - Includes inactive items for admin view

3. **Update Restaurant**
   - Updates basic info (name, code, active status)
   - Handles child entities with smart upsert logic:
     - Items **with ID** → update existing
     - Items **without ID** → create new
     - Items **not in request** → optionally delete (if `deleteOrphaned=true`)
   - Validates unique constraints (code, usernames)
   - Regenerates QR token if table number changes

4. **Delete Restaurant**
   - **Soft delete** (default): Sets `isActive=false` for all entities
   - **Hard delete**: Permanently removes all data in correct order

---

### 2.3 DTOs

**New Response DTOs:**

| DTO | Purpose |
|-----|---------|
| `RestaurantListResponse` | Paginated list wrapper |
| `RestaurantListItemResponse` | Summary item for list view |
| `RestaurantDetailResponse` | Complete restaurant with all child data |
| `CategoryDetailResponse` | Category with nested items |
| `MenuItemDetailResponse` | Menu item details |
| `TableDetailResponse` | Table details with QR token |
| `UserDetailResponse` | User info (password never exposed) |
| `DeleteRestaurantResponse` | Deletion result with counts |

**New Request DTOs:**

| DTO | Purpose |
|-----|---------|
| `UpdateRestaurantRequest` | Root update request |
| `UpdateCategoryRequest` | Category update with optional ID |
| `UpdateMenuItemRequest` | Menu item update with optional ID |
| `UpdateTableRequest` | Table update with optional ID |
| `UpdateUserRequest` | User update (password optional) |

---

### 2.4 Entities

No entity changes were required. Existing entities used:

| Entity | Relationship |
|--------|-------------|
| `Restaurant` | Root entity |
| `MenuCategory` | `@ManyToOne` → Restaurant |
| `MenuItem` | `@ManyToOne` → Restaurant, Category |
| `RestaurantTable` | `@ManyToOne` → Restaurant |
| `User` | `@ManyToOne` → Restaurant |

---

### 2.5 Repositories

**New methods added:**

**RestaurantRepository:**
```java
// Check code uniqueness excluding current restaurant
boolean existsByCodeAndIdNot(String code, Long excludeId);

// Search with pagination
Page<Restaurant> findAllWithSearch(String search, Pageable pageable);
Page<Restaurant> findActiveWithSearch(String search, Pageable pageable);
```

**MenuCategoryRepository:**
```java
List<MenuCategory> findAllByRestaurantId(Long restaurantId);
int countByRestaurantId(Long restaurantId);
void deleteByRestaurantId(Long restaurantId);
void deactivateByRestaurantId(Long restaurantId);
```

**MenuItemRepository:**
```java
List<MenuItem> findAllByCategoryId(Long categoryId);
int countByRestaurantId(Long restaurantId);
void deleteByRestaurantId(Long restaurantId);
void deactivateByRestaurantId(Long restaurantId);
void deleteByCategoryId(Long categoryId);
```

**RestaurantTableRepository:**
```java
int countByRestaurantId(Long restaurantId);
void deleteByRestaurantId(Long restaurantId);
void deactivateByRestaurantId(Long restaurantId);
```

**UserRepository:**
```java
boolean existsByUsernameAndIdNot(String username, Long excludeId);
List<User> findByRestaurantIdOrderByRoleAsc(Long restaurantId);
int countByRestaurantId(Long restaurantId);
void deleteByRestaurantId(Long restaurantId);
void deactivateByRestaurantId(Long restaurantId);
```

---

### 2.6 Validation

**Request Validation (Jakarta Validation):**

| Field | Rules |
|-------|-------|
| `restaurantName` | `@NotBlank`, 2-100 chars |
| `restaurantCode` | `@NotBlank`, 2-20 chars, `^[A-Z0-9_-]+$` |
| `categoryName` | `@NotBlank`, 2-100 chars |
| `itemName` | `@NotBlank`, 2-100 chars |
| `itemPrice` | `@NotNull`, min 0.01, max 8 integer + 2 decimal |
| `tableNumber` | `@NotNull`, min 1 |
| `username` | `@NotBlank`, 3-50 chars, `^[a-z0-9_]+$` |
| `password` | 6-100 chars (optional for updates) |

**Business Rule Validation:**

- Restaurant code must be unique (checked against DB)
- Usernames must be unique system-wide
- Table numbers must be unique within request
- Category/item/table IDs must belong to the correct restaurant
- Password required for new users

---

### 2.7 Security

**Controller-Level:**
```java
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestaurantController { ... }
```

**Access Control:**
- All endpoints require JWT authentication
- Only users with `ROLE_ADMIN` can access
- ADMIN users have `restaurant = null` (system-wide access)

**Data Protection:**
- Passwords are never returned in responses
- Passwords are hashed with BCrypt before storage

---

### 2.8 Transactions

**`@Transactional` Usage:**

```java
@Transactional(readOnly = true)
public RestaurantListResponse listRestaurants(...) { ... }

@Transactional(readOnly = true)
public RestaurantDetailResponse getRestaurantDetails(...) { ... }

@Transactional
public RestaurantDetailResponse updateRestaurant(...) { ... }

@Transactional
public DeleteRestaurantResponse deleteRestaurant(...) { ... }
```

**Why:**
- **Read operations** use `readOnly=true` for performance optimization
- **Write operations** ensure atomicity - if any part fails, entire operation rolls back
- **Delete operations** must maintain referential integrity (delete items before categories, etc.)

---

## 3. API Design

### 3.1 List Restaurants

**Endpoint:** `GET /api/v1/admin/restaurants`

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `search` | string | - | Search by name or code |
| `page` | int | 0 | Page number (0-based) |
| `size` | int | 20 | Page size (max 100) |
| `activeOnly` | boolean | false | Filter active restaurants only |

**Response:**
```json
{
  "content": [
    {
      "restaurantId": 1,
      "restaurantName": "Bella Italia",
      "restaurantCode": "BELLA-001",
      "isActive": true,
      "categoryCount": 5,
      "menuItemCount": 25,
      "tableCount": 10,
      "userCount": 4,
      "createdAt": "2026-03-15T10:30:00Z"
    }
  ],
  "totalElements": 15,
  "totalPages": 1,
  "currentPage": 0,
  "pageSize": 20
}
```

---

### 3.2 Get Restaurant Details

**Endpoint:** `GET /api/v1/admin/restaurants/{id}`

**Response:**
```json
{
  "restaurantId": 1,
  "restaurantName": "Bella Italia",
  "restaurantCode": "BELLA-001",
  "isActive": true,
  "createdAt": "2026-03-15T10:30:00Z",
  "updatedAt": "2026-03-16T14:20:00Z",
  "categories": [
    {
      "categoryId": 1,
      "name": "Pizzas",
      "displayOrder": 1,
      "isActive": true,
      "items": [
        {
          "itemId": 1,
          "name": "Margherita",
          "description": "Tomato, Mozzarella, Basil",
          "price": 12.50,
          "imageUrl": "/uploads/bella-italia-BELLA-001/margherita.jpg",
          "displayOrder": 1,
          "isAvailable": true,
          "isActive": true
        }
      ]
    }
  ],
  "tables": [
    {
      "tableId": 25,
      "tableNumber": 1,
      "label": null,
      "qrToken": "BELLA-001-T1-550e8400-e29b-41d4-a716-446655440000",
      "isActive": true
    }
  ],
  "users": [
    {
      "userId": 10,
      "username": "bella_manager",
      "role": "MANAGER",
      "isActive": true
    }
  ]
}
```

---

### 3.3 Update Restaurant

**Endpoint:** `PUT /api/v1/admin/restaurants/{id}`

**Request Body:**
```json
{
  "restaurantName": "Bella Italia Updated",
  "restaurantCode": "BELLA-001",
  "isActive": true,
  "deleteOrphaned": true,
  "categories": [
    {
      "categoryId": 1,
      "name": "Pizzas",
      "displayOrder": 1,
      "isActive": true,
      "items": [
        {
          "itemId": 1,
          "name": "Margherita",
          "price": 13.00,
          "isAvailable": true,
          "isActive": true
        },
        {
          "name": "New Pizza",
          "description": "A new pizza",
          "price": 15.00
        }
      ]
    }
  ],
  "tables": [
    {
      "tableId": 25,
      "tableNumber": 1,
      "isActive": true
    }
  ],
  "users": [
    {
      "userId": 10,
      "username": "bella_manager",
      "password": null,
      "role": "MANAGER",
      "isActive": true
    }
  ]
}
```

**Update Logic:**
- `categoryId`, `itemId`, `tableId`, `userId` present → **Update**
- ID not present → **Create new**
- `deleteOrphaned: true` → Items not in request are **deleted**
- `password: null` → Keep existing password

**Response:** Same as Get Restaurant Details

---

### 3.4 Delete Restaurant

**Endpoint:** `DELETE /api/v1/admin/restaurants/{id}?hard={boolean}`

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `hard` | boolean | false | Permanent deletion if true |

**Response:**
```json
{
  "message": "Restaurant BELLA-001 successfully deactivated",
  "restaurantId": 1,
  "restaurantCode": "BELLA-001",
  "hardDelete": false,
  "deletedCategories": 5,
  "deletedMenuItems": 25,
  "deletedTables": 10,
  "deletedUsers": 4,
  "deletedAt": "2026-03-17T09:15:00Z"
}
```

---

## 4. Frontend Changes

**File:** `admin-setup.html`

### 4.1 Tab Navigation

Added tab-based navigation:
- **Tab 1:** "إنشاء مطعم جديد" (Create New Restaurant) - existing functionality
- **Tab 2:** "إدارة المطاعم" (Manage Restaurants) - new functionality

### 4.2 Restaurant List

- Table displaying all restaurants with columns:
  - ID, Name, Code, Categories, Items, Tables, Users, Status
- Click on row to select and load details
- Visual selection indicator

### 4.3 Search Functionality

- Search input field with Enter key support
- Searches by name or code (partial match)
- Refresh button to reload list

### 4.4 Pagination

- Previous/Next buttons
- Page info display (e.g., "Page 1 of 3")
- 15 items per page

### 4.5 Edit Panel

Sub-tabs for editing different aspects:
- **Basic Info:** Name, Code, Active status
- **Categories & Items:** Add/edit/remove categories and menu items
- **Tables:** Add/edit/remove tables
- **Users:** Add/edit/remove users (password optional for existing)

**Image Upload Support:**
- Each menu item has an image upload field
- Shows existing image preview if available
- Supports jpg, png, gif, webp (max 5MB)
- Uses multipart endpoint when images are uploaded

### 4.6 Delete Action

- Confirmation modal with warning message
- Checkbox for permanent deletion option
- Cancel and Confirm buttons

### 4.7 Bug Fixes Applied

1. **Validation Scope Fix:** Category/Table validation now correctly scopes to Create tab only
   - Changed selectors from `[id^="category-"]` to `#categoriesContainer > [id^="category-"]`
   - Prevents false validation errors from Manage tab elements

2. **Image Upload in Edit:** Added image upload capability when editing menu items
   - New `handleEditImageSelect()` function
   - Uses `/with-images` endpoint when images are present
   - Preserves existing images when not replaced

---

## 5. User Flow

### 5.1 View Restaurants

1. Admin logs in and navigates to `admin-setup.html`
2. Clicks "إدارة المطاعم" (Manage Restaurants) tab
3. Restaurant list loads automatically
4. Admin can use search box to filter by name/code
5. Pagination controls navigate through pages

### 5.2 Edit Restaurant

1. Click on a restaurant row to select it
2. Edit panel opens below the list
3. Navigate between sub-tabs (Info, Categories, Tables, Users)
4. Make changes to any field
5. Click "💾 حفظ التغييرات" (Save Changes)
6. Success message appears, list refreshes

### 5.3 Add New Items

1. In Categories tab, click "➕ إضافة فئة" to add category
2. Within category, click "➕ صنف" to add menu item
3. In Tables tab, click "➕ إضافة طاولة" to add table
4. In Users tab, click "➕ إضافة مستخدم" to add user
5. Save changes to persist

### 5.4 Delete Restaurant

1. Select restaurant from list
2. Click "🗑️ حذف المطعم" (Delete Restaurant)
3. Modal appears with confirmation
4. Optionally check "حذف نهائي" for permanent deletion
5. Click "🗑️ حذف" to confirm
6. Restaurant removed/deactivated, list refreshes

---

## 6. Edge Cases & Notes

### 6.1 Validation Errors

| Error | Handling |
|-------|----------|
| Empty restaurant name | Form validation + backend 400 error |
| Duplicate restaurant code | Backend returns `BUSINESS_RULE_VIOLATION` |
| Duplicate username | Backend returns error with username |
| Invalid table number | Frontend + backend validation |
| Missing required fields | Jakarta validation returns field errors |

### 6.2 Empty Results

- Empty list shows "لا توجد مطاعم" (No restaurants)
- Search with no matches shows empty state
- Pagination hidden when single page

### 6.3 Invalid IDs

- Non-existent restaurant ID → 404 `RESOURCE_NOT_FOUND`
- Category/item ID not belonging to restaurant → 400 `BUSINESS_RULE_VIOLATION`

### 6.4 Security Restrictions

- Non-ADMIN users → 403 Forbidden
- Invalid/expired JWT → 401 Unauthorized
- All endpoints protected at controller level

### 6.5 Concurrent Edits

- Last write wins (no optimistic locking)
- Refresh recommended before editing

### 6.6 Image Handling

- Update with images uses multipart endpoint (`PUT /restaurants/{id}/with-images`)
- Existing images preserved if not replaced
- Images stored in restaurant-specific folders
- **Image Update Logic:**
  1. If new image file uploaded → stores new image and uses new URL
  2. If `imageUrl` provided in JSON (no file) → uses provided URL (allows keeping existing)
  3. If `imageUrl` is null and no file → removes image from item
- Supports: jpg, jpeg, png, gif, webp (max 5MB)

---

## 7. Future Improvements

### 7.1 Recommended

- **Optimistic Locking:** Add `@Version` to entities for concurrent edit detection
- **Audit Trail:** Log who made changes and when
- **Bulk Operations:** Delete/activate multiple restaurants at once
- **Export:** Export restaurant data to JSON/CSV

### 7.2 Nice to Have

- **Image Preview:** Show existing images in edit form
- **Drag & Drop:** Reorder categories and items
- **Duplicate Restaurant:** Clone existing restaurant as template
- **Activity History:** Show recent changes for each restaurant

### 7.3 Performance

- **Lazy Loading:** Load child entities on demand for large restaurants
- **Caching:** Cache restaurant list for frequent admin access
- **Search Indexing:** Full-text search for large datasets

---

## Appendix: File Changes Summary

### New Files Created

```
backend-repo/src/main/java/com/restaurantqr/admin/dto/
├── CategoryDetailResponse.java
├── DeleteRestaurantResponse.java
├── MenuItemDetailResponse.java
├── RestaurantDetailResponse.java
├── RestaurantListItemResponse.java
├── RestaurantListResponse.java
├── TableDetailResponse.java
├── UpdateCategoryRequest.java
├── UpdateMenuItemRequest.java
├── UpdateRestaurantRequest.java
├── UpdateTableRequest.java
├── UpdateUserRequest.java
└── UserDetailResponse.java
```

### Modified Files

```
backend-repo/src/main/java/com/restaurantqr/
├── admin/
│   ├── AdminRestaurantController.java    (added 5 endpoints)
│   ├── RestaurantSetupService.java       (added 5 methods)
│   └── RestaurantSetupServiceImpl.java   (implemented CRUD logic)
├── restaurant/
│   └── RestaurantRepository.java         (added search queries)
├── menu/
│   ├── MenuCategoryRepository.java       (added admin queries)
│   └── MenuItemRepository.java           (added admin queries)
├── table/
│   └── RestaurantTableRepository.java    (added admin queries)
└── user/
    └── UserRepository.java               (added admin queries)

frontend-repo/
└── admin-setup.html                      (added management tab)
```

---

*Documentation generated: 2026-03-17*
*Author: Backend Engineering Team*



