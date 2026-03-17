package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Service for admin operations like creating and managing restaurant setups.
 */
public interface RestaurantSetupService {

    /**
     * Creates a complete restaurant setup including:
     * - Restaurant entity
     * - Menu categories with items
     * - Tables with QR tokens
     * - Users (MANAGER, STAFF, KITCHEN)
     *
     * @param request the complete setup request
     * @return response with created entity IDs
     */
    RestaurantSetupResponse createRestaurantSetup(CreateRestaurantSetupRequest request);

    /**
     * Creates a complete restaurant setup with image uploads.
     * Images are stored in a restaurant-specific folder.
     *
     * @param request the complete setup request
     * @param itemImages map of item identifiers to their image files
     *                   Key format: "categoryIndex-itemIndex" (e.g., "0-0", "0-1", "1-0")
     * @return response with created entity IDs
     */
    RestaurantSetupResponse createRestaurantSetupWithImages(
            CreateRestaurantSetupRequest request,
            Map<String, MultipartFile> itemImages
    );

    /**
     * Lists all restaurants with optional search and pagination.
     *
     * @param search optional search term (matches name or code)
     * @param page page number (0-based)
     * @param size page size
     * @param activeOnly if true, only return active restaurants
     * @return paginated list of restaurants
     */
    RestaurantListResponse listRestaurants(String search, int page, int size, boolean activeOnly);

    /**
     * Gets complete details of a restaurant including all related data.
     *
     * @param restaurantId the restaurant ID
     * @return complete restaurant details
     */
    RestaurantDetailResponse getRestaurantDetails(Long restaurantId);

    /**
     * Updates a restaurant and all its related data.
     *
     * @param restaurantId the restaurant ID
     * @param request the update request
     * @return updated restaurant details
     */
    RestaurantDetailResponse updateRestaurant(Long restaurantId, UpdateRestaurantRequest request);

    /**
     * Updates a restaurant with image uploads.
     *
     * @param restaurantId the restaurant ID
     * @param request the update request
     * @param itemImages map of item identifiers to their image files
     * @return updated restaurant details
     */
    RestaurantDetailResponse updateRestaurantWithImages(
            Long restaurantId,
            UpdateRestaurantRequest request,
            Map<String, MultipartFile> itemImages
    );

    /**
     * Deletes a restaurant (soft delete by default).
     *
     * @param restaurantId the restaurant ID
     * @param hardDelete if true, permanently delete the restaurant and all related data
     * @return deletion result with counts
     */
    DeleteRestaurantResponse deleteRestaurant(Long restaurantId, boolean hardDelete);
}

