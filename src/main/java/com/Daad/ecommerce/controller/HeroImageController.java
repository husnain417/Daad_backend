package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.HeroImage;
import com.Daad.ecommerce.repository.HeroImageRepository;
import com.Daad.ecommerce.service.BackblazeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/hero-images")
@CrossOrigin(origins = "*")
public class HeroImageController {

    @Autowired 
    private HeroImageRepository heroImageRepository;
    
    @Autowired 
    private BackblazeService backblazeService;

    @PostMapping("/{pageType}/{viewType}")
    public ResponseEntity<?> uploadImage(@PathVariable String pageType, @PathVariable String viewType,
                                         @RequestPart("image") MultipartFile image) {
        try {
            if (image == null || image.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No file uploaded"));
            }
            
            // Get existing hero image to delete old file if exists
            HeroImage existingImage = heroImageRepository.findOne(pageType, viewType).orElse(null);
            
            // Upload new image to Backblaze B2
            BackblazeService.UploadResult uploadResult = backblazeService.uploadMultipart(
                image, 
                "hero-images/" + pageType
            );
            
            // Delete old image from Backblaze if exists
            if (existingImage != null && existingImage.getLocalPath() != null) {
                backblazeService.deleteFile(existingImage.getLocalPath());
            }
            
            // Create or update hero image record
            HeroImage heroImage = existingImage != null ? existingImage : new HeroImage();
            heroImage.setPageType(pageType);
            heroImage.setViewType(viewType);
            heroImage.setImageUrl(uploadResult.url);
            heroImage.setLocalPath(uploadResult.key); // Store B2 key for deletion
            
            heroImageRepository.save(heroImage);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "data", heroImage,
                "storageProvider", "Backblaze B2"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false, 
                "message", Objects.toString(e.getMessage(), "Error uploading image")
            ));
        }
    }

    @PostMapping("/{pageType}")
    public ResponseEntity<?> uploadPageImages(@PathVariable String pageType,
                                              @RequestPart("webImage") MultipartFile webImage,
                                              @RequestPart("mobileImage") MultipartFile mobileImage) {
        try {
            // Get existing images for deletion
            HeroImage existingWeb = heroImageRepository.findOne(pageType, "web").orElse(null);
            HeroImage existingMobile = heroImageRepository.findOne(pageType, "mobile").orElse(null);
            
            // Upload web image
            BackblazeService.UploadResult webUpload = backblazeService.uploadMultipart(
                webImage, 
                "hero-images/" + pageType
            );
            
            // Upload mobile image
            BackblazeService.UploadResult mobileUpload = backblazeService.uploadMultipart(
                mobileImage, 
                "hero-images/" + pageType
            );
            
            // Delete old images if they exist
            if (existingWeb != null && existingWeb.getLocalPath() != null) {
                backblazeService.deleteFile(existingWeb.getLocalPath());
            }
            if (existingMobile != null && existingMobile.getLocalPath() != null) {
                backblazeService.deleteFile(existingMobile.getLocalPath());
            }
            
            // Create/update web image
            HeroImage webImg = existingWeb != null ? existingWeb : new HeroImage();
            webImg.setPageType(pageType);
            webImg.setViewType("web");
            webImg.setImageUrl(webUpload.url);
            webImg.setLocalPath(webUpload.key);
            heroImageRepository.save(webImg);
            
            // Create/update mobile image
            HeroImage mobileImg = existingMobile != null ? existingMobile : new HeroImage();
            mobileImg.setPageType(pageType);
            mobileImg.setViewType("mobile");
            mobileImg.setImageUrl(mobileUpload.url);
            mobileImg.setLocalPath(mobileUpload.key);
            heroImageRepository.save(mobileImg);
            
            Map<String, Object> data = new HashMap<>();
            data.put("web", webImg);
            data.put("mobile", mobileImg);
            data.put("storageProvider", "Backblaze B2");
            
            return ResponseEntity.ok(Map.of("success", true, "data", data));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false, 
                "message", Objects.toString(e.getMessage(), "Error uploading images")
            ));
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
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Image not found"));
        }
        
        HeroImage heroImage = existing.get();
        
        // Delete from Backblaze B2
        if (heroImage.getLocalPath() != null) {
            backblazeService.deleteFile(heroImage.getLocalPath());
        }
        
        // Delete from database
        heroImageRepository.delete(pageType, viewType);
        
        return ResponseEntity.ok(Map.of("success", true, "message", "Image deleted successfully"));
    }
}