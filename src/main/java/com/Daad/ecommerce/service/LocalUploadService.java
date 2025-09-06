package com.Daad.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class LocalUploadService {

    public static class UploadResult {
        public final String url;
        public final String filename;
        public UploadResult(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }
    }

    public UploadResult uploadToLocal(Path sourcePath, String subdirectory) throws IOException {
        Path uploadsRoot = Path.of("uploads");
        Path targetDir = uploadsRoot.resolve(subdirectory);
        Files.createDirectories(targetDir);

        String filename = sourcePath.getFileName().toString();
        Path targetPath = targetDir.resolve(filename);

        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        String url = "/uploads/" + subdirectory + "/" + filename;
        return new UploadResult(url, filename);
    }

    public UploadResult uploadMultipart(MultipartFile file, String subdirectory) throws IOException {
        Path uploadsRoot = Path.of("uploads");
        Path targetDir = uploadsRoot.resolve(subdirectory);
        Files.createDirectories(targetDir);
        
        String original = file.getOriginalFilename();
        String safe = original == null ? "file" : original;
        
        // Strip any path separators and collapse whitespace to underscores
        safe = safe.replaceAll("[\\\\/]+", "");
        safe = safe.replaceAll("\\s+", "_");
        
        // Ensure filename has proper extension
        String filename;
        if (safe.contains(".")) {
            // Original filename has extension
            filename = System.currentTimeMillis() + "-" + safe;
        } else {
            // No extension, try to determine from content type
            String contentType = file.getContentType();
            String extension = "";
            if (contentType != null) {
                if (contentType.startsWith("image/")) {
                    extension = contentType.substring(6); // Remove "image/" prefix
                    if (extension.equals("jpeg")) extension = "jpg";
                }
            }
            filename = System.currentTimeMillis() + "-" + safe + (extension.isEmpty() ? "" : "." + extension);
        }
        
        Path targetPath = targetDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        String url = "/uploads/" + subdirectory + "/" + filename;
        return new UploadResult(url, filename);
    }
}


