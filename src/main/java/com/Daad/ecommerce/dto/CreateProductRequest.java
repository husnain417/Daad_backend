package com.Daad.ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;

public class CreateProductRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String subcategory;
    private String gender;
    private String ageRange;
    private List<ColorInventoryRequest> colorInventories;
    private DiscountRequest discount;
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    
    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }
    
    public List<ColorInventoryRequest> getColorInventories() { return colorInventories; }
    public void setColorInventories(List<ColorInventoryRequest> colorInventories) { this.colorInventories = colorInventories; }
    
    public DiscountRequest getDiscount() { return discount; }
    public void setDiscount(DiscountRequest discount) { this.discount = discount; }
    
    // Inner classes
    public static class ColorInventoryRequest {
        private String color;
        private String colorCode;
        private List<SizeInventoryRequest> sizes;
        
        public ColorInventoryRequest() {}
        
        public ColorInventoryRequest(String color, String colorCode) {
            this.color = color;
            this.colorCode = colorCode;
        }
        
        // Getters and Setters
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public String getColorCode() { return colorCode; }
        public void setColorCode(String colorCode) { this.colorCode = colorCode; }
        
        public List<SizeInventoryRequest> getSizes() { return sizes; }
        public void setSizes(List<SizeInventoryRequest> sizes) { this.sizes = sizes; }
    }
    
    public static class SizeInventoryRequest {
        private String size;
        private int stock;
        
        public SizeInventoryRequest() {}
        
        public SizeInventoryRequest(String size, int stock) {
            this.size = size;
            this.stock = stock;
        }
        
        // Getters and Setters
        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }
        
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }
    
    public static class DiscountRequest {
        private BigDecimal discountValue;
        private String discountType;
        private String endDate;
        private Boolean isActive;
        
        public DiscountRequest() {}
        
        public DiscountRequest(BigDecimal discountValue, String discountType, String endDate, Boolean isActive) {
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
