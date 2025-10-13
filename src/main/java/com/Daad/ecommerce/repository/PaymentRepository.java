package com.Daad.ecommerce.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbcTemplate;
    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertPaymentTransaction(String orderId, String provider, String transactionId, String paymentReference, double amount, String currency, String status, String providerResponseJson) {
        String sql = "INSERT INTO payment_transactions (order_id, payment_provider, transaction_id, payment_reference, amount, currency, status, provider_response) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, to_json(?::text))";
        jdbcTemplate.update(sql, orderId, provider, transactionId, paymentReference, amount, currency, status, providerResponseJson);
    }

    public void updatePaymentTransactionStatus(String transactionId, String status, String webhookDataJson) {
        String sql = "UPDATE payment_transactions SET status = ?, webhook_data = to_json(?::text), updated_at = NOW() WHERE transaction_id = ?";
        jdbcTemplate.update(sql, status, webhookDataJson, transactionId);
    }

    public void updateOrderPaymentFields(String orderId, Map<String, Object> fields) {
        String sql = "UPDATE orders SET payment_provider = ?, payment_reference = ?, transaction_id = ?, payment_status = ?, paid_at = ?, failure_reason = ?, payment_metadata = to_json(?::text), updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql,
                fields.get("payment_provider"),
                fields.get("payment_reference"),
                fields.get("transaction_id"),
                fields.get("payment_status"),
                fields.get("paid_at"),
                fields.get("failure_reason"),
                fields.get("payment_metadata"),
                java.util.UUID.fromString(orderId)
        );
    }

    // Insert refund transaction record
    public void insertRefundTransaction(String orderId, String transactionId, String refundType, 
                                       double amount, String currency, String status, String reason, String initiatedBy, String paymobResponse) {
        String sql = "INSERT INTO refund_transactions (order_id, transaction_id, refund_type, amount, currency, status, reason, initiated_by, paymob_response) " +
                     "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid, to_json(?::text))";
        jdbcTemplate.update(sql, orderId, transactionId, refundType, amount, currency, status, reason, initiatedBy, paymobResponse);
    }
    
    // Update refund transaction status
    public void updateRefundTransactionStatus(String refundId, String status, String paymobRefundId, String paymobResponse, String errorMessage) {
        String sql = "UPDATE refund_transactions SET status = ?, paymob_refund_id = ?, paymob_response = to_json(?::text), error_message = ?, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, status, paymobRefundId, paymobResponse, errorMessage, refundId);
    }
    
    // Update order refund fields
    public void updateOrderRefundStatus(String orderId, Map<String, Object> refundFields) {
        String sql = "UPDATE orders SET refund_status = ?, refund_amount = ?, refund_reference = ?, refunded_at = ?, refund_reason = ?, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, 
            refundFields.get("refund_status"),
            refundFields.get("refund_amount"),
            refundFields.get("refund_reference"),
            refundFields.get("refunded_at"),
            refundFields.get("refund_reason"),
            orderId
        );
    }

    public Map<String, Object> getTransactionDetails(String transactionId) {
        String sql = "SELECT order_id, status, created_at, amount, currency, payment_provider FROM payment_transactions WHERE transaction_id = ? LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{transactionId}, rs -> {
            if (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("order_id", rs.getString("order_id"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getTimestamp("created_at"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("currency", rs.getString("currency"));
                row.put("payment_provider", rs.getString("payment_provider"));
                return row;
            }
            return null;
        });
    }

    public Map<String, Object> getLatestTransactionForOrder(String orderId) {
        String sql = "SELECT transaction_id, status, created_at, amount, currency, payment_provider FROM payment_transactions WHERE order_id = ?::uuid ORDER BY created_at DESC LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{orderId}, rs -> {
            if (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("transaction_id", rs.getString("transaction_id"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getTimestamp("created_at"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("currency", rs.getString("currency"));
                row.put("payment_provider", rs.getString("payment_provider"));
                return row;
            }
            return null;
        });
    }

    // Get transaction details by transaction_id
    public Map<String, Object> getTransactionByTransactionId(String transactionId) {
        String sql = "SELECT * FROM payment_transactions WHERE transaction_id = ? LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{transactionId}, rs -> {
            if (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("order_id", rs.getString("order_id"));
                row.put("transaction_id", rs.getString("transaction_id"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getTimestamp("created_at"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("currency", rs.getString("currency"));
                row.put("payment_provider", rs.getString("payment_provider"));
                row.put("is_refunded", rs.getBoolean("is_refunded"));
                row.put("refund_id", rs.getString("refund_id"));
                return row;
            }
            return null;
        });
    }
    
    // Get transaction details by order_id
    public Map<String, Object> getTransactionByOrderId(String orderId) {
        String sql = "SELECT * FROM payment_transactions WHERE order_id = ?::uuid AND status = 'completed' ORDER BY created_at DESC LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{orderId}, rs -> {
            if (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("order_id", rs.getString("order_id"));
                row.put("transaction_id", rs.getString("transaction_id"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getTimestamp("created_at"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("currency", rs.getString("currency"));
                row.put("payment_provider", rs.getString("payment_provider"));
                row.put("is_refunded", rs.getBoolean("is_refunded"));
                row.put("refund_id", rs.getString("refund_id"));
                return row;
            }
            return null;
        });
    }
    
    // Mark payment transaction as refunded
    public void markTransactionAsRefunded(String transactionId, String refundId) {
        String sql = "UPDATE payment_transactions SET is_refunded = TRUE, refund_id = ?, refunded_at = NOW(), updated_at = NOW() WHERE transaction_id = ?";
        jdbcTemplate.update(sql, refundId, transactionId);
    }

    public Map<String, Object> getLatestRefundForOrder(String orderId) {
        String sql = "SELECT id, refund_type, amount, currency, status, paymob_refund_id, reason, error_message, created_at, updated_at FROM refund_transactions WHERE order_id = ?::uuid ORDER BY created_at DESC LIMIT 1";
        return jdbcTemplate.query(sql, new Object[]{orderId}, rs -> {
            if (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("refund_type", rs.getString("refund_type"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("currency", rs.getString("currency"));
                row.put("status", rs.getString("status"));
                row.put("paymob_refund_id", rs.getString("paymob_refund_id"));
                row.put("reason", rs.getString("reason"));
                row.put("error_message", rs.getString("error_message"));
                row.put("created_at", rs.getTimestamp("created_at"));
                row.put("updated_at", rs.getTimestamp("updated_at"));
                return row;
            }
            return null;
        });
    }
}


