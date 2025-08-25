#!/bin/bash

echo "Testing Railway build configuration..."

# Check if we can build without errors
echo "Checking Gradle wrapper..."
chmod +x ./gradlew

echo "Running clean build..."
./gradlew clean build --no-daemon -x test

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "Generated artifacts:"
    find build -name "*.jar" -type f
else
    echo "❌ Build failed!"
    exit 1
fi
