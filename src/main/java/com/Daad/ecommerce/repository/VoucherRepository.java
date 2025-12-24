package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.model.Voucher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class VoucherRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Voucher> voucherRowMapper = new RowMapper<Voucher>() {
        @Override
        public Voucher mapRow(ResultSet rs, int rowNum) throws SQLException {
            Voucher v = new Voucher();
            v.setId(rs.getString("id"));
            v.setCode(rs.getString("code"));
            v.setType(rs.getString("type"));
            v.setValue(rs.getDouble("value"));
            v.setMinimumOrder(rs.getDouble("minimum_order"));
            double maxDisc = rs.getDouble("maximum_discount");
            if (rs.wasNull()) v.setMaximumDiscount(null); else v.setMaximumDiscount(maxDisc);
            int usageLimit = rs.getInt("usage_limit");
            if (rs.wasNull()) v.setUsageLimit(null); else v.setUsageLimit(usageLimit);
            v.setUsedCount(rs.getInt("used_count"));
            v.setApplicableFor(rs.getString("applicable_for"));
            Timestamp fromTs = rs.getTimestamp("valid_from");
            if (fromTs != null) v.setValidFrom(fromTs.toInstant());
            Timestamp untilTs = rs.getTimestamp("valid_until");
            if (untilTs != null) v.setValidUntil(untilTs.toInstant());
            v.setActive(rs.getBoolean("is_active"));
            v.setCreatedBy(rs.getString("created_by"));
            Timestamp createdTs = rs.getTimestamp("created_at");
            if (createdTs != null) v.setCreatedAt(createdTs.toInstant());
            Timestamp updatedTs = rs.getTimestamp("updated_at");
            if (updatedTs != null) v.setUpdatedAt(updatedTs.toInstant());
            return v;
        }
    };

    public Optional<Voucher> findActiveByCode(String code, Instant now) {
        String sql = """
                SELECT * FROM vouchers
                WHERE code = ? AND is_active = TRUE
                  AND valid_from <= ? AND valid_until >= ?
                LIMIT 1
                """;
        List<Voucher> list = jdbcTemplate.query(sql, voucherRowMapper, code, Timestamp.from(now), Timestamp.from(now));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<Voucher> findAll() {
        String sql = "SELECT * FROM vouchers ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, voucherRowMapper);
    }

    // Find vouchers created by a specific user (vendor)
    public List<Voucher> findByCreatedBy(String userId) {
        String sql = "SELECT * FROM vouchers WHERE created_by = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, voucherRowMapper, UUID.fromString(userId));
    }

    // Find vouchers applicable to a specific vendor
    public List<Voucher> findApplicableToVendor(String vendorId) {
        String sql = """
            SELECT DISTINCT v.* FROM vouchers v
            LEFT JOIN voucher_applicable_items vai ON v.id = vai.voucher_id
            WHERE v.is_active = TRUE
            AND (v.applicable_for = 'all' OR (v.applicable_for = 'vendor' AND vai.applicable_id = ?::uuid))
            ORDER BY v.created_at DESC
            """;
        return jdbcTemplate.query(sql, voucherRowMapper, UUID.fromString(vendorId));
    }

    public Voucher insert(Voucher voucher) {
        String id = UUID.randomUUID().toString();
        voucher.setId(id);
        Instant now = Instant.now();
        voucher.setCreatedAt(now);
        voucher.setUpdatedAt(now);

        String sql = """
                INSERT INTO vouchers (
                    id, code, type, value, minimum_order, maximum_discount,
                    usage_limit, used_count, applicable_for, valid_from,
                    valid_until, is_active, created_by, created_at, updated_at
                ) VALUES (?::uuid, ?, ?::voucher_type, ?, ?, ?, ?, 0, ?::voucher_applicable,
                          ?, ?, ?, ?::uuid, ?, ?)
                """;
        jdbcTemplate.update(sql,
                UUID.fromString(voucher.getId()),
                voucher.getCode(),
                voucher.getType(),
                voucher.getValue(),
                voucher.getMinimumOrder(),
                voucher.getMaximumDiscount(),
                voucher.getUsageLimit(),
                voucher.getApplicableFor() != null ? voucher.getApplicableFor() : "all",
                Timestamp.from(voucher.getValidFrom()),
                Timestamp.from(voucher.getValidUntil()),
                voucher.isActive(),
                UUID.fromString(voucher.getCreatedBy()),
                Timestamp.from(voucher.getCreatedAt()),
                Timestamp.from(voucher.getUpdatedAt())
        );
        return voucher;
    }

    public void incrementUsedCount(String id) {
        String sql = "UPDATE vouchers SET used_count = used_count + 1, updated_at = ? WHERE id = ?::uuid";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), UUID.fromString(id));
    }

    public void deactivate(String id) {
        String sql = "UPDATE vouchers SET is_active = FALSE, updated_at = ? WHERE id = ?::uuid";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), UUID.fromString(id));
    }

    public void delete(String id) {
        String sql = "DELETE FROM vouchers WHERE id = ?::uuid";
        jdbcTemplate.update(sql, UUID.fromString(id));
    }

    // Add applicable items to a voucher
    public void addApplicableItems(String voucherId, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        
        // First, remove existing applicable items for this voucher
        String deleteSql = "DELETE FROM voucher_applicable_items WHERE voucher_id = ?::uuid";
        jdbcTemplate.update(deleteSql, UUID.fromString(voucherId));
        
        // Then, insert new applicable items
        String insertSql = "INSERT INTO voucher_applicable_items (voucher_id, applicable_id, created_at) VALUES (?, ?::uuid, NOW())";
        List<Object[]> batchArgs = itemIds.stream()
            .map(itemId -> new Object[]{UUID.fromString(voucherId), UUID.fromString(itemId)})
            .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(insertSql, batchArgs);
    }
}


