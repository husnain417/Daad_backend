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
}


