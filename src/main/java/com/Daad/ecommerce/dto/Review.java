package com.Daad.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Review {
	private String id;
	private String productId;
	private String customerId;
	private Integer rating; // 1..5
	private String title;
	private String comment;
	private List<Image> images = new ArrayList<>();
	private Boolean isVerified = false;
	private String status = "approved"; // pending, approved, rejected (default approved per Node code)
	private Helpful helpful = new Helpful();
	private LocalDateTime createdAt = LocalDateTime.now();
	private LocalDateTime updatedAt = LocalDateTime.now();

	public static class Image {
		private String url;
		private String publicId;
		public String getUrl() { return url; }
		public void setUrl(String url) { this.url = url; }
		public String getPublicId() { return publicId; }
		public void setPublicId(String publicId) { this.publicId = publicId; }
	}

	public static class Helpful {
		private Integer count = 0;
		private List<Long> users = new ArrayList<>();
		public Integer getCount() { return count; }
		public void setCount(Integer count) { this.count = count; }
		public List<Long> getUsers() { return users; }
		public void setUsers(List<Long> users) { this.users = users; }
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getProductId() { return productId; }
	public void setProductId(String productId) { this.productId = productId; }
	public String getCustomerId() { return customerId; }
	public void setCustomerId(String customerId) { this.customerId = customerId; }
	public Integer getRating() { return rating; }
	public void setRating(Integer rating) { this.rating = rating; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getComment() { return comment; }
	public void setComment(String comment) { this.comment = comment; }
	public List<Image> getImages() { return images; }
	public void setImages(List<Image> images) { this.images = images; }
	public Boolean getIsVerified() { return isVerified; }
	public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public Helpful getHelpful() { return helpful; }
	public void setHelpful(Helpful helpful) { this.helpful = helpful; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
