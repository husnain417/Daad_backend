# 🧪 Comprehensive Postman Testing Guide for Daad E-commerce API

## 📋 Table of Contents
1. [Setup & Configuration](#setup--configuration)
2. [Test Data Setup](#test-data-setup)
3. [Authentication Flow Testing](#authentication-flow-testing)
4. [Product Management Testing](#product-management-testing)
5. [Shopping Cart Testing](#shopping-cart-testing)
6. [Order Management Testing](#order-management-testing)
7. [Review & Rating Testing](#review--rating-testing)
8. [Admin Panel Testing](#admin-panel-testing)
9. [Vendor Dashboard Testing](#vendor-dashboard-testing)
10. [Error Scenarios Testing](#error-scenarios-testing)
11. [Performance Testing](#performance-testing)

---

## 🔧 Setup & Configuration

### Base URL
```
http://localhost:8080
```

### Environment Variables
Create these variables in Postman:
- `base_url`: `http://localhost:8080`
- `customer_token`: (will be set after login)
- `vendor_token`: (will be set after login)
- `admin_token`: (will be set after login)
- `customer_id`: (will be set after registration)
- `vendor_id`: (will be set after registration)
- `product_id`: (will be set after product creation)
- `order_id`: (will be set after order creation)
- `review_id`: (will be set after review creation)

### Headers Setup
For authenticated requests, add:
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

---

## 📊 Test Data Setup

### Sample Test Data

#### Customer User
```json
{
  "username": "testcustomer",
  "email": "customer@test.com",
  "password": "TestPass123!",
  "role": "customer"
}
```

#### Vendor User
```json
{
  "username": "testvendor",
  "email": "vendor@test.com",
  "password": "TestPass123!",
  "role": "vendor",
  "vendorDetails": {
    "businessName": "Test Business Store",
    "businessType": "Retail",
    "phoneNumber": "+1234567890",
    "businessAddress": {
      "addressLine1": "123 Business St",
      "city": "Karachi",
      "state": "Sindh",
      "postalCode": "75000",
      "country": "Pakistan"
    },
    "description": "Test vendor store for testing purposes"
  }
}
```

#### Admin User
```json
{
  "username": "admin",
  "email": "admin@test.com",
  "password": "AdminPass123!",
  "role": "admin"
}
```

#### Sample Product
```json
{
  "name": "Test Product",
  "description": "A test product for API testing",
  "price": 29.99,
  "category": "Electronics",
  "gender": "Unisex",
  "colorInventories": [
    {
      "color": "Red",
      "sizes": [
        {
          "size": "M",
          "stock": 100
        },
        {
          "size": "L",
          "stock": 50
        }
      ]
    }
  ],
  "images": ["test-image-url.jpg"],
  "isActive": true
}
```

---

## 🔐 Authentication Flow Testing

### 1. Customer Registration
**POST** `{{base_url}}/api/users/register`

```json
{
  "username": "testcustomer",
  "email": "customer@test.com",
  "password": "TestPass123!",
  "role": "customer"
}
```

**Expected Response:**
```json
{
  "message": "Customer account created successfully",
  "role": "customer",
  "userId": "generated-user-id"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    const response = pm.response.json();
    pm.environment.set("customer_id", response.userId);
    console.log("Customer registered successfully");
}
```

### 2. Vendor Registration
**POST** `{{base_url}}/api/users/register`

```json
{
  "username": "testvendor",
  "email": "vendor@test.com",
  "password": "TestPass123!",
  "role": "vendor",
  "vendorDetails": {
    "businessName": "Test Business Store",
    "businessType": "Retail",
    "phoneNumber": "+1234567890",
    "businessAddress": {
      "addressLine1": "123 Business St",
      "city": "Karachi",
      "state": "Sindh",
      "postalCode": "75000",
      "country": "Pakistan"
    },
    "description": "Test vendor store for testing purposes"
  }
}
```

**Expected Response:**
```json
{
  "message": "Vendor account created successfully. Your account is pending approval.",
  "role": "vendor",
  "userId": "generated-user-id"
}
```

### 3. Customer Login
**POST** `{{base_url}}/api/users/login`

```json
{
  "email": "customer@test.com",
  "password": "TestPass123!"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": "user-id",
    "username": "testcustomer",
    "email": "customer@test.com",
    "role": "customer"
  },
  "token": "jwt-token"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    const response = pm.response.json();
    pm.environment.set("customer_token", response.token);
    pm.environment.set("customer_id", response.user.id);
    console.log("Customer logged in successfully");
}
```

### 4. Vendor Login
**POST** `{{base_url}}/api/users/login`

```json
{
  "email": "vendor@test.com",
  "password": "TestPass123!"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": "user-id",
    "username": "testvendor",
    "email": "vendor@test.com",
    "role": "vendor",
    "vendor": {
      "businessName": "Test Business Store",
      "status": "pending"
    }
  },
  "token": "jwt-token"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    const response = pm.response.json();
    pm.environment.set("vendor_token", response.token);
    pm.environment.set("vendor_id", response.user.id);
    console.log("Vendor logged in successfully");
}
```

### 5. Get User Profile
**GET** `{{base_url}}/api/users/profile`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

**Expected Response:**
```json
{
  "message": "Profile",
  "user": {
    "id": "user-id",
    "username": "testcustomer",
    "email": "customer@test.com",
    "role": "customer"
  }
}
```

### 6. Password Reset Flow
**POST** `{{base_url}}/api/users/password-forgot`

```json
{
  "email": "customer@test.com"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "OTP sent to your email"
}
```

### 7. Google OAuth (Mock)
**POST** `{{base_url}}/api/users/google-auth`

```json
{
  "id_token": "mock-google-token"
}
```

---

## 🛍️ Product Management Testing

### 1. Get All Products (Public)
**GET** `{{base_url}}/api/products/`

**Query Parameters:**
```
page=1
limit=20
category=Electronics
minPrice=10
maxPrice=100
inStock=true
sort=-createdAt
```

**Expected Response:**
```json
{
  "success": true,
  "count": 10,
  "total": 50,
  "pagination": {
    "page": 1,
    "pages": 3
  },
  "data": [...]
}
```

### 2. Search Products
**GET** `{{base_url}}/api/products/search?q=test&page=1&limit=10`

**Expected Response:**
```json
{
  "success": true,
  "count": 5,
  "total": 5,
  "pagination": {
    "page": 1,
    "pages": 1
  },
  "data": [...]
}
```

### 3. Get Single Product
**GET** `{{base_url}}/api/products/{{product_id}}`

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "id": "product-id",
    "name": "Test Product",
    "price": 29.99,
    "description": "Product description",
    "category": "Electronics",
    "colorInventories": [...],
    "images": [...],
    "vendor": {...}
  }
}
```

### 4. Create Product (Vendor)
**POST** `{{base_url}}/api/products/`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
Content-Type: application/json
```

```json
{
  "name": "Test Product",
  "description": "A test product for API testing",
  "price": 29.99,
  "category": "Electronics",
  "gender": "Unisex",
  "colorInventories": [
    {
      "color": "Red",
      "sizes": [
        {
          "size": "M",
          "stock": 100
        },
        {
          "size": "L",
          "stock": 50
        }
      ]
    }
  ],
  "images": ["test-image-url.jpg"],
  "isActive": true
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": "generated-product-id",
    "name": "Test Product",
    "status": "pending"
  }
}
```

**Test Script:**
```javascript
if (pm.response.code === 201) {
    const response = pm.response.json();
    pm.environment.set("product_id", response.data.id);
    console.log("Product created successfully");
}
```

### 5. Update Product (Vendor)
**PUT** `{{base_url}}/api/products/{{product_id}}`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
Content-Type: application/json
```

```json
{
  "name": "Updated Test Product",
  "description": "Updated description",
  "price": 39.99
}
```

### 6. Upload Product Images
**POST** `{{base_url}}/api/products/{{product_id}}/images/default`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Body:** Form-data
```
images: [file upload]
```

### 7. Add Color to Product
**POST** `{{base_url}}/api/products/{{product_id}}/inventory/colors`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
Content-Type: application/json
```

```json
{
  "color": "Blue",
  "sizes": [
    {
      "size": "S",
      "stock": 25
    },
    {
      "size": "M",
      "stock": 30
    }
  ]
}
```

### 8. Update Stock
**PUT** `{{base_url}}/api/products/{{product_id}}/inventory/stock`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
Content-Type: application/json
```

```json
{
  "color": "Red",
  "size": "M",
  "stock": 150
}
```

### 9. Get Inventory Summary
**GET** `{{base_url}}/api/products/{{product_id}}/inventory/summary`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 10. Delete Product
**DELETE** `{{base_url}}/api/products/{{product_id}}`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

---

## 🛒 Shopping Cart Testing

### 1. Get Cart
**GET** `{{base_url}}/api/cart/`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "items": [],
    "subtotal": 0,
    "tax": 0,
    "shipping": 0,
    "total": 0
  },
  "summary": {
    "itemCount": 0,
    "totalItems": 0,
    "subtotal": 0,
    "tax": 0,
    "shipping": 0,
    "discount": 0,
    "total": 0
  }
}
```

### 2. Add Item to Cart
**POST** `{{base_url}}/api/cart/add`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 2
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Item added to cart successfully",
  "data": {
    "items": [
      {
        "productId": "product-id",
        "vendorId": "vendor-id",
        "color": "Red",
        "size": "M",
        "quantity": 2,
        "price": 29.99
      }
    ],
    "subtotal": 59.98
  },
  "summary": {
    "itemCount": 1,
    "totalItems": 2,
    "subtotal": 59.98
  }
}
```

### 3. Update Cart Item
**PUT** `{{base_url}}/api/cart/update`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 3
}
```

### 4. Remove Item from Cart
**DELETE** `{{base_url}}/api/cart/remove`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M"
}
```

### 5. Update Shipping Address
**PUT** `{{base_url}}/api/cart/shipping-address`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "fullName": "John Doe",
  "addressLine1": "123 Main St",
  "city": "Karachi",
  "state": "Sindh",
  "postalCode": "75000",
  "country": "Pakistan",
  "phoneNumber": "+1234567890"
}
```

### 6. Calculate Shipping
**POST** `{{base_url}}/api/cart/calculate-shipping`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "shippingAddress": {
    "country": "Pakistan",
    "city": "Karachi"
  }
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "shipping": 200.0,
    "tax": 2.99,
    "estimatedDelivery": {
      "minDays": 2,
      "maxDays": 5
    },
    "subtotal": 59.98,
    "total": 262.97
  }
}
```

### 7. Get Cart Summary
**GET** `{{base_url}}/api/cart/summary`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

### 8. Clear Cart
**DELETE** `{{base_url}}/api/cart/clear`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

---

## 📦 Order Management Testing

### 1. Create Order (Authenticated)
**POST** `{{base_url}}/api/orders/create`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "customerInfo": {
    "email": "customer@test.com",
    "phone": "+1234567890"
  },
  "items": [
    {
      "product": "{{product_id}}",
      "color": "Red",
      "size": "M",
      "quantity": 2,
      "price": 29.99
    }
  ],
  "shippingAddress": {
    "fullName": "John Doe",
    "addressLine1": "123 Main St",
    "city": "Karachi",
    "state": "Sindh",
    "postalCode": "75000",
    "country": "Pakistan",
    "phoneNumber": "+1234567890",
    "email": "customer@test.com"
  },
  "subtotal": 59.98,
  "shippingCharges": 200.0,
  "total": 259.98,
  "discount": 0.0,
  "discountInfo": {
    "amount": 0,
    "reasons": [],
    "pointsUsed": 0
  },
  "paymentMethod": "bank-transfer"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "order": {
    "id": "generated-order-id",
    "items": [...],
    "total": 259.98,
    "orderStatus": "pending",
    "customerEmail": "customer@test.com"
  }
}
```

**Test Script:**
```javascript
if (pm.response.code === 201) {
    const response = pm.response.json();
    pm.environment.set("order_id", response.order.id);
    console.log("Order created successfully");
}
```

### 2. Create Guest Order
**POST** `{{base_url}}/api/orders/create-guest`

**Headers:**
```
Content-Type: application/json
```

```json
{
  "customerInfo": {
    "email": "guest@test.com",
    "phone": "+1234567890"
  },
  "items": [
    {
      "product": "{{product_id}}",
      "color": "Red",
      "size": "M",
      "quantity": 1,
      "price": 29.99
    }
  ],
  "shippingAddress": {
    "fullName": "Guest User",
    "addressLine1": "456 Guest St",
    "city": "Lahore",
    "state": "Punjab",
    "postalCode": "54000",
    "country": "Pakistan",
    "phoneNumber": "+1234567890",
    "email": "guest@test.com"
  },
  "subtotal": 29.99,
  "shippingCharges": 200.0,
  "total": 229.99,
  "discount": 0.0,
  "discountInfo": {
    "amount": 0,
    "reasons": [],
    "pointsUsed": 0
  },
  "paymentMethod": "cash-on-delivery"
}
```

### 3. Get Order Details
**GET** `{{base_url}}/api/orders/details/{{order_id}}`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

### 4. Get Order Tracking
**GET** `{{base_url}}/api/orders/{{order_id}}/tracking`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

### 5. Cancel Order
**POST** `{{base_url}}/api/orders/{{order_id}}/cancel`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "reason": "Changed mind"
}
```

### 6. Get My Orders
**GET** `{{base_url}}/api/orders/my-orders`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

### 7. Get All Orders (Admin)
**GET** `{{base_url}}/api/orders/all?page=1&limit=10&status=pending`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

### 8. Update Order Status (Admin)
**PUT** `{{base_url}}/api/orders/update-status/{{order_id}}`

**Headers:**
```
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

```json
{
  "status": "processing",
  "trackingNumber": "TRK123456789"
}
```

### 9. Get Vendor Orders
**GET** `{{base_url}}/api/orders/vendor`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 10. Update Order Status (Vendor)
**PUT** `{{base_url}}/api/orders/vendor/{{order_id}}/status`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
Content-Type: application/json
```

```json
{
  "status": "shipped"
}
```

---

## ⭐ Review & Rating Testing

### 1. Create Review
**POST** `{{base_url}}/api/ratings/`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "productId": "{{product_id}}",
  "rating": 5,
  "title": "Great product!",
  "comment": "Really satisfied with this purchase. Quality is excellent."
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Rating submitted successfully",
  "data": {
    "id": "review-id",
    "productId": "product-id",
    "customerId": "customer-id",
    "rating": 5,
    "title": "Great product!",
    "comment": "Really satisfied with this purchase.",
    "status": "approved"
  }
}
```

**Test Script:**
```javascript
if (pm.response.code === 201) {
    const response = pm.response.json();
    pm.environment.set("review_id", response.data.id);
    console.log("Review created successfully");
}
```

### 2. Get Product Reviews
**GET** `{{base_url}}/api/ratings/product/{{product_id}}?page=1&limit=10&sort=-createdAt`

**Expected Response:**
```json
{
  "success": true,
  "count": 1,
  "total": 1,
  "pagination": {
    "page": 1,
    "pages": 1
  },
  "product": {
    "id": "product-id",
    "name": "Test Product",
    "category": "Electronics"
  },
  "ratingStats": {
    "averageRating": 5.0,
    "totalReviews": 1,
    "ratingDistribution": {
      "5": 1
    }
  },
  "data": [...]
}
```

### 3. Get User Reviews
**GET** `{{base_url}}/api/ratings/user?page=1&limit=10`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

### 4. Update Review
**PUT** `{{base_url}}/api/ratings/{{review_id}}`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "rating": 4,
  "title": "Updated review",
  "comment": "Updated comment"
}
```

### 5. Delete Review
**DELETE** `{{base_url}}/api/ratings/{{review_id}}`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

---

## 👨‍💼 Admin Panel Testing

### 1. Get All Vendors
**GET** `{{base_url}}/api/users/vendors`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

### 2. Get Pending Vendors
**GET** `{{base_url}}/api/users/admin/vendors/pending-approval`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

### 3. Approve Vendor
**PUT** `{{base_url}}/api/users/admin/vendors/{{vendor_id}}/approve`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

### 4. Update Vendor Status
**PUT** `{{base_url}}/api/users/vendors/{{vendor_id}}/status`

**Headers:**
```
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

```json
{
  "status": "approved"
}
```

### 5. Get Pending Products
**GET** `{{base_url}}/api/products/admin/pending-approval`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

### 6. Approve Product
**PUT** `{{base_url}}/api/products/admin/{{product_id}}/approval`

**Headers:**
```
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

```json
{
  "status": "approved",
  "reason": "Product meets all requirements"
}
```

### 7. Get Product Stats
**GET** `{{base_url}}/api/products/stats`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

---

## 🏪 Vendor Dashboard Testing

### 1. Get Dashboard Overview
**GET** `{{base_url}}/api/vendor-dashboard/overview`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 2. Get Sales Analytics
**GET** `{{base_url}}/api/vendor-dashboard/sales-analytics?period=month&startDate=2024-01-01&endDate=2024-01-31`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 3. Get Sales Report
**GET** `{{base_url}}/api/vendor-dashboard/analytics/sales-report`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 4. Get Customer Insights
**GET** `{{base_url}}/api/vendor-dashboard/analytics/customer-insights`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 5. Get Performance Metrics
**GET** `{{base_url}}/api/vendor-dashboard/performance-metrics?days=30`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 6. Get Financial Summary
**GET** `{{base_url}}/api/vendor-dashboard/financial-summary?year=2024`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

### 7. Get Vendor Inventory
**GET** `{{base_url}}/api/vendor/inventory?page=1&limit=20`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

---

## 🏷️ Additional Features Testing

### 1. Get Categories
**GET** `{{base_url}}/api/categories/?page=1&limit=50&sort=name&includeInactive=false&parentOnly=false`

### 2. Get Brands
**GET** `{{base_url}}/api/brands/`

### 3. Get Hero Images
**GET** `{{base_url}}/api/hero-images/`

### 4. Upload Hero Image (Admin)
**POST** `{{base_url}}/api/hero-images/`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

**Body:** Form-data
```
image: [file upload]
title: "Hero Image Title"
description: "Hero Image Description"
isActive: true
```

### 5. Get Contact Information
**GET** `{{base_url}}/api/contact/`

### 6. Submit Contact Form
**POST** `{{base_url}}/api/contact/`

```json
{
  "name": "John Doe",
  "email": "john@test.com",
  "subject": "Test Inquiry",
  "message": "This is a test message"
}
```

### 7. Calculate Discount Preview
**POST** `{{base_url}}/api/discount/calculate-preview`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "subtotal": 100.0,
  "discountCode": "SAVE10"
}
```

---

## ❌ Error Scenarios Testing

### 1. Invalid Authentication
**Test:** Try to access protected endpoint without token
**Expected:** 401 Unauthorized

### 2. Invalid Token
**Test:** Use expired or invalid JWT token
**Expected:** 401 Unauthorized

### 3. Insufficient Permissions
**Test:** Customer tries to access vendor-only endpoint
**Expected:** 403 Forbidden

### 4. Invalid Product ID
**Test:** Get product with non-existent ID
**Expected:** 404 Not Found

### 5. Insufficient Stock
**Test:** Add more items to cart than available stock
**Expected:** 400 Bad Request with stock error

### 6. Invalid Order Data
**Test:** Create order with missing required fields
**Expected:** 400 Bad Request

### 7. Duplicate Review
**Test:** Create review for same product twice
**Expected:** 400 Bad Request

### 8. Invalid Email Format
**Test:** Register with invalid email
**Expected:** 400 Bad Request

### 9. Weak Password
**Test:** Register with weak password
**Expected:** 400 Bad Request

### 10. Invalid File Upload
**Test:** Upload invalid file type
**Expected:** 400 Bad Request

---

## 🚀 Performance Testing

### 1. Load Testing
- Test with 100+ concurrent requests
- Monitor response times
- Check for memory leaks

### 2. Stress Testing
- Test with maximum payload sizes
- Test with large number of items in cart
- Test with large product catalogs

### 3. Endurance Testing
- Run tests for extended periods
- Monitor database performance
- Check for resource leaks

---

## 📝 Test Execution Flow

### Phase 1: Setup & Authentication
1. Register customer user
2. Register vendor user
3. Login both users
4. Set up admin user (if available)

### Phase 2: Product Management
1. Create products as vendor
2. Test product CRUD operations
3. Test inventory management
4. Test product approval flow

### Phase 3: Shopping Experience
1. Browse products
2. Search products
3. Add items to cart
4. Test cart operations
5. Calculate shipping

### Phase 4: Order Processing
1. Create authenticated order
2. Create guest order
3. Test order tracking
4. Test order cancellation
5. Test order status updates

### Phase 5: Reviews & Ratings
1. Create reviews
2. Test review management
3. Test rating aggregation

### Phase 6: Admin Operations
1. Approve vendors
2. Approve products
3. Manage orders
4. View analytics

### Phase 7: Vendor Dashboard
1. View dashboard metrics
2. Manage inventory
3. View sales reports
4. Manage orders

### Phase 8: Error Scenarios
1. Test all error conditions
2. Verify proper error messages
3. Test edge cases

---

## 🔍 Monitoring & Validation

### Response Time Expectations
- Authentication: < 500ms
- Product listing: < 1s
- Order creation: < 2s
- Search: < 1s

### Success Criteria
- All endpoints return expected status codes
- Response times within acceptable limits
- No memory leaks during extended testing
- Proper error handling for all scenarios
- Email notifications working correctly

### Common Issues to Watch
- JWT token expiration
- Database connection timeouts
- File upload failures
- Email delivery issues
- CORS problems

---

## 📊 Test Results Template

| Test Case | Endpoint | Method | Status | Response Time | Notes |
|-----------|----------|--------|--------|---------------|-------|
| Customer Registration | /api/users/register | POST | ✅ | 250ms | Success |
| Customer Login | /api/users/login | POST | ✅ | 180ms | Success |
| Get Products | /api/products/ | GET | ✅ | 320ms | Success |
| Add to Cart | /api/cart/add | POST | ✅ | 150ms | Success |
| Create Order | /api/orders/create | POST | ✅ | 800ms | Success |

---

This comprehensive testing guide covers all aspects of your e-commerce API. Follow the phases in order for systematic testing, and use the error scenarios to ensure robust error handling. Remember to update environment variables as you progress through the tests!
