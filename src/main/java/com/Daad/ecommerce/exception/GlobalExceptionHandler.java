package com.Daad.ecommerce.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        logger.error("File upload size exceeded: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "File too large. Maximum file size is 50MB.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipartException(MultipartException e) {
        logger.error("Multipart error: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        if (e.getMessage() != null && e.getMessage().contains("file size")) {
            response.put("message", "File too large. Maximum file size is 50MB.");
        } else if (e.getMessage() != null && e.getMessage().contains("file count")) {
            response.put("message", "Too many files. Maximum 10 files allowed.");
        } else {
            response.put("message", "Error processing file upload.");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParsingError(HttpMessageNotReadableException e) {
        logger.error("JSON parsing error: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Invalid JSON format");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        logger.error("Illegal argument: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        if (e.getMessage() != null && e.getMessage().contains("Only image files are allowed")) {
            response.put("message", "Only image files are allowed.");
        } else {
            response.put("message", "Invalid request parameters.");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        logger.error("Server Error: ", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Internal server error");

        if ("development".equals(activeProfile)) {
            response.put("error", e.getMessage());
        } else {
            response.put("error", "Something went wrong");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}


