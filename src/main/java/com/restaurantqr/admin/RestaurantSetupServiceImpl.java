package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.*;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.menu.MenuCategory;
import com.restaurantqr.menu.MenuCategoryRepository;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    public RestaurantSetupServiceImpl(
            RestaurantRepository restaurantRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository menuItemRepository,
            RestaurantTableRepository tableRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ImageStorageService imageStorageService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.imageStorageService = imageStorageService;
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
        return restaurantRepository.save(restaurant);
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
}







