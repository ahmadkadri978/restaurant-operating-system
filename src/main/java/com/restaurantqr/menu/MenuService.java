package com.restaurantqr.menu;


import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;

import org.springframework.stereotype.Service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;


@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    private final RestaurantTableRepository tableRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    public MenuService(RestaurantTableRepository tableRepository,
                       MenuCategoryRepository categoryRepository,
                       MenuItemRepository itemRepository) {
        this.tableRepository = tableRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenuByQrToken(String qrToken) {

        if (qrToken == null || qrToken.isBlank()) {
            throw new BusinessRuleException("qrToken is required");
        }

        log.info("Get menu | qrToken={}", qrToken);

        // ✅ Fetch restaurant in same query to avoid Lazy issues / extra queries
        RestaurantTable table = tableRepository.findActiveByQrTokenFetchRestaurant(qrToken)
                .orElseThrow(() -> {
                    log.warn("Invalid/inactive QR token | qrToken={}", qrToken);
                    return new ResourceNotFoundException("Invalid or inactive QR token");
                });

        Restaurant restaurant = table.getRestaurant();
        if (restaurant == null || !restaurant.isActive()) {
            log.warn("Restaurant inactive/not found | qrToken={} | tableId={}", qrToken, table.getId());
            throw new ResourceNotFoundException("Invalid or inactive QR token");
        }

        Long restaurantId = restaurant.getId();

        // ✅ Categories: stable ordering (null displayOrder safe)
        List<MenuCategoryDto> categories = categoryRepository
                .findActiveByRestaurantOrderByDisplayOrder(restaurantId)
                .stream()
                .map(c -> new MenuCategoryDto(c.getId(), c.getName(), c.getDisplayOrder()))
                .toList();

        // ✅ Items: fetch category to avoid N+1 in mapping (i.getCategory().getId())
        List<MenuItemDto> items = itemRepository
                .findActiveAvailableMenuForRestaurantFetchCategory(restaurantId)
                .stream()
                .map(i -> new MenuItemDto(
                        i.getId(),
                        i.getCategory().getId(),
                        i.getName(),
                        i.getDescription(),
                        i.getPrice(),
                        i.isAvailable(),
                        i.getDisplayOrder(),
                        i.getImageUrl()
                ))
                .toList();

        log.info("Menu loaded | restaurantId={} | tableNumber={} | categoriesCount={} | itemsCount={}",
                restaurantId, table.getTableNumber(), categories.size(), items.size());

        return new MenuResponse(
                restaurant.getName(),
                table.getTableNumber(),
                categories,
                items
        );
    }
}

