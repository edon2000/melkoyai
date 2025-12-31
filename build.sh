#!/bin/bash

# Build script for Render deployment
echo "Starting build process..."

# Install Maven if not present
if ! command -v mvn &> /dev/null; then
    echo "Installing Maven..."
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar xzf - -C /opt
    export PATH="/opt/apache-maven-3.9.6/bin:$PATH"
fi

# Make mvnw executable
chmod +x ./mvnw

# Clean and build the application
echo "Building application..."
./mvnw clean package -DskipTests

echo "Build completed successfully!"