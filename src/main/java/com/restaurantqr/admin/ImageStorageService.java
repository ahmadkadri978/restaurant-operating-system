package com.restaurantqr.admin;

import com.restaurantqr.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for handling image uploads for restaurant menu items.
 * Stores images in restaurant-specific folders and returns public URLs.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9-]");

    private final Path uploadDir;
    private final Set<String> allowedExtensions;
    private final long maxFileSize;

    public ImageStorageService(
            @Value("${app.upload.dir:../frontend-repo/uploads}") String uploadDirPath,
            @Value("${app.upload.allowed-extensions:jpg,jpeg,png,gif,webp}") String allowedExts,
            @Value("${app.upload.max-file-size:5MB}") String maxSize
    ) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.allowedExtensions = Set.of(allowedExts.toLowerCase().split(","));
        this.maxFileSize = parseSize(maxSize);

        log.info("ImageStorageService initialized with upload directory: {}", this.uploadDir);
        log.info("Allowed extensions: {}, Max file size: {} bytes", this.allowedExtensions, this.maxFileSize);

        // Ensure base upload directory exists
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", this.uploadDir, e);
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    /**
     * Creates a restaurant-specific folder for storing images.
     * Folder name format: {restaurant-name}-{restaurant-code} (sanitized)
     *
     * @param restaurantName the restaurant name
     * @param restaurantCode the restaurant code
     * @return the folder name (not full path)
     */
    public String createRestaurantFolder(String restaurantName, String restaurantCode) {
        String folderName = sanitizeFolderName(restaurantName, restaurantCode);
        Path restaurantDir = uploadDir.resolve(folderName);

        try {
            Files.createDirectories(restaurantDir);
            log.info("Created restaurant folder: {}", restaurantDir);
            return folderName;
        } catch (IOException e) {
            log.error("Failed to create restaurant folder: {}", restaurantDir, e);
            throw new BusinessRuleException("Could not create restaurant image folder");
        }
    }

    /**
     * Stores an image file in the restaurant's folder.
     *
     * @param file           the uploaded file
     * @param restaurantFolder the restaurant folder name
     * @param itemName       the menu item name (used as base filename)
     * @return the public URL path (e.g., /uploads/restaurant-folder/item-name.jpg)
     */
    public String storeImage(MultipartFile file, String restaurantFolder, String itemName) {
        // Validate file
        validateFile(file);

        // Get file extension
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        // Create sanitized filename with UUID to prevent collisions
        String sanitizedName = sanitizeFilename(itemName);
        String uniqueFilename = String.format("%s-%s.%s",
                sanitizedName,
                UUID.randomUUID().toString().substring(0, 8),
                extension
        );

        // Resolve full path
        Path restaurantDir = uploadDir.resolve(restaurantFolder);
        Path targetPath = restaurantDir.resolve(uniqueFilename);

        try {
            // Ensure restaurant directory exists
            Files.createDirectories(restaurantDir);

            // Copy file to target location
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored image: {}", targetPath);

            // Return public URL path (not absolute filesystem path)
            return String.format("/uploads/%s/%s", restaurantFolder, uniqueFilename);

        } catch (IOException e) {
            log.error("Failed to store image: {}", targetPath, e);
            throw new BusinessRuleException("Could not store image file: " + e.getMessage());
        }
    }

    /**
     * Validates the uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Image file is empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new BusinessRuleException(String.format(
                    "File size exceeds maximum allowed (%d MB)",
                    maxFileSize / (1024 * 1024)
            ));
        }

        // Check extension
        String extension = getExtension(file.getOriginalFilename());
        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new BusinessRuleException(String.format(
                    "File type not allowed. Allowed types: %s",
                    String.join(", ", allowedExtensions)
            ));
        }

        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessRuleException("File must be an image");
        }
    }

    /**
     * Extracts file extension from filename.
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessRuleException("Invalid filename: missing extension");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Sanitizes a filename to be safe for filesystem storage.
     */
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "item";
        }

        // Normalize Unicode characters
        String normalized = Normalizer.normalize(name.toLowerCase(), Normalizer.Form.NFD);

        // Replace spaces with hyphens, remove non-alphanumeric
        String sanitized = normalized
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");

        // Remove consecutive hyphens and trim
        sanitized = sanitized.replaceAll("-+", "-").replaceAll("^-|-$", "");

        // Ensure not empty and reasonable length
        if (sanitized.isEmpty()) {
            return "item";
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized;
    }

    /**
     * Creates a sanitized folder name from restaurant name and code.
     */
    private String sanitizeFolderName(String restaurantName, String restaurantCode) {
        String combined = restaurantName.toLowerCase() + "-" + restaurantCode.toLowerCase();

        // Normalize and sanitize
        String normalized = Normalizer.normalize(combined, Normalizer.Form.NFD);
        String sanitized = NON_ALPHANUMERIC.matcher(
                normalized.replaceAll("\\s+", "-")
        ).replaceAll("");

        // Clean up hyphens
        sanitized = sanitized.replaceAll("-+", "-").replaceAll("^-|-$", "");

        if (sanitized.isEmpty()) {
            sanitized = "restaurant-" + restaurantCode.toLowerCase();
        }

        return sanitized;
    }

    /**
     * Parses size string like "5MB" to bytes.
     */
    private long parseSize(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        if (sizeStr.endsWith("MB")) {
            return Long.parseLong(sizeStr.replace("MB", "").trim()) * 1024 * 1024;
        } else if (sizeStr.endsWith("KB")) {
            return Long.parseLong(sizeStr.replace("KB", "").trim()) * 1024;
        } else if (sizeStr.endsWith("GB")) {
            return Long.parseLong(sizeStr.replace("GB", "").trim()) * 1024 * 1024 * 1024;
        }
        return Long.parseLong(sizeStr);
    }

    /**
     * Gets the configured upload directory path.
     */
    public Path getUploadDir() {
        return uploadDir;
    }
}

