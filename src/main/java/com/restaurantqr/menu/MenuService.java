package com.restaurantqr.menu;


import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.slf4j.*;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public MenuResponse getMenuByQrToken(String qrToken) {

        log.info("Get menu request received. qrToken={}", qrToken);

        RestaurantTable table = tableRepository.findByQrTokenAndIsActiveTrue(qrToken)
                .orElseThrow(() -> {
                    log.warn("Invalid or inactive QR token. qrToken={}", qrToken);
                    return new ResourceNotFoundException("Invalid or inactive QR token");
                });

        var restaurant = table.getRestaurant();

        if (!restaurant.isActive()) {
            log.warn("Restaurant inactive. restaurantId={}, qrToken={}", restaurant.getId(), qrToken);
            throw new BusinessRuleException("Restaurant is inactive");
        }

        Long restaurantId = restaurant.getId();

        List<MenuCategoryDto> categories = categoryRepository
                .findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(c -> new MenuCategoryDto(c.getId(), c.getName(), c.getDisplayOrder()))
                .toList();

        List<MenuItemDto> items = itemRepository
                .findByRestaurantIdAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(restaurantId)
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

        log.info("Menu loaded successfully. restaurantId={}, tableNumber={}, categoriesCount={}, itemsCount={}",
                restaurantId, table.getTableNumber(), categories.size(), items.size());

        return new MenuResponse(
                restaurant.getName(),
                table.getTableNumber(),
                categories,
                items
        );
    }
}

