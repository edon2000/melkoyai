#!/bin/bash

# Clean deployment script for Melkoyai application
# This script ensures a clean build and deployment to Render

echo "🧹 Starting clean deployment process..."

# Step 1: Clean Maven cache and target directory
echo "📦 Cleaning Maven build artifacts..."
mvn clean
rm -rf target/
rm -rf .mvn/wrapper/maven-wrapper.jar

# Step 2: Verify configuration files
echo "🔍 Verifying configuration..."
echo "Database URL in application.properties:"
grep "quarkus.datasource.jdbc.url" src/main/resources/application.properties

echo "Database username:"
grep "quarkus.datasource.username" src/main/resources/application.properties

# Step 3: Build application
echo "🔨 Building application..."
mvn package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please check the errors above."
    exit 1
fi

echo "✅ Build successful!"

# Step 4: Verify JAR file
if [ -f "target/quarkus-app/quarkus-run.jar" ]; then
    echo "✅ JAR file created successfully"
    ls -la target/quarkus-app/quarkus-run.jar
else
    echo "❌ JAR file not found!"
    exit 1
fi

# Step 5: Git operations for deployment
echo "📤 Preparing for deployment..."

# Add all changes
git add .

# Commit changes
git commit -m "Clean deployment: Fixed database configuration and removed warnings"

# Push to trigger Render deployment
git push origin main

echo "🚀 Deployment triggered! Check Render dashboard for progress."
echo "🌐 Application will be available at: https://melkoyai-app.onrender.com"

# Step 6: Show final configuration summary
echo ""
echo "📋 Final Configuration Summary:"
echo "================================"
echo "Database: Neon PostgreSQL"
echo "Host: ep-gentle-flower-a9huvah-pooler.5-2.eu-central-1.aws.neon.tech"
echo "Database: neondb"
echo "Username: neondb_owner"
echo "SSL Mode: Required"
echo "Connection Pool: 2-10 connections"
echo "Application Name: melkoyai-app"
echo ""
echo "🎉 Clean deployment completed!"