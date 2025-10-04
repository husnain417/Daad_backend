package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Product.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CategoryRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Category> categoryRowMapper = new RowMapper<Category>() {
        @Override
        public Category mapRow(ResultSet rs, int rowNum) throws SQLException {
            Category category = new Category();
            category.setId(rs.getString("id"));
            category.setName(rs.getString("name"));
            category.setSlug(rs.getString("slug"));
            category.setDescription(rs.getString("description"));
            category.setImageUrl(rs.getString("image_url"));
            category.setImagePublicId(rs.getString("image_public_id"));
            category.setParentCategoryId(rs.getString("parent_category_id"));
            category.setLevel(rs.getInt("level"));
            category.setIsActive(rs.getBoolean("is_active"));
            return category;
        }
    };
    
    public List<Category> findAll() {
        String sql = "SELECT * FROM categories WHERE is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, categoryRowMapper);
    }
    
    public Optional<Category> findById(String id) {
        String sql = "SELECT * FROM categories WHERE id = ? AND is_active = true LIMIT 1";
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, UUID.fromString(id));
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }
    
    public Optional<Category> findByName(String name) {
        String sql = "SELECT * FROM categories WHERE name ILIKE ? AND is_active = true LIMIT 1";
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, name);
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }
    
    public Optional<Category> findBySlug(String slug) {
        String sql = "SELECT * FROM categories WHERE slug = ? AND is_active = true LIMIT 1";
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, slug);
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }

    // Find parent category by slug
    public Optional<Category> findParentCategoryBySlug(String slug) {
        String sql = "SELECT * FROM categories WHERE slug = ? AND parent_category_id IS NULL AND is_active = true LIMIT 1";
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, slug);
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }
    
    public Category save(Category category) {
        if (category.getId() == null) {
            return insert(category);
        } else {
            return update(category);
        }
    }
    
    private Category insert(Category category) {
        String id = UUID.randomUUID().toString();
        category.setId(id);
        
        String sql = """
            INSERT INTO categories (
                id, name, slug, description, image_url, image_public_id,
                parent_category_id, level, is_active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;

        jdbcTemplate.update(sql,
            UUID.fromString(category.getId()),
            category.getName(),
            category.getSlug(),
            category.getDescription(),
            category.getImageUrl(),
            category.getImagePublicId(),
            category.getParentCategoryId() != null ? UUID.fromString(category.getParentCategoryId()) : null,
            category.getLevel() != null ? category.getLevel() : 0,
            category.getIsActive() != null ? category.getIsActive() : true
        );
        
        return category;
    }
    
    private Category update(Category category) {
        String sql = """
            UPDATE categories SET 
                name = ?, slug = ?, description = ?, image_url = ?, 
                image_public_id = ?, parent_category_id = ?, level = ?, 
                is_active = ?, updated_at = NOW()
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            category.getName(),
            category.getSlug(),
            category.getDescription(),
            category.getImageUrl(),
            category.getImagePublicId(),
            category.getParentCategoryId() != null ? UUID.fromString(category.getParentCategoryId()) : null,
            category.getLevel(),
            category.getIsActive(),
            UUID.fromString(category.getId())
        );

        return category;
    }
    
    public void deleteById(String id) {
        // Hard delete row
        String sql = "DELETE FROM categories WHERE id = ?";
        jdbcTemplate.update(sql, UUID.fromString(id));
    }

    public List<Category> findByParentCategoryId(String parentCategoryId) {
        String sql = "SELECT * FROM categories WHERE parent_category_id = ? AND is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, categoryRowMapper, UUID.fromString(parentCategoryId));
    }

    public List<Category> findTopLevelCategories() {
        String sql = "SELECT * FROM categories WHERE parent_category_id IS NULL AND is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, categoryRowMapper);
    }

    public List<Category> findByLevel(Integer level) {
        String sql = "SELECT * FROM categories WHERE level = ? AND is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, categoryRowMapper, level);
    }

    public boolean existsBySlug(String slug) {
        String sql = "SELECT COUNT(*) FROM categories WHERE slug = ? AND is_active = true";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, slug);
        return count != null && count > 0;
    }

    public boolean existsByNameUnderParent(String name, String parentCategoryId) {
        if (parentCategoryId == null) {
            String sql = "SELECT COUNT(*) FROM categories WHERE name ILIKE ? AND parent_category_id IS NULL AND is_active = true";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
            return count != null && count > 0;
        } else {
            String sql = "SELECT COUNT(*) FROM categories WHERE name ILIKE ? AND parent_category_id = ?::uuid AND is_active = true";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name, UUID.fromString(parentCategoryId));
            return count != null && count > 0;
        }
    }

    public int countProductsInCategory(String categoryId) {
        try {
            String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?::uuid AND is_active = true";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, categoryId);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Category> searchCategories(String searchTerm) {
        String sql = "SELECT * FROM categories WHERE (name ILIKE ? OR description ILIKE ?) AND is_active = true ORDER BY name ASC";
        String searchPattern = "%" + searchTerm + "%";
        return jdbcTemplate.query(sql, categoryRowMapper, searchPattern, searchPattern);
    }
    
    // Find subcategory by parent category name and subcategory name
    public Optional<Category> findSubcategoryByParentAndName(String parentCategoryName, String subcategoryName) {
        String sql = """
            SELECT c.* FROM categories c
            INNER JOIN categories p ON c.parent_category_id = p.id
            WHERE p.name ILIKE ? AND c.name ILIKE ? AND c.is_active = true AND p.is_active = true
            LIMIT 1
            """;
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, parentCategoryName, subcategoryName);
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }
    
    // Find subcategory by parent slug and child slug
    public Optional<Category> findSubcategoryByParentSlugAndChildSlug(String parentSlug, String childSlug) {
        String sql = """
            SELECT c.* FROM categories c
            INNER JOIN categories p ON c.parent_category_id = p.id
            WHERE p.slug = ? AND c.slug = ? AND c.is_active = true AND p.is_active = true
            LIMIT 1
            """;
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, parentSlug, childSlug);
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }

    // Find subcategory by parent (slug or name) and child (slug or name) generically
    public Optional<Category> findSubcategoryByParentAndChildFlexible(String parent, String child) {
        if (parent == null || child == null) {
            return Optional.empty();
        }
        // Try parent slug + child slug
        Optional<Category> bySlugSlug = findSubcategoryByParentSlugAndChildSlug(parent, child);
        if (bySlugSlug.isPresent()) return bySlugSlug;

        // Resolve parent by slug then name
        Optional<Category> parentCat = findParentCategoryBySlug(parent);
        if (parentCat.isEmpty()) {
            parentCat = findParentCategoryByName(parent);
        }
        if (parentCat.isPresent()) {
            String parentId = parentCat.get().getId();
            // Try child by slug under parent id
            String byChildSlugSql = "SELECT * FROM categories WHERE parent_category_id = ?::uuid AND slug = ? AND is_active = true LIMIT 1";
            List<Category> c1 = jdbcTemplate.query(byChildSlugSql, categoryRowMapper, java.util.UUID.fromString(parentId), child);
            if (!c1.isEmpty()) return Optional.of(c1.get(0));
            // Try child by name under parent id
            String byChildNameSql = "SELECT * FROM categories WHERE parent_category_id = ?::uuid AND name ILIKE ? AND is_active = true LIMIT 1";
            List<Category> c2 = jdbcTemplate.query(byChildNameSql, categoryRowMapper, java.util.UUID.fromString(parentId), child);
            if (!c2.isEmpty()) return Optional.of(c2.get(0));
        }

        // Try combined slug "parent-child" on child record directly (for legacy combined slugs)
        String combined = parent + "-" + child;
        Optional<Category> combinedSlug = findBySlug(combined);
        if (combinedSlug.isPresent()) return combinedSlug;

        // As a last resort, try child slug/name without verifying parent (least strict)
        Optional<Category> childOnly = findBySlug(child);
        if (childOnly.isPresent()) return childOnly;
        return findByName(child);
    }
    
    // Find parent category by name
    public Optional<Category> findParentCategoryByName(String parentCategoryName) {
        String sql = "SELECT * FROM categories WHERE name ILIKE ? AND parent_category_id IS NULL AND is_active = true LIMIT 1";
        List<Category> categories = jdbcTemplate.query(sql, categoryRowMapper, parentCategoryName);
        return categories.isEmpty() ? Optional.empty() : Optional.of(categories.get(0));
    }
}
