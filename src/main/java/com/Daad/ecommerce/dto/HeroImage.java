package com.Daad.ecommerce.dto;

import java.time.LocalDateTime;

public class HeroImage {
    private String pageType; // home, mens, womens
    private String viewType; // web, mobile
    private String imageUrl;
    private String localPath; // Now stores B2 key for deletion
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    
    public String getViewType() { return viewType; }
    public void setViewType(String viewType) { this.viewType = viewType; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    // Renamed for clarity - this now stores the B2 key for deletion purposes
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    
    // Alternative getter name for better understanding
    public String getB2Key() { return localPath; }
    public void setB2Key(String b2Key) { this.localPath = b2Key; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}