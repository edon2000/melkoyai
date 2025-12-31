# Elon Musk Project

A Quarkus-based web application with user management, product catalog, and financial features.

## Features

- User authentication and registration
- Product management and shopping cart
- Deposit and withdrawal system
- Admin dashboard
- Referral system
- Email notifications

## Technology Stack

- **Framework**: Quarkus 3.8.3
- **Language**: Java 21
- **Database**: PostgreSQL
- **Template Engine**: Qute
- **Build Tool**: Maven
- **Container**: Docker

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- PostgreSQL database
- Docker (optional)

## Getting Started

### 1. Clone the repository
```bash
git clone <repository-url>
cd elonmusk
```

### 2. Configure database
Update `src/main/resources/application.properties` with your database settings.

### 3. Run the application
```bash
./mvnw compile quarkus:dev
```

The application will be available at `http://localhost:8080`

## Building for Production

### Native executable
```bash
./mvnw package -Pnative
```

### Docker image
```bash
./mvnw clean package -Dquarkus.container-image.build=true
```

## API Documentation

When running in dev mode, API documentation is available at:
- Swagger UI: `http://localhost:8080/q/swagger-ui/`
- OpenAPI spec: `http://localhost:8080/q/openapi`

## Health Checks

- Health: `http://localhost:8080/q/health`
- Metrics: `http://localhost:8080/q/metrics`

## License

This project is licensed under the MIT License.