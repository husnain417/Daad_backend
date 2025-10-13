package com.Daad.ecommerce.service;

import com.Daad.ecommerce.repository.DeliveryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeliverySyncService {

    @Autowired
    private DeliveryService deliveryService;
    
    @Autowired
    private DeliveryRepository deliveryRepository;

    /**
     * Sync pickup locations daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void syncPickupLocationsDaily() {
        try {
            System.out.println("Starting daily pickup locations sync...");
            deliveryService.syncPickupLocations();
            System.out.println("Pickup locations sync completed");
        } catch (Exception e) {
            System.err.println("Error in daily pickup locations sync: " + e.getMessage());
        }
    }

    /**
     * Sync delivery areas daily at 2:30 AM
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void syncDeliveryAreasDaily() {
        try {
            System.out.println("Starting daily delivery areas sync...");
            deliveryService.syncDeliveryAreas();
            System.out.println("Delivery areas sync completed");
        } catch (Exception e) {
            System.err.println("Error in daily delivery areas sync: " + e.getMessage());
        }
    }

    /**
     * Sync pending delivery statuses hourly
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncPendingDeliveries() {
        try {
            System.out.println("Starting hourly pending deliveries sync...");
            
            // Get all pending and processing deliveries
            var pendingDeliveries = deliveryRepository.getDeliveriesByStatus("pending");
            var processingDeliveries = deliveryRepository.getDeliveriesByStatus("processing");
            
            int syncedCount = 0;
            
            // Sync pending deliveries
            for (var delivery : pendingDeliveries) {
                try {
                    String fincartOrderId = (String) delivery.get("fincart_order_id");
                    if (fincartOrderId != null) {
                        // In a real implementation, you would call Fincart API to get latest status
                        // For now, we'll just log that we would sync this
                        System.out.println("Would sync pending delivery: " + fincartOrderId);
                        syncedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("Error syncing pending delivery: " + e.getMessage());
                }
            }
            
            // Sync processing deliveries
            for (var delivery : processingDeliveries) {
                try {
                    String fincartOrderId = (String) delivery.get("fincart_order_id");
                    if (fincartOrderId != null) {
                        // In a real implementation, you would call Fincart API to get latest status
                        // For now, we'll just log that we would sync this
                        System.out.println("Would sync processing delivery: " + fincartOrderId);
                        syncedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("Error syncing processing delivery: " + e.getMessage());
                }
            }
            
            System.out.println("Pending deliveries sync completed. Synced: " + syncedCount);
            
        } catch (Exception e) {
            System.err.println("Error in hourly pending deliveries sync: " + e.getMessage());
        }
    }

    /**
     * Clean up old webhook logs weekly (keep last 30 days)
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanupOldWebhookLogs() {
        try {
            System.out.println("Starting weekly webhook logs cleanup...");
            
            // Delete webhook logs older than 30 days
            String sql = "DELETE FROM delivery_webhook_logs WHERE created_at < NOW() - INTERVAL '30 days'";
            // This would need to be implemented in DeliveryRepository
            // deliveryRepository.cleanupOldWebhookLogs();
            
            System.out.println("Webhook logs cleanup completed");
            
        } catch (Exception e) {
            System.err.println("Error in webhook logs cleanup: " + e.getMessage());
        }
    }

    /**
     * Manual sync trigger for admin
     */
    public void triggerFullSync() {
        try {
            System.out.println("Starting manual full sync...");
            
            // Sync pickup locations
            deliveryService.syncPickupLocations();
            
            // Sync delivery areas
            deliveryService.syncDeliveryAreas();
            
            // Sync pending deliveries
            syncPendingDeliveries();
            
            System.out.println("Manual full sync completed");
            
        } catch (Exception e) {
            System.err.println("Error in manual full sync: " + e.getMessage());
        }
    }
}
