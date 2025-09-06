package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.HeroImage;
import com.Daad.ecommerce.repository.HeroImageRepository;
import com.Daad.ecommerce.service.LocalUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/hero-images")
@CrossOrigin(origins = "*")
public class HeroImageController {

	@Autowired private HeroImageRepository heroImageRepository;
	@Autowired private LocalUploadService localUploadService;

	@PostMapping("/{pageType}/{viewType}")
	public ResponseEntity<?> uploadImage(@PathVariable String pageType, @PathVariable String viewType,
											 @RequestPart("image") MultipartFile image) {
		try {
			if (image == null || image.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No file uploaded"));
			}
			var uploaded = localUploadService.uploadMultipart(image, "hero-images/" + pageType);
			HeroImage hi = heroImageRepository.findOne(pageType, viewType).orElse(new HeroImage());
			hi.setPageType(pageType);
			hi.setViewType(viewType);
			hi.setImageUrl(uploaded.url);
			hi.setLocalPath(uploaded.filename);
			heroImageRepository.save(hi);
			return ResponseEntity.ok(Map.of("success", true, "data", hi));
		} catch (Exception e) {
			return ResponseEntity.status(500).body(Map.of("success", false, "message", Objects.toString(e.getMessage(), "Error uploading image")));
		}
	}

	@PostMapping("/{pageType}")
	public ResponseEntity<?> uploadPageImages(@PathVariable String pageType,
											   @RequestPart("webImage") MultipartFile webImage,
											   @RequestPart("mobileImage") MultipartFile mobileImage) {
		try {
			var web = localUploadService.uploadMultipart(webImage, "hero-images/" + pageType);
			var mobile = localUploadService.uploadMultipart(mobileImage, "hero-images/" + pageType);
			HeroImage webImg = heroImageRepository.findOne(pageType, "web").orElse(new HeroImage());
			webImg.setPageType(pageType); webImg.setViewType("web"); webImg.setImageUrl(web.url); webImg.setLocalPath(web.filename);
			heroImageRepository.save(webImg);
			HeroImage mobImg = heroImageRepository.findOne(pageType, "mobile").orElse(new HeroImage());
			mobImg.setPageType(pageType); mobImg.setViewType("mobile"); mobImg.setImageUrl(mobile.url); mobImg.setLocalPath(mobile.filename);
			heroImageRepository.save(mobImg);
			Map<String, Object> data = new HashMap<>();
			data.put("web", webImg);
			data.put("mobile", mobImg);
			return ResponseEntity.ok(Map.of("success", true, "data", data));
		} catch (Exception e) {
			return ResponseEntity.status(500).body(Map.of("success", false, "message", Objects.toString(e.getMessage(), "Error uploading images")));
		}
	}

	@GetMapping("/home")
	public ResponseEntity<?> getHomePageImages() {
		return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
			"web", heroImageRepository.findOne("home", "web").orElse(null),
			"mobile", heroImageRepository.findOne("home", "mobile").orElse(null)
		)));
	}
	@GetMapping("/mens")
	public ResponseEntity<?> getMensPageImages() {
		return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
			"web", heroImageRepository.findOne("mens", "web").orElse(null),
			"mobile", heroImageRepository.findOne("mens", "mobile").orElse(null)
		)));
	}
	@GetMapping("/womens")
	public ResponseEntity<?> getWomensPageImages() {
		return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
			"web", heroImageRepository.findOne("womens", "web").orElse(null),
			"mobile", heroImageRepository.findOne("womens", "mobile").orElse(null)
		)));
	}

	@GetMapping("/")
	public ResponseEntity<?> getAllImages() {
		List<HeroImage> all = heroImageRepository.findAll();
		return ResponseEntity.ok(Map.of("success", true, "data", all));
	}

	@GetMapping("/{pageType}")
	public ResponseEntity<?> getImagesByPage(@PathVariable String pageType) {
		return ResponseEntity.ok(Map.of("success", true, "data", heroImageRepository.findByPageType(pageType)));
	}

	@DeleteMapping("/{pageType}/{viewType}")
	public ResponseEntity<?> deleteImage(@PathVariable String pageType, @PathVariable String viewType) {
		var existing = heroImageRepository.findOne(pageType, viewType);
		if (existing.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Image not found"));
		// In real impl, delete physical file by localPath
		heroImageRepository.delete(pageType, viewType);
		return ResponseEntity.ok(Map.of("success", true, "message", "Image deleted successfully"));
	}
}
