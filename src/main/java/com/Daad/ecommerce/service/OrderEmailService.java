package com.Daad.ecommerce.service;

import com.Daad.ecommerce.dto.Order;
import org.springframework.stereotype.Service;

@Service
public class OrderEmailService {
	public void sendOrderConfirmationToCustomer(Order order, String email) {
		System.out.println("[Email] Order confirmation sent to " + email + " for order " + order.getId());
	}

	public void sendNewOrderEmailToAdmin(Order order, String email) {
		System.out.println("[Email] New order notification to admin. Customer email: " + email + ", order " + order.getId());
	}

	public void sendOrderStatusUpdateToCustomer(Order order, String email) {
		System.out.println("[Email] Order status update to " + email + ": status=" + order.getOrderStatus());
	}

	public void sendContactFormEmail(String name, String email, String message) {
		System.out.println("[Email] Contact form: from=" + name + " <" + email + "> message=" + message);
	}
}
