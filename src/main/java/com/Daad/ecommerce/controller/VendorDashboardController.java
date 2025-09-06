package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Order;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.dto.Review;
import com.Daad.ecommerce.model.User;
import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.repository.OrderRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.repository.ReviewRepository;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendor-dashboard")
@CrossOrigin(origins = "*")
@PreAuthorize("isAuthenticated()")
public class VendorDashboardController {

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VendorRepository vendorRepository;

    private String getCurrentVendorId() {
        String userId = SecurityUtils.currentUserId();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = userOpt.get();
        if (!"vendor".equals(user.getRole())) {
            throw new RuntimeException("User is not a vendor");
        }
        Optional<Vendor> vendorOpt = vendorRepository.findByUser(user);
        if (vendorOpt.isEmpty()) {
            throw new RuntimeException("Vendor profile not found");
        }
        return vendorOpt.get().getId();
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        String vendorId = getCurrentVendorId();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() % 7).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<Order> allOrders = orderRepository.findAllByStatusOptional(null);

        List<Order> monthlyOrders = allOrders.stream()
                .filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId)))
                .filter(o -> o.getCreatedAt().isAfter(startOfMonth))
                .collect(Collectors.toList());
        List<Order> weeklyOrders = allOrders.stream()
                .filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId)))
                .filter(o -> o.getCreatedAt().isAfter(startOfWeek))
                .collect(Collectors.toList());
        List<Order> dailyOrders = allOrders.stream()
                .filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId)))
                .filter(o -> o.getCreatedAt().isAfter(startOfDay))
                .collect(Collectors.toList());

        double monthlyRevenue = monthlyOrders.stream().mapToDouble(o -> o.getItems().stream()
                .filter(i -> Objects.equals(i.getVendorId(), vendorId))
                .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum()).sum();
        double weeklyRevenue = weeklyOrders.stream().mapToDouble(o -> o.getItems().stream()
                .filter(i -> Objects.equals(i.getVendorId(), vendorId))
                .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum()).sum();
        double dailyRevenue = dailyOrders.stream().mapToDouble(o -> o.getItems().stream()
                .filter(i -> Objects.equals(i.getVendorId(), vendorId))
                .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum()).sum();

        List<Product> vendorProducts = productRepository.findAll().stream()
                .filter(p -> p.getVendor() != null && Objects.equals(p.getVendor().getId(), vendorId))
                .collect(Collectors.toList());
        Map<String, Object> productsStats = new HashMap<>();
        productsStats.put("totalProducts", vendorProducts.size());
        productsStats.put("activeProducts", vendorProducts.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).count());
        productsStats.put("approvedProducts", vendorProducts.stream().filter(p -> "approved".equalsIgnoreCase(p.getStatus())).count());
        productsStats.put("pendingProducts", vendorProducts.stream().filter(p -> "awaiting_approval".equalsIgnoreCase(p.getStatus())).count());
        productsStats.put("totalStock", vendorProducts.stream().mapToInt(p -> Optional.ofNullable(p.getTotalStock()).orElse(0)).sum());

        Map<String, Object> orderStatus = new HashMap<>();
        orderStatus.put("totalOrders", allOrders.stream().filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId))).count());
        orderStatus.put("completedOrders", allOrders.stream().filter(o -> "delivered".equalsIgnoreCase(o.getOrderStatus()) && o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId))).count());
        orderStatus.put("pendingOrders", allOrders.stream().filter(o -> "pending".equalsIgnoreCase(o.getOrderStatus()) && o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId))).count());
        orderStatus.put("processingOrders", allOrders.stream().filter(o -> "processing".equalsIgnoreCase(o.getOrderStatus()) && o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId))).count());
        orderStatus.put("shippedOrders", allOrders.stream().filter(o -> "shipped".equalsIgnoreCase(o.getOrderStatus()) && o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId))).count());

        // Ratings for vendor's products
        Set<String> vendorProductIds = vendorProducts.stream().map(Product::getId).collect(Collectors.toSet());
        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        List<Review> allReviews = reviewRepository.findByCustomerIdAndStatus(null, "approved"); // get all approved
        allReviews.stream()
                .filter(r -> vendorProductIds.contains(r.getProductId()))
                .forEach(r -> dist.compute(r.getRating(), (k, v) -> v == null ? 1L : v + 1));
        double avg = 0.0;
        long totalReviews = 0;
        for (Map.Entry<Integer, Long> e : dist.entrySet()) {
            avg += e.getKey() * e.getValue();
            totalReviews += e.getValue();
        }
        avg = totalReviews > 0 ? Math.round((avg / totalReviews) * 10.0) / 10.0 : 0.0;

        Map<String, Object> ratings = new HashMap<>();
        ratings.put("averageRating", avg);
        ratings.put("totalReviews", totalReviews);
        ratings.put("ratingDistribution", dist);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", Map.of(
                "revenue", Map.of(
                        "monthly", Math.round(monthlyRevenue * 100.0) / 100.0,
                        "weekly", Math.round(weeklyRevenue * 100.0) / 100.0,
                        "daily", Math.round(dailyRevenue * 100.0) / 100.0
                ),
                "orders", Map.of(
                        "monthly", monthlyOrders.size(),
                        "weekly", weeklyOrders.size(),
                        "daily", dailyOrders.size()
                ),
                "products", productsStats,
                "orderStatus", orderStatus,
                "ratings", ratings
        ));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/sales-analytics")
    public ResponseEntity<Map<String, Object>> getSalesAnalytics(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        String vendorId = getCurrentVendorId();

        LocalDateTime start;
        if (startDate != null && endDate != null) {
            start = LocalDate.parse(startDate).atStartOfDay();
        } else {
            LocalDateTime now = LocalDateTime.now();
            switch (period) {
                case "week":
                    start = now.minusDays(7);
                    break;
                case "quarter":
                    start = now.minusMonths(3);
                    break;
                case "year":
                    start = now.minusYears(1);
                    break;
                case "month":
                default:
                    start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            }
        }

        List<Order> orders = orderRepository.findAllByStatusOptional(null).stream()
                .filter(o -> o.getCreatedAt().isAfter(start))
                .filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId)))
                .collect(Collectors.toList());

        Map<String, Map<String, Object>> byDate = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Order o : orders) {
            String date = o.getCreatedAt().format(fmt);
            double revenue = o.getItems().stream()
                    .filter(i -> Objects.equals(i.getVendorId(), vendorId))
                    .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
            byDate.computeIfAbsent(date, k -> new HashMap<>(Map.of("totalQuantity", 0L, "totalRevenue", 0.0, "orderCount", 0L)));
            Map<String, Object> entry = byDate.get(date);
            entry.put("totalRevenue", (double) entry.get("totalRevenue") + revenue);
            entry.put("orderCount", (long) entry.get("orderCount") + 1L);
            long qty = o.getItems().stream().filter(i -> Objects.equals(i.getVendorId(), vendorId)).mapToLong(Order.Item::getQuantity).sum();
            entry.put("totalQuantity", (long) entry.get("totalQuantity") + qty);
        }

        // Top products
        Map<String, Map<String, Object>> productAgg = new HashMap<>();
        for (Order o : orders) {
            for (Order.Item i : o.getItems()) {
                if (!Objects.equals(i.getVendorId(), vendorId)) continue;
                productAgg.computeIfAbsent(i.getProduct(), k -> new HashMap<>(Map.of("totalQuantity", 0L, "totalRevenue", 0.0)));
                Map<String, Object> e = productAgg.get(i.getProduct());
                e.put("totalQuantity", (long) e.get("totalQuantity") + i.getQuantity());
                e.put("totalRevenue", (double) e.get("totalRevenue") + (i.getPrice() * i.getQuantity()));
            }
        }
        List<Map<String, Object>> topProducts = productAgg.entrySet().stream()
                .sorted((a, b) -> Long.compare((long) b.getValue().get("totalQuantity"), (long) a.getValue().get("totalQuantity")))
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", e.getKey());
                    productRepository.findById(e.getKey()).ifPresent(p -> m.put("productName", p.getName()));
                    m.put("totalQuantity", e.getValue().get("totalQuantity"));
                    m.put("totalRevenue", e.getValue().get("totalRevenue"));
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "salesData", byDate.entrySet().stream().map(en -> Map.of(
                                "date", en.getKey(),
                                "totalQuantity", en.getValue().get("totalQuantity"),
                                "totalRevenue", en.getValue().get("totalRevenue"),
                                "orderCount", en.getValue().get("orderCount")
                        )).collect(Collectors.toList()),
                        "topProducts", topProducts,
                        "period", period
                )
        ));
    }

    @GetMapping("/performance-metrics")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics(@RequestParam(defaultValue = "30") Integer days) {
        String vendorId = getCurrentVendorId();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Order> orders = orderRepository.findAllByStatusOptional(null).stream()
                .filter(o -> o.getCreatedAt().isAfter(startDate))
                .filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId)))
                .collect(Collectors.toList());

        double totalRevenue = 0.0;
        int totalOrders = orders.size();
        int completedOrders = 0;
        long totalItems = 0L;
        for (Order o : orders) {
            boolean hasVendorItem = false;
            for (Order.Item i : o.getItems()) {
                if (Objects.equals(i.getVendorId(), vendorId)) {
                    totalRevenue += i.getPrice() * i.getQuantity();
                    totalItems += i.getQuantity();
                    hasVendorItem = true;
                }
            }
            if (hasVendorItem && "delivered".equalsIgnoreCase(o.getOrderStatus())) {
                completedOrders++;
            }
        }
        double averageOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0.0;

        long totalProducts = productRepository.findAll().stream().filter(p -> p.getVendor() != null && Objects.equals(p.getVendor().getId(), vendorId) && Boolean.TRUE.equals(p.getIsActive())).count();
        double conversionRate = totalProducts > 0 ? (double) totalOrders / totalProducts * 100.0 : 0.0;

        // Reviews in period
        List<Review> allReviews = reviewRepository.findByCustomerIdAndStatus(null, "approved");
        List<Review> vendorReviews = allReviews.stream()
                .filter(r -> productRepository.findById(r.getProductId()).map(p -> p.getVendor() != null && Objects.equals(p.getVendor().getId(), vendorId)).orElse(false))
                .collect(Collectors.toList());
        double averageRating = vendorReviews.isEmpty() ? 0.0 : vendorReviews.stream().mapToInt(Review::getRating).average().orElse(0.0);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        metrics.put("totalOrders", totalOrders);
        metrics.put("completedOrders", completedOrders);
        metrics.put("totalItems", totalItems);
        metrics.put("averageOrderValue", Math.round(averageOrderValue * 100.0) / 100.0);
        metrics.put("conversionRate", Math.round(conversionRate * 100.0) / 100.0);
        metrics.put("averageRating", Math.round(averageRating * 10.0) / 10.0);
        metrics.put("customerSatisfaction", Math.round((averageRating / 5.0) * 100.0));

        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("period", days + " days", "metrics", metrics)));
    }

    @GetMapping("/financial-summary")
    public ResponseEntity<Map<String, Object>> getFinancialSummary(@RequestParam(required = false) Integer year) {
        String vendorId = getCurrentVendorId();
        int y = year != null ? year : LocalDate.now().getYear();
        LocalDateTime start = LocalDateTime.of(y, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(y, 12, 31, 23, 59);

        List<Order> orders = orderRepository.findAllByStatusOptional(null).stream()
                .filter(o -> o.getCreatedAt().isAfter(start) && o.getCreatedAt().isBefore(end))
                .filter(o -> o.getItems().stream().anyMatch(i -> Objects.equals(i.getVendorId(), vendorId)))
                .collect(Collectors.toList());

        double[] monthlyRevenue = new double[12];
        int[] monthlyOrders = new int[12];
        for (Order o : orders) {
            int month = o.getCreatedAt().getMonthValue() - 1;
            double orderRevenue = o.getItems().stream()
                    .filter(i -> Objects.equals(i.getVendorId(), vendorId))
                    .mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
            monthlyRevenue[month] += orderRevenue;
            monthlyOrders[month] += o.getItems().stream().filter(i -> Objects.equals(i.getVendorId(), vendorId)).count();
        }

        double totalRevenue = Arrays.stream(monthlyRevenue).sum();
        double commissionRate = 10.0;
        double totalCommission = totalRevenue * (commissionRate / 100.0);
        double netRevenue = totalRevenue - totalCommission;

        Map<String, Object> data = new HashMap<>();
        data.put("year", y);
        data.put("monthlyRevenue", Arrays.stream(monthlyRevenue).boxed().map(v -> Math.round(v * 100.0) / 100.0).collect(Collectors.toList()));
        data.put("monthlyOrders", Arrays.stream(monthlyOrders).boxed().collect(Collectors.toList()));
        data.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        data.put("commissionRate", commissionRate);
        data.put("totalCommission", Math.round(totalCommission * 100.0) / 100.0);
        data.put("netRevenue", Math.round(netRevenue * 100.0) / 100.0);

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}
