package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.service.OrderEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*")
public class ContactController {

	@Autowired private OrderEmailService orderEmailService;

	@PostMapping("/")
	public ResponseEntity<?> submitContactForm(@RequestBody Map<String, Object> body) {
		try {
			String name = body.get("name") != null ? body.get("name").toString() : null;
			String email = body.get("email") != null ? body.get("email").toString() : null;
			String message = body.get("message") != null ? body.get("message").toString() : null;

			if (name == null || email == null || message == null) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Please provide name, email, and message"));
			}

			if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Please provide a valid email address"));
			}

			orderEmailService.sendContactFormEmail(name, email, message);
			return ResponseEntity.ok(Map.of("success", true, "message", "Your message has been sent. We will get back to you soon!"));
		} catch (Exception e) {
			return ResponseEntity.status(500).body(Map.of("success", false, "error", "There was a problem sending your message. Please try again later."));
		}
	}
}
