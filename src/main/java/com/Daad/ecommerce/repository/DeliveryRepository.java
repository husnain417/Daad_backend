package com.Daad.ecommerce.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DeliveryRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Pickup Locations
    public void insertPickupLocation(String vendorId, String fincartLocationId, String name, 
                                   String city, String area, String address, String contactPerson, String contactPhone) {
        String sql = "INSERT INTO pickup_locations (vendor_id, fincart_location_id, name, city, area, address, contact_person, contact_phone) " +
                    "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, vendorId, fincartLocationId, name, city, area, address, contactPerson, contactPhone);
    }

    public void updatePickupLocation(String id, String name, String city, String area, String address, 
                                   String contactPerson, String contactPhone, boolean isActive) {
        String sql = "UPDATE pickup_locations SET name = ?, city = ?, area = ?, address = ?, " +
                    "contact_person = ?, contact_phone = ?, is_active = ?, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, name, city, area, address, contactPerson, contactPhone, isActive, id);
    }

    public List<Map<String, Object>> getAllPickupLocations() {
        String sql = "SELECT * FROM pickup_locations WHERE is_active = TRUE ORDER BY name";
        return jdbcTemplate.query(sql, this::mapPickupLocationRow);
    }

    public Map<String, Object> getPickupLocationByVendor(String vendorId) {
        String sql = "SELECT * FROM pickup_locations WHERE vendor_id = ?::uuid AND is_active = TRUE LIMIT 1";
        List<Map<String, Object>> results = jdbcTemplate.query(sql, new Object[]{vendorId}, this::mapPickupLocationRow);
        return results.isEmpty() ? null : results.get(0);
    }

    public Map<String, Object> getPickupLocationByFincartId(String fincartLocationId) {
        String sql = "SELECT * FROM pickup_locations WHERE fincart_location_id = ? LIMIT 1";
        List<Map<String, Object>> results = jdbcTemplate.query(sql, new Object[]{fincartLocationId}, this::mapPickupLocationRow);
        return results.isEmpty() ? null : results.get(0);
    }

    // Delivery Areas
    public void insertDeliveryArea(String city, String area, boolean isAvailable) {
        String sql = "INSERT INTO delivery_areas (city, area, is_available) VALUES (?, ?, ?) " +
                    "ON CONFLICT (city, area) DO UPDATE SET is_available = EXCLUDED.is_available, updated_at = NOW()";
        jdbcTemplate.update(sql, city, area, isAvailable);
    }

    public List<Map<String, Object>> getAvailableDeliveryAreas() {
        String sql = "SELECT * FROM delivery_areas WHERE is_available = TRUE ORDER BY city, area";
        return jdbcTemplate.query(sql, this::mapDeliveryAreaRow);
    }

    public List<Map<String, Object>> getAreasByCity(String city) {
        String sql = "SELECT * FROM delivery_areas WHERE city = ? AND is_available = TRUE ORDER BY area";
        return jdbcTemplate.query(sql, new Object[]{city}, this::mapDeliveryAreaRow);
    }

    // Deliveries
    public void insertDelivery(String orderId, String vendorId, String fincartOrderId, String fincartOrderCode,
                             String paymentType, int amountCents, String serviceType, String packageType,
                             int noOfItems, String description, String referenceNumber, String pickupLocationId) {
        String sql = "INSERT INTO deliveries (order_id, vendor_id, fincart_order_id, fincart_order_code, " +
                    "payment_type, amount_cents, service_type, package_type, no_of_items, description, " +
                    "reference_number, pickup_location_id) " +
                    "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid)";
        jdbcTemplate.update(sql, orderId, vendorId, fincartOrderId, fincartOrderCode, paymentType, 
                          amountCents, serviceType, packageType, noOfItems, description, referenceNumber, pickupLocationId);
    }

    public void updateDeliveryStatus(String fincartOrderId, String status, String subStatus, 
                                   String rejectionReason, String supportNote, String trackingNumber,
                                   String returnTrackingNumber, String courier, String courierLogo,
                                   Boolean invoiced, Long invoicedAt) {
        String sql = "UPDATE deliveries SET status = ?, sub_status = ?, rejection_reason = ?, " +
                    "support_note = ?, tracking_number = ?, return_tracking_number = ?, courier = ?, " +
                    "courier_logo = ?, invoiced = ?, invoiced_at = CASE WHEN ? IS NOT NULL THEN " +
                    "to_timestamp(?/1000) ELSE invoiced_at END, updated_at = NOW() " +
                    "WHERE fincart_order_id = ?";
        jdbcTemplate.update(sql, status, subStatus, rejectionReason, supportNote, trackingNumber,
                          returnTrackingNumber, courier, courierLogo, invoiced, invoicedAt, invoicedAt, fincartOrderId);
    }

    public Map<String, Object> getDeliveryByFincartOrderId(String fincartOrderId) {
        String sql = "SELECT * FROM deliveries WHERE fincart_order_id = ? LIMIT 1";
        List<Map<String, Object>> results = jdbcTemplate.query(sql, new Object[]{fincartOrderId}, this::mapDeliveryRow);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Map<String, Object>> getDeliveriesByOrderId(String orderId) {
        String sql = "SELECT d.*, v.business_name as vendor_name, pl.name as pickup_location_name " +
                    "FROM deliveries d " +
                    "LEFT JOIN vendors v ON v.id = d.vendor_id " +
                    "LEFT JOIN pickup_locations pl ON pl.id = d.pickup_location_id " +
                    "WHERE d.order_id = ?::uuid ORDER BY d.created_at";
        return jdbcTemplate.query(sql, new Object[]{orderId}, this::mapDeliveryRow);
    }

    public List<Map<String, Object>> getDeliveriesByVendorId(String vendorId) {
        String sql = "SELECT d.*, o.id as order_number, o.shipping_full_name as customer_name " +
                    "FROM deliveries d " +
                    "LEFT JOIN orders o ON o.id = d.order_id " +
                    "WHERE d.vendor_id = ?::uuid ORDER BY d.created_at DESC";
        return jdbcTemplate.query(sql, new Object[]{vendorId}, this::mapDeliveryRow);
    }

    public List<Map<String, Object>> getDeliveriesByStatus(String status) {
        String sql = "SELECT d.*, v.business_name as vendor_name, o.id as order_number, o.shipping_full_name as customer_name " +
                    "FROM deliveries d " +
                    "LEFT JOIN vendors v ON v.id = d.vendor_id " +
                    "LEFT JOIN orders o ON o.id = d.order_id " +
                    "WHERE d.status = ? ORDER BY d.created_at DESC";
        return jdbcTemplate.query(sql, new Object[]{status}, this::mapDeliveryRow);
    }

    // Delivery Logs
    public void insertDeliveryLog(String deliveryId, String status, String subStatus, String rejectionReason,
                                String supportNote, String trackingNumber, String returnTrackingNumber,
                                String courier, String courierLogo, Boolean invoiced, Long invoicedAt, String notes) {
        String sql = "INSERT INTO delivery_logs (delivery_id, status, sub_status, rejection_reason, " +
                    "support_note, tracking_number, return_tracking_number, courier, courier_logo, " +
                    "invoiced, invoiced_at, notes) " +
                    "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                    "CASE WHEN ? IS NOT NULL THEN to_timestamp(?/1000) ELSE NULL END, ?)";
        jdbcTemplate.update(sql, deliveryId, status, subStatus, rejectionReason, supportNote, trackingNumber,
                          returnTrackingNumber, courier, courierLogo, invoiced, invoicedAt, invoicedAt, notes);
    }

    public List<Map<String, Object>> getDeliveryLogs(String deliveryId) {
        String sql = "SELECT * FROM delivery_logs WHERE delivery_id = ?::uuid ORDER BY logged_at ASC";
        return jdbcTemplate.query(sql, new Object[]{deliveryId}, this::mapDeliveryLogRow);
    }

    // Webhook Logs
    public void insertWebhookLog(String fincartOrderId, String eventType, String payload) {
        String sql = "INSERT INTO delivery_webhook_logs (fincart_order_id, event_type, payload) VALUES (?, ?, to_json(?::text))";
        jdbcTemplate.update(sql, fincartOrderId, eventType, payload);
    }

    public void markWebhookProcessed(String webhookId) {
        String sql = "UPDATE delivery_webhook_logs SET processed = TRUE, processed_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, webhookId);
    }

    public boolean isWebhookProcessed(String fincartOrderId, String eventType) {
        String sql = "SELECT COUNT(*) FROM delivery_webhook_logs WHERE fincart_order_id = ? AND event_type = ? AND processed = TRUE";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{fincartOrderId, eventType}, Integer.class);
        return count != null && count > 0;
    }

    // Fincart Config
    public void updateFincartToken(String accessToken, long expiresAt) {
        String sql = "INSERT INTO fincart_config (access_token, token_expires_at) VALUES (?, to_timestamp(?)) " +
                    "ON CONFLICT (id) DO UPDATE SET access_token = EXCLUDED.access_token, " +
                    "token_expires_at = EXCLUDED.token_expires_at, updated_at = NOW()";
        jdbcTemplate.update(sql, accessToken, expiresAt);
    }

    public Map<String, Object> getFincartConfig() {
        String sql = "SELECT * FROM fincart_config WHERE is_active = TRUE ORDER BY created_at DESC LIMIT 1";
        List<Map<String, Object>> results = jdbcTemplate.query(sql, this::mapFincartConfigRow);
        return results.isEmpty() ? null : results.get(0);
    }

    // Row Mappers
    private Map<String, Object> mapPickupLocationRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("vendor_id", rs.getString("vendor_id"));
        row.put("fincart_location_id", rs.getString("fincart_location_id"));
        row.put("name", rs.getString("name"));
        row.put("city", rs.getString("city"));
        row.put("area", rs.getString("area"));
        row.put("address", rs.getString("address"));
        row.put("contact_person", rs.getString("contact_person"));
        row.put("contact_phone", rs.getString("contact_phone"));
        row.put("is_active", rs.getBoolean("is_active"));
        row.put("created_at", rs.getTimestamp("created_at"));
        row.put("updated_at", rs.getTimestamp("updated_at"));
        return row;
    }

    private Map<String, Object> mapDeliveryAreaRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("city", rs.getString("city"));
        row.put("area", rs.getString("area"));
        row.put("is_available", rs.getBoolean("is_available"));
        row.put("created_at", rs.getTimestamp("created_at"));
        row.put("updated_at", rs.getTimestamp("updated_at"));
        return row;
    }

    private Map<String, Object> mapDeliveryRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("order_id", rs.getString("order_id"));
        row.put("vendor_id", rs.getString("vendor_id"));
        row.put("fincart_order_id", rs.getString("fincart_order_id"));
        row.put("fincart_order_code", rs.getString("fincart_order_code"));
        row.put("tracking_number", rs.getString("tracking_number"));
        row.put("return_tracking_number", rs.getString("return_tracking_number"));
        row.put("courier", rs.getString("courier"));
        row.put("courier_logo", rs.getString("courier_logo"));
        row.put("status", rs.getString("status"));
        row.put("sub_status", rs.getString("sub_status"));
        row.put("rejection_reason", rs.getString("rejection_reason"));
        row.put("support_note", rs.getString("support_note"));
        row.put("invoiced", rs.getBoolean("invoiced"));
        row.put("invoiced_at", rs.getTimestamp("invoiced_at"));
        row.put("pickup_location_id", rs.getString("pickup_location_id"));
        row.put("payment_type", rs.getString("payment_type"));
        row.put("amount_cents", rs.getInt("amount_cents"));
        row.put("service_type", rs.getString("service_type"));
        row.put("package_type", rs.getString("package_type"));
        row.put("no_of_items", rs.getInt("no_of_items"));
        row.put("description", rs.getString("description"));
        row.put("reference_number", rs.getString("reference_number"));
        row.put("created_at", rs.getTimestamp("created_at"));
        row.put("updated_at", rs.getTimestamp("updated_at"));
        
        // Additional fields from joins
        try {
            row.put("vendor_name", rs.getString("vendor_name"));
        } catch (SQLException e) {
            // Field doesn't exist in this query
        }
        try {
            row.put("pickup_location_name", rs.getString("pickup_location_name"));
        } catch (SQLException e) {
            // Field doesn't exist in this query
        }
        try {
            row.put("order_number", rs.getString("order_number"));
        } catch (SQLException e) {
            // Field doesn't exist in this query
        }
        try {
            row.put("customer_name", rs.getString("customer_name"));
        } catch (SQLException e) {
            // Field doesn't exist in this query
        }
        
        return row;
    }

    private Map<String, Object> mapDeliveryLogRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("delivery_id", rs.getString("delivery_id"));
        row.put("status", rs.getString("status"));
        row.put("sub_status", rs.getString("sub_status"));
        row.put("rejection_reason", rs.getString("rejection_reason"));
        row.put("support_note", rs.getString("support_note"));
        row.put("tracking_number", rs.getString("tracking_number"));
        row.put("return_tracking_number", rs.getString("return_tracking_number"));
        row.put("courier", rs.getString("courier"));
        row.put("courier_logo", rs.getString("courier_logo"));
        row.put("invoiced", rs.getBoolean("invoiced"));
        row.put("invoiced_at", rs.getTimestamp("invoiced_at"));
        row.put("logged_at", rs.getTimestamp("logged_at"));
        row.put("notes", rs.getString("notes"));
        return row;
    }

    private Map<String, Object> mapFincartConfigRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("access_token", rs.getString("access_token"));
        row.put("token_expires_at", rs.getTimestamp("token_expires_at"));
        row.put("webhook_secret", rs.getString("webhook_secret"));
        row.put("base_url", rs.getString("base_url"));
        row.put("is_active", rs.getBoolean("is_active"));
        row.put("created_at", rs.getTimestamp("created_at"));
        row.put("updated_at", rs.getTimestamp("updated_at"));
        return row;
    }
}
