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
        try {
            String userId = SecurityUtils.currentUserId();
            System.out.println("VendorDashboardController: Current user ID: " + userId);
            
            if (userId == null || userId.isEmpty()) {
                throw new RuntimeException("No authenticated user found");
            }
            
            // Retry mechanism for database operations
            User user = null;
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    Optional<User> userOpt = userRepository.findById(userId);
                    if (userOpt.isPresent()) {
                        user = userOpt.get();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("Error getting user (attempt " + (i + 1) + "): " + e.getMessage());
                    if (i == maxRetries - 1) {
                        throw new RuntimeException("User not found after retries: " + e.getMessage());
                    }
                    // Wait before retry
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
            
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            
            System.out.println("VendorDashboardController: User role: " + user.getRole());
            
            if (!"vendor".equals(user.getRole())) {
                throw new RuntimeException("User is not a vendor");
            }
            
            // Retry mechanism for vendor lookup
            Vendor vendor = null;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    Optional<Vendor> vendorOpt = vendorRepository.findByUser(user);
                    if (vendorOpt.isPresent()) {
                        vendor = vendorOpt.get();
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("Error getting vendor (attempt " + (i + 1) + "): " + e.getMessage());
                    if (i == maxRetries - 1) {
                        throw new RuntimeException("Vendor profile not found after retries: " + e.getMessage());
                    }
                    // Wait before retry
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
            
            if (vendor == null) {
                throw new RuntimeException("Vendor profile not found");
            }
            
            String vendorId = vendor.getId();
            System.out.println("VendorDashboardController: Vendor ID: " + vendorId);
            return vendorId;
        } catch (Exception e) {
            System.err.println("Error getting vendor ID: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error getting vendor ID: " + e.getMessage(), e);
        }
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        try {
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
        
        } catch (Exception e) {
            System.err.println("Error in getDashboardOverview: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching dashboard overview: " + e.getMessage()
            ));
        }
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

    // New: Sales report
    @GetMapping("/analytics/sales-report")
    public ResponseEntity<Map<String, Object>> salesReport() {
        String vendorId = getCurrentVendorId();
        Map<String, Object> report = orderRepository.vendorSalesReport(vendorId);
        return ResponseEntity.ok(Map.of("success", true, "data", report));
    }

    // New: Customer insights
    @GetMapping("/analytics/customer-insights")
    public ResponseEntity<Map<String, Object>> customerInsights() {
        String vendorId = getCurrentVendorId();
        Map<String, Object> insights = orderRepository.vendorCustomerInsights(vendorId);
        return ResponseEntity.ok(Map.of("success", true, "data", insights));
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

    @GetMapping("/overview-stats")
    public ResponseEntity<Map<String, Object>> getVendorOverviewStats() {
        try {
            String vendorId = getCurrentVendorId();
            
            // Get vendor details for commission percentage
            Optional<Vendor> vendorOpt = vendorRepository.findById(vendorId);
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vendor not found"));
            }
            
            Vendor vendor = vendorOpt.get();
            double adminCommissionPercentage = vendor.getCommission() != null ? vendor.getCommission() : 10.0; // Default 10%
            
            // Use efficient repository methods instead of loading all data
            int totalProducts = productRepository.countActiveProductsByVendorId(vendorId);
            int totalOrders = orderRepository.countVendorOrders(vendorId);
            double totalRevenue = orderRepository.calculateVendorRevenue(vendorId);
            int pendingOrders = orderRepository.countVendorPendingOrders(vendorId);
            
            double totalCommission = (totalRevenue * adminCommissionPercentage) / 100.0;
            double netRevenue = totalRevenue - totalCommission;
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalProducts", totalProducts);
            stats.put("totalOrders", totalOrders);
            stats.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
            stats.put("pendingOrders", pendingOrders);
            stats.put("adminCommission", adminCommissionPercentage);
            stats.put("totalCommission", Math.round(totalCommission * 100.0) / 100.0);
            stats.put("netRevenue", Math.round(netRevenue * 100.0) / 100.0);
            
            return ResponseEntity.ok(Map.of("success", true, "data", stats));
            
        } catch (Exception e) {
            System.err.println("Error in getVendorOverviewStats: " + e.getMessage());
            e.printStackTrace();
            
            // Return default values instead of error to prevent frontend crashes
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "totalProducts", 0,
                    "totalOrders", 0,
                    "totalRevenue", 0.0,
                    "pendingOrders", 0,
                    "adminCommissionPercentage", 10.0,
                    "totalCommission", 0.0,
                    "netRevenue", 0.0
                ),
                "message", "Stats temporarily unavailable"
            ));
        }
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<Map<String, Object>> getRecentOrders(@RequestParam(defaultValue = "10") int limit) {
        try {
            String vendorId = getCurrentVendorId();
            System.out.println("VendorDashboardController: Getting recent orders for vendor ID: " + vendorId + " with limit: " + limit);
            
            // Use efficient repository method
            List<Order> recentOrders = orderRepository.findRecentOrdersByVendorId(vendorId, limit);
            System.out.println("VendorDashboardController: Found " + recentOrders.size() + " recent orders");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", recentOrders.size(),
                "data", recentOrders
            ));
            
        } catch (Exception e) {
            System.err.println("Error in getRecentOrders: " + e.getMessage());
            e.printStackTrace();
            
            // Return empty list instead of error to prevent frontend crashes
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", 0,
                "data", new ArrayList<>(),
                "message", "No recent orders available at the moment"
            ));
        }
    }

    @GetMapping("/recent-products")
    public ResponseEntity<Map<String, Object>> getRecentProducts(@RequestParam(defaultValue = "10") int limit) {
        try {
            String vendorId = getCurrentVendorId();
            
            // Use efficient repository method
            List<Product> recentProducts = productRepository.findRecentProductsByVendorId(vendorId, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", recentProducts.size(),
                "data", recentProducts
            ));
            
        } catch (Exception e) {
            System.err.println("Error in getRecentProducts: " + e.getMessage());
            e.printStackTrace();
            
            // Return empty list instead of error to prevent frontend crashes
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", 0,
                "data", new ArrayList<>(),
                "message", "No recent products available at the moment"
            ));
        }
    }
    
    // Get all products by vendor (awaiting approval and approved) - no pagination
    @GetMapping("/all-products")
    public ResponseEntity<Map<String, Object>> getAllProducts(@RequestParam(defaultValue = "awaiting_approval_and_approved") String status) {
        try {
            String vendorId = getCurrentVendorId();
            System.out.println("VendorDashboardController: Getting all products for vendor ID: " + vendorId + " with status: " + status);
            
            List<Product> products = productRepository.getAllProductsByVendor(vendorId, status);
            System.out.println("VendorDashboardController: Found " + products.size() + " products");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", products.size(),
                "status", status,
                "data", products
            ));
            
        } catch (Exception e) {
            System.err.println("Error in getAllProducts: " + e.getMessage());
            e.printStackTrace();
            
            // Return empty list instead of error to prevent frontend crashes
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", 0,
                "status", status,
                "data", new ArrayList<>(),
                "message", "No products available at the moment"
            ));
        }
    }

    // Filtered Orders API for Vendor Dashboard
    @GetMapping("/orders/filtered")
    public ResponseEntity<Map<String, Object>> getFilteredOrders(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            String vendorId = getCurrentVendorId();
            
            List<Map<String, Object>> orders = orderRepository.findVendorOrdersWithFilters(
                vendorId, productName, startDate, endDate, orderStatus, page, limit);
            
            int total = orderRepository.countVendorOrdersWithFilters(
                vendorId, productName, startDate, endDate, orderStatus);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", orders.size(),
                "total", total,
                "pagination", Map.of(
                    "page", page,
                    "pages", (int) Math.ceil((double) total / limit)
                ),
                "data", orders
            ));
            
        } catch (Exception e) {
            System.err.println("Error in getFilteredOrders: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching filtered orders: " + e.getMessage()
            ));
        }
    }

    // Filtered Products API for Vendor Dashboard
    @GetMapping("/products/filtered")
    public ResponseEntity<Map<String, Object>> getFilteredProducts(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String productStatus,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            String vendorId = getCurrentVendorId();
            
            List<Map<String, Object>> products = productRepository.findVendorProductsWithFilters(
                vendorId, productName, startDate, endDate, productStatus, page, limit);
            
            int total = productRepository.countVendorProductsWithFilters(
                vendorId, productName, startDate, endDate, productStatus);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", products.size(),
                "total", total,
                "pagination", Map.of(
                    "page", page,
                    "pages", (int) Math.ceil((double) total / limit)
                ),
                "data", products
            ));
            
        } catch (Exception e) {
            System.err.println("Error in getFilteredProducts: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching filtered products: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/financial-summary")
    public ResponseEntity<Map<String, Object>> getFinancialSummary(@RequestParam(required = false) Integer year) {
        try {
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
        
        } catch (Exception e) {
            System.err.println("Error in getFinancialSummary: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching financial summary: " + e.getMessage()
            ));
        }
    }
}
