# Daad E-commerce Backend

A Spring Boot backend application converted from Node.js/Express.js.

## 🚀 Quick Start

### 1. Environment Setup

Copy the example environment file and fill in your values:

```bash
cp env.example .env
```

Edit `.env` with your actual values:

```bash
# Database Configuration
DB_URL=jdbc:postgresql://your-db-host:5432/your-database
DB_USERNAME=your_username
DB_PASSWORD=your_password
DB_DRIVER=org.postgresql.Driver

# JWT Configuration (generate secure keys)
JWT_ACCESS_SECRET=your_super_long_jwt_access_secret_key_here_at_least_256_bits_long_for_security
JWT_RESET_SECRET=your_super_long_jwt_reset_secret_key_here_at_least_256_bits_long_for_security

# Server Configuration
SERVER_PORT=5000
```

### 2. Run the Application

```bash
# Using Maven
mvn spring-boot:run

# Or using Java directly
mvn clean compile
java -jar target/my-app-1.0-SNAPSHOT.jar
```

### 3. Access the API

- **Health Check**: http://localhost:5000/
- **API Base**: http://localhost:5000/api/

## 🔧 Configuration

The application automatically loads environment variables from:
1. `.env` file (if exists)
2. System environment variables
3. `application.properties` (fallback values)

## 📁 Project Structure

```
src/main/java/com/Daad/ecommerce/
├── config/          # Configuration classes
├── controller/      # REST API controllers
├── dto/            # Data Transfer Objects
├── repository/     # Data access layer
├── security/       # Security configuration
└── service/        # Business logic services
```

## 🔐 Security

- JWT-based authentication
- Role-based access control (Admin, Vendor, Customer)
- Spring Security integration
- CORS configured for development

## 🗄️ Database

- PostgreSQL (configured via environment variables)
- JPA/Hibernate for data persistence
- In-memory repositories for development/testing

## 📝 API Endpoints

### Authentication
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `POST /api/auth/google` - Google OAuth

### Products
- `GET /api/products` - List products
- `POST /api/products` - Create product (Vendor)
- `PUT /api/products/:id` - Update product
- `DELETE /api/products/:id` - Delete product

### Orders
- `POST /api/orders/create` - Create order
- `GET /api/orders/my-orders` - User orders
- `GET /api/orders/all` - All orders (Admin)

### Categories
- `GET /api/categories` - List categories
- `POST /api/categories` - Create category (Admin)

## 🛠️ Development

### Prerequisites
- Java 17+
- Maven 3.6+
- PostgreSQL (optional for development)

### Build
```bash
mvn clean compile
mvn package
```

### Test
```bash
mvn test
```

## 🔒 Environment Variables Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL connection string | `jdbc:postgresql://localhost:5432/ecommerce` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `JWT_ACCESS_SECRET` | JWT access token secret | Generated secure key |
| `JWT_RESET_SECRET` | JWT reset token secret | Generated secure key |
| `SERVER_PORT` | Application port | `5000` |

## 🚨 Security Notes

- Never commit `.env` files to version control
- Use strong, unique JWT secrets (at least 256 bits)
- Change default database credentials
- Use environment-specific configurations for production
