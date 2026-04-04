package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.*;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.menu.MenuCategory;
import com.restaurantqr.menu.MenuCategoryRepository;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.order.RestaurantOrderCounter;
import com.restaurantqr.order.RestaurantOrderCounterRepository;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.sse.MenuItemDeletedPayload;
import com.restaurantqr.sse.MenuItemUpdatedPayload;
import com.restaurantqr.sse.SseEventType;
import com.restaurantqr.sse.SseOutboundEvent;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class RestaurantSetupServiceImpl implements RestaurantSetupService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantSetupServiceImpl.class);

    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantTableRepository tableRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService imageStorageService;
    private final RestaurantOrderCounterRepository orderCounterRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RestaurantSetupServiceImpl(
            RestaurantRepository restaurantRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository menuItemRepository,
            RestaurantTableRepository tableRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ImageStorageService imageStorageService,
            RestaurantOrderCounterRepository orderCounterRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.imageStorageService = imageStorageService;
        this.orderCounterRepository = orderCounterRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public RestaurantSetupResponse createRestaurantSetup(CreateRestaurantSetupRequest request) {
        log.info("Creating restaurant setup: {}", request.restaurantCode());

        // === 1. Validate uniqueness constraints before creating anything ===
        validateUniqueConstraints(request);

        // === 2. Create Restaurant ===
        Restaurant restaurant = createRestaurant(request);
        log.info("Created restaurant: id={}, code={}", restaurant.getId(), restaurant.getCode());

        // === 3. Create Categories and Items ===
        AtomicInteger itemCount = new AtomicInteger(0);
        int categoryCount = 0;

        if (request.categories() != null) {
            for (CreateCategoryRequest catReq : request.categories()) {
                MenuCategory category = createCategory(restaurant, catReq, categoryCount + 1);
                categoryCount++;

                if (catReq.items() != null) {
                    for (CreateMenuItemRequest itemReq : catReq.items()) {
                        createMenuItem(restaurant, category, itemReq, itemCount.incrementAndGet());
                    }
                }
            }
        }
        log.info("Created {} categories with {} items", categoryCount, itemCount.get());

        // === 4. Create Tables ===
        List<CreatedTableResponse> createdTables = createTables(restaurant, request.tables());
        log.info("Created {} tables", createdTables.size());

        // === 5. Create Users ===
        List<CreatedUserResponse> createdUsers = createUsers(restaurant, request.users());
        log.info("Created {} users", createdUsers.size());

        // === 6. Build Response ===
        return new RestaurantSetupResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCode(),
                categoryCount,
                itemCount.get(),
                createdTables,
                createdUsers,
                Instant.now()
        );
    }

    @Override
    @Transactional
    public RestaurantSetupResponse createRestaurantSetupWithImages(
            CreateRestaurantSetupRequest request,
            Map<String, MultipartFile> itemImages
    ) {
        log.info("Creating restaurant setup with images: {}", request.restaurantCode());

        // === 1. Validate uniqueness constraints before creating anything ===
        validateUniqueConstraints(request);

        // === 2. Create Restaurant ===
        Restaurant restaurant = createRestaurant(request);
        log.info("Created restaurant: id={}, code={}", restaurant.getId(), restaurant.getCode());

        // === 3. Create restaurant folder for images ===
        String restaurantFolder = imageStorageService.createRestaurantFolder(
                restaurant.getName(),
                restaurant.getCode()
        );
        log.info("Created restaurant image folder: {}", restaurantFolder);

        // === 4. Create Categories and Items with Images ===
        AtomicInteger itemCount = new AtomicInteger(0);
        int categoryCount = 0;

        if (request.categories() != null) {
            for (int catIndex = 0; catIndex < request.categories().size(); catIndex++) {
                CreateCategoryRequest catReq = request.categories().get(catIndex);
                MenuCategory category = createCategory(restaurant, catReq, categoryCount + 1);
                categoryCount++;

                if (catReq.items() != null) {
                    for (int itemIndex = 0; itemIndex < catReq.items().size(); itemIndex++) {
                        CreateMenuItemRequest itemReq = catReq.items().get(itemIndex);

                        // Check if there's an uploaded image for this item
                        String imageKey = catIndex + "-" + itemIndex;
                        MultipartFile imageFile = itemImages != null ? itemImages.get(imageKey) : null;

                        log.debug("Checking image for category={}, item={}, key={}, hasFile={}",
                                catIndex, itemIndex, imageKey, imageFile != null);

                        String imageUrl = itemReq.imageUrl(); // Use provided URL as fallback

                        // If image file was uploaded, store it and use the new URL
                        if (imageFile != null && !imageFile.isEmpty()) {
                            log.info("Storing image for item '{}': size={} bytes",
                                    itemReq.name(), imageFile.getSize());
                            imageUrl = imageStorageService.storeImage(
                                    imageFile,
                                    restaurantFolder,
                                    itemReq.name()
                            );
                            log.info("Stored image for item '{}': {}", itemReq.name(), imageUrl);
                        } else {
                            log.debug("No image file for item '{}' (key={})", itemReq.name(), imageKey);
                        }

                        createMenuItemWithImage(restaurant, category, itemReq, imageUrl, itemCount.incrementAndGet());
                    }
                }
            }
        }
        log.info("Created {} categories with {} items", categoryCount, itemCount.get());

        // === 5. Create Tables ===
        List<CreatedTableResponse> createdTables = createTables(restaurant, request.tables());
        log.info("Created {} tables", createdTables.size());

        // === 6. Create Users ===
        List<CreatedUserResponse> createdUsers = createUsers(restaurant, request.users());
        log.info("Created {} users", createdUsers.size());

        // === 7. Build Response ===
        return new RestaurantSetupResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCode(),
                categoryCount,
                itemCount.get(),
                createdTables,
                createdUsers,
                Instant.now()
        );
    }

    private void validateUniqueConstraints(CreateRestaurantSetupRequest request) {
        // Check restaurant code uniqueness
        if (restaurantRepository.existsByCode(request.restaurantCode())) {
            throw new BusinessRuleException("Restaurant code already exists: " + request.restaurantCode());
        }

        // Collect all usernames from request
        Set<String> requestUsernames = new HashSet<>();
        CreateUsersRequest users = request.users();

        // Owner is optional
        if (users.owner() != null) {
            addUsernameToSet(requestUsernames, users.owner().username(), "owner");
        }
        addUsernameToSet(requestUsernames, users.manager().username(), "manager");
        addUsernameToSet(requestUsernames, users.staff().username(), "staff");
        addUsernameToSet(requestUsernames, users.kitchen().username(), "kitchen");

        // Expected count: 3 required + 1 optional owner
        int expectedCount = 3 + (users.owner() != null ? 1 : 0);
        if (requestUsernames.size() < expectedCount) {
            throw new BusinessRuleException("All usernames must be unique within the request");
        }

        // Check usernames against database
        if (userRepository.existsByUsernameIn(requestUsernames)) {
            // Find which one exists for better error message
            for (String username : requestUsernames) {
                if (userRepository.existsByUsername(username)) {
                    throw new BusinessRuleException("Username already exists: " + username);
                }
            }
        }

        // Check for duplicate table numbers in request
        Set<Integer> tableNumbers = new HashSet<>();
        for (CreateTableRequest table : request.tables()) {
            if (!tableNumbers.add(table.tableNumber())) {
                throw new BusinessRuleException("Duplicate table number in request: " + table.tableNumber());
            }
        }
    }

    private void addUsernameToSet(Set<String> set, String username, String role) {
        if (username == null || username.isBlank()) {
            throw new BusinessRuleException("Username for " + role + " is required");
        }
        if (!set.add(username.toLowerCase())) {
            throw new BusinessRuleException("Duplicate username in request: " + username);
        }
    }

    private Restaurant createRestaurant(CreateRestaurantSetupRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.restaurantName());
        restaurant.setCode(request.restaurantCode());
        restaurant.setActive(true);
        restaurant.setCreatedAt(Instant.now());
        restaurant.setUpdatedAt(Instant.now());
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        // Initialize order counter for the new restaurant
        RestaurantOrderCounter counter = new RestaurantOrderCounter(savedRestaurant.getId(), 0);
        orderCounterRepository.save(counter);
        log.debug("Initialized order counter for restaurant: {}", savedRestaurant.getId());

        return savedRestaurant;
    }

    private MenuCategory createCategory(Restaurant restaurant, CreateCategoryRequest request, int defaultOrder) {
        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName(request.name());
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : defaultOrder);
        category.setActive(true);
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());
        return categoryRepository.save(category);
    }

    private MenuItem createMenuItem(Restaurant restaurant, MenuCategory category, CreateMenuItemRequest request, int defaultOrder) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName(request.name());
        item.setDescription(request.description());
        item.setPrice(request.price());
        item.setImageUrl(request.imageUrl());
        item.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : defaultOrder);
        item.setAvailable(request.isAvailableOrDefault());
        item.setActive(true);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return menuItemRepository.save(item);
    }

    /**
     * Creates a menu item with a custom image URL (used when image was uploaded).
     */
    private MenuItem createMenuItemWithImage(Restaurant restaurant, MenuCategory category,
                                              CreateMenuItemRequest request, String imageUrl, int defaultOrder) {
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName(request.name());
        item.setDescription(request.description());
        item.setPrice(request.price());
        item.setImageUrl(imageUrl); // Use the provided/uploaded image URL
        item.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : defaultOrder);
        item.setAvailable(request.isAvailableOrDefault());
        item.setActive(true);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return menuItemRepository.save(item);
    }

    private List<CreatedTableResponse> createTables(Restaurant restaurant, List<CreateTableRequest> tableRequests) {
        List<CreatedTableResponse> createdTables = new ArrayList<>();
        for (CreateTableRequest req : tableRequests) {
            RestaurantTable table = new RestaurantTable();
            table.setRestaurant(restaurant);
            table.setTableNumber(req.tableNumber());
            table.setQrToken(generateQrToken(restaurant.getCode(), req.tableNumber()));
            table.setActive(true);
            table.setCreatedAt(Instant.now());
            table.setUpdatedAt(Instant.now());
            RestaurantTable savedTable = tableRepository.save(table);
            
            createdTables.add(new CreatedTableResponse(
                    savedTable.getId(),
                    savedTable.getTableNumber(),
                    savedTable.getQrToken()
            ));
        }
        return createdTables;
    }

    private String generateQrToken(String restaurantCode, int tableNumber) {
        // Format: RESTAURANTCODE-T#-UUID (full UUID for uniqueness)
        // Example: BELLA-001-T1-550e8400-e29b-41d4-a716-446655440000
        return String.format("%s-T%d-%s",
                restaurantCode,
                tableNumber,
                UUID.randomUUID().toString()
        );
    }

    private List<CreatedUserResponse> createUsers(Restaurant restaurant, CreateUsersRequest usersRequest) {
        List<CreatedUserResponse> createdUsers = new ArrayList<>();

        // Create OWNER (optional)
        if (usersRequest.owner() != null) {
            User owner = createUser(restaurant, usersRequest.owner(), Role.OWNER);
            createdUsers.add(new CreatedUserResponse(owner.getId(), owner.getUsername(), Role.OWNER.name()));
        }

        // Create MANAGER
        User manager = createUser(restaurant, usersRequest.manager(), Role.MANAGER);
        createdUsers.add(new CreatedUserResponse(manager.getId(), manager.getUsername(), Role.MANAGER.name()));

        // Create STAFF
        User staff = createUser(restaurant, usersRequest.staff(), Role.STAFF);
        createdUsers.add(new CreatedUserResponse(staff.getId(), staff.getUsername(), Role.STAFF.name()));

        // Create KITCHEN
        User kitchen = createUser(restaurant, usersRequest.kitchen(), Role.KITCHEN);
        createdUsers.add(new CreatedUserResponse(kitchen.getId(), kitchen.getUsername(), Role.KITCHEN.name()));

        return createdUsers;
    }

    private User createUser(Restaurant restaurant, CreateUserRequest request, Role role) {
        User user = new User();
        user.setRestaurant(restaurant);
        user.setUsername(request.username().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    // ==================== LIST RESTAURANTS ====================

    @Override
    @Transactional(readOnly = true)
    public RestaurantListResponse listRestaurants(String search, int page, int size, boolean activeOnly) {
        log.info("Listing restaurants: search={}, page={}, size={}, activeOnly={}", search, page, size, activeOnly);

        Pageable pageable = PageRequest.of(page, size);
        Page<Restaurant> restaurantPage;

        if (activeOnly) {
            restaurantPage = restaurantRepository.findActiveWithSearch(search, pageable);
        } else {
            restaurantPage = restaurantRepository.findAllWithSearch(search, pageable);
        }

        List<RestaurantListItemResponse> items = restaurantPage.getContent().stream()
                .map(this::mapToListItem)
                .collect(Collectors.toList());

        return new RestaurantListResponse(
                items,
                restaurantPage.getTotalElements(),
                restaurantPage.getTotalPages(),
                restaurantPage.getNumber(),
                restaurantPage.getSize()
        );
    }

    private RestaurantListItemResponse mapToListItem(Restaurant restaurant) {
        return new RestaurantListItemResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCode(),
                restaurant.isActive(),
                categoryRepository.countByRestaurantId(restaurant.getId()),
                menuItemRepository.countByRestaurantId(restaurant.getId()),
                tableRepository.countByRestaurantId(restaurant.getId()),
                userRepository.countByRestaurantId(restaurant.getId()),
                restaurant.getCreatedAt()
        );
    }

    // ==================== GET RESTAURANT DETAILS ====================

    @Override
    @Transactional(readOnly = true)
    public RestaurantDetailResponse getRestaurantDetails(Long restaurantId) {
        log.info("Getting restaurant details: id={}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        return mapToDetailResponse(restaurant);
    }

    private RestaurantDetailResponse mapToDetailResponse(Restaurant restaurant) {
        // Get categories with items
        List<MenuCategory> categories = categoryRepository.findAllByRestaurantId(restaurant.getId());
        List<CategoryDetailResponse> categoryResponses = categories.stream()
                .map(this::mapCategoryToDetail)
                .collect(Collectors.toList());

        // Get tables
        List<RestaurantTable> tables = tableRepository.findByRestaurantIdOrderByTableNumberAsc(restaurant.getId());
        List<TableDetailResponse> tableResponses = tables.stream()
                .map(this::mapTableToDetail)
                .collect(Collectors.toList());

        // Get users
        List<User> users = userRepository.findByRestaurantIdOrderByRoleAsc(restaurant.getId());
        List<UserDetailResponse> userResponses = users.stream()
                .map(this::mapUserToDetail)
                .collect(Collectors.toList());

        return new RestaurantDetailResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCode(),
                restaurant.isActive(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt(),
                categoryResponses,
                tableResponses,
                userResponses
        );
    }

    private CategoryDetailResponse mapCategoryToDetail(MenuCategory category) {
        List<MenuItem> items = menuItemRepository.findAllByCategoryId(category.getId());
        List<MenuItemDetailResponse> itemResponses = items.stream()
                .map(this::mapItemToDetail)
                .collect(Collectors.toList());

        return new CategoryDetailResponse(
                category.getId(),
                category.getName(),
                category.getDisplayOrder(),
                category.isActive(),
                itemResponses
        );
    }

    private MenuItemDetailResponse mapItemToDetail(MenuItem item) {
        return new MenuItemDetailResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getImageUrl(),
                item.getDisplayOrder(),
                item.isAvailable(),
                item.isActive(),
                item.getUpdatedAt() != null ? item.getUpdatedAt().toEpochMilli() : null
        );
    }

    private TableDetailResponse mapTableToDetail(RestaurantTable table) {
        return new TableDetailResponse(
                table.getId(),
                table.getTableNumber(),
                null, // label field doesn't exist in entity
                table.getQrToken(),
                table.isActive()
        );
    }

    private UserDetailResponse mapUserToDetail(User user) {
        return new UserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isActive()
        );
    }

    // ==================== UPDATE RESTAURANT ====================

    @Override
    @Transactional
    public RestaurantDetailResponse updateRestaurant(Long restaurantId, UpdateRestaurantRequest request) {
        return updateRestaurantInternal(restaurantId, request, null);
    }

    @Override
    @Transactional
    public RestaurantDetailResponse updateRestaurantWithImages(
            Long restaurantId,
            UpdateRestaurantRequest request,
            Map<String, MultipartFile> itemImages
    ) {
        return updateRestaurantInternal(restaurantId, request, itemImages);
    }

    private RestaurantDetailResponse updateRestaurantInternal(
            Long restaurantId,
            UpdateRestaurantRequest request,
            Map<String, MultipartFile> itemImages
    ) {
        log.info("Updating restaurant: id={}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        // Validate code uniqueness if changed
        if (!restaurant.getCode().equals(request.restaurantCode())) {
            if (restaurantRepository.existsByCodeAndIdNot(request.restaurantCode(), restaurantId)) {
                throw new BusinessRuleException("Restaurant code already exists: " + request.restaurantCode());
            }
        }

        // Update basic info
        restaurant.setName(request.restaurantName());
        restaurant.setCode(request.restaurantCode());
        restaurant.setActive(request.isActiveOrDefault());
        restaurant.setUpdatedAt(Instant.now());
        restaurantRepository.save(restaurant);

        // Get or create restaurant folder for images
        String restaurantFolder = null;
        if (itemImages != null && !itemImages.isEmpty()) {
            restaurantFolder = imageStorageService.createRestaurantFolder(
                    restaurant.getName(),
                    restaurant.getCode()
            );
        }

        // Update categories and items
        if (request.categories() != null) {
            updateCategories(restaurant, request.categories(), request.shouldDeleteOrphaned(), itemImages, restaurantFolder);
        }

        // Update tables
        if (request.tables() != null) {
            updateTables(restaurant, request.tables(), request.shouldDeleteOrphaned());
        }

        // Update users
        if (request.users() != null) {
            updateUsers(restaurant, request.users(), request.shouldDeleteOrphaned());
        }

        log.info("Restaurant updated successfully: id={}", restaurantId);
        return mapToDetailResponse(restaurant);
    }

    private void updateCategories(
            Restaurant restaurant,
            List<UpdateCategoryRequest> categoryRequests,
            boolean deleteOrphaned,
            Map<String, MultipartFile> itemImages,
            String restaurantFolder
    ) {
        Set<Long> updatedCategoryIds = new HashSet<>();
        int catIndex = 0;

        for (UpdateCategoryRequest catReq : categoryRequests) {
            MenuCategory category;

            if (catReq.categoryId() != null) {
                // Update existing category
                category = categoryRepository.findById(catReq.categoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + catReq.categoryId()));

                if (!category.getRestaurant().getId().equals(restaurant.getId())) {
                    throw new BusinessRuleException("Category does not belong to this restaurant: " + catReq.categoryId());
                }

                category.setName(catReq.name());
                category.setDisplayOrder(catReq.displayOrder());
                category.setActive(catReq.isActiveOrDefault());
                category.setUpdatedAt(Instant.now());
                category = categoryRepository.save(category);
            } else {
                // Create new category
                category = new MenuCategory();
                category.setRestaurant(restaurant);
                category.setName(catReq.name());
                category.setDisplayOrder(catReq.displayOrder() != null ? catReq.displayOrder() : catIndex + 1);
                category.setActive(catReq.isActiveOrDefault());
                category.setCreatedAt(Instant.now());
                category.setUpdatedAt(Instant.now());
                category = categoryRepository.save(category);
            }

            updatedCategoryIds.add(category.getId());

            // Update items for this category
            if (catReq.items() != null) {
                updateMenuItems(restaurant, category, catReq.items(), deleteOrphaned, itemImages, restaurantFolder, catIndex);
            }

            catIndex++;
        }

        // Delete orphaned categories
        if (deleteOrphaned) {
            List<MenuCategory> allCategories = categoryRepository.findAllByRestaurantId(restaurant.getId());
            for (MenuCategory cat : allCategories) {
                if (!updatedCategoryIds.contains(cat.getId())) {
                    // Delete items first
                    menuItemRepository.deleteByCategoryId(cat.getId());
                    categoryRepository.delete(cat);
                }
            }
        }
    }

    private void updateMenuItems(
            Restaurant restaurant,
            MenuCategory category,
            List<UpdateMenuItemRequest> itemRequests,
            boolean deleteOrphaned,
            Map<String, MultipartFile> itemImages,
            String restaurantFolder,
            int catIndex
    ) {
        Set<Long> updatedItemIds = new HashSet<>();
        int itemIndex = 0;

        for (UpdateMenuItemRequest itemReq : itemRequests) {
            MenuItem item;

            // Check for image upload
            String imageKey = catIndex + "-" + itemIndex;
            MultipartFile imageFile = itemImages != null ? itemImages.get(imageKey) : null;
            
            // Determine final image URL:
            // 1. If new image uploaded -> use new stored URL
            // 2. If imageUrl provided in request -> use that (allows keeping existing or setting new URL)
            // 3. If updating existing item with no new image and no imageUrl -> keep existing
            String imageUrl = null;
            boolean hasNewImage = imageFile != null && !imageFile.isEmpty() && restaurantFolder != null;
            
            if (hasNewImage) {
                // Store new uploaded image
                imageUrl = imageStorageService.storeImage(imageFile, restaurantFolder, itemReq.name());
                log.info("Stored new image for item '{}': {}", itemReq.name(), imageUrl);
            } else {
                // Use imageUrl from request (can be null to remove, or existing URL to keep)
                imageUrl = itemReq.imageUrl();
            }

            if (itemReq.itemId() != null) {
                // Update existing item
                item = menuItemRepository.findById(itemReq.itemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + itemReq.itemId()));

                if (!item.getRestaurant().getId().equals(restaurant.getId())) {
                    throw new BusinessRuleException("Menu item does not belong to this restaurant: " + itemReq.itemId());
                }

                item.setCategory(category);
                item.setName(itemReq.name());
                item.setDescription(itemReq.description());
                item.setPrice(itemReq.price());
                // Always update imageUrl - allows setting to null (remove) or new value
                item.setImageUrl(imageUrl);
                item.setDisplayOrder(itemReq.displayOrder());
                item.setAvailable(itemReq.isAvailableOrDefault());
                item.setActive(itemReq.isActiveOrDefault());
                item.setUpdatedAt(Instant.now());
                item = menuItemRepository.save(item);
            } else {
                // Create new item
                item = new MenuItem();
                item.setRestaurant(restaurant);
                item.setCategory(category);
                item.setName(itemReq.name());
                item.setDescription(itemReq.description());
                item.setPrice(itemReq.price());
                item.setImageUrl(imageUrl);
                item.setDisplayOrder(itemReq.displayOrder() != null ? itemReq.displayOrder() : itemIndex + 1);
                item.setAvailable(itemReq.isAvailableOrDefault());
                item.setActive(itemReq.isActiveOrDefault());
                item.setCreatedAt(Instant.now());
                item.setUpdatedAt(Instant.now());
                item = menuItemRepository.save(item);
            }

            // Publish SSE event for menu item update (real-time update for customers)
            publishMenuItemUpdate(item);

            updatedItemIds.add(item.getId());
            itemIndex++;
        }

        // Delete orphaned items
        if (deleteOrphaned) {
            List<MenuItem> allItems = menuItemRepository.findAllByCategoryId(category.getId());
            for (MenuItem item : allItems) {
                if (!updatedItemIds.contains(item.getId())) {
                    // Publish SSE event for deleted item before deletion
                    publishMenuItemDeleted(restaurant.getId(), item.getId(), category.getId());
                    menuItemRepository.delete(item);
                }
            }
        }
    }

    private void updateTables(Restaurant restaurant, List<UpdateTableRequest> tableRequests, boolean deleteOrphaned) {
        Set<Long> updatedTableIds = new HashSet<>();

        // Check for duplicate table numbers in request
        Set<Integer> tableNumbers = new HashSet<>();
        for (UpdateTableRequest tableReq : tableRequests) {
            if (!tableNumbers.add(tableReq.tableNumber())) {
                throw new BusinessRuleException("Duplicate table number in request: " + tableReq.tableNumber());
            }
        }

        for (UpdateTableRequest tableReq : tableRequests) {
            RestaurantTable table;

            if (tableReq.tableId() != null) {
                // Update existing table
                table = tableRepository.findById(tableReq.tableId())
                        .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableReq.tableId()));

                if (!table.getRestaurant().getId().equals(restaurant.getId())) {
                    throw new BusinessRuleException("Table does not belong to this restaurant: " + tableReq.tableId());
                }

                // If table number changed, generate new QR token
                if (!table.getTableNumber().equals(tableReq.tableNumber())) {
                    table.setTableNumber(tableReq.tableNumber());
                    table.setQrToken(generateQrToken(restaurant.getCode(), tableReq.tableNumber()));
                }
                table.setActive(tableReq.isActiveOrDefault());
                table.setUpdatedAt(Instant.now());
                table = tableRepository.save(table);
            } else {
                // Create new table
                table = new RestaurantTable();
                table.setRestaurant(restaurant);
                table.setTableNumber(tableReq.tableNumber());
                table.setQrToken(generateQrToken(restaurant.getCode(), tableReq.tableNumber()));
                table.setActive(tableReq.isActiveOrDefault());
                table.setCreatedAt(Instant.now());
                table.setUpdatedAt(Instant.now());
                table = tableRepository.save(table);
            }

            updatedTableIds.add(table.getId());
        }

        // Delete orphaned tables
        if (deleteOrphaned) {
            List<RestaurantTable> allTables = tableRepository.findByRestaurantIdOrderByTableNumberAsc(restaurant.getId());
            for (RestaurantTable table : allTables) {
                if (!updatedTableIds.contains(table.getId())) {
                    tableRepository.delete(table);
                }
            }
        }
    }

    private void updateUsers(Restaurant restaurant, List<UpdateUserRequest> userRequests, boolean deleteOrphaned) {
        Set<Long> updatedUserIds = new HashSet<>();

        // Validate unique usernames in request
        Set<String> usernames = new HashSet<>();
        for (UpdateUserRequest userReq : userRequests) {
            String username = userReq.username().toLowerCase();
            if (!usernames.add(username)) {
                throw new BusinessRuleException("Duplicate username in request: " + username);
            }
        }

        for (UpdateUserRequest userReq : userRequests) {
            User user;
            Role role;

            try {
                role = Role.valueOf(userReq.role().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid role: " + userReq.role());
            }

            if (userReq.userId() != null) {
                // Update existing user
                user = userRepository.findById(userReq.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userReq.userId()));

                if (user.getRestaurant() == null || !user.getRestaurant().getId().equals(restaurant.getId())) {
                    throw new BusinessRuleException("User does not belong to this restaurant: " + userReq.userId());
                }

                // Check username uniqueness if changed
                String newUsername = userReq.username().toLowerCase();
                if (!user.getUsername().equals(newUsername)) {
                    if (userRepository.existsByUsernameAndIdNot(newUsername, userReq.userId())) {
                        throw new BusinessRuleException("Username already exists: " + newUsername);
                    }
                    user.setUsername(newUsername);
                }

                // Update password only if provided
                if (userReq.password() != null && !userReq.password().isBlank()) {
                    user.setPasswordHash(passwordEncoder.encode(userReq.password()));
                }

                user.setRole(role);
                user.setActive(userReq.isActiveOrDefault());
                user.setUpdatedAt(Instant.now());
                user = userRepository.save(user);
            } else {
                // Create new user
                String newUsername = userReq.username().toLowerCase();
                if (userRepository.existsByUsername(newUsername)) {
                    throw new BusinessRuleException("Username already exists: " + newUsername);
                }

                if (userReq.password() == null || userReq.password().isBlank()) {
                    throw new BusinessRuleException("Password is required for new users");
                }

                user = new User();
                user.setRestaurant(restaurant);
                user.setUsername(newUsername);
                user.setPasswordHash(passwordEncoder.encode(userReq.password()));
                user.setRole(role);
                user.setActive(userReq.isActiveOrDefault());
                user.setCreatedAt(Instant.now());
                user.setUpdatedAt(Instant.now());
                user = userRepository.save(user);
            }

            updatedUserIds.add(user.getId());
        }

        // Delete orphaned users
        if (deleteOrphaned) {
            List<User> allUsers = userRepository.findByRestaurantIdOrderByRoleAsc(restaurant.getId());
            for (User user : allUsers) {
                if (!updatedUserIds.contains(user.getId())) {
                    userRepository.delete(user);
                }
            }
        }
    }

    // ==================== DELETE RESTAURANT ====================

    @Override
    @Transactional
    public DeleteRestaurantResponse deleteRestaurant(Long restaurantId, boolean hardDelete) {
        log.info("Deleting restaurant: id={}, hardDelete={}", restaurantId, hardDelete);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        int categoryCount = categoryRepository.countByRestaurantId(restaurantId);
        int itemCount = menuItemRepository.countByRestaurantId(restaurantId);
        int tableCount = tableRepository.countByRestaurantId(restaurantId);
        int userCount = userRepository.countByRestaurantId(restaurantId);

        String message;

        if (hardDelete) {
            // Hard delete - remove everything permanently
            menuItemRepository.deleteByRestaurantId(restaurantId);
            categoryRepository.deleteByRestaurantId(restaurantId);
            tableRepository.deleteByRestaurantId(restaurantId);
            userRepository.deleteByRestaurantId(restaurantId);
            restaurantRepository.delete(restaurant);
            message = "Restaurant " + restaurant.getCode() + " permanently deleted";
        } else {
            // Soft delete - deactivate everything
            menuItemRepository.deactivateByRestaurantId(restaurantId);
            categoryRepository.deactivateByRestaurantId(restaurantId);
            tableRepository.deactivateByRestaurantId(restaurantId);
            userRepository.deactivateByRestaurantId(restaurantId);
            restaurant.setActive(false);
            restaurant.setUpdatedAt(Instant.now());
            restaurantRepository.save(restaurant);
            message = "Restaurant " + restaurant.getCode() + " successfully deactivated";
        }

        log.info("Restaurant deleted: id={}, hardDelete={}, message={}", restaurantId, hardDelete, message);

        return new DeleteRestaurantResponse(
                message,
                restaurantId,
                restaurant.getCode(),
                hardDelete,
                categoryCount,
                itemCount,
                tableCount,
                userCount,
                Instant.now()
        );
    }

    // ==================== SSE Event Publishing ====================

    /**
     * Publishes a MENU_ITEM_UPDATED event to all connected customers.
     * Called whenever a menu item is created or updated.
     * Actual SSE broadcast happens after transaction commits via @TransactionalEventListener.
     */
    private void publishMenuItemUpdate(MenuItem item) {
        try {
            MenuItemUpdatedPayload payload = MenuItemUpdatedPayload.from(
                    item.getId(),
                    item.getCategory().getId(),
                    item.getName(),
                    item.getDescription(),
                    item.getPrice(),
                    item.getImageUrl(),
                    item.getUpdatedAt() != null ? item.getUpdatedAt().toEpochMilli() : null,
                    item.isAvailable(),
                    item.isActive(),
                    item.getDisplayOrder()
            );
            eventPublisher.publishEvent(SseOutboundEvent.of(
                    SseEventType.MENU_ITEM_UPDATED,
                    item.getRestaurant().getId(),
                    payload
            ));
        } catch (Exception e) {
            // Log but don't fail the transaction - SSE is best-effort
            log.warn("Failed to publish menu item update SSE event for itemId={}: {}", 
                    item.getId(), e.getMessage());
        }
    }

    /**
     * Publishes a MENU_ITEM_DELETED event to all connected customers.
     * Called before a menu item is deleted.
     * Actual SSE broadcast happens after transaction commits via @TransactionalEventListener.
     */
    private void publishMenuItemDeleted(Long restaurantId, Long itemId, Long categoryId) {
        try {
            MenuItemDeletedPayload payload = new MenuItemDeletedPayload(itemId, categoryId);
            eventPublisher.publishEvent(SseOutboundEvent.of(
                    SseEventType.MENU_ITEM_DELETED,
                    restaurantId,
                    payload
            ));
        } catch (Exception e) {
            // Log but don't fail the transaction - SSE is best-effort
            log.warn("Failed to publish menu item deleted SSE event for itemId={}: {}", 
                    itemId, e.getMessage());
        }
    }
}







