package com.Daad.ecommerce.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Product {
    
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private Category category; // subcategory when level > 0
    private Category parentCategory; // parent/top-level category
    private Vendor vendor;
    private String gender;
    private String ageRange; // Age range for kids products (e.g., "0-3 months", "2-4 years")
    private List<ColorInventory> colorInventories = new ArrayList<>();
    private Integer totalStock = 0;
    private List<Image> defaultImages = new ArrayList<>();
    private List<ColorImageSet> colorImages = new ArrayList<>();
    private Discount discount;
    private BigDecimal averageRating = BigDecimal.ZERO;
    private Boolean isActive = true;
    private String status = "DRAFT";
    private Boolean isCustomersAlsoBought = false;
    private String createdAt;
    private String updatedAt;
    private String referenceId;
    
    // Approval details
    private ApprovalDetails approvalDetails;
    
    public static class ApprovalDetails {
        private String action;
        private String approvedBy;
        private String approvedAt;
        private String reason;
        
        public ApprovalDetails() {}
        
        public ApprovalDetails(String action, String approvedBy, String approvedAt, String reason) {
            this.action = action;
            this.approvedBy = approvedBy;
            this.approvedAt = approvedAt;
            this.reason = reason;
        }
        
        // Getters and Setters
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        
        public String getApprovedAt() { return approvedAt; }
        public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Category getParentCategory() { return parentCategory; }
    public void setParentCategory(Category parentCategory) { this.parentCategory = parentCategory; }
    
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    
    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }
    
    public List<ColorInventory> getColorInventories() { return colorInventories; }
    public void setColorInventories(List<ColorInventory> colorInventories) { this.colorInventories = colorInventories; }
    
    public Integer getTotalStock() { return totalStock; }
    public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }
    
    public List<Image> getDefaultImages() { return defaultImages; }
    public void setDefaultImages(List<Image> defaultImages) { this.defaultImages = defaultImages; }
    
    public List<ColorImageSet> getColorImages() { return colorImages; }
    public void setColorImages(List<ColorImageSet> colorImages) { this.colorImages = colorImages; }
    
    public Discount getDiscount() { return discount; }
    public void setDiscount(Discount discount) { this.discount = discount; }
    
    public BigDecimal getAverageRating() { return averageRating; }
    public void setAverageRating(BigDecimal averageRating) { this.averageRating = averageRating; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Boolean getIsCustomersAlsoBought() { return isCustomersAlsoBought; }
    public void setIsCustomersAlsoBought(Boolean isCustomersAlsoBought) { this.isCustomersAlsoBought = isCustomersAlsoBought; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    
    public ApprovalDetails getApprovalDetails() { return approvalDetails; }
    public void setApprovalDetails(ApprovalDetails approvalDetails) { this.approvalDetails = approvalDetails; }
    
    // Helper methods for inventory management
    public void calculateTotalStock() {
        this.totalStock = colorInventories.stream()
                .flatMap(color -> color.getSizes().stream())
                .mapToInt(SizeInventory::getStock)
                .sum();
    }
    
    public boolean hasStock(String color, String size) {
        return colorInventories.stream()
                .filter(c -> c.getColor().equals(color))
                .flatMap(c -> c.getSizes().stream())
                .anyMatch(s -> s.getSize().equals(size) && s.getStock() > 0);
    }
    
    public int getStock(String color, String size) {
        return colorInventories.stream()
                .filter(c -> c.getColor().equals(color))
                .flatMap(c -> c.getSizes().stream())
                .filter(s -> s.getSize().equals(size))
                .findFirst()
                .map(SizeInventory::getStock)
                .orElse(0);
    }
    
    public void updateStock(String color, String size, int newStock) {
        colorInventories.stream()
                .filter(c -> c.getColor().equals(color))
                .flatMap(c -> c.getSizes().stream())
                .filter(s -> s.getSize().equals(size))
                .findFirst()
                .ifPresent(s -> {
                    s.setStock(newStock);
                    s.setIsAvailable(newStock > 0);
                });
        calculateTotalStock();
    }
    
    public void decrementStock(String color, String size, int quantity) {
        colorInventories.stream()
                .filter(c -> c.getColor().equals(color))
                .flatMap(c -> c.getSizes().stream())
                .filter(s -> s.getSize().equals(size))
                .findFirst()
                .ifPresent(s -> {
                    int newStock = Math.max(0, s.getStock() - quantity);
                    s.setStock(newStock);
                    s.setIsAvailable(newStock > 0);
                });
        calculateTotalStock();
    }
    
    // Inner classes for nested data
    public static class ColorInventory {
        private String color;
        private String colorCode;
        private List<SizeInventory> sizes = new ArrayList<>();
        private boolean isAvailable = true;
        
        public ColorInventory() {}
        
        public ColorInventory(String color, String colorCode) {
            this.color = color;
            this.colorCode = colorCode;
        }
        
        // Getters and Setters
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public String getColorCode() { return colorCode; }
        public void setColorCode(String colorCode) { this.colorCode = colorCode; }
        
        public List<SizeInventory> getSizes() { return sizes; }
        public void setSizes(List<SizeInventory> sizes) { this.sizes = sizes; }
        
        public boolean isAvailable() { return isAvailable; }
        public void setAvailable(boolean available) { isAvailable = available; }
    }
    
    public static class SizeInventory {
        private String size;
        private int stock;
        private boolean isAvailable = true;
        private int minStockThreshold = 5;
        
        public SizeInventory() {}
        
        public SizeInventory(String size, int stock) {
            this.size = size;
            this.stock = stock;
            this.isAvailable = stock > 0;
        }
        
        // Getters and Setters
        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }
        
        public int getStock() { return stock; }
        public void setStock(int stock) { 
            this.stock = stock; 
            this.isAvailable = stock > 0;
        }
        
        public boolean isAvailable() { return isAvailable; }
        public void setIsAvailable(boolean available) { isAvailable = available; }
        
        public int getMinStockThreshold() { return minStockThreshold; }
        public void setMinStockThreshold(int minStockThreshold) { this.minStockThreshold = minStockThreshold; }
        
        public boolean isLowStock() { return stock <= minStockThreshold; }
    }
    
    public static class Category {
        private String id;
        private String name;
        private String slug;
        private String description;
        private String imageUrl;
        private String imagePublicId;
        private String parentCategoryId;
        private Integer level;
        private Boolean isActive;
        
        public Category() {}
        
        public Category(String id, String name, String slug, String description) {
            this.id = id;
            this.name = name;
            this.slug = slug;
            this.description = description;
        }
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        
        public String getImagePublicId() { return imagePublicId; }
        public void setImagePublicId(String imagePublicId) { this.imagePublicId = imagePublicId; }
        
        public String getParentCategoryId() { return parentCategoryId; }
        public void setParentCategoryId(String parentCategoryId) { this.parentCategoryId = parentCategoryId; }
        
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
    
    public static class Vendor {
        private String id;
        private String businessName;
        private String businessType;
        private String status;
        private Double rating;
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getBusinessName() { return businessName; }
        public void setBusinessName(String businessName) { this.businessName = businessName; }
        
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Double getRating() { return rating; }
        public void setRating(Double rating) { this.rating = rating; }
    }
    
    public static class Image {
        private String url;
        private String alt;
        private Boolean isPrimary;
        private String fileId;
        
        // Getters and Setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getAlt() { return alt; }
        public void setAlt(String alt) { this.alt = alt; }
        
        public Boolean getIsPrimary() { return isPrimary; }
        public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
        
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
    }
    
    public static class ColorImageSet {
        private String color;
        private List<Image> images = new ArrayList<>();
        
        // Getters and Setters
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public List<Image> getImages() { return images; }
        public void setImages(List<Image> images) { this.images = images; }
    }
    
    public static class Discount {
        private BigDecimal discountValue;
        private String discountType;
        private String endDate;
        private Boolean isActive;
        
        public Discount() {}
        
        public Discount(BigDecimal discountValue, String discountType, String endDate, Boolean isActive) {
            this.discountValue = discountValue;
            this.discountType = discountType;
            this.endDate = endDate;
            this.isActive = isActive;
        }
        
        // Getters and Setters
        public BigDecimal getDiscountValue() { return discountValue; }
        public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
        
        public String getDiscountType() { return discountType; }
        public void setDiscountType(String discountType) { this.discountType = discountType; }
        
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
}
