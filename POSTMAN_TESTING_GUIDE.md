# 🧪 Comprehensive Postman Testing Guide for Daad E-commerce API

## 📋 Table of Contents
1. [Setup & Configuration](#setup--configuration)
2. [Test Data Setup](#test-data-setup)
3. [Authentication Flow Testing](#authentication-flow-testing)
4. [Product Management Testing](#product-management-testing)
5. [Shopping Cart Testing](#shopping-cart-testing)
6. [Order Management Testing](#order-management-testing)
7. [Payment Integration Testing (Paymob)](#payment-integration-testing-paymob)
8. [Review & Rating Testing](#review--rating-testing)
9. [Admin Panel Testing](#admin-panel-testing)
10. [Vendor Dashboard Testing](#vendor-dashboard-testing)
11. [Error Scenarios Testing](#error-scenarios-testing)
12. [Performance Testing](#performance-testing)

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
- `payment_reference`: (will be set after payment session creation)
- `transaction_id`: (will be set after payment completion)

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
  "name": "Premium Cotton T-Shirt",
  "description": "High-quality cotton t-shirt perfect for everyday wear. Made from 100% organic cotton with a comfortable fit.",
  "price": 29.99,
  "category": "Clothing",
  "gender": "Unisex",
  "colorInventories": [
    {
      "color": "Red",
      "colorCode": "#FF0000",
      "sizes": [
        {
          "size": "S",
          "stock": 25
        },
        {
          "size": "M",
          "stock": 50
        },
        {
          "size": "L",
          "stock": 30
        },
        {
          "size": "XL",
          "stock": 20
        }
      ]
    },
    {
      "color": "Blue",
      "colorCode": "#0000FF",
      "sizes": [
        {
          "size": "S",
          "stock": 15
        },
        {
          "size": "M",
          "stock": 40
        },
        {
          "size": "L",
          "stock": 35
        },
        {
          "size": "XL",
          "stock": 25
        }
      ]
    },
    {
      "color": "Black",
      "colorCode": "#000000",
      "sizes": [
        {
          "size": "S",
          "stock": 30
        },
        {
          "size": "M",
          "stock": 60
        },
        {
          "size": "L",
          "stock": 45
        },
        {
          "size": "XL",
          "stock": 35
        }
      ]
    }
  ],
  "discount": {
    "discountValue": 5.00,
    "discountType": "percentage",
    "endDate": "2024-12-31",
    "isActive": true
  }
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

### 2. Vendor Registration (now persists bank details)
**POST** `{{base_url}}/api/users/register`

```json
{
  "username": "testvendor",
  "email": "vendor@test.com",
  "password": "TestPass123!",
  "role": "vendor",
  "vendorDetails": {
    "businessName": "Test Business Store",
    "businessType": "business",
    "phoneNumber": "+1234567890",
    "businessAddress": {
      "addressLine1": "123 Business St",
      "addressLine2": "Suite 100",
      "city": "Karachi",
      "state": "Sindh",
      "postalCode": "75000",
      "country": "Pakistan"
    },
    "description": "Test vendor store for testing purposes",
    "bankDetails": {
      "bankName": "Habib Bank Limited",
      "accountNumber": "1234567890123456",
      "accountHolderName": "Test Business Store"
    }
  }
}
```

**Notes:** 
- `businessType`: `"individual"`, `"business"`, or `"company"`
- `bankDetails` fields are now saved to the vendor record (bank_name, bank_account_number, bank_account_holder_name). They will appear in:
  - GET `{{base_url}}/api/users/vendor/details` (vendor JWT) under `bankDetails`
  - GET `{{base_url}}/api/users/vendors/{vendorId}/details` (admin) under `bankDetails`
- `country` defaults to "Pakistan" if not provided

**Verify Bank Details Saved:**
- After registering, call:
  - Vendor: `GET {{base_url}}/api/users/vendor/details` with `{{vendor_token}}`
  - Admin: `GET {{base_url}}/api/users/vendors/{{vendor_id}}/details`
  - Expect `bankDetails` to include: `bankName`, `accountNumber`, `accountHolderName`.

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

#### 📋 Complete Password Reset Workflow

**Step-by-step process for password reset:**

1. **Request Password Reset** → OTP sent to email (expires in 2 minutes)
2. **Resend OTP** (optional) → If OTP not received (30-second cooldown)
3. **Verify OTP** → Validate OTP and get reset token (expires in 15 minutes)
4. **Set New Password** → Use reset token to set new password
5. **Alternative: Change Password** → For logged-in users (requires old password)

**Important Notes:**
- OTP expires in 2 minutes
- Reset token expires in 15 minutes
- Rate limiting: 30 seconds between OTP requests
- Password requirements: 8+ characters, 1 special character, 1 number
- User is automatically verified after password reset

---

#### 6.1. Request Password Reset
**POST** `{{base_url}}/api/users/password-forgot`

```json
{
  "email": "customer@test.com"
}
```

**Expected Response:**
```json
{
  "message": "OTP sent to your email",
  "email": "customer@test.com"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    console.log("OTP sent successfully to: " + pm.response.json().email);
    console.log("Check your email for the 6-digit OTP");
}
```

#### 6.2. Resend OTP (if needed)
**POST** `{{base_url}}/api/users/resend-otp`

```json
{
  "email": "customer@test.com"
}
```

**Expected Response:**
```json
{
  "message": "New OTP sent to your email",
  "email": "customer@test.com"
}
```

**Rate Limiting:**
- 30-second cooldown between requests
- If rate limited, you'll get:
```json
{
  "message": "Please wait before requesting a new OTP",
  "retryAfter": 30
}
```

#### 6.3. Verify OTP
**POST** `{{base_url}}/api/users/verify-otp`

```json
{
  "email": "customer@test.com",
  "otp": "123456"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "resetToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": "15 minutes"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    const response = pm.response.json();
    pm.environment.set("reset_token", response.resetToken);
    console.log("OTP verified successfully");
    console.log("Reset token: " + response.resetToken);
    console.log("Token expires in: " + response.expiresIn);
}
```

**Error Responses:**
- **Invalid OTP**: `{"success": false, "message": "Invalid OTP"}`
- **Expired OTP**: `{"success": false, "message": "OTP has expired. Please request a new one."}`
- **No OTP**: `{"success": false, "message": "No OTP found. Please request a new one."}`

#### 6.4. Set New Password (with reset token)
**POST** `{{base_url}}/api/users/set-new-password`

**Headers:**
```
Authorization: Bearer {{reset_token}}
Content-Type: application/json
```

```json
{
  "newPassword": "NewSecurePass123!"
}
```

**Expected Response:**
```json
{
  "message": "Password reset successfully"
}
```

**Important Notes:**
- Reset token is generated after OTP verification
- Token expires in 15 minutes
- Password must meet requirements: 8+ chars, 1 special char, 1 number
- User is automatically verified after password reset
- OTP is cleared after verification

#### 6.5. Alternative: Change Password (for logged-in users)
**POST** `{{base_url}}/api/users/password-update`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

```json
{
  "oldPassword": "OldPassword123!",
  "newPassword": "NewPassword123!"
}
```

**Expected Response:**
```json
{
  "message": "Password updated successfully"
}
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    console.log("Password updated successfully");
} else {
    console.log("Error: " + pm.response.json().message);
}
```

#### 6.6. Password Reset Error Scenarios

**Invalid Email:**
```json
{
  "message": "Enter a valid email format"
}
```

**User Not Found:**
```json
{
  "message": "User not found"
}
```

**Rate Limited (Resend OTP):**
```json
{
  "message": "Please wait before requesting a new OTP",
  "retryAfter": 30
}
```

**Invalid Reset Token:**
```json
{
  "message": "No token provided"
}
```

**Weak Password:**
```json
{
  "message": "Password must be at least 8 characters long, include at least one special character, and contain at least one number."
}
```

**Same Password (for password update):**
```json
{
  "message": "You entered the same password please change: "
}
```

**Invalid Old Password:**
```json
{
  "message": "Invalid old password"
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

## 🗂️ Category Management Testing

### 1. Create Parent Category (Admin)
POST `{{base_url}}/api/categories/`

Headers:
```
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

Body:
```json
{
  "name": "Clothing",
  "description": "All clothing items",
  "isActive": true
}
```

Expected Response:
```json
{
  "success": true,
  "message": "Category created successfully",
  "data": {
    "id": "category-id",
    "name": "Clothing",
    "slug": "clothing"
  }
}
```

Test Script:
```javascript
if (pm.response.code === 201) {
  const data = pm.response.json().data;
  pm.environment.set("parent_category_id", data.id);
  pm.environment.set("category_slug", data.slug);
}
```

### 2. Create Subcategory (Admin)
POST `{{base_url}}/api/categories/`

Headers:
```
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

Body:
```json
{
  "name": "Men",
  "description": "Men's wear",
  "parentCategory": "{{parent_category_id}}",
  "isActive": true
}
```

Expected Response sets `data.parentCategoryId` to parent id and returns a slug like `men`.

Test Script:
```javascript
if (pm.response.code === 201) {
  const data = pm.response.json().data;
  pm.environment.set("subcategory_id", data.id);
}
```

### 3. Get All Categories (Public)
GET `{{base_url}}/api/categories/?page=1&limit=50&sort=name&includeInactive=false&parentOnly=false`

Notes:
- `parentOnly=true` returns only top-level categories
- Response includes `stats` with `productCount` per category

### 4. Get Category By ID (Public)
GET `{{base_url}}/api/categories/{{parent_category_id}}`

Returns category details, subcategories and product count.

### 5. Update Category (Admin)
PUT `{{base_url}}/api/categories/{{subcategory_id}}`

Headers:
```
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

Body (example):
```json
{
  "description": "Updated description for Men",
  "isActive": true
}
```

### 6. Delete Category (Admin)
DELETE `{{base_url}}/api/categories/{{subcategory_id}}`

Notes:
- Cannot delete a category that has subcategories or products assigned.

---

## 🛍️ Product Management Testing

### 📋 Complete Product Creation Workflow

**Step-by-step process for creating a product:**

1. **Create Product** → Product created in "draft" status
2. **Upload Images** → Product status changes to "awaiting_approval"
3. **Add Colors** → Add additional color variants
4. **Update Stock** → Manage inventory levels
5. **Admin Approval** → Product becomes "approved" and visible to customers

**Important Notes:**
- Products start in "draft" status
- Images must be uploaded to change status to "awaiting_approval"
- Admin approval required for products to be visible to customers
- Inventory management can be done after product creation
- **Category Handling**: Use `category` for parent category (e.g., "men", "women", "kids") and `subcategory` for specific subcategory (e.g., "tshirt", "pants"). If subcategory is provided, it will be used; otherwise, the parent category will be used.
- Each color can have multiple sizes with individual stock levels

---

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
  "name": "Premium Cotton T-Shirt",
  "description": "High-quality cotton t-shirt perfect for everyday wear. Made from 100% organic cotton with a comfortable fit.",
  "price": 29.99,
  "category": "men",
  "subcategory": "tshirt",
  "gender": "men",
  "colorInventories": [
    {
      "color": "Red",
      "colorCode": "#FF0000",
      "sizes": [
        {
          "size": "S",
          "stock": 25
        },
        {
          "size": "M",
          "stock": 50
        },
        {
          "size": "L",
          "stock": 30
        },
        {
          "size": "XL",
          "stock": 20
        }
      ]
    },
    {
      "color": "Blue",
      "colorCode": "#0000FF",
      "sizes": [
        {
          "size": "S",
          "stock": 15
        },
        {
          "size": "M",
          "stock": 40
        },
        {
          "size": "L",
          "stock": 35
        },
        {
          "size": "XL",
          "stock": 25
        }
      ]
    },
    {
      "color": "Black",
      "colorCode": "#000000",
      "sizes": [
        {
          "size": "S",
          "stock": 30
        },
        {
          "size": "M",
          "stock": 60
        },
        {
          "size": "L",
          "stock": 45
        },
        {
          "size": "XL",
          "stock": 35
        }
      ]
    }
  ],
  "discount": {
    "discountValue": 5.00,
    "discountType": "percentage",
    "endDate": "2024-12-31",
    "isActive": true
  }
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": "generated-product-id",
    "name": "Premium Cotton T-Shirt",
    "status": "draft",
    "totalStock": 425,
    "colorInventories": [
      {
        "color": "Red",
        "colorCode": "#FF0000",
        "sizes": [
          {
            "size": "S",
            "stock": 25
          },
          {
            "size": "M",
            "stock": 50
          },
          {
            "size": "L",
            "stock": 30
          },
          {
            "size": "XL",
            "stock": 20
          }
        ]
      }
    ],
    "discount": {
      "discountValue": 5.00,
      "discountType": "percentage",
      "endDate": "2024-12-31",
      "isActive": true
    }
  }
}
```

**Test Script:**
```javascript
if (pm.response.code === 201) {
    const response = pm.response.json();
    pm.environment.set("product_id", response.data.id);
    console.log("Product created successfully with ID: " + response.data.id);
    console.log("Total stock: " + response.data.totalStock);
}
```

### 5. Upload Product Default Images
**POST** `{{base_url}}/api/products/{{product_id}}/images/default`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Body:** Form-data
```
images: [file upload] (multiple files allowed)
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Images uploaded successfully. Product status changed to awaiting approval.",
  "data": {
    "uploadedUrls": [
      "http://localhost:8080/uploads/product/product-id/default/image1.jpg",
      "http://localhost:8080/uploads/product/product-id/default/image2.jpg",
      "http://localhost:8080/uploads/product/product-id/default/image3.jpg"
    ],
    "totalImages": 3
  }
}
```

**Note:** Images are now saved to the `product_images` table in the database and will be available in cart responses and product details.

**Test Script:**
```javascript
if (pm.response.code === 200) {
    const response = pm.response.json();
    console.log("Images uploaded successfully");
    console.log("Total images: " + response.data.totalImages);
    console.log("Image URLs: " + response.data.uploadedUrls.join(", "));
}
```

**Important Notes:**
- Upload multiple image files at once
- Images are stored in `/uploads/product/{productId}/default/` directory
- Product status changes from "draft" to "awaiting_approval" after image upload
- Supported formats: JPG, PNG, GIF, WebP
- Maximum file size: 5MB per image
- Recommended: Upload 3-5 high-quality product images

### 6. Update Product (Vendor)
**PUT** `{{base_url}}/api/products/{{product_id}}`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
Content-Type: application/json
```

```json
{
  "name": "Updated Premium Cotton T-Shirt",
  "description": "Updated description with more details",
  "price": 34.99
}
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
  "color": "Green",
  "colorCode": "#00FF00",
  "sizes": [
    {
      "size": "S",
      "stock": 20
    },
    {
      "size": "M",
      "stock": 35
    },
    {
      "size": "L",
      "stock": 25
    },
    {
      "size": "XL",
      "stock": 15
    }
  ]
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Color added successfully",
  "data": {
    "color": "Green",
    "colorCode": "#00FF00",
    "totalStock": 95,
    "sizes": [...]
  }
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
  "stock": 75
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Stock updated successfully",
  "data": {
    "color": "Red",
    "size": "M",
    "oldStock": 50,
    "newStock": 75,
    "totalStock": 500
  }
}
```

### 9. Get Inventory Summary
**GET** `{{base_url}}/api/products/{{product_id}}/inventory/summary`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "productId": "product-id",
    "productName": "Premium Cotton T-Shirt",
    "totalStock": 500,
    "colorSummary": [
      {
        "color": "Red",
        "colorCode": "#FF0000",
        "totalStock": 125,
        "sizes": [
          {
            "size": "S",
            "stock": 25
          },
          {
            "size": "M",
            "stock": 75
          },
          {
            "size": "L",
            "stock": 30
          },
          {
            "size": "XL",
            "stock": 20
          }
        ]
      }
    ],
    "lowStockItems": [
      {
        "color": "Blue",
        "size": "S",
        "stock": 15,
        "status": "low"
      }
    ]
  }
}
```

### 10. Delete Product
**DELETE** `{{base_url}}/api/products/{{product_id}}`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Product deleted successfully"
}
```

---

## 🛒 Shopping Cart Testing

### 🧭 Cart Testing - Definitive Guide (User & Guest)

Use this section if you ever see "Item added successfully" but GET/UPDATE show an empty cart.

**New Feature**: Cart items now include product details (name, description, and default image URL) for better display in the frontend.

#### Identity rules
- Logged-in users: identified by JWT userId. Do NOT send `cartId`.
- Guests: identified by a UUID `cartId`. The server returns it after first add; you must reuse it on every subsequent request.

#### Environment variables to add
- `guest_cart_id` (empty initially)
- `cart_line_product_id`, `cart_line_color`, `cart_line_size` (captured from add response)

#### Add item (logged-in)
Headers:
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```
Body:
```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 2
}
```
Test script:
```javascript
if (pm.response.code === 200) {
  const res = pm.response.json();
  if (res.data && Array.isArray(res.data.items) && res.data.items.length > 0) {
    const it = res.data.items[0];
    pm.environment.set("cart_line_product_id", it.productId);
    pm.environment.set("cart_line_color", it.color);
    pm.environment.set("cart_line_size", it.size);
  }
}
```

#### Add item (guest)
Headers:
```
Content-Type: application/json
```
Body (first add):
```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 2
}
```
Test script (captures cartId and line identifiers):
```javascript
if (pm.response.code === 200) {
  const res = pm.response.json();
  if (res.cartId) pm.environment.set("guest_cart_id", res.cartId);
  if (res.data && Array.isArray(res.data.items) && res.data.items.length > 0) {
    const it = res.data.items[0];
    pm.environment.set("cart_line_product_id", it.productId);
    pm.environment.set("cart_line_color", it.color);
    pm.environment.set("cart_line_size", it.size);
  }
}
```
Body (subsequent adds as guest):
```json
{
  "cartId": "{{guest_cart_id}}",
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 1
}
```

#### Get cart
- Logged-in: `GET {{base_url}}/api/cart/` with `Authorization` header.
- Guest: `GET {{base_url}}/api/cart/?cartId={{guest_cart_id}}`

Note: GET filters out products that are not active or not approved, but does NOT persist those removals.

#### Update item
- Logged-in headers:
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```
Body:
```json
{
  "productId": "{{cart_line_product_id}}",
  "color": "{{cart_line_color}}",
  "size": "{{cart_line_size}}",
  "quantity": 3
}
```
- Guest body:
```json
{
  "cartId": "{{guest_cart_id}}",
  "productId": "{{cart_line_product_id}}",
  "color": "{{cart_line_color}}",
  "size": "{{cart_line_size}}",
  "quantity": 3
}
```

#### Troubleshooting: Empty cart on GET/UPDATE after successful add
- Ensure you are reusing the correct identifiers:
  - Logged-in: do not send `cartId`.
  - Guest: always send `cartId: {{guest_cart_id}}` on GET/UPDATE/REMOVE.
- Ensure you use the exact line identifiers from the add response:
  - `productId = {{cart_line_product_id}}`, `color = {{cart_line_color}}`, `size = {{cart_line_size}}`.
- Verify product availability:
  - GET filters inactive/unapproved products. Check `GET /api/products/{{cart_line_product_id}}` → `isActive=true` and `status=approved`.
- Database quick checks (optional, if you can peek):
  - A row exists in `carts` for your identifier (userId for JWT, `cartId` for guest).
  - Rows exist in `cart_items` for that cart’s `id`.
- Schema alignment (must-have for guests):
  - `carts` should have `cart_id UUID UNIQUE` and `is_guest BOOLEAN`.
- Retest sequence:
  1) Add → capture `cartId` (guest) and line identifiers.
  2) GET with proper identity (JWT or `cartId`).
  3) UPDATE with the captured identifiers (and `cartId` for guest).

### 1. Get Cart
**GET** `{{base_url}}/api/cart/`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

**Expected Response (Empty Cart):**
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

**Expected Response (With Items):**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "productId": "product-id",
        "vendorId": "vendor-id",
        "color": "Red",
        "size": "M",
        "quantity": 2,
        "price": 29.99,
        "discountedPrice": null,
        "totalPrice": 59.98,
        "productName": "Premium Cotton T-Shirt",
        "productDescription": "High-quality cotton t-shirt perfect for everyday wear.",
        "defaultImageUrl": "https://example.com/images/tshirt-red.jpg"
      }
    ],
    "subtotal": 59.98,
    "tax": 0,
    "shipping": 0,
    "total": 59.98
  },
  "summary": {
    "itemCount": 1,
    "totalItems": 2,
    "subtotal": 59.98,
    "tax": 0,
    "shipping": 0,
    "discount": 0,
    "total": 59.98
  }
}
```

### 2. Add Item to Cart
**POST** `{{base_url}}/api/cart/add`

Use ONE of the following:
- Logged-in flow: send JWT header; DO NOT send `cartId`.
- Guest flow: omit JWT; include `cartId` if you already have one, otherwise the response will return a `cartId` to reuse.

**Headers (logged-in):**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

**Body (logged-in):**
```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 2
}
```

**Headers (guest):**
```
Content-Type: application/json
```

**Body (guest, first add):**
```json
{
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 2
}
```

**Body (guest, subsequent adds):**
```json
{
  "cartId": "{{guest_cart_id}}",
  "productId": "{{product_id}}",
  "color": "Red",
  "size": "M",
  "quantity": 2
}
```

**Test Script (logged-in and guest):**
```javascript
if (pm.response.code === 200) {
  const res = pm.response.json();
  // Capture the exact productId used in the line item for future updates
  if (res.data && Array.isArray(res.data.items) && res.data.items.length > 0) {
    pm.environment.set("cart_line_product_id", res.data.items[0].productId);
    pm.environment.set("cart_line_color", res.data.items[0].color);
    pm.environment.set("cart_line_size", res.data.items[0].size);
  }
  // For guests, keep the cartId
  if (res.cartId) {
    pm.environment.set("guest_cart_id", res.cartId);
  }
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
        "price": 29.99,
        "discountedPrice": null,
        "totalPrice": 59.98,
        "productName": "Premium Cotton T-Shirt",
        "productDescription": "High-quality cotton t-shirt perfect for everyday wear.",
        "defaultImageUrl": "https://example.com/images/tshirt-red.jpg"
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

Important:
- You MUST use the exact `productId`, `color`, and `size` of the line you want to update (as returned by Add Item response).
- Logged-in: DO NOT send `cartId`.
- Guest: include `cartId` returned from the first add.

**Headers (logged-in):**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

**Body (logged-in):**
```json
{
  "productId": "{{cart_line_product_id}}",
  "color": "{{cart_line_color}}",
  "size": "{{cart_line_size}}",
  "quantity": 3
}
```

**Headers (guest):**
```
Content-Type: application/json
```

**Body (guest):**
```json
{
  "cartId": "{{guest_cart_id}}",
  "productId": "{{cart_line_product_id}}",
  "color": "{{cart_line_color}}",
  "size": "{{cart_line_size}}",
  "quantity": 3
}
```

**Notes:**
- If the item disappears after update, ensure the product is still active and approved (GET filters inactive/unapproved products).
- `cartId` is only for guests. Logged-in users are identified by JWT.

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

### Order Creation Overview
The system supports both authenticated and guest order creation:
- **Authenticated Users**: Use `/api/orders/create` with JWT token
- **Guest Users**: Use `/api/orders/create-guest` without authentication

### 1. Create Order (Authenticated User)
**POST** `{{base_url}}/api/orders/create`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

**Note:** Requires valid JWT token for authentication.

**Important:** 
- `customerInfo.email` is used for order notifications
- `shippingAddress` contains all shipping details including phone and email

```json
{
  "customerInfo": {
    "email": "customer@test.com"
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
    "phoneNumber": "+1234567890"
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

**Note:** No authentication required - this endpoint is publicly accessible for guest users.

**Important:** 
- `customerInfo.email` is used for order notifications
- `shippingAddress` contains all shipping details including phone and email

```json
{
  "customerInfo": {
    "email": "guest@test.com"
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
    "phoneNumber": "+1234567890"
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

### 6. Get My Orders (Optimized with Vendor Details)
**GET** `{{base_url}}/api/orders/my-orders`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "count": 1,
  "orders": [
    {
      "id": "05883a12-b3a7-4739-95b6-848cb234dc24",
      "userId": "77dd07bc-2d90-4291-afaf-af76fd786052",
      "items": [
        {
          "product": "c228fdeb-70da-4edf-aeda-c01ebf05e060",
          "vendorId": "c08b906d-aaff-4821-9d9e-d12778b0692b",
          "productName": "prod9",
          "color": "Blue",
          "size": "S",
          "quantity": 1,
          "price": 100.0,
          "discountedPrice": null,
          "totalPrice": 100.0,
          "defaultImageUrl": "https://s3.us-east-005.backblazeb2.com/DaadImgs/products/c228fdeb-70da-4edf-aeda-c01ebf05e060/default/image1.jpg"
        }
      ],
      "shippingAddress": {
        "fullName": "dasda",
        "addressLine1": "asdsads",
        "addressLine2": "",
        "city": "asd",
        "state": "asd",
        "postalCode": "sads",
        "country": "Pakistan",
        "phoneNumber": "dsads"
      },
      "subtotal": 100.0,
      "discount": 0.0,
      "discountCode": "",
      "shippingCharges": 200.0,
      "total": 300.0,
      "pointsUsed": 0,
      "pointsEarned": 3,
      "paymentMethod": "cash-on-delivery",
      "paymentStatus": "pending",
      "paymentProvider": null,
      "transactionId": null,
      "paymentReference": null,
      "paymentReceipt": null,
      "orderStatus": "pending",
      "isFirstOrder": true,
      "trackingNumber": null,
      "estimatedDelivery": null,
      "deliveredAt": null,
      "cancelledAt": null,
      "cancellationReason": null,
      "customerEmail": "customer2@yopmail.com",
      "createdAt": "2025-10-02T23:03:20.848163",
      "updatedAt": "2025-10-02T23:03:20.848163",
      "userInfo": null,
      "vendors": [
        {
          "id": "c08b906d-aaff-4821-9d9e-d12778b0692b",
          "businessName": "Vendor Business Name",
          "businessType": "retail",
          "status": "approved",
          "rating": 4.5,
          "phone": "+1234567890",
          "email": "vendor@example.com",
          "username": "vendor_username"
        }
      ]
    }
  ]
}
```

**Performance Notes:**
- ✅ **Single Query**: Uses optimized approach with minimal database calls
- ✅ **Vendor Details**: Includes complete vendor information for each order
- ✅ **Product Details**: Includes product names and default image URLs in order items
- ✅ **Image URLs**: Automatically fetches primary default image for each product
- ✅ **Fast Response**: Efficient data loading with proper error handling

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

### 9. Get Vendor Orders (All Orders with Status Counts)
**GET** `{{base_url}}/api/orders/vendor`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "totalOrders": 7,
  "count": 7,
  "orders": [
    {
      "id": "order-uuid",
      "userId": "customer-user-id",
      "customerEmail": "customer@example.com",
      "orderStatus": "pending",
      "paymentStatus": "pending",
      "paymentMethod": "bank-transfer",
      "total": 259.98,
      "subtotal": 59.98,
      "shippingCharges": 200.0,
      "items": [
        {
          "product": "product-id",
          "vendorId": "vendor-id",
          "productName": "Product Name",
          "color": "Red",
          "size": "M",
          "quantity": 2,
          "price": 29.99
        }
      ],
      "shippingAddress": {
        "fullName": "John Doe",
        "addressLine1": "123 Main Street",
        "addressLine2": "Apt 4B",
        "city": "Cairo",
        "state": "Cairo Governorate",
        "postalCode": "11511",
        "country": "Egypt",
        "phoneNumber": "+201234567890",
        "email": "customer@example.com"
      },
      "userInfo": {
        "username": "johndoe",
        "email": "customer@example.com",
        "firstName": "John",
        "lastName": "Doe",
        "phoneNumber": "+201234567890"
      },
      "createdAt": "2025-09-28T10:30:00"
    }
  ],
  "statusCounts": {
    "pending": 1,
    "confirmed": 2,
    "processing": 0,
    "shipped": 2,
    "delivered": 2,
    "cancelled": 0
  }
}
```

### 10. Get Vendor Order Counts Only
**GET** `{{base_url}}/api/orders/vendor/counts`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "totalOrders": 7,
  "statusCounts": {
    "pending": 1,
    "confirmed": 2,
    "processing": 0,
    "shipped": 2,
    "delivered": 2,
    "cancelled": 0
  }
}
```

**Use Cases:**
- **Get All Orders**: Use `/api/orders/vendor` to get complete order details with status counts
- **Dashboard Widgets**: Use `/api/orders/vendor/counts` for lightweight status count displays
- **Real-time Updates**: Call `/api/orders/vendor/counts` frequently for dashboard counters

---

## 💳 Payment Integration Testing (Paymob)

### Payment Flow Overview
The system supports two payment methods:
- **Cash on Delivery (COD)**: Traditional payment method, no online processing
- **Bank Transfer via Paymob**: Online payment processing through Paymob gateway

### Prerequisites
1. Ensure Paymob credentials are configured in `application.properties`
2. Run the payment schema migration: `payment_schema.sql`
3. Set up Paymob webhook URL in your Paymob dashboard

### 1. Create Bank Transfer Order (Paymob Integration)
**POST** `{{base_url}}/api/orders/create`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

**Request Body:**
```json
{
  "customerInfo": {
    "email": "customer@test.com"
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
    "city": "Cairo",
    "state": "Cairo",
    "postalCode": "11511",
    "country": "Egypt",
    "phoneNumber": "+201234567890"
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
  "message": "Order created. Proceed to Paymob checkout",
  "order": {
    "id": "generated-order-id",
    "items": [...],
    "total": 259.98,
    "orderStatus": "pending",
    "customerEmail": "customer@test.com"
  },
  "payment": {
    "paymentKey": "paymob-payment-token",
    "checkoutUrl": "https://accept.paymob.com/api/acceptance/iframes/INTEGRATION_ID?payment_token=paymob-payment-token",
    "paymentReference": "ref-generated-order-id",
    "expiresAt": 1703123456
  }
}
```

**Test Script:**
```javascript
if (pm.response.code === 201) {
    const response = pm.response.json();
    pm.environment.set("order_id", response.order.id);
    pm.environment.set("payment_reference", response.payment.paymentReference);
    console.log("Order created with Paymob payment session");
    console.log("Checkout URL:", response.payment.checkoutUrl);
}
```

### 2. Create Payment Session (Alternative Endpoint)
**POST** `{{base_url}}/api/payments/paymob/create-session`

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "orderId": "{{order_id}}",
  "customerEmail": "customer@test.com",
  "customerPhone": "+201234567890",
  "currency": "EGP"
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "paymentKey": "paymob-payment-token",
    "checkoutUrl": "https://accept.paymob.com/api/acceptance/iframes/INTEGRATION_ID?payment_token=paymob-payment-token",
    "paymentReference": "ref-generated-order-id",
    "expiresAt": 1703123456
  }
}
```

### 3. Check Payment Status
**GET** `{{base_url}}/api/payments/order/{{order_id}}/status`

**Headers:**
```
Content-Type: application/json
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "paymentStatus": "pending",
    "paymentMethod": "bank-transfer",
    "transactionId": null,
    "paymentReference": "ref-generated-order-id"
  }
}
```

### 4. Simulate Paymob Webhook (Success)
**POST** `{{base_url}}/api/payments/paymob/webhook`

**Headers:**
```
Content-Type: application/json
x-signature: calculated-hmac-signature
```

**Request Body:**
```json
{
  "type": "TRANSACTION",
  "data": {
    "id": "123456789",
    "success": true,
    "merchant_order_id": "{{order_id}}",
    "amount_cents": 25998,
    "currency": "EGP",
    "created_at": "2023-12-21T10:30:00Z"
  },
  "signature": "calculated-hmac-signature"
}
```

**Expected Response:**
```
ok
```

**Test Script:**
```javascript
if (pm.response.code === 200) {
    console.log("Webhook processed successfully");
    pm.environment.set("transaction_id", "123456789");
}
```

### 5. Simulate Paymob Webhook (Failure)
**POST** `{{base_url}}/api/payments/paymob/webhook`

**Headers:**
```
Content-Type: application/json
x-signature: calculated-hmac-signature
```

**Request Body:**
```json
{
  "type": "TRANSACTION",
  "data": {
    "id": "123456790",
    "success": false,
    "merchant_order_id": "{{order_id}}",
    "amount_cents": 25998,
    "currency": "EGP",
    "created_at": "2023-12-21T10:30:00Z",
    "error_occured": true,
    "error_code": "DECLINED"
  },
  "signature": "calculated-hmac-signature"
}
```

### 6. Verify Order Status After Payment
**GET** `{{base_url}}/api/orders/details/{{order_id}}`

**Headers:**
```
Authorization: Bearer {{customer_token}}
```

**Expected Response (After Successful Payment):**
```json
{
  "success": true,
  "order": {
    "id": "generated-order-id",
    "paymentStatus": "paid",
    "paymentMethod": "bank-transfer",
    "paymentProvider": "paymob",
    "transactionId": "123456789",
    "paymentReference": "ref-generated-order-id",
    "orderStatus": "pending"
  }
}
```

### 7. Test COD Order (No Paymob Integration)
**POST** `{{base_url}}/api/orders/create`

**Headers:**
```
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```

**Request Body:**
```json
{
  "customerInfo": {
    "email": "customer@test.com"
  },
  "items": [
    {
      "product": "{{product_id}}",
      "color": "Blue",
      "size": "L",
      "quantity": 1,
      "price": 29.99
    }
  ],
  "shippingAddress": {
    "fullName": "John Doe",
    "addressLine1": "123 Main St",
    "city": "Cairo",
    "state": "Cairo",
    "postalCode": "11511",
    "country": "Egypt",
    "phoneNumber": "+201234567890"
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

**Expected Response:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "order": {
    "id": "generated-order-id",
    "paymentStatus": "pending",
    "paymentMethod": "cash-on-delivery",
    "orderStatus": "pending"
  }
}
```

### Payment Testing Checklist
- [ ] Bank transfer order creation returns payment session
- [ ] Payment session contains valid checkout URL
- [ ] Payment status endpoint returns correct status
- [ ] Webhook processing updates order status correctly
- [ ] COD orders work without payment integration
- [ ] Payment transactions are logged in database
- [ ] Order status updates after successful payment
- [ ] Failed payments are handled gracefully

### Troubleshooting Payment Issues
1. **Authentication Failed**: Check Paymob API key configuration
2. **Order Creation Failed**: Verify integration ID is correct
3. **Webhook Not Working**: Check webhook URL configuration and signature verification
4. **Payment Status Not Updating**: Verify webhook payload structure matches expected format
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

**Query Parameters (Optional):**
- `status`: Filter vendors by status (`pending`, `approved`, `rejected`, `suspended`)

**Examples:**
- Get all vendors: `{{base_url}}/api/users/vendors`
- Get only pending vendors: `{{base_url}}/api/users/vendors?status=pending`
- Get only approved vendors: `{{base_url}}/api/users/vendors?status=approved`

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

### 2. Get Vendor Overview Stats
**GET** `{{base_url}}/api/vendor-dashboard/overview-stats`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "totalProducts": 45,
    "totalOrders": 127,
    "totalRevenue": 25640.00,
    "pendingOrders": 8,
    "adminCommission": 15.0,
    "totalCommission": 3846.00,
    "netRevenue": 21794.00
  }
}
```

**Test Script:**
```javascript
pm.test("Vendor overview stats returns correct structure", function () {
    const response = pm.response.json();
    pm.expect(response.success).to.be.true;
    pm.expect(response.data).to.have.property('totalProducts');
    pm.expect(response.data).to.have.property('totalOrders');
    pm.expect(response.data).to.have.property('totalRevenue');
    pm.expect(response.data).to.have.property('pendingOrders');
    pm.expect(response.data).to.have.property('adminCommission');
    pm.expect(response.data).to.have.property('totalCommission');
    pm.expect(response.data).to.have.property('netRevenue');
    
    // Verify data types
    pm.expect(response.data.totalProducts).to.be.a('number');
    pm.expect(response.data.totalOrders).to.be.a('number');
    pm.expect(response.data.totalRevenue).to.be.a('number');
    pm.expect(response.data.pendingOrders).to.be.a('number');
    pm.expect(response.data.adminCommission).to.be.a('number');
    pm.expect(response.data.totalCommission).to.be.a('number');
    pm.expect(response.data.netRevenue).to.be.a('number');
});
```

### 3. Get Recent Orders
**GET** `{{base_url}}/api/vendor-dashboard/recent-orders?limit=10`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Query Parameters:**
- `limit`: Number of recent orders to return (default: 10)

**Expected Response:**
```json
{
  "success": true,
  "count": 5,
  "data": [
    {
      "id": "order-uuid",
      "customerEmail": "customer@example.com",
      "orderStatus": "pending",
      "total": 150.00,
      "items": [
        {
          "productId": "product-uuid",
          "vendorId": "vendor-uuid",
          "productName": "Product Name",
          "quantity": 2,
          "price": 75.00
        }
      ],
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### 4. Get Recent Products
**GET** `{{base_url}}/api/vendor-dashboard/recent-products?limit=10`

**Headers:**
```
Authorization: Bearer {{vendor_token}}
```

**Query Parameters:**
- `limit`: Number of recent products to return (default: 10)

**Note:** Only returns products with status "awaiting_approval" or "approved"

**Expected Response:**
```json
{
  "success": true,
  "count": 3,
  "data": [
    {
      "id": "product-uuid",
      "name": "New Product",
      "price": 99.99,
      "status": "approved",
      "isActive": true,
      "createdAt": "2024-01-15T09:00:00Z"
    },
    {
      "id": "product-uuid-2",
      "name": "Pending Product",
      "price": 49.99,
      "status": "awaiting_approval",
      "isActive": true,
      "createdAt": "2024-01-14T15:30:00Z"
    }
  ]
}
```

### 5. Get Sales Analytics
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

---

## 🔧 Admin Dashboard Testing

### 1. Get Admin Dashboard Overview
**GET** `{{base_url}}/api/admin/dashboard/overview`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "totalVendors": 32,
    "totalCustomers": 1245,
    "totalRevenue": 125640.0,
    "pendingApprovals": 5,
    "pendingVendors": 2,
    "pendingProducts": 3
  }
}
```

**Test Script:**
```javascript
pm.test("Admin dashboard overview returns correct structure", function () {
    const response = pm.response.json();
    pm.expect(response.success).to.be.true;
    pm.expect(response.data).to.have.property('totalVendors');
    pm.expect(response.data).to.have.property('totalCustomers');
    pm.expect(response.data).to.have.property('totalRevenue');
    pm.expect(response.data).to.have.property('pendingApprovals');
    pm.expect(response.data).to.have.property('pendingVendors');
    pm.expect(response.data).to.have.property('pendingProducts');
    
    // Verify data types
    pm.expect(response.data.totalVendors).to.be.a('number');
    pm.expect(response.data.totalCustomers).to.be.a('number');
    pm.expect(response.data.totalRevenue).to.be.a('number');
    pm.expect(response.data.pendingApprovals).to.be.a('number');
});
```

### 2. Get Vendor Sales Ranking
**GET** `{{base_url}}/api/admin/vendors/sales-ranking`

**Headers:**
```
Authorization: Bearer {{admin_token}}
```

**Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "vendor_id": "uuid-here",
      "business_name": "Top Vendor Store",
      "business_type": "business",
      "status": "approved",
      "rating_average": 4.5,
      "rating_count": 25,
      "total_sales": 12500.50,
      "total_orders": 45,
      "products_sold": 12
    },
    {
      "vendor_id": "uuid-here-2",
      "business_name": "Second Best Store",
      "business_type": "individual",
      "status": "approved",
      "rating_average": 4.2,
      "rating_count": 18,
      "total_sales": 8900.75,
      "total_orders": 32,
      "products_sold": 8
    }
  ]
}
```

**Test Script:**
```javascript
pm.test("Vendor sales ranking returns correct structure", function () {
    const response = pm.response.json();
    pm.expect(response.success).to.be.true;
    pm.expect(response.data).to.be.an('array');
    
    if (response.data.length > 0) {
        const vendor = response.data[0];
        pm.expect(vendor).to.have.property('vendor_id');
        pm.expect(vendor).to.have.property('business_name');
        pm.expect(vendor).to.have.property('business_type');
        pm.expect(vendor).to.have.property('status');
        pm.expect(vendor).to.have.property('rating_average');
        pm.expect(vendor).to.have.property('rating_count');
        pm.expect(vendor).to.have.property('total_sales');
        pm.expect(vendor).to.have.property('total_orders');
        pm.expect(vendor).to.have.property('products_sold');
        
        // Verify data types
        pm.expect(vendor.total_sales).to.be.a('number');
        pm.expect(vendor.total_orders).to.be.a('number');
        pm.expect(vendor.products_sold).to.be.a('number');
    }
});
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
1. Create product with inventory (colors, sizes, stock)
2. Upload product default images
3. Test product CRUD operations
4. Test inventory management (add colors, update stock)
5. Test product approval flow

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
