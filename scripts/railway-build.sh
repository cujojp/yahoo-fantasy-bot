#!/bin/bash

echo "Starting Railway build process..."

# Make gradlew executable
chmod +x ./gradlew

# Clean and build the project
echo "Building project with Gradle..."
./gradlew clean build shadowJar --no-daemon

# Ensure the jars are in the right location
echo "Organizing build artifacts..."
mkdir -p build/libs
cp bot/build/libs/*.jar build/libs/ 2>/dev/null || true
cp backend/build/libs/*.jar build/libs/ 2>/dev/null || true

echo "Build artifacts:"
ls -la build/libs/

echo "Railway build completed successfully!"
