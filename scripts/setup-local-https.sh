#!/bin/bash

# Script to set up HTTPS for local development
# This creates a self-signed certificate for localhost

echo "Setting up HTTPS for local development..."

# Create certificates directory
mkdir -p certificates

# Generate a private key
openssl genrsa -out certificates/localhost.key 2048

# Generate a certificate signing request
openssl req -new -key certificates/localhost.key -out certificates/localhost.csr \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"

# Generate the self-signed certificate
openssl x509 -req -days 365 -in certificates/localhost.csr \
    -signkey certificates/localhost.key -out certificates/localhost.crt

# Convert to PKCS12 format for Java/Kotlin
openssl pkcs12 -export -out certificates/keystore.p12 \
    -inkey certificates/localhost.key -in certificates/localhost.crt \
    -password pass:changeit -name localhost

# Clean up intermediate files
rm certificates/localhost.csr

echo "HTTPS setup complete!"
echo ""
echo "Certificate created at: certificates/keystore.p12"
echo "Password: changeit"
echo ""
echo "Next steps:"
echo "1. Trust the certificate in your browser/system"
echo "2. Update your application.conf to use HTTPS"
echo "3. Update Yahoo Developer Console redirect URI to: https://localhost:8443/auth"
