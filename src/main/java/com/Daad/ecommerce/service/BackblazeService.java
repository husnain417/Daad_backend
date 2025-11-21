package com.Daad.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class BackblazeService {

    @Value("${backblaze.b2.key-id}")
    private String keyId;

    @Value("${backblaze.b2.application-key}")
    private String applicationKey;

    @Value("${backblaze.b2.bucket-name}")
    private String bucketName;

    @Value("${backblaze.b2.endpoint}")
    private String endpoint;

    @Value("${backblaze.b2.region}")
    private String region;

    private S3Client s3Client;

    public static class UploadResult {
        public final String url;
        public final String key;
        public final String filename;
        
        public UploadResult(String url, String key, String filename) {
            this.url = url;
            this.key = key;
            this.filename = filename;
        }
    }

    @PostConstruct
    public void initializeS3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(keyId, applicationKey);
        
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(endpoint))
                .build();
        
        // Test the connection
        testConnection();
    }
    
    public void testConnection() {
        System.out.println("🔍 Testing Backblaze B2 connection...");
        System.out.println("📋 Configuration:");
        System.out.println("   Key ID: " + keyId);
        System.out.println("   Bucket: " + bucketName);
        System.out.println("   Endpoint: " + endpoint);
        System.out.println("   Region: " + region);
        
        try {
            // Test if we can list buckets
            ListBucketsResponse response = s3Client.listBuckets();
            System.out.println("✅ Backblaze B2 connection successful!");
            System.out.println("📦 Available buckets: " + response.buckets().size());
            
            // Test if we can access the specific bucket
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            System.out.println("✅ Bucket '" + bucketName + "' is accessible!");
            
            // Test a simple operation on the bucket
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1)
                    .build();
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            System.out.println("✅ Bucket operations working! Objects in bucket: " + listResponse.contents().size());
            
        } catch (S3Exception e) {
            System.err.println("❌ Backblaze B2 connection failed!");
            System.err.println("   Error Code: " + e.awsErrorDetails().errorCode());
            System.err.println("   Error Message: " + e.getMessage());
            System.err.println("   Status Code: " + e.statusCode());
            
            if (e.statusCode() == 403) {
                System.err.println("🔑 Possible issues:");
                System.err.println("   - Invalid Key ID or Application Key");
                System.err.println("   - Key doesn't have required permissions");
                System.err.println("   - Key is expired or disabled");
            } else if (e.statusCode() == 404) {
                System.err.println("📦 Possible issues:");
                System.err.println("   - Bucket '" + bucketName + "' doesn't exist");
                System.err.println("   - Wrong region or endpoint");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Unexpected error during connection test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public UploadResult uploadMultipart(MultipartFile file, String folder) throws IOException {
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String safeFilename = sanitizeFilename(originalFilename);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String filename = timestamp + "-" + uniqueId + "-" + safeFilename;
        
        // Create S3 key (path in bucket)
        String key = folder + "/" + filename;
        
        // Determine content type
        String contentType = determineContentType(file.getContentType(), safeFilename);
        
        try {
            // Upload to Backblaze B2
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            // Generate public URL
            String publicUrl = String.format("https://%s.s3.%s.backblazeb2.com/%s", bucketName, region, key);
            
            return new UploadResult(publicUrl, key, filename);
            
        } catch (S3Exception e) {
            throw new IOException("Failed to upload file to Backblaze B2: " + e.getMessage(), e);
        }
    }

    /**
     * Download an image from a remote URL and upload it to Backblaze B2.
     * Returns the public URL and key similar to uploadMultipart.
     */
    public UploadResult uploadFromUrl(String imageUrl, String folder) throws IOException {
        // Download bytes from imageUrl
        try (InputStream in = new URL(imageUrl).openStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }

            byte[] bytes = baos.toByteArray();

            // Derive filename from URL path
            String path = new URL(imageUrl).getPath();
            String originalFilename = path.substring(path.lastIndexOf('/') + 1);
            if (originalFilename == null || originalFilename.isBlank()) originalFilename = "file";
            String safeFilename = sanitizeFilename(originalFilename);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String filename = timestamp + "-" + uniqueId + "-" + safeFilename;

            String key = folder + "/" + filename;

            // Attempt to guess content type from filename
            String contentType = determineContentType(null, safeFilename);

            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) bytes.length)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

                String publicUrl = String.format("https://%s.s3.%s.backblazeb2.com/%s", bucketName, region, key);
                return new UploadResult(publicUrl, key, filename);

            } catch (S3Exception e) {
                throw new IOException("Failed to upload file to Backblaze B2: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new IOException("Failed to download image from URL: " + imageUrl + " -> " + e.getMessage(), e);
        }
    }

    public boolean deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (S3Exception e) {
            // Log error but don't throw exception
            System.err.println("Failed to delete file from Backblaze B2: " + e.getMessage());
            return false;
        }
    }

    public boolean fileExists(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            return false;
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return "file";
        }
        
        // Remove path separators and replace spaces with underscores
        String safe = originalFilename.replaceAll("[\\\\/]+", "");
        safe = safe.replaceAll("\\s+", "_");
        
        // Remove any special characters that might cause issues
        safe = safe.replaceAll("[^a-zA-Z0-9._-]", "");
        
        return safe.isEmpty() ? "file" : safe;
    }

    private String determineContentType(String providedContentType, String filename) {
        if (providedContentType != null && !providedContentType.equals("application/octet-stream")) {
            return providedContentType;
        }
        
        // Fallback based on file extension
        if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (filename.toLowerCase().endsWith(".gif")) {
            return "image/gif";
        } else if (filename.toLowerCase().endsWith(".webp")) {
            return "image/webp";
        }
        
        return "application/octet-stream";
    }

    // Method to generate signed URLs for private files (if needed)
    public String generatePresignedUrl(String key, int expirationHours) {
        try {
            GetUrlRequest getUrlRequest = GetUrlRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            return s3Client.utilities().getUrl(getUrlRequest).toString();
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}
