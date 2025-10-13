# Commission Management API Documentation

## Overview
This document provides complete API documentation for managing vendor commission rates in the admin dashboard.

## API Endpoints

### 1. Get All Vendors with Commission Information
**Endpoint:** `GET /api/admin/vendors/commission`  
**Authorization:** Admin only  
**Description:** Retrieves a list of all vendors with their commission rates and basic information.

#### Request
```http
GET /api/admin/vendors/commission
Authorization: Bearer <admin_jwt_token>
```

#### Response
```json
{
  "success": true,
  "count": 3,
  "data": [
    {
      "vendorId": "123e4567-e89b-12d3-a456-426614174000",
      "businessName": "Fashion Store",
      "businessType": "business",
      "status": "approved",
      "commission": 10.00,
      "ratingAverage": 4.5,
      "ratingCount": 25,
      "totalSales": 15000.00,
      "totalOrders": 45,
      "vendorCreatedAt": "2024-01-15T10:30:00Z",
      "user": {
        "userId": "456e7890-e89b-12d3-a456-426614174001",
        "username": "fashionstore_owner",
        "email": "owner@fashionstore.com",
        "profilePicUrl": "https://example.com/profile.jpg",
        "isVerified": true,
        "userCreatedAt": "2024-01-10T08:00:00Z"
      }
    },
    {
      "vendorId": "789e0123-e89b-12d3-a456-426614174002",
      "businessName": "Tech Gadgets",
      "businessType": "company",
      "status": "approved",
      "commission": 12.50,
      "ratingAverage": 4.8,
      "ratingCount": 18,
      "totalSales": 8500.00,
      "totalOrders": 32,
      "vendorCreatedAt": "2024-01-20T14:15:00Z",
      "user": {
        "userId": "012e3456-e89b-12d3-a456-426614174003",
        "username": "techgadgets_admin",
        "email": "admin@techgadgets.com",
        "profilePicUrl": null,
        "isVerified": true,
        "userCreatedAt": "2024-01-18T09:30:00Z"
      }
    }
  ]
}
```

#### Response Fields
- `vendorId`: Unique vendor identifier
- `businessName`: Vendor's business name
- `businessType`: Type of business (individual, business, company)
- `status`: Vendor status (pending, approved, rejected, suspended)
- `commission`: Commission rate percentage (default: 10.00)
- `ratingAverage`: Average customer rating
- `ratingCount`: Number of ratings received
- `totalSales`: Total sales amount from delivered orders
- `totalOrders`: Number of delivered orders
- `vendorCreatedAt`: Vendor registration timestamp
- `user`: User account information
  - `userId`: User account ID
  - `username`: Username
  - `email`: Email address
  - `profilePicUrl`: Profile picture URL
  - `isVerified`: Email verification status
  - `userCreatedAt`: User account creation timestamp

### 2. Update Individual Vendor Commission Rate
**Endpoint:** `PUT /api/admin/vendors/{vendorId}/commission`  
**Authorization:** Admin only  
**Description:** Updates the commission rate for a specific vendor.

#### Request
```http
PUT /api/admin/vendors/123e4567-e89b-12d3-a456-426614174000/commission
Authorization: Bearer <admin_jwt_token>
Content-Type: application/json

{
  "commissionRate": 15.5
}
```

#### Request Body
```json
{
  "commissionRate": 15.5
}
```

#### Response (Success)
```json
{
  "success": true,
  "message": "Commission rate updated successfully",
  "vendorId": "123e4567-e89b-12d3-a456-426614174000",
  "commissionRate": 15.5
}
```

#### Response (Error - Invalid Rate)
```json
{
  "success": false,
  "message": "commissionRate must be between 0 and 100"
}
```

#### Response (Error - Vendor Not Found)
```json
{
  "success": false,
  "message": "Vendor not found"
}
```

#### Response (Error - Missing Rate)
```json
{
  "success": false,
  "message": "commissionRate is required"
}
```

### 3. Get Global Commission Rate
**Endpoint:** `GET /api/admin/commission-rate`  
**Authorization:** Admin or Vendor  
**Description:** Gets the current global commission rate.

#### Request
```http
GET /api/admin/commission-rate
Authorization: Bearer <jwt_token>
```

#### Response
```json
{
  "success": true,
  "commissionRate": 10.0
}
```

### 4. Set Global Commission Rate (Updates All Vendors)
**Endpoint:** `POST /api/admin/commission-rate`  
**Authorization:** Admin only  
**Description:** Updates the commission rate for all vendors at once.

#### Request
```http
POST /api/admin/commission-rate
Authorization: Bearer <admin_jwt_token>
Content-Type: application/json

{
  "commissionRate": 12.0
}
```

#### Response
```json
{
  "success": true,
  "updatedRows": 15,
  "commissionRate": 12.0
}
```

## Error Handling

### Common HTTP Status Codes
- `200 OK`: Request successful
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: Insufficient permissions (non-admin)
- `404 Not Found`: Vendor not found
- `500 Internal Server Error`: Server error

### Error Response Format
```json
{
  "success": false,
  "message": "Error description",
  "error": "Detailed error information (optional)"
}
```

## Frontend Integration Examples

### JavaScript/React Example

```javascript
// Get all vendors with commission info
const fetchVendorsWithCommission = async () => {
  try {
    const response = await fetch('/api/admin/vendors/commission', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${adminToken}`,
        'Content-Type': 'application/json'
      }
    });
    
    const data = await response.json();
    if (data.success) {
      return data.data; // Array of vendors
    } else {
      throw new Error(data.message);
    }
  } catch (error) {
    console.error('Failed to fetch vendors:', error);
    throw error;
  }
};

// Update vendor commission rate
const updateVendorCommission = async (vendorId, commissionRate) => {
  try {
    const response = await fetch(`/api/admin/vendors/${vendorId}/commission`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ commissionRate })
    });
    
    const data = await response.json();
    if (data.success) {
      return data;
    } else {
      throw new Error(data.message);
    }
  } catch (error) {
    console.error('Failed to update commission:', error);
    throw error;
  }
};
```

### React Component Example

```jsx
import React, { useState, useEffect } from 'react';

const CommissionManagement = () => {
  const [vendors, setVendors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState({});

  useEffect(() => {
    fetchVendors();
  }, []);

  const fetchVendors = async () => {
    try {
      setLoading(true);
      const vendorsData = await fetchVendorsWithCommission();
      setVendors(vendorsData);
    } catch (error) {
      alert('Failed to fetch vendors: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCommissionUpdate = async (vendorId, newRate) => {
    try {
      setUpdating(prev => ({ ...prev, [vendorId]: true }));
      await updateVendorCommission(vendorId, newRate);
      
      // Update local state
      setVendors(prev => prev.map(vendor => 
        vendor.vendorId === vendorId 
          ? { ...vendor, commission: newRate }
          : vendor
      ));
      
      alert('Commission rate updated successfully!');
    } catch (error) {
      alert('Failed to update commission: ' + error.message);
    } finally {
      setUpdating(prev => ({ ...prev, [vendorId]: false }));
    }
  };

  if (loading) return <div>Loading vendors...</div>;

  return (
    <div className="commission-management">
      <h2>Vendor Commission Management</h2>
      <div className="vendors-list">
        {vendors.map(vendor => (
          <div key={vendor.vendorId} className="vendor-card">
            <div className="vendor-info">
              <h3>{vendor.businessName}</h3>
              <p>Email: {vendor.user.email}</p>
              <p>Status: {vendor.status}</p>
              <p>Total Sales: ${vendor.totalSales}</p>
              <p>Total Orders: {vendor.totalOrders}</p>
            </div>
            
            <div className="commission-section">
              <label>Commission Rate (%):</label>
              <input
                type="number"
                min="0"
                max="100"
                step="0.1"
                defaultValue={vendor.commission}
                onBlur={(e) => {
                  const newRate = parseFloat(e.target.value);
                  if (newRate !== vendor.commission && newRate >= 0 && newRate <= 100) {
                    handleCommissionUpdate(vendor.vendorId, newRate);
                  }
                }}
                disabled={updating[vendor.vendorId]}
              />
              {updating[vendor.vendorId] && <span>Updating...</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default CommissionManagement;
```

## Important Notes

1. **Default Commission Rate**: New vendors automatically get a 10% commission rate
2. **Rate Validation**: Commission rates must be between 0 and 100
3. **Real-time Updates**: Commission changes take effect immediately for new orders
4. **Existing Orders**: Commission rates are calculated at order time, so existing orders are not affected
5. **Authorization**: All commission management endpoints require admin privileges
6. **Data Format**: Commission rates are stored as DECIMAL(5,2) in the database (e.g., 10.50)

## Database Schema

The commission data is stored in the `vendors` table:

```sql
CREATE TABLE vendors (
    -- ... other fields ...
    commission DECIMAL(5,2) DEFAULT 10.00,
    -- ... other fields ...
);
```

This allows for commission rates from 0.00% to 999.99% with 2 decimal precision.
