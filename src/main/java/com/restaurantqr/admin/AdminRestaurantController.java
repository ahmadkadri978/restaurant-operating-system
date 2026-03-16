package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.CreateRestaurantSetupRequest;
import com.restaurantqr.admin.dto.RestaurantSetupResponse;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Tag(name = "Admin - Restaurant Management", description = "Admin APIs for managing restaurants (ADMIN role required)")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestaurantController {

    private static final Logger log = LoggerFactory.getLogger(AdminRestaurantController.class);

    private final RestaurantSetupService restaurantSetupService;
    private final ObjectMapper objectMapper;

    public AdminRestaurantController(RestaurantSetupService restaurantSetupService) {
        this.restaurantSetupService = restaurantSetupService;
        // Create ObjectMapper for JSON parsing in multipart requests
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Operation(
            summary = "Create complete restaurant setup",
            description = """
                Creates a new restaurant with all required data in one transaction:
                - Restaurant basic information
                - Menu categories with items
                - Restaurant tables (QR tokens auto-generated)
                - Users (MANAGER, STAFF, KITCHEN)
                
                **Security:**
                - Requires ADMIN role
                - System-wide operation (not bound to any restaurant)
                
                **Validation:**
                - Restaurant code must be unique
                - All usernames must be unique (system-wide)
                - Table numbers must be unique within the request
                - At least one category and one table required
                
                **Transaction:**
                - All operations are atomic - if any fails, everything rolls back
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Restaurant setup created successfully",
                    content = @Content(schema = @Schema(implementation = RestaurantSetupResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error or business rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden (not ADMIN role)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping("/restaurants/setup")
    public ResponseEntity<RestaurantSetupResponse> createRestaurantSetup(
            @Valid @RequestBody CreateRestaurantSetupRequest request
    ) {
        RestaurantSetupResponse response = restaurantSetupService.createRestaurantSetup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Create complete restaurant setup with image uploads",
            description = """
                Creates a new restaurant with all required data including menu item images.
                
                **Multipart Form Data:**
                - `data`: JSON string containing the CreateRestaurantSetupRequest
                - `images[0-0]`, `images[0-1]`, etc.: Image files for menu items
                  - Key format: `images[categoryIndex-itemIndex]`
                  - Example: `images[0-0]` = first item of first category
                
                **Image Storage:**
                - Images are stored in restaurant-specific folders
                - Folder naming: `{restaurant-name}-{restaurant-code}`
                - Database stores public URL paths like `/uploads/restaurant-folder/item.jpg`
                
                **Supported formats:** JPG, JPEG, PNG, GIF, WEBP (max 5MB each)
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Restaurant setup created successfully with images",
                    content = @Content(schema = @Schema(implementation = RestaurantSetupResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error, invalid image, or business rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden (not ADMIN role)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping(value = "/restaurants/setup-with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestaurantSetupResponse> createRestaurantSetupWithImages(
            @RequestPart("data") String requestJson,
            HttpServletRequest request
    ) {
        log.info("Received restaurant setup request with images");

        try {
            // Parse JSON data
            CreateRestaurantSetupRequest setupRequest = objectMapper.readValue(
                    requestJson,
                    CreateRestaurantSetupRequest.class
            );

            // Extract all uploaded files from the multipart request
            Map<String, MultipartFile> itemImages = new HashMap<>();

            if (request instanceof MultipartHttpServletRequest multipartRequest) {
                // Iterate through all file names in the request
                Iterator<String> fileNames = multipartRequest.getFileNames();
                while (fileNames.hasNext()) {
                    String fieldName = fileNames.next();
                    MultipartFile file = multipartRequest.getFile(fieldName);

                    if (file != null && !file.isEmpty()) {
                        // Extract the key from field names like "images[0-0]" -> "0-0"
                        String imageKey;
                        if (fieldName.startsWith("images[") && fieldName.endsWith("]")) {
                            imageKey = fieldName.substring(7, fieldName.length() - 1);
                        } else if (fieldName.startsWith("images_")) {
                            // Alternative format: images_0-0
                            imageKey = fieldName.substring(7);
                        } else {
                            // Use the field name directly if it doesn't match expected patterns
                            imageKey = fieldName;
                        }

                        itemImages.put(imageKey, file);
                        log.info("Image received: fieldName={}, key={}, size={} bytes, contentType={}",
                                fieldName, imageKey, file.getSize(), file.getContentType());
                    }
                }
            }

            log.info("Total images to process: {}", itemImages.size());

            RestaurantSetupResponse response = restaurantSetupService.createRestaurantSetupWithImages(
                    setupRequest,
                    itemImages
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (BusinessRuleException e) {
            throw e; // Let the global exception handler deal with it
        } catch (Exception e) {
            log.error("Error processing restaurant setup with images", e);
            throw new BusinessRuleException("Failed to process request: " + e.getMessage());
        }
    }
}

