package com.restaurantqr.config;

import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItem;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.user.Role;
import com.restaurantqr.menu.MenuCategory;
import com.restaurantqr.menu.MenuCategoryRepository;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;




import com.restaurantqr.servicecall.CallType;
import com.restaurantqr.servicecall.ServiceCall;
import com.restaurantqr.servicecall.ServiceCallRepository;



import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Configuration @Profile("dev")
public class DataSeeder1 {
    @Bean
    CommandLineRunner seedData(
            RestaurantRepository restaurantRepository,
            UserRepository userRepository,
            RestaurantTableRepository tableRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository,
            CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ServiceCallRepository serviceCallRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {

            // ✅ Seed once
            if (restaurantRepository.count() > 0) return;

            // =========================
            // 1) Restaurant
            // =========================
            Restaurant restaurant = new Restaurant();
            restaurant.setName("Demo Restaurant");
            restaurant.setCode("DEMO");
            restaurant.setActive(true);
            restaurantRepository.save(restaurant);

            // =========================
            // 2) Users (STAFF only + optional manager)
            // =========================
            userRepository.save(makeUser("staff1", "123456", Role.STAFF, restaurant, passwordEncoder));
            userRepository.save(makeUser("staff2", "123456", Role.STAFF, restaurant, passwordEncoder));

            // optional
//            userRepository.save(makeUser("manager1", "123456", Role.MANAGER, restaurant, passwordEncoder));

            // =========================
            // 3) Categories + Items (enough items for orders)
            // =========================
            List<String> categoryNames = List.of(
                    "المشروبات", "المقبلات", "السندويش", "البيتزا",
                    "البرغر", "الأطباق الرئيسية", "الحلويات", "الشيشا"
            );

            List<MenuCategory> categories = categoryNames.stream().map(name -> {
                MenuCategory c = new MenuCategory();
                c.setRestaurant(restaurant);
                c.setName(name);
                c.setActive(true);

                c.setDisplayOrder(categoryNames.indexOf(name) + 1);
                return categoryRepository.save(c);
            }).toList();

            // 10 items per category (80 total)
            for (MenuCategory cat : categories) {
                for (int i = 1; i <= 10; i++) {
                    MenuItem item = new MenuItem();
                    item.setRestaurant(restaurant);
                    item.setCategory(cat);
                    item.setName(buildItemName(cat.getName(), i));
                    item.setDescription(buildItemDescription(cat.getName(), i));
                    item.setPrice(randomPrice(cat.getName()));
                    item.setAvailable(true);      // ✅ make them available so menu/order works
                    item.setActive(true);
                    item.setDisplayOrder(i);
                    item.setImageUrl("/uploads/menu/cola.jpg");
                    itemRepository.save(item);
                }
            }

            // fetch all items for random orders
            List<MenuItem> allItems = itemRepository
                    .findByRestaurantIdAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(restaurant.getId());

            // =========================
            // 4) Tables + Each table: 1 Order + 1 Call
            // =========================
            int tablesCount = 20;

            // we want calls to be active: createdAt within last 3 minutes
            Instant baseNow = Instant.now();

            for (int t = 1; t <= tablesCount; t++) {

                // Table
                RestaurantTable table = new RestaurantTable();
                table.setRestaurant(restaurant);
                table.setTableNumber(t);
                table.setQrToken("DEMO-TABLE-" + t);
                table.setActive(true);
                tableRepository.save(table);

                // ---- Service Call (one per table) ----
                ServiceCall call = new ServiceCall();
                call.setRestaurant(restaurant);
                call.setTable(table);

                // random type
                call.setCallType(randomCallType());

                // set createdAt within last 120 seconds to guarantee active
                // (assumes ServiceCall has createdAt @CreationTimestamp; if not, set it)
                Instant callCreatedAt = baseNow.minusSeconds(ThreadLocalRandom.current().nextInt(10, 120));
                call.setCreatedAt(callCreatedAt); // ✅ requires setter + not @CreationTimestamp-only

                serviceCallRepository.save(call);

                // ---- Order (one per table) ----
                CustomerOrder order = new CustomerOrder();
                order.setRestaurant(restaurant);
                order.setTable(table);


                Instant orderCreatedAt = baseNow.minusSeconds(ThreadLocalRandom.current().nextInt(10, 120));
                order.setCreatedAt(orderCreatedAt); // ✅ requires setter
                order.setStatus(OrderStatus.NEW);
                order.setNote("طلب تجريبي للطاولة " + t);

                // Build items (2-4 items)
                int itemsCount = ThreadLocalRandom.current().nextInt(2, 5);
                BigDecimal total = BigDecimal.ZERO;


                List<OrderItem> itemsBuffer = new ArrayList<>();

                for (int i = 0; i < itemsCount; i++) {
                    MenuItem randomItem = allItems.get(ThreadLocalRandom.current().nextInt(allItems.size()));
                    int qty = ThreadLocalRandom.current().nextInt(1, 4);

                    BigDecimal unitPrice = randomItem.getPrice();
                    BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                    total = total.add(lineTotal);

                    OrderItem oi = new OrderItem();
                    oi.setMenuItem(randomItem);
                    oi.setQuantity(qty);
                    oi.setUnitPrice(unitPrice);
                    oi.setTotalPrice(lineTotal);
                    oi.setNote((i == 0) ? "بدون ثلج" : null);

                    itemsBuffer.add(oi);
                }

// 2️⃣ الآن totalAmount معروف
                order.setTotalAmount(total);

// 3️⃣ احفظ الطلب
                CustomerOrder savedOrder = orderRepository.save(order);

// 4️⃣ اربط العناصر بالطلب واحفظها
                for (OrderItem oi : itemsBuffer) {
                    oi.setOrder(savedOrder);
                    orderItemRepository.save(oi);
                }
            }

            System.out.println("✅ Seeded: 1 restaurant + users + 20 tables + 20 orders + 20 service calls");
            System.out.println("➡ STAFF login: staff1 / 123456");
            System.out.println("➡ Try menu: GET /api/v1/public/tables/DEMO-TABLE-1/menu");
        };
    }

    private User makeUser(String username, String rawPassword, Role role, Restaurant restaurant, PasswordEncoder encoder) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(role);
        u.setRestaurant(restaurant);
        u.setActive(true);
        return u;
    }

    private CallType randomCallType(){
        int r = ThreadLocalRandom.current().nextInt(3);
        if(r==0) return CallType.WAITER;
        if(r==1) return CallType.NARA;
        return CallType.BILL;
    }

    private String buildItemName(String category, int i) {
        return switch (category) {
            case "المشروبات" -> "مشروب رقم " + i;
            case "المقبلات" -> "مقبلات رقم " + i;
            case "السندويش" -> "سندويش رقم " + i;
            case "البيتزا" -> "بيتزا رقم " + i;
            case "البرغر" -> "برغر رقم " + i;
            case "الأطباق الرئيسية" -> "طبق رئيسي رقم " + i;
            case "الحلويات" -> "حلوى رقم " + i;
            case "الشيشا" -> "شيشا رقم " + i;
            default -> "عنصر رقم " + i;
        };
    }

    private String buildItemDescription(String category, int i) {
        return switch (category) {
            case "المشروبات" -> "مشروب بارد/ساخن حسب الاختيار";
            case "المقبلات" -> "طبق خفيف قبل الوجبة";
            case "السندويش" -> "سندويش طازج مع إضافات";
            case "البيتزا" -> "بيتزا ساخنة مع جبنة";
            case "البرغر" -> "برغر لحم/دجاج مع صوص";
            case "الأطباق الرئيسية" -> "وجبة رئيسية مشبعة";
            case "الحلويات" -> "حلوى لذيذة بعد الأكل";
            case "الشيشا" -> "نكهة شيشا مميزة";
            default -> "وصف تجريبي";
        };
    }

    private BigDecimal randomPrice(String category) {
        double min, max;
        switch (category) {
            case "المشروبات" -> { min=1.0; max=4.0; }
            case "المقبلات" -> { min=2.0; max=6.0; }
            case "السندويش" -> { min=4.0; max=9.0; }
            case "البيتزا" -> { min=7.0; max=14.0; }
            case "البرغر" -> { min=6.0; max=12.0; }
            case "الأطباق الرئيسية" -> { min=8.0; max=18.0; }
            case "الحلويات" -> { min=3.0; max=8.0; }
            case "الشيشا" -> { min=6.0; max=15.0; }
            default -> { min=2.0; max=10.0; }
        }
        double val = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(Math.round(val * 100.0) / 100.0);
    }
}

