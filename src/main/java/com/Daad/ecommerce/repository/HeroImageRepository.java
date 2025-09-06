package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.HeroImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.OptionalDouble;

@Repository
public class HeroImageRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

            private final RowMapper<HeroImage> heroImageRowMapper = new RowMapper<HeroImage>() {
        @Override
        public HeroImage mapRow(ResultSet rs, int rowNum) throws SQLException {
            HeroImage heroImage = new HeroImage();
            heroImage.setPageType(rs.getString("page_type"));
            heroImage.setViewType(rs.getString("view_type"));
            heroImage.setImageUrl(rs.getString("image_url"));
            heroImage.setLocalPath(rs.getString("cloudinary_id")); // Map cloudinary_id to localPath
            
            // Set timestamps
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                heroImage.setCreatedAt(createdAtTs.toLocalDateTime());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                heroImage.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
            
            return heroImage;
        }
    };

    public HeroImage save(HeroImage img) {
        Optional<HeroImage> existing = findOne(img.getPageType(), img.getViewType());
        if (existing.isEmpty()) {
            return insert(img);
        } else {
            return update(img);
        }
    }
    
    private HeroImage insert(HeroImage img) {
        String sql = """
            INSERT INTO hero_images (
                page_type, view_type, image_url, cloudinary_id, created_at, updated_at
            ) VALUES (?::page_type, ?::view_type, ?, ?, NOW(), NOW())
            """;

        jdbcTemplate.update(sql,
            img.getPageType(),
            img.getViewType(),
            img.getImageUrl(),
            img.getLocalPath()
        );
        
        return img;
    }
    
    private HeroImage update(HeroImage img) {
        String sql = """
            UPDATE hero_images SET 
                image_url = ?, cloudinary_id = ?, updated_at = NOW()
            WHERE page_type = ?::page_type AND view_type = ?::view_type
            """;

        jdbcTemplate.update(sql,
            img.getImageUrl(),
            img.getLocalPath(),
            img.getPageType(),
            img.getViewType()
        );

        return img;
    }

    public Optional<HeroImage> findOne(String pageType, String viewType) {
        String sql = "SELECT * FROM hero_images WHERE page_type = ?::page_type AND view_type = ?::view_type LIMIT 1";
        List<HeroImage> images = jdbcTemplate.query(sql, heroImageRowMapper, pageType, viewType);
        return images.isEmpty() ? Optional.empty() : Optional.of(images.get(0));
    }

    public List<HeroImage> findAll() {
        String sql = "SELECT * FROM hero_images ORDER BY page_type, view_type";
        return jdbcTemplate.query(sql, heroImageRowMapper);
    }

    public List<HeroImage> findByPageType(String pageType) {
        String sql = "SELECT * FROM hero_images WHERE page_type = ?::page_type ORDER BY view_type";
        return jdbcTemplate.query(sql, heroImageRowMapper, pageType);
    }

    public void delete(String pageType, String viewType) {
        String sql = "DELETE FROM hero_images WHERE page_type = ?::page_type AND view_type = ?::view_type";
        jdbcTemplate.update(sql, pageType, viewType);
    }



    public boolean existsByPageTypeAndViewType(String pageType, String viewType) {
        String sql = "SELECT COUNT(*) FROM hero_images WHERE page_type = ?::page_type AND view_type = ?::view_type";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pageType, viewType);
        return count != null && count > 0;
    }
}
