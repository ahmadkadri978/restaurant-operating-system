package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.CreateRestaurantSetupRequest;
import com.restaurantqr.admin.dto.RestaurantSetupResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Service for admin operations like creating complete restaurant setups.
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
}

