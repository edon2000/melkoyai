#!/bin/bash

# Configuration validation script
echo "🔍 Validating Melkoyai Configuration"
echo "===================================="

# Check application.properties
echo "📄 Checking application.properties..."
if [ -f "src/main/resources/application.properties" ]; then
    echo "✅ application.properties exists"
    
    # Check database configuration
    echo ""
    echo "🗄️ Database Configuration:"
    echo "-------------------------"
    grep -E "quarkus\.datasource\.(db-kind|username|password|jdbc\.url)" src/main/resources/application.properties
    
    # Check for old Supabase references
    echo ""
    echo "🔍 Checking for old Supabase references..."
    if grep -q "supabase" src/main/resources/application.properties; then
        echo "⚠️ WARNING: Found Supabase references in configuration!"
        grep "supabase" src/main/resources/application.properties
    else
        echo "✅ No old Supabase references found"
    fi
    
    # Check for Neon configuration
    echo ""
    echo "🔍 Checking for Neon Database configuration..."
    if grep -q "neon.tech" src/main/resources/application.properties; then
        echo "✅ Neon Database configuration found"
    else
        echo "❌ Neon Database configuration NOT found!"
    fi
    
else
    echo "❌ application.properties not found!"
fi

# Check pom.xml
echo ""
echo "📦 Checking pom.xml..."
if [ -f "pom.xml" ]; then
    echo "✅ pom.xml exists"
    
    # Check for PostgreSQL driver
    if grep -q "quarkus-jdbc-postgresql" pom.xml; then
        echo "✅ PostgreSQL JDBC driver configured"
    else
        echo "❌ PostgreSQL JDBC driver NOT found!"
    fi
    
    # Check for Hibernate ORM
    if grep -q "quarkus-hibernate-orm" pom.xml; then
        echo "✅ Hibernate ORM configured"
    else
        echo "❌ Hibernate ORM NOT found!"
    fi
    
    # Check for duplicate dependencies
    echo ""
    echo "🔍 Checking for duplicate dependencies..."
    duplicates=$(grep -o "quarkus-scheduler" pom.xml | wc -l)
    if [ "$duplicates" -gt 1 ]; then
        echo "⚠️ WARNING: Found $duplicates instances of quarkus-scheduler"
    else
        echo "✅ No duplicate dependencies found"
    fi
    
else
    echo "❌ pom.xml not found!"
fi

# Check Java source files for old references
echo ""
echo "☕ Checking Java source files..."
echo "-------------------------------"

# Check BackupService
if [ -f "src/main/java/elonmusk/service/BackupService.java" ]; then
    if grep -q "Supabase" src/main/java/elonmusk/service/BackupService.java; then
        echo "⚠️ WARNING: Found Supabase references in BackupService.java"
    else
        echo "✅ BackupService.java updated for Neon"
    fi
fi

# Check for any hardcoded database URLs
echo ""
echo "🔍 Checking for hardcoded database URLs..."
if find src/ -name "*.java" -exec grep -l "supabase\.co\|pkdmonstyusgkjaqzm" {} \; | head -5; then
    echo "⚠️ WARNING: Found hardcoded Supabase URLs in Java files!"
else
    echo "✅ No hardcoded Supabase URLs found"
fi

echo ""
echo "🎯 Validation Summary:"
echo "====================="
echo "✅ Configuration validation completed"
echo "📋 Review any warnings above before deployment"
echo ""
echo "🚀 To deploy with clean configuration, run:"
echo "   ./clean-deploy.sh"