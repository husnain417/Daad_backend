package com.Daad.ecommerce.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class VendorPayoutRepository {

    private final JdbcTemplate jdbcTemplate;

    public VendorPayoutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertPayout(String vendorId,
                             String orderId,
                             double grossAmount,
                             double commissionAmount,
                             double netAmount,
                             LocalDateTime scheduledFor,
                             Map<String, Object> bankSnapshot) {
        String sql = "INSERT INTO vendor_payouts (vendor_id, order_id, gross_amount, commission_amount, net_amount, status, scheduled_for, bank_account_number, bank_routing_number, bank_account_holder_name, bank_name) VALUES (?::uuid, ?::uuid, ?, ?, ?, 'pending', ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                UUID.fromString(vendorId),
                UUID.fromString(orderId),
                grossAmount,
                commissionAmount,
                netAmount,
                scheduledFor,
                bankSnapshot.getOrDefault("bank_account_number", null),
                bankSnapshot.getOrDefault("bank_routing_number", null),
                bankSnapshot.getOrDefault("bank_account_holder_name", null),
                bankSnapshot.getOrDefault("bank_name", null)
        );
    }

    public List<Map<String, Object>> findDuePayouts(int limit) {
        String sql = "SELECT vp.* FROM vendor_payouts vp JOIN orders o ON o.id = vp.order_id WHERE vp.status = 'pending' AND vp.scheduled_for <= NOW() AND o.order_status != 'cancelled' ORDER BY vp.scheduled_for ASC LIMIT ?";
        return jdbcTemplate.query(sql, new Object[]{limit}, (rs, rowNum) -> mapRow(rs));
    }

    public void markProcessing(String payoutId) {
        String sql = "UPDATE vendor_payouts SET status = 'processing', updated_at = NOW() WHERE id = ?::uuid AND status = 'pending'";
        jdbcTemplate.update(sql, UUID.fromString(payoutId));
    }

    public void markCompleted(String payoutId, String paymobPayoutId, String responseJson) {
        String sql = "UPDATE vendor_payouts SET status = 'completed', paymob_payout_id = ?, paymob_response = to_json(?::text), processed_at = NOW(), updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, paymobPayoutId, responseJson, UUID.fromString(payoutId));
    }

    public void markProcessingWithResponse(String payoutId, String paymobPayoutId, String responseJson) {
        String sql = "UPDATE vendor_payouts SET status = 'processing', paymob_payout_id = ?, paymob_response = to_json(?::text), updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, paymobPayoutId, responseJson, UUID.fromString(payoutId));
    }

    public void markFailed(String payoutId, String errorMessage) {
        String sql = "UPDATE vendor_payouts SET status = 'failed', retry_count = retry_count + 1, error_message = ?, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, errorMessage, UUID.fromString(payoutId));
    }

    public void rescheduleForRetry(String payoutId, int minutes) {
        String sql = "UPDATE vendor_payouts SET status = 'pending', scheduled_for = NOW() + (INTERVAL '1 minute' * ?), updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, minutes, UUID.fromString(payoutId));
    }

    public Map<String, Object> findById(String payoutId) {
        String sql = "SELECT * FROM vendor_payouts WHERE id = ?::uuid";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, new Object[]{UUID.fromString(payoutId)}, (rs, rowNum) -> mapRow(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void cancelPendingByOrderId(String orderId, String reason) {
        String sql = "UPDATE vendor_payouts SET status = 'cancelled', error_message = ?, cancellation_reason = ?, cancelled_at = NOW(), updated_at = NOW() WHERE order_id = ?::uuid AND status = 'pending'";
        jdbcTemplate.update(sql, reason, reason, UUID.fromString(orderId));
    }

    public void insertWebhookLog(String paymobPayoutId, String eventType, String payloadJson) {
        String sql = "INSERT INTO payout_webhook_logs (paymob_payout_id, event_type, payload) VALUES (?, ?, to_json(?::text))";
        jdbcTemplate.update(sql, paymobPayoutId, eventType, payloadJson);
    }

    public Boolean isWebhookProcessed(String paymobPayoutId) {
        String sql = "SELECT processed FROM payout_webhook_logs WHERE paymob_payout_id = ? AND processed = TRUE ORDER BY created_at DESC LIMIT 1";
        List<Boolean> list = jdbcTemplate.query(sql, new Object[]{paymobPayoutId}, (rs, i) -> rs.getBoolean("processed"));
        return list.isEmpty() ? false : list.get(0);
    }

    public void markWebhookProcessed(String paymobPayoutId) {
        String sql = "UPDATE payout_webhook_logs SET processed = TRUE, processed_at = NOW() WHERE paymob_payout_id = ? AND processed = FALSE";
        jdbcTemplate.update(sql, paymobPayoutId);
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("vendor_id", rs.getString("vendor_id"));
        row.put("order_id", rs.getString("order_id"));
        row.put("gross_amount", rs.getBigDecimal("gross_amount"));
        row.put("commission_amount", rs.getBigDecimal("commission_amount"));
        row.put("net_amount", rs.getBigDecimal("net_amount"));
        row.put("status", rs.getString("status"));
        row.put("scheduled_for", rs.getTimestamp("scheduled_for"));
        row.put("processed_at", rs.getTimestamp("processed_at"));
        row.put("paymob_payout_id", rs.getString("paymob_payout_id"));
        row.put("retry_count", rs.getInt("retry_count"));
        row.put("error_message", rs.getString("error_message"));
        row.put("bank_account_number", rs.getString("bank_account_number"));
        row.put("bank_routing_number", rs.getString("bank_routing_number"));
        row.put("bank_account_holder_name", rs.getString("bank_account_holder_name"));
        row.put("bank_name", rs.getString("bank_name"));
        return row;
    }
}


